package dev.system.voicesystem;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Turns microphone audio into key presses when a trigger phrase is heard.
 * Audio comes either from Simple Voice Chat (preferred) or straight from the microphone.
 */
public class VoiceListener {
	/** Simple Voice Chat delivers microphone audio at 48 kHz. */
	private static final int VOICECHAT_RATE = 48000;
	private static final int VOSK_RATE = 16000;
	private static final short[] END_OF_SPEECH = AudioGate.END_OF_SPEECH;

	private final VoiceConfig config;
	private final Path modelDir;
	private final Map<String, VoiceConfig.Trigger> byWord = new HashMap<>();
	private final LinkedBlockingQueue<short[]> queue = new LinkedBlockingQueue<>(64);
	private final AudioGate gate = new AudioGate();

	private volatile boolean running;
	private Model model;
	private Recognizer recognizer;

	public VoiceListener(VoiceConfig config, Path modelDir) {
		this.config = config;
		this.modelDir = modelDir;
		for (VoiceConfig.Trigger t : config.triggers) {
			if (t.word != null && t.key != null) byWord.put(t.word.toLowerCase().trim(), t);
		}
	}

	// ---------------------------------------------------------------- startup

	/** Listen through Simple Voice Chat's microphone audio. */
	public void startFromVoiceChat() {
		if (!prepare()) return;
		Thread thread = new Thread(this::runVoiceChatWorker, "voicesystem-worker");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);   // never compete with the game for CPU
		thread.start();
		VoiceSystemClient.LOGGER.info("Voice System is listening through Simple Voice Chat for phrases: {}",
			byWord.keySet());
	}

	/** Listen by opening the microphone directly. */
	public void startFromMic() {
		if (!prepare()) return;
		Thread thread = new Thread(this::runMic, "voicesystem-mic");
		thread.setDaemon(true);
		thread.start();
	}

	public void stop() {
		running = false;
	}

	private boolean prepare() {
		if (!Files.isDirectory(modelDir)) {
			VoiceSystemClient.LOGGER.error(
				"Voice model not found at {}. Download vosk-model-small-en-us-0.15 from "
					+ "https://alphacephei.com/vosk/models, unzip it, and put its contents in that folder.",
				modelDir);
			return false;
		}
		try {
			LibVosk.setLogLevel(LogLevel.WARNINGS);

			// Limiting Vosk to our trigger phrases makes it far more accurate and less likely to misfire.
			String grammar = byWord.keySet().stream()
				.map(w -> "\"" + w + "\"")
				.collect(Collectors.joining(", ", "[", ", \"[unk]\"]"));

			model = new Model(modelDir.toString());
			recognizer = new Recognizer(model, VOSK_RATE, grammar);
			running = true;
			return true;
		} catch (Throwable t) {
			VoiceSystemClient.LOGGER.error("Could not start the speech recognizer", t);
			return false;
		}
	}

	// ------------------------------------------------- Simple Voice Chat input

	/** Called by the Simple Voice Chat plugin for every chunk of microphone audio. */
	public void offerVoiceChatAudio(short[] audio) {
		if (!running || audio == null) return;
		// An empty array means you stopped talking.
		if (audio.length == 0) {
			queue.offer(END_OF_SPEECH);
			return;
		}
		// Only quiet-to-loud audio reaches the recognizer; silence is dropped here, so it costs almost nothing.
		for (short[] piece : gate.process(audio.clone())) {
			queue.offer(piece);
		}
	}

	private void runVoiceChatWorker() {
		boolean pending = false;
		try {
			while (running) {
				short[] frame = queue.poll(300, TimeUnit.MILLISECONDS);

				if (frame == null || frame == END_OF_SPEECH) {
					// Silence: finish the phrase if we were in the middle of one.
					if (pending) {
						handle(recognizer.getFinalResult());
						pending = false;
					}
					continue;
				}

				pending = true;
				short[] down = downsample(frame);
				if (recognizer.acceptWaveForm(down, down.length)) {
					handle(recognizer.getResult());
					pending = false;
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Throwable t) {
			VoiceSystemClient.LOGGER.error("Voice worker stopped", t);
		}
	}

	/** 48 kHz to 16 kHz by averaging every three samples. */
	private static short[] downsample(short[] in) {
		int factor = VOICECHAT_RATE / VOSK_RATE;
		short[] out = new short[in.length / factor];
		for (int i = 0; i < out.length; i++) {
			int sum = 0;
			for (int j = 0; j < factor; j++) sum += in[i * factor + j];
			out[i] = (short) (sum / factor);
		}
		return out;
	}

	// ------------------------------------------------------- direct mic input

	private void runMic() {
		AudioFormat format = new AudioFormat(VOSK_RATE, 16, 1, true, false);
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

		try (TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(info)) {
			mic.open(format);
			mic.start();
			VoiceSystemClient.LOGGER.info("Voice System is listening on the microphone for phrases: {}",
				byWord.keySet());

			byte[] buffer = new byte[4096];
			while (running) {
				int read = mic.read(buffer, 0, buffer.length);
				if (read <= 0) continue;
				if (recognizer.acceptWaveForm(buffer, read)) {
					handle(recognizer.getResult());
				}
			}
		} catch (Throwable t) {
			VoiceSystemClient.LOGGER.error("Voice listener stopped", t);
		}
	}

	// ---------------------------------------------------------------- results

	private void handle(String json) {
		try {
			JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
			String text = obj.has("text") ? obj.get("text").getAsString().trim().toLowerCase() : "";
			if (text.isEmpty()) return;
			VoiceSystemClient.LOGGER.info("Heard: '{}'", text);

			// Triggers can be single words or phrases like "system open". Try the longest phrase
			// first and fire only once per utterance, so "system open" never also fires "system".
			String padded = " " + text + " ";
			VoiceConfig.Trigger best = null;
			for (Map.Entry<String, VoiceConfig.Trigger> entry : byWord.entrySet()) {
				if (padded.contains(" " + entry.getKey() + " ")
					&& (best == null || entry.getKey().length() > best.word.trim().length())) {
					best = entry.getValue();
				}
			}
			if (best != null) {
				VoiceSystemClient.LOGGER.info("Matched '{}', pressing {}", best.word, best.key);
				KeyPresser.press(best.key, best.holdMillis);
			}
		} catch (Exception e) {
			VoiceSystemClient.LOGGER.warn("Could not read recognizer output: {}", json);
		}
	}
}

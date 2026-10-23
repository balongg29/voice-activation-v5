package dev.system.voicesystem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** config/voicesystem.json: which spoken words press which keys. */
public class VoiceConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Folder (inside the config folder) that holds the unzipped Vosk model. */
	public String modelFolder = "voicesystem-model";

	/** Use Simple Voice Chat's microphone audio when that mod is installed. Set false to open the mic directly. */
	public boolean useSimpleVoiceChat = true;

	public List<Trigger> triggers = new ArrayList<>();

	/** Where this config was loaded from, so it can be saved again. Not written to the file. */
	public transient Path file;

	public static class Trigger {
		/** The word to listen for, lowercase. */
		public String word;
		/** A Minecraft key name such as key.keyboard.k, key.keyboard.left.shift or key.mouse.left. */
		public volatile String key;
		/** How long to hold the key in milliseconds. 50 is a quick tap. */
		public int holdMillis = 50;

		public Trigger() {}

		public Trigger(String word, String key, int holdMillis) {
			this.word = word;
			this.key = key;
			this.holdMillis = holdMillis;
		}
	}

	/** The main trigger (first in the list), shown in the settings screen. Created if missing. */
	public Trigger systemTrigger() {
		if (triggers.isEmpty()) {
			triggers.add(new Trigger("system open", "key.keyboard.k", 50));
		}
		return triggers.get(0);
	}

	public void save() {
		if (file == null) return;
		try (Writer writer = Files.newBufferedWriter(file)) {
			GSON.toJson(this, writer);
		} catch (IOException e) {
			VoiceSystemClient.LOGGER.error("Could not save {}", file, e);
		}
	}

	public static VoiceConfig loadOrCreate(Path file) {
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				VoiceConfig loaded = GSON.fromJson(reader, VoiceConfig.class);
				if (loaded != null && loaded.triggers != null) {
					loaded.file = file;
					return loaded;
				}
			} catch (Exception e) {
				VoiceSystemClient.LOGGER.error("Could not read {}, using defaults", file, e);
			}
		}

		VoiceConfig config = new VoiceConfig();
		config.file = file;
		config.systemTrigger();
		config.save();
		return config;
	}
}

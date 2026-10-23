package dev.system.voicesystem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Only passes audio to the recognizer while someone is talking.
 * Quiet frames are kept in a short pre-roll buffer, so the first word isn't cut off when the gate opens.
 */
final class AudioGate {

	/** Marks the end of a phrase. Compared by identity, so it must be this exact object. */
	static final short[] END_OF_SPEECH = new short[0];

	/** Average loudness (16-bit scale) that counts as talking. Raise it if the mod hears background noise. */
	private static final double THRESHOLD = 700;
	/** Quiet frames kept before the gate opens (about 20 ms per frame at 48 kHz from Simple Voice Chat). */
	private static final int PRE_ROLL = 10;
	/** Frames to keep the gate open after the last loud frame, so short pauses don't cut a word in half. */
	private static final int HANGOVER = 25;

	private final Deque<short[]> preRoll = new ArrayDeque<>();
	private int openFrames = 0;

	/** Takes one frame and returns the frames the recognizer should get (possibly none). */
	List<short[]> process(short[] frame) {
		List<short[]> out = new ArrayList<>(4);
		if (loudness(frame) > THRESHOLD) {
			if (openFrames == 0) {
				out.addAll(preRoll);   // the gate just opened: include the lead-in
				preRoll.clear();
			}
			openFrames = HANGOVER;
		}
		if (openFrames > 0) {
			out.add(frame);
			if (--openFrames == 0) {
				out.add(END_OF_SPEECH);   // the gate just closed: finish the phrase
			}
		} else {
			preRoll.addLast(frame);
			if (preRoll.size() > PRE_ROLL) preRoll.removeFirst();
		}
		return out;
	}

	private static double loudness(short[] frame) {
		if (frame.length == 0) return 0;
		double sum = 0;
		for (short s : frame) sum += (double) s * s;
		return Math.sqrt(sum / frame.length);
	}
}

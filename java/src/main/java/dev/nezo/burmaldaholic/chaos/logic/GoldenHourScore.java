package dev.nezo.burmaldaholic.chaos.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * The Golden Hour lounge loop (ambient music, AMBIENT category): a four-bar I–vi–IV–V progression in F♯ major
 * played on vanilla note-block voices, one step every {@link #STEP_TICKS} ticks (an eighth note at 120 BPM). PURE
 * and deterministic (the same bar for every player; no randomness), so it is unit-tested and needs no audio files.
 *
 * <p>Note numbers are note-block semitones 0–24 (F♯3 … F♯5); {@link #pitch} maps them to the vanilla pitch
 * multiplier {@code 2^((n − 12) / 12)}. Voices: {@code chime} (arpeggio), {@code bell} (bar accent), {@code bass}
 * (roots on beats 1 and 3).
 */
public final class GoldenHourScore {
	public static final int STEP_TICKS = 5;
	public static final int STEPS_PER_BAR = 8;
	public static final int BARS = 4;
	public static final int LOOP_STEPS = STEPS_PER_BAR * BARS;

	/** Chord tones (note-block semitones) of the four bars: F♯, D♯m, B, C♯. */
	private static final int[][] CHORDS = {{0, 4, 7}, {9, 12, 16}, {5, 9, 12}, {7, 11, 13}};
	/** Arpeggio pattern over the eight steps: chord-tone index + octave (−1 = rest). */
	private static final int[] ARP = {0, 1, 2, 1, 3, 2, 1, -1};

	/** One note: vanilla note-block voice ({@code chime}, {@code bell}, {@code bass}), semitone 0–24, volume 0–1. */
	public record Note(String voice, int semitone, float volume) {}

	private GoldenHourScore() {}

	/** Notes to start at loop step {@code step} (any integer; the loop repeats every {@link #LOOP_STEPS}). */
	public static List<Note> notes(long step) {
		int s = (int) Math.floorMod(step, (long) LOOP_STEPS);
		int bar = s / STEPS_PER_BAR;
		int beat = s % STEPS_PER_BAR;
		int[] chord = CHORDS[bar];
		List<Note> out = new ArrayList<>(3);
		int a = ARP[beat];
		if (a >= 0) {
			int n = a == 3 ? chord[0] + 12 : chord[a];
			out.add(new Note("chime", Math.min(24, n + (n < 7 ? 12 : 0)), beat == 0 ? 0.32f : 0.24f));
		}
		if (beat == 0 || beat == 4) out.add(new Note("bass", chord[0] % 12, 0.30f));
		if (beat == 0 && bar == 0) out.add(new Note("bell", 12 + chord[1] % 12, 0.18f));
		return out;
	}

	/** Loop step of a tick count ({@code tick / STEP_TICKS}); a note starts only on a step boundary. */
	public static boolean onStep(long tick) {
		return Math.floorMod(tick, (long) STEP_TICKS) == 0;
	}

	/** Vanilla note-block pitch multiplier of semitone {@code n} (0 → 0.5, 12 → 1, 24 → 2). */
	public static float pitch(int n) {
		return (float) Math.pow(2, (Math.max(0, Math.min(24, n)) - 12) / 12.0);
	}
}

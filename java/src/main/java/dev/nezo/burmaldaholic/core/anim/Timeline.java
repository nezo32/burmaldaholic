package dev.nezo.burmaldaholic.core.anim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * An immutable presentation timeline: the cross-edition "PTL" format of docs/architecture/animation.md §3.
 * Built by a PURE per-game builder from the server outcome (never from client RNG), so the player's screen,
 * the BER, Bedrock forms/entities and the server settle timer all agree on every beat.
 *
 * <p>Beats are sorted by {@code at}, then by insertion order (stable). {@link #toCanonicalJson()} is the
 * format of the shared test vectors ({@code src/test/resources/fx/vectors/*.json}, mirrored in
 * {@code bedrock/test/fx/vectors/}).
 */
public final class Timeline {
	/** Format version of the canonical JSON. */
	public static final int VERSION = 1;

	private final String game;
	private final int seed;
	private final List<Beat> beats;

	Timeline(String game, int seed, List<Beat> sortedBeats) {
		this.game = game;
		this.seed = seed;
		this.beats = Collections.unmodifiableList(new ArrayList<>(sortedBeats));
	}

	public static TimelineBuilder builder(String game, int seed) {
		return new TimelineBuilder(game, seed);
	}

	public String game() {
		return game;
	}

	/** Cosmetic seed ({@link SeedMix}); the only source of visual variation (jitter, rest offsets). */
	public int seed() {
		return seed;
	}

	public List<Beat> beats() {
		return beats;
	}

	/** End of the last beat (ms). */
	public int endMs() {
		int end = 0;
		for (Beat b : beats) end = Math.max(end, b.end());
		return end;
	}

	/**
	 * End of the last {@link Clock#SHARED} beat: the reveal gate. The server posts result text / chat /
	 * forms at {@code startTick + ceil(sharedEndMs / 50)} and settles at the latest then (money itself is
	 * settled immediately for restart safety where the game design says so).
	 */
	public int sharedEndMs() {
		int end = 0;
		for (Beat b : beats) if (b.clock() == Clock.SHARED) end = Math.max(end, b.end());
		return end;
	}

	/** End of skip group {@code group} (ms), or -1 if the group has no beats. */
	public int groupEnd(int group) {
		int end = -1;
		for (Beat b : beats) if (b.group() == group) end = Math.max(end, b.end());
		return end;
	}

	/** Skip group running at {@code t}: the group of the latest-started beat that is active, or -1. */
	public int groupAt(double t) {
		int g = -1;
		for (Beat b : beats) if (b.activeAt(t)) g = b.group();
		return g;
	}

	/** Visits the beats that are active at {@code t} (in timeline order). No allocation. */
	public void forEachActive(double t, Consumer<Beat> visitor) {
		for (Beat b : beats) {
			if (b.at() > t) break;
			if (b.activeAt(t)) visitor.accept(b);
		}
	}

	/** Ticks (50 ms) from the start until the reveal gate, rounded up (Bedrock and server timers). */
	public int sharedEndTicks() {
		return ceilTicks(sharedEndMs());
	}

	public static int ceilTicks(int ms) {
		return (ms + 49) / 50;
	}

	public String toCanonicalJson() {
		StringBuilder sb = new StringBuilder(64 + beats.size() * 72);
		sb.append("{\"v\":").append(VERSION).append(",\"game\":\"").append(game).append("\",\"seed\":").append(seed)
			.append(",\"beats\":[");
		for (int i = 0; i < beats.size(); i++) {
			if (i > 0) sb.append(',');
			sb.append(beats.get(i).toCanonicalJson());
		}
		return sb.append("]}").toString();
	}

	@Override
	public String toString() {
		return toCanonicalJson();
	}
}

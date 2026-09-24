package dev.nezo.burmaldaholic.core.anim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a {@link Timeline}. A cursor ({@link #cursor()}) helps sequential storyboards; {@link #add} takes
 * absolute times. Local-clock durations go through {@link TimingProfile#scale}; shared-clock durations are
 * never scaled by a viewer preference (only by server-side config such as turbo on a solo machine, which
 * the builder receives as the profile of the spinning player and publishes to spectators).
 *
 * <p>Bedrock port: {@code TimelineBuilder} in {@code bedrock/src/core/logic/anim/timeline.ts}. Keep the two
 * in lock-step: same method names, same integer maths.
 */
public final class TimelineBuilder {
	private final String game;
	private final int seed;
	private final List<Beat> beats = new ArrayList<>();
	private Clock clock = Clock.SHARED;
	private int group;
	private int cursor;

	TimelineBuilder(String game, int seed) {
		this.game = game;
		this.seed = seed;
	}

	/** Clock for the next beats (default {@link Clock#SHARED}). */
	public TimelineBuilder clock(Clock clock) {
		this.clock = clock;
		return this;
	}

	/** Skip group for the next beats (default 0). */
	public TimelineBuilder group(int group) {
		this.group = group;
		return this;
	}

	public int cursor() {
		return cursor;
	}

	public TimelineBuilder cursor(int ms) {
		if (ms < 0) throw new IllegalArgumentException("cursor < 0");
		this.cursor = ms;
		return this;
	}

	/** Adds a beat at an absolute time; the cursor moves to its end if that is later. */
	public TimelineBuilder add(int at, int dur, String kind, int lane, int... args) {
		beats.add(new Beat(at, dur, kind, lane, group, clock, args));
		cursor = Math.max(cursor, at + dur);
		return this;
	}

	/** Adds a beat at the cursor and advances the cursor by its duration. */
	public TimelineBuilder then(int dur, String kind, int lane, int... args) {
		return add(cursor, dur, kind, lane, args);
	}

	/** Adds an instantaneous cue at the cursor (sound, particle burst, prop write). */
	public TimelineBuilder cue(String kind, int lane, int... args) {
		beats.add(new Beat(cursor, 0, kind, lane, group, clock, args));
		return this;
	}

	/** Moves the cursor forward by {@code ms} (a pause). */
	public TimelineBuilder pause(int ms) {
		cursor += ms;
		return this;
	}

	public Timeline build() {
		List<Beat> sorted = new ArrayList<>(beats);
		sorted.sort(Comparator.comparingInt(Beat::at)); // List.sort is stable
		return new Timeline(game, seed, sorted);
	}
}

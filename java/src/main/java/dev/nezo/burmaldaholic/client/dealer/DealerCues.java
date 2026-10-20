package dev.nezo.burmaldaholic.client.dealer;

import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import org.jspecify.annotations.Nullable;

/**
 * Pure helper of the tick-based cue mappers (roulette, craps, poker; DL-SPEC §1 "Gestures"): collects gestures that
 * start at given game ticks and answers the one playing now (the latest started, while it lasts).
 */
public final class DealerCues {
	private final double nowTicks;
	private DealerCueSource.@Nullable Cue best;
	private double bestStart = Double.NEGATIVE_INFINITY;

	private DealerCues(long gameTime, float partialTick) {
		this.nowTicks = gameTime + (double) partialTick;
	}

	public static DealerCues at(long gameTime, float partialTick) {
		return new DealerCues(gameTime, partialTick);
	}

	/** A gesture that starts at game tick {@code startTick}, turned toward {@code seat} (−1 … 1). */
	public DealerCues add(long startTick, DealerGesture gesture, float seat) {
		double ageMs = (nowTicks - startTick) * 50.0;
		if (ageMs >= 0 && startTick >= bestStart) {
			bestStart = startTick;
			best = ageMs < gesture.ms ? new DealerCueSource.Cue(gesture, ageMs, seat) : null;
		}
		return this;
	}

	/** The gesture playing now, or null (idle). */
	public DealerCueSource.@Nullable Cue cue() {
		return best;
	}

	/** Seat {@code index} of {@code count} spread over −1 … 1 (left … right of the dealer). */
	public static float seat(int index, int count) {
		return count <= 1 || index < 0 ? 0f : Math.max(-1f, Math.min(1f, index / (float) (count - 1) * 2f - 1f));
	}
}

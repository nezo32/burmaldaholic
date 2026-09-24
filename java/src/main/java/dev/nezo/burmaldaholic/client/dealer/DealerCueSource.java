package dev.nezo.burmaldaholic.client.dealer;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A table whose dealer NPC gestures along with it (task J-C11). Implemented by the table block entities; the dealer
 * renderer asks the nearest table within 3 blocks (the "open the nearest table" rule of the dealer entities) on the
 * CLIENT, from the table's public block-update state, so gestures need no extra entity data or packets and land on
 * the same tick as the cards (they are sampled from the same beat timeline).
 *
 * <p>HOOK for lane J-L4: {@code BlackjackTableBlockEntity} / {@code BaccaratTableBlockEntity} implement this from
 * their own beats (deal, peek, flip, pay, sweep, shuffle) and the blackjack / baccarat dealers gesture too.
 */
public interface DealerCueSource {
	/** The gesture that started last at {@code gameTime} (client level time), or null (idle). */
	@Nullable Cue dealerCue(long gameTime, float partialTick);

	/**
	 * @param gesture    the gesture
	 * @param ageMs      milliseconds since it started
	 * @param towardSeat −1 … 1: where the served seat sits (left … right of the dealer)
	 */
	record Cue(DealerMotion.Gesture gesture, double ageMs, float towardSeat) {}

	/**
	 * The latest beat of {@code tl} (started at or before {@code tMs}) that maps to a gesture, or null. Pure helper for
	 * the implementations.
	 */
	static @Nullable Cue latest(Timeline tl, double tMs, Function<Beat, DealerMotion.@Nullable Gesture> map, Function<Beat, Float> seat) {
		Cue best = null;
		for (Beat b : tl.beats()) {
			if (b.at() > tMs) break;
			DealerMotion.Gesture g = map.apply(b);
			if (g == null || g == DealerMotion.Gesture.IDLE) continue;
			best = new Cue(g, tMs - b.at(), seat.apply(b));
		}
		return best != null && best.ageMs() < best.gesture().durationMs ? best : null;
	}
}

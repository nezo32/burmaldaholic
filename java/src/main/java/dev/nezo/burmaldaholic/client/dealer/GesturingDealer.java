package dev.nezo.burmaldaholic.client.dealer;

import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;

/**
 * A dealer entity that carries its gesture as synced data ({@code GESTURE} + {@code GESTURE_TICK}, set by its table on
 * the beats; the kit's {@link DealerGesture} contract). The shared dealer renderer plays it with {@link DealerMotion};
 * dealers without it gesture from the nearest {@link DealerCueSource} table instead.
 *
 * <p>HOOK for lane J-L4: {@code BlackjackDealer} / {@code BaccaratDealer} implement this over their synced data.
 */
public interface GesturingDealer {
	/** The current gesture ({@link DealerGesture#NONE} = idle). */
	DealerGesture dealerGesture();

	/** Level game time the gesture started. */
	long dealerGestureTick();

	/** −1 … 1: where the served seat sits (left … right of the dealer). */
	default float dealerGestureSeat() {
		return 0f;
	}
}

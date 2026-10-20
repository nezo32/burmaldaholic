package dev.nezo.burmaldaholic.client.dealer;

import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;

/**
 * A dealer entity a table can make gesture (server side, one synced-data change per beat): the blackjack and baccarat
 * tables look up the nearest sink within 3 blocks instead of their own dealer classes, so the worldgen house dealers
 * ({@code worldgen/npc/HouseDealer}) and the legacy game dealers both follow the cards (DL-SPEC §1 "Gestures").
 */
public interface DealerGestureSink {
	/** Plays {@code gesture} now. */
	void gesture(DealerGesture gesture);
}

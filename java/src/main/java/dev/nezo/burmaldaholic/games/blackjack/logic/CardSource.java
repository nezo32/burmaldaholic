package dev.nezo.burmaldaholic.games.blackjack.logic;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Where the round draws cards from. PURE. */
@FunctionalInterface
public interface CardSource {
	Card draw();

	/** Deals {@code cards} in order first (tests, replays), then falls back to {@code rest} (may be null). */
	static CardSource stacked(List<Card> cards, CardSource rest) {
		Deque<Card> queue = new ArrayDeque<>(cards);
		return () -> {
			Card c = queue.pollFirst();
			if (c != null) {
				return c;
			}
			if (rest == null) {
				throw new IllegalStateException("stacked card source exhausted");
			}
			return rest.draw();
		};
	}
}

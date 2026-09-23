package dev.nezo.burmaldaholic.games.blackjack.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Multi-deck shoe with a cut card (GAME_DESIGN §6.1: reshuffle when ≥ penetration of the shoe has
 * been dealt, checked before the next round). PURE; randomness comes from {@code rng}
 * ({@code bound -> [0, bound)}), in game the fair {@code OddsService} RNG.
 */
public final class Shoe implements CardSource {
	private final IntUnaryOperator rng;
	private final int decks;
	private final List<Card> cards = new ArrayList<>();
	private int pos;

	public Shoe(IntUnaryOperator rng, int decks) {
		this.rng = rng;
		this.decks = Math.max(1, Math.min(8, decks));
		shuffle();
	}

	public int decks() {
		return decks;
	}

	public int size() {
		return cards.size();
	}

	public int dealt() {
		return pos;
	}

	public int remaining() {
		return cards.size() - pos;
	}

	/** True once the cut card has come out. */
	public boolean needsShuffle(double penetration) {
		return pos >= cards.size() * penetration;
	}

	/** Fresh shoe of {@code decks × 52} cards, Fisher–Yates shuffled. */
	public void shuffle() {
		cards.clear();
		for (int d = 0; d < decks; d++) {
			for (int code = 0; code < 52; code++) {
				cards.add(Card.fromCode(code));
			}
		}
		for (int i = cards.size() - 1; i > 0; i--) {
			int j = rng.applyAsInt(i + 1);
			Card t = cards.get(i);
			cards.set(i, cards.get(j));
			cards.set(j, t);
		}
		pos = 0;
	}

	@Override
	public Card draw() {
		// Cannot happen with ≤ 7 seats × 4 hands and penetration ≤ 0.9, but never run dry.
		if (pos >= cards.size()) {
			shuffle();
		}
		return cards.get(pos++);
	}
}

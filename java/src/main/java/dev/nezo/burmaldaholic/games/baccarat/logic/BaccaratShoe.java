package dev.nezo.burmaldaholic.games.baccarat.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * The table's shoe (GAME_DESIGN §20.1): {@code decks × 52} cards, reshuffled before a coup once the
 * penetration is reached, burn procedure after each shuffle. PURE; randomness through {@code rng}
 * ({@code bound -> [0, bound)}; in game the fair {@code OddsService} RNG). Serializable as card codes
 * + position ({@link #codes()}, {@link #restore}) so it survives restarts (§20.5).
 */
public final class BaccaratShoe {
	private final List<Card> cards = new ArrayList<>();
	private int decks;
	private int pos;
	/** Test hook: cards dealt before the shoe itself (in order). */
	private final List<Card> stacked = new ArrayList<>();

	public BaccaratShoe(int decks) {
		this.decks = clampDecks(decks);
	}

	private static int clampDecks(int decks) {
		return Math.max(1, Math.min(8, decks));
	}

	public int decks() {
		return decks;
	}

	/** Cards in a full shoe (0 before the first shuffle). */
	public int size() {
		return cards.size();
	}

	public int dealt() {
		return pos;
	}

	public int remaining() {
		return cards.size() - pos;
	}

	/**
	 * Shuffle due before the next coup: a new shoe, a changed deck count, the penetration reached, or
	 * too few cards left for a coup.
	 */
	public boolean needsShuffle(double penetration, int wantedDecks) {
		return cards.isEmpty() || clampDecks(wantedDecks) != decks || pos >= cards.size() * penetration || remaining() < 6;
	}

	/**
	 * Fresh Fisher–Yates shuffled shoe, then the burn (§20.1): the first card is shown and burned, then
	 * as many more as its burn value (A = 1, 2–9 face, 10/J/Q/K = 10).
	 *
	 * @return the number of burned cards (0 if {@code burn} is false; else 2–11)
	 */
	public int shuffle(IntUnaryOperator rng, int wantedDecks, boolean burn) {
		decks = clampDecks(wantedDecks);
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
		if (!burn) {
			return 0;
		}
		Card first = cards.get(pos++);
		int more = first.burnValue();
		pos += more;
		return 1 + more;
	}

	/** Next card; a shoe that runs dry is reshuffled without burn (cannot happen with penetration ≤ 0.9). */
	public Card draw(IntUnaryOperator rng) {
		if (!stacked.isEmpty()) {
			return stacked.removeFirst();
		}
		if (pos >= cards.size()) {
			shuffle(rng, decks, false);
		}
		return cards.get(pos++);
	}

	/** Tests: the next cards dealt, in order, before the shoe continues. */
	public void stack(List<Card> next) {
		stacked.clear();
		stacked.addAll(next);
	}

	public int[] codes() {
		return cards.stream().mapToInt(Card::code).toArray();
	}

	/** Restores a saved shoe; invalid data leaves an empty shoe (shuffled before the next coup). */
	public void restore(int[] codes, int position, int savedDecks) {
		cards.clear();
		pos = 0;
		decks = clampDecks(savedDecks);
		if (codes == null || codes.length != decks * 52 || position < 0 || position > codes.length) {
			return;
		}
		try {
			for (int c : codes) {
				cards.add(Card.fromCode(c));
			}
		} catch (IllegalArgumentException e) {
			cards.clear();
			return;
		}
		pos = position;
	}
}

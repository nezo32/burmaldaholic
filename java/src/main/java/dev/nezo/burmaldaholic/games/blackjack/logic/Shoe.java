package dev.nezo.burmaldaholic.games.blackjack.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Multi-deck shoe with a cut card (GAME_DESIGN §6.1: reshuffle when ≥ penetration of the shoe has
 * been dealt, checked BETWEEN rounds only). PURE; randomness comes from {@code rng}
 * ({@code bound -> [0, bound)}), in game the fair {@code OddsService} RNG — never a nudged one, and only
 * at a shuffle: every draw is the next card of the shuffled order, so nothing can bias a draw (E5).
 *
 * <p>Countable (E5): the order and the position persist ({@link #codes()}, {@link #restore}), the dealt / left /
 * discard counts and the cut-card position are public.
 *
 * <p><b>Dry shoe (deterministic, documented):</b> the cut card leaves at least {@code size × (1 − penetration)} ≥ 5 cards
 * for the round, so a shoe only runs dry with 1 deck, high penetration and a very long round. If it does, the cards
 * of the CURRENT round stay on the felt and the discard tray (every card dealt before {@link #beginRound}) is shuffled
 * (one Fisher–Yates over the tray with the same rng) and placed behind them; the round continues from it, and
 * {@link #needsShuffle} is true from then on, so the full shoe is reshuffled before the next round. No card can
 * appear twice in one round. Only when the tray is empty too (one round dealt the whole shoe) a fresh pack is
 * shuffled in (duplicates of cards on the felt are then possible; unreachable with ≤ 7 seats × 4 hands and 52+ cards).
 */
public final class Shoe implements CardSource {
	private final IntUnaryOperator rng;
	private final int decks;
	private final List<Card> cards = new ArrayList<>();
	private int pos;
	/** Cards dealt before the current round (the discard tray). */
	private int roundStart;
	/** The shoe ran dry mid-round and was refilled from the tray: reshuffle before the next round. */
	private boolean refilled;

	public Shoe(IntUnaryOperator rng, int decks) {
		this.rng = rng;
		this.decks = clampDecks(decks);
		shuffle();
	}

	private Shoe(IntUnaryOperator rng, int decks, boolean unused) {
		this.rng = rng;
		this.decks = clampDecks(decks);
	}

	private static int clampDecks(int decks) {
		return Math.max(1, Math.min(8, decks));
	}

	/**
	 * A saved shoe (NBT): the exact order and position, no RNG used. Returns null when the data is not a valid
	 * {@code decks × 52} shoe (every card exactly {@code decks} times) — the caller then starts a new shoe.
	 */
	public static Shoe restore(IntUnaryOperator rng, int decks, int[] codes, int pos, int roundStart, boolean refilled) {
		if (decks < 1 || decks > 8 || codes == null || codes.length != decks * 52) return null;
		int[] count = new int[52];
		for (int c : codes) {
			if (c < 0 || c >= 52 || ++count[c] > decks) return null;
		}
		if (pos < 0 || pos > codes.length || roundStart < 0 || roundStart > pos) return null;
		Shoe s = new Shoe(rng, decks, true);
		for (int c : codes) s.cards.add(Card.fromCode(c));
		s.pos = pos;
		s.roundStart = roundStart;
		s.refilled = refilled;
		return s;
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

	/** Cards in the discard tray: everything dealt before the current round. */
	public int discards() {
		return roundStart;
	}

	/** True once the shoe ran dry mid-round and was refilled from the tray (see the class note). */
	public boolean refilled() {
		return refilled;
	}

	/** The cut card's place: it comes out when this many cards have been dealt ({@code ceil(size × penetration)}). */
	public int cut(double penetration) {
		return (int) Math.ceil(cards.size() * penetration - 1e-9);
	}

	/** True once the cut card has come out (or the shoe was refilled mid-round). Check it between rounds only. */
	public boolean needsShuffle(double penetration) {
		return refilled || pos >= cut(penetration);
	}

	/** A round starts: the cards dealt so far are in the discard tray. */
	public void beginRound() {
		roundStart = pos;
	}

	/** The round's cards were collected into the discard tray. */
	public void endRound() {
		roundStart = pos;
	}

	/** The order of the shoe as card codes (saving only — never sent to a client). */
	public int[] codes() {
		int[] out = new int[cards.size()];
		for (int i = 0; i < out.length; i++) out[i] = cards.get(i).code();
		return out;
	}

	/** Fresh shoe of {@code decks × 52} cards, Fisher–Yates shuffled. */
	public void shuffle() {
		cards.clear();
		for (int d = 0; d < decks; d++) {
			for (int code = 0; code < 52; code++) {
				cards.add(Card.fromCode(code));
			}
		}
		fisherYates(cards);
		pos = 0;
		roundStart = 0;
		refilled = false;
	}

	private void fisherYates(List<Card> list) {
		for (int i = list.size() - 1; i > 0; i--) {
			int j = rng.applyAsInt(i + 1);
			Card t = list.get(i);
			list.set(i, list.get(j));
			list.set(j, t);
		}
	}

	@Override
	public Card draw() {
		if (pos >= cards.size()) {
			refillFromTray();
		}
		return cards.get(pos++);
	}

	/** The dry-shoe rule of the class note: the round's cards first, then the shuffled tray. */
	private void refillFromTray() {
		List<Card> tray = new ArrayList<>(cards.subList(0, roundStart));
		List<Card> inPlay = new ArrayList<>(cards.subList(roundStart, cards.size()));
		if (tray.isEmpty()) {
			shuffle();
			refilled = true;
			return;
		}
		fisherYates(tray);
		cards.clear();
		cards.addAll(inPlay);
		cards.addAll(tray);
		pos = inPlay.size();
		roundStart = 0;
		refilled = true;
	}
}

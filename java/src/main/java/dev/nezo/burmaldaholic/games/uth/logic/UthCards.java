package dev.nezo.burmaldaholic.games.uth.logic;

import dev.nezo.burmaldaholic.games.poker.logic.Cards;
import dev.nezo.burmaldaholic.games.poker.logic.HandEvaluator;
import java.util.function.IntUnaryOperator;

/**
 * Cards and hand values for Ultimate Texas Hold'em. GAME_DESIGN §21 reuses the poker hand evaluator
 * (§7.2) and the poker card encoding (int 0..51 = rankIdx × 4 + suit); this is the single place the
 * uth module touches the (pure, Minecraft-free) poker logic classes, so moving the evaluator to a
 * shared package later only changes this file.
 */
public final class UthCards {
	public static final int DECK_SIZE = Cards.DECK_SIZE;
	public static final int PAIR = HandEvaluator.PAIR;
	public static final int STRAIGHT = HandEvaluator.STRAIGHT;

	private UthCards() {}

	/** Value of the best 5 of the first {@code n} (5..7) cards; higher is better, equal = tie. */
	public static int evaluate(int[] cards, int n) {
		return HandEvaluator.evaluate(cards, n);
	}

	public static int evaluate(int... cards) {
		return HandEvaluator.evaluate(cards, cards.length);
	}

	/** Category 0..8 (high card .. straight flush). */
	public static int category(int value) {
		return HandEvaluator.category(value);
	}

	/** Lang suffix for {@code gui.burmaldaholic.poker.hand.<name>} (royal flush included). */
	public static String handName(int value) {
		return HandEvaluator.handName(value);
	}

	public static int rank(int card) {
		return Cards.rank(card);
	}

	public static int suit(int card) {
		return Cards.suit(card);
	}

	public static int parse(String id) {
		return Cards.parse(id);
	}

	public static int[] parseAll(String ids) {
		return Cards.parseAll(ids);
	}

	public static String id(int card) {
		return Cards.id(card);
	}

	public static boolean valid(int card) {
		return Cards.valid(card);
	}

	/** Fresh 52-card deck shuffled with Fisher–Yates ({@code nextInt(bound)} uniform in [0, bound)). */
	public static int[] shuffledDeck(IntUnaryOperator nextInt) {
		int[] a = new int[DECK_SIZE];
		for (int i = 0; i < DECK_SIZE; i++) {
			a[i] = i;
		}
		for (int i = DECK_SIZE - 1; i > 0; i--) {
			int j = nextInt.applyAsInt(i + 1);
			int t = a[i];
			a[i] = a[j];
			a[j] = t;
		}
		return a;
	}
}

package dev.nezo.burmaldaholic.games.blackjack.logic;

import java.util.List;

/** Hand values (GAME_DESIGN §6.2). PURE. A = 1 or 11, 2–10 face value, J/Q/K = 10. */
public final class Hands {
	private Hands() {}

	/** Best total and whether an ace is still counted as 11. */
	public record Value(int total, boolean soft) {}

	public static Value value(List<Card> cards) {
		int total = 0;
		int aces = 0;
		for (Card c : cards) {
			if (c.isAce()) {
				aces++;
			}
			total += c.value();
		}
		while (total > 21 && aces > 0) {
			total -= 10;
			aces--;
		}
		return new Value(total, aces > 0);
	}

	public static int total(List<Card> cards) {
		return value(cards).total();
	}

	public static boolean isBust(List<Card> cards) {
		return total(cards) > 21;
	}

	/** Natural: exactly two cards totalling 21 on an unsplit hand. */
	public static boolean isNatural(List<Card> cards, boolean fromSplit) {
		return !fromSplit && cards.size() == 2 && total(cards) == 21;
	}

	public static boolean isNatural(List<Card> cards) {
		return isNatural(cards, false);
	}

	/** Split needs two cards of the same RANK (K+K yes, K+Q no). */
	public static boolean isPair(List<Card> cards) {
		return cards.size() == 2 && cards.get(0).rank() == cards.get(1).rank();
	}

	/** Display totals: soft hands below 21 show both ("7/17" → {7, 17}); otherwise {total}. */
	public static int[] displayTotals(List<Card> cards) {
		Value v = value(cards);
		return v.soft() && v.total() < 21 ? new int[] {v.total() - 10, v.total()} : new int[] {v.total()};
	}
}

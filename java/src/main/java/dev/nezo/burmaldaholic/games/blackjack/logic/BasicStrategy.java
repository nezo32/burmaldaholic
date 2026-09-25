package dev.nezo.burmaldaholic.games.blackjack.logic;

import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Action;
import java.util.List;

/**
 * Basic strategy for multi-deck, dealer stands on soft 17, double after split, no surrender (the §6.1
 * defaults). PURE. Used by the house-edge Monte-Carlo test.
 */
public final class BasicStrategy {
	private BasicStrategy() {}

	private enum Want {
		H, S, D, DS, P
	}

	private static Want pair(int rank, int up) {
		int v = Math.min(10, rank);
		if (rank == Card.ACE || v == 8) {
			return Want.P;
		}
		if (v == 10 || v == 5) {
			return null;
		}
		if (v == 9) {
			return up <= 6 || up == 8 || up == 9 ? Want.P : Want.S;
		}
		if (v == 7) {
			return up <= 7 ? Want.P : null;
		}
		if (v == 6) {
			return up <= 6 ? Want.P : null;
		}
		if (v == 4) {
			return up == 5 || up == 6 ? Want.P : null;
		}
		return up <= 7 ? Want.P : null; // 2,2 and 3,3
	}

	private static Want soft(int total, int up) {
		if (total >= 19) {
			return Want.S;
		}
		if (total == 18) {
			return up >= 3 && up <= 6 ? Want.DS : up <= 8 ? Want.S : Want.H;
		}
		if (total == 17) {
			return up >= 3 && up <= 6 ? Want.D : Want.H;
		}
		if (total >= 15) {
			return up >= 4 && up <= 6 ? Want.D : Want.H;
		}
		return up >= 5 && up <= 6 ? Want.D : Want.H;
	}

	private static Want hard(int total, int up) {
		if (total >= 17) {
			return Want.S;
		}
		if (total >= 13) {
			return up <= 6 ? Want.S : Want.H;
		}
		if (total == 12) {
			return up >= 4 && up <= 6 ? Want.S : Want.H;
		}
		if (total == 11) {
			return up <= 10 ? Want.D : Want.H;
		}
		if (total == 10) {
			return up <= 9 ? Want.D : Want.H;
		}
		if (total == 9) {
			return up >= 3 && up <= 6 ? Want.D : Want.H;
		}
		return Want.H;
	}

	/** Picks the basic-strategy action among the legal ones. */
	public static Action choose(List<Card> cards, Card dealerUp, List<Action> legal) {
		int up = dealerUp.value();
		if (legal.contains(Action.SPLIT) && Hands.isPair(cards) && pair(cards.getFirst().rank(), up) == Want.P) {
			return Action.SPLIT;
		}
		if (!legal.contains(Action.HIT)) {
			return Action.STAND; // split aces
		}
		Hands.Value v = Hands.value(cards);
		Want want = v.soft() ? soft(v.total(), up) : hard(v.total(), up);
		return switch (want) {
			case D -> legal.contains(Action.DOUBLE) ? Action.DOUBLE : Action.HIT;
			case DS -> legal.contains(Action.DOUBLE) ? Action.DOUBLE : Action.STAND;
			case S -> Action.STAND;
			default -> Action.HIT;
		};
	}
}

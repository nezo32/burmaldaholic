package dev.nezo.burmaldaholic.games.roulette.logic;

import java.util.ArrayList;
import java.util.List;

/** Settlement math for a player's slip (list of bets for one spin). Pure Java. */
public final class Bets {
	/** One chip bet on a spot. */
	public record Bet(Spot spot, long amount) {
		public BetType type() {
			return spot.type();
		}
	}

	private Bets() {}

	/**
	 * Total return of one bet (stake included). Outside bets lose on 0; with la partage even-money bets
	 * get half their stake back (floored) on 0.
	 */
	public static long betReturn(Bet b, int result, boolean laPartage) {
		if (b.spot().covers(result)) {
			return b.amount() * (b.type().payout() + 1L);
		}
		if (result == 0 && laPartage && b.type().evenMoney()) {
			return b.amount() / 2;
		}
		return 0;
	}

	public static long totalReturn(List<Bet> slip, int result, boolean laPartage) {
		long sum = 0;
		for (Bet b : slip) {
			sum += betReturn(b, result, laPartage);
		}
		return sum;
	}

	public static long totalStaked(List<Bet> slip) {
		long sum = 0;
		for (Bet b : slip) {
			sum += b.amount();
		}
		return sum;
	}

	/** Max total return over the 37 outcomes (bankroll reservation, GAME_DESIGN.md §18.2). */
	public static long worstCase(List<Bet> slip, boolean laPartage) {
		long max = 0;
		for (int r = 0; r < Wheel.POCKETS; r++) {
			max = Math.max(max, totalReturn(slip, r, laPartage));
		}
		return max;
	}

	/** Adds a bet to a slip, merging with an existing bet on the same spot. Returns a new list. */
	public static List<Bet> merge(List<Bet> slip, Bet bet) {
		List<Bet> out = new ArrayList<>(slip);
		for (int i = 0; i < out.size(); i++) {
			if (out.get(i).spot().equals(bet.spot())) {
				out.set(i, new Bet(bet.spot(), out.get(i).amount() + bet.amount()));
				return out;
			}
		}
		out.add(bet);
		return out;
	}

	public static List<Bet> mergeAll(List<Bet> slip, List<Bet> bets) {
		List<Bet> out = slip;
		for (Bet b : bets) {
			out = merge(out, b);
		}
		return out;
	}

	/** Amount on a spot in a slip (0 if none). */
	public static long amountOn(List<Bet> slip, Spot spot) {
		for (Bet b : slip) {
			if (b.spot().equals(spot)) {
				return b.amount();
			}
		}
		return 0;
	}
}

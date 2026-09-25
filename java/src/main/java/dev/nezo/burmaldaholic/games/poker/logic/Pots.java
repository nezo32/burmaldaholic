package dev.nezo.burmaldaholic.games.poker.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Pots, side pots, uncalled bets, rake and odd-chip splitting (GAME_DESIGN.md §7.3). Players are
 * referred to by index; {@code totals[i]} = chips player i put in this hand.
 */
public final class Pots {
	private Pots() {}

	/**
	 * One pot.
	 *
	 * @param eligible     non-folded players who can win it
	 * @param contributors everyone who put chips into it (folded players included)
	 * @param paid         chips each player (by index) put into this pot
	 */
	public record Pot(long amount, List<Integer> eligible, List<Integer> contributors, long[] paid) {
		public Pot {
			paid = paid.clone();
		}

		/** Chips player i put into this pot. */
		public long paidBy(int i) {
			return i >= 0 && i < paid.length ? paid[i] : 0;
		}

		@Override
		public long[] paid() {
			return paid.clone();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Pot p && amount == p.amount && eligible.equals(p.eligible) && contributors.equals(p.contributors)
				&& java.util.Arrays.equals(paid, p.paid);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(amount, eligible, contributors, java.util.Arrays.hashCode(paid));
		}

		@Override
		public String toString() {
			return "Pot[" + amount + ", eligible=" + eligible + ", contributors=" + contributors + ", paid=" + java.util.Arrays.toString(paid) + "]";
		}
	}

	/** @param percent 0.05 = 5 %; @param capBb cap in big blinds; @param noFlopNoDrop no flop, no rake */
	public record RakeConfig(double percent, int capBb, boolean noFlopNoDrop) {
		public static final RakeConfig NONE = new RakeConfig(0, 0, true);
	}

	/** Uncalled excess of the largest contribution. */
	public record Uncalled(int player, long amount) {}

	/**
	 * The part of the largest contribution above the second-largest goes back to its owner before
	 * pots are built. Null when the top bet was matched.
	 */
	public static Uncalled uncalledBet(long[] totals) {
		int top = -1;
		long second = 0;
		for (int i = 0; i < totals.length; i++) {
			long v = totals[i];
			if (top < 0 || v > totals[top]) {
				if (top >= 0) {
					second = Math.max(second, totals[top]);
				}
				top = i;
			} else {
				second = Math.max(second, v);
			}
		}
		if (top < 0) {
			return null;
		}
		long amount = totals[top] - second;
		return amount > 0 ? new Uncalled(top, amount) : null;
	}

	/**
	 * Main pot and side pots: levels = distinct contributions of non-folded players, ascending;
	 * pot(L) = Σ min(c, L) − previous levels, eligible = non-folded with c ≥ L. Folded chips above the
	 * top level fall into the last pot. Adjacent pots with the same eligible set are merged.
	 * Call after removing the uncalled bet (for display it also works before).
	 */
	public static List<Pot> buildPots(long[] totals, boolean[] folded) {
		TreeSet<Long> levels = new TreeSet<>();
		long grand = 0;
		for (int i = 0; i < totals.length; i++) {
			grand += totals[i];
			if (!folded[i] && totals[i] > 0) {
				levels.add(totals[i]);
			}
		}
		List<Pot> pots = new ArrayList<>();
		long prev = 0;
		long assigned = 0;
		int li = 0;
		for (long level : levels) {
			long amount = 0;
			List<Integer> contributors = new ArrayList<>();
			List<Integer> eligible = new ArrayList<>();
			long[] paid = new long[totals.length];
			for (int i = 0; i < totals.length; i++) {
				long c = totals[i];
				long part = Math.min(c, level) - Math.min(c, prev);
				if (part > 0) {
					amount += part;
					paid[i] = part;
					contributors.add(i);
				}
				if (!folded[i] && c >= level) {
					eligible.add(i);
				}
			}
			if (li == levels.size() - 1) {
				for (int i = 0; i < totals.length; i++) {
					if (totals[i] > level) {
						paid[i] += totals[i] - level;
						if (!contributors.contains(i)) {
							contributors.add(i);
						}
					}
				}
				amount = grand - assigned;
			}
			assigned += amount;
			prev = level;
			li++;
			Pot last = pots.isEmpty() ? null : pots.get(pots.size() - 1);
			if (last != null && sameSet(last.eligible(), eligible)) {
				List<Integer> merged = new ArrayList<>(last.contributors());
				for (int c : contributors) {
					if (!merged.contains(c)) {
						merged.add(c);
					}
				}
				long[] sum = last.paid();
				for (int i = 0; i < sum.length; i++) {
					sum[i] += paid[i];
				}
				pots.set(pots.size() - 1, new Pot(last.amount() + amount, last.eligible(), List.copyOf(merged), sum));
			} else if (amount > 0) {
				pots.add(new Pot(amount, List.copyOf(eligible), List.copyOf(contributors), paid));
			}
		}
		if (pots.isEmpty() && grand > 0) {
			List<Integer> contributors = new ArrayList<>();
			for (int i = 0; i < totals.length; i++) {
				if (totals[i] > 0) {
					contributors.add(i);
				}
			}
			pots.add(new Pot(grand, List.of(), List.copyOf(contributors), totals));
		}
		return pots;
	}

	private static boolean sameSet(List<Integer> a, List<Integer> b) {
		return a.size() == b.size() && a.containsAll(b);
	}

	/** Rake of a pot without bot chips (see {@link #rakeFor(long, int, boolean, long, RakeConfig, long)}). */
	public static long rakeFor(long amount, int humanContributors, boolean sawFlop, long bb, RakeConfig cfg) {
		return rakeFor(amount, humanContributors, sawFlop, bb, cfg, 0);
	}

	/**
	 * Rake for one pot: {@code min(floor((pot − botContrib) × percent), capBb × BB)} (BOTS.md §5.1: bot chips
	 * never become rake), only when the hand saw a flop (with no-flop-no-drop) and at least 2 humans
	 * contributed (a lone human facing bots is never raked — the bots are the house).
	 */
	public static long rakeFor(long amount, int humanContributors, boolean sawFlop, long bb, RakeConfig cfg, long botContrib) {
		if (humanContributors < 2) {
			return 0;
		}
		if (cfg.noFlopNoDrop() && !sawFlop) {
			return 0;
		}
		long raked = Math.max(0, amount - Math.max(0, botContrib));
		long r = Math.min((long) Math.floor(raked * cfg.percent() + 1e-9), Math.max(0, cfg.capBb()) * bb);
		return Math.max(0, Math.min(amount, r));
	}

	/**
	 * Splits {@code amount} between {@code winners} (ordered clockwise starting left of the button); odd
	 * chips go one at a time to the first winners in that order.
	 */
	public static long[] splitPot(long amount, int winners) {
		if (winners <= 0) {
			return new long[0];
		}
		long base = amount / winners;
		long odd = amount - base * winners;
		long[] out = new long[winners];
		for (int k = 0; k < winners; k++) {
			out[k] = base + (k < odd ? 1 : 0);
		}
		return out;
	}

	/** Seat distance clockwise from the button: 0 = first seat left of the button. */
	public static int orderFromButton(int i, int button, int n) {
		return Math.floorMod(i - button - 1, n);
	}
}

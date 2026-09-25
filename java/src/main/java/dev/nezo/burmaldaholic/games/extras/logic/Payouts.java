package dev.nezo.burmaldaholic.games.extras.logic;

/** Shared payout arithmetic for the extras games. Pure Java. */
public final class Payouts {
	private Payouts() {}

	/**
	 * {@code floor(stake × mult)}, robust to binary floating point ({@code 1.4 × 15 = 20.999…} → 21).
	 * Spec multipliers have at most 2 decimals, so a tiny epsilon is safe.
	 */
	public static long floorPay(long stake, double mult) {
		if (stake <= 0 || !(mult > 0)) {
			return 0;
		}
		return (long) Math.floor(stake * mult + 1e-9);
	}

	/** Multiplier as a short language-neutral string: {@code 10}, {@code 0.5}, {@code 8.1}. */
	public static String formatMultiplier(double mult) {
		if (mult == Math.rint(mult) && Math.abs(mult) < 1e15) {
			return Long.toString((long) mult);
		}
		String s = String.format(java.util.Locale.ROOT, "%.2f", mult);
		while (s.endsWith("0")) {
			s = s.substring(0, s.length() - 1);
		}
		return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
	}
}

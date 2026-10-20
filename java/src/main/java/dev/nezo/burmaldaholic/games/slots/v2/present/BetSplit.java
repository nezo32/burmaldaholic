package dev.nezo.burmaldaholic.games.slots.v2.present;

/**
 * Owner R3 (E1): the bet reads as "coin value × multiplier", a front-end over the unchanged total-bet ladder (every pay
 * is still × the TOTAL bet, SLOTS.md §1.1, §6.1). The coin is {@code slots.<m>.coinValue} when it divides every ladder
 * entry, else the ladder's greatest common divisor (Overworld 5, Nether 10, End 50). VIP / owner caps keep filtering
 * the ladder; the multiplier is just {@code bet / coin}. PURE.
 */
public final class BetSplit {
	private BetSplit() {}

	/** Coin value for a ladder: the configured value when it divides every entry, else the ladder's gcd. */
	public static long coin(long[] ladder, long configured) {
		long g = 0;
		for (long b : ladder) g = gcd(g, Math.abs(b));
		if (configured > 0) {
			boolean divides = ladder.length > 0;
			for (long b : ladder) divides &= b % configured == 0;
			if (divides) return configured;
		}
		return g;
	}

	public static long coin(int[] ladder, long configured) {
		long[] l = new long[ladder.length];
		for (int i = 0; i < l.length; i++) l[i] = ladder[i];
		return coin(l, configured);
	}

	/** {@code bet / coin}, or 0 when the bet is not a whole number of coins. */
	public static long multiplier(long bet, long coin) {
		return coin > 0 && bet > 0 && bet % coin == 0 ? bet / coin : 0;
	}

	private static long gcd(long a, long b) {
		while (b != 0) {
			long t = a % b;
			a = b;
			b = t;
		}
		return a;
	}
}

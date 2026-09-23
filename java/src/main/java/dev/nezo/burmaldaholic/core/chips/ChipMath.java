package dev.nezo.burmaldaholic.core.chips;

/** Pure chip arithmetic (unit-tested). Denominations of GAME_DESIGN.md §3.1, largest first. */
public final class ChipMath {
	public static final int[] DENOMINATIONS = {500, 100, 25, 5, 1};

	private ChipMath() {}

	/**
	 * Greedy split of {@code amount} into chip counts per denomination (same order as
	 * {@link #DENOMINATIONS}); §3.2 "converted greedily into the largest denominations".
	 */
	public static long[] split(long amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("amount < 0");
		}
		long[] counts = new long[DENOMINATIONS.length];
		long rest = amount;
		for (int i = 0; i < DENOMINATIONS.length; i++) {
			counts[i] = rest / DENOMINATIONS[i];
			rest -= counts[i] * DENOMINATIONS[i];
		}
		return counts;
	}

	/** Split into a single denomination only (Bedrock "Denomination" dropdown); the remainder uses smaller chips. */
	public static long[] split(long amount, int preferred) {
		long[] counts = new long[DENOMINATIONS.length];
		long rest = amount;
		boolean started = false;
		for (int i = 0; i < DENOMINATIONS.length; i++) {
			if (DENOMINATIONS[i] == preferred) {
				started = true;
			}
			if (started) {
				counts[i] = rest / DENOMINATIONS[i];
				rest -= counts[i] * DENOMINATIONS[i];
			}
		}
		if (!started) {
			return split(amount);
		}
		return counts;
	}

	/** Chips returned for selling {@code chips} at {@code rate} chips per emerald: whole emeralds only. */
	public static long emeraldsForChips(long chips, int rate) {
		return rate <= 0 ? 0 : chips / rate;
	}

	/**
	 * Withdrawable amount (§3.2): {@code max(0, balance − debt)}, and 0 while a loan is in default.
	 */
	public static long withdrawable(long balance, long debt, boolean inDefault) {
		return inDefault ? 0 : Math.max(0, balance - Math.max(0, debt));
	}
}

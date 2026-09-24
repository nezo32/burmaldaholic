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

	/** Whether {@code denom} is one of the chip {@link #DENOMINATIONS} (client input is untrusted). */
	public static boolean isDenomination(int denom) {
		for (int d : DENOMINATIONS) {
			if (d == denom) {
				return true;
			}
		}
		return false;
	}

	/** Item stacks needed for a split (counts per {@link #DENOMINATIONS}) at {@code stackSize} chips per stack. */
	public static long stacks(long[] counts, int stackSize) {
		long n = 0;
		for (long c : counts) {
			n += (c + stackSize - 1) / stackSize;
		}
		return n;
	}

	/**
	 * {@code amount} reduced (if needed) so that its split ({@code denom} 0 = greedy) fits in {@code maxStacks}
	 * item stacks of {@code stackSize}: full stacks of the largest chips first (review M3: one withdrawal never
	 * spawns an unbounded number of items).
	 */
	public static long capToStacks(long amount, int denom, int maxStacks, int stackSize) {
		if (amount <= 0 || maxStacks <= 0 || stackSize <= 0) {
			return 0;
		}
		long[] counts = splitFor(amount, denom);
		if (stacks(counts, stackSize) <= maxStacks) {
			return amount;
		}
		// Keep the split's largest chips while stacks remain; truncate the first denomination that no longer
		// fits and drop the smaller ones. The greedy split of the result is exactly this truncated split.
		long budget = maxStacks;
		long total = 0;
		for (int i = 0; i < counts.length; i++) {
			long n = Math.min(counts[i], budget * stackSize);
			total += n * DENOMINATIONS[i];
			budget -= (n + stackSize - 1) / stackSize;
			if (n < counts[i]) {
				break;
			}
		}
		return total;
	}

	private static long[] splitFor(long amount, int denom) {
		return denom > 0 ? split(amount, denom) : split(amount);
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

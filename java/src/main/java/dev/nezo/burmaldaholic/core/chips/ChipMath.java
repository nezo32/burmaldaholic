package dev.nezo.burmaldaholic.core.chips;

/**
 * Pure chip arithmetic (unit-tested). Denominations of GAME_DESIGN.md §3.1, largest first.
 *
 * <p>{@link #DENOMINATIONS} is THE list of chip values: the chip items, the cashier, the counting tray, the bet trays and
 * every chip-stack renderer read it (or {@link #ascending()}); {@code DenominationSourceTest} fails when a list of chip
 * values is written anywhere else (owner playtest E4: 1000 / 5000 / 25000 existed in the UI but not as items).
 */
public final class ChipMath {
	/** Every chip value, largest first. Never modify (shared array, read by hot render paths). */
	public static final int[] DENOMINATIONS = {25_000, 5_000, 1_000, 500, 100, 25, 5, 1};
	private static final long[] ASCENDING;

	static {
		ASCENDING = new long[DENOMINATIONS.length];
		for (int i = 0; i < DENOMINATIONS.length; i++) ASCENDING[i] = DENOMINATIONS[DENOMINATIONS.length - 1 - i];
	}

	private ChipMath() {}

	/** The chip values smallest first (a copy). */
	public static long[] ascending() {
		return ASCENDING.clone();
	}

	/** Index of {@code denom} in {@link #DENOMINATIONS} (largest first), or -1. */
	public static int indexOf(long denom) {
		for (int i = 0; i < DENOMINATIONS.length; i++) {
			if (DENOMINATIONS[i] == denom) {
				return i;
			}
		}
		return -1;
	}

	/** The largest chip worth at most {@code amount} (the smallest chip for anything smaller). */
	public static int largestAtMost(long amount) {
		for (int d : DENOMINATIONS) {
			if (d <= amount) {
				return d;
			}
		}
		return DENOMINATIONS[DENOMINATIONS.length - 1];
	}

	/**
	 * The discs of a decorative chip stack for {@code amount}: at most {@code out.length} chip values, largest first (the
	 * bottom of a stack), greedy; a huge amount shows the top chips only. Returns the count (0 for amounts &le; 0).
	 */
	public static int discs(long amount, int[] out) {
		int n = 0;
		long left = amount;
		for (int i = 0; i < DENOMINATIONS.length && n < out.length && left > 0; i++) {
			int d = DENOMINATIONS[i];
			while (left >= d && n < out.length) {
				out[n++] = d;
				left -= d;
			}
		}
		return n;
	}

	/** A short label of a chip value as printed on the chip art: {@code 500}, {@code 1K}, {@code 25K}. */
	public static String label(long denom) {
		return denom >= 1000 && denom % 1000 == 0 ? denom / 1000 + "K" : Long.toString(denom); // literal-ok: thousands suffix
	}

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

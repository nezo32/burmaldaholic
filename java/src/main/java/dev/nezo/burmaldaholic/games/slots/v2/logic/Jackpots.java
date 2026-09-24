package dev.nezo.burmaldaholic.games.slots.v2.logic;

/**
 * Progressive jackpot maths (SLOTS.md §5). Pools are kept by the module ({@code seed + increment} plus a hidden
 * contribution remainder); this is the pure part. Conservation: Σ contributions + minted seed shares = Σ awards +
 * Δ increments, where the bank mints {@code award − (increment − incrementAfter)} for every award.
 */
public final class Jackpots {
	public static final int MINI = 1;
	public static final int MINOR = 2;
	public static final int MAJOR = 3;
	public static final int GRAND = 4;
	/** Contribution remainders are kept in millionths of a chip (contribution rates are in ppm). */
	public static final long MICROS = 1_000_000L;

	private Jackpots() {}

	/**
	 * Award for a hit: {@code floor((seed + increment) × r)}, {@code r = min(1, bet / ref)} (SLOTS.md §5.2).
	 * Integer maths: {@code (seed + increment) × min(bet, ref) / ref}.
	 */
	public static long award(long seed, long increment, long bet, long ref) {
		long b = Math.min(bet, ref);
		return Math.floorDiv(Math.multiplyExact(seed + increment, b), ref);
	}

	/** Increment left after an award: {@code increment × (1 − r)} (floor; the remainder stays hidden). */
	public static long incrementAfter(long increment, long bet, long ref) {
		long b = Math.min(bet, ref);
		return Math.floorDiv(Math.multiplyExact(increment, ref - b), ref);
	}

	/** Pool value ({@code seed + increment}) after an award; the seed part is unchanged (the bank mints its share). */
	public static long poolAfter(long pool, long seed, long bet, long ref) {
		return seed + incrementAfter(Math.max(0, pool - seed), bet, ref);
	}

	/** Award from a pool value. */
	public static long awardFromPool(long pool, long seed, long bet, long ref) {
		return award(seed, Math.max(0, pool - seed), bet, ref);
	}

	/**
	 * Contribution of one stake to one tier: returns {@code {increment added (chips), new remainder (micros)}}.
	 * {@code stake × ppm} micros accumulate; every full million becomes one chip.
	 */
	public static long[] contribute(long remainderMicros, long stake, int ppm) {
		long micros = Math.addExact(remainderMicros, Math.multiplyExact(stake, (long) ppm));
		return new long[] {micros / MICROS, micros % MICROS};
	}
}

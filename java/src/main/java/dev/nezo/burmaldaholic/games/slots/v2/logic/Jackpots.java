package dev.nezo.burmaldaholic.games.slots.v2.logic;

/** Progressive jackpot maths (SLOTS.md §5). Pools are kept by the module; this is the pure part. */
public final class Jackpots {
	public static final int MINI = 1;
	public static final int MINOR = 2;
	public static final int MAJOR = 3;
	public static final int GRAND = 4;

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
}

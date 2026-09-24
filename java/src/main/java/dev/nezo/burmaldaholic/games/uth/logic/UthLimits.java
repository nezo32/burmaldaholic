package dev.nezo.burmaldaholic.games.uth.logic;

/**
 * Bet limits of GAME_DESIGN §21.2 (pure). The VIP / table maximum applies to the worst-case total
 * {@code W = 6 × Ante + Trips} (Ante + Blind + a ×4 Play bet + Trips), not to the Ante.
 */
public final class UthLimits {
	public enum Problem {
		NONE, INVALID, ANTE_MIN, TRIPS_OFF, TRIPS_MIN, WORST_CASE_MAX, INSUFFICIENT_FUNDS, KEEP_FOR_RIVER
	}

	private UthLimits() {}

	/** W = 6 × Ante + Trips. */
	public static long worstCaseTotal(long ante, long trips) {
		return 6 * ante + trips;
	}

	/** Debited at confirmation: Ante + Blind + Trips. */
	public static long confirmCost(long ante, long trips) {
		return 2 * ante + trips;
	}

	/** Largest Ante allowed with {@code trips} under {@code maxW} (0 = none). */
	public static long maxAnte(long maxW, long trips) {
		return Math.max(0, (maxW - Math.max(0, trips)) / 6);
	}

	/**
	 * @param minAnte      table minimum Ante (owner min included)
	 * @param maxW         maximum of W (min(table max, tier max), High-Roller: × multiplier)
	 * @param tripsMin     minimum Trips when placed ({@code uth.minAnte})
	 * @param balance      the player's balance before the debit
	 */
	public static Problem check(long ante, long trips, long minAnte, long maxW, boolean tripsEnabled, long tripsMin, long balance) {
		if (ante <= 0 || trips < 0 || ante > Long.MAX_VALUE / 16 || trips > Long.MAX_VALUE / 16) {
			return Problem.INVALID;
		}
		if (ante < minAnte) {
			return Problem.ANTE_MIN;
		}
		if (trips > 0 && !tripsEnabled) {
			return Problem.TRIPS_OFF;
		}
		if (trips > 0 && trips < tripsMin) {
			return Problem.TRIPS_MIN;
		}
		if (worstCaseTotal(ante, trips) > maxW) {
			return Problem.WORST_CASE_MAX;
		}
		if (balance < confirmCost(ante, trips)) {
			return Problem.INSUFFICIENT_FUNDS;
		}
		if (balance < confirmCost(ante, trips) + ante) {
			return Problem.KEEP_FOR_RIVER;
		}
		return Problem.NONE;
	}
}

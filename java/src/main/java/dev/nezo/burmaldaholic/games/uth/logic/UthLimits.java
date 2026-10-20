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

	/** Antes in the worst-case total W: Ante + Blind + a ×4 Play bet. */
	public static final int WORST_CASE_ANTES = 6;
	/** Antes debited at confirmation: Ante + Blind. */
	public static final int CONFIRM_ANTES = 2;
	/** Antes the balance must hold at confirmation: Ante + Blind + the river's ×1 Play bet (KEEP_FOR_RIVER). */
	public static final int BALANCE_ANTES = CONFIRM_ANTES + 1;

	/** W = 6 × Ante + Trips. */
	public static long worstCaseTotal(long ante, long trips) {
		return WORST_CASE_ANTES * ante + trips;
	}

	/** Debited at confirmation: Ante + Blind + Trips. */
	public static long confirmCost(long ante, long trips) {
		return CONFIRM_ANTES * ante + trips;
	}

	/** What the balance must hold at confirmation: 3 × Ante + Trips (the confirm cost + the smallest Play bet). */
	public static long balanceNeeded(long ante, long trips) {
		return BALANCE_ANTES * ante + trips;
	}

	/** Largest Ante allowed with {@code trips} under {@code maxW} (0 = none). */
	public static long maxAnte(long maxW, long trips) {
		return Math.max(0, (maxW - Math.max(0, trips)) / WORST_CASE_ANTES);
	}

	/**
	 * The largest Ante {@link #check} accepts with {@code trips} on the table: both the worst-case total under {@code maxW}
	 * and the balance check (3 × Ante + Trips). Shared by the server's check and the client's bet-entry Max (T-HR D5),
	 * so they cannot drift. 0 = none.
	 */
	public static long maxAnte(long maxW, long trips, long balance) {
		long t = Math.max(0, trips);
		return Math.max(0, Math.min(maxAnte(maxW, t), (balance - t) / BALANCE_ANTES));
	}

	/** The largest Trips {@link #check} accepts with {@code ante} (limits only; Trips minimum / enabled aside). 0 = none. */
	public static long maxTrips(long maxW, long ante, long balance) {
		long a = Math.max(0, ante);
		return Math.max(0, Math.min(maxW - WORST_CASE_ANTES * a, balance - BALANCE_ANTES * a));
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
		if (balance < balanceNeeded(ante, trips)) {
			return Problem.KEEP_FOR_RIVER;
		}
		return Problem.NONE;
	}
}

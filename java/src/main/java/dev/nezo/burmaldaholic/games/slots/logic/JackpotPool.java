package dev.nezo.burmaldaholic.games.slots.logic;

/**
 * Progressive jackpot pool state (GAME_DESIGN.md §8.5), immutable and PURE.
 *
 * <ul>
 *   <li>Seeded (chips minted by the bank).</li>
 *   <li>Every spin adds floor(spinBet × contribution); the fraction accumulates in a hidden remainder
 *       (kept in millionths of a chip, so it is exact for any config value with ≤ 6 decimals).</li>
 *   <li>Win: award = floor(pool × min(1, spinBet / machineMaxSpinBet)); pool −= award; if the pool falls
 *       below the seed the bank tops it up to the seed.</li>
 * </ul>
 *
 * @param pool      whole chips in the pool (≥ 0)
 * @param remMicros hidden fractional remainder in millionths of a chip, [0, 1 000 000)
 */
public record JackpotPool(long pool, long remMicros) {
	public static final long MICROS = 1_000_000L;

	public JackpotPool {
		pool = Math.max(0, pool);
		remMicros = remMicros >= 0 && remMicros < MICROS ? remMicros : 0;
	}

	public static JackpotPool seeded(long seed) {
		return new JackpotPool(Math.max(0, seed), 0);
	}

	/** Result of adding one spin's contribution. */
	public record Contribution(JackpotPool state, long added) {}

	/** Result of paying a jackpot. {@code toppedUp} = chips minted to restore the seed. */
	public record Payout(JackpotPool state, long award, long toppedUp) {}

	public Contribution contribute(long spinBet, double rate) {
		if (!(rate > 0) || spinBet <= 0) {
			return new Contribution(this, 0);
		}
		long rateMicros = Math.round(rate * MICROS);
		long exact = remMicros + spinBet * rateMicros;
		long added = exact / MICROS;
		return new Contribution(new JackpotPool(pool + added, exact % MICROS), added);
	}

	/** Share of the pool a spin bet wins, capped at the full pool. */
	public long awardFor(long spinBet, long machineMaxSpinBet) {
		if (machineMaxSpinBet <= 0 || spinBet >= machineMaxSpinBet) {
			return pool;
		}
		// floor(pool × spinBet / max) without overflow for realistic values
		return (long) Math.floor((double) pool * spinBet / machineMaxSpinBet + 1e-9);
	}

	public Payout pay(long spinBet, long machineMaxSpinBet, long seed) {
		long award = Math.max(0, Math.min(pool, awardFor(spinBet, machineMaxSpinBet)));
		long left = pool - award;
		long floorSeed = Math.max(0, seed);
		long next = Math.max(left, floorSeed);
		return new Payout(new JackpotPool(next, remMicros), award, next - left);
	}
}

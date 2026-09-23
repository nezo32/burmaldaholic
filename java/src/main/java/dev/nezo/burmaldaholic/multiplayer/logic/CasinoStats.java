package dev.nezo.burmaldaholic.multiplayer.logic;

/**
 * Charter screen statistics (§18.2): today's and total handle, payouts, rake, profit. PURE, mutable.
 * A "day" is a Minecraft day index (game time / 24 000); a new day resets {@link #today()} only.
 */
public final class CasinoStats {
	public static final long TICKS_PER_DAY = 24_000L;

	/** One tally. {@code paid} = chips returned to players (stake included). */
	public static final class Tally {
		public long handle;
		public long paid;
		public long rake;
		public long rounds;

		public long profit() {
			return handle - paid + rake;
		}

		void add(long staked, long payout, long rakeAmount, long roundCount) {
			handle += Math.max(0, staked);
			paid += Math.max(0, payout);
			rake += Math.max(0, rakeAmount);
			rounds += Math.max(0, roundCount);
		}

		void clear() {
			handle = paid = rake = rounds = 0;
		}

		public Tally copy() {
			Tally t = new Tally();
			t.handle = handle;
			t.paid = paid;
			t.rake = rake;
			t.rounds = rounds;
			return t;
		}
	}

	private long day;
	private final Tally today = new Tally();
	private final Tally total = new Tally();

	public static long dayOf(long gameTime) {
		return Math.floorDiv(gameTime, TICKS_PER_DAY);
	}

	public long day() {
		return day;
	}

	/** Moves to {@code newDay} (a later day clears today's tally). */
	public void roll(long newDay) {
		if (newDay != day) {
			today.clear();
			day = newDay;
		}
	}

	public Tally today() {
		return today;
	}

	public Tally total() {
		return total;
	}

	/** One settled house round at an owned table. */
	public void recordRound(long day, long staked, long payout) {
		roll(day);
		today.add(staked, payout, 0, 1);
		total.add(staked, payout, 0, 1);
	}

	/** Poker rake paid into the bankroll. */
	public void recordRake(long day, long rake) {
		roll(day);
		today.add(0, 0, rake, 0);
		total.add(0, 0, rake, 0);
	}

	/** Restores persisted values. */
	public void load(long day, Tally savedToday, Tally savedTotal) {
		this.day = day;
		copyInto(savedToday, today);
		copyInto(savedTotal, total);
	}

	private static void copyInto(Tally from, Tally to) {
		to.handle = from.handle;
		to.paid = from.paid;
		to.rake = from.rake;
		to.rounds = from.rounds;
	}
}

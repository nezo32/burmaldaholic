package dev.nezo.burmaldaholic.games.uth.logic;

/**
 * Player-banked Ultimate Texas Hold'em (GAME_DESIGN §21.9), pure rules: coverage of the bank, the
 * house rake on the banker's positive net, and when a bank can keep dealing.
 */
public final class BankRules {
	private BankRules() {}

	/** A seat's worst case against the bank (§21.6 formula, 505 × Ante + 50 × Trips at defaults). */
	public static long seatWorstCase(long ante, long trips, Paytables pays) {
		return pays.worstCase(ante, trips);
	}

	/** Accept a seat only if {@code reserved + seatWorstCase ≤ bank}. */
	public static boolean covers(long bank, long reserved, long seatWorstCase) {
		return seatWorstCase >= 0 && reserved + seatWorstCase <= bank;
	}

	/** Largest Ante the bank still covers for a seat that also bets {@code trips} (0 = none). */
	public static long maxCoveredAnte(long bank, long reserved, long trips, Paytables pays) {
		long left = bank - reserved - Math.max(0, trips) * pays.tripsFactor();
		long perAnte = Math.max(1, pays.anteFactor());
		return left <= 0 ? 0 : left / perAnte;
	}

	/** Rake on the banker's round result: {@code floor(net × percent)} when positive, else 0. */
	public static long rake(long bankerNet, double percent) {
		if (bankerNet <= 0 || !(percent > 0)) {
			return 0;
		}
		return (long) Math.floor(bankerNet * percent + 1e-9);
	}

	/** The bank can keep dealing: at least {@code minBank} and able to cover one seat at the minimum Ante. */
	public static boolean canKeepBanking(long bank, long minBank, long minAnte, Paytables pays) {
		return bank >= minBank && bank >= pays.worstCase(Math.max(1, minAnte), 0);
	}

	/** Seat nets → banker net ({@code −Σ seatNet}). */
	public static long bankerNet(long... seatNets) {
		long sum = 0;
		for (long n : seatNets) {
			sum += n;
		}
		return -sum;
	}
}

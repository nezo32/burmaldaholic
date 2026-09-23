package dev.nezo.burmaldaholic.games.craps.logic;

/**
 * Payout arithmetic of craps (GAME_DESIGN §10.1). All returns are TOTAL returns (stake included).
 * Pure Java, unit-tested.
 */
public final class CrapsMath {
	public static final int[] POINT_NUMBERS = {4, 5, 6, 8, 9, 10};

	private CrapsMath() {}

	public static boolean isPoint(int n) {
		return n == 4 || n == 5 || n == 6 || n == 8 || n == 9 || n == 10;
	}

	/** Take-odds true-odds ratio {numerator, denominator}: 2:1 (4/10), 3:2 (5/9), 6:5 (6/8). Lay is the inverse. */
	public static int[] oddsRatio(OddsSide side, int point) {
		int[] take = switch (point) {
			case 4, 10 -> new int[] {2, 1};
			case 5, 9 -> new int[] {3, 2};
			case 6, 8 -> new int[] {6, 5};
			default -> throw new IllegalArgumentException("not a point: " + point);
		};
		return side == OddsSide.TAKE ? take : new int[] {take[1], take[0]};
	}

	/** Odds amounts must be a multiple of this so payouts are whole (take 1/2/5, lay 2/3/6). */
	public static int oddsUnit(OddsSide side, int point) {
		return oddsRatio(side, point)[1];
	}

	/** Winnings of an odds bet (excluding the returned odds stake). */
	public static long oddsWin(OddsSide side, int point, long amount) {
		int[] r = oddsRatio(side, point);
		return Math.floorDiv(amount * r[0], r[1]);
	}

	/** Rounds {@code amount} down to a multiple of {@code unit} (never negative). */
	public static long snapOdds(long amount, long unit) {
		return amount <= 0 ? 0 : amount / unit * unit;
	}

	/**
	 * Maximum total odds behind a flat bet. Take: {@code multiple × flat}. Lay: the amount whose
	 * win is {@code multiple × flat} (6× the flat bet at 3-4-5×). Snapped to whole payouts.
	 */
	public static long maxOdds(OddsSide side, int point, long flat, CrapsRules rules) {
		long m = Math.max(0, rules.maxOddsMultiple(point));
		int[] r = oddsRatio(side, point);
		long raw = side == OddsSide.TAKE ? m * flat : m * flat * r[1] / r[0];
		return snapOdds(raw, oddsUnit(side, point));
	}

	/** Field total return (0 = lost): 3,4,9,10,11 pay 1:1, 2 pays fieldPays2:1, 12 pays fieldPays12:1. */
	public static long fieldReturn(int total, long flat, CrapsRules rules) {
		return switch (total) {
			case 2 -> flat * (1 + rules.fieldPays2());
			case 12 -> flat * (1 + rules.fieldPays12());
			case 3, 4, 9, 10, 11 -> flat * 2;
			default -> 0;
		};
	}

	/** Worst-case total return of a flat bet (bankroll reservation, §18.2). */
	public static long flatWorstCase(BetKind kind, long flat, CrapsRules rules) {
		return kind == BetKind.FIELD ? flat * (1 + Math.max(1, Math.max(rules.fieldPays2(), rules.fieldPays12()))) : flat * 2;
	}

	/** Worst-case total return of an odds bet. */
	public static long oddsWorstCase(OddsSide side, int point, long amount) {
		return amount + oddsWin(side, point, amount);
	}
}

package dev.nezo.burmaldaholic.games.uth.logic;

import java.math.BigInteger;

/**
 * Exact Trips side-bet math (GAME_DESIGN §21.3). The category counts over all C(52,7) = 133 784 560
 * seven-card hands are fixed (asserted against a full enumeration with the evaluator in the unit tests),
 * so the exact edge of any configured paytable is integer arithmetic — cheap enough for
 * {@code uth.validateEdge} at every config load.
 */
public final class TripsMath {
	public static final long TOTAL = 133_784_560L;
	/** Seven-card hands whose best 5 is the class (royal, straight flush, quads, full house, flush, straight, trips). */
	public static final long ROYAL = 4_324L, STRAIGHT_FLUSH = 37_260L, QUADS = 224_848L, FULL_HOUSE = 3_473_184L,
		FLUSH = 4_047_644L, STRAIGHT = 6_180_020L, TRIPS = 6_461_620L;

	private TripsMath() {}

	public static long count(PayHand h) {
		return switch (h) {
			case ROYAL -> ROYAL;
			case STRAIGHT_FLUSH -> STRAIGHT_FLUSH;
			case QUADS -> QUADS;
			case FULL_HOUSE -> FULL_HOUSE;
			case FLUSH -> FLUSH;
			case STRAIGHT -> STRAIGHT;
			case TRIPS -> TRIPS;
			case NONE -> TOTAL - ROYAL - STRAIGHT_FLUSH - QUADS - FULL_HOUSE - FLUSH - STRAIGHT - TRIPS;
		};
	}

	/**
	 * Total player result (in Trips units) summed over all 133 784 560 hands: {@code Σ count × pay} for
	 * paying classes minus the losing hands. −2 547 324 for the default 50-40-30-8-6-5-3 table.
	 */
	public static BigInteger totalReturnUnits(Paytables pays) {
		BigInteger sum = BigInteger.ZERO;
		for (PayHand h : PayHand.values()) {
			int pay = h == PayHand.NONE ? 0 : pays.tripsPay(h);
			BigInteger c = BigInteger.valueOf(count(h));
			sum = pay > 0 ? sum.add(c.multiply(BigInteger.valueOf(pay))) : sum.subtract(c);
		}
		return sum;
	}

	/** House edge of the Trips bet (fraction of the bet; 0.019040… at defaults). */
	public static double houseEdge(Paytables pays) {
		return -totalReturnUnits(pays).doubleValue() / TOTAL;
	}
}

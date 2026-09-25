package dev.nezo.burmaldaholic.core.pvp.logic;

/**
 * Coin Flip Duel "Double or nothing" chain arithmetic (PVP.md §4.2). Every flip is its own match; this
 * tracks who is the chain loser and the deficit D (before rake). The next flip's stake is D for each
 * player. The chain ends at "all square" (the chain loser wins a flip), when anyone walks away / takes the
 * money, or after {@code pvp.coin.maxDoubles} doubles (at most maxDoubles + 1 flips).
 *
 * @param link    the flip just settled (1-based)
 * @param loser   participant index of the chain loser (-1 = none: all square)
 * @param deficit D after this flip
 * @param over    the chain cannot continue (all square or the double limit)
 */
public record CoinChain(int link, int loser, long deficit, boolean over) {
	/**
	 * State after flip {@code link} with stake {@code stake} each, lost by {@code flipLoser}.
	 *
	 * @param prev state after the previous flip (null for the first flip)
	 */
	public static CoinChain after(CoinChain prev, int link, int flipLoser, long stake, int maxDoubles) {
		if (prev == null || link <= 1) {
			return new CoinChain(1, flipLoser, stake, 1 > maxDoubles);
		}
		if (flipLoser != prev.loser()) {
			return new CoinChain(link, -1, 0, true); // ALL SQUARE
		}
		long d = Math.addExact(prev.deficit(), stake);
		return new CoinChain(link, flipLoser, d, link > maxDoubles);
	}

	/** Stake of the next flip (each player). */
	public long nextStake() {
		return deficit;
	}

	public boolean allSquare() {
		return loser < 0;
	}

	/**
	 * Why a Double-or-nothing offer is disabled (PVP.md §4.2): null = allowed, else the key shown instead of
	 * the buttons ({@code …coin.don_limit} with {@code tierMax} &lt; D, {@code …coin.don_unaffordable} with a balance &lt; D).
	 */
	public static String offerBlock(long d, long[] tierMax, long[] balance) {
		for (long t : tierMax) {
			if (d > t) {
				return "gui.burmaldaholic.pvp.coin.don_limit";
			}
		}
		for (long b : balance) {
			if (d > b) {
				return "gui.burmaldaholic.pvp.coin.don_unaffordable";
			}
		}
		return null;
	}
}

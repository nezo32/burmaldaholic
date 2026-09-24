package dev.nezo.burmaldaholic.core.anim.dice;

/**
 * Chip stacks on table felts (tables.md §0.4): an amount becomes at most {@link #MAX_DISCS} discs, greedy by the tray
 * denominations 500 / 100 / 25 / 5 / 1, bottom to top. The disc count is visual only; the exact amount is always
 * written (labels use the server's numbers). Pure; shared by the roulette, craps and Dice Duel screens.
 */
public final class ChipStacks {
	public static final int[] DENOMS = {1, 5, 25, 100, 500};
	public static final int MAX_DISCS = 5;
	/** Each disc sits this many px above the one below it (visual/tables.md §2.2). */
	public static final int DISC_STEP = 3;

	private ChipStacks() {}

	/**
	 * Fills {@code out} (length ≥ {@link #MAX_DISCS}) with the disc denominations, bottom first; returns the count
	 * (0 for amounts ≤ 0). Large amounts keep their biggest chips.
	 */
	public static int discs(long amount, int[] out) {
		int n = 0;
		long left = amount;
		for (int i = DENOMS.length - 1; i >= 0 && n < MAX_DISCS && left > 0; i--) {
			int d = DENOMS[i];
			while (left >= d && n < MAX_DISCS) {
				out[n++] = d;
				left -= d;
			}
		}
		return n;
	}

	/** The denomination a single chip of this value is drawn as (largest tray chip ≤ amount; 1 for anything smaller). */
	public static int denomOf(long amount) {
		int best = 1;
		for (int d : DENOMS) {
			if (amount >= d) {
				best = d;
			}
		}
		return best;
	}

	/** Payout discs popping in beside a winning stack: 1 per 40 ms, at most 8 (tables.md §0.4). */
	public static int payoutDiscs(long payout) {
		if (payout <= 0) {
			return 0;
		}
		int[] tmp = new int[MAX_DISCS];
		return Math.min(8, Math.max(1, discs(payout, tmp)));
	}
}

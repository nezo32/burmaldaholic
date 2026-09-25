package dev.nezo.burmaldaholic.core.ui;

/**
 * Chip stack columns (wallet "In your pocket", cashier counting tray; extras.md §8.3, global.md §4.3): side sprites
 * stacked {@link #PITCH} px apart, at most {@link #MAX_VISIBLE} per column (taller stacks show "×N"). Pure.
 */
public final class ChipColumns {
	/** Denominations, largest first (GAME_DESIGN §3.1). */
	public static final int[] DENOMS = {500, 100, 25, 5, 1};
	public static final int PITCH = 2;
	public static final int MAX_VISIBLE = 12;

	private ChipColumns() {}

	/** Greedy breakdown of {@code amount} into {@link #DENOMS} (counts in the same order). */
	public static long[] greedy(long amount) {
		long[] out = new long[DENOMS.length];
		long left = Math.max(0, amount);
		for (int i = 0; i < DENOMS.length; i++) {
			out[i] = left / DENOMS[i];
			left -= out[i] * DENOMS[i];
		}
		return out;
	}

	/** Visible discs of a column of {@code count} chips. */
	public static int visible(long count) {
		return (int) Math.max(0, Math.min(MAX_VISIBLE, count));
	}

	/**
	 * Visible discs when every column shares one height budget: the largest column gets {@code maxVisible}, the others
	 * scale with their count (at least 1 for a non-empty column), so relative sizes still read.
	 */
	public static int[] scaled(long[] counts, int maxVisible) {
		long max = 0;
		for (long c : counts) max = Math.max(max, c);
		int[] out = new int[counts.length];
		for (int i = 0; i < counts.length; i++) {
			if (counts[i] <= 0) continue;
			out[i] = max <= maxVisible ? (int) counts[i] : (int) Math.max(1, Math.round(counts[i] * (double) maxVisible / max));
		}
		return out;
	}
}

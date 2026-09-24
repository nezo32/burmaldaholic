package dev.nezo.burmaldaholic.pvp.logic;

/**
 * Order and place numbers of the shared Final Reveal / result window (PVP.md §3.11.4): places are revealed
 * from the LAST to the 2nd, then the winner(s). Losers with equal points share a place; every pot sharer is
 * place 1 ("dead heat"). Pure.
 */
public final class RevealOrder {
	private RevealOrder() {}

	/**
	 * Place (1-based) of every participant index.
	 *
	 * @param rankOrder participant indices best → worst (the mode's tie-breaks already applied)
	 * @param points    points per participant index
	 * @param winners   the pot sharers: always place 1
	 */
	public static int[] places(int[] rankOrder, long[] points, int[] winners) {
		int n = points.length;
		int[] place = new int[n];
		boolean[] win = new boolean[n];
		for (int w : winners) {
			if (w >= 0 && w < n) {
				win[w] = true;
			}
		}
		int prev = -1;
		int prevPlace = 0;
		int pos = 0;
		for (int i : rankOrder) {
			if (i < 0 || i >= n) {
				continue;
			}
			pos++;
			int p;
			if (win[i]) {
				p = 1;
			} else if (prev >= 0 && !win[prev] && points[prev] == points[i]) {
				p = prevPlace;
			} else {
				p = pos;
			}
			place[i] = p;
			prev = i;
			prevPlace = p;
		}
		return place;
	}

	/** Participant indices in reveal order: worst first, the best last. */
	public static int[] sequence(int[] rankOrder) {
		int[] out = new int[rankOrder.length];
		for (int k = 0; k < rankOrder.length; k++) {
			out[k] = rankOrder[rankOrder.length - 1 - k];
		}
		return out;
	}

	/**
	 * Rows visible after {@code ticks} of the result-window reveal: one more row every {@code step} ticks,
	 * starting with the last place; all rows once the animation is over.
	 */
	public static int visibleRows(int rows, int ticks, int step) {
		if (step <= 0 || ticks < 0) {
			return rows;
		}
		return Math.min(rows, ticks / step + 1);
	}

	/** Bell pitch of the k-th place reveal (0-based among {@code count} reveals): 0.8 → 1.6 rising (§3.11.4). */
	public static float bellPitch(int k, int count) {
		if (count <= 1) {
			return 1.2f;
		}
		int kk = Math.max(0, Math.min(count - 1, k));
		return 0.8f + 0.8f * kk / (count - 1);
	}
}

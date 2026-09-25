package dev.nezo.burmaldaholic.games.poker.logic;

/**
 * The five cards that make a hand's value (animation/cards.md §2.2 "Best 5": the winner's best five lift and
 * glow, the others dim). Pure; shared by poker and UTH (same card encoding and evaluator).
 */
public final class BestFive {
	private BestFive() {}

	/**
	 * Indices (into {@code cards}, 5..7 entries) of a five-card subset whose value equals the value of all the
	 * cards. Among equal subsets the one with the lowest indices wins (hole cards first), so the result is
	 * deterministic.
	 */
	public static int[] indices(int[] cards) {
		int n = cards.length;
		if (n < 5 || n > 7) throw new IllegalArgumentException("5-7 cards");
		int target = HandEvaluator.evaluate(cards, n);
		int[] pick = new int[5];
		int[] idx = new int[5];
		// combinations in lexicographic order
		for (int a = 0; a < n; a++)
			for (int b = a + 1; b < n; b++)
				for (int c = b + 1; c < n; c++)
					for (int d = c + 1; d < n; d++)
						for (int e = d + 1; e < n; e++) {
							pick[0] = cards[a];
							pick[1] = cards[b];
							pick[2] = cards[c];
							pick[3] = cards[d];
							pick[4] = cards[e];
							if (HandEvaluator.evaluate(pick, 5) == target) {
								idx[0] = a;
								idx[1] = b;
								idx[2] = c;
								idx[3] = d;
								idx[4] = e;
								return idx;
							}
						}
		throw new IllegalStateException("no five-card subset reaches the hand value");
	}

	/**
	 * Bit mask over {@code hole ++ board} (bit 0, 1 = hole cards, bit 2 + i = board card i) of the best five.
	 */
	public static int mask(int[] hole, int[] board) {
		int[] seven = new int[hole.length + board.length];
		System.arraycopy(hole, 0, seven, 0, hole.length);
		System.arraycopy(board, 0, seven, hole.length, board.length);
		int m = 0;
		for (int i : indices(seven)) m |= 1 << i;
		return m;
	}
}

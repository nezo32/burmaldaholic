package dev.nezo.burmaldaholic.games.slots.logic;

/**
 * Payline cell coordinates {row, col} (row 0 = top), left→right. The 1-based index is the line
 * number shown to players: 1 middle, 2 top, 3 bottom, 4 diagonal ↘, 5 diagonal ↗. Copper plays
 * line 1, Gold lines 1–3, Netherite 1–5 (GAME_DESIGN.md §8.2–8.4).
 */
public final class Paylines {
	public static final int MAX = 5;

	private static final int[][][] LINES = {
		{{1, 0}, {1, 1}, {1, 2}},
		{{0, 0}, {0, 1}, {0, 2}},
		{{2, 0}, {2, 1}, {2, 2}},
		{{0, 0}, {1, 1}, {2, 2}},
		{{2, 0}, {1, 1}, {0, 2}},
	};

	private Paylines() {}

	/** Row of cell {@code col} (0..2) on 1-based {@code line}. */
	public static int row(int line, int col) {
		return LINES[line - 1][col][0];
	}
}

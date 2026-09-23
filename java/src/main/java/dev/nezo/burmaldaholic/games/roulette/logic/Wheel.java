package dev.nezo.burmaldaholic.games.roulette.logic;

import java.util.List;
import java.util.Set;

/** European single-zero wheel (GAME_DESIGN.md §9). Pure Java. */
public final class Wheel {
	public static final int POCKETS = 37;

	public static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

	/** Pocket order clockwise starting at 0 (animation). */
	public static final List<Integer> ORDER = List.of(
		0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26);

	public enum Color {
		RED, BLACK, GREEN;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private Wheel() {}

	public static boolean isPocket(int n) {
		return n >= 0 && n <= 36;
	}

	public static Color color(int n) {
		if (!isPocket(n)) {
			throw new IllegalArgumentException("not a pocket: " + n);
		}
		return n == 0 ? Color.GREEN : RED.contains(n) ? Color.RED : Color.BLACK;
	}

	/** Index of a pocket in {@link #ORDER}. */
	public static int wheelIndex(int n) {
		int i = ORDER.indexOf(n);
		if (i < 0) {
			throw new IllegalArgumentException("not a pocket: " + n);
		}
		return i;
	}

	/** Layout column 0..2 of 1..36 (1st/2nd/3rd column: 1,4,…/2,5,…/3,6,…). */
	public static int layoutCol(int n) {
		return (n - 1) % 3;
	}

	/** Layout row 0..11 of 1..36 (row 0 = street 1-2-3). */
	public static int layoutRow(int n) {
		return (n - 1) / 3;
	}
}

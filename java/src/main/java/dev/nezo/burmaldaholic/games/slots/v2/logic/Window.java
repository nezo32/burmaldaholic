package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.Arrays;

/** A 5 × 3 window of symbol indices; cell index = reel × 3 + row (row 0 = top). */
public final class Window {
	private final int[] cells;

	public Window(int[] cells) {
		if (cells.length != MachineDef.REELS * MachineDef.ROWS) throw new IllegalArgumentException("15 cells expected");
		this.cells = cells.clone();
	}

	public static Window fromStops(MachineDef def, int[] stops) {
		int[] c = new int[15];
		for (int r = 0; r < 5; r++) for (int y = 0; y < 3; y++) c[r * 3 + y] = def.symbolAt(r, stops[r], y);
		return new Window(c);
	}

	public int at(int reel, int row) {
		return cells[reel * 3 + row];
	}

	public int[] cells() {
		return cells.clone();
	}

	/** 15-bit mask helper: bit (reel × 3 + row). */
	public static int bit(int reel, int row) {
		return 1 << (reel * 3 + row);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Window w && Arrays.equals(w.cells, cells);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(cells);
	}
}

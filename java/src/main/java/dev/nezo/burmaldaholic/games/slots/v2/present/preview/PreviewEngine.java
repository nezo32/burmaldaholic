package dev.nezo.burmaldaholic.games.slots.v2.present.preview;

import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Tumble;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Ways;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Window;

/**
 * PREVIEW FIXTURE (lane J-L9): small helpers over the real engine ({@link Ways}, {@link Tumble}) used to build the
 * preview tapes. Never used to settle money.
 */
public final class PreviewEngine {
	private PreviewEngine() {}

	/** 15 cells (reel × 3 + row) of the strip window at {@code stops}. */
	public static int[] window(MachineDef def, int[] stops) {
		return Window.fromStops(def, stops).cells();
	}

	/** Ways evaluation; {@code stickyMask} bit r-1 forces reel r (2..4) to wilds. */
	public static Ways.Result evaluate(MachineDef def, int[] cells, int stickyMask) {
		return Ways.evaluate(def, applySticky(def, cells, stickyMask), 0);
	}

	/** Cells with the sticky reels (bits 0..2 = reels 2..4) shown as wilds. */
	public static int[] applySticky(MachineDef def, int[] cells, int stickyMask) {
		int[] out = cells.clone();
		for (int b = 0; b < 3; b++) {
			if ((stickyMask & (1 << b)) == 0) continue;
			int r = b + 1;
			for (int y = 0; y < 3; y++) out[r * 3 + y] = wildIndex(def);
		}
		return out;
	}

	public static int wildIndex(MachineDef def) {
		return Math.max(0, def.wild());
	}

	/** Scatter pay in fifths for {@code count} scatters (0 below 3). */
	public static long scatterPay(MachineDef def, int count) {
		return Ways.scatterPay(def, count);
	}

	/** Tumble chain (SLOTS.md §3.2) from the stops with {@code ladder}. */
	public static Tumble.Chain tumble(MachineDef def, int[] stops, int[] ladder) {
		return Tumble.run(def, stops, ladder);
	}
}

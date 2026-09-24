package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.core.anim.Ease;

/**
 * Win show maths (slots.md §4.4; PURE): dimming, the all-wins overview, the symbol cycle and its loop, the cell
 * pulse and the 243-ways paths (segments between winning cells of adjacent reels, flow dashes travelling left →
 * right). Times are ms since the WIN_SHOW beat start.
 */
public final class WinShowPlan {
	public static final int DIM_MS = 150;
	public static final float DIM_BRIGHTNESS = 0.40f;
	public static final int CYCLE_MS = 700;
	/** The cycle loops until the next spin, a feature, or 12 s (then only the frames stay). */
	public static final int LOOP_LIMIT_MS = 12_000;
	/** Flow dash speed (px per ms) and length (px). */
	public static final double FLOW_SPEED = 0.09;
	public static final int FLOW_LEN = 6;
	/** Path alpha during the overview / the current cycle symbol. */
	public static final float OVERVIEW_ALPHA = 0.5f;
	public static final float CYCLE_ALPHA = 1.0f;

	private WinShowPlan() {}

	/** Brightness of a NON-winning cell: 1 → 0.4 over 150 ms; back to 1 after the loop limit. */
	public static float dim(double ms) {
		if (ms <= 0 || ms >= LOOP_LIMIT_MS) return 1f;
		return (float) (1 - (1 - DIM_BRIGHTNESS) * Math.min(1, ms / DIM_MS));
	}

	/** Winning-cell pulse {@code 1 + 0.08·sin(4πt)} (t in s, 2 Hz); reduce motion = 1. */
	public static double pulse(double ms, boolean reduceMotion) {
		if (reduceMotion || ms <= 0) return 1;
		return 1 + 0.08 * Math.sin(4 * Math.PI * ms / 1000.0);
	}

	/**
	 * Cycle entry shown {@code ms} after the cycle started (entries loop), or -1 when the loop limit passed.
	 *
	 * @param entries number of cycle entries (symbols + scatter)
	 */
	public static int cycleIndex(double ms, int entries, double msSinceShow) {
		if (entries <= 0 || msSinceShow >= LOOP_LIMIT_MS || ms < 0) return -1;
		return (int) (ms / CYCLE_MS) % entries;
	}

	/** Pitch of the cycle's {@code slots.win_small}: 1.0 + 0.06·i, restarting after 6. */
	public static float cyclePitch(int i) {
		return 1.0f + 0.06f * (i % 7);
	}

	/** Frame glint phase (4-frame loop at 12.5 fps) for the gold win frame. */
	public static int frameGlint(double ms) {
		return (int) (Math.max(0, ms) / 80) % 4;
	}

	/**
	 * Way-path segments of one winning symbol: every winning cell of reel r to every winning cell of reel r+1, for
	 * r &lt; k−1 (≤ 9 per gap, ≤ 36 per symbol). Writes pairs {@code [cellA, cellB]} into {@code out} and returns the
	 * count of pairs. Cell = reel × 3 + row.
	 */
	public static int segments(int cellMask, int k, int[] out) {
		int n = 0;
		for (int r = 0; r + 1 < Math.min(k, 5); r++) {
			for (int a = 0; a < 3; a++) {
				if ((cellMask & (1 << (r * 3 + a))) == 0) continue;
				for (int b = 0; b < 3; b++) {
					if ((cellMask & (1 << ((r + 1) * 3 + b))) == 0) continue;
					if (2 * n + 1 >= out.length) return n;
					out[2 * n] = r * 3 + a;
					out[2 * n + 1] = (r + 1) * 3 + b;
					n++;
				}
			}
		}
		return n;
	}

	/** Reels spanned by a mask (1 + highest reel with a bit). */
	public static int reelsOf(int cellMask) {
		int k = 0;
		for (int r = 0; r < 5; r++) if ((cellMask & (7 << (r * 3))) != 0) k = r + 1;
		return k;
	}

	/**
	 * Position of the flow dash along a segment of length {@code len} px at {@code ms}: the start offset in px (the
	 * dash is {@link #FLOW_LEN} long; it wraps). Travels left → right at 90 px/s.
	 */
	public static double flowOffset(double ms, double len) {
		if (len <= 0) return 0;
		double period = len + FLOW_LEN;
		return (ms * FLOW_SPEED) % period - FLOW_LEN;
	}

	/** Colour-blind aid: dash shape per cycle slot (0 dash, 1 dot, 2 chevron). */
	public static int dashShape(int cycleSlot) {
		return Math.floorMod(cycleSlot, 3);
	}

	/** Nice banner pop (250 ms outBack scale 0 → 1). */
	public static double bannerPop(double ms) {
		if (ms <= 0) return 0;
		if (ms >= 250) return 1;
		return Ease.OUT_BACK.apply(ms / 250.0);
	}
}

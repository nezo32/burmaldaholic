package dev.nezo.burmaldaholic.games.craps.present;

/**
 * The one fraction table of the craps felt (CS §2.2, §4.1), shared by the v2 screen ({@link CrapsLayout}), the in-world
 * print ({@link CrapsBoardGeometry}, generator {@code craps_v2.mjs printGeometry()}) and the canonical dice arena
 * ({@link CrapsThrow}). Rows top to bottom below the back wall: the place row (Don't Come box on the left spanning into
 * the COME row, six point boxes), the COME row (dice rest zone, total pill, {@code point} caption, come stacks), the
 * FIELD, the DON'T PASS BAR and the PASS LINE with its odds lane. Because the screen, the world and the arena all derive
 * from these numbers, the rest zone, the wall and dice home land on the same printed features at every size. Pure.
 *
 * <p>The arena (tray units, 1 u = the die half-edge; {@code core/anim/dice}) is {@link #ARENA_W} × {@link #ARENA_H}:
 * {@code y = 0} is the back wall's face, {@code y = ARENA_H} the shooter's rail; {@code x = 0} the felt's left edge.
 * Fractions below are of the playing area (the felt under the wall strip), ignoring the 2–3 px gaps between rows.
 */
public final class CrapsFeltFractions {
	private CrapsFeltFractions() {}

	/** Canonical arena width / depth in tray units (CS §2.2: 26 × 10.5 die edges). */
	public static final double ARENA_W = 52, ARENA_H = 21;

	/** Row heights as fractions of the playing area's height (the Pass line takes the rest). */
	public static final double PLACE = 0.27, COME = 0.245, FIELD = 0.18, DONT_PASS = 0.115;
	public static final double PASS = 1 - PLACE - COME - FIELD - DONT_PASS;
	/** Don't Come box width (fraction of the felt width). */
	public static final double DONT_COME_W = 0.17;
	/** The rest zone starts this far into the COME row (fraction of the COME row width). */
	public static final double ZONE_FROM = 0.44;
	/** The total pill's left edge (fraction of the COME row width); the zone ends 4 px before it. */
	public static final double TOTAL_AT = 0.66;
	/** Dice home: the outer {@code HOME} of the Pass line at either end (CS §4.1: past the odds word / first 9 %). */
	public static final double HOME = 0.09;

	/** Back wall strip height in GUI px per tier (T1 4, T2 6, T3 8, ROOMY 10) and on the in-world print (art px). */
	public static final int WALL_T1 = 4, WALL_T2 = 6, WALL_T3 = 8, WALL_ROOMY = 10, WALL_PRINT = 6;

	/** Top (fraction of the playing area) of each row: place, come, field, don't pass, pass, bottom. */
	public static double rowTop(int row) {
		double[] h = {PLACE, COME, FIELD, DONT_PASS, PASS};
		double y = 0;
		for (int i = 0; i < row; i++) y += h[i];
		return y;
	}

	/** Left edge of the COME row / point boxes (fraction of the width). */
	public static double comeX() {
		return DONT_COME_W;
	}

	public static double comeW() {
		return 1 - DONT_COME_W;
	}

	/** Point box {@code i} (0..5) left and right fractions. */
	public static double pointX0(int i) {
		return comeX() + comeW() * i / 6.0;
	}

	public static double pointX1(int i) {
		return comeX() + comeW() * (i + 1) / 6.0;
	}

	// ---- arena (tray units) ------------------------------------------------------------------------------------------

	/** Arena x of a felt-width fraction. */
	public static double u(double fx) {
		return fx * ARENA_W;
	}

	/** Arena y of a playing-area-height fraction. */
	public static double v(double fy) {
		return fy * ARENA_H;
	}
}

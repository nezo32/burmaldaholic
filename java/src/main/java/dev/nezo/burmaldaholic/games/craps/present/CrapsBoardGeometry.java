package dev.nezo.burmaldaholic.games.craps.present;

import dev.nezo.burmaldaholic.core.ui.v2.Box;

/**
 * The in-world craps print (ui-v2.md §7.6, lane D; CM CS §6): the same rows and fractions as the v2 screen's
 * {@link CrapsLayout} ({@link CrapsFeltFractions}), at the size of the generated texture
 * {@code entity/craps/board_print.png} (256 × 176, {@code printGeometry()} of
 * {@code tools/assets/modules/tables/craps_v2.mjs} — keep the two in step): the rubber-pyramid back wall strip along the
 * far edge, then the rows. Laid flat on the design felt with the Pass line toward the players (+z); the board kit maps
 * the design felt onto the full 3 × 2 table. Converts print px to table-local (design) block coordinates and the dice
 * arena (tray units) to print px. Pure.
 */
public final class CrapsBoardGeometry {
	public static final int PRINT_W = 256, PRINT_H = 176;
	/** Print size on the design felt (blocks); centred on the block. Fills the design felt's width (2.0). */
	public static final float W = 2.0f, D = W * PRINT_H / PRINT_W;
	public static final float SCALE = W / PRINT_W;
	/** Margin and row gap of the print (px). */
	public static final int M = 4, GAP = 3;
	/** The back wall strip (pyramids) on the print; its bottom edge is the wall face the dice hit. */
	public static final Box WALL = new Box(M, M, PRINT_W - 2 * M, CrapsFeltFractions.WALL_PRINT);
	/** The rows start here (under the wall and its shadow line). */
	public static final int ROWS_Y = 12;
	/** Raised wall box (blocks above the felt) drawn on the strip. */
	public static final float WALL_HEIGHT = 0.03f;
	/** World die edge (design blocks): two dice resting at the arena's 2.4-u separation never touch on the print. */
	public static final float DIE = 0.078f;

	public static final Box DONT_COME, COME, FIELD, DONT_PASS, PASS;
	public static final Box[] POINTS = new Box[6];
	/** The field number columns' centres (px), 2 3 4 9 10 11 12. */
	public static final int[] FIELD_CX = new int[7];

	static {
		int w = PRINT_W - 2 * M;
		int h = PRINT_H - ROWS_Y - M;
		int avail = h - 4 * GAP;
		int placeH = (int) Math.round(avail * CrapsFeltFractions.PLACE);
		int comeH = (int) Math.round(avail * CrapsFeltFractions.COME);
		int fieldH = (int) Math.round(avail * CrapsFeltFractions.FIELD);
		int dpH = (int) Math.round(avail * CrapsFeltFractions.DONT_PASS);
		int passH = avail - placeH - comeH - fieldH - dpH;
		int dcW = (int) Math.round(w * CrapsFeltFractions.DONT_COME_W);
		int px0 = M + dcW + GAP;
		int pw = M + w - px0;
		for (int i = 0; i < 6; i++) {
			int a = px0 + pw * i / 6;
			int b = px0 + pw * (i + 1) / 6;
			POINTS[i] = new Box(a, ROWS_Y, b - a - (i < 5 ? GAP : 0), placeH);
		}
		int comeY = ROWS_Y + placeH + GAP;
		int fieldY = comeY + comeH + GAP;
		int dpY = fieldY + fieldH + GAP;
		int passY = dpY + dpH + GAP;
		DONT_COME = new Box(M, ROWS_Y, dcW, placeH + GAP + comeH);
		COME = new Box(px0, comeY, pw, comeH);
		FIELD = new Box(M, fieldY, w, fieldH);
		DONT_PASS = new Box(M, dpY, w, dpH);
		PASS = new Box(M, passY, w, passH);
		int fx0 = FIELD.x() + (int) Math.round(FIELD.w() * 0.2);
		int fw = FIELD.right() - 6 - fx0;
		for (int i = 0; i < 7; i++) FIELD_CX[i] = fx0 + fw * (2 * i + 1) / 14;
	}

	private CrapsBoardGeometry() {}

	/** Table-local x of print px {@code px}. */
	public static float x(double px) {
		return (float) (0.5 - W / 2 + px * SCALE);
	}

	/** Table-local z of print px {@code py} (+z = the players' side). */
	public static float z(double py) {
		return (float) (0.5 - D / 2 + py * SCALE);
	}

	// ---- the dice arena on the print ---------------------------------------------------------------------------------

	/** Half the world die in print px. */
	public static double dieHalfPx() {
		return DIE / 2 / SCALE;
	}

	/** Print px per tray unit along x (the arena spans the print's width inside the margins). */
	public static double sx() {
		return (PRINT_W - 2 * M) / CrapsFeltFractions.ARENA_W;
	}

	/**
	 * Print px per tray unit along y: anchored so a die touching the back wall (centre {@code R_PLANE} from it) touches
	 * the wall face on the print, and a die on the shooter's rail touches the print's bottom edge.
	 */
	public static double sy() {
		double r = dev.nezo.burmaldaholic.core.anim.dice.DiceTray.R_PLANE;
		double top = WALL.bottom() + dieHalfPx();
		double bot = PRINT_H - M - dieHalfPx();
		return (bot - top) / (CrapsFeltFractions.ARENA_H - 2 * r);
	}

	/** Print x of arena x. */
	public static double px(double u) {
		return M + u * sx();
	}

	/** Print y of arena y. */
	public static double py(double v) {
		double r = dev.nezo.burmaldaholic.core.anim.dice.DiceTray.R_PLANE;
		return WALL.bottom() + dieHalfPx() + (v - r) * sy();
	}

	public static Box point(int point) {
		for (int i = 0; i < CrapsLayout.POINTS.length; i++) {
			if (CrapsLayout.POINTS[i] == point) return POINTS[i];
		}
		throw new IllegalArgumentException("not a point: " + point);
	}

	/**
	 * Print-px centre {x, y} of a stack: bet {@code kind} on {@code point} (travelled come / don't come; 0 = its own area),
	 * {@code odds} = the odds behind it, {@code seat} spreads the players (0–7) along the area.
	 */
	public static double[] stack(String kind, int point, boolean odds, int seat) {
		int s = Math.floorMod(seat, 8);
		if (point != 0 && ("come".equals(kind) || "dont_come".equals(kind))) {
			Box b = point(point);
			boolean dc = "dont_come".equals(kind);
			double fx = (dc ? 0.72 : 0.28) + (odds ? (dc ? -0.18 : 0.18) : 0);
			double fy = 0.62 + 0.12 * (s % 3) / 2.0;
			return new double[] {b.x() + b.w() * fx + (s / 3) * 3, b.y() + b.h() * fy};
		}
		Box a = switch (kind) {
			case "dont_come" -> DONT_COME;
			case "come" -> COME;
			case "field" -> FIELD;
			case "dont_pass" -> DONT_PASS;
			default -> PASS;
		};
		// players spread left to right across the area (seat 0 at 18 %, seat 7 at 88 %); odds just right of the flat bet
		double fx = 0.18 + 0.10 * s + (odds ? 0.045 : 0);
		double fy = "field".equals(kind) ? 0.72 : "dont_come".equals(kind) ? 0.35 + 0.08 * s : 0.55;
		if ("dont_come".equals(kind)) fx = odds ? 0.7 : 0.4;
		if ("come".equals(kind)) fx = 0.82 + 0.02 * (s % 4) + (odds ? 0.06 : 0); // right of the dice zone and the total
		return new double[] {a.x() + a.w() * fx, a.y() + a.h() * fy};
	}

	/** Puck centre (print px): ON on the point box's top-right corner, OFF in the Don't Come box. */
	public static double[] puck(int point) {
		if (point == 0) return new double[] {DONT_COME.right() - 10, DONT_COME.y() + 10};
		Box b = point(point);
		return new double[] {b.right() - 9, b.y() + 9};
	}

	/** The house bank on the print (top centre, at the wall): where the take goes and the payouts come from. */
	public static double[] bank() {
		return new double[] {PRINT_W / 2.0, WALL.bottom() + 6};
	}
}

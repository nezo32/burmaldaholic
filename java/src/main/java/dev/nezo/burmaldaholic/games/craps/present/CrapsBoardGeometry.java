package dev.nezo.burmaldaholic.games.craps.present;

import dev.nezo.burmaldaholic.core.ui.v2.Box;

/**
 * The in-world craps print (ui-v2.md §7.6, lane D): the same rows and fractions as the v2 screen's {@link CrapsLayout},
 * at the size of the generated texture {@code entity/craps/board_print.png} (256 × 168, {@code printGeometry()} of
 * {@code tools/assets/modules/tables/craps_v2.mjs} — keep the two in step), laid flat on the 2.0 × 1.4 felt with the
 * Pass line toward the players (+z). Converts print px to table-local block coordinates (the block spans 0..1, the
 * felt overhangs it). Pure.
 */
public final class CrapsBoardGeometry {
	public static final int PRINT_W = 256, PRINT_H = 168;
	/** Print size on the felt (blocks); centred on the block. */
	public static final float W = 1.84f, D = W * PRINT_H / PRINT_W;
	public static final float SCALE = W / PRINT_W;

	public static final Box DONT_COME, COME, FIELD, DONT_PASS, PASS;
	public static final Box[] POINTS = new Box[6];
	/** The field number columns' centres (px), 2 3 4 9 10 11 12. */
	public static final int[] FIELD_CX = new int[7];

	static {
		int m = 4;
		int gap = 3;
		int w = PRINT_W - 2 * m;
		int h = PRINT_H - 2 * m;
		int avail = h - 4 * gap;
		int placeH = (int) Math.round(avail * 0.28);
		int comeH = (int) Math.round(avail * 0.245);
		int fieldH = (int) Math.round(avail * 0.18);
		int dpH = (int) Math.round(avail * 0.105);
		int passH = avail - placeH - comeH - fieldH - dpH;
		int dcW = (int) Math.round(w * 0.17);
		int px0 = m + dcW + gap;
		int pw = m + w - px0;
		for (int i = 0; i < 6; i++) {
			int a = px0 + pw * i / 6;
			int b = px0 + pw * (i + 1) / 6;
			POINTS[i] = new Box(a, m, b - a - (i < 5 ? gap : 0), placeH);
		}
		int comeY = m + placeH + gap;
		int fieldY = comeY + comeH + gap;
		int dpY = fieldY + fieldH + gap;
		int passY = dpY + dpH + gap;
		DONT_COME = new Box(m, m, dcW, placeH + gap + comeH);
		COME = new Box(px0, comeY, pw, comeH);
		FIELD = new Box(m, fieldY, w, fieldH);
		DONT_PASS = new Box(m, dpY, w, dpH);
		PASS = new Box(m, passY, w, passH);
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
		return new double[] {a.x() + a.w() * fx, a.y() + a.h() * fy};
	}

	/** Puck centre (print px): ON on the point box's top-right corner, OFF in the Don't Come box. */
	public static double[] puck(int point) {
		if (point == 0) return new double[] {DONT_COME.right() - 10, DONT_COME.y() + 10};
		Box b = point(point);
		return new double[] {b.right() - 8, b.y() + 8};
	}

	/** Where the dice rest (print px): the COME row, centre-right; die 2 to the right of die 1. */
	public static double[] diceRest() {
		return new double[] {COME.x() + COME.w() * 0.55, COME.cy()};
	}
}

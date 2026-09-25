package dev.nezo.burmaldaholic.games.craps.logic;

import java.util.List;
import java.util.Optional;

/**
 * Geometry of the generated craps felt (docs/design/visual/tables.md §4.1; {@code crapsRects(g)} of
 * {@code tools/assets/modules/tables/craps.mjs}): every bet area, the chip spots, the puck and dice positions, in
 * image-local pixels. The screen hit-tests and draws with exactly these rects. Pure.
 *
 * @param w      layout width
 * @param wall   back wall height
 * @param gap    gap between rows
 * @param placeH point box height
 * @param dcW    Don't Come box width
 * @param comeH  Come row height
 * @param fieldH Field row height
 * @param dpH    Don't Pass bar height
 * @param passH  Pass line height
 * @param oddsH  odds lane height
 * @param big    big layout (×2 point digits)
 */
public record CrapsFelt(int w, int wall, int gap, int placeH, int dcW, int comeH, int fieldH, int dpH, int passH, int oddsH, boolean big) {
	public static final CrapsFelt BIG = new CrapsFelt(386, 6, 3, 36, 64, 32, 30, 16, 18, 8, true);
	public static final CrapsFelt COMPACT = new CrapsFelt(264, 4, 2, 26, 40, 22, 22, 12, 14, 6, false);
	public static final int[] POINTS = {4, 5, 6, 8, 9, 10};

	public record Rect(int x, int y, int w, int h) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}

		public int cx() {
			return x + w / 2;
		}

		public int cy() {
			return y + h / 2;
		}
	}

	/** A clickable bet area: {@code kind} = bet kind id, or "point" with {@code point}. */
	public record Area(String kind, int point, Rect rect) {}

	private int y0() {
		return wall + gap;
	}

	private int boxW() {
		return (w - dcW - 2) / 6;
	}

	public Rect wallRect() {
		return new Rect(0, 0, w, wall);
	}

	/** Point box of {@code point} (4, 5, 6, 8, 9, 10). */
	public Rect place(int point) {
		int k = indexOf(point);
		if (k < 0) {
			throw new IllegalArgumentException("not a point: " + point);
		}
		return new Rect(dcW + 2 + k * boxW(), y0(), boxW() - 1, placeH);
	}

	private static int indexOf(int point) {
		for (int i = 0; i < POINTS.length; i++) {
			if (POINTS[i] == point) {
				return i;
			}
		}
		return -1;
	}

	public Rect dontCome() {
		return new Rect(0, y0(), dcW, placeH + gap + comeH);
	}

	public Rect come() {
		return new Rect(dcW + 2, comeY(), w - dcW - 2, comeH);
	}

	private int comeY() {
		return y0() + placeH + gap;
	}

	private int fieldY() {
		return comeY() + comeH + gap;
	}

	public Rect field() {
		return new Rect(0, fieldY(), w, fieldH);
	}

	public Rect fieldLabel() {
		return new Rect(0, fieldY(), (int) Math.round(w * 0.22), fieldH);
	}

	private int dpY() {
		return fieldY() + fieldH + gap;
	}

	public Rect dontPass() {
		return new Rect(0, dpY(), w, dpH);
	}

	private int passY() {
		return dpY() + dpH + gap;
	}

	public Rect pass() {
		return new Rect(0, passY(), w, passH);
	}

	public Rect odds() {
		return new Rect(0, passY() + passH + 1, w, oddsH);
	}

	public int height() {
		return passY() + passH + 1 + oddsH;
	}

	/** Every clickable area (point boxes first). */
	public List<Area> areas() {
		return List.of(
			new Area("point", 4, place(4)), new Area("point", 5, place(5)), new Area("point", 6, place(6)),
			new Area("point", 8, place(8)), new Area("point", 9, place(9)), new Area("point", 10, place(10)),
			new Area("dont_come", 0, dontCome()), new Area("come", 0, come()), new Area("field", 0, field()),
			new Area("dont_pass", 0, dontPass()), new Area("pass", 0, pass()), new Area("pass", 0, odds()));
	}

	/** The area under image-local (x, y); the odds lane counts as the Pass line (odds are clicked on the line bet). */
	public Optional<Area> hit(double x, double y) {
		for (Area a : areas()) {
			if (a.rect().contains(x, y)) {
				return Optional.of(a);
			}
		}
		return Optional.empty();
	}

	// ---- chips, puck, dice -----------------------------------------------------------------------------------------

	/**
	 * Chip spot (the bottom-centre of a stack) of a flat bet: the right third of its area; a moved come / don't come bet
	 * sits on the chip ring of its point box. {@code slot} spreads several players' stacks (0 = yours).
	 */
	public int[] chipSpot(String kind, int point, int slot) {
		int dx = slot * (big ? 9 : 7);
		if (point != 0 && ("come".equals(kind) || "dont_come".equals(kind))) {
			Rect b = place(point);
			int x = b.cx() + (slot == 0 ? 0 : (slot % 2 == 1 ? 1 : -1) * ((slot + 1) / 2) * (big ? 9 : 7));
			return new int[] {x, b.y() + b.h() - (big ? 3 : 2)};
		}
		Rect r = switch (kind) {
			case "dont_come" -> dontCome();
			case "come" -> come();
			case "field" -> field();
			case "dont_pass" -> dontPass();
			default -> pass();
		};
		int x = r.x() + r.w() * 7 / 9 - dx;
		int y = "dont_come".equals(kind) ? r.y() + r.h() - (big ? 8 : 6) : r.y() + r.h() - (big ? 4 : 3);
		return new int[] {x, y};
	}

	/** Odds stack spot: in the odds lane directly below the flat bet ("behind the line"); come odds sit beside it. */
	public int[] oddsSpot(String kind, int point, int slot) {
		int[] flat = chipSpot(kind, point, slot);
		if ("pass".equals(kind) || "dont_pass".equals(kind)) {
			Rect o = odds();
			return new int[] {flat[0] + (big ? 8 : 6), o.y() + o.h()};
		}
		return new int[] {flat[0] + (big ? 8 : 6), flat[1]};
	}

	/** Puck spot (top-left of the 18² sprite): ON on the point box's top-right corner, OFF in the Don't Come box. */
	public int[] puck(int point) {
		if (point == 0) {
			Rect d = dontCome();
			return new int[] {d.x() + d.w() - 20, d.y() + 2};
		}
		Rect b = place(point);
		return new int[] {b.x() + b.w() - 17, b.y() - 3};
	}

	/** Rest region of die 1's centre (die 2 lands 22–28 px to its right): the Come area, left of the COME label. */
	public double[] restRegion() {
		Rect c = come();
		return big ? new double[] {c.x() + 40, c.y() + 14, c.x() + 70, c.y() + c.h() - 12}
			: new double[] {c.x() + 16, c.y() + 10, c.x() + 30, c.y() + c.h() - 9};
	}

	/** Where the dice are thrown from: the shooter's side (bottom right, the chip tray). */
	public double[] throwStart() {
		return new double[] {w - (big ? 40 : 28), height() - (big ? 6 : 4)};
	}
}

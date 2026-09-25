package dev.nezo.burmaldaholic.games.roulette.logic;

import java.util.List;
import java.util.Optional;

/**
 * Geometry of the clickable betting layout (UI.md §7), in layout-local pixels. Pure Java so that the
 * hit-testing (click a cell = straight, an edge = split, the outer edge of a row = street, an
 * intersection = corner, the outer intersection of two rows = six line, zero edges = split/trio/first
 * four) is unit-tested; the client screen only translates and draws.
 *
 * <pre>
 *   ┌──┬──┬──┬── … ──┬───┐
 *   │  │ 3│ 6│       │2:1│   visual row 0 = 3rd column
 *   │0 │ 2│ 5│       │2:1│   visual row 1 = 2nd column
 *   │  │ 1│ 4│       │2:1│   visual row 2 = 1st column
 *   └──┴──┴──┴── … ──┴───┘   ← street / six-line strip (outer edge)
 *      │ 1–12 │13–24│25–36│
 *      │1–18│EVEN│RED│BLACK│ODD│19–36│
 * </pre>
 */
public final class Layout {
	/** Zero cell width. */
	public static final int ZW = 20;
	/** Number cell size. */
	public static final int CW = 24;
	public static final int CH = 18;
	/** Edge tolerance for splits/corners/streets. */
	public static final int E = 4;
	/** Gap below the grid that belongs to the street strip. */
	public static final int GAP = 5;
	public static final int GRID_RIGHT = ZW + 12 * CW;
	public static final int GRID_BOTTOM = 3 * CH;
	public static final int DOZEN_Y = GRID_BOTTOM + GAP;
	public static final int EVEN_Y = DOZEN_Y + CH;
	public static final int WIDTH = GRID_RIGHT + CW;
	public static final int HEIGHT = EVEN_Y + CH;

	/** Order of the even-money boxes in the bottom row. */
	public static final List<BetType> EVEN_ROW = List.of(BetType.LOW, BetType.EVEN, BetType.RED, BetType.BLACK, BetType.ODD, BetType.HIGH);

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

	private Layout() {}

	/** Number at grid column {@code c} (0..11) and visual row {@code r} (0 = top). */
	public static int number(int c, int r) {
		return c * 3 + (3 - r);
	}

	public static int gridCol(int n) {
		return Wheel.layoutRow(n);
	}

	public static int visualRow(int n) {
		return 2 - Wheel.layoutCol(n);
	}

	/** Cell of a pocket (0 = the tall zero cell). */
	public static Rect cell(int n) {
		return LayoutGrid.LEGACY.cell(n);
	}

	/** Box of an outside bet. */
	public static Rect outsideBox(Spot spot) {
		return LayoutGrid.LEGACY.outsideBox(spot);
	}

	/** Where a chip on this spot is drawn (and a point that {@link #hit} maps back to it). */
	public static int[] center(Spot spot) {
		return LayoutGrid.LEGACY.center(spot);
	}

	/** The spot under layout-local point (x, y), if any ({@link LayoutGrid} has the same rules for the art sizes). */
	public static Optional<Spot> hit(double x, double y) {
		return LayoutGrid.LEGACY.hit(x, y);
	}
}

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
		if (n == 0) {
			return new Rect(0, 0, ZW, GRID_BOTTOM);
		}
		return new Rect(ZW + gridCol(n) * CW, visualRow(n) * CH, CW, CH);
	}

	/** Box of an outside bet. */
	public static Rect outsideBox(Spot spot) {
		return switch (spot.type()) {
			case DOZEN -> new Rect(ZW + (spot.outsideIndex() - 1) * 4 * CW, DOZEN_Y, 4 * CW, CH);
			case COLUMN -> new Rect(GRID_RIGHT, (3 - spot.outsideIndex()) * CH, CW, CH);
			default -> new Rect(ZW + EVEN_ROW.indexOf(spot.type()) * 2 * CW, EVEN_Y, 2 * CW, CH);
		};
	}

	/** Where a chip on this spot is drawn (and a point that {@link #hit} maps back to it). */
	public static int[] center(Spot spot) {
		List<Integer> n = spot.numbers();
		int a = n.get(0);
		return switch (spot.type()) {
			case STRAIGHT -> a == 0 ? new int[] {(ZW - E) / 2, GRID_BOTTOM / 2} : new int[] {cell(a).cx(), cell(a).cy()};
			case SPLIT -> {
				int b = n.get(1);
				if (a == 0) {
					yield new int[] {ZW, cell(b).cy()};
				}
				if (b - a == 3) {
					yield new int[] {ZW + (gridCol(a) + 1) * CW, cell(a).cy()};
				}
				yield new int[] {cell(a).cx(), visualRow(a) * CH};
			}
			case TRIO -> new int[] {ZW, n.contains(1) ? 2 * CH : CH};
			case FIRST_FOUR -> new int[] {ZW, GRID_BOTTOM};
			case CORNER -> new int[] {ZW + (gridCol(a) + 1) * CW, visualRow(a) * CH};
			case STREET -> new int[] {cell(a).cx(), GRID_BOTTOM};
			case SIX_LINE -> new int[] {ZW + (gridCol(a) + 1) * CW, GRID_BOTTOM};
			default -> new int[] {outsideBox(spot).cx(), outsideBox(spot).cy()};
		};
	}

	/** The spot under layout-local point (x, y), if any. */
	public static Optional<Spot> hit(double x, double y) {
		if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) {
			return Optional.empty();
		}
		// Outside boxes.
		if (y >= DOZEN_Y && y < EVEN_Y) {
			if (x < ZW || x >= GRID_RIGHT) {
				return Optional.empty();
			}
			int i = (int) ((x - ZW) / (4 * CW)) + 1;
			return Optional.of(Spot.of(BetType.DOZEN, Spot.outsideNumbers(BetType.DOZEN, i)));
		}
		if (y >= EVEN_Y) {
			if (x < ZW || x >= GRID_RIGHT) {
				return Optional.empty();
			}
			BetType t = EVEN_ROW.get((int) ((x - ZW) / (2 * CW)));
			return Optional.of(Spot.of(t, Spot.outsideNumbers(t, 0)));
		}
		if (x >= GRID_RIGHT) {
			if (y >= GRID_BOTTOM) {
				return Optional.empty();
			}
			int col = 3 - (int) (y / CH);
			return Optional.of(Spot.of(BetType.COLUMN, Spot.outsideNumbers(BetType.COLUMN, col)));
		}
		boolean streetStrip = y >= GRID_BOTTOM - E;
		// Zero and its edge.
		if (x < ZW - E) {
			return y < GRID_BOTTOM ? Optional.of(Spot.of(BetType.STRAIGHT, 0)) : Optional.empty();
		}
		if (x < ZW + E) {
			if (streetStrip) {
				return Optional.of(Spot.of(BetType.FIRST_FOUR, 0, 1, 2, 3));
			}
			if (Math.abs(y - CH) < E) {
				return Optional.of(Spot.of(BetType.TRIO, 0, 2, 3));
			}
			if (Math.abs(y - 2 * CH) < E) {
				return Optional.of(Spot.of(BetType.TRIO, 0, 1, 2));
			}
			return Optional.of(Spot.of(BetType.SPLIT, 0, number(0, (int) (y / CH))));
		}
		double gx = x - ZW;
		int c = Math.min(11, (int) (gx / CW));
		double fx = gx - c * CW;
		// Vertical boundary to the right of column `bc` (between bc and bc + 1), or -1.
		int bc = -1;
		if (fx < E && c > 0) {
			bc = c - 1;
		} else if (fx >= CW - E && c < 11) {
			bc = c;
		}
		if (streetStrip) {
			if (bc >= 0) {
				int a = number(bc, 2);
				return Optional.of(Spot.of(BetType.SIX_LINE, a, a + 1, a + 2, a + 3, a + 4, a + 5));
			}
			int a = number(c, 2);
			return Optional.of(Spot.of(BetType.STREET, a, a + 1, a + 2));
		}
		int r = Math.min(2, (int) (y / CH));
		double fy = y - r * CH;
		// Horizontal boundary below visual row `br` (between br and br + 1), or -1.
		int br = -1;
		if (fy < E && r > 0) {
			br = r - 1;
		} else if (fy >= CH - E && r < 2) {
			br = r;
		}
		if (bc >= 0 && br >= 0) {
			int a = number(bc, br + 1);
			return Optional.of(Spot.of(BetType.CORNER, a, a + 1, a + 3, a + 4));
		}
		if (bc >= 0) {
			int a = number(bc, r);
			return Optional.of(Spot.of(BetType.SPLIT, a, a + 3));
		}
		if (br >= 0) {
			int a = number(c, br + 1);
			return Optional.of(Spot.of(BetType.SPLIT, a, a + 1));
		}
		return Optional.of(Spot.of(BetType.STRAIGHT, number(c, r)));
	}
}

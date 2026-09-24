package dev.nezo.burmaldaholic.games.roulette.logic;

import java.util.List;
import java.util.Optional;

/**
 * Geometry + hit-testing of a roulette betting layout, parametrised by its cell sizes. {@link #BIG} and
 * {@link #COMPACT} are the generated felt art ({@code tools/assets/modules/tables/roulette.mjs} {@code LAYOUT_BIG} /
 * {@code LAYOUT_COMPACT}, docs/design/visual/tables.md §3.2): the screen hit-tests exactly the shapes the art draws.
 * {@link #LEGACY} is the pre-redesign geometry ({@link Layout}'s constants). Pure; see {@link Layout} for the rules
 * (cell = straight, edge = split, outer edge = street, intersection = corner / six line, zero edges = split, trio,
 * first four).
 *
 * @param zw     zero cell width
 * @param cw     number cell width
 * @param ch     number cell height
 * @param colW   column-bet (2:1) cell width
 * @param dozenH dozen row height
 * @param outH   even-money row height
 * @param gap    gap between the grid and the dozens (belongs to the street strip)
 * @param e      edge tolerance of splits / corners / streets
 */
public record LayoutGrid(int zw, int cw, int ch, int colW, int dozenH, int outH, int gap, int e) {
	/** Big felt layout 278 × 98 (GUI 427 × 240). */
	public static final LayoutGrid BIG = new LayoutGrid(18, 20, 22, 20, 16, 16, 0, 4);
	/** Compact felt layout 194 × 67 (GUI &lt; 400 × 240). */
	public static final LayoutGrid COMPACT = new LayoutGrid(12, 14, 15, 14, 11, 11, 0, 3);
	/** The pre-redesign layout (unit tests of {@link Layout}). */
	public static final LayoutGrid LEGACY = new LayoutGrid(20, 24, 18, 24, 18, 18, 5, 4);

	public int gridRight() {
		return zw + 12 * cw;
	}

	public int gridBottom() {
		return 3 * ch;
	}

	public int dozenY() {
		return gridBottom() + gap;
	}

	public int evenY() {
		return dozenY() + dozenH;
	}

	public int width() {
		return gridRight() + colW;
	}

	public int height() {
		return evenY() + outH;
	}

	private static int number(int c, int r) {
		return Layout.number(c, r);
	}

	private static int gridCol(int n) {
		return Wheel.layoutRow(n);
	}

	private static int visualRow(int n) {
		return 2 - Wheel.layoutCol(n);
	}

	/** Cell of a pocket (0 = the tall zero cell). */
	public Layout.Rect cell(int n) {
		if (n == 0) {
			return new Layout.Rect(0, 0, zw, gridBottom());
		}
		return new Layout.Rect(zw + gridCol(n) * cw, visualRow(n) * ch, cw, ch);
	}

	/** Box of an outside bet. */
	public Layout.Rect outsideBox(Spot spot) {
		return switch (spot.type()) {
			case DOZEN -> new Layout.Rect(zw + (spot.outsideIndex() - 1) * 4 * cw, dozenY(), 4 * cw, dozenH);
			case COLUMN -> new Layout.Rect(gridRight(), (3 - spot.outsideIndex()) * ch, colW, ch);
			default -> new Layout.Rect(zw + Layout.EVEN_ROW.indexOf(spot.type()) * 2 * cw, evenY(), 2 * cw, outH);
		};
	}

	/** Where a chip on this spot is drawn (the exact edge / intersection; {@link #hit} maps it back). */
	public int[] center(Spot spot) {
		List<Integer> n = spot.numbers();
		int a = n.get(0);
		return switch (spot.type()) {
			case STRAIGHT -> a == 0 ? new int[] {(zw - e) / 2, gridBottom() / 2} : new int[] {cell(a).cx(), cell(a).cy()};
			case SPLIT -> {
				int b = n.get(1);
				if (a == 0) {
					yield new int[] {zw, cell(b).cy()};
				}
				if (b - a == 3) {
					yield new int[] {zw + (gridCol(a) + 1) * cw, cell(a).cy()};
				}
				yield new int[] {cell(a).cx(), visualRow(a) * ch};
			}
			case TRIO -> new int[] {zw, n.contains(1) ? 2 * ch : ch};
			case FIRST_FOUR -> new int[] {zw, gridBottom()};
			case CORNER -> new int[] {zw + (gridCol(a) + 1) * cw, visualRow(a) * ch};
			case STREET -> new int[] {cell(a).cx(), gridBottom()};
			case SIX_LINE -> new int[] {zw + (gridCol(a) + 1) * cw, gridBottom()};
			default -> new int[] {outsideBox(spot).cx(), outsideBox(spot).cy()};
		};
	}

	/** The spot under layout-local point (x, y), if any. */
	public Optional<Spot> hit(double x, double y) {
		if (x < 0 || y < 0 || x >= width() || y >= height()) {
			return Optional.empty();
		}
		int gridRight = gridRight();
		int gridBottom = gridBottom();
		// The street strip: the outer edge of the bottom row (and the gap below it, or — without a gap — the top of the
		// dozen row, so a chip on the line is clickable from both sides).
		int streetEnd = gap > 0 ? dozenY() : gridBottom + e;
		if (y >= streetEnd && y < evenY()) {
			if (x < zw || x >= gridRight) {
				return Optional.empty();
			}
			int i = (int) ((x - zw) / (4 * cw)) + 1;
			return Optional.of(Spot.of(BetType.DOZEN, Spot.outsideNumbers(BetType.DOZEN, i)));
		}
		if (y >= evenY()) {
			if (x < zw || x >= gridRight) {
				return Optional.empty();
			}
			BetType t = Layout.EVEN_ROW.get((int) ((x - zw) / (2 * cw)));
			return Optional.of(Spot.of(t, Spot.outsideNumbers(t, 0)));
		}
		if (x >= gridRight) {
			if (y >= gridBottom) {
				return Optional.empty();
			}
			int col = 3 - (int) (y / ch);
			return Optional.of(Spot.of(BetType.COLUMN, Spot.outsideNumbers(BetType.COLUMN, col)));
		}
		boolean streetStrip = y >= gridBottom - e;
		if (x < zw - e) {
			return y < gridBottom ? Optional.of(Spot.of(BetType.STRAIGHT, 0)) : Optional.empty();
		}
		if (x < zw + e) {
			if (streetStrip) {
				return Optional.of(Spot.of(BetType.FIRST_FOUR, 0, 1, 2, 3));
			}
			if (Math.abs(y - ch) < e) {
				return Optional.of(Spot.of(BetType.TRIO, 0, 2, 3));
			}
			if (Math.abs(y - 2 * ch) < e) {
				return Optional.of(Spot.of(BetType.TRIO, 0, 1, 2));
			}
			return Optional.of(Spot.of(BetType.SPLIT, 0, number(0, (int) (y / ch))));
		}
		double gx = x - zw;
		int c = Math.min(11, (int) (gx / cw));
		double fx = gx - c * cw;
		int bc = -1;
		if (fx < e && c > 0) {
			bc = c - 1;
		} else if (fx >= cw - e && c < 11) {
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
		int r = Math.min(2, (int) (y / ch));
		double fy = y - r * ch;
		int br = -1;
		if (fy < e && r > 0) {
			br = r - 1;
		} else if (fy >= ch - e && r < 2) {
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

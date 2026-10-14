package dev.nezo.burmaldaholic.games.roulette.present;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import dev.nezo.burmaldaholic.games.poker.present.LabelPlacer;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Layout;
import dev.nezo.burmaldaholic.games.roulette.logic.LayoutGrid;
import dev.nezo.burmaldaholic.games.roulette.logic.Racetrack;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The PlayArea of the roulette v2 screen (REDESIGN.md §7.5, docs/architecture/ui-v2.md §2.2, §3): the wheel column on
 * the left (wheel + "Last numbers" pills), the betting layout (zero, 12 × 3 numbers, 2:1 column, dozens, even-money
 * row) and the call-bet row (Tiers / Orphelins / Voisins / Zero) under it. Pure: every box the screen draws and every
 * spot it hit-tests come from here, so what is drawn is exactly what is clicked at every GUI size.
 *
 * <p>Hit-testing reuses {@link LayoutGrid} (cell = straight, edge = split, outer edge = street, intersection = corner /
 * six line, zero edges = split / trio / first four) with the cell sizes computed for the size. Chip stacks are snapped
 * OFF the printed numbers (§2.2): a straight-up or a vertical split sits in the lower part of the cell, line bets on
 * their line, outside bets at the right end of their box, so every number stays readable; the unit test checks every
 * spot of the layout against every number glyph box at every size.
 */
public final class RouletteLayout {
	/** Wheel sprite diameters (generated: the mini wheel 72, {@code wheel_bowl_m} 104 / 144). */
	public static final int[] WHEELS = {72, 104, 144};
	public static final int PILL_W = 23, PILL_H = 13, PILL_PITCH_X = 26, PILL_PITCH_Y = 15;
	/** Glyph height of the 1× vanilla font (digits). */
	public static final int GLYPH_H = 8;
	/** Each disc of a felt stack rises this many px; at most {@link #MAX_DISCS} discs are drawn. */
	public static final int DISC_RISE = 1, MAX_DISCS = 4;

	public final TableGrid grid;
	public final Tier tier;
	/** Wheel column, wheel box (diameter = {@link #wheelD}), "Last numbers" caption box, history pill boxes (newest first). */
	public final Box column, wheel, historyLabel;
	public final int wheelD;
	public final List<Box> pills;
	/** The cell geometry relative to {@link #gx}, {@link #gy} (top-left corner of the zero cell). */
	public final LayoutGrid cells;
	public final int gx, gy;
	/** Whole betting layout (zero .. 2:1, numbers .. even-money row). */
	public final Box layout;
	/** Call bets in {@link Racetrack.Section} order. */
	public final List<Box> calls;
	/** Felt chip disc size (px), the px each disc of a stack rises and the gap under a cell's stack. */
	public final int chip, rise, pad;
	/** The 37 number cells (zero + 12 × 3): never under the ResultBanner (the winning cell stays visible). */
	public final Box numbers;

	private RouletteLayout(TableGrid g) {
		this.grid = g;
		this.tier = g.tier;
		int m = g.m();
		Box play = g.play;
		int w = play.w();
		int ph = play.h();
		int oh = switch (tier) {
			case T1 -> 14;
			case T2 -> 17;
			case T3 -> 22;
		};
		int callGap = tier == Tier.T1 ? 6 : 8;
		int top = play.y() + (tier == Tier.T1 ? 8 : 10);
		int colW = (int) Math.round(w * (tier == Tier.T3 ? 0.26 : 0.25));
		int cap = Math.max(switch (tier) {
			case T1 -> 21;
			case T2 -> 23;
			case T3 -> 30;
		}, Math.min(40, (int) (ph * 0.11)));
		int lx = m + colW + 6;
		int cw = Math.max(8, Math.min(cap, (w - m - lx - 4) / 14));
		int chMax = (ph - (top - play.y()) - 2 * oh - (oh + callGap) - 8) / 3;
		// tall enough for a stack under the printed number (§2.2): ceil(ch / 2) ≥ glyph half + pad + chip + rise
		int chNeed = 2 * (GLYPH_H / 2 + 2 + tier.feltChip + (MAX_DISCS - 1) * DISC_RISE) - 1;
		int ch = Math.max(12, Math.min(Math.max((int) (1.8 * cw), chNeed), chMax));
		// a short cell (small windows) gives up the disc rise, then the pad, then 2 px of chip
		int[][] fits = {{2, 1, 0}, {1, 1, 0}, {1, 0, 0}, {1, 0, 2}, {1, 0, 4}};
		int[] pick = fits[fits.length - 1];
		for (int[] f : fits) {
			if ((ch + 1) / 2 >= GLYPH_H / 2 + f[0] + tier.feltChip - f[2] + (MAX_DISCS - 1) * f[1]) {
				pick = f;
				break;
			}
		}
		this.pad = pick[0];
		this.rise = pick[1];
		this.chip = tier.feltChip - pick[2];
		int e = Math.max(3, cw / 5);
		this.cells = new LayoutGrid(cw, cw, ch, cw, oh, oh, 0, e);
		// the layout is centred in the space right of the wheel column
		int free = w - m - lx;
		this.gx = lx + Math.max(0, (free - cells.width()) / 2);
		this.gy = top;
		this.layout = new Box(gx, gy, cells.width(), cells.height());
		// call-bet row under the layout, spanning it
		int cy = layout.bottom() + callGap;
		int gap = tier == Tier.T1 ? 4 : 6;
		int bw = (layout.w() - 3 * gap) / 4;
		List<Box> c = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			int x = layout.x() + i * (bw + gap);
			int right = i == 3 ? layout.right() : x + bw;
			c.add(new Box(x, cy, right - x, oh));
		}
		this.calls = List.copyOf(c);
		// the wheel column
		this.column = new Box(m, play.y(), colW, ph);
		int maxD = Math.min(colW - 8, ph - 64);
		int d = WHEELS[0];
		for (int s : WHEELS) {
			if (s <= maxD) d = s;
		}
		this.wheelD = d;
		this.wheel = new Box(m + (colW - d) / 2, top, d, d);
		int perRow = Math.max(1, Math.min(4, (colW + 3) / PILL_PITCH_X));
		int pillsW = perRow * PILL_PITCH_X - 3;
		int px = m + (colW - pillsW) / 2;
		int ly = wheel.bottom() + (tier == Tier.T1 ? 4 : 8);
		this.historyLabel = new Box(px, ly, pillsW, 9);
		List<Box> p = new ArrayList<>();
		int py = ly + 12;
		for (int row = 0; row < 2; row++) {
			int y = py + row * PILL_PITCH_Y;
			if (y + PILL_H > play.bottom() - 2) break;
			for (int i = 0; i < perRow; i++) {
				p.add(new Box(px + i * PILL_PITCH_X, y, PILL_W, PILL_H));
			}
		}
		this.pills = List.copyOf(p);
		this.numbers = new Box(gx, gy, cells.gridRight(), cells.gridBottom());
	}

	public static RouletteLayout of(TableGrid g) {
		return new RouletteLayout(g);
	}

	public static RouletteLayout of(int w, int h) {
		return of(TableGrid.of(w, h));
	}

	// ---- geometry --------------------------------------------------------------------------------------------------

	private Box abs(Layout.Rect r) {
		return new Box(gx + r.x(), gy + r.y(), r.w(), r.h());
	}

	/** Cell of a pocket (0 = the tall zero cell). */
	public Box cell(int n) {
		return abs(cells.cell(n));
	}

	/** Box of an outside bet (dozen, column, even-money). */
	public Box outside(Spot s) {
		return abs(cells.outsideBox(s));
	}

	/** The box that lights for a spot: its cell (straight), its outside box, else the hull of its cells. */
	public Box area(Spot s) {
		if (!s.type().inside()) {
			return outside(s);
		}
		Box b = null;
		for (int n : s.numbers()) {
			Box c = cell(n);
			b = b == null ? c : hull(b, c);
		}
		return b;
	}

	private static Box hull(Box a, Box b) {
		int x = Math.min(a.x(), b.x());
		int y = Math.min(a.y(), b.y());
		return new Box(x, y, Math.max(a.right(), b.right()) - x, Math.max(a.bottom(), b.bottom()) - y);
	}

	/** The printed number of a pocket: its 1× glyph box (an obstacle for stacks and tags). */
	public Box glyph(int n) {
		Box c = cell(n);
		int gw = String.valueOf(n).length() * 6 - 1;
		return new Box(c.cx() - gw / 2, c.cy() - GLYPH_H / 2, gw, GLYPH_H);
	}

	/** Every number glyph box (0..36). */
	public List<Box> glyphBoxes() {
		return glyphs();
	}

	/**
	 * Where the ResultBanner may go, in preference order (R-CDE M4, DES roulette P1): over the call-bet row and the
	 * even-money row (the band from the even-money row to the felt's bottom), then the whole outside-bet band under the
	 * numbers, then the wheel column, then anywhere on the felt clear of {@link #bannerObstacles}.
	 */
	public List<Box> bannerRegions() {
		int bottom = grid.play.bottom() - 2;
		int evenTop = gy + cells.evenY();
		int outsideTop = gy + cells.dozenY();
		List<Box> out = new ArrayList<>(4);
		out.add(new Box(layout.x(), evenTop, layout.w(), Math.max(0, bottom - evenTop)));
		out.add(new Box(layout.x(), outsideTop, layout.w(), Math.max(0, bottom - outsideTop)));
		out.add(new Box(column.x(), wheel.y(), column.w(), Math.max(0, bottom - wheel.y())));
		out.add(grid.play);
		return out;
	}

	/**
	 * What the ResultBanner must never cover: the 37 number cells (so the winning cell is always visible), every printed
	 * number glyph, the wheel and the "Last numbers" caption + history pills.
	 */
	public List<Box> bannerObstacles() {
		List<Box> out = new ArrayList<>(48);
		out.add(numbers);
		out.addAll(glyphs());
		out.add(wheel);
		out.add(historyLabel);
		out.addAll(pills);
		return out;
	}

	/** Every number glyph box (0..36). */
	public List<Box> glyphs() {
		List<Box> out = new ArrayList<>(37);
		for (int n = 0; n <= 36; n++) {
			out.add(glyph(n));
		}
		return out;
	}

	/** Lower anchor y inside a cell: the bottom disc ends 2 px above the cell's bottom line. */
	private int lowY(Box cell) {
		return cell.bottom() - pad - (chip + 1) / 2;
	}

	/**
	 * Centre of the bottom disc of the stack on a spot (layout px). It is also a point {@link #hit} maps back to the
	 * spot (unit-tested), so the HitMap box of a spot is centred on it.
	 */
	public int[] anchor(Spot s) {
		List<Integer> n = s.numbers();
		int a = n.get(0);
		int cw = cells.cw();
		int ch = cells.ch();
		int zw = cells.zw();
		return switch (s.type()) {
			case STRAIGHT -> {
				Box c = cell(a);
				yield a == 0 ? new int[] {gx + (zw - cells.e()) / 2, lowY(c)} : new int[] {c.cx(), lowY(c)};
			}
			case SPLIT -> {
				int b = n.get(1);
				if (a == 0) {
					yield new int[] {gx + zw, lowY(cell(b))};
				}
				if (b - a == 3) {
					yield new int[] {gx + zw + (Layout.gridCol(a) + 1) * cw, lowY(cell(a))};
				}
				yield new int[] {cell(a).cx(), gy + Layout.visualRow(a) * ch};
			}
			case TRIO -> new int[] {gx + zw, gy + (n.contains(1) ? 2 * ch : ch)};
			case FIRST_FOUR -> new int[] {gx + zw, gy + 3 * ch};
			case CORNER -> new int[] {gx + zw + (Layout.gridCol(a) + 1) * cw, gy + Layout.visualRow(a) * ch};
			case STREET -> new int[] {cell(a).cx(), gy + 3 * ch};
			case SIX_LINE -> new int[] {gx + zw + (Layout.gridCol(a) + 1) * cw, gy + 3 * ch};
			case COLUMN -> {
				Box o = outside(s);
				yield new int[] {o.cx(), lowY(o)};
			}
			case RED, BLACK -> new int[] {outside(s).cx(), outside(s).cy()};
			default -> {
				Box o = outside(s);
				yield new int[] {o.right() - chip / 2 - 3, o.cy()};
			}
		};
	}

	/** Disc box of a stack of {@code discs} discs on a spot (the bottom disc at the anchor, each one {@link #DISC_RISE} up). */
	public Box stack(Spot s, int discs) {
		int[] p = anchor(s);
		int up = Math.max(0, Math.min(MAX_DISCS, discs) - 1) * rise;
		return new Box(p[0] - chip / 2, p[1] - chip / 2 - up, chip, chip + up);
	}

	/** The spot under a layout-px point, if any. */
	public Optional<Spot> hit(double x, double y) {
		return cells.hit(x - gx, y - gy);
	}

	/** The call bet under a point, if any. */
	public Optional<Racetrack.Section> callAt(double x, double y) {
		for (int i = 0; i < calls.size(); i++) {
			if (calls.get(i).contains(x, y)) return Optional.of(Racetrack.Section.values()[i]);
		}
		return Optional.empty();
	}

	/** HitMap id of a felt spot ({@code felt.split:17-20}, the kit's {@code felt.<id>} convention). */
	public static String spotId(Spot s) {
		return "felt." + s.key();
	}

	/** HitMap id of a call bet ({@code felt.call.voisins}). */
	public static String callId(Racetrack.Section s) {
		return "felt.call." + s.id();
	}

	/**
	 * The click box of a spot for the HitMap (and the real-flow tests): the cell for straights and outside bets, a small
	 * box centred on the anchor for line bets. Its centre always hits the spot.
	 */
	public Box target(Spot s) {
		if (s.type() == BetType.STRAIGHT || !s.type().inside()) {
			Box a = s.type() == BetType.STRAIGHT ? cell(s.numbers().get(0)) : outside(s);
			int[] p = anchor(s);
			// centred on the anchor (which is inside the area), clipped to it
			int hw = Math.min(p[0] - a.x(), a.right() - p[0]);
			int hh = Math.min(p[1] - a.y(), a.bottom() - p[1]);
			return new Box(p[0] - hw, p[1] - hh, Math.max(1, 2 * hw), Math.max(1, 2 * hh));
		}
		int[] p = anchor(s);
		int e = cells.e();
		return new Box(p[0] - e + 1, p[1] - e + 1, 2 * e - 2, 2 * e - 2);
	}

	/** Every spot of the European layout, cells first then line bets (HitMap order: line bets on top). */
	public static List<Spot> allSpots() {
		List<Spot> out = new ArrayList<>();
		for (BetType t : List.of(BetType.STRAIGHT, BetType.COLUMN, BetType.DOZEN, BetType.LOW, BetType.EVEN, BetType.RED, BetType.BLACK, BetType.ODD,
				BetType.HIGH, BetType.SPLIT, BetType.STREET, BetType.TRIO, BetType.FIRST_FOUR, BetType.SIX_LINE, BetType.CORNER)) {
			out.addAll(Spot.all(t));
		}
		return out;
	}

	/**
	 * A value tag ({@code w × h}) for the stack on {@code s}: above-right, above-left, right, left of the stack, clear of
	 * every number glyph and of {@code avoid} (other stacks, the other tag). Empty when nothing is free (the status line
	 * then carries the readout, §2.2).
	 */
	public Optional<Box> tag(Spot s, int discs, int w, int h, List<Box> avoid) {
		Box st = stack(s, discs);
		List<LabelPlacer.Rect> hard = new ArrayList<>();
		for (Box g : glyphs()) {
			hard.add(rect(g));
		}
		for (Box a : avoid) {
			hard.add(rect(a));
		}
		Box b = grid.play;
		int[] xy = {st.right() - 2, st.y() - h + 2, st.x() - w + 2, st.y() - h + 2, st.right() + 1, st.cy() - h / 2, st.x() - w - 1, st.cy() - h / 2,
			st.cx() - w / 2, st.y() - h - 1, st.cx() - w / 2, st.bottom() + 1};
		for (int i = 0; i + 1 < xy.length; i += 2) {
			LabelPlacer.Rect c = new LabelPlacer.Rect(xy[i], xy[i + 1], w, h);
			if (!c.inside(rect(b)) || c.intersects(rect(st))) continue;
			boolean free = true;
			for (LabelPlacer.Rect o : hard) {
				if (c.intersects(o)) {
					free = false;
					break;
				}
			}
			if (free) return Optional.of(new Box(c.x(), c.y(), w, h));
		}
		return Optional.empty();
	}

	private static LabelPlacer.Rect rect(Box b) {
		return new LabelPlacer.Rect(b.x(), b.y(), b.w(), b.h());
	}
}

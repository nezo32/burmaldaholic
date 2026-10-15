package dev.nezo.burmaldaholic.games.craps.present;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The craps felt of the v2 table screen (REDESIGN.md §7.6, mockup {@code v2_craps_point_480.png}; ui-v2.md §3): the
 * layout fills the PlayArea minus the margin. Rows top to bottom: the place row ({@code DON'T COME} box on the left
 * spanning into the COME row, six point boxes with 2× / 3× numbers), the COME row with the dice, the total pill and the
 * {@code point} caption, the FIELD row (2× word, numbers, 2 and 12 with their multipliers), the DON'T PASS BAR and the
 * PASS LINE (gold edge) with its odds lane on the right.
 *
 * <p>Every bet area is a {@link Box} in layout px; the screen draws and hit-tests with exactly these boxes (ids
 * {@code felt.<id>}); chip stacks, the puck, the dice and the banner region are placed here as well so the pure
 * layout test can audit every size of ui-v2.md §11.1 (nothing outside the felt, stacks never on a printed number,
 * stacks never overlapping each other). Pure.
 */
public final class CrapsLayout {
	public static final int[] POINTS = {4, 5, 6, 8, 9, 10};
	/** Hit ids of the bet areas (the felt spots of the HitMap, ui-v2.md §6). */
	public static final String DONT_COME = "felt.dont_come", COME = "felt.come", FIELD = "felt.field", DONT_PASS = "felt.dont_pass",
		PASS = "felt.pass", PASS_ODDS = "felt.pass_odds";

	/** Every felt spot id (point boxes, Don't Come, Come, Field, Don't Pass, Pass, the odds lane). */
	public static final List<String> SPOT_IDS = List.of("felt.point.4", "felt.point.5", "felt.point.6", "felt.point.8", "felt.point.9",
		"felt.point.10", DONT_COME, COME, FIELD, DONT_PASS, PASS, PASS_ODDS);

	public static String pointId(int point) {
		return "felt.point." + point;
	}

	public final TableGrid grid;
	public final Tier tier;
	/** The whole printed layout. */
	public final Box felt;
	public final Box dontCome;
	/** Point boxes in {@link #POINTS} order. */
	public final Box[] points = new Box[POINTS.length];
	/** Glyph box of each point number (an obstacle for chip stacks). */
	public final Box[] pointGlyphs = new Box[POINTS.length];
	public final Box come;
	public final Box comeWord;
	public final Box field;
	public final Box fieldWord;
	/** Field numbers 2, 3, 4, 9, 10, 11, 12: glyph boxes (with the x2 / x3 line for 2 and 12). */
	public final int[] fieldNumbers = {2, 3, 4, 9, 10, 11, 12};
	public final Box[] fieldGlyphs = new Box[7];
	public final Box dontPass;
	public final Box dontPassWord;
	public final Box pass;
	public final Box passWord;
	/** The odds lane at the right of the Pass line (click = odds behind the Pass bet). */
	public final Box passOdds;
	public final Box oddsWord;
	/** The total pill and the {@code point} caption: fixed boxes right of the dice zone (the readout never moves). */
	public final Box total, pointCaption;
	/**
	 * CM (CS §4.1): the rubber-pyramid back wall along the felt's top edge (the dice's wall plane is its bottom), the
	 * zone the resting dice CENTRES lie in (COME row from {@code come.x + 44 %} to {@code total.x − 4}; no slots, no
	 * snap) and dice home at either end of the Pass line (two dice wide: where the stick returns them and the throw
	 * starts).
	 */
	public final Box backWall, diceZone, diceHomeR, diceHomeL;
	/** Die cell (atlas) px: 14 / 18 / 22 at T1 / T2 / T3, 30 when ROOMY. */
	public final int die;
	/** Stick shaft width (px): 3 / 4 / 5 / 6. */
	public final int stick;
	/** The playing area under the wall (arena y 0 = {@code backWall.bottom()}, y = ARENA_H = {@code felt.bottom()}). */
	public final Box arena;
	/** Number scale of the point boxes (2 / 3) and of the field word. */
	public final int pointScale, wordScale;
	/** Chip disc size on the felt and the puck size. */
	public final int chip, puck;
	public final int gap;

	private CrapsLayout(TableGrid g, int reserveBottom) {
		this.grid = g;
		this.tier = g.tier;
		int m = tier.margin;
		gap = tier == Tier.T1 ? 2 : 3;
		chip = tier.feltChip;
		boolean roomy = g.roomy();
		puck = roomy ? 26 : tier == Tier.T1 ? 12 : tier == Tier.T2 ? 16 : 20;
		die = roomy ? 30 : tier == Tier.T1 ? 14 : tier == Tier.T2 ? 18 : 22;
		stick = roomy ? 6 : tier == Tier.T1 ? 3 : tier == Tier.T2 ? 4 : 5;
		int wallH = roomy ? CrapsFeltFractions.WALL_ROOMY : tier == Tier.T1 ? CrapsFeltFractions.WALL_T1
			: tier == Tier.T2 ? CrapsFeltFractions.WALL_T2 : CrapsFeltFractions.WALL_T3;
		int x0 = g.play.x() + m + 4;
		int y0 = g.play.y() + m + 2;
		int w = g.play.w() - 2 * (m + 4);
		// T-D2 V5: the Dock's hint strip (status line 2 when the status box is narrow) sits above the Dock inside the
		// PlayArea: the felt ends above it
		int h = g.play.h() - (m + 2) - Math.max(m + 6, reserveBottom + 2);
		felt = new Box(x0, y0, w, h);
		backWall = new Box(x0, y0, w, wallH);
		arena = new Box(x0, backWall.bottom(), w, felt.bottom() - backWall.bottom());
		// the rows start under the wall
		y0 = backWall.bottom() + gap;
		h = felt.bottom() - y0;
		int avail = h - 4 * gap;
		int placeH = (int) Math.round(avail * CrapsFeltFractions.PLACE);
		int comeH = (int) Math.round(avail * CrapsFeltFractions.COME);
		int fieldH = (int) Math.round(avail * CrapsFeltFractions.FIELD);
		int dpH = (int) Math.round(avail * CrapsFeltFractions.DONT_PASS);
		int passH = avail - placeH - comeH - fieldH - dpH;
		int dcW = (int) Math.round(w * CrapsFeltFractions.DONT_COME_W);
		dontCome = new Box(x0, y0, dcW, placeH + gap + comeH);
		int px0 = x0 + dcW + gap;
		int pw = x0 + w - px0;
		int boxW = pw / POINTS.length - gap;
		// 2× numbers (3× on T3); a narrow box (4:3 windows) keeps room for the puck right of "10"
		int ps = tier == Tier.T3 ? 3 : 2;
		while (ps > 1 && boxW - 11 * ps < 2 * (12 + 4)) ps--;
		pointScale = ps;
		wordScale = 2;
		for (int i = 0; i < POINTS.length; i++) {
			int a = px0 + pw * i / POINTS.length;
			int b = px0 + pw * (i + 1) / POINTS.length;
			points[i] = new Box(a, y0, b - a - (i < POINTS.length - 1 ? gap : 0), placeH);
			int digits = POINTS[i] >= 10 ? 2 : 1;
			int gw = (digits * 6 - 1) * pointScale;
			int gh = 7 * pointScale;
			pointGlyphs[i] = new Box(points[i].cx() - gw / 2, y0 + Math.max(3, placeH / 8), gw, gh);
		}
		int comeY = y0 + placeH + gap;
		come = new Box(px0, comeY, pw, comeH);
		comeWord = new Box(come.x() + come.w() * 6 / 100, come.cy() - 7, Math.max(40, come.w() * 26 / 100), 14);
		// the dice zone and the fixed total pill: the centre-right of the COME row (CS §4.1)
		int tw = roomy ? 34 : tier == Tier.T1 ? 18 : tier == Tier.T2 ? 22 : 28;
		int tx = come.x() + (int) Math.round(come.w() * CrapsFeltFractions.TOTAL_AT);
		total = new Box(tx, come.cy() - tw / 2 - 1, tw, tw + 2);
		pointCaption = new Box(total.right() + 4, come.cy() - 4, 32, 9);
		int zx0 = come.x() + (int) Math.round(come.w() * CrapsFeltFractions.ZONE_FROM) + die / 2;
		int zx1 = total.x() - 4 - die / 2;
		int zy0 = come.y() + die / 2 + 1;
		int zy1 = come.bottom() - die / 2 - 1;
		diceZone = new Box(zx0, zy0, Math.max(1, zx1 - zx0), Math.max(1, zy1 - zy0));
		int fieldY = comeY + comeH + gap;
		field = new Box(x0, fieldY, w, fieldH);
		int fw = (int) Math.round(w * 0.2);
		fieldWord = new Box(x0 + 4, field.cy() - 8, fw - 8, 16);
		int nx0 = x0 + fw;
		int nw = field.right() - 6 - nx0;
		for (int i = 0; i < fieldNumbers.length; i++) {
			int cx = nx0 + nw * (2 * i + 1) / (2 * fieldNumbers.length);
			int digits = fieldNumbers[i] >= 10 ? 2 : 1;
			int gw = digits * 6 - 1;
			boolean mult = fieldNumbers[i] == 2 || fieldNumbers[i] == 12;
			int gh = mult ? 18 : 8;
			if (mult) gw = Math.max(gw, 11);
			fieldGlyphs[i] = new Box(cx - gw / 2, field.y() + 3, gw, gh);
		}
		int dpY = fieldY + fieldH + gap;
		dontPass = new Box(x0, dpY, w, dpH);
		dontPassWord = new Box(dontPass.cx() - w * 10 / 100, dontPass.cy() - 4, w * 20 / 100, 8);
		int passY = dpY + dpH + gap;
		pass = new Box(x0, passY, w, passH);
		passWord = new Box(pass.cx() - w * 8 / 100, pass.cy() - 4, w * 16 / 100, 8);
		int ox = pass.x() + w * 70 / 100;
		passOdds = new Box(ox, pass.y(), pass.right() - ox, pass.h());
		int homeW = 2 * die + 2;
		int ow = Math.min(passOdds.w() - w * 9 / 100 - 4, w * 12 / 100);
		ow = Math.min(ow, pass.right() - 2 - homeW - 2 - (passOdds.x() + w * 9 / 100));
		oddsWord = new Box(passOdds.x() + w * 9 / 100, pass.cy() - 4, Math.max(1, ow), 8);
		int homeH = Math.min(pass.h() - 2, die + 2);
		int hy = pass.cy() - homeH / 2;
		diceHomeR = new Box(pass.right() - 2 - homeW, hy, homeW, homeH);
		diceHomeL = new Box(pass.x() + 2, hy, homeW, homeH);
	}

	/** Dice home of a shooter side ({@link CrapsThrow#LEFT} / {@link CrapsThrow#RIGHT}). */
	public Box diceHome(int side) {
		return side == CrapsThrow.LEFT ? diceHomeL : diceHomeR;
	}

	/** Layout px per tray unit along x / y. */
	public double sx() {
		return arena.w() / CrapsFeltFractions.ARENA_W;
	}

	public double sy() {
		return arena.h() / CrapsFeltFractions.ARENA_H;
	}

	/** Lift (layout px) per tray unit of height: a little under the floor scale so a hop into the wall stays on the wall. */
	public static final double LIFT = 0.62;

	/**
	 * The arena → layout map (CS §2.2 ArenaMap): x across the felt, y from the wall plane ({@code backWall.bottom()})
	 * to the shooter's rail ({@code felt.bottom()}), height lifting the sprite.
	 */
	public dev.nezo.burmaldaholic.core.anim.motion.StageMap arenaMap() {
		return dev.nezo.burmaldaholic.core.anim.motion.StageMap.of(arena.x(), arena.y(), sx(), sy(), sy() * LIFT);
	}

	public static CrapsLayout of(TableGrid g) {
		return new CrapsLayout(g, 0);
	}

	/** The layout with {@code reserveBottom} px kept free above the Dock (the Dock's hint strip). */
	public static CrapsLayout of(TableGrid g, int reserveBottom) {
		return new CrapsLayout(g, Math.max(0, reserveBottom));
	}

	/**
	 * Where the value pill ({@code w} × 11) of one of your stacks goes (T-D2 V1): above-right of its top disc, else
	 * above-left, then centred higher, then beside it — inside the felt, off every printed glyph, the dice zone, the
	 * total / caption and the pills already placed ({@code placed}, appended to).
	 */
	public Box valueTag(Box stack, int discs, int w, List<Box> placed) {
		int top = stack.bottom() - stack.w() - Math.min(MAX_DISCS - 1, Math.max(0, discs - 1)) - 1;
		Box[] tries = {new Box(stack.right() - 2, top - 9, w, 11), new Box(stack.x() - w + 2, top - 9, w, 11), new Box(stack.cx() - w / 2, top - 12, w, 11),
			new Box(stack.right() + 1, stack.bottom() - 11, w, 11), new Box(stack.x() - w - 1, stack.bottom() - 11, w, 11)};
		List<Box> avoid = new ArrayList<>(glyphs());
		avoid.add(diceZone.inset(-die / 2));
		avoid.add(total);
		avoid.add(pointCaption);
		Box pick = null;
		for (Box c : tries) {
			Box in = c;
			if (in.right() > felt.right() - 1) in = in.offset(felt.right() - 1 - in.right(), 0);
			if (in.x() < felt.x() + 1) in = in.offset(felt.x() + 1 - in.x(), 0);
			if (in.y() < felt.y() + 1) in = in.offset(0, felt.y() + 1 - in.y());
			boolean clear = true;
			for (Box o : avoid) if (o.intersects(in)) clear = false;
			for (Box o : placed) if (o.intersects(in)) clear = false;
			if (clear) {
				pick = in;
				break;
			}
		}
		if (pick == null) {
			pick = tries[0];
			for (Box o : placed) if (o.intersects(pick)) pick = pick.offset(0, o.y() - pick.bottom() - 1);
		}
		placed.add(pick);
		return pick;
	}

	public Box point(int point) {
		for (int i = 0; i < POINTS.length; i++) {
			if (POINTS[i] == point) return points[i];
		}
		throw new IllegalArgumentException("not a point: " + point);
	}

	public Box pointGlyph(int point) {
		for (int i = 0; i < POINTS.length; i++) {
			if (POINTS[i] == point) return pointGlyphs[i];
		}
		throw new IllegalArgumentException("not a point: " + point);
	}

	/** Every bet area by hit id, in hit-map order (point boxes first, the odds lane last so it wins over the Pass line). */
	public Map<String, Box> spots() {
		Map<String, Box> out = new LinkedHashMap<>();
		for (int i = 0; i < POINTS.length; i++) out.put(pointId(POINTS[i]), points[i]);
		out.put(DONT_COME, dontCome);
		out.put(COME, come);
		out.put(FIELD, field);
		out.put(DONT_PASS, dontPass);
		out.put(PASS, pass);
		out.put(PASS_ODDS, passOdds);
		return out;
	}

	/** The bet-kind id of a hit id ({@code pass}, {@code point}, …; the odds lane is {@code pass_odds}). */
	public static String kindOf(String spotId) {
		String s = spotId.startsWith("felt.") ? spotId.substring(5) : spotId;
		return s.startsWith("point.") ? "point" : s;
	}

	/** The point of a {@code spot.point.N} id, else 0. */
	public static int pointOf(String spotId) {
		return spotId.startsWith("felt.point.") ? Integer.parseInt(spotId.substring("felt.point.".length())) : 0;
	}

	// ---- chips --------------------------------------------------------------------------------------------------------

	/** Where a chip stack sits. {@code odds}: the odds stack behind it; {@code others}: the other players' stack. */
	public enum Layer {
		MINE, ODDS, OTHERS
	}

	/**
	 * Box of the top disc of a stack (the stack grows UP from {@code bottom - chip}; up to 4 discs, {@link #STACK_STEP} px
	 * each) for a bet of kind {@code kind} ({@code pass}, {@code dont_pass}, {@code come}, {@code dont_come},
	 * {@code field}) on {@code point} (come / don't come bets that travelled; 0 = on their own area).
	 * The returned box is the whole stack's footprint at its tallest (4 discs).
	 */
	public Box stack(String kind, int point, Layer layer) {
		int c = chip;
		int sh = stackH();
		int step = c + 3;
		int cx;
		int bottom;
		if (point != 0 && ("come".equals(kind) || "dont_come".equals(kind))) {
			Box b = point(point);
			bottom = b.bottom() - 2;
			if (layer == Layer.OTHERS) {
				// everybody else's come / don't come chips on this number: one stack in the top-left corner
				cx = b.x() + c / 2 + 2;
				bottom = Math.min(b.bottom() - 2, b.y() + 2 + sh);
				return clampX(new Box(cx - c / 2, bottom - sh, c, sh), b);
			}
			boolean dc = "dont_come".equals(kind);
			int in = Math.max((int) Math.round(b.w() * 0.2), c / 2 + 2);
			int mine = dc ? b.right() - in : b.x() + in;
			cx = layer == Layer.MINE ? mine : mine + (dc ? -1 : 1) * (c + 1);
			return clampX(new Box(cx - c / 2, bottom - sh, c, sh), b);
		}
		Box area = switch (kind) {
			case "dont_come" -> dontCome;
			case "come" -> come;
			case "field" -> field;
			case "dont_pass" -> dontPass;
			default -> pass;
		};
		switch (kind) {
			case "dont_come" -> {
				cx = area.cx() + (layer == Layer.MINE ? 0 : layer == Layer.ODDS ? step : -step);
				bottom = area.bottom() - 4;
			}
			case "come" -> {
				// right of the dice and the point caption: yours at 35 %, everybody else's at 80 % of that band
				int r0 = pointCaption.right() + 4;
				int rw = come.right() - 2 - r0;
				cx = r0 + rw * (layer == Layer.OTHERS ? 80 : 35) / 100;
				bottom = come.bottom() - 3;
				if (layer == Layer.OTHERS && rw < 2 * c + 8) {
					// a narrow band: everybody else's chips above yours
					cx = r0 + rw * 35 / 100;
					bottom = come.y() + 2 + sh;
				}
			}
			case "field" -> {
				// between 10 and 11, under the numbers
				int a = fieldGlyphs[4].cx();
				int b = fieldGlyphs[5].cx();
				int base = (a + b) / 2;
				cx = base + (layer == Layer.MINE ? 0 : layer == Layer.ODDS ? step : -step);
				bottom = field.bottom() - 2;
			}
			case "dont_pass" -> {
				int base = dontPass.x() + dontPass.w() * 66 / 100;
				cx = layer == Layer.MINE ? base : layer == Layer.ODDS ? passOdds.x() + dontPass.w() * 4 / 100 : dontPass.x() + dontPass.w() * 30 / 100;
				bottom = dontPass.bottom() - 1;
			}
			default -> {
				int base = pass.x() + pass.w() * 62 / 100;
				cx = layer == Layer.MINE ? base : layer == Layer.ODDS ? passOdds.x() + pass.w() * 4 / 100 : pass.x() + pass.w() * 30 / 100;
				bottom = pass.bottom() - 2;
			}
		}
		Box s = new Box(cx - c / 2, bottom - sh, c, sh);
		return clampX(s, area);
	}

	/** Pixel step between discs of a stack and the max discs drawn (ui-v2.md §4.2 ChipStack: ≤ 4). */
	public static final int STACK_STEP = 2, MAX_DISCS = 4;

	public int stackH() {
		return Math.min(chip + (MAX_DISCS - 1) * STACK_STEP, Math.max(chip, rowMin() - 3));
	}

	private int rowMin() {
		return Math.min(dontPass.h(), pass.h());
	}

	/** Discs that fit a stack box of this layout (a short row draws fewer). */
	public int maxDiscs() {
		return Math.max(1, Math.min(MAX_DISCS, 1 + (stackH() - chip) / STACK_STEP));
	}

	private static Box clampX(Box s, Box area) {
		int x = Math.max(area.x() + 1, Math.min(s.x(), area.right() - 1 - s.w()));
		int y = Math.max(area.y() + 1, Math.min(s.y(), area.bottom() - 1 - s.h()));
		return new Box(x, y, s.w(), s.h());
	}

	/** Puck (top-left) for {@code point} (0 = OFF in the Don't Come box's top-right corner). */
	public Box puckBox(int point) {
		if (point == 0) {
			return new Box(dontCome.right() - puck - 3, dontCome.y() + 3, puck, puck);
		}
		Box b = point(point);
		Box glyph = pointGlyph(point);
		// never on the number: shrink in a narrow box (4:3 windows)
		int p = Math.max(10, Math.min(puck, b.right() - 2 - glyph.right() - 2));
		return new Box(b.right() - p - 2, b.y() + 2, p, p);
	}

	/**
	 * Free band for the result banner (ui-v2.md §2.1): the COME row left of the dice (it may cover the printed COME
	 * word, never a number, a stack or the dice).
	 */
	public Box bannerRegion() {
		return new Box(come.x() + 2, come.y() + 2, diceZone.x() - die / 2 - 4 - come.x() - 2, come.h() - 4);
	}

	/** Every printed glyph (point numbers, field numbers, words): obstacles for stacks and tags. */
	public List<Box> glyphs() {
		List<Box> out = new ArrayList<>(List.of(pointGlyphs));
		out.addAll(List.of(fieldGlyphs));
		out.add(fieldWord);
		out.add(comeWord);
		out.add(dontPassWord);
		out.add(passWord);
		out.add(oddsWord);
		return out;
	}
}

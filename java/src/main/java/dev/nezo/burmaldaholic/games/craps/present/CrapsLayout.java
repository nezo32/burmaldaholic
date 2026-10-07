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
	/** Two dice at rest, the total pill and the {@code point} caption, inside the COME row. */
	public final Box die1, die2, total, pointCaption;
	/** Where the dice rest (centre of die 1 range) and where they are thrown from (layout px). */
	public final Box restRegion;
	public final int throwX, throwY;
	/** Number scale of the point boxes (2 / 3) and of the field word. */
	public final int pointScale, wordScale;
	/** Chip disc size on the felt and the puck size. */
	public final int chip, puck;
	public final int gap;

	private CrapsLayout(TableGrid g) {
		this.grid = g;
		this.tier = g.tier;
		int m = tier.margin;
		gap = tier == Tier.T1 ? 2 : 3;
		chip = tier.feltChip;
		puck = tier == Tier.T1 ? 12 : tier == Tier.T2 ? 16 : 20;
		int x0 = g.play.x() + m + 4;
		int y0 = g.play.y() + m + 2;
		int w = g.play.w() - 2 * (m + 4);
		int h = g.play.h() - (m + 2) - (m + 6);
		felt = new Box(x0, y0, w, h);
		int avail = h - 4 * gap;
		int placeH = (int) Math.round(avail * 0.28);
		int comeH = (int) Math.round(avail * 0.245);
		int fieldH = (int) Math.round(avail * 0.18);
		int dpH = (int) Math.round(avail * 0.105);
		int passH = avail - placeH - comeH - fieldH - dpH;
		int dcW = (int) Math.round(w * 0.17);
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
		// dice region: the centre-right of the COME row
		int die = tier == Tier.T1 ? 14 : tier == Tier.T2 ? 18 : 22;
		int dy = come.cy() - die / 2;
		int dx = come.x() + come.w() * 52 / 100;
		die1 = new Box(dx, dy, die, die);
		die2 = new Box(dx + die + 6, dy - 2, die, die);
		int tw = tier == Tier.T1 ? 18 : tier == Tier.T2 ? 22 : 28;
		total = new Box(die2.right() + 6, come.cy() - tw / 2 - 1, tw, tw + 2);
		pointCaption = new Box(total.right() + 4, come.cy() - 4, 32, 9);
		restRegion = new Box(die1.x() - die, come.y() + die / 2 + 1, die * 2, Math.max(1, come.h() - die - 2));
		throwX = felt.right() - die;
		throwY = felt.bottom() - die / 2;
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
		oddsWord = new Box(passOdds.x() + w * 9 / 100, pass.cy() - 4, Math.min(passOdds.w() - w * 9 / 100 - 4, w * 12 / 100), 8);
	}

	public static CrapsLayout of(TableGrid g) {
		return new CrapsLayout(g);
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
		return new Box(come.x() + 2, come.y() + 2, die1.x() - 6 - come.x() - 2, come.h() - 4);
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

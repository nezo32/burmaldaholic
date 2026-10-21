package dev.nezo.burmaldaholic.games.extras.present;

import dev.nezo.burmaldaholic.core.anim.motion.ArtScale;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;

/**
 * The Dice Duel PlayArea at every resolution (DI §4.1, PHASE2B §2.2.4, replaces {@code ExtrasLayout.dice}): the PvP
 * strip (opponent picker + Challenge, the incoming challenge card, the stake toggle) across the top, and under it the
 * 360 × 140 art arena at a CRISP art scale ({@link ArtScale}: a whole number {@code n} of physical px per art px when
 * that keeps ≥ 80 % of the size; e.g. 4K GUI Auto 427×240 k9 → n 9 or 8, 960×540 k2 → n 5), its origin snapped to
 * physical px. The arena is centred in the space under the strip. Pure; audited by {@code DiceStageTest} at every size
 * of the resolution matrix (inside the PlayArea, clear of the strip, integer n where ≥ 0.8·r).
 *
 * @param art   the chosen art scale ({@code art.scale()} layout px per art px)
 * @param ox    arena origin x in layout px (snapped to 1/k)
 * @param oy    arena origin y in layout px (snapped to 1/k)
 * @param strip the PvP strip above the arena
 * @param row   height of one strip row (buttons, the invite card)
 * @param roomy the grid is ROOMY (≥ 800 × 450): plates get a second line
 */
public record DiceStage(ArtScale.Result art, double ox, double oy, Box strip, int row, boolean roomy) {
	/** Arena art size. */
	public static final int W = 360, H = 140;
	/** Gap between the strip and the arena (layout px). */
	public static final int GAP = 4;

	/** The stage of a table grid. */
	public static DiceStage of(TableGrid g) {
		Box play = g.play;
		int row = ExtrasLayout.rowH(g.tier);
		Box all = g.columns(1, 12, play.y() + 2, play.h() - 4);
		int strip = 2 * row + 8;
		int top = all.y() + strip + GAP;
		int availH = all.bottom() - top;
		ArtScale.Result art = ArtScale.pick(W, H, all.w(), availH, Math.max(1, g.viewport.k()));
		double ox = art.originX(all.x(), all.w());
		double oy = art.originY(top, availH);
		// the strip spans the arena's width (at least the 12 columns at small sizes) right above it
		int sx = (int) Math.floor(Math.max(all.x(), ox));
		int sw = (int) Math.min(all.w(), Math.ceil(art.w()));
		Box stripBox = new Box(sx, all.y() + 2, Math.max(sw, Math.min(all.w(), 300)), strip);
		if (stripBox.right() > all.right()) stripBox = new Box(all.right() - stripBox.w(), stripBox.y(), stripBox.w(), strip);
		return new DiceStage(art, ox, oy, stripBox, row, g.roomy());
	}

	/** Layout px per art px. */
	public double scale() {
		return art.scale();
	}

	/** Physical px per layout px. */
	public int k() {
		return art.k();
	}

	/** Physical px per art px, rounded (exact when {@link ArtScale.Result#integral}); the snap grid inside the arena pose. */
	public int n() {
		return Math.max(1, art.physPerArt());
	}

	/** Layout x of art x. */
	public double x(double artX) {
		return ox + artX * art.scale();
	}

	/** Layout y of art y. */
	public double y(double artY) {
		return oy + artY * art.scale();
	}

	/** The arena's layout box (whole layout px covering the art; hit map, banner region). */
	public Box arena() {
		int x0 = (int) Math.floor(ox);
		int y0 = (int) Math.floor(oy);
		return new Box(x0, y0, (int) Math.ceil(ox + art.w()) - x0, (int) Math.ceil(oy + art.h()) - y0);
	}

	/** Layout box of an art rectangle (rounded outward). */
	public Box box(double ax, double ay, double aw, double ah) {
		int x0 = (int) Math.floor(x(ax));
		int y0 = (int) Math.floor(y(ay));
		return new Box(x0, y0, (int) Math.ceil(x(ax + aw)) - x0, (int) Math.ceil(y(ay + ah)) - y0);
	}

	/** Seat plate of the lane centred on art x {@code cx}, over the top rail: {@code w} wide, {@code h} high. */
	public Box plate(int cx, int w, int h) {
		int x = (int) Math.round(x(cx)) - w / 2;
		int y = (int) Math.round(y(6) + Math.max(0, (14 * scale() - h) / 2));
		return new Box(x, y, w, h);
	}
}

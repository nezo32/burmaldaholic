package dev.nezo.burmaldaholic.core.ui.v2;

/**
 * The frame of every v2 table screen (REDESIGN.md §3.2): TopBar, PlayArea (felt, full bleed) and Dock, plus the
 * 12-column grid of the PlayArea and fraction helpers for seat anchors. Pure; built once per {@code init()} from the
 * {@link Viewport}.
 */
public final class TableGrid {
	public static final int COLUMNS = 12, GUTTER = 4;

	public final Viewport viewport;
	public final Tier tier;
	/** How much room the frame has beyond its tier ({@link Room#ROOMY} from 800 × 450; games opt in). */
	public final Room room;
	public final Box screen, topBar, play, dock;
	/** Width of one grid column (≥ 1). */
	public final int colW;

	public TableGrid(Viewport viewport) {
		this.viewport = viewport;
		int w = viewport.frameW(); // the screen lays out in the frame (§3.7)
		int h = viewport.frameH();
		this.tier = Tier.of(w, h);
		this.room = Room.of(w, h);
		this.screen = new Box(0, 0, w, h);
		this.topBar = new Box(0, 0, w, tier.topBar);
		this.dock = new Box(0, h - tier.dock, w, tier.dock);
		this.play = new Box(0, tier.topBar, w, h - tier.topBar - tier.dock);
		this.colW = Math.max(1, (w - 2 * tier.margin - GUTTER * (COLUMNS - 1)) / COLUMNS);
	}

	public static TableGrid of(int w, int h) {
		return new TableGrid(new Viewport(w, h, 1, 1f));
	}

	public Room room() {
		return room;
	}

	public boolean roomy() {
		return room == Room.ROOMY;
	}

	public int m() {
		return tier.margin;
	}

	/** PlayArea x for a fraction of the width. */
	public int fx(double f) {
		return play.x() + (int) Math.round(f * play.w());
	}

	/** PlayArea y for a fraction of the PlayArea height (0 = top of the felt). */
	public int fy(double f) {
		return play.y() + (int) Math.round(f * play.h());
	}

	/** Columns {@code from..to} (1-based, inclusive) of the grid as a box spanning {@code y, h}. */
	public Box columns(int from, int to, int y, int h) {
		if (from < 1 || to > COLUMNS || from > to) throw new IllegalArgumentException(from + ".." + to);
		int x0 = tier.margin + (from - 1) * (colW + GUTTER);
		int x1 = tier.margin + to * (colW + GUTTER) - GUTTER;
		return new Box(x0, y, x1 - x0, h);
	}

	/** The usable felt: the PlayArea minus the margin; nothing interactive is laid out outside it. */
	public Box felt() {
		return new Box(play.x() + m(), play.y() + 2, play.w() - 2 * m(), play.h() - 6);
	}
}

package dev.nezo.burmaldaholic.core.ui.v2;

/**
 * Size tier of the table UI by virtual GUI height (REDESIGN.md §3.1). T1 is the fallback for small windows; T2 is
 * 1920 × 1080 at GUI 4 (480 × 270), T3 is 2560 × 1440 at GUI 4 or 1080p at GUI 3 (640 × 360). All metrics in layout px.
 */
public enum Tier {
	//  hero card  seat card  trayChip feltChip topBar dock margin primaryW btnH  actionW tool toolPitch plateW
	T1(21, 29, 13, 17, 20, 10, 18, 36, 4, 76, 26, 58, 18, 20, 66),
	T2(37, 49, 21, 29, 24, 12, 22, 44, 6, 92, 30, 70, 20, 22, 74),
	T3(49, 65, 37, 49, 32, 16, 26, 56, 8, 132, 36, 84, 24, 26, 80);

	public final int heroW, heroH, seatW, seatH, trayChip, feltChip, topBar, dock, margin, primaryW, buttonH, actionW, tool,
		toolPitch, plateW;
	/** Seat plate height (one line + avatar) and the gap the kit keeps between any two laid-out elements. */
	public static final int PLATE_H = 18, GAP = 2;

	Tier(int heroW, int heroH, int seatW, int seatH, int trayChip, int feltChip, int topBar, int dock, int margin, int primaryW,
			int buttonH, int actionW, int tool, int toolPitch, int plateW) {
		this.heroW = heroW;
		this.heroH = heroH;
		this.seatW = seatW;
		this.seatH = seatH;
		this.trayChip = trayChip;
		this.feltChip = feltChip;
		this.topBar = topBar;
		this.dock = dock;
		this.margin = margin;
		this.primaryW = primaryW;
		this.buttonH = buttonH;
		this.actionW = actionW;
		this.tool = tool;
		this.toolPitch = toolPitch;
		this.plateW = plateW;
	}

	/** Tier by height; a narrow (4:3 or portrait) window drops to the tier its width can hold (T2 ≥ 400, T3 ≥ 560 wide). */
	public static Tier of(int w, int h) {
		Tier byH = h < 255 ? T1 : h < 330 ? T2 : T3;
		Tier byW = w < 400 ? T1 : w < 560 ? T2 : T3;
		return byH.ordinal() < byW.ordinal() ? byH : byW;
	}
}

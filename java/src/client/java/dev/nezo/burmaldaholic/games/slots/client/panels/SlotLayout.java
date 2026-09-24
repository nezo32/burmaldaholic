package dev.nezo.burmaldaholic.games.slots.client.panels;

/**
 * Screen geometry (SLOTS.md §10.5, slots.md §4): 400 × 240 with 44 px cells, or the compact 320 × 220 with 32 px
 * cells when the GUI is smaller than 400 × 240. All coordinates are absolute GUI px (panel origin already added).
 */
public final class SlotLayout {
	public final boolean compact;
	public final int left;
	public final int top;
	public final int width;
	public final int height;
	public final int cell;
	public final int wx;
	public final int wy;
	public final int meterY;
	public final int meterW;
	public final int[] meterTiers;
	public final int leftPanelX;
	public final int rightPanelX;
	public final int panelY;
	public final int panelW;
	public final int panelH;
	public final int labelY;
	public final int controlsY;
	public final int spinX;
	public final int spinY;
	public final int spinW;
	public final int spinH;
	public final int balanceY;

	private SlotLayout(boolean compact, int screenW, int screenH) {
		this.compact = compact;
		this.width = compact ? 320 : 400;
		this.height = compact ? 220 : 240;
		this.left = (screenW - width) / 2;
		this.top = (screenH - height) / 2;
		this.cell = compact ? 32 : 44;
		this.wx = left + (width - 5 * cell) / 2;
		this.wy = top + (compact ? 36 : 40);
		this.meterY = top + 3;
		this.meterTiers = compact ? new int[] {4, 3} : new int[] {1, 2, 3, 4};
		this.meterW = compact ? 150 : 94;
		this.panelW = compact ? 140 : 76;
		this.leftPanelX = compact ? left + 6 : left + 4;
		this.rightPanelX = compact ? left + width - 6 - panelW : left + width - 4 - panelW;
		this.panelY = compact ? top + 22 : wy;
		this.panelH = compact ? 12 : 3 * cell;
		this.labelY = wy + 3 * cell + 6;
		this.controlsY = compact ? top + 152 : top + 190;
		this.spinW = compact ? 52 : 56;
		this.spinH = compact ? 36 : 40;
		this.spinX = left + width - spinW - 6;
		this.spinY = compact ? top + 150 : top + 188;
		this.balanceY = top + height - 11;
	}

	public static SlotLayout of(int screenW, int screenH) {
		return new SlotLayout(screenW < 400 || screenH < 240, screenW, screenH);
	}

	public static SlotLayout forceCompact(int screenW, int screenH, boolean compact) {
		return new SlotLayout(compact, screenW, screenH);
	}

	/** Meter i's box x (i-th of {@link #meterTiers}). */
	public int meterX(int i) {
		int gap = 4;
		int total = meterTiers.length * meterW + (meterTiers.length - 1) * gap;
		return left + (width - total) / 2 + i * (meterW + gap);
	}

	public int windowW() {
		return 5 * cell;
	}

	public int windowH() {
		return 3 * cell;
	}
}

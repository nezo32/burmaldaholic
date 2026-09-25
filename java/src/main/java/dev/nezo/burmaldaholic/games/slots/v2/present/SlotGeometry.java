package dev.nezo.burmaldaholic.games.slots.v2.present;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Slot screen geometry (SLOTS.md §10.5, slots.md §4; lane J-L9b; PURE so the "nothing overlaps" rules are unit
 * tested): 400 × 240 with 44 px cells, or the compact 320 × 220 with 32 px cells when the GUI is smaller than
 * 400 × 240. All coordinates are absolute GUI px (panel origin already added).
 *
 * <p>Top to bottom: the jackpot meters; the cabinet header (marquee bulbs + the machine's title plate, which the Nice
 * tier plate replaces during a Nice win so no reel cell is ever covered); the reel window between the feature panel
 * (left) and the win panel (right); the result line; the control band: the bet group ("BET" over a value plate
 * between − and +), the flowing control buttons and the round SPIN button with its cost caption under it; the balance.
 */
public class SlotGeometry {
	/** Axis-aligned rectangle. */
	public record Rect(int x, int y, int w, int h) {
		public int right() {
			return x + w;
		}

		public int bottom() {
			return y + h;
		}

		public boolean overlaps(Rect o) {
			return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
		}

		public boolean inside(Rect o) {
			return x >= o.x && y >= o.y && right() <= o.right() && bottom() <= o.bottom();
		}
	}

	/** Cabinet nine-slice overhang around the frame (the sprite is drawn this much outside the border). */
	public static final int CABINET_PAD = 8;
	public static final int CABINET_TOP = 2;
	public static final int CAPTION_H = 9;
	/** Title plate rim (outline + bevel) in px: the nine-slice border of {@code slots/<m>/title_plate}. */
	public static final int TITLE_RIM = 2;
	/** A line of vanilla text: 7 px caps, the descender row and the drop shadow. */
	public static final int TEXT_H = 9;

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
	public final int meterH = 18;
	public final int[] meterTiers;
	/** Cabinet border and marquee height. */
	public final int border;
	public final int marquee;
	public final int titleY;
	public final int titleH;
	public final int niceY;
	public final int niceH;
	public final int leftPanelX;
	public final int rightPanelX;
	public final int panelY;
	public final int panelW;
	public final int panelH;
	public final int labelY;
	public final int controlsY;
	public final int rowH;
	public final int betLabelY;
	public final int betRowY;
	public final int betMinusX;
	public final int betPlateX;
	public final int betPlateW;
	public final int betPlusX;
	public final int betButton;
	public final int flowX;
	public final int flowW;
	public final int spinX;
	public final int spinY;
	public final int spinW;
	public final int spinH;
	public final int captionY;
	public final int balanceY;

	protected SlotGeometry(boolean compact, int screenW, int screenH) {
		this.compact = compact;
		this.width = compact ? 320 : 400;
		this.height = compact ? 220 : 240;
		this.left = (screenW - width) / 2;
		this.top = (screenH - height) / 2;
		this.cell = compact ? 32 : 44;
		this.wx = left + (width - 5 * cell) / 2;
		this.wy = top + (compact ? 38 : 42);
		this.meterY = top + 2;
		this.meterTiers = compact ? new int[] {4, 3} : new int[] {1, 2, 3, 4};
		this.meterW = compact ? 150 : 94;
		this.border = compact ? 5 : 6;
		this.marquee = compact ? 12 : 14;
		this.titleY = wy - border - marquee;
		this.titleH = marquee + 2;
		this.niceH = compact ? 16 : 20;
		this.niceY = niceY(wy, compact);
		this.panelW = compact ? 58 : 72;
		this.leftPanelX = left + (compact ? 4 : 4);
		this.rightPanelX = left + width - (compact ? 4 : 4) - panelW;
		this.panelY = wy;
		this.panelH = 3 * cell;
		this.labelY = wy + 3 * cell + (compact ? 5 : 6);
		this.controlsY = compact ? top + 151 : top + 191;
		this.rowH = 18;
		this.betButton = 18;
		this.betLabelY = controlsY;
		this.betRowY = controlsY + 10;
		this.betMinusX = left + 6;
		this.betPlateW = compact ? 48 : 60;
		this.betPlateX = betMinusX + betButton + 2;
		this.betPlusX = betPlateX + betPlateW + 2;
		this.spinW = compact ? 52 : 56;
		this.spinH = compact ? 36 : 40;
		this.spinX = left + width - spinW - 6;
		this.spinY = compact ? top + 149 : top + 187;
		this.captionY = spinY + spinH + 2;
		this.flowX = betPlusX + betButton + 6;
		this.flowW = spinX - 6 - flowX;
		this.balanceY = top + height - 10;
	}

	/** Top of the Nice tier plate: it sits in the header, ending 1 px above the reel window (never over a cell). */
	public static int niceY(int wy, boolean compact) {
		return wy - (compact ? 16 : 20) - 1;
	}

	public static SlotGeometry of(int screenW, int screenH) {
		return new SlotGeometry(isCompact(screenW, screenH), screenW, screenH);
	}

	public static boolean isCompact(int screenW, int screenH) {
		return screenW < 400 || screenH < 240;
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

	/** Title plate for a title {@code textW} px wide, centred over the marquee. */
	public Rect titlePlate(int textW) {
		int max = windowW() + 2 * border - 16;
		int w = Math.min(max, Math.max(60, textW + 20));
		return new Rect(wx + windowW() / 2 - w / 2, titleY, w, titleH);
	}

	/** Top of the title text in {@code plate}: its glyphs, descenders and shadow all sit on the face, inside the rim. */
	public static int titleTextY(Rect plate) {
		return plate.y() + (plate.h() - 8) / 2;
	}

	/** Nice tier plate for a word {@code textW} px wide at {@code scale}. */
	public Rect nicePlate(int textW, int scale) {
		int w = Math.min(windowW() + 2 * border, textW * scale + 24);
		return new Rect(wx + windowW() / 2 - w / 2, niceY, w, niceH);
	}

	/** Result line under the reels. */
	public Rect label() {
		return new Rect(wx, labelY - 1, windowW(), 10);
	}

	/** The cabinet nine-slice: 8 px overhang at the sides and bottom, 2 px at the top (clear of the meters). */
	public Rect cabinet() {
		return new Rect(wx - border - CABINET_PAD, titleY - CABINET_TOP, windowW() + 2 * (border + CABINET_PAD),
			windowH() + border + marquee + border + CABINET_PAD + CABINET_TOP);
	}

	/** Bet group: label, − / plate / +. */
	public Rect betGroup() {
		return new Rect(betMinusX, betLabelY, betPlusX + betButton - betMinusX, betRowY + betButton - betLabelY);
	}

	/** Area the control buttons flow into (at most two rows). */
	public Rect flowArea() {
		return new Rect(flowX, controlsY, flowW, 2 * rowH + 2);
	}

	public Rect spin() {
		return new Rect(spinX, spinY, spinW, spinH);
	}

	public Rect spinCaption() {
		return new Rect(spinX - 6, captionY, spinW + 12, CAPTION_H);
	}

	public Rect balance() {
		return new Rect(left + 6, balanceY, width / 2, 9);
	}

	public Rect bounds() {
		return new Rect(left, top, width, height);
	}

	/**
	 * Places buttons of the given widths into the flow area: left to right, wrapping into a second row; a button
	 * wider than the area is clamped to it. Returns their rectangles (row height {@link #rowH}).
	 */
	public Rect[] flow(int[] widths) {
		Rect[] out = new Rect[widths.length];
		int x = 0;
		int row = 0;
		for (int i = 0; i < widths.length; i++) {
			int w = Math.min(widths[i], flowW);
			if (x > 0 && x + 4 + w > flowW) {
				row++;
				x = 0;
			}
			if (x > 0) x += 4;
			out[i] = new Rect(flowX + x, controlsY + row * (rowH + 2), w, rowH);
			x += w;
		}
		return out;
	}

	/** Whether buttons of these widths fit the flow area (≤ 2 rows); if not, the screen shows the secondary ones as icons. */
	public boolean flowFits(int[] widths) {
		for (Rect r : flow(widths)) if (!r.inside(flowArea())) return false;
		return true;
	}

	/** Named regions that must never overlap each other (tests; the debug layout report). */
	public Map<String, Rect> regions() {
		Map<String, Rect> m = new LinkedHashMap<>();
		for (int i = 0; i < meterTiers.length; i++) m.put("meter" + i, new Rect(meterX(i), meterY - 1, meterW, meterH));
		m.put("header", new Rect(wx - border, titleY, windowW() + 2 * border, marquee + border));
		m.put("window", new Rect(wx, wy, windowW(), windowH()));
		m.put("leftPanel", new Rect(leftPanelX, panelY, panelW, panelH));
		m.put("rightPanel", new Rect(rightPanelX, panelY, panelW, panelH));
		m.put("label", label());
		m.put("bet", betGroup());
		m.put("flow", flowArea());
		m.put("spin", spin());
		m.put("caption", spinCaption());
		m.put("balance", balance());
		return m;
	}
}

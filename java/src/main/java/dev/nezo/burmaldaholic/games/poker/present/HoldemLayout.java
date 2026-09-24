package dev.nezo.burmaldaholic.games.poker.present;

import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.games.poker.present.LabelPlacer.Rect;

/**
 * Texas Hold'em screen anchors (docs/design/visual/cards.md §6.4, mockups {@code cards_holdem_showdown.png} /
 * {@code cards_holdem_nether.png}): the oval table at (10, 18) on the 428 × 240 design canvas, the board, pot, deck
 * and up to nine seat slots (the viewer always at the bottom, the others clockwise). All coordinates are CANVAS
 * pixels (table-local anchors of the spec + the table origin); the screen scales the canvas by an integer factor.
 * Pure: the unit tests check that no two seats' cards overlap, that every anchor is on the canvas and that labels
 * placed with {@link LabelPlacer} never cover a card.
 */
public final class HoldemLayout {
	public static final int CANVAS_W = 428, CANVAS_H = 240;
	public static final int TABLE_X = 10, TABLE_Y = 18, TABLE_W = 408, TABLE_H = 184;
	public static final int L_W = 37, L_H = 49, M_W = 21, M_H = 29;
	/** The felt centre every bet spot leans toward (table-local (204, 92)). */
	public static final int CENTER_X = TABLE_X + 204, CENTER_Y = TABLE_Y + 92;
	/** Ellipse of the dealer button's travel (radii in px around the centre). */
	public static final int BUTTON_RX = 150, BUTTON_RY = 62;
	public static final int PLATE_H = 22;
	/** Tag bubble height (nine-slice, text line + padding). */
	public static final int TAG_H = 11;

	/**
	 * One seat position (canvas px).
	 *
	 * @param plateX   plate left (the plate grows to the right with its content; right-side plates are clamped)
	 * @param plateY   plate top
	 * @param cardsX   first hole card left
	 * @param cardsY   hole cards top
	 * @param step     x step between the two hole cards
	 * @param large    L cards (the viewer's, "in your hands" over the rail) or M
	 * @param right    the plate sits on the right half (it may be clamped leftwards)
	 */
	public record Slot(int id, int plateX, int plateY, int cardsX, int cardsY, int step, boolean large, boolean right) {
		public int cardW() {
			return large ? L_W : M_W;
		}

		public int cardH() {
			return large ? L_H : M_H;
		}

		public Rect card(int k) {
			return new Rect(cardsX + step * k, cardsY, cardW(), cardH());
		}

		/** Both hole cards' bounding box. */
		public Rect cards() {
			return new Rect(cardsX, cardsY, step + cardW(), cardH());
		}
	}

	/** Slot table (table-local anchors of visual/cards.md §6.4 + three extra slots for 7–9 seats). */
	private static final Slot[] SLOTS = {
		slot(0, 174, 168, 164, 118, 41, true, false), // bottom: the viewer
		slot(1, 34, 140, 72, 108, 23, false, false), // bottom-left
		slot(2, -2, 64, 64, 50, 12, false, false), // left
		slot(3, 44, -6, 84, 20, 12, false, false), // top-left (7+ seats)
		slot(4, 150, -8, 226, -4, 6, false, false), // top
		slot(5, 300, -6, 280, 22, 12, false, true), // top-right (7+ seats)
		slot(6, 336, 64, 314, 50, 12, false, true), // right
		slot(7, 308, 140, 292, 108, 23, false, true), // bottom-right
		slot(8, 250, 168, 250, 138, 23, false, true), // bottom, right of the viewer (9 seats)
	};

	/** Slot ids per table size, clockwise from the viewer (6 = the mockup). */
	private static final int[][] SLOTS_FOR = {
		{0}, {0}, {0, 4}, {0, 2, 6}, {0, 2, 4, 6}, {0, 1, 3, 5, 7}, {0, 1, 2, 4, 6, 7}, {0, 1, 2, 3, 5, 6, 7},
		{0, 1, 2, 3, 4, 5, 6, 7}, {0, 1, 2, 3, 4, 5, 6, 7, 8},
	};

	private static Slot slot(int id, int px, int py, int cx, int cy, int step, boolean large, boolean right) {
		return new Slot(id, TABLE_X + px, TABLE_Y + py, TABLE_X + cx, TABLE_Y + cy, step, large, right);
	}

	private HoldemLayout() {}

	/** The slot of table seat {@code seat} at a table of {@code size} seats seen by {@code viewerSeat} (−1: seat 0 below). */
	public static Slot slotOf(int seat, int viewerSeat, int size) {
		int n = Math.max(2, Math.min(9, size));
		int rel = Math.floorMod(seat - Math.max(0, viewerSeat), n);
		return SLOTS[SLOTS_FOR[n][rel]];
	}

	public static Slot slot(int id) {
		return SLOTS[id];
	}

	/** Slot ids used at a table of {@code size} seats. */
	public static int[] slotIds(int size) {
		return SLOTS_FOR[Math.max(2, Math.min(9, size))].clone();
	}

	// ---- the middle ---------------------------------------------------------------------------------------------

	/** Board card {@code i} (L). */
	public static Rect board(int i) {
		return new Rect(TABLE_X + 106 + 40 * i, TABLE_Y + 56, L_W, L_H);
	}

	/** The deck (21 × 32; the muck lies on it) and the deal origin (card centre at the start of a deal arc). */
	public static Rect deck() {
		return new Rect(TABLE_X + 130, TABLE_Y + 18, 21, 32);
	}

	public static int dealOriginX() {
		return TABLE_X + 140;
	}

	public static int dealOriginY() {
		return TABLE_Y + 30;
	}

	/** Pot print (48 × 24) and the main pot stack's centre; side pot {@code i ≥ 1} sits 18 px right per pot. */
	public static Rect potPrint() {
		return new Rect(TABLE_X + 180, TABLE_Y + 22, 48, 24);
	}

	public static int potX(int pot) {
		return TABLE_X + 204 + 18 * pot;
	}

	public static int potY() {
		return TABLE_Y + 40;
	}

	/** Plate width for a content (name / sub-line) width: avatar + gaps + text (visual §8.2: content + 30). */
	public static int plateWidth(int contentW) {
		return Math.max(40, contentW + 30);
	}

	/** Plate rectangle of a slot for a plate of width {@code w} (kept on the canvas). */
	public static Rect plate(Slot s, int w) {
		int x = s.plateX();
		if (x + w > CANVAS_W - 2) x = CANVAS_W - 2 - w;
		return new Rect(Math.max(0, x), s.plateY(), w, PLATE_H);
	}

	/** Bet-line spot centre: 35 % of the way from the plate centre to the felt centre. */
	public static int[] betSpot(Slot s, int plateW) {
		Rect p = plate(s, plateW);
		double x = p.cx() + 0.35 * (CENTER_X - p.cx());
		double y = p.cy() + 0.35 * (CENTER_Y - p.cy());
		if (s.id() == 0) {
			// the viewer's cards cover the straight line: the spot sits left of them
			return new int[] {s.cardsX() - 14, s.cardsY() + 10};
		}
		return new int[] {(int) Math.round(x), (int) Math.round(y)};
	}

	/** Parametric angle (radians) of a slot on the button ellipse. */
	public static double buttonAngle(Slot s) {
		int px = s.plateX() + 28;
		int py = s.plateY() + PLATE_H / 2;
		return Math.atan2((py - CENTER_Y) / (double) BUTTON_RY, (px - CENTER_X) / (double) BUTTON_RX);
	}

	/**
	 * The dealer button's centre travelling from slot {@code from} to slot {@code to} ({@code u} 0..1, inOutCubic) ALONG
	 * the ellipse (never straight across the felt), clockwise on screen (increasing angle with y down).
	 */
	public static int[] button(Slot from, Slot to, double u) {
		double a0 = buttonAngle(from);
		double a1 = buttonAngle(to);
		double d = a1 - a0;
		while (d < 0) d += 2 * Math.PI;
		while (d >= 2 * Math.PI) d -= 2 * Math.PI;
		double a = a0 + d * Ease.IN_OUT_CUBIC.apply(u);
		return new int[] {(int) Math.round(CENTER_X + BUTTON_RX * Math.cos(a)), (int) Math.round(CENTER_Y + BUTTON_RY * Math.sin(a))};
	}

	/** Every card rectangle on the felt (board, all seats' hole cards, the deck) for {@code size} seats. */
	public static java.util.List<Rect> cardRects(int size) {
		java.util.List<Rect> out = new java.util.ArrayList<>();
		for (int i = 0; i < 5; i++) out.add(board(i));
		for (int id : slotIds(size)) {
			Slot s = SLOTS[id];
			out.add(s.card(0));
			out.add(s.card(1));
		}
		out.add(deck());
		return out;
	}

	public static Rect canvas() {
		return new Rect(0, 0, CANVAS_W, CANVAS_H);
	}

	/** The play area labels may use: the canvas above the console (y &lt; 206). */
	public static Rect labelBounds() {
		return new Rect(2, 0, CANVAS_W - 4, 206);
	}

	/**
	 * Candidate positions (top-left) for a seat's hand-name / action tag of {@code w} px: under the plate for the
	 * side seats, above the cards for the viewer, then beside the cards and above the plate.
	 */
	public static LabelPlacer.Request tagRequest(Slot s, int plateW, int w) {
		Rect p = plate(s, plateW);
		Rect c = s.cards();
		int h = TAG_H;
		int aboveCards = c.y() - h - 2;
		int belowPlate = p.y() + p.h() + 2;
		int abovePlate = p.y() - h - 2;
		int cx = c.cx() - w / 2;
		if (s.id() == 0) {
			return new LabelPlacer.Request(w, h, cx, aboveCards, c.x() - w - 3, c.y() + 4, c.x() + c.w() + 3, c.y() + 4);
		}
		int leftOfCards = c.x() - w - 3;
		int rightOfCards = c.x() + c.w() + 3;
		if (s.plateY() < TABLE_Y + 10) {
			// top seats: under the cards first
			return new LabelPlacer.Request(w, h, cx, c.y() + c.h() + 2, s.right() ? leftOfCards : rightOfCards, c.y() + 4, p.cx() - w / 2, belowPlate);
		}
		return new LabelPlacer.Request(w, h, p.cx() - w / 2, belowPlate, cx, aboveCards, s.right() ? leftOfCards : rightOfCards, c.y() + 4,
			p.cx() - w / 2, abovePlate);
	}
}

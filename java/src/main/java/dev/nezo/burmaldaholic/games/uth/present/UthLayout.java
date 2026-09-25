package dev.nezo.burmaldaholic.games.uth.present;

import dev.nezo.burmaldaholic.games.poker.present.LabelPlacer;
import dev.nezo.burmaldaholic.games.poker.present.LabelPlacer.Rect;

/**
 * Ultimate Texas Hold'em screen anchors (docs/design/visual/cards.md §6.5, mockup {@code cards_uth_decision.png}):
 * the crescent table at (10, 18) on the 427 × 240 canvas, the dealer's hand, the board, the paytable panel, the
 * viewer's hole cards and the row of four circles (Trips, Ante, Blind, Play; pitch 34), and up to five other seats.
 * Canvas pixels; pure (unit-tested: no overlaps, everything on the canvas).
 */
public final class UthLayout {
	public static final int CANVAS_W = 427, CANVAS_H = 240;
	public static final int TABLE_X = 10, TABLE_Y = 18;
	public static final int L_W = 37, L_H = 49, M_W = 21, M_H = 29;
	public static final int CIRCLE = 24;
	public static final int PLATE_H = 22;
	/** Circle centres (table-local x), row order Trips, Ante, Blind, Play. */
	private static final int[] CIRCLE_X = {204, 238, 272, 306};

	/**
	 * Another seat.
	 *
	 * @param stackX combined bet stack centre
	 */
	public record Seat(int id, int plateX, int plateY, int cardsX, int cardsY, int stackX, int stackY) {
		public Rect card(int k) {
			return new Rect(cardsX + 12 * k, cardsY, M_W, M_H);
		}

		public Rect cards() {
			return new Rect(cardsX, cardsY, 12 + M_W, M_H);
		}
	}

	private static final Seat LOWER_LEFT = seat(0, 22, 108, 52, 70, 86, 104);
	private static final Seat LOWER_RIGHT = seat(1, 332, 100, 352, 64, 340, 96);
	private static final Seat UPPER_LEFT = seat(2, -2, 8, 18, 34, 58, 70);
	private static final Seat UPPER_RIGHT = seat(3, 336, 8, 360, 34, 346, 72);
	private static final Seat RAIL_LEFT = seat(4, -2, 150, 62, 150, 58, 146);
	/** Lower-right with 4–6 seats: the upper-right plate takes the space above, so the cards drop to the rail. */
	private static final Seat LOWER_RIGHT_CROWDED = seat(5, 332, 104, 358, 132, 340, 98);

	private static Seat seat(int id, int px, int py, int cx, int cy, int sx, int sy) {
		return new Seat(id, TABLE_X + px, TABLE_Y + py, TABLE_X + cx, TABLE_Y + cy, TABLE_X + sx, TABLE_Y + sy);
	}

	private UthLayout() {}

	/** Slots for {@code others} other seats, clockwise from the viewer (bottom-left rail → lower-left → … → lower-right). */
	public static Seat[] others(int others) {
		return switch (Math.max(0, Math.min(5, others))) {
			case 0 -> new Seat[0];
			case 1 -> new Seat[] {LOWER_LEFT};
			case 2 -> new Seat[] {LOWER_LEFT, LOWER_RIGHT};
			case 3 -> new Seat[] {LOWER_LEFT, UPPER_LEFT, LOWER_RIGHT};
			case 4 -> new Seat[] {LOWER_LEFT, UPPER_LEFT, UPPER_RIGHT, LOWER_RIGHT_CROWDED};
			default -> new Seat[] {RAIL_LEFT, LOWER_LEFT, UPPER_LEFT, UPPER_RIGHT, LOWER_RIGHT_CROWDED};
		};
	}

	/** With 4–6 seats the paytable panel folds into a button and the deck moves left (visual §6.5). */
	public static boolean crowded(int others) {
		return others >= 3;
	}

	public static Rect deck(int others) {
		return crowded(others) ? new Rect(TABLE_X + 300, TABLE_Y + 12, 21, 32) : new Rect(TABLE_X + 352, TABLE_Y + 12, 21, 32);
	}

	public static int[] dealOrigin(int others) {
		Rect d = deck(others);
		return new int[] {d.x() + 10, d.y() + 12};
	}

	public static Rect dealer(int k) {
		return new Rect(TABLE_X + 164 + 41 * k, TABLE_Y + 12, L_W, L_H);
	}

	public static Rect board(int i) {
		return new Rect(TABLE_X + 106 + 40 * i, TABLE_Y + 64, L_W, L_H);
	}

	public static Rect paytable() {
		return new Rect(TABLE_X + 16, TABLE_Y + 12, 112, 46);
	}

	public static Rect hole(int k) {
		return new Rect(TABLE_X + 100 + 41 * k, TABLE_Y + 128, L_W, L_H);
	}

	/** Circle {@code c} (0 Trips, 1 Ante, 2 Blind, 3 Play) of the viewer: its 24 × 24 print. */
	public static Rect circle(int c) {
		return new Rect(TABLE_X + CIRCLE_X[c] - CIRCLE / 2, TABLE_Y + 146 - CIRCLE / 2, CIRCLE, CIRCLE);
	}

	/** Centre of the circle's label (above the circle, y 121 table-local). */
	public static int[] circleLabel(int c) {
		return new int[] {TABLE_X + CIRCLE_X[c], TABLE_Y + 121};
	}

	/** The caption over circle {@code c} ("Trips", «Трипс»…): fitted to 40 px, one text row (glyphs + shadow). */
	public static Rect circleCaption(int c) {
		int[] l = circleLabel(c);
		return new Rect(l[0] - 20, l[1], 40, 9);
	}

	/** Tilt of the Trips / Blind bonus stamps (degrees). */
	public static final double STAMP_TILT = -4;

	/**
	 * Placement box of a bonus stamp whose art is {@code artW} × 16 (text + 14): the bounding box of the art tilted by
	 * {@link #STAMP_TILT}, plus 1 px of air on every side, so the drawn stamp never touches a caption or a card.
	 */
	public static int[] stampBox(int artW) {
		double a = Math.toRadians(Math.abs(STAMP_TILT));
		int w = (int) Math.ceil(artW * Math.cos(a) + 16 * Math.sin(a)) + 2;
		int h = (int) Math.ceil(artW * Math.sin(a) + 16 * Math.cos(a)) + 2;
		return new int[] {w + (w & 1), h + (h & 1)};
	}

	/** What a bonus stamp never covers: every card (dealer, board, hole cards, the seats', the deck), the circles, their captions. */
	public static java.util.List<Rect> stampObstacles(int others) {
		java.util.List<Rect> out = cardRects(others);
		for (int c = 0; c < 4; c++) {
			out.add(circle(c));
			out.add(circleCaption(c));
		}
		return out;
	}

	/**
	 * Bonus stamp request for circle {@code c} ({@code artW} = text + 14): under its circle (right of the viewer's hole
	 * cards), else under the row at the right edge (both also bottom-aligned on the felt), else beside the row on the right. The captions sit above the
	 * circles, so a stamp never goes there.
	 */
	public static LabelPlacer.Request bonusStamp(int c, int artW) {
		int[] box = stampBox(artW);
		Rect r = circle(c);
		Rect hole = hole(1);
		Rect b = labelBounds();
		int under = Math.max(r.cx() - box[0] / 2, hole.x() + hole.w() + 1);
		int y = r.y() + r.h() + 1;
		Rect play = circle(3);
		int low = b.y() + b.h() - box[1]; // bottom-aligned: clears a crowded lower-right seat's cards on the rail
		int right = b.x() + b.w() - box[0];
		return new LabelPlacer.Request(box[0], box[1], under, y, right, y, under, low, right, low, play.x() + play.w() + 3, r.cy() - box[1] / 2);
	}

	/** Pitch between circle centres (the Blind mirror slide distance, animation/cards.md §3.2). */
	public static int circlePitch() {
		return CIRCLE_X[1] - CIRCLE_X[0];
	}

	/** The rack on the dealer edge (payout source, sweep target). */
	public static Rect rack() {
		return new Rect(TABLE_X + 164, TABLE_Y - 1, 80, 14);
	}

	/** Hand-name tag candidates for the viewer: above the hole cards, then left of them. */
	public static LabelPlacer.Request viewerTag(int w) {
		Rect a = hole(0);
		Rect b = hole(1);
		int cx = (a.x() + b.x() + b.w()) / 2 - w / 2;
		return new LabelPlacer.Request(w, 11, cx, a.y() - 13, a.x() - w - 3, a.y() + 4);
	}

	/** Tag candidates for another seat: under its plate, above its cards, beside them. */
	public static LabelPlacer.Request seatTag(Seat s, int plateW, int w) {
		Rect c = s.cards();
		int px = Math.min(s.plateX(), CANVAS_W - 2 - plateW);
		return new LabelPlacer.Request(w, 11, px + plateW / 2 - w / 2, s.plateY() + PLATE_H + 2, c.cx() - w / 2, c.y() - 13,
			px + plateW / 2 - w / 2, s.plateY() - 13);
	}

	/** Every card rectangle for {@code others} other seats (dealer, board, viewer, seats). */
	public static java.util.List<Rect> cardRects(int others) {
		java.util.List<Rect> out = new java.util.ArrayList<>();
		out.add(dealer(0));
		out.add(dealer(1));
		for (int i = 0; i < 5; i++) out.add(board(i));
		out.add(hole(0));
		out.add(hole(1));
		for (Seat s : others(others)) {
			out.add(s.card(0));
			out.add(s.card(1));
		}
		out.add(deck(others));
		return out;
	}

	public static Rect labelBounds() {
		return new Rect(2, 0, CANVAS_W - 4, 206);
	}
}

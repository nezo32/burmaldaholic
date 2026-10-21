package dev.nezo.burmaldaholic.games.uth.present;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.SeatColumn;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;

/**
 * Ultimate Texas Hold'em v2 table layout (REDESIGN.md §7.4; docs/architecture/ui-v2.md §3): the dealer's two cards
 * at the top centre with the dealer pill left of them, the five board cards (hero size) centred at 0.42 of the
 * PlayArea, the viewer's two hero cards bottom-centre with the plate left and the hand pill above, the four bet spots
 * Trips · Ante · Blind · Play as a row right of the cards (felt-print label, the circle, the value pill), and up to
 * five other seats as rows in the two side columns (cards + plate + status pill): {@code others[0 .. ⌈n/2⌉)} in the
 * left column bottom → top, the rest in the right
 * column top → bottom (clockwise from the viewer). Pure; audited at every size × 0–5 others in the unit test.
 */
public final class UthTableLayout {
	public static final String[] SPOTS = {"trips", "ante", "blind", "play"};
	public static final int PILL_H = SeatColumn.PILL_1X;

	/** Another seat: plate (name), pill (bet / status / net) under it, two seat-size cards beside it. */
	public record Other(Box plate, Box pill, Box cards, int step) {
		public Box card(int k, Tier t) {
			return new Box(cards.x() + k * step, cards.y(), t.seatW, t.seatH);
		}
	}

	/**
	 * @param spots       the four bet circles (Trips, Ante, Blind, Play)
	 * @param spotLabels  their felt-print labels above
	 * @param spotValues  their value pills below
	 * @param banner      the free band between the board and the viewer's cards (ResultBanner region)
	 */
	public record Layout(TableGrid grid, Box dealer, int dealerStep, Box dealerPill, Box[] board, Box hero, int heroStep, Box heroPlate, Box heroHand,
			Box[] spots, Box[] spotLabels, Box[] spotValues, Other[] others, Box banner, Box deck) {
		public Box dealerCard(int k) {
			Tier t = grid.tier;
			return new Box(dealer.x() + k * dealerStep, dealer.y(), t.heroW, t.heroH);
		}

		public Box heroCard(int k) {
			Tier t = grid.tier;
			return new Box(hero.x() + k * heroStep, hero.y(), t.heroW, t.heroH);
		}

		public int spotIndex(String id) {
			for (int i = 0; i < SPOTS.length; i++) if (SPOTS[i].equals(id)) return i;
			return -1;
		}
	}

	private UthTableLayout() {}

	/**
	 * @param others      other seats shown (0..5)
	 * @param dealerPillW measured width of the dealer pill ("Dealer · Two pair")
	 * @param handW       measured width of the hero's hand pill
	 * @param labelW      widest spot label (felt print)
	 * @param pillW       other seats' status pill width (≤ plate width)
	 */
	public static Layout of(TableGrid g, int others, int dealerPillW, int handW, int labelW, int pillW) {
		Tier t = g.tier;
		Box play = g.play;
		Box felt = g.felt();
		int cx = play.x() + play.w() / 2;
		int cw = t.heroW;
		int ch = t.heroH;
		// dealer: two hero cards at the top centre, fanned like the viewer's
		int dStep = cw + 3;
		int dW = cw + dStep;
		Box dealer = new Box(cx - dW / 2, play.y() + 4, dW, ch);
		int dpw = Math.min(dealerPillW, dealer.x() - felt.x() - 6);
		Box dealerPill = new Box(dealer.x() - 6 - dpw, dealer.y() + (ch - PILL_H) / 2, Math.max(0, dpw), PILL_H);
		Box deck = new Box(dealer.right() + 8, dealer.y() + 4, t.seatW, t.seatH);
		// board: five hero cards centred on 0.42 PH (never above the dealer's cards)
		int gap = t == Tier.T1 ? 3 : 4;
		int boardW = 5 * cw + 4 * gap;
		int by = Math.max(dealer.bottom() + 6, g.fy(0.42) - ch / 2);
		Box[] board = new Box[5];
		for (int i = 0; i < 5; i++) board[i] = new Box(cx - boardW / 2 + i * (cw + gap), by, cw, ch);
		// the viewer
		int hStep = cw * 2 / 3;
		int hW = cw + hStep;
		Box hero = new Box(cx - hW / 2, play.bottom() - 4 - ch, hW, ch);
		int plateW = t.plateW;
		Box heroPlate = new Box(hero.x() - 8 - plateW, play.bottom() - 4 - Tier.PLATE_H, plateW, Tier.PLATE_H);
		// the spots: Trips · Ante · Blind · Play right of the viewer's cards
		int d = t == Tier.T1 ? 18 : t == Tier.T2 ? 24 : 30;
		int pitch = Math.max(d, labelW) + (t == Tier.T1 ? 3 : 5);
		int avail = felt.right() - (hero.right() + 8);
		if (4 * pitch > avail) pitch = Math.max(d + 2, avail / 4);
		int lw = pitch - 2;
		int colH = PILL_H + 1 + d + 1 + PILL_H;
		int sy = Math.min(hero.y() + (ch - colH) / 2, felt.bottom() - colH);
		Box[] spots = new Box[4];
		Box[] labels = new Box[4];
		Box[] values = new Box[4];
		for (int i = 0; i < 4; i++) {
			int x0 = hero.right() + 8 + i * pitch;
			int mid = x0 + pitch / 2;
			labels[i] = new Box(mid - lw / 2, sy, lw, PILL_H);
			spots[i] = new Box(mid - d / 2, sy + PILL_H + 1, d, d);
			values[i] = new Box(mid - lw / 2, spots[i].bottom() + 1, lw, PILL_H);
		}
		// the hand pill above the viewer's cards, kept left of the spots
		int spotsX = labels[0].x() - 4;
		int hw = Math.min(handW, spotsX - felt.x());
		Box heroHand = new Box(Math.max(felt.x(), Math.min(cx - hw / 2, spotsX - hw)), hero.y() - 3 - PILL_H, hw, PILL_H);
		// the others: side columns of rows
		int nLeft = (others + 1) / 2;
		int nRight = others / 2;
		Other[] out = new Other[Math.max(0, others)];
		int rowH = Math.max(t.seatH, Tier.PLATE_H + 1 + PILL_H) + (t == Tier.T1 ? 3 : 6);
		int sStep = Math.max(SeatColumn.MIN_STEP, t.seatW / 2 - 1);
		int cardsW = t.seatW + sStep;
		int rowW = cardsW + 3 + plateW;
		int top = play.y() + 4;
		// left column: from just above the viewer's plate upwards
		int leftBottom = heroPlate.y() - 4;
		int leftRight = Math.min(board[0].x(), dealerPill.isEmpty() ? dealer.x() : dealerPill.x()) - 4;
		for (int k = 0; k < nLeft; k++) {
			int y = leftBottom - (k + 1) * rowH + (rowH - Math.max(t.seatH, Tier.PLATE_H + 1 + PILL_H));
			y = Math.max(top + (nLeft - 1 - k) * rowH, Math.min(y, leftBottom - rowH));
			int x = felt.x();
			int w = Math.min(rowW, leftRight - x);
			int pw = Math.max(24, w - cardsW - 3);
			Box plate = new Box(x, y, pw, Tier.PLATE_H);
			Box pill = new Box(x, plate.bottom() + 1, Math.min(pillW, pw), PILL_H);
			Box cards = new Box(plate.right() + 3, y, cardsW, t.seatH);
			out[k] = new Other(plate, pill, cards, sStep);
		}
		// right column: from the top down to above the spots
		int rightLeft = Math.max(board[4].right(), deck.right()) + 4;
		for (int k = 0; k < nRight; k++) {
			int y = top + k * rowH;
			int w = Math.min(rowW, felt.right() - rightLeft);
			int pw = Math.max(24, w - cardsW - 3);
			int x = felt.right() - pw;
			Box plate = new Box(x, y, pw, Tier.PLATE_H);
			Box pill = new Box(felt.right() - Math.min(pillW, pw), plate.bottom() + 1, Math.min(pillW, pw), PILL_H);
			Box cards = new Box(x - 3 - cardsW, y, cardsW, t.seatH);
			out[nLeft + k] = new Other(plate, pill, cards, sStep);
		}
		int bannerTop = board[0].bottom() + 2;
		Box banner = new Box(board[0].x(), bannerTop, boardW, Math.max(0, heroHand.y() - 2 - bannerTop));
		return new Layout(g, dealer, dStep, dealerPill, board, hero, hStep, heroPlate, heroHand, spots, labels, values, out, banner, deck);
	}
}

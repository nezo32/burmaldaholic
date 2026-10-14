package dev.nezo.burmaldaholic.games.baccarat.present;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.SeatColumn;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;

/**
 * Pure layout of the baccarat / chemmy v2 PlayArea (REDESIGN.md §7.2): the PLAYER (left, blue edge) and BANKER
 * (right, red edge) hand boxes in the upper half with a 2× total pill in each header; the row of bet boxes
 * P.PAIR · PLAYER · TIE · BANKER · B.PAIR (widths 0.14 / 0.22 / 0.14 / 0.22 / 0.14, renormalised over the open
 * boxes) with name and payout printed inside; the seat plates along the bottom rail (up to 7). Audited by
 * {@code BaccaratLayoutTest} at every size.
 *
 * @param player      the PLAYER hand box
 * @param banker      the BANKER hand box
 * @param playerCards the fan inside it (room for 3 cards)
 * @param bankerCards the fan inside it
 * @param playerTotal 2× total pill in the header
 * @param bankerTotal 2× total pill in the header
 * @param playerLabel header word
 * @param bankerLabel header word
 * @param boxes       per box (P, B, T, P.PAIR, B.PAIR): the felt box, empty when closed
 * @param boxName     per box: the name line inside the box
 * @param boxPays     per box: the payout line inside the box
 * @param mine        per box: the viewer's stack + its value pill to the right
 * @param mineValue   per box: the viewer's value pill
 * @param others      per box: the others' total pill
 * @param plates      per seat slot: the plate
 * @param plateValue  per seat slot: the bet pill above the plate
 * @param banner      the free region for the coup banner (between hands and boxes), may be empty
 */
public record BaccaratLayout(Box player, Box banker, Box playerCards, Box bankerCards, Box playerTotal, Box bankerTotal, Box playerLabel, Box bankerLabel,
		Box[] boxes, Box[] boxName, Box[] boxPays, Box[] mine, Box[] mineValue, Box[] others, Box[] plates, Box[] plateValue, Box banner) {
	private static final double[] WEIGHTS = {0.22, 0.22, 0.14, 0.14, 0.14};
	/** Left-to-right order of the boxes on the felt. */
	public static final int[] ORDER = {BaccaratPub.PP, BaccaratPub.P, BaccaratPub.T, BaccaratPub.B, BaccaratPub.BP};

	/**
	 * @param open     per box: open at this table (chemmy: P and B only; pairs only when the table offers them)
	 * @param seats    seat slots along the rail (1..7)
	 * @param labelW   measured widths of the header words (PLAYER, BANKER)
	 */
	public static BaccaratLayout of(TableGrid g, boolean[] open, int seats, int[] labelW) {
		Tier t = g.tier;
		Box play = g.play;
		Box felt = g.felt();
		int m = t.margin;
		int cw = t == Tier.T1 ? t.seatW : t.heroW;
		int ch = t == Tier.T1 ? t.seatH : t.heroH;
		int header = SeatColumn.PILL_2X + 2;
		int handH = header + ch + 6;
		int handW = Math.min((int) Math.round(play.w() * 0.42), cw * 3 + 2 * (cw / 2 + 2) + 24);
		int hy = play.y() + m;
		int gap = Math.max(8, play.w() / 40);
		Box player = new Box(play.cx() - gap / 2 - handW, hy, handW, handH);
		Box banker = new Box(play.cx() + gap / 2 + (gap & 1), hy, handW, handH);
		int fanW = cw + 2 * (cw / 2 + 2);
		Box playerCards = new Box(player.cx() - fanW / 2, player.y() + header + 2, fanW, ch);
		Box bankerCards = new Box(banker.cx() - fanW / 2, banker.y() + header + 2, fanW, ch);
		int tw = 28;
		Box playerLabel = new Box(player.x() + 6, player.y() + 6, Math.min(labelW[0], handW - tw - 16), 9);
		Box bankerLabel = new Box(banker.x() + 6, banker.y() + 6, Math.min(labelW[1], handW - tw - 16), 9);
		Box playerTotal = new Box(player.right() - 4 - tw, player.y() + 2, tw, SeatColumn.PILL_2X);
		Box bankerTotal = new Box(banker.right() - 4 - tw, banker.y() + 2, tw, SeatColumn.PILL_2X);

		// plates along the bottom rail
		int slots = Math.max(1, seats);
		int plateY = play.bottom() - 4 - Tier.PLATE_H;
		int span = felt.w() - 2;
		int pitch = span / slots;
		int plateW = Math.min(t.plateW, pitch - 4);
		Box[] plates = new Box[slots];
		Box[] plateValue = new Box[slots];
		for (int i = 0; i < slots; i++) {
			int cx = felt.x() + 1 + pitch * i + pitch / 2;
			plates[i] = new Box(cx - plateW / 2, plateY, plateW, Tier.PLATE_H);
			plateValue[i] = Box.EMPTY; // the bet shows on the boxes; the plate is the name only (REDESIGN §4)
		}

		// bet boxes between the hands and the plates
		int by = player.bottom() + (t == Tier.T1 ? 4 : 8);
		int bh = plateY - 4 - by;
		double sum = 0;
		for (int k = 0; k < BaccaratPub.BOXES; k++) if (open[k]) sum += WEIGHTS[k];
		int rowW = felt.w() - 4;
		int x = felt.x() + 2;
		Box[] boxes = new Box[BaccaratPub.BOXES];
		Box[] name = new Box[BaccaratPub.BOXES];
		Box[] pays = new Box[BaccaratPub.BOXES];
		Box[] mine = new Box[BaccaratPub.BOXES];
		Box[] mineValue = new Box[BaccaratPub.BOXES];
		Box[] others = new Box[BaccaratPub.BOXES];
		int used = 0;
		int openCount = 0;
		for (int k : ORDER) if (open[k]) openCount++;
		int seen = 0;
		for (int k : ORDER) {
			if (!open[k]) {
				boxes[k] = name[k] = pays[k] = mine[k] = mineValue[k] = others[k] = Box.EMPTY;
				continue;
			}
			seen++;
			int w = seen == openCount ? rowW - used : (int) Math.round(rowW * WEIGHTS[k] / sum);
			Box b = new Box(x + used + 1, by, Math.max(0, w - 2), Math.max(0, bh));
			used += w;
			boxes[k] = b;
			name[k] = new Box(b.x() + 2, b.y() + 4, b.w() - 4, 9);
			pays[k] = new Box(b.x() + 2, name[k].bottom() + 2, b.w() - 4, 9);
			int chip = t.feltChip;
			int stackH = chip + 4;
			int vw = Math.min(b.w() - chip - 8, 40);
			int sy = b.bottom() - 4 - stackH;
			mine[k] = new Box(b.cx() - (chip + 2 + vw) / 2, sy, chip, stackH);
			mineValue[k] = new Box(mine[k].right() + 2, sy + (stackH - SeatColumn.PILL_1X) / 2, Math.max(0, vw), SeatColumn.PILL_1X);
			others[k] = new Box(b.x() + 3, pays[k].bottom() + 2, Math.max(0, Math.min(b.w() - 6, 40)), SeatColumn.PILL_1X);
			if (others[k].bottom() > mine[k].y() - 1) others[k] = Box.EMPTY; // no room: the others' total goes to the board only
		}
		Box banner = Box.EMPTY;
		return new BaccaratLayout(player, banker, playerCards, bankerCards, playerTotal, bankerTotal, playerLabel, bankerLabel, boxes, name, pays, mine,
			mineValue, others, plates, plateValue, banner);
	}

	/** Card boxes inside a hand fan (≤ 3; the third card lies sideways in real baccarat, here it follows the fan). */
	public static Box card(Box fan, int k, int cw) {
		int step = cw / 2 + 2;
		return new Box(fan.x() + k * step, fan.y(), cw, fan.h());
	}
}

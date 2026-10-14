package dev.nezo.burmaldaholic.games.blackjack.present;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.SeatColumn;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;

/**
 * Pure layout of the blackjack v2 PlayArea (REDESIGN.md §7.1 with the amendments of docs/architecture/ui-v2.md §2):
 * dealer rail + chip rack, the dealer's fan with its total left and the house rule right, shoe and discard tray, felt
 * prints, and the crescent of seat columns with the viewer always in the middle slot (the server seat order is
 * rotated). Every box comes from here; the screen only draws and hit-tests them, and {@code BlackjackLayoutTest}
 * audits them at every size.
 *
 * @param rail        the dealer rail (top of the felt)
 * @param rack        the dealer's chip rack on the rail
 * @param shoe        the shoe (top right)
 * @param discard     the discard tray (top left)
 * @param dealer      the dealer's cards
 * @param dealerTotal the dealer's 2× total pill, left of the cards
 * @param dealerNote  "Dealer stands on 17" (decor, may be empty)
 * @param prints      felt prints (decor, 0..2 lines; each empty when it would touch content)
 * @param banner      the free region for the round banner between the dealer and the seats (may be empty)
 * @param columns     per slot: its seat column, or null for an empty slot
 * @param spots       per slot: the printed betting box (always; the empty seat's "Sit" target, the hero's bet spot)
 * @param levels      per slot: the outcome level chosen by {@link SeatColumn#table}
 * @param hero        slot of the viewer (-1 = watching)
 * @param slotOfSeat  server seat index → slot
 */
public record BlackjackLayout(Box rail, Box rack, Box shoe, Box discard, Box dealer, Box dealerTotal, Box dealerNote, Box[] prints, Box banner,
		SeatColumn[] columns, Box[] spots, int[] levels, int hero, int[] slotOfSeat) {
	/**
	 * One seat as the screen sees it.
	 *
	 * @param cards    cards per hand (empty = seat with no hand: betting lobby / sat out); null = the seat is empty
	 * @param outcomeW widths of the outcome levels, largest first
	 * @param valueW   width of the bet value pill (0 = no bet)
	 */
	public record SeatIn(int[] cards, int[] outcomeW, int valueW) {
		public int fanCards(int handGap) {
			if (cards.length == 0) return 1;
			int n = 0;
			for (int c : cards) n += Math.max(1, c);
			return n + (cards.length - 1) * handGap;
		}
	}

	/** Gap between split hands, in card steps (a split hand starts ~1 card further). */
	public static final int HAND_GAP = 1;

	/**
	 * @param seats      per server seat index: the seat, or null when empty
	 * @param heroSeat   the viewer's server seat, -1 when watching
	 * @param dealerCards cards in the dealer's fan (≥ 2 laid out so the row does not jump)
	 * @param dealerTotalW measured width of the dealer's total pill (0 = none)
	 * @param noteW      measured width of the house rule note (2 lines, the widest)
	 * @param printW     measured widths of the felt prints
	 */
	public static BlackjackLayout of(TableGrid g, SeatIn[] seats, int heroSeat, int dealerCards, int dealerTotalW, int noteW, int[] printW) {
		Tier t = g.tier;
		int m = t.margin;
		int n = seats.length;
		Box play = g.play;
		Box felt = g.felt();
		Box rail = new Box(0, play.y(), play.w(), Math.max(6, t.margin + 2));
		int rackW = Math.min(play.w() / 4, t == Tier.T3 ? 106 : t == Tier.T2 ? 80 : 60);
		Box rack = new Box(play.cx() - rackW / 2, rail.y(), rackW, rail.h() + (t == Tier.T1 ? 4 : 6));
		int shoeW = t == Tier.T3 ? 52 : t == Tier.T2 ? 40 : 28;
		int shoeH = shoeW * 2 / 3;
		Box shoe = new Box(play.right() - m - shoeW - (t == Tier.T1 ? 0 : 6), rail.bottom() + 4, shoeW, shoeH);
		Box discard = new Box(m + (t == Tier.T1 ? 0 : 6), rail.bottom() + 4, shoeW * 3 / 4, shoeH);
		// dealer fan: hero-size cards (seat size at T1), centred
		int cw = t == Tier.T1 ? t.seatW : t.heroW;
		int ch = t == Tier.T1 ? t.seatH : t.heroH;
		int dn = Math.max(2, dealerCards);
		int step = cw / 2 + 2;
		int maxFan = shoe.x() - discard.right() - 2 * (dealerTotalW + 12);
		if (cw + (dn - 1) * step > maxFan) step = Math.max(SeatColumn.MIN_STEP, (maxFan - cw) / (dn - 1));
		int fanW = cw + (dn - 1) * step;
		Box dealer = new Box(play.cx() - fanW / 2, rack.bottom() + 2, fanW, ch);
		Box dealerTotal = dealerTotalW <= 0 ? Box.EMPTY
			: new Box(dealer.x() - 6 - dealerTotalW, dealer.y() + 6, dealerTotalW, SeatColumn.PILL_2X);
		Box dealerNote = new Box(dealer.right() + 8, dealer.y() + 6, noteW, 22);
		if (noteW <= 0 || dealerNote.right() > shoe.x() - 4) dealerNote = Box.EMPTY;

		// seats: rotate so the viewer is in the middle slot
		int slots = n % 2 == 0 ? n + 1 : n;
		int mid = slots / 2;
		int[] slotOfSeat = new int[n];
		for (int s = 0; s < n; s++) {
			int rel = heroSeat >= 0 ? Math.floorMod(s - heroSeat, n) : s - n / 2;
			// relative position around the hero, alternating table order: hero, right…, left…
			slotOfSeat[s] = heroSeat >= 0 ? Math.floorMod(mid + (rel <= n / 2 ? rel : rel - n), slots) : Math.floorMod(mid + rel, slots);
		}
		int heroSlot = heroSeat >= 0 ? mid : -1;
		int[] cards = new int[slots];
		int[][] outcomeW = new int[slots][];
		int[] valueW = new int[slots];
		java.util.Arrays.fill(cards, -1);
		for (int i = 0; i < slots; i++) outcomeW[i] = new int[0];
		for (int s = 0; s < n; s++) {
			SeatIn in = seats[s];
			if (in == null) continue;
			int slot = slotOfSeat[s];
			cards[slot] = in.fanCards(HAND_GAP);
			outcomeW[slot] = in.outcomeW();
			valueW[slot] = Math.max(in.valueW(), 12);
		}
		int[] levels = new int[slots];
		SeatColumn[] cols = SeatColumn.table(g, slots, cards, heroSlot, outcomeW, valueW, levels);
		// printed betting boxes: one per slot at the cards' place (a ring the size of a seat card)
		int[][] anchors = SeatColumn.crescent(g, slots);
		Box[] spots = new Box[slots];
		int ring = t == Tier.T3 ? 36 : t == Tier.T2 ? 28 : 20;
		for (int i = 0; i < slots; i++) {
			boolean hero = i == heroSlot;
			int r = hero ? ring + 8 : ring;
			int cy = anchors[i][1] - (hero ? t.heroH : t.seatH) / 2 - t.feltChip;
			spots[i] = Box.centredAt(anchors[i][0], cy, r, r);
		}
		// banner region: between the dealer's row and the highest box of the seats in the middle third
		int top = Math.max(dealer.bottom(), dealerTotal.isEmpty() ? 0 : dealerTotal.bottom()) + 3;
		int bottom = play.bottom();
		int x0 = g.fx(0.25);
		int x1 = g.fx(0.75);
		for (int i = 0; i < slots; i++) {
			if (cols[i] == null) continue;
			for (Box b : cols[i].boxes()) {
				if (!b.isEmpty() && b.right() > x0 && b.x() < x1) bottom = Math.min(bottom, b.y() - 3);
			}
		}
		Box banner = bottom - top >= SeatColumn.PILL_1X + 4 ? new Box(x0, top, x1 - x0, bottom - top) : Box.EMPTY;
		// felt prints: centred under the dealer, kept only where they touch nothing
		Box[] prints = new Box[printW.length];
		int py = dealer.bottom() + 6;
		for (int k = 0; k < printW.length; k++) {
			Box p = Box.centredAt(play.cx(), py + k * 11 + 5, printW[k], 9);
			boolean ok = p.inside(felt) && !p.intersects(dealer) && !p.intersects(dealerTotal) && (t != Tier.T1 || k == 0) && !(t == Tier.T2 && k > 1);
			for (int i = 0; ok && i < slots; i++) {
				if (cols[i] != null) for (Box b : cols[i].boxes()) ok &= !p.intersects(b);
				if (cols[i] == null || i == heroSlot) ok &= !p.intersects(spots[i]);
			}
			prints[k] = ok ? p : Box.EMPTY;
		}
		return new BlackjackLayout(rail, rack, shoe, discard, dealer, dealerTotal, dealerNote, prints, banner, cols, spots, levels, heroSlot, slotOfSeat);
	}

	/** Splits a column's card box into one fan box per hand (left to right), using the same step as the column. */
	public static Box[] handFans(Box cards, int[] perHand, int cw) {
		int hands = Math.max(1, perHand.length);
		Box[] out = new Box[hands];
		if (perHand.length <= 1) {
			out[0] = cards;
			return out;
		}
		int total = 0;
		for (int c : perHand) total += Math.max(1, c);
		total += (perHand.length - 1) * HAND_GAP;
		int step = total > 1 ? (cards.w() - cw) / (total - 1) : 0;
		int x = cards.x();
		for (int h = 0; h < hands; h++) {
			int c = Math.max(1, perHand[h]);
			int w = cw + (c - 1) * step;
			out[h] = new Box(x, cards.y(), w, cards.h());
			x += (c + HAND_GAP) * step;
		}
		return out;
	}

	/** The fan step inside a box of {@code n} cards of width {@code cw}. */
	public static int step(Box fan, int n, int cw) {
		return n > 1 ? (fan.w() - cw) / (n - 1) : 0;
	}
}

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
 * @param resultBanner the region for the viewer's result banner: as {@code banner} but over the hero's outcome slot (its
 *                    total pill hides under the banner for its ~1.5 s; the stamp on the cards carries the outcome)
 * @param columns     per slot: its seat column, or null for an empty slot
 * @param spots       per slot: the printed betting box (always; the empty seat's "Sit" target, the hero's bet spot)
 * @param levels      per slot: the outcome level chosen by {@link SeatColumn#table}
 * @param hero        slot of the viewer (-1 = watching)
 * @param slotOfSeat  server seat index → slot
 */
public record BlackjackLayout(Box rail, Box rack, Box shoe, Box discard, Box dealer, Box dealerTotal, Box dealerNote, Box[] prints, Box banner,
		Box resultBanner, SeatColumn[] columns, Box[] spots, int[] levels, int hero, int[] slotOfSeat) {
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
		boolean[] handless = new boolean[slots];
		java.util.Arrays.fill(cards, -1);
		for (int i = 0; i < slots; i++) outcomeW[i] = new int[0];
		for (int s = 0; s < n; s++) {
			SeatIn in = seats[s];
			if (in == null) continue;
			int slot = slotOfSeat[s];
			cards[slot] = in.fanCards(HAND_GAP);
			handless[slot] = in.cards().length == 0;
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
		int rbottom = play.bottom();
		for (int i = 0; i < slots; i++) {
			if (cols[i] == null) continue;
			for (Box b : cols[i].boxes()) {
				if (i == heroSlot && b == cols[i].outcome()) continue;
				if (!b.isEmpty() && b.right() > x0 && b.x() < x1) rbottom = Math.min(rbottom, b.y() - 3);
			}
		}
		Box resultBanner = rbottom - top >= SeatColumn.PILL_1X + 4 ? new Box(x0, top, x1 - x0, rbottom - top) : Box.EMPTY;
		// felt prints: centred under the dealer, kept only where they touch nothing
		Box[] prints = new Box[printW.length];
		int py = dealer.bottom() + 6;
		for (int k = 0; k < printW.length; k++) {
			Box p = Box.centredAt(play.cx(), py + k * 11 + 5, printW[k], 9);
			boolean ok = p.inside(felt) && !p.intersects(dealer) && !p.intersects(dealerTotal) && (t != Tier.T1 || k == 0) && !(t == Tier.T2 && k > 1);
			for (int i = 0; ok && i < slots; i++) {
				// the outcome slot of a seat without hands is only the reserved dot and draws nothing: the print may cross it (A-0001: the
				// second line "INSURANCE PAYS 2 TO 1" was dropped at 480 x 270 by the hero's empty slot)
				if (cols[i] != null) for (Box b : cols[i].boxes()) ok &= !p.intersects(b) || handless[i] && b == cols[i].outcome() && b.w() <= SeatColumn.DOT;
				if (cols[i] == null || i == heroSlot) ok &= !p.intersects(spots[i]);
			}
			prints[k] = ok ? p : Box.EMPTY;
		}
		return new BlackjackLayout(rail, rack, shoe, discard, dealer, dealerTotal, dealerNote, prints, banner, resultBanner, cols, spots, levels, heroSlot, slotOfSeat);
	}

	/**
	 * One hand of a seat (DES blackjack P1 split): its fan, the card size used, its own 1× total pill above the fan, its
	 * own stack + value pill under it.
	 */
	public record HandBox(Box fan, int cw, int ch, int step, Box total, Box stack, Box value) {
		public Box[] boxes() {
			return new Box[] {fan, total, stack, value};
		}
	}

	/** Gap between two split hands (px). */
	public static final int SPLIT_GAP = 4;

	/**
	 * The hands of a seat side by side (DES blackjack P1, A-0022): one fan per hand {@link #SPLIT_GAP} px apart, centred on
	 * the column, bottom-aligned in its card box; each with its own total pill (1×, above the fan) and its own stack + value
	 * pill (under the fan, in the column's stack row). The hero's cards drop to seat size when two hero-size fans would
	 * overlap their rank indices. A single hand is the column itself (its outcome slot shows the total).
	 *
	 * @param perHand cards per hand (≥ 1 each laid out)
	 * @param totalW  measured width of each hand's 1× total pill (0 = none)
	 * @param valueW  measured width of each hand's value pill
	 */
	public static HandBox[] hands(SeatColumn c, Tier t, boolean hero, int[] perHand, int[] totalW, int[] valueW) {
		int n = Math.max(1, perHand.length);
		int cw = hero ? t.heroW : t.seatW;
		int ch = hero ? t.heroH : t.seatH;
		HandBox[] out = new HandBox[n];
		if (perHand.length <= 1) {
			int cards = perHand.length == 0 ? 1 : Math.max(1, perHand[0]);
			out[0] = new HandBox(c.cards(), cw, ch, step(c.cards(), cards, cw), Box.EMPTY, c.stack(), c.value());
			return out;
		}
		int w = c.cards().w();
		int extra = 0;
		for (int k : perHand) extra += Math.max(1, k) - 1;
		int step = splitStep(w, n, cw, extra);
		if (hero && step < SeatColumn.MIN_STEP && extra > 0) {
			cw = t.seatW;
			ch = t.seatH;
			step = splitStep(w, n, cw, extra);
		}
		step = Math.max(step, Math.min(SeatColumn.MIN_STEP, cw / 2 + 2));
		int total = n * cw + (n - 1) * SPLIT_GAP + extra * step;
		int x = c.cards().cx() - total / 2;
		int y = c.cards().bottom() - ch;
		int chip = t.feltChip;
		int prevPill = Integer.MIN_VALUE;
		for (int h = 0; h < n; h++) {
			int k = Math.max(1, perHand[h]);
			Box fan = new Box(x, y, cw + (k - 1) * step, ch);
			Box pill = Box.EMPTY;
			int tw = h < totalW.length ? totalW[h] : 0;
			if (tw > 0) {
				int px = Math.max(fan.cx() - tw / 2, prevPill + 2);
				pill = new Box(px, fan.y() - Tier.GAP - SeatColumn.PILL_1X, tw, SeatColumn.PILL_1X);
				prevPill = pill.right();
			}
			int vw = Math.max(12, h < valueW.length ? valueW[h] : 0);
			Box stack;
			Box value;
			int row = c.stack().y();
			int vy = c.value().isEmpty() ? row : c.value().y();
			if (chip + Tier.GAP + vw <= fan.w()) {
				int sx = fan.cx() - (chip + Tier.GAP + vw) / 2;
				stack = new Box(sx, row, chip, chip + 4);
				value = new Box(sx + chip + Tier.GAP, vy, vw, SeatColumn.PILL_1X);
			} else {
				value = new Box(fan.cx() - vw / 2, vy, vw, SeatColumn.PILL_1X);
				stack = new Box(fan.cx(), vy, 0, 0);
			}
			out[h] = new HandBox(fan, cw, ch, step, pill, stack, value);
			x = fan.right() + SPLIT_GAP;
		}
		return out;
	}

	private static int splitStep(int w, int hands, int cw, int extra) {
		if (extra <= 0) return 0;
		return Math.min(cw / 2 + 2, (w - hands * cw - (hands - 1) * SPLIT_GAP) / extra);
	}

	/**
	 * The insurance bet on the felt (DES blackjack P2, A-0021): a stack + value pill left of the seat's own stack row,
	 * empty when it does not fit inside {@code felt}.
	 *
	 * @return {stack, value}
	 */
	public static Box[] insurance(SeatColumn c, Tier t, int valueW, Box felt, Box[] avoid) {
		int chip = t.feltChip;
		int vw = Math.max(12, valueW);
		int right = (c.stack().isEmpty() || c.stack().w() == 0 ? c.value().x() : c.stack().x()) - 2 * Tier.GAP - 2;
		int y = c.stack().w() == 0 ? c.value().y() - (chip + 4 - SeatColumn.PILL_1X) / 2 : c.stack().y();
		Box stack = new Box(right - vw - Tier.GAP - chip, y, chip, chip + 4);
		Box value = new Box(stack.right() + Tier.GAP, y + (chip + 4 - SeatColumn.PILL_1X) / 2, vw, SeatColumn.PILL_1X);
		boolean ok = stack.inside(felt) && value.inside(felt);
		for (Box b : avoid) ok &= !b.intersects(stack) && !b.intersects(value);
		return ok ? new Box[] {stack, value} : new Box[] {Box.EMPTY, Box.EMPTY};
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

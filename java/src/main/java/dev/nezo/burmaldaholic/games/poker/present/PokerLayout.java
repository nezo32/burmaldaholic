package dev.nezo.burmaldaholic.games.poker.present;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.SeatColumn;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import java.util.ArrayList;
import java.util.List;

/**
 * Texas Hold'em v2 table layout (REDESIGN.md §7.3, mockups {@code v2_poker_turn_480/640.png}; docs/architecture/ui-v2.md
 * §3): the oval felt in the PlayArea, the board (five hero-size cards) with the pot under it, the viewer at the bottom
 * centre (hero cards, plate left of them with the stack pill above, the hand-strength pill and the bet right of them)
 * and the other seats clockwise around the oval (plate + stack pill outside the rail, two seat-size cards inside it,
 * the bet further toward the centre, the dealer-button spot beside the cards).
 *
 * <p>Pure and deterministic: every element is placed by searching, from its preferred spot toward the centre of the
 * table, the first box that is inside the felt and touches nothing placed before it (board, pot, the viewer, then all
 * plates, all cards, all bets, all buttons — in that priority). The unit test audits every size × 2–9 seats.
 * Coordinates are layout px of the v2 virtual GUI (D3: the screen fills the GUI).
 */
public final class PokerLayout {
	/** Slot of one seat. {@code rel} 0 is the viewer (or seat 0 for a spectator); the others clockwise. */
	public record Seat(int rel, Box plate, Box pill, Box cards, int cardStep, Box bet, Box betPill, Box button, Box hand) {
		public Box card(int k, Tier t, boolean hero) {
			int cw = hero ? t.heroW : t.seatW;
			int ch = hero ? t.heroH : t.seatH;
			return new Box(cards.x() + k * cardStep, cards.y(), cw, ch);
		}

		/** Bet chip stack (discs) of this seat: the left part of {@link #bet}. */
		public Box stack(Tier t) {
			return new Box(bet.x(), bet.y(), t.feltChip, bet.h());
		}
	}

	/**
	 * @param oval    the felt oval (bounding box)
	 * @param board   the five board slots
	 * @param pot     the pot readout box ("Pot" 1× + value 2×) under the board
	 * @param seats   by relative index (0 = viewer, clockwise)
	 * @param centre  the table centre (deal origin, pot gather target)
	 */
	public record Layout(TableGrid grid, Box oval, Box[] board, Box pot, Seat[] seats, int[] centre) {
		public Seat seat(int rel) {
			return seats[Math.floorMod(rel, seats.length)];
		}

		/** All boxes of the layout (cards, plates, pills, bets, button spots) for label obstacles and audits. */
		public List<Box> cardBoxes() {
			List<Box> out = new ArrayList<>(List.of(board));
			for (Seat s : seats) out.add(s.cards());
			return out;
		}
	}

	/** Widths the caller measures (current language and font). */
	public record Measure(int stackPillW, int betPillW, int potW, int handW) {
		/** Widest likely values: stacks up to 6 digits, hand names up to ~16 chars at 6 px. */
		public static Measure defaults() {
			return new Measure(6 * 6 + 6, 5 * 6 + 6, 4 * 6 + 2 * 6 * 6 + 6, 16 * 6 + 8);
		}
	}

	public static final int PILL_H = SeatColumn.PILL_1X;
	public static final int BUTTON = 12;

	private PokerLayout() {}

	/** Relative position of table seat {@code seat} for a viewer at {@code viewerSeat} (−1 = spectator: seat 0 at the bottom). */
	public static int rel(int seat, int viewerSeat, int size) {
		return Math.floorMod(seat - Math.max(0, viewerSeat), Math.max(2, size));
	}

	public static Layout of(TableGrid g, int size) {
		return of(g, size, Measure.defaults());
	}

	public static Layout of(TableGrid g, int size, Measure m) {
		int n = Math.max(2, Math.min(9, size));
		Tier t = g.tier;
		Box play = g.play;
		Box felt = g.felt();
		int cx = play.x() + play.w() / 2;
		int cy = play.y() + play.h() / 2 - 4;
		int rx = (int) Math.round(0.43 * play.w());
		int ry = (int) Math.round(0.40 * play.h());
		Box oval = new Box(cx - rx, cy - ry, 2 * rx, 2 * ry);
		List<Box> taken = new ArrayList<>();

		// board: five hero cards, gap 4, centred; the pot under it
		int bw = t.heroW;
		int bh = t.heroH;
		int gap = t == Tier.T1 ? 3 : 4;
		int boardW = 5 * bw + 4 * gap;
		int by = cy - bh / 2 - 8;
		Box[] board = new Box[5];
		for (int i = 0; i < 5; i++) {
			board[i] = new Box(cx - boardW / 2 + i * (bw + gap), by, bw, bh);
			taken.add(board[i]);
		}
		int potW = Math.max(m.potW(), 40);
		Box pot = new Box(cx - potW / 2, by + bh + 2, potW, SeatColumn.PILL_2X);
		taken.add(pot);

		Seat[] seats = new Seat[n];
		seats[0] = hero(g, cx, pot, m, taken);

		// the others: plates first (outside the rail), then cards, then bets, then button spots
		Box[] plates = new Box[n];
		Box[] pills = new Box[n];
		double[][] dir = new double[n][2];
		int[][] at = new int[n][2];
		double prx = rx * 1.06;
		double pry = ry * 1.22;
		for (int r = 1; r < n; r++) {
			double a = Math.toRadians(90 + 360.0 * r / n);
			int px = (int) Math.round(cx + prx * Math.cos(a));
			int py = (int) Math.round(cy + pry * Math.sin(a));
			int plateW = t.plateW;
			int groupW = plateW + 2 + m.stackPillW();
			int gx = Math.max(felt.x(), Math.min(felt.right() - groupW, px - groupW / 2));
			int gy = Math.max(play.y() + 2, Math.min(play.bottom() - 4 - Tier.PLATE_H, py - Tier.PLATE_H / 2));
			Box group = new Box(gx, gy, groupW, Tier.PLATE_H);
			group = free(group, taken, felt, 0, Math.cos(a) > 0 ? -1 : 1, 0, 2);
			plates[r] = new Box(group.x(), group.y(), plateW, Tier.PLATE_H);
			pills[r] = new Box(plates[r].right() + 2, group.y() + (Tier.PLATE_H - PILL_H) / 2, m.stackPillW(), PILL_H);
			taken.add(group);
			double dx = cx - group.cx();
			double dy = cy - group.cy();
			double len = Math.max(1, Math.hypot(dx, dy));
			dir[r][0] = dx / len;
			dir[r][1] = dy / len;
			at[r][0] = group.cx();
			at[r][1] = group.cy();
		}
		int cw = t.seatW;
		int ch = t.seatH;
		int step = Math.max(SeatColumn.MIN_STEP, cw / 2 - 2);
		Box[] cards = new Box[n];
		for (int r = 1; r < n; r++) {
			Box c = Box.centredAt(at[r][0], at[r][1], cw + step, ch);
			cards[r] = free(c, taken, felt, 1, dir[r][0], dir[r][1], 2);
			taken.add(cards[r]);
		}
		int stackH = t.feltChip + 4;
		Box[] bets = new Box[n];
		Box[] betPills = new Box[n];
		for (int r = 1; r < n; r++) {
			int w = t.feltChip + 2 + m.betPillW();
			Box start = Box.centredAt(cards[r].cx(), cards[r].cy(), w, stackH);
			Box b = free(start, taken, felt, 1, dir[r][0], dir[r][1], 2);
			bets[r] = b;
			betPills[r] = new Box(b.x() + t.feltChip + 2, b.y() + (stackH - PILL_H) / 2, m.betPillW(), PILL_H);
			taken.add(b);
		}
		for (int r = 1; r < n; r++) {
			Box start = new Box(cards[r].right() + 2, cards[r].bottom() - BUTTON, BUTTON, BUTTON);
			Box button = free(start, taken, felt, 0, dir[r][0], dir[r][1], 2);
			taken.add(button);
			Box hand = new Box(plates[r].x(), plates[r].bottom() + 1, Math.min(m.handW(), plates[r].w() + 2 + m.stackPillW()), PILL_H);
			seats[r] = new Seat(r, plates[r], pills[r], cards[r], step, bets[r], betPills[r], button, hand);
		}
		return new Layout(g, oval, board, pot, seats, new int[] {cx, cy});
	}

	/** The viewer: hero cards bottom-centre, plate left with the stack pill above, hand pill and bet right. */
	private static Seat hero(TableGrid g, int cx, Box pot, Measure m, List<Box> taken) {
		Tier t = g.tier;
		Box play = g.play;
		int cw = t.heroW;
		int ch = t.heroH;
		int step = cw * 2 / 3;
		int fanW = cw + step;
		int bottom = play.bottom() - 4;
		int top = Math.max(pot.bottom() + 2, bottom - ch);
		Box cards = new Box(cx - fanW / 2, top, fanW, ch);
		int plateW = t.plateW + 10;
		Box plate = new Box(cards.x() - 8 - plateW, play.bottom() - 4 - Tier.PLATE_H, plateW, Tier.PLATE_H);
		Box pill = new Box(plate.right() - m.stackPillW(), plate.y() - 2 - PILL_H, m.stackPillW(), PILL_H);
		Box hand = new Box(cards.right() + 6, cards.y() + 4, m.handW(), PILL_H);
		int stackH = t.feltChip + 4;
		Box bet = new Box(cards.right() + 6, Math.min(hand.bottom() + 4, bottom - stackH), t.feltChip + 2 + m.betPillW(), stackH);
		Box betPill = new Box(bet.x() + t.feltChip + 2, bet.y() + (stackH - PILL_H) / 2, m.betPillW(), PILL_H);
		Box button = new Box(bet.x() + bet.w() + 4, bet.y() + (stackH - BUTTON) / 2, BUTTON, BUTTON);
		taken.add(cards);
		taken.add(plate);
		taken.add(pill);
		taken.add(hand);
		taken.add(bet);
		taken.add(button);
		return new Seat(0, plate, pill, cards, step, bet, betPill, button, hand);
	}

	/**
	 * The free box nearest to {@code start} (inside {@code bounds}, touching none of {@code taken} with a {@code pad} px
	 * margin), searched ring by ring (Chebyshev radius in {@code stepPx} steps, ≤ {@link #SEARCH} px); within a ring the
	 * candidates closest to the direction {@code (dx, dy)} (toward the table centre) win. Falls back to the start.
	 */
	static Box free(Box start, List<Box> taken, Box bounds, int pad, double dx, double dy, int stepPx) {
		Box s0 = clampInto(start, bounds);
		if (clear(s0, taken, pad)) return s0;
		int rings = SEARCH / stepPx;
		List<int[]> ring = new ArrayList<>();
		for (int k = 1; k <= rings; k++) {
			ring.clear();
			for (int i = -k; i <= k; i++) {
				ring.add(new int[] {i, -k});
				ring.add(new int[] {i, k});
				if (i != -k && i != k) {
					ring.add(new int[] {-k, i});
					ring.add(new int[] {k, i});
				}
			}
			final int kk = k;
			ring.sort((a, b) -> Double.compare(score(a, dx, dy, kk), score(b, dx, dy, kk)));
			for (int[] o : ring) {
				Box b = start.offset(o[0] * stepPx, o[1] * stepPx);
				if (!b.inside(bounds)) continue;
				if (clear(b, taken, pad)) return b;
			}
		}
		return s0;
	}

	/** Lower is better: the Euclidean distance, minus a bonus for going along the direction. */
	private static double score(int[] o, double dx, double dy, int k) {
		return Math.hypot(o[0], o[1]) - 0.6 * (o[0] * dx + o[1] * dy);
	}

	/** Search radius of {@link #free} in px. */
	static final int SEARCH = 160;

	private static Box clampInto(Box b, Box bounds) {
		int x = Math.max(bounds.x(), Math.min(bounds.right() - b.w(), b.x()));
		int y = Math.max(bounds.y(), Math.min(bounds.bottom() - b.h(), b.y()));
		return new Box(x, y, b.w(), b.h());
	}

	private static boolean clear(Box b, List<Box> taken, int pad) {
		Box grown = new Box(b.x() - pad, b.y() - pad, b.w() + 2 * pad, b.h() + 2 * pad);
		for (Box o : taken) if (grown.intersects(o)) return false;
		return true;
	}
}

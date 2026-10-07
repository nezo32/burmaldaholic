package dev.nezo.burmaldaholic.core.ui.v2;

/**
 * One seat of a crescent card table (blackjack, UTH lanes, baccarat plates; docs/architecture/ui-v2.md §4.4), laid
 * out top to bottom from its anchor (the bottom-centre of the cards' column):
 *
 * <pre>
 *   outcome slot   (TotalPill while playing → Stamp on settle; 1× for others, 2× for the hero)
 *   cards          (fan, step w/2 + 2; never covered by anything)
 *   stack + value  (FeltStack centred, its value pill to the RIGHT of the stack, same row)
 *   plate          (name only)
 * </pre>
 *
 * The outcome slot shows the hand total while the hand plays and the Stamp WORD ({@code BUST}, {@code BLACKJACK})
 * on settle, for every seat including the hero (the total no longer floats left of the hero's fan, where it collided
 * with the neighbour at 5-card hands). The net goes into the seat's value pill under the cards ({@code 50} →
 * {@code +75} green / {@code −50} red / {@code 50} slate), where the chips land: a word + net stamp is too wide for the
 * hero's neighbours at 480 × 270. Stamps never overlap cards (review of the v2 mockups:
 * "BLACKJACK +75" covered the hero's cards). Felt prints are decor and yield to any of these boxes. Pure; the boxes
 * are disjoint by construction and {@link LayoutAudit} checks it in the tests.
 */
public record SeatColumn(Box cards, Box outcome, Box stack, Box value, Box plate) {
	/** Pill heights: 1× text on a pill and 2× text on a pill. */
	public static final int PILL_1X = 11, PILL_2X = 20;

	/**
	 * @param ax, ay   anchor (bottom-centre of the column's cards area)
	 * @param cards    number of cards in the fan (≥ 1 laid out even when empty, so the column does not jump)
	 * @param hero     the viewer's own seat (hero cards, 2× outcome)
	 * @param outcomeW measured width of the widest outcome text of this seat (stamp or total pill)
	 * @param valueW   measured width of the bet value pill
	 * @param plateW   plate width ({@link #plateW})
	 */
	public static SeatColumn layout(Tier t, int ax, int ay, int cards, boolean hero, int outcomeW, int valueW, int plateW) {
		return layout(t, ax, ay, cards, hero, outcomeW, hero ? PILL_2X : PILL_1X, valueW, plateW, Integer.MAX_VALUE);
	}

	/**
	 * @param maxFanW the fan's width limit (neighbours); the step shrinks down to {@link #MIN_STEP} to stay inside it.
	 *                When even the stack + value row does not fit it, the discs are dropped and the value pill stays.
	 */
	public static SeatColumn layout(Tier t, int ax, int ay, int cards, boolean hero, int outcomeW, int outcomeH, int valueW, int plateW,
			int maxFanW) {
		int cw = hero ? t.heroW : t.seatW;
		int ch = hero ? t.heroH : t.seatH;
		int n = Math.max(1, cards);
		int step = cw / 2 + 2;
		if (n > 1 && cw + (n - 1) * step > maxFanW) step = Math.max(MIN_STEP, (maxFanW - cw) / (n - 1));
		int fanW = cw + (n - 1) * step;
		int gap = Tier.GAP;
		int stackH = t.feltChip + 4; // up to 4 stacked discs, 1 px each
		int cardsBottom = ay - stackH - 2 * gap;
		Box cardBox = new Box(ax - fanW / 2, cardsBottom - ch, fanW, ch);
		int oh = outcomeH;
		Box outcome = new Box(ax - outcomeW / 2, cardBox.y() - gap - oh, outcomeW, oh);
		Box stack;
		Box value;
		if (t.feltChip + gap + valueW <= Math.max(maxFanW - 4, 0) || maxFanW == Integer.MAX_VALUE) {
			stack = new Box(ax - t.feltChip / 2 - (valueW + gap) / 2, cardsBottom + gap, t.feltChip, stackH);
			value = new Box(stack.right() + gap, stack.y() + (stackH - PILL_1X) / 2, valueW, PILL_1X);
		} else { // too narrow for discs + pill (7 seats on a 320-wide GUI): the pill alone, centred
			value = new Box(ax - valueW / 2, cardsBottom + gap + (stackH - PILL_1X) / 2, valueW, PILL_1X);
			stack = new Box(ax, value.y(), 0, 0);
		}
		Box plate = new Box(ax - plateW / 2, ay + (hero ? 6 : 2), plateW, Tier.PLATE_H);
		return new SeatColumn(cardBox, outcome, stack, value, plate);
	}

	/** Every box of the column (for the audit and the hit map). */
	public Box[] boxes() {
		return new Box[] {cards, outcome, stack, value, plate};
	}

	/**
	 * Anchors of a crescent table with {@code seats} seats (1..7): evenly spaced from 0.10 to 0.90 of the width (pulled
	 * in so the outer plates stay on the felt) on an
	 * arc from 0.56 (ends) to 0.86 (centre) of the PlayArea height; the viewer always sits in the middle slot (screens
	 * rotate the server seat order). An even seat count uses {@code seats + 1} slots so the viewer stays centred.
	 *
	 * @return {@code [slot][x, y]}, plus {@link #pitch} for plate widths
	 */
	public static int[][] crescent(TableGrid g, int seats) {
		int slots = seats % 2 == 0 ? seats + 1 : seats;
		int[][] out = new int[slots][2];
		int[] span = span(g);
		for (int i = 0; i < slots; i++) {
			double fx = slots == 1 ? 0.5 : 0.10 + 0.80 * i / (slots - 1);
			double d = (fx - 0.5) / 0.4;
			double fy = 0.56 + 0.30 * (1 - d * d);
			out[i][0] = slots == 1 ? g.fx(0.5) : span[0] + (span[1] - span[0]) * i / (slots - 1);
			// the hero's plate (anchor + 6 + PLATE_H) must end 4 px above the felt's bottom edge at every size
			out[i][1] = Math.min(g.fy(fy), g.play.bottom() - 10 - 6 - Tier.PLATE_H);
		}
		return out;
	}

	/** Horizontal distance between neighbouring crescent slots. */
	public static int pitch(TableGrid g, int seats) {
		int slots = seats % 2 == 0 ? seats + 1 : seats;
		int[] span = span(g);
		return slots <= 1 ? g.play.w() : (span[1] - span[0]) / (slots - 1);
	}

	/** x of the outer seats: 0.10 / 0.90 of the width, pulled in so a full-width plate stays on the felt. */
	private static int[] span(TableGrid g) {
		Box felt = g.felt();
		int half = g.tier.plateW / 2 + 1;
		return new int[] {Math.max(g.fx(0.10), felt.x() + half), Math.min(g.fx(0.90), felt.right() - half)};
	}

	/** Plate width for a crescent: the tier's width, narrowed so neighbouring plates keep a 4 px gap. */
	public static int plateW(TableGrid g, int seats) {
		return Math.min(g.tier.plateW, pitch(g, seats) - 4);
	}

	/** Narrowest fan step: the rank index of each card stays visible (index glyph 3 px + 1 px edge + gap). */
	public static final int MIN_STEP = 5;

	/** Size of the last-resort outcome: a coloured stamp dot (win / lose / push / gold), always fits. */
	public static final int DOT = 9;

	/**
	 * Lays out a whole crescent table and resolves outcome collisions (docs/architecture/ui-v2.md §4.4). Each seat
	 * offers its outcome at decreasing levels (hero: 2× word, 1× word; others: 1× word; a game may add a short word)
	 * and finally a {@link #DOT}. Every seat's dot is reserved first, then seats choose in priority order (the
	 * hero, then outwards) the largest level that touches nothing of any other seat and stays on the felt. So stamps
	 * never cover cards or each other at any size; the net also always shows in the TopBar delta and the status.
	 *
	 * @param cards     per slot: cards in the hand, or -1 for an empty slot (no column; the felt print shows the spot)
	 * @param heroSlot  slot of the viewer (the middle one, {@code crescent(g, seats).length / 2}), or -1 when watching
	 * @param outcomeW  per slot: widths of its outcome levels, largest first (the dot is implied)
	 * @param valueW    per slot: width of its bet value pill
	 * @param levelOut  per slot: the chosen level ({@code outcomeW[i].length} = the dot)
	 */
	public static SeatColumn[] table(TableGrid g, int seats, int[] cards, int heroSlot, int[][] outcomeW, int[] valueW, int[] levelOut) {
		int[][] anchors = crescent(g, seats);
		int n = anchors.length;
		int plateW = plateW(g, seats);
		int pitch = pitch(g, seats);
		Box felt = g.felt();
		SeatColumn[] cols = new SeatColumn[n];
		for (int i = 0; i < n; i++) {
			if (cards[i] < 0) continue;
			cols[i] = layout(g.tier, anchors[i][0], anchors[i][1], cards[i], i == heroSlot, DOT, DOT, valueW[i], plateW, Math.min(maxFan(pitch, i == heroSlot), 2 * Math.min(anchors[i][0] - felt.x(), felt.right() - anchors[i][0]) - 2));
			levelOut[i] = outcomeW[i].length;
		}
		int mid = heroSlot >= 0 ? heroSlot : n / 2;
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) order[i] = i;
		java.util.Arrays.sort(order, java.util.Comparator.comparingInt(a -> a == heroSlot ? -1 : Math.abs(a - mid) * 2 + (a < mid ? 1 : 0)));
		for (int i : order) {
			if (cols[i] == null) continue;
			boolean hero = i == heroSlot;
			for (int level = 0; level < outcomeW[i].length; level++) {
				int h = hero && level == 0 ? PILL_2X : PILL_1X;
				SeatColumn c = layout(g.tier, anchors[i][0], anchors[i][1], cards[i], hero, outcomeW[i][level], h, valueW[i], plateW, Math.min(maxFan(pitch, hero), 2 * Math.min(anchors[i][0] - felt.x(), felt.right() - anchors[i][0]) - 2));
				if (free(c.outcome(), cols, i, felt)) {
					cols[i] = c;
					levelOut[i] = level;
					break;
				}
			}
		}
		return cols;
	}

	/** Others' fans stay within their pitch minus a gap; the hero's within what the two neighbours leave. */
	private static int maxFan(int pitch, boolean hero) {
		return hero ? pitch : pitch - 6;
	}

	private static boolean free(Box b, SeatColumn[] cols, int self, Box felt) {
		if (!b.inside(felt)) return false;
		for (int j = 0; j < cols.length; j++) {
			if (j == self || cols[j] == null) continue;
			for (Box o : cols[j].boxes()) if (b.intersects(o)) return false;
		}
		return true;
	}
}

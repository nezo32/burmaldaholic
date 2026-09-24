package dev.nezo.burmaldaholic.core.anim.cards;

/**
 * Screen anchors of the card tables (docs/design/visual/cards.md §6) as PURE layout maths: the 427 × 240 design canvas,
 * the table art at (10, 18) (compact: 280 × 124 at (2, 12) on a 284 × 160 canvas), and the table-local anchors of the
 * blackjack and baccarat layouts. Screens read these instead of computing positions ad hoc; the mockup script
 * ({@code docs/design/visual/mockups/render_cards.py}) mirrors the same numbers. Compact anchors are the full ones
 * scaled by 280 / 408 and snapped to whole pixels (§6.7), except where the mockup fixes them.
 */
public final class CardLayout {
	/** Design canvas (GUI scale 2 at 854 × 480). */
	public static final int CANVAS_W = 427;
	public static final int CANVAS_H = 240;
	/** Compact canvas (GUI scale 3 at 854 × 480). */
	public static final int COMPACT_W = 284;
	public static final int COMPACT_H = 160;
	/** Table art: full 408 × 184 at (10, 18); compact 280 × 124 at (2, 12). */
	public static final int TABLE_W = 408;
	public static final int TABLE_H = 184;
	public static final int TABLE_X = 10;
	public static final int TABLE_Y = 18;
	public static final int TABLE_CW = 280;
	public static final int TABLE_CH = 124;
	public static final int TABLE_CX = 2;
	public static final int TABLE_CY = 12;
	/** Console strip: full (0, 206, 428 × 34), compact (0, 137, 284 × 23). */
	public static final int CONSOLE_Y = 206;
	public static final int CONSOLE_H = 34;
	public static final int CONSOLE_CY = 137;
	public static final int CONSOLE_CH = 23;
	/** Card sizes (§3.1): L, M, S. */
	public static final int L_W = 37, L_H = 49, M_W = 21, M_H = 29, S_W = 13, S_H = 17;

	private CardLayout() {}

	/** Layout scale: the largest integer k with 427k × 240k inside the GUI, or 0 when the compact layout applies. */
	public static int scale(int guiW, int guiH) {
		return Math.max(0, Math.min(guiW / CANVAS_W, guiH / CANVAS_H));
	}

	/** Compact anchor from a full one (× 280 / 408, rounded half up). */
	public static int c(int full) {
		return Math.floorDiv(full * TABLE_CW * 2 + TABLE_W, TABLE_W * 2);
	}

	/** Card width / height by size index 0 = L, 1 = M, 2 = S. */
	public static int cardW(int size) {
		return size == 0 ? L_W : size == 1 ? M_W : S_W;
	}

	public static int cardH(int size) {
		return size == 0 ? L_H : size == 1 ? M_H : S_H;
	}

	/** Width of a fanned hand of {@code n} cards of width {@code w} at {@code step}. */
	public static int handWidth(int n, int w, int step) {
		return n <= 0 ? w : w + step * (n - 1);
	}

	// ---- blackjack (§6.3, compact §6.7) ---------------------------------------------------------------------------

	/** Blackjack anchors (table-local). */
	public static final class Blackjack {
		public static final int TRAY_X = 12, TRAY_Y = 12;
		public static final int SHOE_X = 356, SHOE_Y = 12;
		public static final int SHOE_MOUTH_X = 358, SHOE_MOUTH_Y = 30;
		public static final int RACK_X = 164, RACK_Y = -1;
		public static final int DEALER_X = 166, DEALER_Y = 14;
		public static final int HOLE_DX = 22, HOLE_DY = 2;
		public static final int DEALER_STEP = 20;
		public static final int RULES_CX = 204, RULES_Y = 66;
		public static final int INSURANCE_X = 88, INSURANCE_Y = 74, INSURANCE_CX = 204, INSURANCE_TY = 80;
		public static final int ME_SPOT_X = 204, ME_SPOT_Y = 154;
		public static final int ME_HAND_Y = 104, ME_STEP = 14, ME_STEP_M = 9;
		public static final int ME_BADGE_DY = -12;
		/** Other seats' bet spots: far left, left, right, far right. */
		public static final int[][] SPOTS = {{56, 70}, {92, 128}, {316, 128}, {352, 70}};
		public static final int OTHER_STEP = 9;
		/** Stack anchors of the viewer's hands (base centre). */
		public static final int ME_STACK_Y = 164;

		// compact (§6.7, the mockup's numbers)
		public static final int C_DEALER_X = 118, C_DEALER_Y = 10, C_HOLE_DX = 20, C_HOLE_DY = 1, C_DEALER_STEP = 13;
		public static final int C_SHOE_X = 238, C_SHOE_Y = 4;
		public static final int C_RACK_X = 100;
		public static final int C_BADGE_X = 100, C_BADGE_Y = 12;
		public static final int C_RULES_CX = 140, C_RULES_Y = 44;
		public static final int C_HAND_Y = 62, C_STEP = 9;
		public static final int C_STACK_Y = 104;
		public static final int[][] C_SPOTS = {{38, 50}, {46, 86}, {226, 86}, {242, 50}};

		private Blackjack() {}

		/** Dealer card {@code i}'s x (up card, the tucked hole card, then 20 px steps). */
		public static int dealerX(int i, boolean compact) {
			int x0 = compact ? C_DEALER_X : DEALER_X;
			int hole = compact ? C_HOLE_DX : HOLE_DX;
			int step = compact ? C_DEALER_STEP : DEALER_STEP;
			return i == 0 ? x0 : x0 + hole + step * (i - 1);
		}

		public static int dealerY(int i, boolean compact) {
			int y0 = compact ? C_DEALER_Y : DEALER_Y;
			return i == 1 ? y0 + (compact ? C_HOLE_DY : HOLE_DY) : y0;
		}

		/**
		 * The viewer's hand {@code h} of {@code n} hands (active {@code active}): left x of its first card. One hand is
		 * centred on 204; two sit at 136 / 214; with 3–4 hands the active one (L) sits at 170 and the others (M) fan to its
		 * left and right.
		 */
		public static int handX(int h, int n, int active, int cards, boolean compact) {
			if (compact) {
				if (n <= 1) return 140 - handWidth(cards, M_W, C_STEP) / 2;
				if (n == 2) return h == 0 ? 92 : 152;
				int slot = h - active;
				return 116 + slot * 40;
			}
			if (n <= 1) return 204 - handWidth(cards, L_W, ME_STEP) / 2;
			if (n == 2) return h == 0 ? 136 : 214;
			if (h == active) return 170;
			// inactive hands in M size: left of the active one going left, right of it going right
			if (h < active) return 170 - (active - h) * 44;
			return 170 + 52 + (h - active - 1) * 44;
		}

		/** Size of the viewer's hand {@code h}: 0 = L, 1 = M (3–4 hands: inactive ones shrink), compact always M. */
		public static int handSize(int h, int n, int active, boolean compact) {
			if (compact) return 1;
			return n >= 3 && h != active ? 1 : 0;
		}

		public static int handY(int size, boolean compact) {
			if (compact) return C_HAND_Y;
			return size == 0 ? ME_HAND_Y : ME_HAND_Y + 20;
		}

		/** Base centre x of the viewer's chip stack of hand {@code h} (after a split / double the second stack). */
		public static int stackX(int h, int n, boolean compact) {
			if (compact) return n <= 1 ? 140 : h == 0 ? 128 : 152 + 24 * (h - 1);
			if (n <= 1) return 204;
			return h == 0 ? 190 : 218 + (h - 1) * 18;
		}

		/** Other seat position {@code pos} (0 far left … 3 far right): spot centre. */
		public static int[] spot(int pos, boolean compact) {
			return (compact ? C_SPOTS : SPOTS)[Math.floorMod(pos, 4)];
		}

		/** Seat plate top-left of other seat position {@code pos} (§6.3: plates overlap the rail). */
		public static int[] plate(int pos) {
			int[] s = SPOTS[Math.floorMod(pos, 4)];
			return switch (Math.floorMod(pos, 4)) {
				case 0 -> new int[] {s[0] - 62, s[1] + 12};
				case 3 -> new int[] {s[0] - 12, s[1] + 12};
				default -> new int[] {s[0] - 26, s[1] + 14};
			};
		}
	}

	// ---- baccarat / chemin de fer (§6.6) --------------------------------------------------------------------------

	/** Baccarat anchors (table-local). */
	public static final class Baccarat {
		public static final int TRAY_X = 20, TRAY_Y = 12;
		public static final int SHOE_X = 348, SHOE_Y = 12, SHOE_MOUTH_X = 350, SHOE_MOUTH_Y = 30;
		public static final int RACK_X = 164, RACK_Y = -1;
		/** Hand panels: Player (58, 14), Banker (214, 14), 136 × 68. */
		public static final int PANEL_W = 136, PANEL_H = 68, PANEL_Y = 14;
		public static final int[] PANEL_X = {58, 214};
		/** Cards of each hand: 1st, 2nd upright at +40; the third sideways at (x0 + 86, 36). */
		public static final int CARD_Y = 30, THIRD_Y = 36;
		public static final int RESULT_CX = 204, RESULT_CY = 86;
		public static final int BOX_Y = 98, BOX_H = 28;
		/** Bet boxes P.Pair, Player, Tie, Banker, B.Pair: x and width. */
		public static final int[][] BOXES = {{38, 62}, {104, 80}, {188, 44}, {236, 80}, {320, 62}};
		public static final int ROAD_X = 150, ROAD_Y = 130, ROAD_W = 108, ROAD_H = 44;
		public static final int BEAD_X = 154, BEAD_Y = 134, BEAD_COL = 10, BEAD_ROW = 9, BEAD_ROWS = 4, BEAD_COLS = 10;
		/** Seat plates (≤ 7) along the rail. */
		public static final int[][] PLATES = {{18, 132}, {300, 132}, {44, 156}, {278, 156}, {-4, 56}, {352, 56}, {174, 176}};

		private Baccarat() {}

		/** x of card {@code i} of side {@code side} (0 Player, 1 Banker). */
		public static int cardX(int side, int i) {
			int x0 = PANEL_X[side] + 6;
			return i < 2 ? x0 + 40 * i : x0 + 80;
		}

		public static int cardY(int i) {
			return i < 2 ? CARD_Y : THIRD_Y;
		}
	}

	// ---- chips (§5.2) ---------------------------------------------------------------------------------------------

	/** Denominations largest first. */
	public static final int[] DENOMS = {500, 100, 25, 5, 1};

	/**
	 * Discs of a stack for {@code amount}, largest at the bottom (index 0), at most {@code max} discs chosen greedily
	 * from 500 down. Stack size is decoration: the exact amount is a runtime label.
	 */
	public static int[] discs(long amount, int max) {
		int[] tmp = new int[Math.max(1, max)];
		int n = 0;
		long left = Math.max(0, amount);
		for (int d : DENOMS) {
			while (left >= d && n < tmp.length) {
				tmp[n++] = d;
				left -= d;
			}
		}
		if (n == 0) return amount > 0 ? new int[] {1} : new int[0];
		int[] out = new int[n];
		System.arraycopy(tmp, 0, out, 0, n);
		return out;
	}
}

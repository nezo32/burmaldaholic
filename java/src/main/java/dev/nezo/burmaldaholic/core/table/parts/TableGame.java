package dev.nezo.burmaldaholic.core.table.parts;

import org.jspecify.annotations.Nullable;

/**
 * Footprint kind of a multi-block casino table (MT-DESIGN §1). Every kind is 3×2 today; widths are
 * odd on purpose (mirror() has no HAND property). Slots, wheel, plinko and cashiers stay 1×1 and
 * have no kind ({@link #ofBlockName} returns null).
 */
public enum TableGame {
	BLACKJACK(FeltShape.CRESCENT, 0),
	BACCARAT(FeltShape.CRESCENT, 0),
	UTH(FeltShape.CRESCENT, 0),
	POKER(FeltShape.OVAL, 0),
	ROULETTE(FeltShape.ROULETTE, -1),
	CRAPS(FeltShape.RECT, 0);

	/** Felt outline inside the rail. */
	public enum FeltShape {
		/** Card table: straight dealer edge, rounded front corners (r=0.875). */
		CRESCENT,
		/** Stadium: every corner rounded (r=0.875); seats all around except the dealer slot. */
		OVAL,
		/** Plain rectangle (craps). */
		RECT,
		/** Wheel bowl in column r-1, betting layout on r0..1. */
		ROULETTE
	}

	private final FeltShape shape;
	private final int dealerR;

	TableGame(FeltShape shape, int dealerR) {
		this.shape = shape;
		this.dealerR = dealerR;
	}

	public FeltShape shape() {
		return shape;
	}

	/** Footprint width in cells (along the players' right). */
	public int width() {
		return 3;
	}

	/** Footprint depth in cells (front row f=0 plus back row f=-1). */
	public int depth() {
		return 2;
	}

	/** Dealer NPC cell, local r (cells to the players' right of the main block). */
	public int dealerR() {
		return dealerR;
	}

	/** Dealer NPC cell, local f: always one row behind the back row. */
	public int dealerF() {
		return -depth();
	}

	/**
	 * Footprint kind for a table block registry path ({@code blackjack_table_high_roller},
	 * {@code uth_table_player_banked}, ...), or null for 1×1 machines (slots, wheel, plinko, cashier).
	 */
	public static @Nullable TableGame ofBlockName(String path) {
		if (path.startsWith("blackjack_table")) {
			return BLACKJACK;
		}
		if (path.startsWith("baccarat_table")) {
			return BACCARAT;
		}
		if (path.startsWith("uth_table")) {
			return UTH;
		}
		if (path.startsWith("poker_table")) {
			return POKER;
		}
		if (path.startsWith("roulette_table")) {
			return ROULETTE;
		}
		if (path.startsWith("craps_table")) {
			return CRAPS;
		}
		return null;
	}
}

package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.List;

/** 243-ways evaluation (SLOTS.md §1.1). */
public final class Ways {
	private Ways() {}

	/**
	 * One winning symbol.
	 *
	 * @param symbol     paying symbol index
	 * @param k          reels matched (3–5)
	 * @param ways       n_1 × … × n_k
	 * @param payFifths  pay(P, k) × ways, fifths of the bet, before multipliers
	 * @param cellMask   15-bit mask of every cell taking part (the symbol and wilds on reels 1…k)
	 */
	public record WayWin(int symbol, int k, int ways, long payFifths, int cellMask) {}

	/**
	 * Evaluation of one window.
	 *
	 * @param wins          winning symbols in symbol order
	 * @param payFifths     Σ way pays (no scatter pay, no multiplier)
	 * @param winMask       union of the wins' cell masks (cells that explode in a tumble)
	 * @param scatters      scatter count on the window
	 * @param bonusCount    bonus symbols on their reels
	 * @param coins         coin count (Nether)
	 */
	public record Result(List<WayWin> wins, long payFifths, int winMask, int scatters, int bonusCount, int coins) {}

	/**
	 * Evaluates a window. {@code stickyMask} (End free spins) forces reels 2–4 (bits 0–2) to WWW before
	 * evaluation; expanding wilds on the window are applied by the caller (free-spin logic).
	 * SKELETON — lane S-J1 / S-B1.
	 */
	public static Result evaluate(MachineDef def, Window window, int stickyMask) {
		throw new UnsupportedOperationException("slots v2 ways evaluator: lane S-J1");
	}
}

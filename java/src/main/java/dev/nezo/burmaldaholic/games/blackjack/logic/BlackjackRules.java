package dev.nezo.burmaldaholic.games.blackjack.logic;

/**
 * Table rules (GAME_DESIGN §6.1, CONFIG.md {@code blackjack.*}). PURE.
 *
 * @param decks            decks in the shoe (1–8)
 * @param penetration      reshuffle before the next round once this fraction of the shoe was dealt
 * @param dealerHitsSoft17 H17 when true, S17 (default) when false
 * @param blackjackPayout  natural profit multiplier: 1.5 = 3:2, 1.2 = 6:5 (profit floored)
 * @param maxHands         hands after splits (2–4)
 * @param insurance        insurance / even money offered when the dealer shows an ace
 * @param lateSurrender    surrender the first two cards of an unsplit hand after the peek (half back)
 */
public record BlackjackRules(int decks, double penetration, boolean dealerHitsSoft17, double blackjackPayout,
		boolean doubleAfterSplit, int maxHands, boolean resplitAces, boolean insurance, boolean lateSurrender) {
	public static final BlackjackRules DEFAULT = new BlackjackRules(6, 0.75, false, 1.5, true, 4, false, true, false);

	/** Clamps into the CONFIG.md ranges (config values are already clamped; this guards tests and callers). */
	public BlackjackRules {
		decks = Math.max(1, Math.min(8, decks));
		penetration = Double.isFinite(penetration) ? Math.max(0.25, Math.min(0.9, penetration)) : 0.75;
		blackjackPayout = Double.isFinite(blackjackPayout) ? Math.max(1.0, Math.min(2.0, blackjackPayout)) : 1.5;
		maxHands = Math.max(2, Math.min(4, maxHands));
	}

	public BlackjackRules withDealerHitsSoft17(boolean v) {
		return new BlackjackRules(decks, penetration, v, blackjackPayout, doubleAfterSplit, maxHands, resplitAces, insurance, lateSurrender);
	}

	public BlackjackRules withBlackjackPayout(double v) {
		return new BlackjackRules(decks, penetration, dealerHitsSoft17, v, doubleAfterSplit, maxHands, resplitAces, insurance, lateSurrender);
	}

	public BlackjackRules withDoubleAfterSplit(boolean v) {
		return new BlackjackRules(decks, penetration, dealerHitsSoft17, blackjackPayout, v, maxHands, resplitAces, insurance, lateSurrender);
	}

	public BlackjackRules withResplitAces(boolean v) {
		return new BlackjackRules(decks, penetration, dealerHitsSoft17, blackjackPayout, doubleAfterSplit, maxHands, v, insurance, lateSurrender);
	}

	public BlackjackRules withInsurance(boolean v) {
		return new BlackjackRules(decks, penetration, dealerHitsSoft17, blackjackPayout, doubleAfterSplit, maxHands, resplitAces, v, lateSurrender);
	}

	public BlackjackRules withLateSurrender(boolean v) {
		return new BlackjackRules(decks, penetration, dealerHitsSoft17, blackjackPayout, doubleAfterSplit, maxHands, resplitAces, insurance, v);
	}

	public BlackjackRules withMaxHands(int v) {
		return new BlackjackRules(decks, penetration, dealerHitsSoft17, blackjackPayout, doubleAfterSplit, v, resplitAces, insurance, lateSurrender);
	}

	/** Total return of a natural: stake + floor(stake × payout) (3:2 on 5 → 5 + 7). */
	public long blackjackReturn(long bet) {
		return bet + (long) Math.floor(bet * blackjackPayout);
	}

	/** Late surrender returns half the bet, floored. */
	public static long surrenderReturn(long bet) {
		return bet / 2;
	}

	/** Maximum insurance: half the main bet, floored. */
	public static long maxInsurance(long bet) {
		return bet / 2;
	}

	/**
	 * Worst-case house loss reserved for a main bet (GAME_DESIGN §18.2 "8 × bet (4 hands doubled) +
	 * insurance"): four split hands, all doubled and all won 1:1 (+8 × bet), plus a full insurance
	 * paid 2:1. The extra stakes of doubles/splits/insurance flow into the same bank when placed.
	 */
	public static long worstCasePayout(long bet) {
		return 8 * bet + 2 * maxInsurance(bet);
	}
}

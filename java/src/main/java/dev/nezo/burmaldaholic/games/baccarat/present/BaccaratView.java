package dev.nezo.burmaldaholic.games.baccarat.present;

import java.util.List;

/**
 * What the baccarat / chemin de fer v2 screen knows, as a pure value (docs/architecture/ui-v2.md §5), consumed by
 * {@link BaccaratFlow}. House tables are multi-spot (a chip arms, a box takes it, the server adds it); chemmy uses a
 * local draft for the punt / the bank (chips add to it, the primary sends it).
 *
 * @param seated     the viewer has a seat
 * @param canSit     a seat is free
 * @param chemmy     chemin de fer rules now
 * @param phase      server phase id ({@code idle, betting, no_more_bets, shuffle, reveal, result, bank_offer, waiting})
 * @param min        minimum bet (per box)
 * @param max        maximum bet
 * @param minTotal   minimum of the whole slip
 * @param balance    the viewer's balance
 * @param bets       the viewer's slip per box (P, B, T, P.PAIR, B.PAIR)
 * @param ready      the viewer pressed Deal / is ready
 * @param canRebet   the server holds a last slip it can re-place
 * @param pairs      pair boxes are open at this table
 * @param secondsLeft seconds of the running timer, -1 none
 * @param waitingNext seated, not in the running coup
 * @param draft      chemmy: the local punt / bank draft
 * @param offerYou   chemmy: the bank is offered to the viewer
 * @param offerKeep  chemmy: the viewer may keep the bank (it won)
 * @param youBank    chemmy: the viewer holds the bank
 * @param bankHeld   chemmy: someone holds the bank
 * @param open       chemmy: what the bank can still cover
 * @param myPunt     chemmy: the viewer's punt
 * @param bancoTaken chemmy: someone went banco
 * @param minBank    chemmy: the minimum bank
 * @param banker     chemmy: the banker's name ("" = none)
 * @param pending    an action was sent and the table has not answered yet
 */
public record BaccaratView(boolean seated, boolean canSit, boolean chemmy, String phase, long min, long max, long minTotal, long balance, long[] bets,
		boolean ready, boolean canRebet, boolean pairs, int secondsLeft, boolean waitingNext, long draft, boolean offerYou, boolean offerKeep,
		boolean youBank, boolean bankHeld, long open, long myPunt, boolean bancoTaken, long minBank, String banker, boolean pending) {
	public static final long[] DENOMS = {1, 5, 25, 100, 500, 1_000, 5_000};

	public BaccaratView {
		bets = java.util.Arrays.copyOf(bets, BaccaratPub.BOXES);
	}

	public long total() {
		long t = 0;
		for (long b : bets) t += b;
		return t;
	}

	/** House betting is open: chips go on the boxes. */
	public boolean houseOpen() {
		return !chemmy && ("betting".equals(phase) || "idle".equals(phase));
	}

	/** Chemmy punting is open for the viewer (not the banker). */
	public boolean puntOpen() {
		return chemmy && "betting".equals(phase) && bankHeld && !youBank && !bancoTaken;
	}

	public long cap() {
		return Math.max(0, Math.min(max > 0 ? max : Long.MAX_VALUE, balance));
	}

	/** Chips of the tray (≤ 5), as blackjack's. */
	public List<Long> chips() {
		return java.util.Arrays.stream(BaccaratFlow.INSTANCE.bet(this).chips()).boxed().toList();
	}
}

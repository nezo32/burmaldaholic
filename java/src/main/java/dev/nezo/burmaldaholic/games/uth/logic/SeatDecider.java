package dev.nezo.burmaldaholic.games.uth.logic;

import org.jspecify.annotations.Nullable;

/**
 * Who makes a seat's decisions (4× / 3× / 2× / 1× / fold, and whether to take an offered dealer seat).
 * Every per-seat decision of the table goes through one of these, so a future bot participant only
 * needs its own policy (BOTS.md): {@link #HUMAN} waits for the player's input, {@link #SAFE_DEFAULT} is the
 * §21.4 timeout rule (also used for players who left), {@link ReferenceStrategy} is strategy R (§21.3).
 */
public interface SeatDecider {
	/**
	 * What the decider knows besides the round (the seat's own hole cards and the visible board are in
	 * the round; the decider must not look at the dealer's cards or unrevealed board cards).
	 *
	 * @param balance the seat's chips available for a Play bet
	 */
	record Context(boolean allow3x, boolean autoPlayMadeHands, long balance) {}

	/** The decision for a pending seat now, or null to wait (human input; the timer applies the default). */
	@Nullable Decision decide(UthRound round, UthRound.Seat seat, Context ctx);

	/**
	 * Offered the dealer seat of a player-banked table (§21.9): the bank to escrow, or 0 to decline.
	 * Humans answer through the screen, so the default declines.
	 */
	default long acceptBankOffer(long minBank, long balance) {
		return 0;
	}

	/** A human at the table: decisions come from the screen. */
	SeatDecider HUMAN = (round, seat, ctx) -> null;

	/** §21.4 safe default: check preflop/flop; river fold unless a straight or better may auto-play ×1. */
	SeatDecider SAFE_DEFAULT = (round, seat, ctx) -> round.defaultDecision(seat, ctx.autoPlayMadeHands(), ctx.balance() >= seat.ante);
}

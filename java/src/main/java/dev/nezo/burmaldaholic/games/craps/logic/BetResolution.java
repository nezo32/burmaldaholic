package dev.nezo.burmaldaholic.games.craps.logic;

/**
 * What a roll did to one bet.
 *
 * @param totalReturn  total return (stake included) for resolved outcomes, else 0
 * @param oddsReturned Come odds were off on the come-out roll and were handed back inside {@code totalReturn}
 * @param movedTo      new come point for {@link Outcome#MOVE}
 */
public record BetResolution(Bet bet, Outcome outcome, long totalReturn, boolean oddsReturned, int movedTo) {
	public enum Outcome {
		WIN, LOSE, PUSH,
		/** Come / Don't Come travelled to its point (still working). */
		MOVE,
		/** Nothing happened. */
		STAY;

		public boolean resolved() {
			return this == WIN || this == LOSE || this == PUSH;
		}
	}

	static BetResolution resolved(Bet bet, Outcome outcome, long totalReturn) {
		return new BetResolution(bet, outcome, totalReturn, false, 0);
	}

	static BetResolution stay(Bet bet) {
		return new BetResolution(bet, Outcome.STAY, 0, false, 0);
	}

	/** Net result for the player (return − staked); 0 while unresolved. */
	public long net() {
		return outcome.resolved() ? totalReturn - bet.staked() : 0;
	}
}

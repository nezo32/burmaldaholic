package dev.nezo.burmaldaholic.core.wager;

/**
 * Settlement math of a free-bet token round (EC-SPEC §4.3). Pure.
 *
 * <p>The token's face value is never returned as chips: a win pays {@code max(0, totalReturn − covered)}, a push
 * (total return equal to the total stake) gives the token back plus the chips the player added (blackjack doubles,
 * splits, insurance), a loss pays nothing. Slots ({@code fullWin}) credit the whole spin win instead.
 */
public final class TokenSettle {
	private TokenSettle() {}

	/**
	 * @param credit      chips paid to the player
	 * @param returnToken the token goes back to the player
	 */
	public record Settlement(long credit, boolean returnToken) {}

	/**
	 * One-hand rounds (wheel, plinko, slots, a blackjack seat that never split or insured): the whole round is the
	 * token's hand. Same as {@link #settleHand} with the hand = the round.
	 */
	public static Settlement settle(long totalStake, long covered, long totalReturn, boolean fullWin) {
		return settleHand(totalReturn, totalStake, totalReturn, covered, fullWin);
	}

	/**
	 * The token belongs to ONE hand only (T-EC2 D1): blackjack's original hand. Other split hands and insurance are
	 * plain chips and get their full return.
	 *
	 * @param totalReturn everything the round returns (all hands + insurance)
	 * @param handStake   the token hand's stake (token + chips its double added)
	 * @param handReturn  the token hand's return
	 * @param covered     the token's part of {@code handStake} (no chips were debited for it)
	 * @param fullWin     slots: the whole return is credited, no push rule
	 */
	public static Settlement settleHand(long totalReturn, long handStake, long handReturn, long covered, boolean fullWin) {
		long ret = Math.max(0, totalReturn);
		if (fullWin) {
			return new Settlement(ret, false);
		}
		long hRet = Math.max(0, Math.min(handReturn, ret));
		long others = ret - hRet;
		long cov = Math.max(0, Math.min(covered, Math.max(0, handStake)));
		if (handStake > 0 && hRet == handStake) {
			return new Settlement(others + Math.max(0, handStake - cov), true);
		}
		return new Settlement(others + Math.max(0, hRet - cov), false);
	}

	/** One-bet games ({@link Stakes#settle}): {@code winnings} = chips on top of the stake. */
	public static Settlement ofOutcome(Stakes.Outcome outcome, long winnings) {
		return switch (outcome) {
			case WIN -> new Settlement(Math.max(0, winnings), false);
			case PUSH -> new Settlement(0, true);
			case LOSS -> new Settlement(0, false);
		};
	}
}

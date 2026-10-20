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
	 * @param totalStake  everything at risk in the round (token part + chips added later)
	 * @param covered     the part of {@code totalStake} the token stood for (no chips were debited for it)
	 * @param totalReturn the game's total return for the round (stake included)
	 * @param fullWin     slots: the whole return is credited, no push rule
	 */
	public static Settlement settle(long totalStake, long covered, long totalReturn, boolean fullWin) {
		long ret = Math.max(0, totalReturn);
		long cov = Math.max(0, Math.min(covered, Math.max(0, totalStake)));
		if (fullWin) {
			return new Settlement(ret, false);
		}
		if (ret > 0 && ret == totalStake) {
			return new Settlement(Math.max(0, totalStake - cov), true);
		}
		return new Settlement(Math.max(0, ret - cov), false);
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

package dev.nezo.burmaldaholic.games.poker.present;

import java.util.List;

/**
 * What the Hold'em v2 screen's gates depend on (docs/architecture/ui-v2.md §5.1), built by the screen from the
 * state tag the server sends ({@code PokerTableBlockEntity.writeClientState}) plus the screen's local choices (the
 * picked stake level, the buy-in / top-up / raise amounts, the top-up form). Pure: {@link PokerFlow} maps it to
 * gates and the unit test enumerates it.
 *
 * @param enabled      the game is enabled in the config
 * @param seat         the viewer's seat state
 * @param fixedStake   the table's stake is fixed (humans seated): no level picker, no Back
 * @param levels       the stake levels offered (picker), in order
 * @param level        the chosen level (fixed or picked), or null while picking
 * @param buyIn        the buy-in amount chosen on the stepper
 * @param balance      the viewer's balance
 * @param sittingOut   seated and sitting out
 * @param leaving      seated and leaving after the hand
 * @param canTopUp     a top-up is possible now
 * @param topMax       the largest top-up
 * @param topUpForm    the top-up form is open (local)
 * @param topUp        the top-up amount chosen on the stepper
 * @param turn         the viewer's decision, or null when it is not their turn
 * @param players      players at the table (bots included)
 * @param live         a hand is being played
 * @param presenting   the showdown / award presentation runs
 * @param waitingFor   the name of the player to act ("" none / the viewer)
 * @param secondsLeft  seconds left on the visible timer (action or next hand), −1 none
 * @param nextHandIn   seconds to the next hand, −1 none
 */
public record PokerView(boolean enabled, Seat seat, boolean fixedStake, List<Level> levels, Level level, long buyIn, long balance,
		boolean sittingOut, boolean leaving, boolean canTopUp, long topMax, boolean topUpForm, long topUp, Turn turn, int players, boolean live,
		boolean presenting, String waitingFor, int secondsLeft, int nextHandIn) {
	public enum Seat {
		/** Watching: may buy in. */
		NONE,
		/** Asked to sit; the seat is given after the running hand (bots yield). */
		QUEUED,
		SEATED
	}

	/**
	 * One stake level: the blinds, the buy-in range for this viewer ({@code buyMax < buyMin} = cannot afford) and the VIP
	 * tier it needs.
	 */
	public record Level(String id, long sb, long bb, long buyMin, long buyMax, int minTier, boolean vipOk) {
		public boolean affordable() {
			return buyMax >= buyMin && buyMax > 0;
		}
	}

	/** The viewer's legal actions ({@code legal} tag) and the amount on the raise stepper. */
	public record Turn(int seq, long toCall, boolean canCheck, boolean canRaise, long minTo, long maxTo, boolean isBet, long myStack, long myBet,
			long currentBet, long pot, long bb, long raiseTo) {
		/** Calling puts the viewer all in. */
		public boolean callIsAllIn() {
			return !canCheck && myStack <= toCall;
		}

		public boolean raiseIsAllIn() {
			return raiseTo >= maxTo;
		}
	}

	public boolean seated() {
		return seat == Seat.SEATED;
	}

	public boolean myTurn() {
		return turn != null;
	}
}

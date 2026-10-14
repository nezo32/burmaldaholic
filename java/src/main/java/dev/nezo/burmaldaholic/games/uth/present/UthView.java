package dev.nezo.burmaldaholic.games.uth.present;

import java.util.List;

/**
 * What the UTH v2 screen's gates depend on (docs/architecture/ui-v2.md §5.1), built by the screen from the state tag
 * ({@code UthTableBlockEntity.writeClientState}) and its local {@link UthBetDraft}. Pure; {@link UthFlow} maps it to
 * gates and the unit test enumerates it.
 *
 * @param seated      the viewer holds a seat
 * @param banker      the viewer holds the dealer seat (player-banked table)
 * @param phase       the table phase
 * @param confirmed   the viewer's confirmed bet {@code [ante, trips]} this betting phase, or null
 * @param draft       the bet being built
 * @param minAnte     the table minimum Ante
 * @param maxW        the worst-case limit (6 × Ante + Trips)
 * @param tripsOn     Trips is enabled
 * @param tripsMin    smallest Trips when placed
 * @param balance     the viewer's balance
 * @param lastAnte    the previous round's Ante (0 none)
 * @param lastTrips   the previous round's Trips
 * @param legal       decision ids the viewer may take now ({@code bet_4x}, {@code check}, …), empty when not deciding
 * @param myAnte      the viewer's Ante in the running round (Play bets are multiples of it)
 * @param inRound     the viewer has a hand in the running round
 * @param waitingFor  the deciding player's name ("" none)
 * @param secondsLeft seconds on the visible timer (bet or decision), −1 none
 * @param bank        the player-banked controls, or null at a house table
 */
public record UthView(boolean seated, boolean banker, Phase phase, long[] confirmed, UthBetDraft draft, long minAnte, long maxW, boolean tripsOn,
		long tripsMin, long balance, long lastAnte, long lastTrips, List<String> legal, long myAnte, boolean inRound, String waitingFor,
		int secondsLeft, Bank bank) {
	public enum Phase {
		/** Bets are open (also the idle table). */
		BETTING,
		/** Cards are being dealt / streets turned / decisions taken. */
		PLAYING,
		/** Showdown and settlement are being presented. */
		SHOWDOWN
	}

	/**
	 * @param canTake  the viewer may take the dealer seat now
	 * @param leaving  the viewer (banker) leaves after this round
	 * @param form     the take-the-bank form is open (local)
	 * @param amount   the bank amount on the stepper
	 * @param minBank  the smallest bank
	 */
	public record Bank(boolean canTake, boolean leaving, boolean form, long amount, long minBank) {}

	public boolean betting() {
		return phase == Phase.BETTING;
	}

	public boolean deciding() {
		return !legal.isEmpty();
	}
}

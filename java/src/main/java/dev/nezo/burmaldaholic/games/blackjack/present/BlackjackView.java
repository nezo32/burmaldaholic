package dev.nezo.burmaldaholic.games.blackjack.present;

import java.util.List;
import java.util.Set;

/**
 * What the blackjack v2 screen knows, as a pure value (docs/architecture/ui-v2.md §5): built by the screen from the
 * table's state tag plus the screen's local bet draft, consumed by {@link BlackjackFlow}. No Minecraft types.
 *
 * <p>Blackjack has ONE betting spot and the server takes the whole bet at once ({@code bet amount}), so chips build a
 * local draft (Undo / Clear / Rebet / ×2 edit it) and the primary sends it; the server's {@code bet_placed} echo ends
 * the pending state.
 *
 * @param seated      the viewer has a seat
 * @param canSit      a seat is free (only read when not seated)
 * @param phase       {@link Phase}
 * @param busy        a card is still moving on the felt (decisions wait for it)
 * @param min         table minimum
 * @param max         table maximum
 * @param balance     the viewer's balance
 * @param draft       the local bet draft (chips clicked, not yet sent)
 * @param placed      the bet the server holds for the next round (0 = none)
 * @param lastBet     the viewer's last bet (Rebet)
 * @param canUndo     the draft has chip history
 * @param inRound     the viewer plays this round (has hands)
 * @param myTurn      it is the viewer's decision now
 * @param legal       legal decisions now ({@code hit, stand, double, split, surrender})
 * @param offer       {@code ""}, {@code "insurance"} or {@code "even_money"} (offered to the viewer now)
 * @param insuranceMax the insurance the viewer can take
 * @param waitingFor  name of the player we wait for ("" = nobody / unknown)
 * @param secondsLeft seconds of the running timer, -1 none
 * @param hand        1-based index of the acting hand of the viewer, 0 none
 * @param hands       number of hands of the viewer this round
 * @param pending     an action was sent and the table has not answered yet
 */
public record BlackjackView(boolean seated, boolean canSit, Phase phase, boolean busy, long min, long max, long balance, long draft, long placed,
		long lastBet, boolean canUndo, boolean inRound, boolean myTurn, Set<String> legal, String offer, long insuranceMax, String waitingFor,
		int secondsLeft, int hand, int hands, boolean pending) {
	public enum Phase {
		BETTING, INSURANCE, TURNS, DEALER, RESULT
	}

	/** Chip denominations of the tray (REDESIGN §10), filtered per table. */
	public static final long[] DENOMS = dev.nezo.burmaldaholic.core.chips.ChipMath.ascending();

	public BlackjackView {
		legal = Set.copyOf(legal);
	}

	public static Phase phase(String id, boolean inRound) {
		return switch (id) {
			case "insurance" -> Phase.INSURANCE;
			case "turns" -> Phase.TURNS;
			case "dealer" -> Phase.DEALER;
			case "result" -> Phase.RESULT;
			default -> inRound ? Phase.TURNS : Phase.BETTING;
		};
	}

	/** Betting is open for the viewer: the BETTING phase and no bet held yet. */
	public boolean bettingOpen() {
		return phase == Phase.BETTING && placed == 0;
	}

	/** The most the viewer can bet now. */
	public long cap() {
		return Math.max(0, Math.min(max > 0 ? max : Long.MAX_VALUE, balance));
	}

	/**
	 * Chips of the tray (≤ 5): the denominations within the table limits, ending at the first one above the maximum;
	 * the smallest affordable chip is always offered when the balance covers the minimum (§5.1).
	 */
	public List<Long> chips() {
		return java.util.Arrays.stream(BlackjackFlow.INSTANCE.bet(this).chips()).boxed().toList();
	}

	/** A chip of value {@code d} can be added to the draft now. */
	public boolean chipFits(long d) {
		return draft + d <= cap();
	}
}

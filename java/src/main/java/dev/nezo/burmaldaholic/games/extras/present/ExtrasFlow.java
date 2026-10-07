package dev.nezo.burmaldaholic.games.extras.present;

import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import java.util.List;
import java.util.Optional;

/**
 * The flow of the single-spot extras games (docs/architecture/ui-v2.md §5, REDESIGN.md §7.7): Plinko (Drop), Wheel of
 * Fortune (Spin), Coin Flip (Flip), Dice Duel (Roll). Chips add to the bet ({@link LocalBet}); the primary plays the
 * round with that amount. Disabled ⇒ the reason: the round being revealed (the game's busy word), a game rule (pick a
 * side), no bet / below the minimum / above the maximum / not enough chips (the shared {@link BetGates} reasons). A pawn
 * stake (levels, hearts) has no BetBar: the Dock shows −/+ ({@link #MINUS} / {@link #PLUS}) and the stake in the status.
 * PURE; exhaustively checked by {@code ExtrasFlowTest}.
 */
public final class ExtrasFlow implements Flow<ExtrasView> {
	/** Action ids of the −/+ stake buttons (handled by the screen, never sent). */
	public static final String MINUS = "stake_minus", PLUS = "stake_plus";

	public static final ExtrasFlow PLINKO = new ExtrasFlow("drop", ExtrasKeys.DROP, ExtrasKeys.REASON_DROPPING);
	public static final ExtrasFlow WHEEL = new ExtrasFlow("spin", ExtrasKeys.SPIN, V2Keys.REASON_SPINNING);
	public static final ExtrasFlow COIN = new ExtrasFlow("flip", ExtrasKeys.FLIP, ExtrasKeys.REASON_FLIPPING);
	public static final ExtrasFlow DICE = new ExtrasFlow("roll", ExtrasKeys.ROLL, V2Keys.REASON_ROLLING);

	private final String action;
	private final String labelKey;
	private final String busyKey;

	public ExtrasFlow(String action, String labelKey, String busyKey) {
		this.action = action;
		this.labelKey = labelKey;
		this.busyKey = busyKey;
	}

	public String action() {
		return action;
	}

	@Override
	public Gate primary(ExtrasView v) {
		if (v.busy()) return Gate.off(action, labelKey, busyKey);
		BetView b = v.bet();
		Gate g;
		if (b == null) {
			g = v.stake() > 0 ? Gate.on(action, labelKey) : Gate.off(action, labelKey, ExtrasKeys.REASON_NO_ITEM);
		} else if (b.myTotal() > b.balance()) {
			// the stake is taken with the play action: it must still be covered now (a loss elsewhere since)
			g = Gate.off(action, labelKey, V2Keys.REASON_NOT_ENOUGH);
		} else {
			g = BetGates.primary(action, labelKey, b);
		}
		// a game rule (pick a side) only matters once the bet itself is fine
		return g.enabled() && v.blockKey() != null ? Gate.off(action, labelKey, v.blockKey()) : g;
	}

	@Override
	public List<Gate> actions(ExtrasView v) {
		if (v.bet() != null || v.stakeMax() <= 0) return List.of();
		Gate minus = v.busy() ? Gate.off(MINUS, ExtrasKeys.STAKE_MINUS, busyKey)
			: v.stake() <= 1 ? Gate.off(MINUS, ExtrasKeys.STAKE_MINUS, ExtrasKeys.REASON_STAKE_MIN) : Gate.on(MINUS, ExtrasKeys.STAKE_MINUS);
		Gate plus = v.busy() ? Gate.off(PLUS, ExtrasKeys.STAKE_PLUS, busyKey)
			: v.stake() >= v.stakeMax() ? Gate.off(PLUS, ExtrasKeys.STAKE_PLUS, ExtrasKeys.REASON_STAKE_MAX) : Gate.on(PLUS, ExtrasKeys.STAKE_PLUS);
		return List.of(minus, plus);
	}

	@Override
	public BetView bet(ExtrasView v) {
		return v.bet();
	}

	@Override
	public Optional<String> hintKey(ExtrasView v) {
		if (v.busy()) return Optional.empty();
		if (v.bet() == null) return Optional.empty();
		if (v.bet().myTotal() <= 0) return Optional.of(ExtrasKeys.HINT_CHIPS);
		return v.winUpTo() > 0 ? Optional.of(ExtrasKeys.HINT_WIN_UP_TO) : Optional.empty();
	}

	@Override
	public Object[] hintArgs(ExtrasView v) {
		return new Object[] {v.winUpTo()};
	}
}

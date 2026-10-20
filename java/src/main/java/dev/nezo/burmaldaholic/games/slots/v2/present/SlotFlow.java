package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The slot machine's v2 Dock (owner B6): PRIMARY = Spin {bet} / Stop (skip, never skips the result) / Stop autoplay;
 * the bet is the Dock stepper ({@code − bet +}, the coin × multiplier in the hint, owner R3); ActionRow = Buy {price}
 * (Nether / End), Autoplay, Turbo, the Slot Showdown entry. Every disabled control says why. The paytable is the TopBar
 * {@code ?}. Pure.
 */
public final class SlotFlow implements Flow<SlotView> {
	/** The full Dock: every action in the ActionRow. */
	public static final SlotFlow INSTANCE = new SlotFlow(false);
	/** The compact Dock of narrow frames (T-SLT D3, {@link SlotDock}): Buy / Auto / Turbo / Showdown fold into More. */
	public static final SlotFlow COMPACT = new SlotFlow(true);
	public static final String SPIN = "spin", SKIP = "skip", STOP_AUTO = "stop_auto", BUY = "buy", AUTO = "auto", TURBO = "turbo",
		SHOWDOWN = "showdown", BET_DOWN = "bet_down", BET_UP = "bet_up", MORE = "more";
	private static final String K = "gui.burmaldaholic.slots.";
	private final boolean compact;

	private SlotFlow(boolean compact) {
		this.compact = compact;
	}

	public boolean compact() {
		return compact;
	}

	@Override
	public Gate primary(SlotView v) {
		// autoplay first: Space / the primary stop the autoplay (review T-SLT), a plain spin's Stop skips
		if (v.autoRunning()) return Gate.on(STOP_AUTO, K + "stop_autoplay");
		if (v.busy()) return Gate.on(SKIP, K + "stop");
		if (v.locked()) return new Gate(SPIN, K + "locked", null, false, K + "vip_lock", v.lockArgs());
		if (!v.playable()) return new Gate(SPIN, K + "spin", new Object[] {v.bet()}, false, "gui.burmaldaholic.error.disabled", null);
		if (v.balance() < v.bet()) return new Gate(SPIN, K + "spin", new Object[] {v.bet()}, false, V2Keys.REASON_NOT_ENOUGH, null);
		return Gate.on(SPIN, K + "spin", v.bet());
	}

	/** The stepper's − (lower bet). */
	public static Gate betDown(SlotView v) {
		if (v.busy() || v.autoRunning()) return Gate.off(BET_DOWN, K + "bet_down", V2Keys.REASON_ROUND_IN_PROGRESS);
		if (v.betIndex() <= 0) return Gate.off(BET_DOWN, K + "bet_down", V2Keys.REASON_BELOW_MIN, v.bet());
		return Gate.on(BET_DOWN, K + "bet_down");
	}

	/** The stepper's + (higher bet; the ladder is already VIP / owner capped). */
	public static Gate betUp(SlotView v) {
		if (v.busy() || v.autoRunning()) return Gate.off(BET_UP, K + "bet_up", V2Keys.REASON_ROUND_IN_PROGRESS);
		if (v.betIndex() >= v.betCount() - 1) return Gate.off(BET_UP, K + "bet_up", V2Keys.REASON_ABOVE_MAX, v.bet());
		return Gate.on(BET_UP, K + "bet_up");
	}

	@Override
	public List<Gate> actions(SlotView v) {
		List<Gate> all = secondary(v);
		if (!compact || all.size() <= 1) return all;
		List<Gate> out = new ArrayList<>(1);
		out.add(Gate.on(MORE, K + "more"));
		return out;
	}

	/** Buy / Autoplay (Stop autoplay) / Turbo / Slot Showdown: the ActionRow of the full Dock, the More menu of the compact one. */
	public static List<Gate> secondary(SlotView v) {
		List<Gate> out = new ArrayList<>();
		boolean idle = !v.busy() && !v.autoRunning();
		if (v.buyAllowed()) {
			if (!idle) out.add(new Gate(BUY, K + "buy.button", new Object[] {v.buyPrice()}, false, V2Keys.REASON_ROUND_IN_PROGRESS, null));
			else if (!v.playable()) out.add(new Gate(BUY, K + "buy.button", new Object[] {v.buyPrice()}, false, "gui.burmaldaholic.error.disabled", null));
			else if (v.balance() < v.buyPrice()) out.add(new Gate(BUY, K + "buy.button", new Object[] {v.buyPrice()}, false, V2Keys.REASON_NOT_ENOUGH, null));
			else out.add(Gate.on(BUY, K + "buy.button", v.buyPrice()));
		}
		if (v.autoplayAllowed()) {
			if (v.autoRunning()) out.add(Gate.on(STOP_AUTO, K + "stop_autoplay"));
			else if (v.busy()) out.add(Gate.off(AUTO, K + "auto", V2Keys.REASON_ROUND_IN_PROGRESS));
			else if (!v.playable()) out.add(Gate.off(AUTO, K + "auto", "gui.burmaldaholic.error.disabled"));
			else out.add(Gate.on(AUTO, K + "auto"));
		}
		if (v.turboAllowed()) out.add(Gate.on(TURBO, K + (v.turbo() ? "turbo_on" : "turbo")));
		Gate sd = v.showdown();
		if (sd != null) {
			out.add(idle ? new Gate(SHOWDOWN, sd.labelKey(), sd.labelArgs(), true, null, null)
				: new Gate(SHOWDOWN, sd.labelKey(), sd.labelArgs(), false, V2Keys.REASON_ROUND_IN_PROGRESS, null));
		}
		return out;
	}

	/** Line 2: the bet as coin × multiplier (owner R3). */
	@Override
	public Optional<String> hintKey(SlotView v) {
		return BetSplit.multiplier(v.bet(), v.coin()) > 0 ? Optional.of(K + "bet_coin") : Optional.empty();
	}

	@Override
	public Object[] hintArgs(SlotView v) {
		return new Object[] {v.coin(), BetSplit.multiplier(v.bet(), v.coin())};
	}
}

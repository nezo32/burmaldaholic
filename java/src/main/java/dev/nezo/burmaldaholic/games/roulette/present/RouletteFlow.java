package dev.nezo.burmaldaholic.games.roulette.present;

import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the roulette v2 screen lets you press, and why not (docs/architecture/ui-v2.md §5): the primary button, the bet
 * tools, the chips and the felt as {@link Gate}s from a small pure {@link View} built from the table's state tag. A
 * disabled gate always carries its reason (the status line shows it, a click on it re-shows it), so the betting flow can
 * never be silently stuck — the v0.1.1 "roulette won't let me place a bet" class of bug.
 *
 * <p>Roulette seats you with your first chip (the server's {@code bet} sits a standing player), so the felt and chips
 * are live before you sit; the primary offers {@code Sit down} only while you have no bet and are not seated.
 */
public final class RouletteFlow implements Flow<RouletteView> {
	public static final String K = "gui.burmaldaholic.roulette.v2.";
	public static final String CORE = V2Keys.P;
	public static final String SPIN = "spin";
	public static final RouletteFlow FLOW = new RouletteFlow();

	public enum Phase {
		BETTING, NO_MORE_BETS, SPIN, RESULT;

		public static Phase of(String id) {
			return switch (id) {
				case "no_more_bets" -> NO_MORE_BETS;
				case "spin" -> SPIN;
				case "result" -> RESULT;
				default -> BETTING;
			};
		}
	}

	/**
	 * @param seated       the viewer has a seat
	 * @param tableFull    every seat is taken by someone else
	 * @param enabled      roulette is enabled in the config
	 * @param phase        the round's phase
	 * @param min          minimum chip (per bet)
	 * @param max          maximum per spin (total), ≤ 0 = none
	 * @param minTotal     High-Roller minimum per spin (0 = none)
	 * @param balance      the viewer's chips
	 * @param total        the viewer's bets on the layout this spin
	 * @param canRebet     the server has a last slip and betting is open
	 * @param rebetAmount  that slip's total (0 = unknown)
	 * @param ready        the viewer pressed Spin / Ready
	 * @param multiplayer  more than one bettor: the button says Ready
	 * @param readyCount   bettors ready
	 * @param bettors      bettors this spin
	 * @param secondsLeft  seconds of the current phase (-1 = no timer)
	 */
	public record View(boolean seated, boolean tableFull, boolean enabled, Phase phase, long min, long max, long minTotal, long balance, long total,
			boolean canRebet, long rebetAmount, boolean ready, boolean multiplayer, int readyCount, int bettors, int secondsLeft) {
		public boolean bettingOpen() {
			return enabled && phase == Phase.BETTING;
		}
	}

	private RouletteFlow() {}

	// ---- Flow<RouletteView> (the kit's contract) ------------------------------------------------------------------------

	@Override
	public Gate primary(RouletteView v) {
		return primary(v.flow());
	}

	@Override
	public BetView bet(RouletteView v) {
		return v.bet();
	}

	@Override
	public Gate tool(String id, RouletteView v) {
		return tool(id, v.flow());
	}

	@Override
	public Gate chip(long denom, RouletteView v) {
		return chip(v.flow(), denom);
	}

	@Override
	public Optional<String> hintKey(RouletteView v) {
		return hintKey(v.flow());
	}

	@Override
	public Object[] hintArgs(RouletteView v) {
		return v.flow().secondsLeft() >= 0 && v.flow().phase() == Phase.BETTING && v.flow().total() > 0 ? new Object[] {v.flow().secondsLeft()}
			: new Object[0];
	}

	// ---- the gates --------------------------------------------------------------------------------------------------

	/** Why the felt and the chips take no bet now, or empty when they do. */
	public static Optional<Gate> betBlock(View v) {
		if (!v.enabled()) return Optional.of(Gate.off(BetGates.CHIP, V2Keys.CHIP, "gui.burmaldaholic.error.disabled"));
		if (v.phase() != Phase.BETTING) {
			return Optional.of(v.phase() == Phase.RESULT && v.secondsLeft() >= 0
				? Gate.off(BetGates.CHIP, V2Keys.CHIP, V2Keys.REASON_BETS_OPEN_IN, v.secondsLeft())
				: Gate.off(BetGates.CHIP, V2Keys.CHIP, V2Keys.REASON_BETS_CLOSED));
		}
		if (!v.seated() && v.tableFull()) return Optional.of(Gate.off(BetGates.CHIP, V2Keys.CHIP, V2Keys.REASON_TABLE_FULL));
		if (v.balance() < Math.max(1, v.min())) return Optional.of(Gate.off(BetGates.CHIP, V2Keys.CHIP, V2Keys.REASON_NOT_ENOUGH_MIN, v.min()));
		return Optional.empty();
	}

	/** A chip of {@code denom}: placeable now, or why not. */
	public static Gate chip(View v, long denom) {
		Optional<Gate> block = betBlock(v);
		if (block.isPresent()) return block.get();
		if (denom < v.min()) return Gate.off(BetGates.CHIP, V2Keys.CHIP, V2Keys.REASON_BELOW_MIN, v.min());
		if (v.max() > 0 && v.total() + denom > v.max()) return Gate.off(BetGates.CHIP, V2Keys.CHIP, V2Keys.REASON_ABOVE_MAX, v.max());
		if (denom > v.balance()) return Gate.off(BetGates.CHIP, V2Keys.CHIP, V2Keys.REASON_NOT_ENOUGH);
		return new Gate(BetGates.CHIP, V2Keys.CHIP, new Object[] {denom}, true, null, null);
	}

	/** The PrimaryButton. */
	public static Gate primary(View v) {
		String label = v.multiplayer() ? "gui.burmaldaholic.common.ready" : "gui.burmaldaholic.common.spin";
		if (!v.enabled()) return Gate.off(SPIN, label, "gui.burmaldaholic.error.disabled");
		switch (v.phase()) {
			case NO_MORE_BETS, SPIN -> {
				return Gate.off(SPIN, K + "primary.spinning", V2Keys.REASON_SPINNING);
			}
			case RESULT -> {
				String reason = v.secondsLeft() >= 0 ? V2Keys.REASON_BETS_OPEN_IN : V2Keys.REASON_ROUND_IN_PROGRESS;
				Object[] args = v.secondsLeft() >= 0 ? new Object[] {v.secondsLeft()} : null;
				if (v.rebetAmount() > 0) return new Gate(BetGates.REBET, V2Keys.PRIMARY_REBET, new Object[] {v.rebetAmount()}, false, reason, args);
				return new Gate(SPIN, label, null, false, reason, args);
			}
			default -> { }
		}
		if (!v.seated() && v.tableFull()) return Gate.off(BetGates.SIT, V2Keys.PRIMARY_SIT, V2Keys.REASON_TABLE_FULL);
		if (v.total() <= 0) {
			if (v.canRebet() && v.rebetAmount() > 0 && v.rebetAmount() <= v.balance()) {
				return Gate.on(BetGates.REBET, V2Keys.PRIMARY_REBET, v.rebetAmount());
			}
			if (v.balance() < Math.max(1, v.min())) {
				return Gate.off(v.seated() ? SPIN : BetGates.SIT, v.seated() ? label : V2Keys.PRIMARY_SIT, V2Keys.REASON_NOT_ENOUGH_MIN, v.min());
			}
			return v.seated() ? Gate.off(SPIN, label, V2Keys.REASON_PLACE_BET) : Gate.on(BetGates.SIT, V2Keys.PRIMARY_SIT);
		}
		if (v.minTotal() > 0 && v.total() < v.minTotal()) {
			return Gate.off(SPIN, label, "gui.burmaldaholic.roulette.error.min_total", v.minTotal());
		}
		if (v.ready()) {
			return Gate.off(SPIN, label, K + "reason.waiting_ready", v.readyCount(), v.bettors());
		}
		return Gate.on(SPIN, label);
	}

	/** Bet tool by id: {@code undo}, {@code clear}, {@code rebet}, {@code double}. */
	public static Gate tool(String id, View v) {
		String label = switch (id) {
			case BetGates.UNDO -> V2Keys.TOOL_UNDO;
			case BetGates.CLEAR -> V2Keys.TOOL_CLEAR;
			case BetGates.REBET -> V2Keys.TOOL_REBET;
			case BetGates.DOUBLE -> V2Keys.TOOL_DOUBLE;
			default -> throw new IllegalArgumentException(id);
		};
		Optional<Gate> block = betBlock(v);
		boolean takeBack = id.equals(BetGates.UNDO) || id.equals(BetGates.CLEAR);
		if (block.isPresent() && !(takeBack && v.bettingOpen() && v.total() > 0)) {
			Gate b = block.get();
			return new Gate(id, label, null, false, b.reasonKey(), b.reasonArgs());
		}
		return switch (id) {
			case BetGates.UNDO -> v.total() > 0 ? Gate.on(id, label) : Gate.off(id, label, V2Keys.REASON_NOTHING_TO_UNDO);
			case BetGates.CLEAR -> v.total() > 0 ? Gate.on(id, label) : Gate.off(id, label, V2Keys.REASON_NOTHING_TO_CLEAR);
			case BetGates.REBET -> !v.canRebet() ? Gate.off(id, label, K + "reason.no_last_bets")
				: v.rebetAmount() > v.balance() ? Gate.off(id, label, V2Keys.REASON_NOT_ENOUGH) : Gate.on(id, label);
			default -> v.total() <= 0 ? Gate.off(id, label, V2Keys.REASON_CANNOT_DOUBLE)
				: v.max() > 0 && 2 * v.total() > v.max() ? Gate.off(id, label, V2Keys.REASON_ABOVE_MAX, v.max())
				: v.total() > v.balance() ? Gate.off(id, label, V2Keys.REASON_NOT_ENOUGH) : Gate.on(id, label);
		};
	}

	/** The four tools in the Dock's fixed order. */
	public static List<Gate> tools(View v) {
		List<Gate> out = new ArrayList<>(4);
		for (String id : BetGates.TOOLS) {
			out.add(tool(id, v));
		}
		return out;
	}

	/** The status line's hint when nothing more urgent is shown. */
	public static Optional<String> hintKey(View v) {
		if (!v.enabled()) return Optional.empty();
		return switch (v.phase()) {
			case BETTING -> v.total() > 0 ? Optional.of(v.secondsLeft() >= 0 ? K + "status.spin_in" : K + "status.add_or_spin") : Optional.empty();
			case NO_MORE_BETS, SPIN -> Optional.of(K + "status.ball_spinning");
			case RESULT -> Optional.empty();
		};
	}
}

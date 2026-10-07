package dev.nezo.burmaldaholic.games.roulette.present;

import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the roulette v2 screen lets you press, and why not (docs/architecture/ui-v2.md §5): the primary button, the bet
 * tools, the chips and the felt as {@link Gate}s from a small pure {@link View} built from the table's state tag. A
 * disabled gate always carries its reason (the status line shows it, a click on it re-shows it), so the betting flow can
 * never be silently stuck — the v0.1.1 "roulette won't let me bet" class of bug.
 *
 * <p>Roulette seats you with your first chip (the server's {@code bet} sits a standing player), so the felt and chips
 * are live before you sit; the primary offers {@code Sit down} only while you have no bet and are not seated.
 */
public final class RouletteFlow {
	public static final String K = "gui.burmaldaholic.roulette.v2.";
	public static final String CORE = "gui.burmaldaholic.core.v2.";

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

	/** Why the felt and the chips take no bet now, or empty when they do. */
	public static Optional<Gate> betBlock(View v) {
		if (!v.enabled()) return Optional.of(Gate.off("bet", K + "bet", "gui.burmaldaholic.error.disabled"));
		if (v.phase() != Phase.BETTING) {
			return Optional.of(v.phase() == Phase.RESULT && v.secondsLeft() >= 0
				? Gate.off("bet", K + "bet", CORE + "reason.bets_open_in", v.secondsLeft())
				: Gate.off("bet", K + "bet", CORE + "reason.bets_closed"));
		}
		if (!v.seated() && v.tableFull()) return Optional.of(Gate.off("bet", K + "bet", CORE + "reason.table_full"));
		if (v.balance() < Math.max(1, v.min())) return Optional.of(Gate.off("bet", K + "bet", CORE + "reason.not_enough", v.min()));
		return Optional.empty();
	}

	/** A chip of {@code denom}: placeable now, or why not. */
	public static Gate chip(View v, long denom) {
		Optional<Gate> block = betBlock(v);
		if (block.isPresent()) return block.get();
		if (denom < v.min()) return Gate.off("chip", K + "bet", CORE + "reason.below_min", v.min());
		if (v.max() > 0 && v.total() + denom > v.max()) return Gate.off("chip", K + "bet", CORE + "reason.above_max", v.max());
		if (denom > v.balance()) return Gate.off("chip", K + "bet", CORE + "reason.not_enough", denom);
		return Gate.on("chip", K + "bet");
	}

	/** The PrimaryButton. */
	public static Gate primary(View v) {
		if (!v.enabled()) return Gate.off("spin", "gui.burmaldaholic.common.spin", "gui.burmaldaholic.error.disabled");
		String label = v.multiplayer() ? "gui.burmaldaholic.common.ready" : "gui.burmaldaholic.common.spin";
		switch (v.phase()) {
			case NO_MORE_BETS, SPIN -> {
				return Gate.off("spin", K + "primary.spinning", CORE + "reason.spinning");
			}
			case RESULT -> {
				if (v.rebetAmount() > 0) {
					return v.secondsLeft() >= 0 ? new Gate("rebet", CORE + "primary.rebet", new Object[] {v.rebetAmount()}, false, CORE + "reason.bets_open_in",
						new Object[] {v.secondsLeft()}) : new Gate("rebet", CORE + "primary.rebet", new Object[] {v.rebetAmount()}, false,
						CORE + "reason.round_in_progress", null);
				}
				return v.secondsLeft() >= 0 ? Gate.off("spin", label, CORE + "reason.bets_open_in", v.secondsLeft())
					: Gate.off("spin", label, CORE + "reason.round_in_progress");
			}
			default -> { }
		}
		if (!v.seated() && v.tableFull()) return Gate.off("sit", CORE + "primary.sit", CORE + "reason.table_full");
		if (v.total() <= 0) {
			if (v.canRebet() && v.rebetAmount() > 0 && v.rebetAmount() <= v.balance()) {
				return Gate.on("rebet", CORE + "primary.rebet", v.rebetAmount());
			}
			if (!v.seated()) {
				return v.balance() >= Math.max(1, v.min()) ? Gate.on("sit", CORE + "primary.sit")
					: Gate.off("sit", CORE + "primary.sit", CORE + "reason.not_enough", v.min());
			}
			return Gate.off("spin", label, CORE + "reason.place_bet");
		}
		if (v.minTotal() > 0 && v.total() < v.minTotal()) {
			return Gate.off("spin", label, "gui.burmaldaholic.roulette.error.min_total", v.minTotal());
		}
		if (v.ready()) {
			return Gate.off("spin", label, K + "reason.waiting_ready", v.readyCount(), v.bettors());
		}
		return Gate.on("spin", label);
	}

	/** Bet tool by id: {@code undo}, {@code clear}, {@code rebet}, {@code double}. */
	public static Gate tool(String id, View v) {
		String label = CORE + "tool." + id;
		Optional<Gate> block = betBlock(v);
		if (block.isPresent()) {
			Gate b = block.get();
			return new Gate(id, label, null, false, b.reasonKey(), b.reasonArgs());
		}
		if (v.ready()) return Gate.off(id, label, K + "reason.ready_locked");
		return switch (id) {
			case "undo", "clear" -> v.total() > 0 ? Gate.on(id, label) : Gate.off(id, label, CORE + "reason.place_bet");
			case "rebet" -> !v.canRebet() ? Gate.off(id, label, K + "reason.no_last_bets")
				: v.rebetAmount() > v.balance() ? Gate.off(id, label, CORE + "reason.not_enough", v.rebetAmount()) : Gate.on(id, label);
			case "double" -> v.total() <= 0 ? Gate.off(id, label, CORE + "reason.place_bet")
				: v.max() > 0 && 2 * v.total() > v.max() ? Gate.off(id, label, CORE + "reason.above_max", v.max())
				: v.total() > v.balance() ? Gate.off(id, label, CORE + "reason.not_enough", v.total()) : Gate.on(id, label);
			default -> throw new IllegalArgumentException(id);
		};
	}

	/** The four tools in the Dock's fixed order. */
	public static List<Gate> tools(View v) {
		List<Gate> out = new ArrayList<>(4);
		for (String id : List.of("undo", "clear", "rebet", "double")) {
			out.add(tool(id, v));
		}
		return out;
	}

	/** The status line's hint when nothing is wrong (first line). */
	public static Optional<String> hintKey(View v) {
		if (!v.enabled()) return Optional.empty();
		return switch (v.phase()) {
			case BETTING -> Optional.of(v.total() > 0 ? K + "status.add_or_spin" : CORE + "status.click_layout");
			case NO_MORE_BETS, SPIN -> Optional.of("gui.burmaldaholic.roulette.no_more_bets");
			case RESULT -> Optional.empty();
		};
	}
}

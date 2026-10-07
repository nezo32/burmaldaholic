package dev.nezo.burmaldaholic.games.roulette.present;

import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The roulette v2 screen's view of the table (docs/architecture/ui-v2.md §5.1), built from the state tag by the screen:
 * the {@link RouletteFlow.View} the gates read, the kit's {@link BetView} the BetBar reads, and the viewer's bets by spot.
 * Pure.
 */
public record RouletteView(RouletteFlow.View flow, BetView bet, Map<Spot, Long> mine) {
	public RouletteView {
		mine = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(mine));
	}

	/** The kit's bet model of a roulette flow view (multi-spot: spot keys → amounts). */
	public static BetView betView(RouletteFlow.View v, Map<Spot, Long> mine) {
		BetView.Builder b = BetView.builder().seated(v.seated()).canSit(!v.tableFull())
			.phase(switch (v.phase()) {
				case BETTING -> BetView.Phase.BETTING;
				case NO_MORE_BETS -> BetView.Phase.LOCKED;
				case SPIN -> BetView.Phase.ACTING;
				case RESULT -> BetView.Phase.SETTLING;
			})
			.bettingOpen(v.bettingOpen()).limits(v.min(), v.max()).balance(v.balance()).myTotal(v.total())
			.canUndo(v.bettingOpen() && v.total() > 0).canClear(v.bettingOpen() && v.total() > 0).rebet(v.canRebet(), v.rebetAmount())
			.canDouble(v.bettingOpen() && v.total() > 0).secondsLeft(v.secondsLeft());
		mine.forEach((s, a) -> b.spot(s.key(), a));
		return b.build();
	}

	public static RouletteView of(RouletteFlow.View v, Map<Spot, Long> mine) {
		return new RouletteView(v, betView(v, mine), mine);
	}

	public List<Spot> spots() {
		return List.copyOf(mine.keySet());
	}
}

package dev.nezo.burmaldaholic.games.uth;

import dev.nezo.burmaldaholic.core.config.sections.UthConfig;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.games.uth.logic.Paytables;
import dev.nezo.burmaldaholic.games.uth.logic.TripsMath;

/**
 * House-edge figures of GAME_DESIGN §21.3 used for round reports ({@code PlayResult#houseEdge}: VIP
 * cashback is {@code rate × Σ bet × edge}) and the {@code uth.validateEdge} check.
 */
public final class UthMath {
	/** Ante + Blind + Play at optimal play: 2.185 % of the Ante over 4.15 Antes wagered → 0.527 % of the wager. */
	public static final double ELEMENT_OF_RISK = 0.00527;
	/** Default Trips paytable: 2 547 324 / 133 784 560. */
	public static final double TRIPS_EDGE = 2_547_324.0 / 133_784_560.0;

	private UthMath() {}

	/** Stake-weighted edge of a seat's round: main bets at the element of risk, Trips at its own edge. */
	public static double edgeOf(long mainStake, long trips) {
		long total = mainStake + trips;
		if (total <= 0) {
			return ELEMENT_OF_RISK;
		}
		double tripsEdge = Math.max(0, TripsMath.houseEdge(paytables()));
		return (ELEMENT_OF_RISK * mainStake + tripsEdge * trips) / total;
	}

	/** Blind / Trips paytables of the current {@code uth} config (call again after reloads). */
	public static Paytables paytables() {
		UthConfig c = CasinoConfig.uth();
		return new Paytables(c.blindPays, c.tripsPays);
	}

	/** {@code uth.validateEdge}: a Trips paytable with an edge of 1 % or less is logged loudly (never auto-fixed). */
	static void validate(Paytables pays) {
		double edge = TripsMath.houseEdge(pays);
		if (edge <= 0.01) {
			Burmaldaholic.LOGGER.warn("!!! uth.tripsPays gives the Trips bet a house edge of {} % (≤ 1 %). Default 50-40-30-8-6-5-3 = 1.904 %.",
				String.format(java.util.Locale.ROOT, "%.3f", edge * 100));
		}
	}
}

package dev.nezo.burmaldaholic;

import dev.nezo.burmaldaholic.chaos.ChaosModule;
import dev.nezo.burmaldaholic.core.CoreModule;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.lastchance.LastChanceModule;
import dev.nezo.burmaldaholic.loan.LoanModule;
import dev.nezo.burmaldaholic.multiplayer.MultiplayerModule;
import dev.nezo.burmaldaholic.vip.VipModule;
import dev.nezo.burmaldaholic.worldgen.WorldgenModule;
import java.util.List;

/**
 * THE list of feature modules. Every module is pre-created, so nobody needs to edit this file.
 * Order = registration order; {@link CoreModule} is always first.
 */
public final class ModuleList {
	private ModuleList() {}

	public static List<CasinoModule> create() {
		return List.of(
			new CoreModule(),
			new BlackjackModule(),
			new PokerModule(),
			new SlotsModule(),
			new RouletteModule(),
			new CrapsModule(),
			new ExtrasModule(),
			new LoanModule(),
			new ChaosModule(),
			new LastChanceModule(),
			new WorldgenModule(),
			new VipModule(),
			new MultiplayerModule(),
			new BaccaratModule()
		);
	}
}

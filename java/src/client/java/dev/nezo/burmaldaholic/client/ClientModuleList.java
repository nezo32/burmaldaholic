package dev.nezo.burmaldaholic.client;

import dev.nezo.burmaldaholic.chaos.client.ChaosClientModule;
import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.games.blackjack.client.BlackjackClientModule;
import dev.nezo.burmaldaholic.games.craps.client.CrapsClientModule;
import dev.nezo.burmaldaholic.games.extras.client.ExtrasClientModule;
import dev.nezo.burmaldaholic.games.poker.client.PokerClientModule;
import dev.nezo.burmaldaholic.games.roulette.client.RouletteClientModule;
import dev.nezo.burmaldaholic.games.slots.client.SlotsClientModule;
import dev.nezo.burmaldaholic.games.uth.client.UthClientModule;
import dev.nezo.burmaldaholic.lastchance.client.LastChanceClientModule;
import dev.nezo.burmaldaholic.loan.client.LoanClientModule;
import dev.nezo.burmaldaholic.multiplayer.client.MultiplayerClientModule;
import dev.nezo.burmaldaholic.vip.client.VipClientModule;
import dev.nezo.burmaldaholic.worldgen.client.WorldgenClientModule;
import java.util.List;

/** Client counterpart of {@code ModuleList}. Pre-populated; nobody needs to edit it. */
public final class ClientModuleList {
	private ClientModuleList() {}

	public static List<CasinoClientModule> create() {
		return List.of(
			new CoreClientModule(),
			new BlackjackClientModule(),
			new PokerClientModule(),
			new SlotsClientModule(),
			new RouletteClientModule(),
			new CrapsClientModule(),
			new ExtrasClientModule(),
			new LoanClientModule(),
			new ChaosClientModule(),
			new LastChanceClientModule(),
			new WorldgenClientModule(),
			new VipClientModule(),
			new MultiplayerClientModule(),
			new UthClientModule()
		);
	}
}

package dev.nezo.burmaldaholic.games.roulette.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;

/** Client half of the "roulette" module: the betting-layout screen for both tables. */
public final class RouletteClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "roulette";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		ctx.tableScreen(RouletteModule.TABLE, RouletteScreen::new);
		ctx.tableScreen(RouletteModule.HIGH_ROLLER_TABLE, RouletteScreen::new);
		RouletteTableRenderer.register(); // the in-world wheel (tables.md §1.5)
	}
}

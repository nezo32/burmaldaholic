package dev.nezo.burmaldaholic.games.roulette.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;

/** Client half of the "roulette" module (screens, renderers, HUD, client payload receivers). */
public final class RouletteClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "roulette";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		// TODO(roulette): e.g. ctx.tableScreen(RouletteModule.TABLE, RouletteScreen::new);
	}
}

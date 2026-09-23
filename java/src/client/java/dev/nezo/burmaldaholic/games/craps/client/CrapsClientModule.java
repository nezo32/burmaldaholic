package dev.nezo.burmaldaholic.games.craps.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;

/** Client half of the "craps" module (screens, renderers, HUD, client payload receivers). */
public final class CrapsClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "craps";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		// TODO(craps): e.g. ctx.tableScreen(CrapsModule.TABLE, CrapsScreen::new);
	}
}

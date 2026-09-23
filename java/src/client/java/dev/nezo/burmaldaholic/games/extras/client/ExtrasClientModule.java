package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;

/** Client half of the "extras" module (screens, renderers, HUD, client payload receivers). */
public final class ExtrasClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "extras";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		// TODO(extras): e.g. ctx.tableScreen(ExtrasModule.TABLE, ExtrasScreen::new);
	}
}

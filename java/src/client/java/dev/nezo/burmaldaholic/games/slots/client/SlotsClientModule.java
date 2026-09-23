package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;

/** Client half of the "slots" module (screens, renderers, HUD, client payload receivers). */
public final class SlotsClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "slots";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		// TODO(slots): e.g. ctx.tableScreen(SlotsModule.TABLE, SlotsScreen::new);
	}
}

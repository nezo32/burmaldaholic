package dev.nezo.burmaldaholic.lastchance.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;

/** Client half of the "lastchance" module (screens, renderers, HUD, client payload receivers). */
public final class LastChanceClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "lastchance";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		// TODO(lastchance): e.g. ctx.tableScreen(LastChanceModule.TABLE, LastChanceScreen::new);
	}
}

package dev.nezo.burmaldaholic.chaos.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;

/** Client half of the "chaos" module (screens, renderers, HUD, client payload receivers). */
public final class ChaosClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "chaos";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		// TODO(chaos): e.g. ctx.tableScreen(ChaosModule.TABLE, ChaosScreen::new);
	}
}

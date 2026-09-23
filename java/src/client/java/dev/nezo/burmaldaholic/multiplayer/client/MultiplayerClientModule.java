package dev.nezo.burmaldaholic.multiplayer.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;

/** Client half of the "multiplayer" module (screens, renderers, HUD, client payload receivers). */
public final class MultiplayerClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "multiplayer";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		// TODO(multiplayer): e.g. ctx.tableScreen(MultiplayerModule.TABLE, MultiplayerScreen::new);
	}
}

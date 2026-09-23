package dev.nezo.burmaldaholic.games.poker.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;

/** Client half of the "poker" module (screens, renderers, HUD, client payload receivers). */
public final class PokerClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "poker";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		// TODO(poker): e.g. ctx.tableScreen(PokerModule.TABLE, PokerScreen::new);
	}
}

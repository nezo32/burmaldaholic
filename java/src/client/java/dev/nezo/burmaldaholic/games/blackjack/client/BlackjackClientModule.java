package dev.nezo.burmaldaholic.games.blackjack.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;

/** Client half of the "blackjack" module (screens, renderers, HUD, client payload receivers). */
public final class BlackjackClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "blackjack";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		// TODO(blackjack): e.g. ctx.tableScreen(BlackjackModule.TABLE, BlackjackScreen::new);
	}
}

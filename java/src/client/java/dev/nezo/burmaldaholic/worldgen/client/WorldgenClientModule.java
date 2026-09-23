package dev.nezo.burmaldaholic.worldgen.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;

/** Client half of the "worldgen" module (screens, renderers, HUD, client payload receivers). */
public final class WorldgenClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "worldgen";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		// TODO(worldgen): e.g. ctx.tableScreen(WorldgenModule.TABLE, WorldgenScreen::new);
	}
}

package dev.nezo.burmaldaholic.vip.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;

/** Client half of the "vip" module (screens, renderers, HUD, client payload receivers). */
public final class VipClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "vip";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		// TODO(vip): e.g. ctx.tableScreen(VipModule.TABLE, VipScreen::new);
	}
}

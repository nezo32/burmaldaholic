package dev.nezo.burmaldaholic.pvp.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;

/** Client half of "pvp": lobby / result screens, the match ticker HUD segment (PVP.md §3.11). */
public final class PvpClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "pvp";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		// TODO(J-P2): PvpMatchSyncPayload receiver → client.pvp.PvpScreens.open(modeId, state); HUD ticker segment.
	}
}

package dev.nezo.burmaldaholic.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.client.table.ClientTableCache;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.network.CasinoModeSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client core: casino-mode state, table state cache. J-core adds the HUD here. */
public final class CoreClientModule implements CasinoClientModule {
	private static volatile boolean casinoEnabled;

	/** Casino mode as last reported by the server (false in menus / when not connected). */
	public static boolean casinoEnabled() {
		return casinoEnabled;
	}

	@Override
	public String id() {
		return "core";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		CasinoMode.setClientLookup(CoreClientModule::casinoEnabled);
		ClientPlayNetworking.registerGlobalReceiver(CasinoModeSyncPayload.TYPE, (payload, context) -> casinoEnabled = payload.enabled());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			casinoEnabled = false;
			ClientTableCache.clear();
		});
		ClientTableCache.register();
	}
}

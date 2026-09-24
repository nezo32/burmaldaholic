package dev.nezo.burmaldaholic.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.client.cashier.CashierScreen;
import dev.nezo.burmaldaholic.client.hud.CasinoHud;
import dev.nezo.burmaldaholic.client.menu.ClientCasinoMenu;
import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.client.table.ClientTableCache;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.config.ConfigManager;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.network.CasinoModeSyncPayload;
import dev.nezo.burmaldaholic.core.network.ConfigSyncPayload;
import dev.nezo.burmaldaholic.core.network.PlayerStatusPayload;
import dev.nezo.burmaldaholic.core.network.TableErrorPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Client core: casino-mode state, status/config sync, HUD, table state cache, cashier screen. */
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
		ClientPlayNetworking.registerGlobalReceiver(PlayerStatusPayload.TYPE, (payload, context) -> ClientCasinoState.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) -> {
			// In single player the integrated server already set the same ConfigManager.
			if (!Minecraft.getInstance().hasSingleplayerServer()) {
				JsonObject json = JsonParser.parseString(payload.json()).getAsJsonObject();
				ConfigManager.get().applySynced(json);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(TableErrorPayload.TYPE, (payload, context) -> {
			if (Minecraft.getInstance().gui.screen() instanceof CasinoTableScreen screen && screen.getMenu().pos().equals(payload.pos())) {
				screen.showError(payload.message());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			casinoEnabled = false;
			ClientTableCache.clear();
			ClientCasinoState.reset();
			if (!client.hasSingleplayerServer()) {
				ConfigManager.get().load();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> ClientCasinoState.tick());
		ClientTableCache.register();
		ClientCasinoMenu.register();
		CasinoHud.init();
		ctx.tableScreen(CoreContent.CASHIER, CashierScreen::new);
		ctx.tableScreen(CoreContent.NETHER_CASHIER, CashierScreen::new);
	}
}

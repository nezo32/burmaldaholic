package dev.nezo.burmaldaholic.pvp.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.CoreClientModule;
import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.pvp.net.PvpActionPayload;
import dev.nezo.burmaldaholic.pvp.net.PvpSyncPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Client half of "pvp": lobby / match / result / taunt screens and the match ticker HUD line (PVP.md §3.11). */
public final class PvpClientModule implements CasinoClientModule {
	/** Ticker position: top centre, below the boss-bar area. */
	static final int TICKER_Y = 30;

	@Override
	public String id() {
		return "pvp";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		ClientPlayNetworking.registerGlobalReceiver(PvpSyncPayload.TYPE, (payload, context) -> ClientPvp.receive(payload));
		PvpScreens.setActions(new PvpScreens.Actions() {
			@Override
			public void send(String action, String matchId, String arg, long value) {
				if (ClientPlayNetworking.canSend(PvpActionPayload.TYPE)) {
					ClientPlayNetworking.send(new PvpActionPayload(action, matchId.isEmpty() ? ClientPvp.matchId() : matchId, arg, value));
				}
			}

			@Override
			public void openTaunts(Screen parent) {
				ClientPvp.openTaunts(parent);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> ClientPvp.tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> ClientPvp.setStateForTests(null, "clear"));
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Burmaldaholic.id("pvp_ticker"), PvpClientModule::ticker);
	}

	/** One line, top centre, only while no PvP screen is open (§3.11.1 "match ticker"). */
	private static void ticker(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !CoreClientModule.casinoEnabled() || mc.gui.hud.isHidden() || ClientPvp.isPvpScreen(mc.gui.screen())) {
			return;
		}
		Component line = ClientPvp.tickerLine();
		if (line == null) {
			return;
		}
		int w = mc.font.width(line);
		int x = (g.guiWidth() - w) / 2;
		g.fill(x - 4, TICKER_Y - 2, x + w + 4, TICKER_Y + 10, 0x88000000);
		g.text(mc.font, line, x, TICKER_Y, 0xFFFFD700, true);
	}
}

package dev.nezo.burmaldaholic.lastchance.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.lastchance.net.CoinFlipPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

/** Client half of Last Chance: the coin-flip overlay shown when the server flips for this player. */
public final class LastChanceClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "lastchance";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		ClientPlayNetworking.registerGlobalReceiver(CoinFlipPayload.TYPE, (payload, context) -> CoinFlipOverlay.start(payload));
		ClientTickEvents.END_CLIENT_TICK.register(client -> CoinFlipOverlay.tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CoinFlipOverlay.reset());
		HudElementRegistry.addLast(Burmaldaholic.id("last_chance_flip"), CoinFlipOverlay::extract);
	}
}

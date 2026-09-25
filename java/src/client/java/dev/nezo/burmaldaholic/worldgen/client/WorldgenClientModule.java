package dev.nezo.burmaldaholic.worldgen.client;

import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.worldgen.net.CasinoViewPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client half of the "worldgen" module: NPC renderers and the casino attract mode / arrival ({@link CasinoAttractFx}). */
public final class WorldgenClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "worldgen";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		NpcRenderers.register();
		GlowParticle.register();
		if (CasinoViewPayload.TYPE != null) {
			ClientPlayNetworking.registerGlobalReceiver(CasinoViewPayload.TYPE, (payload, context) -> CasinoAttractFx.onPayload(payload));
		}
		ClientTickEvents.END_CLIENT_TICK.register(CasinoAttractFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CasinoAttractFx.reset());
	}

	/** Client game-test hook: feeds a casino view as if the server had sent it; returns the casinos known (lane J-L3). */
	public static int previewAttract(CasinoViewPayload payload) {
		CasinoAttractFx.onPayload(payload);
		return CasinoAttractFx.nearCount();
	}
}

package dev.nezo.burmaldaholic.chaos.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.chaos.net.ChaosFxPayload;
import dev.nezo.burmaldaholic.client.fx.ClientFx;
import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.core.fx.ServerFx;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Client half of "chaos" (lane J-L3): chaos event intros and world decoration ({@link ChaosFx}), Golden Hour
 * vignette / motes / countdown / lounge loop ({@link GoldenHourFx}) and the flat summon-rune particle.
 */
public final class ChaosClientModule implements CasinoClientModule {
	@Override
	public String id() {
		return "chaos";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		RuneParticle.register();
		ClientFx.on(ServerFx.Kind.CHAOS, ChaosFx::onIntro);
		ClientFx.on(ServerFx.Kind.GOLDEN_HOUR_START, p -> GoldenHourFx.onStart(p.arg()));
		ClientFx.on(ServerFx.Kind.GOLDEN_HOUR_END, p -> GoldenHourFx.onEnd());
		if (ChaosFxPayload.TYPE != null) ClientPlayNetworking.registerGlobalReceiver(ChaosFxPayload.TYPE, (payload, context) -> ChaosFx.onPoints(payload));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			ChaosFx.tick(mc);
			GoldenHourFx.tick(mc);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ChaosFx.reset();
			GoldenHourFx.reset();
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Burmaldaholic.id("chaos_vignette"), (g, d) -> {
			GoldenHourFx.extractVignette(g, d);
			ChaosFx.extractVeil(g, d);
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.TITLE_AND_SUBTITLE, Burmaldaholic.id("chaos_card"), ChaosFx::extractCard);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Burmaldaholic.id("golden_hour_countdown"), GoldenHourFx::extractCountdown);
	}
}

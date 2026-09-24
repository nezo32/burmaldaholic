package dev.nezo.burmaldaholic.core.fx;

import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.network.FxPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Server side of the presentation core (lane J-L1): the {@code burmaldaholic:fx} payload, the installed
 * {@link ServerFx}, the big-win broadcast and the core particle types. Called once from {@code CoreModule.register}.
 */
public final class CoreFx {
	private static NetworkServerFx installed;

	private CoreFx() {}

	public static void register(ModuleContext ctx) {
		FxPayload.TYPE = ctx.payloads().clientbound("fx", FxPayload.CODEC);
		CoreParticles.register(ctx);
		installed = new NetworkServerFx();
		ServerFx.set(installed);
		ServerTickEvents.END_SERVER_TICK.register(installed::flush);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> installed.reset());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> installed.forget(handler.getPlayer().getUUID()));
		CasinoEvents.PLAY_RESOLVED.register(BigWinBroadcast::onResolved);
	}
}

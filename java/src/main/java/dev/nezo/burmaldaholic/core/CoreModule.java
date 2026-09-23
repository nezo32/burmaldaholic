package dev.nezo.burmaldaholic.core;

import dev.nezo.burmaldaholic.core.config.ConfigManager;
import dev.nezo.burmaldaholic.core.config.CoreConfig;
import dev.nezo.burmaldaholic.core.config.CoreConfigs;
import dev.nezo.burmaldaholic.core.economy.AttachmentEconomy;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.network.CasinoModeSyncPayload;
import dev.nezo.burmaldaholic.core.registry.CasinoCreativeTab;
import dev.nezo.burmaldaholic.core.rng.StreakTracker;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Core services. Owned by the J-core developer. Always the first module.
 * Features (chips item, HUD, commands...) are added here by J-core only.
 */
public final class CoreModule implements CasinoModule {
	@Override
	public String id() {
		return "core";
	}

	@Override
	public void register(ModuleContext ctx) {
		ConfigManager.init(FabricLoader.getInstance().getConfigDir().resolve("burmaldaholic.json"));
		CoreConfigs.bind(ctx.config(CoreConfig.class, CoreConfig::new));
		// Config is (re)loaded when a server starts, after all modules registered their sections.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> ConfigManager.get().load());

		CasinoMode.register();
		AttachmentEconomy.register();
		CasinoCreativeTab.register();
		TableNetworking.register(ctx);

		CasinoModeSyncPayload.TYPE = ctx.payloads().clientbound("casino_mode", CasinoModeSyncPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			sender.sendPacket(new CasinoModeSyncPayload(CasinoMode.isEnabled(server))));
		GameRuleEvents.changeCallback(CasinoMode.rule()).register((value, server) -> {
			for (var player : PlayerLookup.all(server)) {
				ServerPlayNetworking.send(player, new CasinoModeSyncPayload(value));
			}
		});

		CasinoEvents.PLAY_RESOLVED.register((player, result) ->
			StreakTracker.get().record(player.getUUID(), Long.signum(result.net())));
	}
}

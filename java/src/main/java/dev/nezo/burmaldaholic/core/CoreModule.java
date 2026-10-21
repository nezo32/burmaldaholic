package dev.nezo.burmaldaholic.core;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.command.CasinoCommands;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.ConfigManager;
import dev.nezo.burmaldaholic.core.data.CasinoWorldData;
import dev.nezo.burmaldaholic.core.data.PlayerRecord;
import dev.nezo.burmaldaholic.core.earnings.Earnings;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.events.PlayResults;
import dev.nezo.burmaldaholic.core.menu.CoreMenu;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.network.CasinoModeSyncPayload;
import dev.nezo.burmaldaholic.core.network.ConfigSyncPayload;
import dev.nezo.burmaldaholic.core.network.PlayerStatusPayload;
import dev.nezo.burmaldaholic.core.registry.CasinoCreativeTab;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.rng.StreakTracker;
import dev.nezo.burmaldaholic.core.table.TableLifecycle;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Inventories;
import dev.nezo.burmaldaholic.core.wager.HeartPenalties;
import java.util.random.RandomGeneratorFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Core services. Owned by the J-core developer. Always the first module. See
 * docs/architecture/java.md "Core API for feature devs".
 */
public final class CoreModule implements CasinoModule {
	private static volatile MinecraftServer server;

	@Override
	public String id() {
		return "core";
	}

	/** The running logical server (null in menus / on a remote client). */
	public static MinecraftServer server() {
		return server;
	}

	@Override
	public void register(ModuleContext ctx) {
		// ---- config: every CONFIG.md section, global file + per-world override ----
		ConfigManager.init(FabricLoader.getInstance().getConfigDir().resolve("burmaldaholic.json"));
		CasinoConfig.registerAll(ConfigManager.get());
		ServerLifecycleEvents.SERVER_STARTING.register(s -> {
			server = s;
			ConfigManager.get().setWorldFile(s.getWorldPath(LevelResource.DATA).resolve("burmaldaholic_config.json"));
			ConfigManager.get().load();
			long seed = CasinoConfig.debug().fixedSeed;
			if (seed != 0) {
				OddsService.set(new OddsService(RandomGeneratorFactory.of("L64X128MixRandom").create(seed)));
				Burmaldaholic.LOGGER.warn("debug.fixedSeed is set: casino RNG is deterministic (tests only)");
			}
			StreakTracker.bind(s);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			DEFAULT_ON_NOTIFIED.clear();
			StreakTracker.bind(null);
			ConfigManager.get().setWorldFile(null);
			ConfigManager.get().load();
		});
		ConfigSyncPayload.TYPE = ctx.payloads().clientbound("config_sync", ConfigSyncPayload.CODEC);
		ConfigManager.get().addListener(() -> {
			MinecraftServer s = server;
			if (s != null) {
				s.execute(() -> PlayerLookup.all(s).forEach(CoreModule::sendConfig));
			}
		});

		// ---- mode, content, economy, earnings, wagers ----
		CasinoMode.register();
		CoreContent.register(ctx);
		CoreSounds.register(ctx);
		dev.nezo.burmaldaholic.core.fx.CoreFx.register(ctx); // fx payload, ServerFx, big-win broadcast, particles (J-L1)
		CasinoCreativeTab.register();
		Earnings.register();
		HeartPenalties.register();
		TableNetworking.register(ctx);
		dev.nezo.burmaldaholic.core.cashier.counter.CounterSessions.register(ctx); // EC-1: counter payloads (cashier / store / comps)
		dev.nezo.burmaldaholic.core.store.StoreService.register(ctx); // EC-1: chip store, /casino store, merchant role
		TableLifecycle.register(); // review M1: tables play out on chunk unload / server stop
		CasinoCommands.register();
		OddsService.get().setStreakSource(StreakTracker::get, StreakTracker::settings);

		// ---- client sync ----
		CasinoModeSyncPayload.TYPE = ctx.payloads().clientbound("casino_mode", CasinoModeSyncPayload.CODEC);
		PlayerStatusPayload.TYPE = ctx.payloads().clientbound("player_status", PlayerStatusPayload.CODEC);
		PlayerSync.register();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			ServerPlayer player = handler.getPlayer();
			sender.sendPacket(new CasinoModeSyncPayload(CasinoMode.isEnabled(s)));
			sendConfig(player);
			welcome(player);
			defaultOnNotice(player);
		});
		CasinoMode.onChange((s, value) -> {
			for (ServerPlayer player : PlayerLookup.all(s)) {
				ServerPlayNetworking.send(player, new CasinoModeSyncPayload(value));
				player.sendSystemMessage(Component.translatable(value ? "msg.burmaldaholic.core.mode_enabled" : "msg.burmaldaholic.core.mode_disabled"));
				if (value) {
					welcome(player);
				}
			}
		});

		// ---- streak (§14): every settled wager with stake ≥ 1 (PvP too), then advancements (§19) ----
		CasinoEvents.PLAY_RESOLVED.register(StreakTracker::onPlayResolved); // skips bot rounds and PvP (C-3)
		PlayResults.register();
		CasinoAdvancements.register();
		dev.nezo.burmaldaholic.core.data.OfflineMail.register();
		dev.nezo.burmaldaholic.core.pvp.Pvp.register(); // PvP engine lifecycle
		dev.nezo.burmaldaholic.core.bots.Bots.register(); // bot job scheduler + ledger (skeleton)
		dev.nezo.burmaldaholic.core.economy.BankrollReferences.register(); // closed-bankroll tombstone pruning
		CoreMenu.register(ctx);
	}

	private static void sendConfig(ServerPlayer player) {
		if (ConfigSyncPayload.TYPE != null && ServerPlayNetworking.canSend(player, ConfigSyncPayload.TYPE)) {
			ServerPlayNetworking.send(player, new ConfigSyncPayload(ConfigManager.get().toJson().toString()));
		}
	}

	/** Operators told (once per server run) that this world got casino mode ON by default. */
	private static final java.util.Set<java.util.UUID> DEFAULT_ON_NOTIFIED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * A world that never chose a casino mode (dedicated server first start, a v0.1.1 world left OFF) is ON
	 * since v0.1.2 (GAME_DESIGN §2.1): tell operators — and the host of a singleplayer world without cheats, who may
	 * run {@code /casino mode off} too ({@link CasinoCommands#mayToggleMode}) — how to turn it off, once per server run.
	 */
	private static void defaultOnNotice(ServerPlayer player) {
		CasinoMode.Boot boot = CasinoMode.lastBoot();
		if ((boot != CasinoMode.Boot.LEGACY_UPGRADED && boot != CasinoMode.Boot.MISSING)
				|| !CasinoMode.isEnabled(player.level().getServer())
				|| !(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions()) || CasinoCommands.isIntegratedHost(player))
				|| !DEFAULT_ON_NOTIFIED.add(player.getUUID())) {
			return;
		}
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.mode_default_on"));
	}

	/** First join while casino mode is on: starting balance + Casino Card + welcome (§3.3, economy.startingBalance). */
	public static void welcome(ServerPlayer player) {
		MinecraftServer s = player.level().getServer();
		if (!CasinoMode.isEnabled(s)) {
			return;
		}
		CasinoWorldData data = CasinoWorldData.get(s);
		PlayerRecord rec = data.player(player.getUUID());
		if (rec.welcomed) {
			return;
		}
		rec.welcomed = true;
		data.setDirty();
		long start = CasinoConfig.economy().startingBalance;
		if (start > 0) {
			Economies.get().deposit(player, start, new Transaction("core", "starting_balance", Transaction.Kind.OTHER));
		}
		if (CasinoConfig.core().giveCasinoCardOnJoin) {
			Inventories.giveOrDrop(player, new ItemStack(CoreContent.CASINO_CARD));
		}
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.welcome", Texts.chipsAcc(start)));
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.welcome_hint"));
	}
}

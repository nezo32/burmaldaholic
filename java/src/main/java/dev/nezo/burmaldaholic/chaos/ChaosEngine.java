package dev.nezo.burmaldaholic.chaos;

import com.mojang.logging.LogUtils;
import dev.nezo.burmaldaholic.chaos.logic.ChaosEvent;
import dev.nezo.burmaldaholic.chaos.logic.ChaosRules;
import dev.nezo.burmaldaholic.chaos.logic.Safety;
import dev.nezo.burmaldaholic.chaos.logic.TriggerResult;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.ChaosConfig;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import org.slf4j.Logger;

/**
 * Chaos triggers (GAME_DESIGN.md §13.1): the request pipeline (enabled → cooldown → safety →
 * reroll → defer/skip → run), ambient rolls, big-win buffs, sunset rolls, deferred events and a
 * small tick scheduler used by the timed effects. Server thread only.
 */
public final class ChaosEngine {
	static final Logger LOG = LogUtils.getLogger();

	private record Pending(ChaosEvent event, String source, long since) {}

	private record Task(long tick, long seq, Runnable run) {}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static final Map<UUID, Long> RESPAWN_AT = new HashMap<>();
	private static final Map<UUID, Integer> AMBIENT_ACC = new HashMap<>();
	private static final PriorityQueue<Task> TASKS = new PriorityQueue<>((a, b) -> a.tick != b.tick ? Long.compare(a.tick, b.tick) : Long.compare(a.seq, b.seq));
	private static long taskSeq;
	private static long prevTimeOfDay = -1;

	private ChaosEngine() {}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ChaosEngine::tick);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				RESPAWN_AT.put(newPlayer.getUUID(), now(newPlayer.level().getServer()));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			PENDING.remove(id);
			RESPAWN_AT.remove(id);
			AMBIENT_ACC.remove(id);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING.clear();
			RESPAWN_AT.clear();
			AMBIENT_ACC.clear();
			TASKS.clear();
			prevTimeOfDay = -1;
		});
		CasinoEvents.PLAY_RESOLVED.register((player, result) -> {
			try {
				GoldenHour.onPlayResolved(player, result);
				onBigWin(player, result);
			} catch (RuntimeException e) {
				LOG.error("chaos: PLAY_RESOLVED handler failed", e);
			}
		});
	}

	// ---- config ------------------------------------------------------------------------------

	static ChaosConfig cfg() {
		return CasinoConfig.chaos();
	}

	/** Casino mode on and {@code chaos.enabled}. */
	static boolean enabled(MinecraftServer server) {
		return server != null && CasinoMode.isEnabled(server) && cfg().enabled;
	}

	static boolean eventEnabled(ChaosEvent e) {
		if (e == ChaosEvent.GOLDEN_HOUR && !cfg().goldenHour.enabled) {
			return false;
		}
		ChaosConfig.EventToggle t = cfg().event.get(e.id());
		return t == null || t.enabled;
	}

	/** Ambient weights with disabled events zeroed. */
	static Map<ChaosEvent, Integer> weights() {
		Map<ChaosEvent, Integer> w = new EnumMap<>(ChaosEvent.class);
		for (ChaosEvent e : ChaosEvent.values()) {
			w.put(e, eventEnabled(e) ? cfg().weight.getOrDefault(e.id(), e.defaultWeight()) : 0);
		}
		return w;
	}

	static CasinoRng rng() {
		return OddsService.get().fair();
	}

	/** World time used for cooldowns and Golden Hour (overworld game time, monotonic). */
	static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	// ---- public pipeline ---------------------------------------------------------------------

	static TriggerResult trigger(ServerPlayer player, ChaosEvent event, String source, boolean ignoreCooldown) {
		try {
			MinecraftServer server = player.level().getServer();
			if (!enabled(server) || player.isRemoved() || player.hasDisconnected()) {
				return TriggerResult.DISABLED;
			}
			if (event == ChaosEvent.GOLDEN_HOUR) {
				return GoldenHour.start(server, "slots".equals(source) ? player : null, source);
			}
			if (!eventEnabled(event)) {
				return TriggerResult.DISABLED;
			}
			ChaosData data = ChaosData.get(server);
			if (!ignoreCooldown && !ChaosRules.cooldownReady(data.lastEvent.get(player.getUUID()), now(server), cfg().playerCooldownTicks)) {
				return TriggerResult.COOLDOWN;
			}
			return attempt(player, event, source);
		} catch (RuntimeException e) {
			LOG.error("chaos: trigger {} failed", event.id(), e);
			return TriggerResult.SKIPPED;
		}
	}

	/** Progressive jackpot (§8.5, §13.1.4): diamond rain for the winner, chip shower within 16 blocks. */
	static void jackpot(ServerPlayer winner) {
		if (!enabled(winner.level().getServer())) {
			return;
		}
		trigger(winner, ChaosEvent.DIAMOND_RAIN, "jackpot", true);
		for (ServerPlayer p : winner.level().players()) {
			if (p != winner && p.distanceToSqr(winner) <= 16 * 16) {
				trigger(p, ChaosEvent.CHIP_SHOWER, "jackpot", true);
			}
		}
	}

	private static Safety.PlayerSnapshot snapshot(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		Long respawn = RESPAWN_AT.get(p.getUUID());
		long sinceRespawn = respawn == null ? -1 : Math.max(0, now(server) - respawn);
		return new Safety.PlayerSnapshot(p.isCreative() || p.isSpectator(), p.isDeadOrDying() || !p.isAlive(), sinceRespawn,
			p.isSleeping(), p.isFallFlying(), p.isPassenger(), p.fallDistance, ChaosWorld.casinoScreenOpen(p), ChaosWorld.inTableRound(p),
			ChaosWorld.nearBoss(p, cfg().bossSafeRadius), CoreServices.claims().isClaimed(p.level(), p.blockPosition()),
			p.level().getDifficulty() == Difficulty.PEACEFUL, ChaosWorld.dimension(p.level()).equals("overworld"));
	}

	private static TriggerResult attempt(ServerPlayer player, ChaosEvent requested, String source) {
		Safety.PlayerSnapshot snap = snapshot(player);
		int grace = cfg().respawnGraceTicks;
		ChaosEvent event = requested;
		Safety.Verdict v = Safety.evaluate(event, snap, grace);
		if (v.action() == Safety.Action.SKIP && (v.reason() == Safety.SkipReason.PEACEFUL || v.reason() == Safety.SkipReason.DIMENSION)) {
			event = ChaosRules.resolve(rng(), requested, snap.peaceful(), snap.overworld(), weights());
			if (event == null) {
				return TriggerResult.SKIPPED;
			}
			v = Safety.evaluate(event, snap, grace);
		}
		if (v.action() == Safety.Action.DEFER) {
			if (cfg().deferMaxTicks <= 0) {
				return TriggerResult.SKIPPED;
			}
			if (!PENDING.containsKey(player.getUUID())) {
				PENDING.put(player.getUUID(), new Pending(event, source, player.level().getServer().getTickCount()));
				player.sendOverlayMessage(Component.translatable("msg.burmaldaholic.chaos.deferred"));
			}
			return TriggerResult.DEFERRED;
		}
		if (v.action() == Safety.Action.SKIP) {
			return TriggerResult.SKIPPED;
		}
		if (!ChaosEffects.run(player, event, source)) {
			return TriggerResult.SKIPPED;
		}
		MinecraftServer server = player.level().getServer();
		ChaosData data = ChaosData.get(server);
		data.lastEvent.put(player.getUUID(), now(server));
		data.setDirty();
		LOG.info("chaos: {} for {} ({})", event.id(), player.getName().getString(), source);
		return TriggerResult.STARTED;
	}

	// ---- triggers ----------------------------------------------------------------------------

	/** §13.1.3: net ≥ multiple × stake and ≥ minChips → chance of lucky_buff (next tick, so specials go first). */
	private static void onBigWin(ServerPlayer player, CasinoEvents.PlayResult result) {
		MinecraftServer server = player.level().getServer();
		if (!enabled(server) || result.deferred()) {
			return;
		}
		ChaosConfig.BigWin bw = cfg().bigWin;
		if (ChaosRules.isBigWin(result.bet(), result.net(), bw.multiple, bw.minChips) && rng().nextDouble() < bw.buffChance) {
			UUID id = player.getUUID();
			schedule(server, 1, () -> {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null) {
					trigger(p, ChaosEvent.LUCKY_BUFF, "big_win", false);
				}
			});
		}
	}

	/** Runs {@code task} {@code delay} server ticks from now (lost on server stop). */
	static void schedule(MinecraftServer server, int delay, Runnable task) {
		TASKS.add(new Task(server.getTickCount() + Math.max(1, delay), taskSeq++, task));
	}

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		while (!TASKS.isEmpty() && TASKS.peek().tick <= tick) {
			try {
				TASKS.poll().run.run();
			} catch (RuntimeException e) {
				LOG.error("chaos: scheduled task failed", e);
			}
		}
		if (tick % 10 == 0) {
			tickPending(server);
		}
		if (tick % 20 == 0) {
			GoldenHour.tick(server);
			if (enabled(server)) {
				tickAmbient(server);
				tickSunset(server);
			}
		}
		if (tick % 100 == 0) {
			ChaosEffects.sweepWaves(server);
		}
	}

	private static void tickPending(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Pending> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			Pending pend = e.getValue();
			if (p == null || !enabled(server) || now - pend.since > cfg().deferMaxTicks) {
				it.remove();
				continue;
			}
			if (ChaosWorld.casinoScreenOpen(p)) {
				continue;
			}
			it.remove();
			ChaosEvent ev = pend.event;
			String src = pend.source;
			schedule(server, 1, () -> {
				try {
					attempt(p, ev, src);
				} catch (RuntimeException ex) {
					LOG.error("chaos: deferred {} failed", ev.id(), ex);
				}
			});
		}
	}

	private static void tickAmbient(MinecraftServer server) {
		int interval = cfg().ambientIntervalTicks;
		double chance = cfg().ambientChance;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			int acc = AMBIENT_ACC.getOrDefault(p.getUUID(), 0) + 20;
			if (acc < interval) {
				AMBIENT_ACC.put(p.getUUID(), acc);
				continue;
			}
			AMBIENT_ACC.put(p.getUUID(), 0);
			CasinoRng rng = rng();
			if (rng.nextDouble() >= chance) {
				continue;
			}
			ChaosEvent ev = ChaosRules.pick(rng, weights());
			if (ev != null) {
				trigger(p, ev, "ambient", false);
			}
		}
	}

	private static void tickSunset(MinecraftServer server) {
		long clock = server.overworld().getOverworldClockTime();
		long tod = Math.floorMod(clock, 24000L);
		long day = Math.floorDiv(clock, 24000L);
		ChaosData data = ChaosData.get(server);
		boolean due = ChaosRules.sunsetDue(prevTimeOfDay, tod, day, data.lastSunsetDay);
		prevTimeOfDay = tod;
		if (!due) {
			return;
		}
		data.lastSunsetDay = day;
		data.setDirty();
		if (server.getPlayerList().getPlayers().isEmpty()) {
			return;
		}
		if (rng().nextDouble() < cfg().goldenHour.sunsetChance) {
			GoldenHour.start(server, null, "sunset");
		}
	}
}

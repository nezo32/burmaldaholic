package dev.nezo.burmaldaholic.chaos;

import dev.nezo.burmaldaholic.chaos.logic.ChaosEvent;
import dev.nezo.burmaldaholic.chaos.logic.ChaosRules;
import dev.nezo.burmaldaholic.chaos.logic.GoldenHourState;
import dev.nezo.burmaldaholic.chaos.logic.TriggerResult;
import dev.nezo.burmaldaholic.core.bots.BotRounds;
import dev.nezo.burmaldaholic.core.config.sections.ChaosConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.text.Numbers;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;

/**
 * Golden Hour (GAME_DESIGN.md §13.3): server-wide ×{@code chaos.goldenHour.multiplier} on net
 * winnings of house-banked games (bonus paid by the bank, capped per player), its own cooldown,
 * start/end announcements (title + chat + bell) and a boss-bar timer. Implements core's
 * {@code GoldenHourProvider} (HUD line, gold balance).
 */
public final class GoldenHour {
	private static final UUID BAR_ID = UUID.nameUUIDFromBytes("burmaldaholic:golden_hour".getBytes(java.nio.charset.StandardCharsets.UTF_8));
	static final Transaction BONUS_TX = new Transaction("chaos", "golden_hour", Transaction.Kind.EARNING);

	/** Bell toll with subtitle {@code subtitles.burmaldaholic.golden_hour} (sounds/chaos/sounds.json). */
	static SoundEvent bell;
	private static ServerBossEvent bar;

	private GoldenHour() {}

	static void register(ModuleContext ctx) {
		bell = ctx.registry().sound("golden_hour");
		CoreServices.setGoldenHour(GoldenHour::remainingTicks);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> syncBar(server)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (bar != null) {
				bar.removePlayer(handler.getPlayer());
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			if (bar != null) {
				bar.removeAllPlayers();
				bar = null;
			}
		});
	}

	private static ChaosConfig.GoldenHour cfg() {
		return ChaosEngine.cfg().goldenHour;
	}

	private static GoldenHourState state(MinecraftServer server) {
		return ChaosData.get(server).goldenHour();
	}

	/** Remaining world ticks, 0 when inactive (core's {@code GoldenHourProvider}). */
	public static long remainingTicks(MinecraftServer server) {
		if (server == null) {
			return 0;
		}
		return state(server).remaining(ChaosEngine.now(server));
	}

	public static boolean isActive(MinecraftServer server) {
		return remainingTicks(server) > 0;
	}

	/** Enabled, not active and off cooldown. */
	public static boolean canStart(MinecraftServer server) {
		return ChaosEngine.enabled(server) && ChaosEngine.eventEnabled(ChaosEvent.GOLDEN_HOUR) && state(server).canStart(ChaosEngine.now(server));
	}

	/** Starts Golden Hour server-wide (respects its cooldown). {@code by}: the player who rang it in with three clocks. */
	static TriggerResult start(MinecraftServer server, ServerPlayer by, String source) {
		if (!ChaosEngine.enabled(server) || !ChaosEngine.eventEnabled(ChaosEvent.GOLDEN_HOUR)) {
			return TriggerResult.DISABLED;
		}
		long now = ChaosEngine.now(server);
		GoldenHourState st = state(server);
		if (!st.canStart(now)) {
			return TriggerResult.COOLDOWN;
		}
		int dur = cfg().durationTicks;
		st.start(now, dur, cfg().cooldownTicks);
		ChaosData.get(server).setDirty();
		Component mult = multiplier();
		Component subtitle = Component.translatable("msg.burmaldaholic.chaos.golden_hour.subtitle", mult, ChaosEffects.duration(dur));
		Component title = Component.translatable("msg.burmaldaholic.chaos.golden_hour.title").withStyle(net.minecraft.ChatFormatting.GOLD);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			ChaosEffects.title(p, title, subtitle, 10, 60, 20);
			toll(p);
		}
		if (by != null) {
			server.getPlayerList().broadcastSystemMessage(Component.translatable("msg.burmaldaholic.chaos.golden_hour.start_by", by.getDisplayName()), false);
		}
		server.getPlayerList().broadcastSystemMessage(Component.translatable("msg.burmaldaholic.chaos.golden_hour.start", mult, ChaosEffects.duration(dur))
			.withStyle(net.minecraft.ChatFormatting.GOLD), false);
		syncBar(server);
		ChaosEngine.LOG.info("chaos: golden hour #{} started ({})", st.id, source);
		return TriggerResult.STARTED;
	}

	/** Admin: ends the current Golden Hour now (cooldown counts from now). */
	static boolean stop(MinecraftServer server) {
		long now = ChaosEngine.now(server);
		GoldenHourState st = state(server);
		if (!st.isActive(now)) {
			return false;
		}
		st.stop(now, cfg().cooldownTicks);
		ChaosData.get(server).setDirty();
		tick(server);
		return true;
	}

	private static Component multiplier() {
		double m = cfg().multiplier;
		String text = m == Math.rint(m) ? Long.toString((long) m) : Double.toString(Math.round(m * 100) / 100.0);
		return Texts.decimal(text); // review m6: localized decimal separator
	}

	private static void toll(ServerPlayer p) {
		SoundEvent s = bell != null ? bell : net.minecraft.sounds.SoundEvents.BELL_BLOCK;
		p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(s), SoundSource.MASTER, p.getX(), p.getY(), p.getZ(),
			1.0F, 1.0F, p.getRandom().nextLong()));
	}

	/** Every second: boss bar, "ending soon" and "over" announcements. */
	static void tick(MinecraftServer server) {
		GoldenHourState st = state(server);
		long now = ChaosEngine.now(server);
		if (st.start < 0 || st.ended) {
			syncBar(server);
			return;
		}
		long remaining = st.remaining(now);
		if (!CasinoMode.isEnabled(server)) {
			// dormant mode: no announcements; the timer keeps running silently (§2.1)
			if (remaining <= 0 || remaining <= GoldenHourState.WARN_TICKS) {
				st.warned = true;
				st.ended = remaining <= 0;
				ChaosData.get(server).setDirty();
			}
			syncBar(server);
			return;
		}
		if (remaining <= 0) {
			st.ended = true;
			ChaosData.get(server).setDirty();
			server.getPlayerList().broadcastSystemMessage(Component.translatable("msg.burmaldaholic.chaos.golden_hour.end"), false);
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				toll(p);
			}
			syncBar(server);
			return;
		}
		if (!st.warned && remaining <= GoldenHourState.WARN_TICKS) {
			st.warned = true;
			ChaosData.get(server).setDirty();
			server.getPlayerList().broadcastSystemMessage(Component.translatable("msg.burmaldaholic.chaos.golden_hour.ending", ChaosEffects.duration(remaining)), false);
		}
		syncBar(server);
	}

	/** Shows / updates / hides the boss-bar timer for every online player. */
	static void syncBar(MinecraftServer server) {
		GoldenHourState st = state(server);
		long now = ChaosEngine.now(server);
		long remaining = st.remaining(now);
		if (remaining <= 0 || !CasinoMode.isEnabled(server)) {
			if (bar != null) {
				bar.removeAllPlayers();
				bar.setVisible(false);
			}
			return;
		}
		Component name = Component.translatable("hud.burmaldaholic.golden_hour", Texts.raw(Numbers.minutesSeconds(remaining)));
		if (bar == null) {
			bar = new ServerBossEvent(BAR_ID, name, BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		bar.setName(name);
		bar.setProgress((float) Math.max(0, Math.min(1, remaining / (double) Math.max(1, st.end - st.start))));
		bar.setVisible(true);
		for (ServerPlayer stale : java.util.List.copyOf(bar.getPlayers())) {
			if (server.getPlayerList().getPlayer(stale.getUUID()) != stale) {
				bar.removePlayer(stale); // disconnected, or replaced on respawn
			}
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!bar.getPlayers().contains(p)) {
				bar.addPlayer(p);
			}
		}
	}

	/**
	 * §13.3 bonus on every settled house-banked win while active (PvP rounds carry {@code houseBanked = false}).
	 * A round settled while the player was offline pays the bonus on join if it settled during a Golden Hour
	 * ({@code PlayResult#goldenHour}, stamped by core), against the current per-player cap.
	 */
	static void onPlayResolved(ServerPlayer player, CasinoEvents.PlayResult result) {
		MinecraftServer server = player.level().getServer();
		if (result.net() <= 0 || !CasinoMode.isEnabled(server) || !ChaosRules.goldenHourEligible(result.houseBanked())
				|| BotRounds.vsBots(result)) { // no Golden Hour on rounds against money bots (BOTS.md §5.3)
			return;
		}
		long now = ChaosEngine.now(server);
		GoldenHourState st = state(server);
		if (result.deferred() ? !result.goldenHour() : !st.isActive(now)) {
			return;
		}
		long cap = cfg().bonusCap;
		GoldenHourState.Bonus b = st.award(player.getUUID(), result.net(), cfg().multiplier, cap);
		if (b.amount() <= 0) {
			return;
		}
		ChaosData.get(server).setDirty();
		long got = Economies.get().deposit(player, b.amount(), BONUS_TX);
		if (got > 0) {
			player.sendOverlayMessage(Component.translatable("msg.burmaldaholic.chaos.golden_hour.bonus", Texts.chips(got))
				.withStyle(net.minecraft.ChatFormatting.GOLD));
		}
		if (b.capReached()) {
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.chaos.golden_hour.cap", Texts.chips(cap)));
		}
	}
}

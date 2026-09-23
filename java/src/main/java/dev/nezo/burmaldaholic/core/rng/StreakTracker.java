package dev.nezo.burmaldaholic.core.rng;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.StreakConfig;
import dev.nezo.burmaldaholic.core.data.CasinoWorldData;
import dev.nezo.burmaldaholic.core.data.PlayerRecord;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Persistent Lucky/Unlucky streak per player (GAME_DESIGN.md §14), stored in {@link CasinoWorldData}.
 * Core feeds it from {@code CasinoEvents.PLAY_RESOLVED}; decay is applied lazily from world time.
 */
public final class StreakTracker {
	private static volatile MinecraftServer server;

	private StreakTracker() {}

	/** Core only (server start/stop). */
	public static void bind(MinecraftServer s) {
		server = s;
	}

	public static StreakRules.Settings settings() {
		StreakConfig c = CasinoConfig.streak();
		return new StreakRules.Settings(c.enabled, c.max, c.luckyPerStep, c.pityPerStep, c.minHouseEdge, c.decayTicks);
	}

	/** Streak S of an online or offline player (0 when no server). */
	public static int get(UUID player) {
		MinecraftServer s = server;
		return s == null ? 0 : get(s, player);
	}

	public static int get(MinecraftServer s, UUID player) {
		CasinoWorldData data = CasinoWorldData.get(s);
		PlayerRecord r = data.player(player);
		StreakRules.Decayed d = StreakRules.decay(r.streak, r.streakAnchor, s.overworld().getGameTime(), CasinoConfig.streak().decayTicks);
		if (d.streak() != r.streak || d.anchor() != r.streakAnchor) {
			r.streak = d.streak();
			r.streakAnchor = d.anchor();
			data.setDirty();
		}
		return r.streak;
	}

	/** Records a settled wager (stake ≥ 1). Returns the new streak and sends the §14 chat messages. */
	public static int record(ServerPlayer player, long stake, long net) {
		MinecraftServer s = player.level().getServer();
		int old = get(s, player.getUUID());
		if (stake < 1) {
			return old;
		}
		int max = CasinoConfig.streak().max;
		int now = StreakRules.update(old, net, max);
		CasinoWorldData data = CasinoWorldData.get(s);
		PlayerRecord r = data.player(player.getUUID());
		r.streak = now;
		r.streakAnchor = s.overworld().getGameTime();
		data.setDirty();
		announce(player, old, now);
		return now;
	}

	/** Admin/tests. */
	public static void set(MinecraftServer s, UUID player, int value) {
		CasinoWorldData data = CasinoWorldData.get(s);
		PlayerRecord r = data.player(player);
		r.streak = value;
		r.streakAnchor = s.overworld().getGameTime();
		data.setDirty();
	}

	private static void announce(ServerPlayer player, int old, int now) {
		String key = null;
		if (old >= 3 && now < 0) {
			key = "msg.burmaldaholic.streak.broken_lucky";
		} else if (old <= -3 && now > 0) {
			key = "msg.burmaldaholic.streak.broken_unlucky";
		} else if (now != old) {
			key = switch (now) {
				case 5 -> "msg.burmaldaholic.streak.lucky_5";
				case 10 -> "msg.burmaldaholic.streak.lucky_10";
				case -5 -> "msg.burmaldaholic.streak.unlucky_5";
				case -10 -> "msg.burmaldaholic.streak.unlucky_10";
				default -> null;
			};
		}
		if (key != null) {
			player.sendSystemMessage(Component.translatable(key));
		}
	}
}

package dev.nezo.burmaldaholic.core;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.network.PlayerStatusPayload;
import dev.nezo.burmaldaholic.core.rng.StreakTracker;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Sends {@link PlayerStatusPayload} (balance, streak, VIP, Golden Hour, loan) to each client when it changes. */
public final class PlayerSync {
	private static final Map<UUID, PlayerStatusPayload> LAST = new HashMap<>();
	private static final Set<UUID> DIRTY = new HashSet<>();

	private PlayerSync() {}

	static void register() {
		CasinoEvents.BALANCE_CHANGED.register((player, before, after, reason) -> DIRTY.add(player.getUUID()));
		CasinoEvents.PLAY_RESOLVED.register((player, result) -> DIRTY.add(player.getUUID()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			LAST.remove(handler.getPlayer().getUUID());
			DIRTY.add(handler.getPlayer().getUUID());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.getPlayer().getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(PlayerSync::tick);
	}

	/** Forces a resend for this player at the end of the tick. */
	public static void markDirty(ServerPlayer player) {
		DIRTY.add(player.getUUID());
	}

	public static PlayerStatusPayload status(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		UUID id = player.getUUID();
		return new PlayerStatusPayload(
			Economies.get().balance(player),
			StreakTracker.get(server, id),
			CoreServices.vip().tier(server, id),
			CoreServices.goldenHour().remainingTicks(server),
			CoreServices.debt().owed(server, id),
			CoreServices.debt().inDefault(server, id),
			CoreServices.debt().ticksToDeadline(server, id));
	}

	private static void tick(MinecraftServer server) {
		boolean periodic = server.getTickCount() % 10 == 0;
		if (!periodic && DIRTY.isEmpty()) {
			return;
		}
		if (!CasinoMode.isEnabled(server)) {
			DIRTY.clear();
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!periodic && !DIRTY.contains(player.getUUID())) {
				continue;
			}
			if (PlayerStatusPayload.TYPE == null || !ServerPlayNetworking.canSend(player, PlayerStatusPayload.TYPE)) {
				continue;
			}
			PlayerStatusPayload now = status(player);
			PlayerStatusPayload last = LAST.get(player.getUUID());
			// Golden Hour / loan timers tick every time: only resend them every second.
			boolean changed = last == null || now.balance() != last.balance() || now.streak() != last.streak() || now.vipTier() != last.vipTier()
				|| now.debt() != last.debt() || now.inDefault() != last.inDefault() || (now.goldenHourTicks() > 0) != (last.goldenHourTicks() > 0)
				|| (server.getTickCount() % 20 == 0 && (now.goldenHourTicks() > 0 || now.debt() > 0));
			if (changed) {
				ServerPlayNetworking.send(player, now);
				LAST.put(player.getUUID(), now);
			}
		}
		DIRTY.clear();
	}
}

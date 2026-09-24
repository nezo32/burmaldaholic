package dev.nezo.burmaldaholic.core.events;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.data.CasinoWorldData;
import dev.nezo.burmaldaholic.core.data.PlayerRecord;
import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The one way to report a settled wager (GAME_DESIGN.md §4.1 "every settled wager updates ...").
 *
 * <ul>
 *   <li>Stamps {@link PlayResult#goldenHour()} (house-banked rounds settled during a Golden Hour).</li>
 *   <li>Online player: fires {@link CasinoEvents#PLAY_RESOLVED} now.</li>
 *   <li>Offline player (disconnected mid-round, the round was auto-completed): the result is stored in
 *       the world data and fired on the player's next join with {@link PlayResult#deferred()} = true,
 *       so streak, VIP wagered / cashback / contracts, Last Chance, statistics and advancements apply
 *       exactly once. The chips themselves were credited at settlement (offline-safe ledger; loan
 *       garnishment runs there too), so a deferred event never pays again.</li>
 * </ul>
 * Server thread only.
 */
public final class PlayResults {
	/** Offline mailbox cap per player (oldest dropped). */
	static final int MAX_PENDING = 512;

	private PlayResults() {}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> deliver(handler.getPlayer()));
	}

	/** Fires (online) or queues (offline) a settled round of {@code player}. */
	public static void fire(MinecraftServer server, UUID player, PlayResult result) {
		PlayResult r = result.houseBanked() && !result.goldenHour() && CoreServices.goldenHour().remainingTicks(server) > 0
			? result.withGoldenHour(true) : result;
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null && !online.hasDisconnected()) {
			invoke(online, r);
			return;
		}
		CasinoWorldData data = CasinoWorldData.get(server);
		List<CompoundTag> queue = data.player(player).pendingResults;
		queue.add(r.save());
		while (queue.size() > MAX_PENDING) {
			queue.removeFirst();
		}
		data.setDirty();
	}

	public static void fire(ServerPlayer player, PlayResult result) {
		fire(player.level().getServer(), player.getUUID(), result);
	}

	/** Fires every queued result of a player who just joined. */
	public static void deliver(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CasinoWorldData data = CasinoWorldData.get(server);
		PlayerRecord rec = data.player(player.getUUID());
		if (rec.pendingResults.isEmpty()) {
			return;
		}
		List<CompoundTag> queue = new ArrayList<>(rec.pendingResults);
		rec.pendingResults.clear();
		data.setDirty();
		for (CompoundTag tag : queue) {
			invoke(player, PlayResult.load(tag).asDeferred());
		}
	}

	/** Number of queued results (tests, admin). */
	public static int pending(MinecraftServer server, UUID player) {
		return CasinoWorldData.get(server).player(player).pendingResults.size();
	}

	private static void invoke(ServerPlayer player, PlayResult r) {
		try {
			CasinoEvents.PLAY_RESOLVED.invoker().onPlayResolved(player, r);
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.error("PLAY_RESOLVED listener failed for {} ({})", player.getName().getString(), r.gameId(), e);
		}
	}
}

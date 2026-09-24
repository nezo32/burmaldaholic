package dev.nezo.burmaldaholic.core.fx;

import dev.nezo.burmaldaholic.core.anim.RateBudget;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.TierWords;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.network.FxPayload;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The installed {@link ServerFx}: sends {@link FxPayload} to modded clients, vanilla titles to others
 * (global.md §3.1). Budgets (global §2.9): celebrations are never dropped (one per settlement by contract);
 * every other kind is limited to 4 payloads per second per player, at most 2 of them of one kind (budget per
 * component, {@link RateBudget}); server-wide toasts to 1 per 10 s with at
 * most 3 queued (beyond that the chat line alone tells it). Server thread only.
 */
public final class NetworkServerFx implements ServerFx {
	/** Radius of the "players nearby" rule (global §4.7). */
	public static final double NEARBY_RADIUS = 32;
	static final int PER_SECOND = 4;
	/** One kind (e.g. nearby wins in a busy casino) may use at most this many per second of a player's budget. */
	static final int PER_KIND_PER_SECOND = 2;
	static final int BROADCAST_GAP_TICKS = 200;
	static final int BROADCAST_QUEUE = 3;

	private final Map<UUID, RateBudget> recent = new HashMap<>();
	private final Deque<Pending> broadcasts = new ArrayDeque<>();
	private int lastBroadcastTick = Integer.MIN_VALUE / 2;

	private record Pending(UUID winner, WinTier tier, long net, String game, Component name) {}

	@Override
	public void celebrate(ServerPlayer player, Celebration c) {
		int flags = c.maxWin() ? FxPayload.FLAG_MAX_WIN : 0;
		int seed = SeedMix.mix(SeedMix.hash(c.game()), (int) c.ret(), player.level().getServer().getTickCount());
		FxPayload payload = new FxPayload(Kind.WIN, c.tier().ordinal(), c.ret(), c.stake(), c.game(), c.subTier(), flags,
			Math.max(0, c.holdUntilMs()), seed, Optional.empty(), Optional.of(player.getUUID()), Optional.empty(), Optional.empty());
		if (!send(player, payload, true) && c.tier().isOverlay()) {
			vanillaTitle(player, c.tier(), c.ret());
		}
		if (c.tier().isOverlay()) {
			nearby(player, player.position(), c.tier(), c.ret() - c.stake(), c.game());
		}
	}

	@Override
	public void event(ServerPlayer player, Kind kind, String game, int arg) {
		send(player, FxPayload.simple(kind, game, arg), false);
	}

	@Override
	public void nearby(ServerPlayer winner, Vec3 pos, WinTier tier, long net, String game) {
		if (!tier.isOverlay()) return;
		double r2 = NEARBY_RADIUS * NEARBY_RADIUS;
		int seed = SeedMix.mix(SeedMix.hash(game), (int) net);
		for (ServerPlayer other : winner.level().players()) {
			if (other == winner || other.distanceToSqr(pos) > r2) continue;
			send(other, new FxPayload(Kind.BIG_WIN_NEARBY, tier.ordinal(), net, 0, game, 0, 0, 0, seed, Optional.of(pos),
				Optional.of(winner.getUUID()), Optional.of(winner.getDisplayName()), Optional.empty()), false);
		}
	}

	@Override
	public void toast(ServerPlayer player, String titleKey, Component body, ToastIcon icon) {
		send(player, new FxPayload(Kind.TOAST, 0, 0, 0, titleKey, (icon == null ? ToastIcon.CHIP : icon).ordinal(), 0, 0, 0, Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.ofNullable(body)), false);
	}

	@Override
	public void broadcast(ServerPlayer winner, WinTier tier, long net, String game) {
		if (tier.ordinal() < WinTier.EPIC.ordinal()) return;
		MinecraftServer server = winner.level().getServer();
		if (broadcasts.size() >= BROADCAST_QUEUE) return; // chat only
		broadcasts.add(new Pending(winner.getUUID(), tier, net, game, winner.getDisplayName()));
		flush(server);
	}

	/** Sends the queued server-wide notices that are due (called every server tick by {@link CoreFx}). */
	void flush(MinecraftServer server) {
		int now = server.getTickCount();
		while (!broadcasts.isEmpty() && now - lastBroadcastTick >= BROADCAST_GAP_TICKS) {
			Pending p = broadcasts.poll();
			lastBroadcastTick = now;
			for (ServerPlayer other : server.getPlayerList().getPlayers()) {
				if (other.getUUID().equals(p.winner)) continue;
				send(other, new FxPayload(Kind.BROADCAST, p.tier.ordinal(), p.net, 0, p.game, 0, 0, 0, 0, Optional.empty(),
					Optional.of(p.winner), Optional.of(p.name), Optional.empty()), false);
			}
		}
	}

	/** Forgets per-player rate state (logout). */
	void forget(UUID player) {
		recent.remove(player);
	}

	/**
	 * Sends when the client understands the channel; returns false when it does not (caller falls back).
	 * Non-essential payloads beyond {@link #PER_SECOND} per second are dropped (returns true: nothing to fall back to).
	 */
	boolean send(ServerPlayer player, FxPayload payload, boolean essential) {
		if (FxPayload.TYPE == null || !ServerPlayNetworking.canSend(player, FxPayload.TYPE)) return false;
		if (!essential) {
			long nowMs = player.level().getServer().getTickCount() * 50L;
			RateBudget budget = recent.computeIfAbsent(player.getUUID(), k -> new RateBudget(PER_KIND_PER_SECOND, PER_SECOND, 1000));
			if (!budget.tryAcquire(payload.kind().name(), nowMs)) return true;
		}
		ServerPlayNetworking.send(player, payload);
		return true;
	}

	/** Vanilla fallback of an overlay tier: title = tier word, subtitle = +amount (global §2.6 Bedrock-style timings). */
	static void vanillaTitle(ServerPlayer player, WinTier tier, long ret) {
		int stay = switch (tier) {
			case BIG -> 36;
			case MEGA -> 44;
			case EPIC -> 52;
			default -> 60;
		};
		player.connection.send(new ClientboundSetTitlesAnimationPacket(3, stay, 10));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(TierWords.CORE.key(tier))));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("gui.burmaldaholic.fx.amount", Texts.number(ret))));
	}
}

package dev.nezo.burmaldaholic.vip;

import dev.nezo.burmaldaholic.core.fx.CoreParticles;
import dev.nezo.burmaldaholic.core.fx.ServerFx;
import dev.nezo.burmaldaholic.core.network.FxPayload;
import dev.nezo.burmaldaholic.vip.logic.VipRules;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * VIP presentation, server side (global.md §4.11, lane J-L3): the tier-up celebration through the shared {@code fx}
 * channel ({@code VIP_UP}: {@code tier} = the tier before the jump, {@code arg} = the new tier, {@code pos} /
 * {@code actor} = the promoted player) — the promoted player's client plays the overlay, spectators within 16 blocks
 * see a rising ring in the tier colour — plus the Netherite server-wide toast ({@link #netheriteToast}) and the win aura cosmetics.
 */
final class VipFx {
	static final double SPECTATORS = 16;

	private VipFx() {}

	private static boolean canSend(ServerPlayer p) {
		return FxPayload.TYPE != null && ServerPlayNetworking.canSend(p, FxPayload.TYPE);
	}

	/**
	 * Plays the tier-up for {@code player} ({@code fromTier} → {@code tier}); false when the player's client lacks
	 * the {@code fx} channel (the caller keeps the vanilla title / sound / particles).
	 */
	static boolean celebrate(ServerPlayer player, int fromTier, int tier) {
		if (!canSend(player)) {
			return false;
		}
		ServerLevel level = player.level();
		FxPayload payload = new FxPayload(ServerFx.Kind.VIP_UP, fromTier, 0, 0, "vip", tier, 0, 0, player.getId(), Optional.of(player.position()),
			Optional.of(player.getUUID()), Optional.of(player.getDisplayName()), Optional.empty());
		ServerPlayNetworking.send(player, payload);
		double r2 = SPECTATORS * SPECTATORS;
		for (ServerPlayer other : level.players()) {
			if (other != player && other.distanceToSqr(player) <= r2 && canSend(other)) {
				ServerPlayNetworking.send(other, payload);
			}
		}
		return true;
	}

	/**
	 * Netherite promotion: a casino toast for every other player (once per promotion; sent with the chat broadcast,
	 * so it honours {@code vip.announceNetherite} and does not depend on the promoted player's client).
	 */
	static void netheriteToast(ServerPlayer player) {
		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			if (other != player) {
				ServerFx.get().toast(other, "toast.burmaldaholic.vip_netherite.title", player.getDisplayName(), ServerFx.ToastIcon.VIP);
			}
		}
	}

	/** Win aura (§12 cosmetics): Gold+ a sparkle puff, Netherite soul fire + gold embers; ≤ 2 particle calls. */
	static void winAura(ServerPlayer player, int tier) {
		ServerLevel level = player.level();
		SimpleParticleType sparkle = CoreParticles.get("sparkle");
		SimpleParticleType gold = CoreParticles.get("gold_burst");
		if (tier >= VipRules.NETHERITE) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.3, player.getZ(), 12, 0.6, 0.1, 0.6, 0.01);
			if (gold != null) level.sendParticles(gold, player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.4, 0.5, 0.4, 0.04);
		} else if (tier >= VipRules.GOLD) {
			if (sparkle != null) level.sendParticles(sparkle, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.5, 0.5, 0.5, 0.03);
			else level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.5, 0.3, 0.5, 0.05);
		}
	}
}

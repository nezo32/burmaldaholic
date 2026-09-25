package dev.nezo.burmaldaholic.core.service;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Answers "which VIP tier is this player" (0 = Bronze … 5 = Netherite). Core ships a default that
 * always says 0; the vip module installs the real one with {@code CoreServices.setVip(...)}.
 * Core uses it for bet limits ({@code BetLimits}), the HUD badge and stake validation.
 */
@FunctionalInterface
public interface VipTierProvider {
	VipTierProvider DEFAULT = (server, player) -> VipTiers.BRONZE;

	int tier(MinecraftServer server, UUID player);

	/** Max bet for the player (tier max from config unless overridden). */
	default long maxBet(MinecraftServer server, UUID player) {
		return VipTiers.maxBet(tier(server, player));
	}
}

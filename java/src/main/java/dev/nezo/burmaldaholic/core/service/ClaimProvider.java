package dev.nezo.burmaldaholic.core.service;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Player-casino claims (GAME_DESIGN.md §18.2; multiplayer implements it, default: no claims). Chaos
 * keeps mob waves / random teleports out of claims (§13.4), loan keeps Debt Collectors from spawning
 * in other players' casinos (§5.8). Server thread only.
 */
public interface ClaimProvider {
	ClaimProvider NONE = new ClaimProvider() {
		@Override
		public boolean isClaimed(ServerLevel level, BlockPos pos) {
			return false;
		}

		@Override
		public Optional<UUID> ownerAt(ServerLevel level, BlockPos pos) {
			return Optional.empty();
		}
	};

	/** The block column lies inside a casino claim (full height). */
	boolean isClaimed(ServerLevel level, BlockPos pos);

	/** Owner of the claim containing the position. */
	Optional<UUID> ownerAt(ServerLevel level, BlockPos pos);
}

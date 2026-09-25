package dev.nezo.burmaldaholic.core.wager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * "May this player wager right now?" hook (GAME_DESIGN.md §5.6 Asset Freeze, owner rules §18.2 ...).
 * Registered with {@link Wagers#addVeto}; checked by {@link Wagers#check} for every new stake: chips
 * ({@link BetLimits#validate}, table {@code placeBet}), pawn stakes ({@link Stakes}) and PvP entry
 * points (poker buy-in, dice duels). Not checked for settling / doubling an already accepted round.
 */
@FunctionalInterface
public interface WagerVeto {
	/** @return the translated refusal, or null to allow */
	@Nullable Component check(ServerPlayer player, Context context);

	/**
	 * @param gameId game id of the stake
	 * @param kind   chips or a pawn kind
	 * @param table  table / machine position in the player's level (null = none, e.g. coin flip)
	 * @param pvp    PvP entry (poker buy-in, dice duel between players)
	 */
	record Context(String gameId, Stake.Kind kind, @Nullable BlockPos table, boolean pvp) {
		public static Context chips(String gameId) {
			return new Context(gameId, Stake.Kind.CHIPS, null, false);
		}

		public static Context at(String gameId, @Nullable BlockPos table) {
			return new Context(gameId, Stake.Kind.CHIPS, table, false);
		}

		public Context withKind(Stake.Kind k) {
			return new Context(gameId, k, table, pvp);
		}
	}
}

package dev.nezo.burmaldaholic.core.service;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Fixed settings of a generated casino table (GAME_DESIGN.md §16; worldgen implements it, default: none).
 * E.g. the Piglin Parlor poker table (Low stakes, 3 bots) and the High Roller Lounge blackjack / roulette
 * tables (min bet 100, 2 × tier max, Gold VIP) — same presets as Bedrock's worldgen {@code PRESETS}.
 */
@FunctionalInterface
public interface TablePresetProvider {
	TablePresetProvider NONE = (level, pos) -> Optional.empty();

	Optional<TablePreset> preset(ServerLevel level, BlockPos pos);

	/**
	 * @param id                preset id ("parlor_poker", "high_roller_blackjack", ...)
	 * @param pokerStakes       poker stake level id ("low" ...), "" = the players choose
	 * @param pokerBots         max poker bots (-1 = default fill)
	 * @param minBet            table minimum (0 = the game's default)
	 * @param tierMaxMultiplier max bet = multiplier × VIP tier max (0 = the game's default)
	 * @param minTier           VIP tier required to play (0 = none)
	 * @param pokerBotMix       poker bot Fish/Regular/Shark weights (empty = the stake level's {@code poker.botMix})
	 */
	record TablePreset(String id, String pokerStakes, int pokerBots, long minBet, double tierMaxMultiplier, int minTier,
			List<Integer> pokerBotMix) {
		/** "Regular-heavy" bot mix of the Piglin Parlor (§16.2; same weights as Bedrock). */
		public static final List<Integer> REGULAR_HEAVY = List.of(20, 70, 10);

		public TablePreset {
			pokerBotMix = List.copyOf(pokerBotMix);
		}

		public static TablePreset parlorPoker() {
			return new TablePreset("parlor_poker", "low", 3, 0, 0, 0, REGULAR_HEAVY);
		}

		public static TablePreset highRollerBlackjack() {
			return new TablePreset("high_roller_blackjack", "", -1, 100, 2, VipTiers.GOLD, List.of());
		}

		public static TablePreset highRollerRoulette() {
			return new TablePreset("high_roller_roulette", "", -1, 100, 0, 0, List.of());
		}
	}
}

package dev.nezo.burmaldaholic.core.service;

import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

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
	 * Seats &amp; Bots defaults of a generated casino table (J-G6, BOTS.md §2.3 / §7.1): the
	 * {@code bots.table.<game>.worldgen*} columns + the table's preset (e.g. the Piglin Parlor poker mix),
	 * never BOTS_ONLY. Empty for tables outside generated casinos (use {@code TableBots.defaultsFor(game)}).
	 * Games pass {@link BotPreset#defaults()} to {@code new TableBots(...)} and return
	 * {@link BotPreset#nameTheme()} / {@link BotPreset#levelMix()} from their {@code BotTable} hooks.
	 */
	default Optional<BotPreset> botDefaults(ServerLevel level, BlockPos pos, String gameId) {
		return Optional.empty();
	}

	/**
	 * @param defaults  table defaults (policy, count, difficulty, ...)
	 * @param nameTheme bot name pool of the casino (village any, Parlor piglin, End lounge ender)
	 * @param levelMix  fixed MIXED weights [easy, normal, hard]; null = the game's own mix
	 */
	record BotPreset(BotSettings defaults, BotRoster.Theme nameTheme, int @Nullable [] levelMix) {}

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

		/** End City lounge baccarat (§16.3): min 100 per coup, 2 × tier max, Gold VIP. */
		public static TablePreset highRollerBaccarat() {
			return new TablePreset("high_roller_baccarat", "", -1, 100, 2, VipTiers.GOLD, List.of());
		}

		/** End City lounge Ultimate Texas Hold'em (§16.3): min Ante 50, W ≤ 2 × tier max, Gold VIP. */
		public static TablePreset highRollerUth() {
			return new TablePreset("high_roller_uth", "", -1, 50, 2, VipTiers.GOLD, List.of());
		}
	}
}

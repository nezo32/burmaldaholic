package dev.nezo.burmaldaholic.worldgen.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Bot presets of worldgen-placed (house) tables: BOTS.md §2.3 / §3.3 / §7.1, pvp-bots.md §4.2 / §7.4
 * (J-G6). PURE. Bedrock twin: {@code bedrock/src/worldgen/logic/bots.ts} (B-G6).
 *
 * <ul>
 *   <li>table defaults = the {@code bots.table.<game>.worldgen*} columns (read by
 *       {@code TableBots.defaultsFor(game, true)}), then the casino/table preset overrides below;
 *       BOTS_ONLY is never a worldgen default (→ MIXED);</li>
 *   <li>Piglin Parlor poker: MIXED with the fixed Parlor mix [20, 70, 10] (Low stakes, 3 bots — §16.2); the
 *       count stays with {@code bots.table.poker.worldgenCount} (default 3) so admins can tune it;</li>
 *   <li>name theme per casino type: Lucky Villager {@code ANY}, Piglin Parlor {@code PIGLIN} (+ any),
 *       End City High Roller Lounge {@code ENDER} (+ any).</li>
 * </ul>
 */
public final class WorldgenBotPresets {
	/** Piglin Parlor poker preset mix (Easy / Normal / Hard %) — the old "Regular-heavy" mix. */
	public static final List<Integer> PARLOR_POKER_MIX = List.of(20, 70, 10);

	/** Name theme pool per casino type (BOTS.md §7.1). */
	public static final Map<CasinoKind, BotRoster.Theme> CASINO_NAME_THEME = Map.of(
		CasinoKind.VILLAGE_CASINO, BotRoster.Theme.ANY,
		CasinoKind.PIGLIN_PARLOR, BotRoster.Theme.PIGLIN,
		CasinoKind.HIGH_ROLLER, BotRoster.Theme.ENDER);

	/** Bot table block ids → game family ({@code bots.table.<game>}) of the tables worldgen places. */
	private static final Map<String, String> GAME_OF = Map.of(
		Layouts.POKER, "poker",
		Layouts.BLACKJACK, "blackjack",
		Layouts.BLACKJACK_HIGH_ROLLER, "blackjack",
		Layouts.ROULETTE, "roulette",
		Layouts.ROULETTE_HIGH_ROLLER, "roulette",
		Layouts.CRAPS, "craps",
		Layouts.BACCARAT, "baccarat",
		Layouts.BACCARAT_HIGH_ROLLER, "baccarat",
		Layouts.UTH, "uth",
		Layouts.UTH_HIGH_ROLLER, "uth");

	/**
	 * What a worldgen table adds to / overrides in the configured {@code worldgen*} table defaults.
	 *
	 * @param nameTheme  bot name pool ({@code BotTable#botNameTheme})
	 * @param levelMix   fixed MIXED weights [easy, normal, hard] ({@code BotTable#botDifficultyMix}); empty = the game's own mix
	 * @param policy     override of the configured worldgen policy (null = config)
	 * @param count      override of the configured worldgen count (null = config)
	 * @param difficulty override of the configured difficulty (null = config)
	 */
	public record Preset(BotRoster.Theme nameTheme, List<Integer> levelMix, @Nullable SeatPolicy policy, @Nullable Integer count,
			@Nullable BotDifficulty difficulty) {
		public Preset {
			nameTheme = nameTheme == null ? BotRoster.Theme.ANY : nameTheme;
			levelMix = levelMix == null ? List.of() : List.copyOf(levelMix);
		}

		public static Preset theme(BotRoster.Theme theme) {
			return new Preset(theme, List.of(), null, null, null);
		}

		/** {@link #levelMix} as the {@code int[]} {@code BotTable#botDifficultyMix} wants; null = the game's own mix. */
		public int @Nullable [] levelMixArray() {
			return levelMix.size() == 3 ? levelMix.stream().mapToInt(Integer::intValue).toArray() : null;
		}
	}

	private WorldgenBotPresets() {}

	/** The game family of a bot table block ({@code burmaldaholic:poker_table} → poker); empty = no bot seats. */
	public static Optional<String> gameOf(String blockId) {
		return Optional.ofNullable(blockId == null ? null : GAME_OF.get(blockId));
	}

	/** Name theme of a generated casino's bots. */
	public static BotRoster.Theme nameTheme(@Nullable CasinoKind kind) {
		return kind == null ? BotRoster.Theme.ANY : CASINO_NAME_THEME.getOrDefault(kind, BotRoster.Theme.ANY);
	}

	/**
	 * The bot preset of a worldgen table in a casino of {@code kind}.
	 *
	 * @param tablePresetId the table's {@link TablePresets} id, or null for a standard table
	 */
	public static Preset preset(@Nullable CasinoKind kind, @Nullable String tablePresetId) {
		BotRoster.Theme theme = nameTheme(kind);
		if (TablePresets.PARLOR_POKER.equals(tablePresetId)) {
			return new Preset(theme, PARLOR_POKER_MIX, null, null, BotDifficulty.MIXED);
		}
		return Preset.theme(theme);
	}

	/** The bot preset of the table block {@code blockId} in a casino of {@code kind} (preset id from {@link TablePresets}). */
	public static Preset presetFor(CasinoKind kind, String blockId) {
		return preset(kind, TablePresets.presetId(kind, blockId).orElse(null));
	}

	/** Configured worldgen defaults ({@code TableBots.defaultsFor(game, true)}) + the table preset. Never BOTS_ONLY. */
	public static BotSettings apply(BotSettings base, @Nullable Preset preset) {
		SeatPolicy policy = preset != null && preset.policy() != null ? preset.policy() : base.policy();
		if (policy == SeatPolicy.BOTS_ONLY) {
			policy = SeatPolicy.MIXED;
		}
		int count = Math.max(0, preset != null && preset.count() != null ? preset.count() : base.count());
		BotDifficulty difficulty = preset != null && preset.difficulty() != null ? preset.difficulty() : base.difficulty();
		return new BotSettings(policy, count, difficulty, base.keepFree(), base.chatter(), base.speed());
	}
}

package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.Optional;

/**
 * Which generated-casino preset a game table gets (PURE; GAME_DESIGN §16.2–16.3, same presets as
 * Bedrock's worldgen {@code PRESETS}): the Piglin Parlor poker table plays Low stakes with at most 3
 * Regular-heavy bots, the High Roller Lounge blackjack / roulette tables are High-Roller tables
 * (min 100, 2 × tier max, Gold VIP for blackjack), and so are its baccarat (min 100 per coup) and Ultimate
 * Texas Hold'em (min Ante 50) tables (2 × tier max, Gold VIP). Everything else keeps its game's defaults.
 *
 * <p>v0.2 (R-CB M11): the Parlor's baccarat table gets {@code parlor_baccarat} directly (the baccarat module's
 * neighbour search no longer reaches the v2 Parlor's other tables); the Lounge's craps table is a High-Roller table
 * ({@code high_roller_craps}: min 100, 2 × tier max, Gold VIP) and its poker table plays fixed Mid stakes
 * ({@code high_roller_poker}; Mid needs Gold VIP, the Lounge's tier).
 */
public final class TablePresets {
	public static final String PARLOR_POKER = "parlor_poker";
	public static final String HIGH_ROLLER_BLACKJACK = "high_roller_blackjack";
	public static final String HIGH_ROLLER_ROULETTE = "high_roller_roulette";
	public static final String HIGH_ROLLER_BACCARAT = "high_roller_baccarat";
	public static final String HIGH_ROLLER_UTH = "high_roller_uth";
	public static final String PARLOR_BACCARAT = "parlor_baccarat";
	public static final String HIGH_ROLLER_CRAPS = "high_roller_craps";
	public static final String HIGH_ROLLER_POKER = "high_roller_poker";

	private TablePresets() {}

	/**
	 * @param kind    the generated casino the table stands in
	 * @param blockId the table block's registry id ({@code burmaldaholic:poker_table} ...)
	 */
	public static Optional<String> presetId(CasinoKind kind, String blockId) {
		if (kind == null || blockId == null) {
			return Optional.empty();
		}
		return switch (kind) {
			case PIGLIN_PARLOR -> blockId.equals(Layouts.POKER) ? Optional.of(PARLOR_POKER)
				: blockId.equals(Layouts.BACCARAT) ? Optional.of(PARLOR_BACCARAT) : Optional.empty();
			case HIGH_ROLLER -> Optional.ofNullable(switch (blockId) {
				case Layouts.BLACKJACK, Layouts.BLACKJACK_HIGH_ROLLER -> HIGH_ROLLER_BLACKJACK;
				case Layouts.ROULETTE, Layouts.ROULETTE_HIGH_ROLLER -> HIGH_ROLLER_ROULETTE;
				case Layouts.BACCARAT, Layouts.BACCARAT_HIGH_ROLLER -> HIGH_ROLLER_BACCARAT;
				case Layouts.UTH, Layouts.UTH_HIGH_ROLLER -> HIGH_ROLLER_UTH;
				case Layouts.CRAPS -> HIGH_ROLLER_CRAPS;
				case Layouts.POKER -> HIGH_ROLLER_POKER;
				default -> null;
			});
			// Standalone casinos: VIP tables are their own *_high_roller block ids; no preset needed.
			case VILLAGE_CASINO, CASINO -> Optional.empty();
		};
	}
}

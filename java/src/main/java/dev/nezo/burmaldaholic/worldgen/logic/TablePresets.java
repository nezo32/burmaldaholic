package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.Optional;

/**
 * Which generated-casino preset a game table gets (PURE; GAME_DESIGN §16.2–16.3, same presets as
 * Bedrock's worldgen {@code PRESETS}): the Piglin Parlor poker table plays Low stakes with at most 3
 * Regular-heavy bots, the High Roller Lounge blackjack / roulette tables are High-Roller tables
 * (min 100, 2 × tier max, Gold VIP for blackjack). Everything else keeps its game's defaults.
 */
public final class TablePresets {
	public static final String PARLOR_POKER = "parlor_poker";
	public static final String HIGH_ROLLER_BLACKJACK = "high_roller_blackjack";
	public static final String HIGH_ROLLER_ROULETTE = "high_roller_roulette";

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
			case PIGLIN_PARLOR -> blockId.equals(Layouts.POKER) ? Optional.of(PARLOR_POKER) : Optional.empty();
			case HIGH_ROLLER -> blockId.equals(Layouts.BLACKJACK) || blockId.equals(Layouts.BLACKJACK_HIGH_ROLLER)
				? Optional.of(HIGH_ROLLER_BLACKJACK)
				: blockId.equals(Layouts.ROULETTE) || blockId.equals(Layouts.ROULETTE_HIGH_ROLLER)
					? Optional.of(HIGH_ROLLER_ROULETTE) : Optional.empty();
			case VILLAGE_CASINO -> Optional.empty();
		};
	}
}

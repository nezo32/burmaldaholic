package dev.nezo.burmaldaholic.client.fx;

import dev.nezo.burmaldaholic.client.fx.CelebrationRequest.TierStems;
import dev.nezo.burmaldaholic.core.anim.TierWords;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.WinTierTable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-game celebration styles on the client (lead decision L2: games pass their own tier words, threshold
 * table and stems). The server's {@code fx} payload carries only the game id; the game's client module
 * registers its style once, e.g. slots: {@code CelebrationStyles.register("slots", SlotTiers.TABLE,
 * SlotTiers.WORDS, slotStems, jackpotNameKeys)}. Unknown games use {@link #CORE}.
 */
public final class CelebrationStyles {
	/** Table, words, stems and jackpot names (lang keys by sub-tier 1…4, or {@code null}) of one game. */
	public record Style(WinTierTable table, TierWords words, TierStems stems, String[] jackpotNames) {}

	public static final Style CORE = new Style(WinTierTable.DEFAULT, TierWords.CORE, TierStems.CORE, null);

	private static final Map<String, Style> STYLES = new ConcurrentHashMap<>();

	private CelebrationStyles() {}

	public static void register(String game, WinTierTable table, TierWords words, TierStems stems) {
		register(game, table, words, stems, null);
	}

	public static void register(String game, WinTierTable table, TierWords words, TierStems stems, String[] jackpotNames) {
		STYLES.put(game, new Style(table, words, stems, jackpotNames));
	}

	public static Style of(String game) {
		return game == null ? CORE : STYLES.getOrDefault(game, CORE);
	}

	/** The request for a server-sent celebration of {@code game}. */
	public static CelebrationRequest request(String game, WinTier tier, long ret, long stake, int subTier, boolean maxWin, int seed) {
		Style s = of(game);
		return new CelebrationRequest(tier, ret, stake, s.table(), s.words(), s.stems(), subTier, maxWin, seed, s.jackpotNames());
	}
}

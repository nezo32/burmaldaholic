package dev.nezo.burmaldaholic.client.fx;

import dev.nezo.burmaldaholic.core.anim.TierWords;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.WinTierTable;

/**
 * Input of {@link CelebrationOverlay#play} (global.md §2.6, ⚠ CHANGED by the lead decision: the caller supplies
 * its tier words, threshold table and stems; docs/architecture/animation.md §4).
 *
 * <p>Word arguments: the RETURN and WIN words receive the amount as {@code %1$s} (keys without a placeholder
 * ignore it, and then the overlay draws a separate {@code +N} line); the JACKPOT word receives the jackpot's
 * name ({@code jackpotNames[subTier]}) as {@code %1$s}.
 *
 * @param tier         final tier (server-computed)
 * @param ret          total return shown by the roll-up (exact final frame)
 * @param stake        stake the multiples refer to
 * @param table        thresholds for the upgrade beats
 * @param words        lang keys per tier
 * @param stems        sound ids per tier (catalog ids)
 * @param subTier      jackpot sub-tier 0 none, 1 Mini … 4 Grand
 * @param maxWin       show the MAX WIN plate ({@code words.maxWin()})
 * @param seed         cosmetic seed (coin burst scatter)
 * @param jackpotNames lang keys of the jackpot names by sub-tier (index 1…4), or {@code null}
 */
public record CelebrationRequest(WinTier tier, long ret, long stake, WinTierTable table, TierWords words, TierStems stems,
		int subTier, boolean maxWin, int seed, String[] jackpotNames) {
	public CelebrationRequest(WinTier tier, long ret, long stake, WinTierTable table, TierWords words, TierStems stems, int subTier,
			boolean maxWin, int seed) {
		this(tier, ret, stake, table, words, stems, subTier, maxWin, seed, null);
	}

	/** Fanfare stem per tier (catalog sound ids; {@code null} = silent). */
	public record TierStems(String win, String nice, String big, String mega, String epic, String jackpot, String returned,
			String tick) {
		public static final TierStems CORE = new TierStems("win_small", "win_nice", "win_big", "win_mega", "win_mega", "jackpot",
			"push", "chip_count");

		public String of(WinTier t) {
			return switch (t) {
				case WIN -> win;
				case NICE -> nice;
				case BIG -> big;
				case MEGA -> mega;
				case EPIC -> epic;
				case JACKPOT -> jackpot;
				case RETURN -> returned;
				default -> null;
			};
		}
	}

	/** Lang key of this request's jackpot name, or {@code null}. */
	public String jackpotName() {
		return jackpotNames != null && subTier > 0 && subTier < jackpotNames.length ? jackpotNames[subTier] : null;
	}

	/** Default kit request for table/extras games. */
	public static CelebrationRequest core(WinTier tier, long ret, long stake, int seed) {
		return new CelebrationRequest(tier, ret, stake, WinTierTable.DEFAULT, TierWords.CORE, TierStems.CORE, 0, false, seed);
	}
}

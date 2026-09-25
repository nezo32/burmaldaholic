package dev.nezo.burmaldaholic.games.extras.logic;

import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.WinTierTable;

/**
 * Server-side win tiers of the four extras games (extras-pvp.md §0.4; global.md §2.4: the tier is computed by the
 * SERVER and sent with {@code ServerFx.celebrate}, clients never derive it). PURE.
 *
 * <ul>
 *   <li>Coin Flip: WIN (1.96×) or nothing;</li>
 *   <li>Wheel of Fortune: WIN for Double / Triple / Emerald, BIG and above only by the default thresholds;</li>
 *   <li>Plinko: the default thresholds, JACKPOT for an edge bin on High;</li>
 *   <li>Scratch Cards: the default thresholds against the card price, JACKPOT for the top prize.</li>
 * </ul>
 * Pawn and Soul stakes are not celebrated (their "return" is an item or a life, not chips).
 */
public final class ExtrasTiers {
	private ExtrasTiers() {}

	/**
	 * The tier of a settled extras round, or {@code null} when nothing is celebrated (a loss, a partial return, a push,
	 * or a non-chip stake).
	 *
	 * @param stake   chips at risk (the card price for Scratch Cards)
	 * @param ret     total return, stake included
	 * @param jackpot the game's top result (Plinko edge bin on High, the Scratch top prize)
	 * @param chips   the stake was chips (not a pawn / soul)
	 */
	public static WinTier celebrated(long stake, long ret, boolean jackpot, boolean chips) {
		if (!chips || stake <= 0 || ret <= stake) {
			return null;
		}
		WinTier tier = WinTier.of(ret, stake, WinTierTable.DEFAULT, jackpot, null);
		return tier.isWin() ? tier : null;
	}

	/** Plinko's jackpot rule: an edge bin (0 or the last) on High risk (global.md §2.4). */
	public static boolean plinkoJackpot(Plinko.Risk risk, int bin, int bins) {
		return risk == Plinko.Risk.HIGH && (bin == 0 || bin == bins - 1);
	}
}

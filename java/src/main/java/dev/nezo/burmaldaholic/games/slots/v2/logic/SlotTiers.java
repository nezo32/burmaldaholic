package dev.nezo.burmaldaholic.games.slots.v2.logic;

import dev.nezo.burmaldaholic.core.anim.TierWords;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.WinTierTable;

/** Slot tier table and words for the shared celebration API (SLOTS.md §10.1; lead decision, slots.md §0.3 D4). */
public final class SlotTiers {
	/** Nice / Big / Mega / Epic at 5 / 15 / 40 / 100 × ({@code slots.bigWinTiers}). */
	public static final WinTierTable TABLE = WinTierTable.SLOTS;

	public static final TierWords WORDS = new TierWords(
		"gui.burmaldaholic.slots.win",
		"gui.burmaldaholic.slots.tier.nice",
		"gui.burmaldaholic.slots.tier.big",
		"gui.burmaldaholic.slots.tier.mega",
		"gui.burmaldaholic.slots.tier.epic",
		"gui.burmaldaholic.slots.jackpot.won",
		"gui.burmaldaholic.slots.returned",
		"gui.burmaldaholic.slots.win",
		"gui.burmaldaholic.slots.win",
		"gui.burmaldaholic.slots.max_win");

	private SlotTiers() {}

	/**
	 * Tier of a whole spin (base + features, EXCLUDING progressive awards) from its total in fifths.
	 * Jackpots are celebrated separately after the spin roll-up (slots.md §4.11).
	 */
	public static WinTier of(long totalFifths, int[] bigWinTiers) {
		WinTierTable t = bigWinTiers == null ? TABLE : TABLE.withMultiples(bigWinTiers[0], bigWinTiers[1], bigWinTiers[2], bigWinTiers[3]);
		return WinTier.of(totalFifths, 5, t);
	}
}

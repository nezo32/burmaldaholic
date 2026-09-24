package dev.nezo.burmaldaholic.core.bots.logic;

/**
 * Owner / keeper limits of a table (BOTS.md §6.2).
 *
 * @param botsMode       owner's Bots control (unowned: ALLOWED)
 * @param hostMayChange  "Players may change seating"
 * @param maxBots        0 … seats − 1
 * @param allowPrivate   "Allow private tables"
 */
public record OwnerControls(BotsMode botsMode, boolean hostMayChange, int maxBots, boolean allowPrivate) {
	/** Unowned craftable / worldgen table. */
	public static OwnerControls unowned(int seats) {
		return new OwnerControls(BotsMode.ALLOWED, true, Math.max(0, seats - 1), true);
	}

	/** Owned table defaults (§6.2: Atmosphere only, private forbidden). */
	public static OwnerControls ownedDefaults(int seats) {
		return new OwnerControls(BotsMode.ATMOSPHERE, true, Math.max(0, seats - 1), false);
	}
}

package dev.nezo.burmaldaholic.core.pvp.logic;

/**
 * PvP win streak call-outs (PVP.md §3.8): thresholds {@code pvp.streakAnnounce} = [heating, rampage,
 * legendary]. Only matches with ≥ 1 human opponent count (a bot-only match neither extends nor breaks it).
 */
public final class WinStreaks {
	private WinStreaks() {}

	/** Tier reached exactly at {@code streak} (0 heating, 1 rampage, 2 legendary) or -1. */
	public static int announceTier(int streak, int[] thresholds) {
		for (int i = thresholds.length - 1; i >= 0; i--) {
			if (streak == thresholds[i]) {
				return i;
			}
		}
		return -1;
	}

	/** A broken streak is called out if it had reached the first threshold. */
	public static boolean brokenCallout(int previousStreak, int[] thresholds) {
		return thresholds.length > 0 && previousStreak >= thresholds[0];
	}
}

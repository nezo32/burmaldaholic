package dev.nezo.burmaldaholic.games.poker.logic;

import java.util.Locale;

/** Stake levels (GAME_DESIGN.md §7.1) with their minimum VIP tier: Bronze, Silver, Gold, Diamond. */
public enum StakeLevel {
	MICRO(0), LOW(1), MID(2), HIGH(4);

	private final int minTier;

	StakeLevel(int minTier) {
		this.minTier = minTier;
	}

	/** Minimum VIP tier index (0 = Bronze … 5 = Netherite). */
	public int minTier() {
		return minTier;
	}

	/** Config / lang id: micro, low, mid, high. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Null for unknown ids. */
	public static StakeLevel byId(String id) {
		for (StakeLevel s : values()) {
			if (s.id().equals(id)) {
				return s;
			}
		}
		return null;
	}

	/** SB = BB / 2 (floor, min 1) — CONFIG.md {@code poker.stakes.*.bb}. */
	public static long smallBlind(long bb) {
		return Math.max(1, bb / 2);
	}

	/**
	 * Buy-in range {min, max} in chips, clamped by the balance; max &lt; min = cannot sit. With
	 * {@code current > 0} it is a top-up: up to the max buy-in in total (§7.1).
	 */
	public static long[] buyInRange(long bb, int minBb, int maxBb, long balance, long current) {
		long lo = (long) Math.min(minBb, maxBb) * bb;
		long hi = (long) Math.max(minBb, maxBb) * bb;
		if (current > 0) {
			return new long[] {1, Math.min(balance, Math.max(0, hi - current))};
		}
		return new long[] {lo, Math.min(hi, balance)};
	}
}

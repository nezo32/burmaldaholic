package dev.nezo.burmaldaholic.core.wager;

/** Pure rules of non-chip stakes (GAME_DESIGN.md §4.3/§4.4). Unit-tested; shared with Bedrock. */
public final class PawnRules {
	/** Resulting max health may never drop below this (HP). */
	public static final double MIN_MAX_HEALTH = 10.0;

	private PawnRules() {}

	/** Vanilla XP points needed to go from {@code level} to {@code level + 1}. */
	public static int xpForNextLevel(int level) {
		if (level >= 30) {
			return 112 + (level - 30) * 9;
		}
		return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
	}

	/** Vanilla total XP points needed to reach {@code level} from 0. */
	public static long totalXpForLevel(int level) {
		long total = 0;
		for (int l = 0; l < level; l++) {
			total += xpForNextLevel(l);
		}
		return total;
	}

	/** Points between level {@code current − levels} and {@code current}. */
	public static long xpPoints(int current, int levels) {
		return totalXpForLevel(current) - totalXpForLevel(current - levels);
	}

	/** Stake value of {@code levels} XP levels: {@code floor(points / pointsPerChip)}. */
	public static long xpStakeValue(int current, int levels, int pointsPerChip) {
		return xpPoints(current, levels) / Math.max(1, pointsPerChip);
	}

	/** Valid XP stake: 1 ≤ L ≤ current level, L ≤ maxLevels. */
	public static boolean xpStakeAllowed(int current, int levels, int maxLevels) {
		return levels >= 1 && levels <= current && levels <= maxLevels;
	}

	/**
	 * Valid heart stake: 1 ≤ h ≤ maxPerBet, active + h ≤ maxTotal, and the resulting max health
	 * {@code currentMaxHealth − 2h} ≥ 10 HP.
	 */
	public static boolean heartStakeAllowed(int hearts, int activeHearts, int maxPerBet, int maxTotal, double currentMaxHealth) {
		return hearts >= 1 && hearts <= maxPerBet && activeHearts + hearts <= maxTotal
			&& currentMaxHealth - 2.0 * hearts >= MIN_MAX_HEALTH;
	}

	/** Soul Wager stake value: {@code max(minValue, balance)}. */
	public static long soulValue(long balance, long minValue) {
		return Math.max(minValue, balance);
	}
}

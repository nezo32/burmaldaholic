package dev.nezo.burmaldaholic.core.bots.logic;

/**
 * Per-table (or per-match) seating + bot settings (BOTS.md §2.2). Immutable; a change is saved as
 * "pending" and applied at the table's next safe point (§2.4).
 *
 * @param count    target bot seats: MIXED upper bound, BOTS_ONLY exact (≥ 1), ignored for HUMANS_ONLY
 * @param keepFree MIXED: leave one seat for walk-ins
 */
public record BotSettings(SeatPolicy policy, int count, BotDifficulty difficulty, boolean keepFree, boolean chatter, BotSpeed speed) {
	public static final BotSettings HUMANS_ONLY = new BotSettings(SeatPolicy.HUMANS_ONLY, 0, BotDifficulty.NORMAL, true, true, BotSpeed.NORMAL);

	public BotSettings {
		count = Math.max(0, count);
		if (policy == null) {
			policy = SeatPolicy.HUMANS_ONLY;
		}
		if (difficulty == null) {
			difficulty = BotDifficulty.NORMAL;
		}
		if (speed == null) {
			speed = BotSpeed.NORMAL;
		}
	}

	public BotSettings withPolicy(SeatPolicy p) {
		return new BotSettings(p, count, difficulty, keepFree, chatter, speed);
	}

	public BotSettings withCount(int c) {
		return new BotSettings(policy, c, difficulty, keepFree, chatter, speed);
	}

	public BotSettings withDifficulty(BotDifficulty d) {
		return new BotSettings(policy, count, d, keepFree, chatter, speed);
	}

	/** FAST/INSTANT only apply in BOTS_ONLY (other humans need time to follow the action). */
	public BotSpeed effectiveSpeed() {
		return policy == SeatPolicy.BOTS_ONLY ? speed : BotSpeed.NORMAL;
	}
}

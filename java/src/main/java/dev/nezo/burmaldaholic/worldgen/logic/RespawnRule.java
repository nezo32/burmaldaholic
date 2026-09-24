package dev.nezo.burmaldaholic.worldgen.logic;

/**
 * NPC respawn bookkeeping (PURE, same rule as Bedrock). A casino NPC that is not found (killed,
 * {@code /kill}ed, wandered off and unloaded) is respawned at its home once it has been missing for
 * {@code worldgen.loanSharkRespawnTicks} (1 MCD by default, GAME_DESIGN §5.1) — never sooner than
 * {@link #MIN_MISSING_TICKS}, which absorbs chunk/entity loading races.
 */
public final class RespawnRule {
	public static final long MIN_MISSING_TICKS = 200;
	/** "Not missing" marker for {@code missingSince}. */
	public static final long NONE = Long.MIN_VALUE;

	private RespawnRule() {}

	/** @param missingSince new value to store ({@link #NONE} = present), {@code respawn} = spawn now */
	public record Decision(long missingSince, boolean respawn) {}

	public static Decision decide(boolean present, long missingSince, long now, long respawnTicks) {
		if (present) {
			return new Decision(NONE, false);
		}
		if (missingSince == NONE) {
			return new Decision(now, false);
		}
		long due = missingSince + Math.max(respawnTicks, MIN_MISSING_TICKS);
		return now >= due ? new Decision(NONE, true) : new Decision(missingSince, false);
	}
}

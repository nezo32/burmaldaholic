package dev.nezo.burmaldaholic.bots.logic;

import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;

/** Conditions of the bots module's advancements (BOTS.md §10). Pure. */
public final class AdvancementRules {
	public static final String MEMBERS_ONLY = "members_only";
	public static final String NO_ROBOTS = "no_robots";
	public static final String WORD_GOT_AROUND = "word_got_around";
	public static final int NO_ROBOTS_HUMANS = 4;

	private AdvancementRules() {}

	/** A round at a private table while a player the settler invited is seated. */
	public static boolean membersOnly(boolean privateTable, boolean invitedPlayerSeated) {
		return privateTable && invitedPlayerSeated;
	}

	/** A round at a HUMANS_ONLY table with at least 4 humans seated (and no bot in a seat). */
	public static boolean noRobots(SeatPolicy policy, int humansSeated, int botsSeated) {
		return policy == SeatPolicy.HUMANS_ONLY && humansSeated >= NO_ROBOTS_HUMANS && botsSeated == 0;
	}

	/** Reached the "Word got around" heat stage (or beyond). */
	public static boolean wordGotAround(HeatStage stage) {
		return stage.ordinal() >= HeatStage.WORD_GOT_AROUND.ordinal();
	}
}

package dev.nezo.burmaldaholic.games.slots.client;

import net.minecraft.network.chat.Component;

/** What the {@link SlotStage} needs from the screen that hosts it (real machine screen or preview). */
public interface StageHost {
	/** The player pressed chest {@code chest} (Treasure Hunt): ask the server for the next entry (D6). */
	void pickChest(int chest);

	/** Centre (GUI px) of the jackpot meter of tier 1 Mini … 4 Grand, for gem / plate flights. */
	int[] meterCenter(int tier);

	/** Centre (GUI px) of the win panel amount (banner and coin flights land there). */
	int[] winPanelCenter();

	/** Centre (GUI px) of the feature panel counter (free-spin plates, hunt/hoard totals). */
	int[] featurePanelCenter();

	/** Step result line for the narrator (UI.md §13); may be ignored. */
	default void narrate(Component line) {}

	/** Interactive picks / wheel button (the real screen of the spinning player, the preview). */
	default boolean interactive() {
		return true;
	}

	/**
	 * The shared clock is the server's (the real machine screen): the server never waits for the wheel button, so the
	 * stage does not hold there; the Treasure Hunt pause is the server's too (it waits for the picks).
	 */
	default boolean serverPaced() {
		return false;
	}
}

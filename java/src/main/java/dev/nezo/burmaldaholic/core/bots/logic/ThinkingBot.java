package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Picks the bot for {@code BotTable#botThinking()} (global.md §4.12) when several bots wait on their own
 * decision moment (atmosphere bets, parallel UTH decisions): the one that acts next, i.e. the smallest due
 * tick still in the future; ties keep the map's (seat) order. Pure; timing only, never the hand.
 */
public final class ThinkingBot {
	private ThinkingBot() {}

	/**
	 * @param due bot key → game tick of its pending decision (a negative tick = none scheduled)
	 * @param now the current game tick
	 * @return the key of the bot deciding next, or null when none is pending
	 */
	public static @Nullable String next(Map<String, Long> due, long now) {
		String best = null;
		long bestAt = Long.MAX_VALUE;
		for (Map.Entry<String, Long> e : due.entrySet()) {
			Long at = e.getValue();
			if (at == null || at < 0 || at <= now) {
				continue;
			}
			if (at < bestAt) {
				bestAt = at;
				best = e.getKey();
			}
		}
		return best;
	}
}

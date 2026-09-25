package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Rate limits of bot quips (BOTS.md §7.4): chance per trigger, per-bot and per-table cooldowns;
 * explanatory triggers ({@code yield}, {@code sulk}, {@code duel_*}) always fire (queued ≤ 40 t, else dropped); {@code bots.chatter.maxPerMinute} is enforced by the caller's minute window.
 * One instance per table / match. Pure; time = world ticks.
 */
public final class ChatterLimiter {
	public static final Set<String> ALWAYS = Set.of("yield", "word_got_around", "sulk", "duel_accept", "duel_decline");

	public record Config(double chance, int botCooldownTicks, int tableCooldownTicks) {}

	private final Map<String, Long> lastByBot = new HashMap<>();
	private long lastTable = Long.MIN_VALUE / 2;

	/**
	 * @return ticks from now at which the line may be said (0 = now), or -1 = drop it.
	 */
	public long admit(String botKey, String trigger, long now, Config c, BotRng rng) {
		boolean always = ALWAYS.contains(trigger);
		if (!always) {
			if (!rng.chance(c.chance())) {
				return -1;
			}
			Long last = lastByBot.get(botKey);
			if (last != null && now - last < c.botCooldownTicks()) {
				return -1;
			}
		}
		long wait = Math.max(0, lastTable + c.tableCooldownTicks() - now);
		if (wait > 40 || (wait > 0 && !always)) {
			return -1;
		}
		lastByBot.put(botKey, now + wait);
		lastTable = now + wait;
		return wait;
	}
}

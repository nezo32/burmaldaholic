package dev.nezo.burmaldaholic.bots.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.ChatterLimiter;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Chatter rate limits of ONE table / PvP anchor (BOTS.md §7.4): core's {@link ChatterLimiter} (chance,
 * per-bot and per-table cooldowns, explanatory lines queued ≤ 40 t) plus the per-table
 * {@code bots.chatter.maxPerMinute} window. HARD bots talk at half the chance. Pure; time = world ticks.
 */
public final class ChatterGate {
	public static final long MINUTE = 1200;

	/** @param maxPerMinute ordinary lines per table per rolling minute (explanatory lines are not counted against it) */
	public record Config(double chance, int botCooldownTicks, int tableCooldownTicks, int maxPerMinute) {}

	private final ChatterLimiter limiter = new ChatterLimiter();
	private final Deque<Long> said = new ArrayDeque<>();

	/** @return ticks from now at which the line is said (0 = now), or -1 = dropped */
	public long admit(String botKey, BotDifficulty level, String event, long now, Config c, BotRng rng) {
		boolean always = ChatterLimiter.ALWAYS.contains(event);
		while (!said.isEmpty() && said.peekFirst() <= now - MINUTE) {
			said.removeFirst();
		}
		if (!always && said.size() >= c.maxPerMinute()) {
			return -1;
		}
		double chance = level == BotDifficulty.HARD ? c.chance() / 2 : c.chance();
		long wait = limiter.admit(botKey, event, now, new ChatterLimiter.Config(chance, c.botCooldownTicks(), c.tableCooldownTicks()), rng);
		if (wait >= 0 && !always) {
			said.addLast(now + wait);
		}
		return wait;
	}

	/** Lines counted in the current minute window (tests / debug). */
	public int inWindow(long now) {
		return (int) said.stream().filter(t -> t > now - MINUTE).count();
	}

	/**
	 * Does a player hear bot lines of this table? Server switch, table toggle, the player's own mute, and
	 * (unless seated) being within {@code radius} blocks.
	 */
	public static boolean hears(boolean serverOn, boolean tableOn, boolean muted, boolean seated, double distanceSq, int radius) {
		if (!serverOn || !tableOn || muted) {
			return false;
		}
		return seated || distanceSq <= (double) radius * radius;
	}
}

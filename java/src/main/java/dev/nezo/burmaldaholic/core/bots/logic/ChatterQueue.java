package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * One table's (or match's) bot quip queue (BOTS.md §7.4). Pure; time = world ticks. Applies, in order:
 * the switches (server / table), {@code bots.chatter.maxPerMinute} (a sliding 1 200-tick window; the
 * explanatory events of {@link ChatterLimiter#ALWAYS} are exempt), the chance (HARD bots: half), the
 * per-bot and per-table cooldowns ({@link ChatterLimiter}: explanatory lines wait ≤ 40 t for the table
 * cooldown, else they are dropped) and the variant draw — all from the BOT rng. {@link #due} hands out
 * the lines whose time has come.
 */
public final class ChatterQueue {
	public static final int MINUTE_TICKS = 1200;

	/** From {@code bots.chatter.*}; {@code enabled} = server switch AND the table's Chatter toggle. */
	public record Config(boolean enabled, double chance, int botCooldownTicks, int tableCooldownTicks, int maxPerMinute) {}

	/**
	 * A line to say.
	 *
	 * @param human name argument {@code %1$s} of the line (may be empty)
	 */
	public record Line(String botKey, String event, int variant, long dueTick, String human) {
		public String key() {
			return BotLines.key(event, variant);
		}
	}

	private final ChatterLimiter limiter = new ChatterLimiter();
	private final Deque<Long> said = new ArrayDeque<>();
	private final List<Line> pending = new ArrayList<>();

	/**
	 * Offers an event of a bot.
	 *
	 * @return the queued line, or null when it is dropped
	 */
	public Line offer(String botKey, BotDifficulty level, String event, String human, long now, Config c, BotRng rng) {
		int variants = BotLines.variants(event);
		if (!c.enabled() || variants <= 0) {
			return null;
		}
		boolean always = ChatterLimiter.ALWAYS.contains(event);
		prune(now);
		if (!always && said.size() >= c.maxPerMinute()) {
			return null;
		}
		double chance = level == BotDifficulty.HARD ? c.chance() / 2 : c.chance();
		long wait = limiter.admit(botKey, event, now, new ChatterLimiter.Config(chance, c.botCooldownTicks(), c.tableCooldownTicks()), rng);
		if (wait < 0) {
			return null;
		}
		Line line = new Line(botKey, event, rng.between(1, variants), now + wait, human == null ? "" : human);
		said.addLast(line.dueTick());
		pending.add(line);
		return line;
	}

	/** Lines due at {@code now} (removed from the queue), in order. */
	public List<Line> due(long now) {
		List<Line> out = new ArrayList<>();
		for (Iterator<Line> it = pending.iterator(); it.hasNext();) {
			Line l = it.next();
			if (l.dueTick() <= now) {
				out.add(l);
				it.remove();
			}
		}
		return out;
	}

	public boolean isEmpty() {
		return pending.isEmpty();
	}

	private void prune(long now) {
		while (!said.isEmpty() && said.peekFirst() <= now - MINUTE_TICKS) {
			said.pollFirst();
		}
	}
}

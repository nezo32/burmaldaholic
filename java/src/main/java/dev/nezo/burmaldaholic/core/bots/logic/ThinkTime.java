package dev.nezo.burmaldaholic.core.bots.logic;

/** Bot think delays (BOTS.md §7.3). Pure; draws from the BOT rng. */
public final class ThinkTime {
	/**
	 * @param minTicks   {@code bots.think.minTicks} (poker: {@code poker.botThinkMinTicks})
	 * @param maxTicks   {@code bots.think.maxTicks}
	 * @param fastFactor {@code bots.think.fastFactor}
	 * @param tankTicks  {@code bots.think.tankTicks} (HARD poker, decisions ≥ 50 % of stack)
	 */
	public record Config(int minTicks, int maxTicks, double fastFactor, int tankTicks) {}

	private ThinkTime() {}

	/**
	 * Delay before a bot's decision. {@code big} = a HARD poker decision ≥ 50 % of its stack (tank);
	 * {@code humanTimer} = the game's human action timer (a bot never uses more than half of it).
	 */
	public static int ticks(BotRng rng, Config c, BotProfile bot, BotSpeed speed, boolean big, int humanTimer) {
		if (speed == BotSpeed.INSTANT) {
			return 0;
		}
		int t = rng.between(Math.min(c.minTicks(), c.maxTicks()), Math.max(c.minTicks(), c.maxTicks()));
		if (bot.level() == BotDifficulty.HARD && big) {
			t += c.tankTicks() + rng.between(0, 40);
		}
		if (bot.level() == BotDifficulty.EASY && rng.chance(0.10)) {
			t += 20; // "misclick-hesitate"
		}
		if (speed == BotSpeed.FAST) {
			t = (int) Math.round(t * c.fastFactor());
		}
		if (humanTimer > 0) {
			t = Math.min(t, humanTimer / 2);
		}
		return Math.max(0, t);
	}
}

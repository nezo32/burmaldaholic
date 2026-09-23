package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.Size;
import dev.nezo.burmaldaholic.core.config.Validatable;

/** Config section `poker` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class PokerConfig implements Validatable {
	public boolean enabled = true;
	@Range(min = 2, max = 9) public int maxSeats = 6;
	public static final class Stakes {
		public static final class Micro {
			/** SB = BB/2 (floor, min 1). */
			@Range(min = 2, max = 1000000) public int bb = 2;
		}
		public Micro micro = new Micro();

		public static final class Low {
			@Range(min = 2, max = 1000000) public int bb = 10;
		}
		public Low low = new Low();

		public static final class Mid {
			@Range(min = 2, max = 1000000) public int bb = 50;
		}
		public Mid mid = new Mid();

		public static final class High {
			@Range(min = 2, max = 1000000) public int bb = 200;
		}
		public High high = new High();

	}
	public Stakes stakes = new Stakes();

	@Range(min = 10, max = 1000) public int minBuyInBb = 40;
	@Range(min = 10, max = 1000) public int maxBuyInBb = 100;
	@Range(min = 200, max = 2400) public int actionTimerTicks = 600;
	@Range(min = 1, max = 10) public int timeoutsToSitOut = 2;
	@Range(min = 1, max = 100) public int sitOutHandsToRemove = 3;
	@Range(min = 0, max = 0.1) public double rakePercent = 0.05;
	@Range(min = 0, max = 100) public int rakeCapBb = 3;
	public boolean rakeNoFlopNoDrop = true;
	public boolean botsEnabled = true;
	@Range(min = 20, max = 1000) public int botBuyInBb = 100;
	@Range(min = 0, max = 200) public int botThinkMinTicks = 20;
	@Range(min = 0, max = 400) public int botThinkMaxTicks = 60;
	public static final class Bot {
		/** Monte-Carlo iterations. */
		@Range(min = 50, max = 5000) public int regularSamples = 200;
		@Range(min = 50, max = 5000) public int sharkSamples = 500;
	}
	public Bot bot = new Bot();

	public static final class BotMix {
		/** Fish/Regular/Shark %. Normalized. */
		@Range(min = 0, max = 100) @Size(min = 3, max = 3) public int[] micro = {50, 40, 10};
		@Range(min = 0, max = 100) @Size(min = 3, max = 3) public int[] low = {50, 40, 10};
		@Range(min = 0, max = 100) @Size(min = 3, max = 3) public int[] mid = {30, 50, 20};
		@Range(min = 0, max = 100) @Size(min = 3, max = 3) public int[] high = {10, 50, 40};
	}
	public BotMix botMix = new BotMix();

	/** Blocks from table before sitting out. */
	@Range(min = 3, max = 32) public int maxDistance = 8;

	@Override
	public void validate(Issues issues) {
		if (maxBuyInBb < minBuyInBb) {
			issues.clamped("maxBuyInBb", maxBuyInBb, minBuyInBb);
			maxBuyInBb = minBuyInBb;
		}
		if (botThinkMaxTicks < botThinkMinTicks) {
			issues.clamped("botThinkMaxTicks", botThinkMaxTicks, botThinkMinTicks);
			botThinkMaxTicks = botThinkMinTicks;
		}
	}
}

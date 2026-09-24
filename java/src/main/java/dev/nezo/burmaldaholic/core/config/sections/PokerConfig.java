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
	/** Legacy alias of {@code bots.enabled} for poker: false forces poker tables to HUMANS_ONLY (BOTS.md §9.3). */
	public boolean botsEnabled = true;
	@Range(min = 20, max = 1000) public int botBuyInBb = 100;
	@Range(min = 0, max = 200) public int botThinkMinTicks = 20;
	@Range(min = 0, max = 400) public int botThinkMaxTicks = 60;
	public static final class Bot {
		/** Monte-Carlo samples of a NORMAL (Regular) bot decision (range-aware equity, BOTS.md §4.3). */
		@Range(min = 50, max = 5000) public int regularSamples = 300;
		/** Monte-Carlo samples of a HARD (Shark) bot decision. */
		@Range(min = 50, max = 5000) public int sharkSamples = 700;
	}
	public Bot bot = new Bot();

	public static final class BotMix {
		/** Easy/Normal/Hard (Fish/Regular/Shark) % for MIXED difficulty. Normalized; Easy is forced to 0 above {@code bots.poker.easyMaxStake}. */
		@Range(min = 0, max = 100) @Size(min = 3, max = 3) public int[] micro = {45, 45, 10};
		@Range(min = 0, max = 100) @Size(min = 3, max = 3) public int[] low = {35, 50, 15};
		@Range(min = 0, max = 100) @Size(min = 3, max = 3) public int[] mid = {10, 55, 35};
		@Range(min = 0, max = 100) @Size(min = 3, max = 3) public int[] high = {0, 45, 55};
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

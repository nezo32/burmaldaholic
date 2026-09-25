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

	/**
	 * All-in exposure: on an all-in run-out (betting closed, at most one live player with chips) every live hand is
	 * turned face up before the remaining streets (animation/cards.md §2.2; approved by the lead 2026-09-24).
	 */
	public boolean exposeAllIn = true;

	/** Presentation pacing in ticks (animation/cards.md §2.2, §8): publication only, never the game logic. */
	public static final class Fx {
		/** Ticks between hole-card deal beats. */
		@Range(min = 1, max = 10) public int dealBeatTicks = 3;
		/** Street-end gather of the bets into the pot. */
		@Range(min = 2, max = 40) public int gatherTicks = 8;
		/** Street start → the board cards slide in (at least gather + 4). */
		@Range(min = 2, max = 40) public int streetTicks = 12;
		/** Ticks between showdown show beats. */
		@Range(min = 2, max = 40) public int showBeatTicks = 10;
		/** Best five → the first pot award. */
		@Range(min = 2, max = 40) public int awardTicks = 12;
		/** Sweat pause before every all-in run-out street (the same for every street). */
		@Range(min = 0, max = 60) public int runoutPauseTicks = 24;
		/** Pot of at least this many big blinds: the big-pot moment. */
		@Range(min = 10, max = 1000) public int bigPotBb = 50;
		/** Pot of at least this many big blinds: the monster-pot moment. */
		@Range(min = 10, max = 1000) public int monsterPotBb = 100;
	}
	public Fx fx = new Fx();

	/** Blocks from table before sitting out. */
	@Range(min = 3, max = 32) public int maxDistance = 8;

	@Override
	public void validate(Issues issues) {
		if (maxBuyInBb < minBuyInBb) {
			issues.clamped("maxBuyInBb", maxBuyInBb, minBuyInBb);
			maxBuyInBb = minBuyInBb;
		}
		if (fx.monsterPotBb < fx.bigPotBb) {
			issues.clamped("fx.monsterPotBb", fx.monsterPotBb, fx.bigPotBb);
			fx.monsterPotBb = fx.bigPotBb;
		}
		if (botThinkMaxTicks < botThinkMinTicks) {
			issues.clamped("botThinkMaxTicks", botThinkMaxTicks, botThinkMinTicks);
			botThinkMaxTicks = botThinkMinTicks;
		}
	}
}

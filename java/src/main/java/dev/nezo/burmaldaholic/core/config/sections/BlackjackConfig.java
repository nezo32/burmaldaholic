package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;

/** Config section `blackjack` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class BlackjackConfig {
	public boolean enabled = true;
	@Range(min = 1, max = 8) public int decks = 6;
	/** Reshuffle point. */
	@Range(min = 0.25, max = 0.9) public double penetration = 0.75;
	public boolean dealerHitsSoft17 = false;
	/** 1.5 = 3:2, 1.2 = 6:5. */
	@Range(min = 1, max = 2) public double blackjackPayout = 1.5;
	public boolean doubleAfterSplit = true;
	/** Hands after splits. */
	@Range(min = 2, max = 4) public int maxHands = 4;
	public boolean resplitAces = false;
	public boolean insurance = true;
	/** Adds a Surrender button (lose half). */
	public boolean lateSurrender = false;
	/** Standard table. */
	@Range(min = 1, max = 1000000) public int minBet = 1;
	@Range(min = 1, max = 1000000) public int highRollerMinBet = 100;
	/** × tier max. */
	@Range(min = 1, max = 10) public double highRollerMaxMultiplier = 2.0;
	@Range(min = 1, max = 7) public int seats = 5;
	@Range(min = 100, max = 2400) public int betTimerTicks = 300;
	@Range(min = 100, max = 1200) public int insuranceTimerTicks = 200;
	@Range(min = 100, max = 2400) public int turnTimerTicks = 400;
}

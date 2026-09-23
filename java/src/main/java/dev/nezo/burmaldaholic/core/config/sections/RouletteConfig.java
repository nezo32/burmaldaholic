package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;

/** Config section `roulette` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class RouletteConfig {
	public boolean enabled = true;
	public boolean laPartage = false;
	/** Per individual bet. */
	@Range(min = 1, max = 1000000) public int minBet = 1;
	/** Inside bet ≤ tier max × this. */
	@Range(min = 0.01, max = 1) public double insideMaxFraction = 0.25;
	@Range(min = 1, max = 1000000) public int highRollerMinTotal = 100;
	@Range(min = 1, max = 10) public double highRollerMaxMultiplier = 2.0;
	@Range(min = 100, max = 2400) public int betTimerTicks = 500;
	@Range(min = 40, max = 300) public int spinTicks = 100;
	@Range(min = 1, max = 16) public int maxBettors = 8;
	@Range(min = 0, max = 50) public int historyLength = 12;
}

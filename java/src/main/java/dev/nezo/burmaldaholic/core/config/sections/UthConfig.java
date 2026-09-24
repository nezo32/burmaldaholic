package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Maps;
import dev.nezo.burmaldaholic.core.config.Range;
import java.util.Map;

/** Config section {@code uth} — keys, defaults and ranges from docs/design/CONFIG.md (§uth). */
public final class UthConfig {
	public boolean enabled = true;
	/** Player seats (the dealer seat is extra). */
	@Range(min = 1, max = 6) public int seats = 6;
	public boolean allow3x = true;
	@Range(min = 1, max = 1000000) public int minAnte = 1;
	@Range(min = 1, max = 1000000) public int highRollerMinAnte = 50;
	@Range(min = 1.0, max = 10.0) public double highRollerMaxMultiplier = 2.0;
	@Range(min = 0, max = 5) public int highRollerMinVipTier = 2;
	public boolean tripsEnabled = true;
	@Range(min = 0, max = 1000) public Map<String, Double> blindPays = Maps.of("royal", 500.0, "straightFlush", 50.0, "quads", 10.0,
		"fullHouse", 3.0, "flush", 1.5, "straight", 1.0);
	@Range(min = 0, max = 1000) public Map<String, Integer> tripsPays = Maps.of("royal", 50, "straightFlush", 40, "quads", 30,
		"fullHouse", 8, "flush", 6, "straight", 5, "trips", 3);
	public boolean validateEdge = true;
	@Range(min = 100, max = 2400) public int betTimerTicks = 300;
	@Range(min = 200, max = 2400) public int decisionTimerTicks = 400;
	public boolean autoPlayMadeHands = true;

	public static final class Pvp {
		public boolean enabled = true;
		@Range(min = 505, max = 1000000000) public int minBank = 1000;
		@Range(min = 0, max = 5) public int minBankerVip = 2;
		@Range(min = 0.0, max = 0.10) public double rakePercent = 0.01;
		@Range(min = 0, max = 1000) public int bankerRounds = 10;
		public boolean houseRoundsWhenNoBanker = true;
	}

	public Pvp pvp = new Pvp();
}

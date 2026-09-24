package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;

/** Config section {@code baccarat} — keys, defaults and ranges from docs/design/CONFIG.md (§baccarat). */
public final class BaccaratConfig {
	public boolean enabled = true;
	@Range(min = 1, max = 8) public int decks = 8;
	@Range(min = 0.25, max = 0.90) public double penetration = 0.80;
	public boolean burnCards = true;
	@Range(min = 0.0, max = 0.10) public double bankerCommission = 0.05;
	@Range(min = 8, max = 9) public int tiePays = 8;
	public boolean pairBets = true;
	@Range(min = 1, max = 12) public int pairPays = 11;
	@Range(min = 1, max = 1000000) public int minBet = 1;
	@Range(min = 0.01, max = 1.0) public double sideMaxFraction = 0.25;
	@Range(min = 1, max = 1000000) public int highRollerMinTotal = 100;
	@Range(min = 1.0, max = 10.0) public double highRollerMaxMultiplier = 2.0;
	@Range(min = 0, max = 5) public int highRollerMinVipTier = 2;
	@Range(min = 1, max = 7) public int seats = 7;
	@Range(min = 100, max = 2400) public int betTimerTicks = 400;
	@Range(min = 20, max = 300) public int revealTicks = 80;
	@Range(min = 0, max = 120) public int historyLength = 60;
	@Range(min = 0, max = 10) public int tieStreakChaos = 3;
	public Chemmy chemmy = new Chemmy();

	/** {@code baccarat.chemmy.*} — Chemin de fer (§20.9). */
	public static final class Chemmy {
		public boolean enabled = true;
		@Range(min = 1, max = 1000000000) public long minBank = 20;
		@Range(min = 0.0, max = 0.10) public double rakePercent = 0.05;
		@Range(min = 100, max = 1200) public int bankOfferTicks = 200;
		@Range(min = 100, max = 6000) public int idleTicks = 600;
		public boolean houseCoupWhenNoBanker = true;
	}
}

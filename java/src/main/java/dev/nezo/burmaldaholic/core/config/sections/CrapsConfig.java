package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;

/** Config section `craps` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class CrapsConfig {
	public boolean enabled = true;
	@Range(min = 1, max = 1000000) public int minBet = 1;
	/** X:1 on 2. */
	@Range(min = 1, max = 10) public int fieldPays2 = 2;
	/** X:1 on 12 (2 → HE 5.56 %). */
	@Range(min = 1, max = 10) public int fieldPays12 = 3;
	/** × flat bet. */
	@Range(min = 0, max = 100) public int maxOdds4_10 = 3;
	@Range(min = 0, max = 100) public int maxOdds5_9 = 4;
	@Range(min = 0, max = 100) public int maxOdds6_8 = 5;
	@Range(min = 40, max = 1200) public int betWindowTicks = 160;
	@Range(min = 100, max = 2400) public int rollTimerTicks = 400;
	@Range(min = 1, max = 8) public int seats = 6;
}

package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;

/** Config section `streak` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class StreakConfig {
	/** False: streak still tracked for HUD/advancements but no odds effect. */
	public boolean enabled = true;
	@Range(min = 1, max = 100) public int max = 10;
	@Range(min = 0, max = 0.1) public double luckyPerStep = 0.005;
	@Range(min = 0, max = 0.1) public double pityPerStep = 0.003;
	/** Hard floor (§14). Values < 0.005 are clamped to 0.005. */
	@Range(min = 0.005, max = 0.5) public double minHouseEdge = 0.01;
	/** 0 = no decay. */
	@Range(min = 0, max = 240000) public int decayTicks = 12000;
}

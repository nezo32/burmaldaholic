package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;

/** Config section `worldgen` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class WorldgenConfig {
	/** New chunks only. */
	public boolean enabled = true;
	public static final class VillageCasino {
		@Range(min = 0, max = 1) public double chance = 0.35;
	}
	public VillageCasino villageCasino = new VillageCasino();

	public static final class PiglinParlor {
		@Range(min = 0, max = 1) public double chance = 0.3;
	}
	public PiglinParlor piglinParlor = new PiglinParlor();

	public static final class HighRoller {
		@Range(min = 0, max = 1) public double chance = 0.2;
	}
	public HighRoller highRoller = new HighRoller();

	@Range(min = 0, max = 240000) public int loanSharkRespawnTicks = 24000;
}

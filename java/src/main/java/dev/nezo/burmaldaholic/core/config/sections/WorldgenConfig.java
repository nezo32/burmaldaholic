package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;

/** Config section `worldgen` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class WorldgenConfig {
	/** New chunks only. */
	public boolean enabled = true;
	public static final class VillageCasino {
		@Range(min = 0, max = 1) public double chance = 0.5;
	}
	public VillageCasino villageCasino = new VillageCasino();

	public static final class PiglinParlor {
		@Range(min = 0, max = 1) public double chance = 0.6;
	}
	public PiglinParlor piglinParlor = new PiglinParlor();

	public static final class HighRoller {
		@Range(min = 0, max = 1) public double chance = 0.5;
	}
	public HighRoller highRoller = new HighRoller();

	/** v0.2 standalone casinos (structure set {@code burmaldaholic:casinos}); annexes, Parlor and Lounge are not affected. New chunks only. */
	public static final class Standalone {
		public boolean enabled = true;
	}
	public Standalone standalone = new Standalone();

	@Range(min = 0, max = 240000) public int loanSharkRespawnTicks = 24000;

	/** {@code /casino locate}: seconds between searches of one non-operator player (0 = no cooldown). */
	@Range(min = 0, max = 3600) public int locateCooldownSeconds = 30;
}

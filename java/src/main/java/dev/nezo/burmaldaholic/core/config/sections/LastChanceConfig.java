package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.TranslatableEnum;
import java.util.Locale;

/** Config section `lastChance` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class LastChanceConfig {
	public boolean enabled = true;
	public static final class Chance {
		/** Also Peaceful. */
		@Range(min = 0, max = 1) public double easy = 0.6;
		@Range(min = 0, max = 1) public double normal = 0.5;
		@Range(min = 0, max = 1) public double hard = 0.4;
	}
	public Chance chance = new Chance();

	@Range(min = 0, max = 2400000) public int cooldownTicks = 24000;
	/** Of balance, on success. */
	@Range(min = 0, max = 100) public int costPercent = 10;
	public HardcoreMode hardcoreMode = HardcoreMode.DISABLED;
	public static final class Hardcore {
		@Range(min = 0, max = 1) public double chance = 0.5;
		@Range(min = 0, max = 2400000) public int cooldownTicks = 120000;
		/** Balance + carried chips needed. */
		@Range(min = 0, max = 1000000000) public int minStake = 100;
		/** Permanent max-health HP removed. */
		@Range(min = 1, max = 10) public int heartCost = 2;
		/** Not eligible below this max HP. */
		@Range(min = 2, max = 20) public int minMaxHealth = 8;
	}
	public Hardcore hardcore = new Hardcore();


	public enum HardcoreMode implements TranslatableEnum {
		DISABLED, HIGH_STAKES;

		@Override
		public String translationKey() {
			return "config.burmaldaholic.lastChance.hardcoreMode." + name().toLowerCase(Locale.ROOT);
		}
	}
}

package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Family;
import dev.nezo.burmaldaholic.core.config.Maps;
import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.Validatable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Config section `chaos` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class ChaosConfig implements Validatable {
	public boolean enabled = true;
	@Range(min = 600, max = 240000) public int ambientIntervalTicks = 6000;
	@Range(min = 0, max = 1) public double ambientChance = 0.08;
	@Range(min = 0, max = 240000) public int playerCooldownTicks = 3000;
	/** Ambient weight per event (§13.2); 0 disables it. */
	@Family(member = "gui.burmaldaholic.chaos.event.%s") @Range(min = 0, max = 1000)
	public Map<String, Integer> weight = Maps.of("chip_shower", 18, "lucky_buff", 20, "diamond_rain", 4, "xp_fountain", 12, "curse", 16, "mob_wave", 12, "random_teleport", 8, "weather_change", 8, "golden_hour", 2);

	/** Per-event switch; also disables the event for slot/wheel triggers. */
	@Family(member = "gui.burmaldaholic.chaos.event.%s")
	public Map<String, EventToggle> event = eventToggles();

	public static final class EventToggle {
		public boolean enabled = true;
	}

	private static Map<String, EventToggle> eventToggles() {
		Map<String, EventToggle> map = new LinkedHashMap<>();
		for (String id : List.of("chip_shower", "lucky_buff", "diamond_rain", "xp_fountain", "curse", "mob_wave", "random_teleport", "weather_change", "golden_hour")) {
			map.put(id, new EventToggle());
		}
		return map;
	}

	public static final class BigWin {
		@Range(min = 2, max = 10000) public int multiple = 50;
		@Range(min = 1, max = 1000000000) public int minChips = 500;
		@Range(min = 0, max = 1) public double buffChance = 0.3;
	}
	public BigWin bigWin = new BigWin();

	public static final class ChipShower {
		@Range(min = 0, max = 100000) public int min = 20;
		@Range(min = 0, max = 100000) public int max = 100;
	}
	public ChipShower chipShower = new ChipShower();

	public static final class DiamondRain {
		/** Hard/Hardcore −1. */
		@Range(min = 0, max = 64) public int min = 3;
		@Range(min = 0, max = 64) public int max = 6;
	}
	public DiamondRain diamondRain = new DiamondRain();

	public static final class XpFountain {
		@Range(min = 0, max = 10000) public int min = 50;
		@Range(min = 0, max = 10000) public int max = 150;
	}
	public XpFountain xpFountain = new XpFountain();

	public static final class Buff {
		@Range(min = 20, max = 72000) public int minTicks = 1200;
		@Range(min = 20, max = 72000) public int maxTicks = 3600;
	}
	public Buff buff = new Buff();

	public static final class Curse {
		@Range(min = 20, max = 72000) public int minTicks = 600;
		@Range(min = 20, max = 72000) public int maxTicks = 1800;
	}
	public Curse curse = new Curse();

	public static final class MobWave {
		@Range(min = 0, max = 20) public int easy = 3;
		@Range(min = 0, max = 20) public int normal = 4;
		@Range(min = 0, max = 20) public int hard = 6;
		@Range(min = 4, max = 64) public int minDistance = 8;
		@Range(min = 4, max = 64) public int maxDistance = 16;
		@Range(min = 200, max = 72000) public int despawnTicks = 6000;
	}
	public MobWave mobWave = new MobWave();

	public static final class Teleport {
		@Range(min = 8, max = 10000) public int minDistance = 32;
		@Range(min = 8, max = 10000) public int maxDistance = 256;
		@Range(min = 1, max = 64) public int attempts = 16;
	}
	public Teleport teleport = new Teleport();

	public static final class Weather {
		@Range(min = 600, max = 72000) public int durationTicks = 6000;
	}
	public Weather weather = new Weather();

	public static final class GoldenHour {
		public boolean enabled = true;
		/** Applied to net winnings. */
		@Range(min = 1, max = 10) public double multiplier = 2.0;
		@Range(min = 600, max = 24000) public int durationTicks = 3600;
		@Range(min = 0, max = 240000) public int cooldownTicks = 24000;
		@Range(min = 0, max = 1) public double sunsetChance = 0.1;
		/** Per player per event. */
		@Range(min = 0, max = 1000000000) public int bonusCap = 5000;
	}
	public GoldenHour goldenHour = new GoldenHour();

	@Range(min = 0, max = 2400) public int respawnGraceTicks = 200;
	/** Defer while a casino UI is open. */
	@Range(min = 0, max = 6000) public int deferMaxTicks = 600;
	@Range(min = 0, max = 256) public int bossSafeRadius = 64;

	@Override
	public void validate(Issues issues) {
		chipShower.max = atLeast(issues, "chipShower.max", chipShower.max, chipShower.min);
		diamondRain.max = atLeast(issues, "diamondRain.max", diamondRain.max, diamondRain.min);
		xpFountain.max = atLeast(issues, "xpFountain.max", xpFountain.max, xpFountain.min);
		buff.maxTicks = atLeast(issues, "buff.maxTicks", buff.maxTicks, buff.minTicks);
		curse.maxTicks = atLeast(issues, "curse.maxTicks", curse.maxTicks, curse.minTicks);
		mobWave.maxDistance = atLeast(issues, "mobWave.maxDistance", mobWave.maxDistance, mobWave.minDistance);
		teleport.maxDistance = atLeast(issues, "teleport.maxDistance", teleport.maxDistance, teleport.minDistance);
	}

	private static int atLeast(Issues issues, String key, int value, int min) {
		if (value < min) {
			issues.clamped(key, value, min);
			return min;
		}
		return value;
	}
}

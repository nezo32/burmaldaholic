package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Family;
import dev.nezo.burmaldaholic.core.config.Maps;
import dev.nezo.burmaldaholic.core.config.Range;
import java.util.Map;

/** Config section `wager` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class WagerConfig {
	/** Item/XP/heart stakes. */
	public boolean pawnEnabled = true;
	public static final class Items {
		public boolean enabled = true;
	}
	public Items items = new Items();

	public static final class Xp {
		public boolean enabled = true;
		@Range(min = 1, max = 100) public int maxLevels = 30;
		/** V = floor(points / this). */
		@Range(min = 1, max = 1000) public int pointsPerChip = 4;
	}
	public Xp xp = new Xp();

	public static final class Hearts {
		public boolean enabled = true;
		@Range(min = 1, max = 100000) public int valuePerHeart = 100;
		@Range(min = 1, max = 5) public int maxPerBet = 3;
		/** Max simultaneously lost hearts. */
		@Range(min = 1, max = 9) public int maxTotal = 5;
		@Range(min = 1200, max = 240000) public int durationTicks = 24000;
	}
	public Hearts hearts = new Hearts();

	/** Enables Soul Wager in Hardcore. */
	public boolean hardcoreSoulWager = false;
	public static final class Soul {
		@Range(min = 1, max = 1000000) public int minValue = 1000;
		@Range(min = 0, max = 2400000) public int cooldownTicks = 72000;
	}
	public Soul soul = new Soul();

	/** Item appraisal values in chips (§4.3.1), by item id; 0 removes the item from the list. */
	@Family(member = "item", open = true) @Range(min = 0, max = 100000)
	public Map<String, Integer> appraisal = Maps.of("minecraft:iron_ingot", 2, "minecraft:gold_ingot", 4, "minecraft:emerald", 8, "minecraft:emerald_block", 72, "minecraft:lapis_block", 15, "minecraft:golden_apple", 30, "minecraft:totem_of_undying", 150, "minecraft:nether_star", 400, "minecraft:elytra", 500, "minecraft:trident", 250, "minecraft:diamond", 20, "minecraft:diamond_block", 180, "minecraft:netherite_scrap", 40, "minecraft:netherite_ingot", 150, "minecraft:ancient_debris", 45, "minecraft:enchanted_golden_apple", 300, "minecraft:heart_of_the_sea", 200, "minecraft:echo_shard", 25);

}

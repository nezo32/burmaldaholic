package dev.nezo.burmaldaholic.core.earnings;

import dev.nezo.burmaldaholic.core.config.sections.EconomyConfig;
import java.util.ArrayDeque;
import java.util.Deque;

/** Pure earning rules (GAME_DESIGN.md §3.4), unit-tested. Ids are vanilla registry paths. */
public final class RewardRules {
	private RewardRules() {}

	/**
	 * Chips for breaking an ore block, before anti-exploit checks. {@code tags} tells which vanilla
	 * ore tag the block is in ({@code "coal_ores"}, …, or null). Nether gold is checked before gold.
	 */
	public static int ore(String blockId, String tag, EconomyConfig.Ore c) {
		switch (blockId) {
			case "nether_gold_ore":
				return c.netherGold;
			case "nether_quartz_ore":
				return c.netherQuartz;
			case "ancient_debris":
				return c.ancientDebris;
			case "gilded_blackstone":
				return 0;
			default:
				break;
		}
		if (tag == null) {
			return 0;
		}
		return switch (tag) {
			case "coal_ores" -> c.coal;
			case "copper_ores" -> c.copper;
			case "iron_ores" -> c.iron;
			case "gold_ores" -> c.gold;
			case "redstone_ores" -> c.redstone;
			case "lapis_ores" -> c.lapis;
			case "emerald_ores" -> c.emerald;
			case "diamond_ores" -> c.diamond;
			default -> 0;
		};
	}

	/** The vanilla ore tags checked by {@link #ore}, in order. */
	public static final String[] ORE_TAGS = {"coal_ores", "copper_ores", "iron_ores", "gold_ores", "redstone_ores", "lapis_ores", "emerald_ores", "diamond_ores"};

	/**
	 * Base chips for killing a mob (§3.4.2 table), before difficulty and diminishing returns.
	 * @param cubeSize     slime/magma cube size (only size ≥ 2 pays), else ignored
	 * @param hasTrident   drowned holding a trident (uncommon category)
	 * @param dragonKilled true if the dragon was killed before in this world
	 */
	public static int mob(String entityId, int cubeSize, boolean hasTrident, boolean dragonKilled, EconomyConfig.Mob c) {
		return switch (entityId) {
			case "zombie", "husk", "zombie_villager", "skeleton", "stray", "bogged", "spider", "cave_spider",
				"silverfish", "endermite", "vex", "zombified_piglin" -> c.common;
			// "drowned with trident" is in the uncommon category (3) but has no own key: uses pillager's value.
			case "drowned" -> hasTrident ? c.pillager : c.common;
			case "slime", "magma_cube" -> cubeSize >= 2 ? c.common : 0;
			case "creeper" -> c.creeper;
			case "phantom" -> c.phantom;
			case "pillager" -> c.pillager;
			case "hoglin", "zoglin" -> c.hoglin;
			case "piglin" -> c.piglin;
			case "enderman" -> c.enderman;
			case "blaze" -> c.blaze;
			case "guardian" -> c.guardian;
			case "witch" -> c.witch;
			case "vindicator" -> c.vindicator;
			case "wither_skeleton" -> c.witherSkeleton;
			case "creaking" -> c.creaking;
			case "ghast" -> c.ghast;
			case "breeze" -> c.breeze;
			case "shulker" -> c.shulker;
			case "piglin_brute" -> c.piglinBrute;
			case "evoker" -> c.evoker;
			case "ravager" -> c.ravager;
			case "elder_guardian" -> c.elderGuardian;
			case "warden" -> c.warden;
			case "wither" -> c.wither;
			case "ender_dragon" -> dragonKilled ? c.enderDragonRepeat : c.enderDragonFirst;
			default -> 0;
		};
	}

	/** Difficulty factor (§2.3): Hard/Hardcore × hardMultiplier, floor. */
	public static long applyDifficulty(long base, boolean hard, double hardMultiplier) {
		return hard ? (long) Math.floor(base * hardMultiplier) : base;
	}

	/**
	 * Diminishing returns: the {@code n}-th kill of this type within the window (1-based) pays
	 * 100 % up to {@code full}, {@code factor} up to {@code reduced}, 0 afterwards (floor).
	 */
	public static long diminished(long base, int n, int full, int reduced, double factor) {
		if (n <= full) {
			return base;
		}
		if (n <= reduced) {
			return (long) Math.floor(base * factor);
		}
		return 0;
	}

	/** Villager trade reward: {@code min(cap, base + perEmerald × emeralds)}. */
	public static long trade(int emeralds, EconomyConfig.Trade c) {
		return Math.max(0, Math.min(c.perTradeCap, c.perTradeBase + (long) c.perEmerald * emeralds));
	}

	/** Remaining daily trade allowance. */
	public static long tradeAllowance(long earnedToday, int dailyCap) {
		return Math.max(0, dailyCap - earnedToday);
	}

	/** Sliding window of kill times for one (player, mob type). */
	public static final class KillWindow {
		private final Deque<Long> ticks = new ArrayDeque<>();

		/** Records a kill at {@code now} and returns its 1-based index within the window. */
		public int record(long now, int windowTicks) {
			while (!ticks.isEmpty() && ticks.peekFirst() <= now - windowTicks) {
				ticks.removeFirst();
			}
			ticks.addLast(now);
			return ticks.size();
		}
	}
}

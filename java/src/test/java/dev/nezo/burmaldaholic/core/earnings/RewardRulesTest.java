package dev.nezo.burmaldaholic.core.earnings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nezo.burmaldaholic.core.config.sections.EconomyConfig;
import org.junit.jupiter.api.Test;

/** GAME_DESIGN.md §3.4 tables with default config. */
class RewardRulesTest {
	private final EconomyConfig c = new EconomyConfig();

	@Test
	void ores() {
		assertEquals(20, RewardRules.ore("deepslate_diamond_ore", "diamond_ores", c.ore));
		assertEquals(1, RewardRules.ore("nether_gold_ore", "gold_ores", c.ore), "nether gold before gold tag");
		assertEquals(4, RewardRules.ore("gold_ore", "gold_ores", c.ore));
		assertEquals(50, RewardRules.ore("ancient_debris", null, c.ore));
		assertEquals(0, RewardRules.ore("gilded_blackstone", null, c.ore));
		assertEquals(0, RewardRules.ore("stone", null, c.ore));
	}

	@Test
	void mobs() {
		assertEquals(2, RewardRules.mob("zombie", 0, false, false, c.mob));
		assertEquals(3, RewardRules.mob("drowned", 0, true, false, c.mob));
		assertEquals(0, RewardRules.mob("slime", 1, false, false, c.mob), "size-1 slimes pay nothing");
		assertEquals(2, RewardRules.mob("magma_cube", 2, false, false, c.mob));
		assertEquals(4, RewardRules.mob("zoglin", 0, false, false, c.mob));
		assertEquals(1000, RewardRules.mob("ender_dragon", 0, false, false, c.mob));
		assertEquals(200, RewardRules.mob("ender_dragon", 0, false, true, c.mob));
		assertEquals(0, RewardRules.mob("cow", 0, false, false, c.mob));
		assertEquals(6, RewardRules.applyDifficulty(5, true, 1.25), "floor(5 × 1.25)");
		assertEquals(5, RewardRules.applyDifficulty(5, false, 1.25));
	}

	@Test
	void diminishingReturnsWindow() {
		RewardRules.KillWindow w = new RewardRules.KillWindow();
		for (int i = 1; i <= 20; i++) {
			assertEquals(i, w.record(i, 6000));
		}
		assertEquals(4, RewardRules.diminished(4, 20, 20, 60, 0.25));
		assertEquals(1, RewardRules.diminished(4, 21, 20, 60, 0.25));
		assertEquals(0, RewardRules.diminished(2, 30, 20, 60, 0.25), "floor(2 × 0.25) = 0");
		assertEquals(0, RewardRules.diminished(4, 61, 20, 60, 0.25));
		assertEquals(1, w.record(10_000, 6000), "old kills fall out of the window");
	}

	@Test
	void trades() {
		assertEquals(4, RewardRules.trade(3, c.trade));
		assertEquals(10, RewardRules.trade(64, c.trade), "per-trade cap");
		assertEquals(5, RewardRules.tradeAllowance(195, 200));
		assertEquals(0, RewardRules.tradeAllowance(250, 200));
	}
}

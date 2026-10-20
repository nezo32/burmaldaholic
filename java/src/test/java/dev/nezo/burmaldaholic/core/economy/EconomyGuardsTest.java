package dev.nezo.burmaldaholic.core.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.config.sections.EconomyConfig;
import dev.nezo.burmaldaholic.core.config.sections.StoreConfig;
import dev.nezo.burmaldaholic.core.config.sections.WagerConfig;
import dev.nezo.burmaldaholic.core.store.StoreCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** EC-SPEC §2 R1–R6: the defaults pass, each rule catches a crafted violation, and no round trip ever wins. */
class EconomyGuardsTest {
	private static final int V = 11;

	@Test
	void defaultsPass() {
		EconomyConfig e = new EconomyConfig();
		assertEquals(List.of(), EconomyGuards.rows(e.guardRows()));
		assertEquals(List.of(), StoreCatalog.violations(new StoreConfig(), e, new WagerConfig().appraisal));
		EconomyConfig.ExchangeRow em = e.exchange.get("emerald");
		assertEquals(V, EconomyGuards.villagerValue(em.toChips, e.trade.perTradeBase, e.trade.perEmerald), "V = 9 + 1 + 1");
	}

	@Test
	void defaultRates() {
		EconomyConfig e = new EconomyConfig();
		assertEquals(9, e.exchange.get("emerald").toChips);
		assertEquals(10, e.exchange.get("emerald").fromChips);
		assertEquals(6, e.exchange.get("gold_ingot").toChips);
		assertEquals(8, e.exchange.get("gold_ingot").fromChips);
		assertEquals("N", e.exchange.get("gold_ingot").dims);
		assertEquals(4, e.exchange.get("netherite_scrap").buyMinTier, "Diamond VIP");
	}

	@Test
	void r1() {
		List<EconomyGuards.Violation> v = EconomyGuards.rows(List.of(new EconomyGuards.Row("x", "minecraft:emerald", 10, 10)));
		assertTrue(v.stream().anyMatch(x -> x.rule().equals("R1")));
		assertEquals(List.of(), EconomyGuards.rows(List.of(new EconomyGuards.Row("x", "minecraft:emerald", 10, 0))), "sell-only rows are fine");
	}

	@Test
	void r2() {
		// emerald 9 / 10; block bought out at 80 < 9 x 9 handed back in = 81 -> uncraft wins
		List<EconomyGuards.Row> rows = List.of(new EconomyGuards.Row("e", "minecraft:emerald", 9, 10),
			new EconomyGuards.Row("b", "minecraft:emerald_block", 70, 80));
		assertTrue(EconomyGuards.rows(rows).stream().anyMatch(x -> x.rule().equals("R2") && x.id().equals("b")));
		// block handed in at 95 > 9 x 10 bought out = 90 -> craft wins
		rows = List.of(new EconomyGuards.Row("e", "minecraft:emerald", 9, 10), new EconomyGuards.Row("b", "minecraft:emerald_block", 95, 100));
		assertTrue(EconomyGuards.rows(rows).stream().anyMatch(x -> x.rule().equals("R2") && x.id().equals("e")));
	}

	@Test
	void r3ToR6() {
		List<EconomyGuards.Row> rows = new EconomyConfig().guardRows();
		Map<String, Integer> appraisal = Map.of("minecraft:totem_of_undying", 150);
		assertRule("R3", new EconomyGuards.Offer("t", "minecraft:totem_of_undying", 1, 150), appraisal, rows);
		assertRule("R4", new EconomyGuards.Offer("p", "minecraft:potion", 1, 1), appraisal, rows);
		assertRule("R4", new EconomyGuards.Offer("w", "minecraft:wheat", 1, 0), appraisal, rows);
		assertRule("R5", new EconomyGuards.Offer("s", "minecraft:copper_ingot", 1, 3),
			appraisal, List.of(new EconomyGuards.Row("c", "minecraft:copper_ingot", 3, 5)));
		for (String bad : List.of("minecraft:emerald", "minecraft:gold_nugget", "minecraft:raw_iron", "minecraft:iron_pickaxe", "minecraft:netherite_ingot",
			"minecraft:zombie_spawn_egg", "minecraft:spawner", "minecraft:enchanted_golden_apple", "minecraft:nether_star", "minecraft:beacon",
			"minecraft:red_shulker_box", "minecraft:dragon_egg", "minecraft:netherite_upgrade_smithing_template")) {
			assertRule("R6", new EconomyGuards.Offer("f", bad, 1, 1_000_000), appraisal, rows);
		}
	}

	private static void assertRule(String rule, EconomyGuards.Offer o, Map<String, Integer> appraisal, List<EconomyGuards.Row> rows) {
		List<EconomyGuards.Violation> v = EconomyGuards.offers(List.of(o), appraisal, rows, V);
		assertTrue(v.stream().anyMatch(x -> x.rule().equals(rule)), rule + " for " + o + ": " + v);
	}

	/** Randomised rows that pass R1/R2: every in/out round trip (also through crafting) loses chips. */
	@Test
	void everyRoundTripLoses() {
		Random r = new Random(42);
		int checked = 0;
		for (int i = 0; i < 20_000; i++) {
			int eIn = 1 + r.nextInt(50), eOut = 1 + r.nextInt(60), bIn = 1 + r.nextInt(500), bOut = 1 + r.nextInt(600);
			List<EconomyGuards.Row> rows = List.of(new EconomyGuards.Row("e", "minecraft:emerald", eIn, eOut),
				new EconomyGuards.Row("b", "minecraft:emerald_block", bIn, bOut));
			if (!EconomyGuards.rows(rows).isEmpty()) continue;
			checked++;
			List<Long> trips = new ArrayList<>();
			trips.add((long) eIn - eOut); // buy an emerald, hand it back
			trips.add((long) bIn - bOut);
			trips.add(9L * eIn - bOut); // buy a block, uncraft, hand in 9
			trips.add(bIn - 9L * eOut); // buy 9 emeralds, craft, hand in the block
			assertTrue(trips.get(0) < 0 && trips.get(1) < 0, "a direct round trip must lose: " + rows);
			for (long t : trips) assertTrue(t <= 0, "round trip wins " + t + " at " + rows);
		}
		assertTrue(checked > 100, "enough valid samples: " + checked);
	}
}

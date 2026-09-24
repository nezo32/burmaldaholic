package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CasinoLootTest {
	@Test
	void specTables() {
		assertEquals(4, CasinoLoot.Table.VILLAGE_CASINO.minRolls());
		assertEquals(7, CasinoLoot.Table.VILLAGE_CASINO.maxRolls());
		assertEquals(5, CasinoLoot.Table.PIGLIN_PARLOR.minRolls());
		assertEquals(8, CasinoLoot.Table.PIGLIN_PARLOR.maxRolls());
		assertEquals(3, CasinoLoot.Table.HIGH_ROLLER.minRolls());
		assertEquals(5, CasinoLoot.Table.HIGH_ROLLER.maxRolls());
		assertEquals(30 + 25 + 12 + 15 + 12 + 8 + 5 + 3,
			CasinoLoot.Table.VILLAGE_CASINO.entries().stream().mapToInt(CasinoLoot.Entry::weight).sum());
		assertEquals("burmaldaholic:chests/piglin_parlor", CasinoLoot.Table.PIGLIN_PARLOR.lootTableId());
	}

	@Test
	void rollCountsAndStackSizesStayInRange() {
		Random r = new Random(7);
		for (CasinoLoot.Table t : CasinoLoot.Table.values()) {
			Set<Integer> rollCounts = new HashSet<>();
			for (int i = 0; i < 5_000; i++) {
				List<CasinoLoot.Stack> stacks = CasinoLoot.roll(t, r::nextInt, id -> true);
				assertTrue(stacks.size() >= t.minRolls() && stacks.size() <= t.maxRolls());
				rollCounts.add(stacks.size());
				for (CasinoLoot.Stack s : stacks) {
					CasinoLoot.Entry e = t.entries().stream().filter(en -> en.item().equals(s.item())).findFirst().orElseThrow();
					assertTrue(s.count() >= e.min() && s.count() <= e.max(), s.toString());
					assertEquals(e.enchant(), s.enchant());
				}
			}
			assertEquals(t.maxRolls() - t.minRolls() + 1, rollCounts.size(), "all roll counts occur");
		}
	}

	@Test
	void weightsAreRespected() {
		Random r = new Random(11);
		Map<String, Integer> hits = new HashMap<>();
		int stacks = 0;
		for (int i = 0; i < 40_000; i++) {
			for (CasinoLoot.Stack s : CasinoLoot.roll(CasinoLoot.Table.VILLAGE_CASINO, r::nextInt, id -> true)) {
				hits.merge(s.item(), 1, Integer::sum);
				stacks++;
			}
		}
		assertEquals(30 / 110.0, hits.get("burmaldaholic:chip_1") / (double) stacks, 0.005);
		assertEquals(3 / 110.0, hits.get("burmaldaholic:casino_card") / (double) stacks, 0.003);
	}

	@Test
	void missingItemsAreLeftOutNotWasted() {
		Random r = new Random(3);
		Set<String> present = Set.of("minecraft:gold_ingot", "minecraft:gold_block", "burmaldaholic:chip_25",
			"burmaldaholic:chip_100", "minecraft:netherite_scrap");
		for (int i = 0; i < 2_000; i++) {
			List<CasinoLoot.Stack> stacks = CasinoLoot.roll(CasinoLoot.Table.PIGLIN_PARLOR, r::nextInt, present::contains);
			assertTrue(stacks.size() >= 5);
			stacks.forEach(s -> assertTrue(present.contains(s.item())));
		}
		assertTrue(CasinoLoot.roll(CasinoLoot.Table.PIGLIN_PARLOR, r::nextInt, id -> false).isEmpty());
	}

	@Test
	void scatterSlotsDistinct() {
		Random r = new Random(5);
		List<Integer> slots = CasinoLoot.scatterSlots(8, 27, r::nextInt);
		assertEquals(8, new HashSet<>(slots).size());
		slots.forEach(s -> assertTrue(s >= 0 && s < 27));
		assertEquals(27, CasinoLoot.scatterSlots(40, 27, r::nextInt).size());
	}

	@Test
	void enchantPickInRange() {
		Random r = new Random(9);
		for (int i = 0; i < 1000; i++) {
			CasinoLoot.EnchantPick p = CasinoLoot.pickEnchant(r::nextInt);
			CasinoLoot.Enchant e = CasinoLoot.BOOK_ENCHANTMENTS.stream().filter(x -> x.id().equals(p.id())).findFirst().orElseThrow();
			assertTrue(p.level() >= 1 && p.level() <= e.maxLevel());
		}
	}
}

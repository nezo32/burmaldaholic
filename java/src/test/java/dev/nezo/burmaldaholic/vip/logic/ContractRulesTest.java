package dev.nezo.burmaldaholic.vip.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

/** GAME_DESIGN.md §3.4.4 vectors. */
class ContractRulesTest {
	private static IntUnaryOperator rng(long seed) {
		SplittableRandom r = new SplittableRandom(seed);
		return r::nextInt;
	}

	private static ContractRules.Params params(int tier) {
		return new ContractRules.Params(tier, 0.25, VipRules.contractBonus(tier, 0.05, 0.10), 1.0, Map.of());
	}

	@Test
	void specTableHasSeventeenContracts() {
		assertEquals(17, ContractRules.DEFS.size());
		assertEquals(24, ContractRules.DEFS.get("mine_iron").target());
		assertEquals(50, ContractRules.DEFS.get("mine_iron").reward());
		assertEquals(3, ContractRules.DEFS.get("smelt").weight());
	}

	@Test
	void scaling() {
		assertEquals(24, ContractRules.scaledTarget(24, 0, 0.25));
		assertEquals(30, ContractRules.scaledTarget(24, 1, 0.25));
		assertEquals(4, ContractRules.scaledTarget(3, 1, 0.25), "3 × 1.25 = 3.75 rounds up");
		assertEquals(6, ContractRules.scaledTarget(3, 4, 0.25));
		assertEquals(50, ContractRules.scaledReward(50, 0, 0.25, 0, 1));
		// Silver: 50 × 1.25 × 1.05 = 65.625
		assertEquals(65, ContractRules.scaledReward(50, 1, 0.25, 0.05, 1));
		// Netherite: 80 × 2.25 × 1.10 = 198
		assertEquals(198, ContractRules.scaledReward(80, 5, 0.25, 0.10, 1));
		assertEquals(0, ContractRules.scaledReward(80, 5, 0.25, 0.10, 0));
	}

	@Test
	void generateDrawsDistinctIds() {
		for (long seed = 0; seed < 200; seed++) {
			ContractRules.State s = ContractRules.generate(rng(seed), 7, 5, params(4));
			assertEquals(5, s.list.size());
			Set<String> ids = new HashSet<>();
			s.list.forEach(c -> ids.add(c.id));
			assertEquals(5, ids.size());
		}
	}

	@Test
	void disabledWeightsAreNeverDrawn() {
		Map<String, Integer> w = new HashMap<>();
		ContractRules.DEFS.keySet().forEach(id -> w.put(id, 0));
		w.put("fish", 3);
		w.put("smelt", 1);
		ContractRules.Params p = new ContractRules.Params(0, 0.25, 0, 1, w);
		ContractRules.State s = ContractRules.generate(rng(1), 1, 3, p);
		assertEquals(2, s.list.size(), "pool smaller than slots");
		assertTrue(s.list.stream().allMatch(c -> c.id.equals("fish") || c.id.equals("smelt")));
	}

	@Test
	void weightedDrawFollowsWeights() {
		Map<String, Integer> counts = new HashMap<>();
		IntUnaryOperator r = rng(99);
		int n = 200_000;
		for (int i = 0; i < n; i++) {
			counts.merge(ContractRules.draw(r, params(0), Set.of()), 1, Integer::sum);
		}
		int total = ContractRules.DEFS.values().stream().mapToInt(ContractRules.Def::weight).sum();
		for (ContractRules.Def d : ContractRules.DEFS.values()) {
			double expected = (double) d.weight() / total;
			assertEquals(expected, counts.getOrDefault(d.id(), 0) / (double) n, 0.005, d.id());
		}
	}

	@Test
	void progressCompletesOnce() {
		ContractRules.State s = new ContractRules.State(1, new java.util.ArrayList<>(List.of(ContractRules.make("mine_iron", params(0)))));
		assertTrue(ContractRules.addProgress(s, "mine_iron", 23).isEmpty());
		assertTrue(ContractRules.addProgress(s, "mine_coal", 50).isEmpty());
		assertEquals(1, ContractRules.addProgress(s, "mine_iron", 5).size());
		assertEquals(24, s.list.get(0).progress, "capped at the target");
		assertTrue(ContractRules.addProgress(s, "mine_iron", 5).isEmpty(), "rewarded only once");
		assertTrue(s.allDone());
		assertFalse(s.wants("mine_iron"));
	}

	@Test
	void rerollRules() {
		ContractRules.State s = ContractRules.generate(rng(5), 1, 3, params(0));
		String before = s.list.get(1).id;
		ContractRules.RerollResult r = ContractRules.reroll(rng(6), s, 1, params(0));
		assertTrue(r.ok());
		assertNotEquals(before, s.list.get(1).id);
		assertTrue(s.list.get(1).rerolled);
		assertEquals(ContractRules.RerollError.REROLLED, ContractRules.reroll(rng(7), s, 1, params(0)).error());
		assertEquals(ContractRules.RerollError.NO_SLOT, ContractRules.canReroll(s, 3));
		s.list.get(0).done = true;
		assertEquals(ContractRules.RerollError.DONE, ContractRules.canReroll(s, 0));
		assertNull(ContractRules.canReroll(s, 2));
	}

	@Test
	void topUpAddsSlotsAfterPromotion() {
		ContractRules.State s = ContractRules.generate(rng(3), 1, 3, params(2));
		assertEquals(1, ContractRules.topUp(rng(4), s, 4, params(3)).size());
		assertEquals(4, s.list.size());
		assertTrue(ContractRules.topUp(rng(4), s, 4, params(3)).isEmpty());
		assertTrue(ContractRules.isStale(s, 2));
		assertFalse(ContractRules.isStale(s, 1));
	}

	@Test
	void classification() {
		assertEquals("mine_iron", ContractRules.oreContract("deepslate_iron_ore"));
		assertNull(ContractRules.oreContract("stone"));
		assertTrue(ContractRules.isHarvestCrop("beetroots"));
		assertFalse(ContractRules.isHarvestCrop("melon"));
		assertEquals(List.of("kill_zombie", "kill_any"), ContractRules.killContracts("husk", true));
		assertEquals(List.of("kill_creeper", "kill_any"), ContractRules.killContracts("creeper", true));
		assertEquals(List.of(), ContractRules.killContracts("cow", false));
		assertEquals(Map.of("wager", 100L, "win_blackjack", 1L), ContractRules.playContracts("blackjack", 100, 250));
		assertEquals(Map.of("wager", 100L), ContractRules.playContracts("blackjack", 100, 0));
		assertEquals(Map.of("wager", 10L, "spin_slots", 1L), ContractRules.playContracts("slots", 10, 0));
		assertEquals(0, ContractRules.travelStep(0, 0, 100, 0, 80));
		assertEquals(5, ContractRules.travelStep(0, 0, 3, 4, 80), 1e-9);
	}
}

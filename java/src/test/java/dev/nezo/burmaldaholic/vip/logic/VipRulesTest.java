package dev.nezo.burmaldaholic.vip.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** GAME_DESIGN.md §12 vectors. */
class VipRulesTest {
	private static final long[] TH = VipRules.DEFAULT_THRESHOLDS;
	private static final int[][] LOANS = {{100, 3, 0}, {500, 3, 0}, {2000, 5, 1}, {10000, 7, 2}, {50000, 7, 4}};
	private static final VipRules.PerkParams PARAMS = new VipRules.PerkParams(LOANS, 0.05, 0.10, 3, new double[] {0.02, 0.03, 0.04, 0.05});

	@Test
	void tierBoundaries() {
		assertEquals(0, VipRules.tierFor(0, TH));
		assertEquals(0, VipRules.tierFor(4_999, TH));
		assertEquals(1, VipRules.tierFor(5_000, TH));
		assertEquals(2, VipRules.tierFor(25_000, TH));
		assertEquals(3, VipRules.tierFor(100_000, TH));
		assertEquals(4, VipRules.tierFor(499_999 + 1, TH));
		assertEquals(5, VipRules.tierFor(2_500_000, TH));
		assertEquals(5, VipRules.tierFor(Long.MAX_VALUE, TH));
	}

	@Test
	void sanitizeKeepsThresholdsAscending() {
		assertArrayEquals(new long[] {5000, 5001, 5002, 500000, 2500000}, VipRules.sanitize(new long[] {5000, 100, 3, 500000, 2500000}));
		assertArrayEquals(TH, VipRules.sanitize(new long[] {0, -1, 0, 0, 0}));
		assertArrayEquals(TH, VipRules.sanitize(null));
	}

	@Test
	void progressToNextTier() {
		VipRules.Progress p = VipRules.progress(12_400, TH, 0);
		assertEquals(1, p.tier());
		assertEquals(2, p.next());
		assertEquals(25_000, p.target());
		assertEquals((12_400 - 5_000) / 20_000.0, p.fraction(), 1e-9);
		assertTrue(VipRules.progress(3_000_000, TH, 0).maxed());
		// tiers are never lost: stored Gold stays Gold even if thresholds were raised
		VipRules.Progress kept = VipRules.progress(10, TH, 2);
		assertEquals(2, kept.tier());
		assertEquals(0.0, kept.fraction());
	}

	@Test
	void promotionsListEveryTierOnce() {
		assertEquals(List.of(1, 2, 3), VipRules.promotions(0, 3));
		assertEquals(List.of(), VipRules.promotions(2, 2));
		assertEquals(List.of(5), VipRules.promotions(4, 9));
	}

	@Test
	void perkNumbers() {
		assertEquals(0, VipRules.cashbackRate(1, PARAMS.cashback()));
		assertEquals(0.02, VipRules.cashbackRate(2, PARAMS.cashback()));
		assertEquals(0.05, VipRules.cashbackRate(5, PARAMS.cashback()));
		assertEquals(0.5, VipRules.cashbackRate(5, new double[] {0, 0, 0, 7}), "clamped to the config range");
		assertEquals(0, VipRules.contractBonus(0, 0.05, 0.1));
		assertEquals(0.05, VipRules.contractBonus(1, 0.05, 0.1));
		assertEquals(0.1, VipRules.contractBonus(4, 0.05, 0.1));
		assertEquals(3, VipRules.contractSlots(2, 3));
		assertEquals(4, VipRules.contractSlots(3, 3));
		assertEquals(5, VipRules.contractSlots(5, 3));
		assertEquals(500, VipRules.maxLoanFor(0, LOANS));
		assertEquals(10000, VipRules.maxLoanFor(3, LOANS));
		assertEquals(0, VipRules.loanUnlockedAt(3, LOANS));
		assertEquals(50000, VipRules.loanUnlockedAt(4, LOANS));
	}

	@Test
	void perksMatchTheSpecTable() {
		List<String> gold = VipRules.perksAt(2, PARAMS).stream().map(VipRules.Perk::key).toList();
		assertTrue(gold.contains("gui.burmaldaholic.vip.perk.netherite_slots"));
		assertTrue(gold.contains("gui.burmaldaholic.vip.perk.cashback"));
		assertTrue(gold.contains("gui.burmaldaholic.vip.perk.contract_bonus"));
		assertTrue(gold.contains("gui.burmaldaholic.vip.perk.cosmetic_particles"));
		VipRules.Perk slots = VipRules.perksAt(3, PARAMS).stream().filter(p -> p.key().endsWith("contract_slots")).findFirst().orElseThrow();
		assertEquals(4, slots.value());
		VipRules.Perk cb = VipRules.perksAt(5, PARAMS).stream().filter(p -> p.key().endsWith("cashback")).findFirst().orElseThrow();
		assertEquals(50, cb.value(), "5 % in tenths");
		assertFalse(VipRules.perksAt(0, PARAMS).stream().anyMatch(p -> p.key().contains("cosmetic")));
		assertTrue(VipRules.perksAt(1, PARAMS).stream().anyMatch(p -> p.key().endsWith("loan") && p.value() == 2000));
	}

	@Test
	void advancementIds() {
		assertNull(VipRules.advancementId(0));
		assertEquals("vip_silver", VipRules.advancementId(1));
		assertEquals("vip_netherite", VipRules.advancementId(5));
	}

	@Test
	void biggestWinIsTheLargestNetWinOnly() {
		assertEquals(0, VipRules.biggestWin(0, 100, 0), "a loss never counts");
		assertEquals(0, VipRules.biggestWin(0, 100, 100), "a push never counts");
		assertEquals(150, VipRules.biggestWin(0, 100, 250));
		assertEquals(150, VipRules.biggestWin(150, 100, 200), "a smaller win keeps the record");
		assertEquals(4_900, VipRules.biggestWin(150, 100, 5_000));
		assertEquals(150, VipRules.biggestWin(150, -5, 100), "a negative bet is treated as 0");
	}
}

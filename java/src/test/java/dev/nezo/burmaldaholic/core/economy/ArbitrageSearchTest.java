package dev.nezo.burmaldaholic.core.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.config.sections.EconomyConfig;
import dev.nezo.burmaldaholic.core.config.sections.StoreConfig;
import dev.nezo.burmaldaholic.core.config.sections.WagerConfig;
import dev.nezo.burmaldaholic.core.store.StoreCatalog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * T-EC1: searches for a profitable round trip across EVERY exchange row, store offer, crafting conversion (both ways,
 * transitively), villager buy (incl. residues, grindstone book) and pawn appraisal. Dimensions are ignored (a player can
 * travel), so the search is conservative.
 *
 * <p>sell(x) = best chips one x can become; cost(x) = cheapest chips to get one x. Arbitrage ⇔ sell(x) &gt; cost(x).
 */
class ArbitrageSearchTest {
	/** Extra one-item → one-item conversions a player can do for free (item → what it becomes). */
	private static final Map<String, String> RESIDUES = Map.of(
		"minecraft:potion", "minecraft:glass_bottle",
		"minecraft:enchanted_book", "minecraft:book", // grindstone
		"minecraft:honey_bottle", "minecraft:glass_bottle");

	record Market(Map<String, Double> in, Map<String, Double> out, Map<String, Double> store, Map<String, Integer> appraisal, double villagerValue) {}

	static Market market(EconomyConfig eco, StoreConfig store, Map<String, Integer> appraisal, boolean applyGuards) {
		List<EconomyGuards.Row> rows = new ArrayList<>(eco.guardRows());
		Set<String> noOut = new HashSet<>();
		if (applyGuards) for (EconomyGuards.Violation v : eco.rowViolations()) noOut.add(v.id());
		Map<String, Double> in = new HashMap<>(), out = new HashMap<>(), st = new HashMap<>();
		for (EconomyGuards.Row r : rows) {
			if (r.toChips() > 0) in.merge(r.item(), (double) r.toChips(), Math::max);
			if (r.fromChips() > 0 && !noOut.contains(r.id())) out.merge(r.item(), (double) r.fromChips(), Math::min);
		}
		Set<String> badOffers = new HashSet<>();
		if (applyGuards) for (EconomyGuards.Violation v : StoreCatalog.violations(store, eco, appraisal)) badOffers.add(v.id());
		store.offers.forEach((id, o) -> {
			if (o.price > 0 && !badOffers.contains(id)) st.merge(o.item, o.price / (double) Math.max(1, o.count), Math::min);
		});
		EconomyConfig.ExchangeRow em = eco.exchange.get("emerald");
		double v = EconomyGuards.villagerValue(em == null ? 0 : em.toChips, eco.trade.perTradeBase, eco.trade.perEmerald);
		return new Market(in, out, st, appraisal, v);
	}

	/** Returns a description of the first profitable loop, or null. */
	static String findArbitrage(Market m) {
		Set<String> items = new HashSet<>();
		items.addAll(m.in().keySet());
		items.addAll(m.out().keySet());
		items.addAll(m.store().keySet());
		items.addAll(m.appraisal().keySet());
		items.addAll(EconomyGuards.VILLAGER_BUYS.keySet());
		for (EconomyGuards.Equivalence e : EconomyGuards.EQUIVALENCES) {
			items.add(e.big());
			items.add(e.small());
		}
		items.addAll(RESIDUES.keySet());
		items.addAll(RESIDUES.values());
		Map<String, Double> sell = new HashMap<>(), cost = new HashMap<>();
		for (String x : items) {
			double s = m.in().getOrDefault(x, 0.0);
			s = Math.max(s, m.appraisal().getOrDefault(x, 0)); // conservative: pawning counts as selling at appraisal
			Integer n = EconomyGuards.VILLAGER_BUYS.get(x);
			if (n != null) s = Math.max(s, m.villagerValue() / n);
			sell.put(x, s);
			double c = Math.min(m.out().getOrDefault(x, Double.POSITIVE_INFINITY), m.store().getOrDefault(x, Double.POSITIVE_INFINITY));
			cost.put(x, c);
		}
		for (int iter = 0; iter < 64; iter++) {
			boolean changed = false;
			for (EconomyGuards.Equivalence e : EconomyGuards.EQUIVALENCES) {
				changed |= raise(sell, e.big(), e.k() * sell.get(e.small())); // uncraft big → k small
				changed |= raise(sell, e.small(), sell.get(e.big()) / e.k()); // craft k small → big
				changed |= lower(cost, e.big(), e.k() * cost.get(e.small()));
				changed |= lower(cost, e.small(), cost.get(e.big()) / e.k());
			}
			for (Map.Entry<String, String> r : RESIDUES.entrySet()) changed |= raise(sell, r.getKey(), sell.get(r.getValue()));
			// the exchange's own emerald: an emerald sells to the exchange; villager-bought items use villagerValue
			if (!changed) break;
		}
		for (String x : items) {
			if (sell.get(x) > cost.get(x) + 1e-9) return x + ": sell " + sell.get(x) + " > cost " + cost.get(x);
		}
		return null;
	}

	private static boolean raise(Map<String, Double> m, String k, double v) {
		if (v > m.get(k) + 1e-9) {
			m.put(k, v);
			return true;
		}
		return false;
	}

	private static boolean lower(Map<String, Double> m, String k, double v) {
		if (v < m.get(k) - 1e-9) {
			m.put(k, v);
			return true;
		}
		return false;
	}

	@Test
	void defaultsHaveNoProfitableRoundTrip() {
		Market m = market(new EconomyConfig(), new StoreConfig(), new WagerConfig().appraisal, false);
		assertEquals(null, findArbitrage(m), "defaults, guards NOT applied");
	}

	@Test
	void defaultsMarginsPerRow() {
		EconomyConfig eco = new EconomyConfig();
		for (EconomyGuards.Row r : eco.guardRows()) {
			assertTrue(r.fromChips() == 0 || r.fromChips() > r.toChips(), r.id());
		}
	}

	/** Random direct-pair configs (the shape R2 is written for): after the guards, never a profit. */
	@Test
	void randomizedPairsAfterGuardsNeverProfit() {
		Random rnd = new Random(7);
		String[][] pairs = {{"minecraft:emerald", "minecraft:emerald_block"}, {"minecraft:diamond", "minecraft:diamond_block"},
			{"minecraft:gold_ingot", "minecraft:gold_block"}, {"minecraft:iron_ingot", "minecraft:iron_block"}};
		for (int t = 0; t < 5000; t++) {
			EconomyConfig eco = new EconomyConfig();
			eco.exchange.clear();
			for (String[] p : pairs) {
				if (rnd.nextBoolean()) row(eco, p[0], 1 + rnd.nextInt(60), rnd.nextInt(80));
				if (rnd.nextBoolean()) row(eco, p[1], 1 + rnd.nextInt(600), rnd.nextInt(800));
			}
			StoreConfig store = new StoreConfig();
			store.offers.clear();
			Market m = market(eco, store, Map.of(), true);
			// villagers included: R4 now covers exchange rows too (T-EC1 D3)
			String r = findArbitrage(m);
			assertEquals(null, r, "trial " + t + " rows " + describe(eco));
		}
	}

	/**
	 * Fixed T-EC1 D3: R2 is transitive. Rows two crafting steps apart (block ↔ nugget, 81) are compared, so the block's
	 * way out is disabled and the config no longer leaks chips.
	 */
	@Test
	void r2IsTransitive() {
		EconomyConfig eco = new EconomyConfig();
		eco.exchange.clear();
		row(eco, "minecraft:iron_block", 16, 27);
		row(eco, "minecraft:iron_nugget", 1, 2);
		List<EconomyGuards.Violation> v = eco.rowViolations();
		assertTrue(v.stream().anyMatch(x -> x.rule().equals("R2")), "R2 catches block -> 81 nuggets: " + v);
		assertEquals(null, findArbitrage(market(eco, new StoreConfig(), Map.of(), true)), "no loop after the guards");
	}

	/** Fixed T-EC1 D3b: R4 (villager floor) also applies to exchange rows' way out. */
	@Test
	void exchangeRowsKeepVillagerFloor() {
		EconomyConfig eco = new EconomyConfig();
		eco.exchange.get("gold_ingot").fromChips = 3; // 3 ingots (9 chips) -> 1 emerald (9 + 2 trade bonus)
		eco.exchange.get("gold_ingot").toChips = 2;
		List<EconomyGuards.Violation> v = eco.rowViolations();
		assertTrue(v.stream().anyMatch(x -> x.rule().equals("R4") && x.id().equals("gold_ingot")), "R4 catches the row: " + v);
		assertEquals(null, findArbitrage(market(eco, new StoreConfig(), Map.of(), true)), "no loop after the guards");
	}

	/** Fixed T-EC1 D3c: R6 forbids gear that smelts into iron / gold nuggets. */
	@Test
	void smeltableGearIsForbidden() {
		for (String id : List.of("minecraft:iron_helmet", "minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots",
			"minecraft:golden_helmet", "minecraft:golden_boots", "minecraft:chainmail_chestplate", "minecraft:iron_horse_armor",
			"minecraft:golden_horse_armor", "minecraft:iron_sword", "minecraft:golden_axe")) {
			assertTrue(EconomyGuards.forbidden(id), id);
		}
		for (String id : List.of("minecraft:golden_apple", "minecraft:golden_carrot", "minecraft:diamond_sword", "minecraft:leather_helmet")) {
			assertTrue(!EconomyGuards.forbidden(id), id);
		}
	}

	/** T-EC1 re-test: random rows over whole crafting chains (block / ingot / nugget, 81x), villagers included. */
	@Test
	void randomizedMultiStepChainsAfterGuardsNeverProfit() {
		Random rnd = new Random(11);
		String[] items = {"minecraft:iron_block", "minecraft:iron_ingot", "minecraft:iron_nugget", "minecraft:gold_block", "minecraft:gold_ingot",
			"minecraft:gold_nugget", "minecraft:emerald", "minecraft:emerald_block", "minecraft:diamond", "minecraft:diamond_block",
			"minecraft:netherite_ingot", "minecraft:netherite_block"};
		int[] scale = {200, 30, 4, 300, 30, 4, 30, 300, 80, 700, 400, 4000};
		for (int t = 0; t < 20000; t++) {
			EconomyConfig eco = new EconomyConfig();
			eco.exchange.clear();
			for (int i = 0; i < items.length; i++) {
				if (rnd.nextInt(3) == 0) continue;
				row(eco, items[i], 1 + rnd.nextInt(scale[i]), rnd.nextInt(2 * scale[i]));
			}
			StoreConfig store = new StoreConfig();
			store.offers.clear();
			String r = findArbitrage(market(eco, store, Map.of(), true));
			assertEquals(null, r, "trial " + t + " rows " + describe(eco));
		}
	}

	/**
	 * DEFECT (T-EC1 D6, config-only): two rows for the same item (e.g. a per-dimension variant) are never compared, so
	 * buying from one and selling to the other profits. Fixed when the guards disable one of them.
	 */
	@Test
	void duplicateRowsOfOneItemAreCompared() {
		EconomyConfig eco = new EconomyConfig();
		eco.exchange.clear();
		row(eco, "minecraft:emerald", 11, 16);
		row(eco, "minecraft:emerald", 2, 3);
		assertEquals(null, findArbitrage(market(eco, new StoreConfig(), Map.of(), true)), "buy at 3, sell at 11");
	}

	private static int seq;

	private static void row(EconomyConfig eco, String item, int to, int from) {
		EconomyConfig.ExchangeRow r = new EconomyConfig.ExchangeRow();
		r.item = item;
		r.toChips = to;
		r.fromChips = from;
		eco.exchange.put("r" + (seq++), r);
	}

	private static String describe(EconomyConfig eco) {
		StringBuilder b = new StringBuilder();
		eco.exchange.forEach((k, v) -> b.append(v.item).append(' ').append(v.toChips).append('/').append(v.fromChips).append("; "));
		return b.toString();
	}
}

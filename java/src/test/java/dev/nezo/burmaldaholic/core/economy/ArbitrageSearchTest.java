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
		if (applyGuards) for (EconomyGuards.Violation v : EconomyGuards.rows(rows)) noOut.add(v.id());
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
			// villagers excluded: R1/R2 do not cover exchange rows vs villager buys (see exchangeRowsSkipVillagerFloor)
			String r = findArbitrage(new Market(m.in(), m.out(), m.store(), m.appraisal(), 0));
			assertEquals(null, r, "trial " + t + " rows " + describe(eco));
		}
	}

	/**
	 * DEFECT (T-EC1 D3, config-only): R2 checks direct pairs only. Rows two crafting steps apart (block ↔ nugget) are not
	 * compared, so a custom config passes the guards and leaks chips. Enabled = documents the gap.
	 */
	@Test
	void r2IsNotTransitive() {
		EconomyConfig eco = new EconomyConfig();
		eco.exchange.clear();
		row(eco, "minecraft:iron_block", 16, 27);
		row(eco, "minecraft:iron_nugget", 1, 2);
		assertEquals(List.of(), EconomyGuards.rows(eco.guardRows()), "guards accept the config");
		String r = findArbitrage(market(eco, new StoreConfig(), Map.of(), true));
		assertTrue(r != null, "...but buying a block (27) and selling 81 nuggets (81) profits: " + r);
	}

	/**
	 * DEFECT (T-EC1 D3b, config-only): R4 (villager floor) is applied to store offers only. An exchange row whose
	 * {@code fromChips × n} is below an emerald's villager value passes every guard.
	 */
	@Test
	void exchangeRowsSkipVillagerFloor() {
		EconomyConfig eco = new EconomyConfig();
		eco.exchange.get("gold_ingot").fromChips = 3; // 3 ingots (9 chips) -> 1 emerald (9 + 2 trade bonus)
		eco.exchange.get("gold_ingot").toChips = 2;
		assertEquals(List.of(), EconomyGuards.rows(eco.guardRows()), "guards accept the config");
		String r = findArbitrage(market(eco, new StoreConfig(), Map.of(), true));
		assertTrue(r != null, "gold ingots bought at 3 sell to a cleric for 11 per 3: " + r);
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

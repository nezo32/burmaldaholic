package dev.nezo.burmaldaholic.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.config.sections.EconomyConfig;
import dev.nezo.burmaldaholic.core.config.sections.StoreConfig;
import dev.nezo.burmaldaholic.core.config.sections.WagerConfig;
import dev.nezo.burmaldaholic.core.economy.EconomyGuards;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** EC-SPEC §3 catalog: 38–42 offers with the 2 scratch cards, nothing forbidden, End flags, limits. */
class StoreCatalogTest {
	private final StoreConfig store = new StoreConfig();

	@Test
	void sizeWithTheScratchCards() {
		int total = store.offers.size() + 2; // + scratch_card, scratch_card_gold (CashierShop, category Games)
		assertTrue(total >= 38 && total <= 42, "catalog size " + total);
	}

	@Test
	void nothingForbiddenAndGuardsPass() {
		for (StoreConfig.Offer o : store.offers.values()) assertFalse(EconomyGuards.forbidden(o.item), o.item);
		assertEquals(List.of(), StoreCatalog.violations(store, new EconomyConfig(), new WagerConfig().appraisal));
	}

	@Test
	void endOnlyFlags() {
		for (Map.Entry<String, StoreConfig.Offer> e : store.offers.entrySet()) {
			boolean endOnly = e.getKey().equals("elytra") || e.getKey().equals("shulker_shell");
			assertEquals(endOnly ? "E" : "", e.getValue().dims, e.getKey());
		}
		StoreConfig.Offer elytra = store.offers.get("elytra");
		assertEquals(3, elytra.minTier, "Platinum");
		assertEquals(1, elytra.limitPerDay);
		assertEquals(7, elytra.limitEveryDays);
		assertEquals(8, store.offers.get("golden_apple").limitPerDay);
	}

	@Test
	void categoriesKnown() {
		for (StoreConfig.Offer o : store.offers.values()) assertTrue(StoreCatalog.CATEGORIES.contains(o.category), o.category);
	}

	@Test
	void excludedBooks() {
		for (StoreConfig.Offer o : store.offers.values()) {
			assertFalse(List.of("minecraft:swift_sneak", "minecraft:soul_speed", "minecraft:wind_burst", "minecraft:frost_walker").contains(o.enchant), o.enchant);
		}
	}

	@Test
	void limits() {
		StoreLimits.Data d = StoreLimits.Data.EMPTY;
		assertEquals(-1, d.left("x", 5, 0, 1), "no limit");
		assertEquals(8, d.left("apple", 5, 8, 1));
		d = d.record("apple", 5, 8);
		assertEquals(0, d.left("apple", 5, 8, 1));
		assertEquals(8, d.left("apple", 6, 8, 1), "a new day");
		d = d.record("elytra", 10, 1);
		assertEquals(0, d.left("elytra", 10, 1, 7));
		assertEquals(0, d.left("elytra", 16, 1, 7), "6 days later still locked");
		assertEquals(1, d.left("elytra", 17, 1, 7), "7 days later");
	}
}

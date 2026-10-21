package dev.nezo.burmaldaholic.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.config.sections.EconomyConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T-EC1: v0.1 rate keys → {@code economy.exchange} (EC-SPEC §2), edge cases beyond ConfigBinderTest. */
class EconomyMigrationTest {
	private final ConfigBinder binder = new ConfigBinder(new com.google.gson.Gson());

	private EconomyConfig bind(String json, List<ConfigIssue> issues) {
		return binder.bind(new EconomyConfig(), JsonParser.parseString(json).getAsJsonObject(), "economy", issues);
	}

	@Test
	void untouchedV01FileGetsAllSixDefaultRowsWithItems() {
		List<ConfigIssue> issues = new ArrayList<>();
		EconomyConfig c = bind("""
			{"maxBalance": 1000000000, "emeraldBuyRate": 8, "emeraldBuyRateGoldVip": 9, "emeraldSellRate": 10, "goldBuyRate": 3, "goldSellRate": 12}""", issues);
		assertEquals(List.of("emerald", "emerald_block", "diamond", "iron_block", "gold_ingot", "netherite_scrap"), List.copyOf(c.exchange.keySet()));
		assertEquals("minecraft:emerald", c.exchange.get("emerald").item);
		assertEquals(9, c.exchange.get("emerald").toChips);
		assertEquals(10, c.exchange.get("emerald").fromChips);
		assertEquals(6, c.exchange.get("gold_ingot").toChips);
		assertEquals(8, c.exchange.get("gold_ingot").fromChips);
		assertEquals(List.of(), issues);
	}

	@Test
	void customisedValuesKeepItemAndOtherRows() {
		List<ConfigIssue> issues = new ArrayList<>();
		EconomyConfig c = bind("{\"emeraldBuyRate\": 7, \"goldSellRate\": 20}", issues);
		assertEquals("minecraft:emerald", c.exchange.get("emerald").item, "a partial migrated row keeps the default item");
		assertEquals(7, c.exchange.get("emerald").toChips);
		assertEquals(20, c.exchange.get("gold_ingot").fromChips);
		assertEquals(6, c.exchange.size());
	}

	@Test
	void explicitExchangeWinsOverLegacy() {
		List<ConfigIssue> issues = new ArrayList<>();
		EconomyConfig c = bind("{\"emeraldBuyRate\": 7, \"exchange\": {\"emerald\": {\"toChips\": 5}}}", issues);
		assertEquals(5, c.exchange.get("emerald").toChips);
	}

	@Test
	void migratedArbitrageIsDisabledByR1() {
		List<ConfigIssue> issues = new ArrayList<>();
		// v0.1 allowed buy == sell (10/10); the migrated row must not keep a zero-margin way out
		EconomyConfig c = bind("{\"emeraldBuyRate\": 10}", issues);
		assertEquals(10, c.exchange.get("emerald").toChips);
		assertEquals(0, c.exchange.get("emerald").fromChips, "R1: sell-only");
		assertTrue(issues.stream().anyMatch(i -> i.key().contains("exchange.emerald.fromChips")), "reported: " + issues);
	}

	@Test
	void garbageLegacyValuesAreIgnored() {
		List<ConfigIssue> issues = new ArrayList<>();
		EconomyConfig c = bind("{\"emeraldBuyRate\": \"lots\", \"goldBuyRate\": -4, \"exchange\": 3}", issues);
		assertEquals(9, c.exchange.get("emerald").toChips, "non-number legacy value dropped");
		// a negative legacy value is moved and then clamped by @Range(min = 0)
		assertTrue(c.exchange.get("gold_ingot").toChips >= 0);
	}
}

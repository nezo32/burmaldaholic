package dev.nezo.burmaldaholic.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.config.sections.ChaosConfig;
import dev.nezo.burmaldaholic.core.config.sections.CoreConfig;
import dev.nezo.burmaldaholic.core.config.sections.EconomyConfig;
import dev.nezo.burmaldaholic.core.config.sections.ExtrasConfig;
import dev.nezo.burmaldaholic.core.config.sections.StreakConfig;
import dev.nezo.burmaldaholic.core.config.sections.VipConfig;
import dev.nezo.burmaldaholic.core.config.sections.WagerConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigBinderTest {
	private final ConfigBinder binder = new ConfigBinder(new com.google.gson.Gson());

	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	private static boolean has(List<ConfigIssue> issues, String key, ConfigIssue.Kind kind) {
		return issues.stream().anyMatch(i -> i.key().equals(key) && i.kind() == kind);
	}

	@Test
	void clampsWrongTypesAndUnknownKeysPerField() {
		List<ConfigIssue> issues = new ArrayList<>();
		EconomyConfig c = binder.bind(new EconomyConfig(), json("""
			{"startingBalance": 5000000, "emeraldBuyRate": "lots", "ore": {"diamond": 25, "coal": 1.5, "mithril": 3}}"""), "economy", issues);
		assertEquals(1_000_000, c.startingBalance, "clamped to the CONFIG.md max");
		assertTrue(has(issues, "economy.startingBalance", ConfigIssue.Kind.CLAMPED));
		assertEquals(8, c.emeraldBuyRate, "wrong type -> default, other fields unaffected");
		assertTrue(has(issues, "economy.emeraldBuyRate", ConfigIssue.Kind.INVALID));
		assertEquals(25, c.ore.diamond);
		assertEquals(1, c.ore.coal, "non-integral int -> default");
		assertTrue(has(issues, "economy.ore.mithril", ConfigIssue.Kind.UNKNOWN));
	}

	@Test
	void crossFieldValidation() {
		List<ConfigIssue> issues = new ArrayList<>();
		EconomyConfig c = binder.bind(new EconomyConfig(), json("{\"emeraldSellRate\": 5}"), "economy", issues);
		assertEquals(9, c.emeraldSellRate, "sell rate must be ≥ buy rates");
		VipConfig v = binder.bind(new VipConfig(), json("{\"threshold\": {\"gold\": 100}}"), "vip", issues);
		assertEquals(5001, v.threshold.gold, "thresholds must ascend");
		StreakConfig s = binder.bind(new StreakConfig(), json("{\"minHouseEdge\": 0.001}"), "streak", issues);
		assertEquals(0.005, s.minHouseEdge, 1e-12, "hard floor 0.005");
		ExtrasConfig e = binder.bind(new ExtrasConfig(), json("{\"plinko\": {\"low\": [1, 2]}}"), "extras", issues);
		assertEquals(13, e.plinko.low.length, "wrong list size -> default");
	}

	@Test
	void familiesAndEnums() {
		List<ConfigIssue> issues = new ArrayList<>();
		ChaosConfig c = binder.bind(new ChaosConfig(), json("""
			{"weight": {"mob_wave": 0, "meteor": 5}, "event": {"curse": {"enabled": false}}}"""), "chaos", issues);
		assertEquals(0, c.weight.get("mob_wave"));
		assertEquals(18, c.weight.get("chip_shower"), "untouched members keep defaults");
		assertFalse(c.weight.containsKey("meteor"));
		assertTrue(has(issues, "chaos.weight.meteor", ConfigIssue.Kind.UNKNOWN));
		assertFalse(c.event.get("curse").enabled);
		assertTrue(c.event.get("mob_wave").enabled);
		WagerConfig w = binder.bind(new WagerConfig(), json("{\"appraisal\": {\"minecraft:gold_block\": 36}}"), "wager", issues);
		assertEquals(36, w.appraisal.get("minecraft:gold_block"), "appraisal is an open family");
		CoreConfig core = binder.bind(new CoreConfig(), json("{\"hud\": {\"position\": \"bottom_right\"}}"), "core", issues);
		assertEquals(CoreConfig.HudPosition.BOTTOM_RIGHT, core.hud.position);
	}

	@Test
	void worldOverrideWinsAndSetValidates(@TempDir Path dir) throws Exception {
		Path global = dir.resolve("burmaldaholic.json");
		Path world = dir.resolve("world/data/burmaldaholic_config.json");
		Files.writeString(global, "{\"economy\":{\"startingBalance\":100,\"ore\":{\"coal\":3}}}");
		Files.createDirectories(world.getParent());
		Files.writeString(world, "{\"economy\":{\"startingBalance\":7}}");
		ConfigManager m = new ConfigManager(global);
		ConfigHandle<EconomyConfig> h = m.register("economy", EconomyConfig.class, EconomyConfig::new);
		m.setWorldFile(world);
		m.load();
		assertEquals(7, h.get().startingBalance, "world file wins");
		assertEquals(3, h.get().ore.coal, "global value kept where the world file is silent");

		assertTrue(m.setWorldValue("economy.ore.diamond", "30").ok());
		assertEquals(30, h.get().ore.diamond);
		assertTrue(Files.readString(world).contains("\"diamond\": 30"), "written to the override file");
		assertFalse(Files.readString(global).contains("\"diamond\": 30"), "global file untouched");

		ConfigManager.SetResult clamped = m.setWorldValue("economy.ore.diamond", "999999");
		assertTrue(clamped.ok());
		assertEquals(1000, h.get().ore.diamond);
		assertTrue(clamped.issues().stream().anyMatch(i -> i.kind() == ConfigIssue.Kind.CLAMPED));

		assertFalse(m.setWorldValue("economy.ore.diamond", "abc").ok(), "invalid value rejected");
		assertEquals(1000, h.get().ore.diamond);
		assertFalse(m.setWorldValue("economy.nope", "1").ok(), "unknown key rejected");

		assertTrue(m.resetWorldValue("economy.startingBalance").ok());
		assertEquals(50, h.get().startingBalance, "reset = CONFIG.md default");

		m.setWorldFile(null);
		m.load();
		assertEquals(100, h.get().startingBalance, "no world: global only");
		assertTrue(m.keys().contains("economy.ore.ancientDebris"));
	}
}

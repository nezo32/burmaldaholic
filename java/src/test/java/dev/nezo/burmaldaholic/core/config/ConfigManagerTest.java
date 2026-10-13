package dev.nezo.burmaldaholic.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigManagerTest {
	public static final class Section {
		public int maxBet = 500;
		public boolean enabled = true;
		public Ore ore = new Ore();
	}

	public static final class Ore {
		public int diamond = 20;
		public int coal = 1;
	}

	@Test
	void missingFileWritesDefaultsAndPartialFileKeepsOtherDefaults(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("burmaldaholic.json");
		ConfigManager m = new ConfigManager(file);
		ConfigHandle<Section> h = m.register("slots", Section.class, Section::new);
		m.load();
		assertTrue(Files.readString(file).contains("\"maxBet\": 500"));

		Files.writeString(file, "{\"slots\":{\"maxBet\":42,\"unknown\":1,\"ore\":{\"diamond\":25}}}");
		m.load();
		assertEquals(42, h.get().maxBet);
		assertEquals(25, h.get().ore.diamond);
		assertEquals(1, h.get().ore.coal);
		assertTrue(h.get().enabled);
		String rewritten = Files.readString(file);
		assertTrue(rewritten.contains("\"enabled\": true"));
		assertTrue(!rewritten.contains("unknown"));
	}

	@Test
	void corruptFileFallsBackToDefaults(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("burmaldaholic.json");
		Files.writeString(file, "{ not json");
		ConfigManager m = new ConfigManager(file);
		ConfigHandle<Section> h = m.register("slots", Section.class, Section::new);
		m.load();
		assertEquals(500, h.get().maxBet);
	}

	public static final class Worldgen {
		public Chance villageCasino = new Chance(0.5);
		public Chance piglinParlor = new Chance(0.6);
		public Chance highRoller = new Chance(0.5);
	}

	public static final class Chance {
		public double chance;

		public Chance() {}

		Chance(double chance) {
			this.chance = chance;
		}
	}

	/** A v0.1.1 global file still holding the old attach-chance defaults is upgraded; custom values are kept. */
	@Test
	void oldWorldgenChanceDefaultsAreUpgradedCustomValuesKept(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("burmaldaholic.json");
		Files.writeString(file, "{\"worldgen\":{\"villageCasino\":{\"chance\":0.35},\"piglinParlor\":{\"chance\":0.3},"
			+ "\"highRoller\":{\"chance\":0.25}}}");
		ConfigManager m = new ConfigManager(file);
		ConfigHandle<Worldgen> h = m.register("worldgen", Worldgen.class, Worldgen::new);
		m.load();
		assertEquals(0.5, h.get().villageCasino.chance);
		assertEquals(0.6, h.get().piglinParlor.chance);
		assertEquals(0.25, h.get().highRoller.chance, "a custom value is not an old default");
		m.load(); // the rewritten file now holds the new defaults and stays stable
		assertEquals(0.5, h.get().villageCasino.chance);
		assertTrue(Files.readString(file).contains("\"chance\": 0.6"));
	}

	/** The upgrade runs once: the rewritten file carries configVersion, so a deliberate old value set later is kept. */
	@Test
	void retiredDefaultSetDeliberatelyInCurrentFileIsKept(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("burmaldaholic.json");
		ConfigManager m = new ConfigManager(file);
		ConfigHandle<Worldgen> h = m.register("worldgen", Worldgen.class, Worldgen::new);
		m.load();
		String written = Files.readString(file);
		assertTrue(written.contains("\"configVersion\": " + ConfigManager.SCHEMA_VERSION), written);
		Files.writeString(file, written.replace("\"chance\": 0.5", "\"chance\": 0.35"));
		m.load();
		assertEquals(0.35, h.get().villageCasino.chance, "an admin's 0.35 in a current file is not reset");
		m.load(); // and stays across restarts
		assertEquals(0.35, h.get().villageCasino.chance);
		assertTrue(Files.readString(file).contains("\"chance\": 0.35"));
		assertTrue(m.lastIssues().stream().noneMatch(i -> i.key().equals(ConfigManager.SCHEMA_KEY)), "configVersion is not an unknown key");
	}

	/** A file stamped older than the current schema is still upgraded, then stamped current. */
	@Test
	void olderSchemaVersionIsUpgradedThenStamped(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("burmaldaholic.json");
		Files.writeString(file, "{\"configVersion\":1,\"worldgen\":{\"highRoller\":{\"chance\":0.2}}}");
		ConfigManager m = new ConfigManager(file);
		ConfigHandle<Worldgen> h = m.register("worldgen", Worldgen.class, Worldgen::new);
		m.load();
		assertEquals(0.5, h.get().highRoller.chance);
		assertTrue(Files.readString(file).contains("\"configVersion\": " + ConfigManager.SCHEMA_VERSION));
	}

	/** The config screen saves a tree without configVersion: it is stamped, so a 0.3 chosen there is kept. */
	@Test
	void saveGlobalStampsVersion(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("burmaldaholic.json");
		ConfigManager m = new ConfigManager(file);
		ConfigHandle<Worldgen> h = m.register("worldgen", Worldgen.class, Worldgen::new);
		m.load();
		com.google.gson.JsonObject root = m.globalJson();
		ConfigManager.put(root, "worldgen.piglinParlor.chance", new com.google.gson.JsonPrimitive(0.3));
		m.saveGlobal(root);
		assertEquals(0.3, h.get().piglinParlor.chance);
		m.load();
		assertEquals(0.3, h.get().piglinParlor.chance);
	}
}

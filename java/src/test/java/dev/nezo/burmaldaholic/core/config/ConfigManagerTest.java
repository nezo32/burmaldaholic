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
}

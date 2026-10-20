package dev.nezo.burmaldaholic.core.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nezo.burmaldaholic.core.config.sections.SlotsConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-SLT: each retirement step must run only for files OLDER than the version that retired it. A v2 file already went
 * through the v1 -> v2 worldgen retirement; an admin's deliberate 0.3 written under v2 must survive the v2 -> v3 slots
 * upgrade (and a v1 file still gets both).
 */
class SchemaUpgradeStepsTest {
	@Test
	void v2FileKeepsDeliberateWorldgenValueOnTheSlotsUpgrade(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("burmaldaholic.json");
		Files.writeString(file, "{\"configVersion\":2,\"worldgen\":{\"piglinParlor\":{\"chance\":0.3}}}");
		ConfigManager m = new ConfigManager(file);
		ConfigHandle<ConfigManagerTest.Worldgen> h = m.register("worldgen", ConfigManagerTest.Worldgen.class, ConfigManagerTest.Worldgen::new);
		m.load();
		assertEquals(0.3, h.get().piglinParlor.chance, "set deliberately under v2 (after the v2 upgrade): not an old default");
	}

	@Test
	void v1FileGetsBothSteps(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("burmaldaholic.json");
		Files.writeString(file, "{\"worldgen\":{\"piglinParlor\":{\"chance\":0.3}},\"slots\":{\"overworld\":{\"freeSpins\":{\"awards\":[8,10,15]}}}}");
		ConfigManager m = new ConfigManager(file);
		ConfigHandle<ConfigManagerTest.Worldgen> w = m.register("worldgen", ConfigManagerTest.Worldgen.class, ConfigManagerTest.Worldgen::new);
		ConfigHandle<SlotsConfig> s = m.register("slots", SlotsConfig.class, SlotsConfig::new);
		m.load();
		assertEquals(0.6, w.get().piglinParlor.chance);
		assertArrayEquals(new int[] {10, 12, 19}, s.get().overworld.freeSpins.awards);
	}

	@Test
	void v3FileKeepsADeliberateOldSlotsLever(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("burmaldaholic.json");
		Files.writeString(file, "{\"configVersion\":3,\"slots\":{\"overworld\":{\"freeSpins\":{\"awards\":[8,10,15]}},\"nether\":{\"buy\":{\"price\":18.4}}}}");
		ConfigManager m = new ConfigManager(file);
		ConfigHandle<SlotsConfig> s = m.register("slots", SlotsConfig.class, SlotsConfig::new);
		m.load();
		assertArrayEquals(new int[] {8, 10, 15}, s.get().overworld.freeSpins.awards);
		assertEquals(18.4, s.get().nether.buy.price);
		m.load();
		assertArrayEquals(new int[] {8, 10, 15}, s.get().overworld.freeSpins.awards, "no double apply across restarts");
	}
}

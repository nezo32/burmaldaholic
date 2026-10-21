package dev.nezo.burmaldaholic.core.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nezo.burmaldaholic.core.config.sections.SlotsConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R3 (RTP ~98 %): a v0.1.x global file holding the old slot defaults gets the new levers; custom values are kept. */
class SlotsRetiredDefaultsTest {
	@Test
	void oldSlotDefaultsAreUpgradedCustomKept(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("burmaldaholic.json");
		Files.writeString(file, "{\"configVersion\":2,\"slots\":{\"overworld\":{\"freeSpins\":{\"awards\":[8,10,15]}},"
			+ "\"nether\":{\"freeSpins\":{\"awards\":[12,15,21]},\"buy\":{\"price\":18.4}},\"end\":{\"buy\":{\"price\":109.0},"
			+ "\"wheel\":{\"outer\":[\"10\",\"UP\",\"12\",\"15\",\"MINI\",\"10\",\"20\",\"12\",\"25\",\"10\",\"40\",\"15\",\"UP\",\"12\",\"20\",\"MINI\",\"15\",\"10\",\"75\",\"25\"]}}}}");
		ConfigManager m = new ConfigManager(file);
		ConfigHandle<SlotsConfig> h = m.register("slots", SlotsConfig.class, SlotsConfig::new);
		m.load();
		SlotsConfig c = h.get();
		assertArrayEquals(new int[] {10, 12, 19}, c.overworld.freeSpins.awards);
		assertArrayEquals(new int[] {12, 15, 21}, c.nether.freeSpins.awards, "a custom value is not an old default");
		assertEquals(20.8, c.nether.buy.price);
		assertEquals(107.2, c.end.buy.price);
		assertEquals("12", c.end.wheel.outer[0]);
		assertEquals(0.00025, c.overworld.jackpot.gem.get("mini"));
		m.load();
		assertArrayEquals(new int[] {10, 12, 19}, h.get().overworld.freeSpins.awards, "stable after the rewrite");
	}
}

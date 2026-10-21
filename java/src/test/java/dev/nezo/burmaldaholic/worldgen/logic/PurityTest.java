package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Everything under {@code worldgen/logic/**} is PURE: no Minecraft / Mojang / Fabric types. {@code PackGen}
 * runs at build time without the game, and the unit tests run without a bootstrap (architecture doc §2).
 */
class PurityTest {
	@Test
	void logicHasNoMinecraftImports() throws IOException {
		Path root = Path.of(System.getProperty("burmaldaholic.projectDir", "."))
			.resolve("src/main/java/dev/nezo/burmaldaholic/worldgen/logic");
		assertTrue(Files.isDirectory(root), root.toString());
		List<String> offenders = new ArrayList<>();
		int files = 0;
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
				files++;
				String src = Files.readString(p, StandardCharsets.UTF_8);
				for (String banned : new String[] {"net.minecraft.", "com.mojang.", "net.fabricmc."}) {
					if (src.contains(banned)) {
						offenders.add(root.relativize(p) + " uses " + banned);
					}
				}
			}
		}
		assertTrue(files > 20, "scanned " + files + " files");
		assertEquals(List.of(), offenders);
	}
}

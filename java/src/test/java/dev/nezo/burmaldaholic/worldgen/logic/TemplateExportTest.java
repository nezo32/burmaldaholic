package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Generator + FREEZE guard for the committed v0.1 data-pack files ({@code resources/resourcepacks/casinos}).
 * Old worlds reference these template / pool ids, so their bytes never change (architecture doc
 * casino-buildings.md §1). v0.2 content is generated at build time by {@code PackGen} under new ids.
 *
 * <pre>
 *   BURMALDAHOLIC_EXPORT_STRUCTURES=1 ./gradlew test --tests '*TemplateExportTest'   # (re)write the files
 *   ./gradlew test                                                                  # fails if they drifted
 * </pre>
 */
class TemplateExportTest {
	private static final Path PACK = Path.of("src/main/resources").resolve(TemplateExporter.PACK_ROOT);

	/** The committed files: every legacy layout's template + single-element pool. */
	static Map<String, byte[]> legacyFiles() {
		Map<String, byte[]> out = new LinkedHashMap<>();
		for (Layout l : Layouts.legacy().values()) {
			out.put(TemplateExporter.templateFile(l), Nbt.writeGzip(TemplateExporter.template(l)));
			out.put(TemplateExporter.poolFile(l), TemplateExporter.poolJson(l).getBytes(StandardCharsets.UTF_8));
		}
		return out;
	}

	@Test
	void committedFilesMatchLayouts() throws IOException {
		boolean export = "1".equals(System.getenv("BURMALDAHOLIC_EXPORT_STRUCTURES"));
		for (Map.Entry<String, byte[]> file : legacyFiles().entrySet()) {
			Path path = PACK.resolve(file.getKey());
			if (export) {
				Files.createDirectories(path.getParent());
				Files.write(path, file.getValue());
			}
			assertTrue(Files.exists(path), path + " is missing — run with BURMALDAHOLIC_EXPORT_STRUCTURES=1");
			assertArrayEquals(file.getValue(), Files.readAllBytes(path),
				path + " differs from logic/Layouts — run with BURMALDAHOLIC_EXPORT_STRUCTURES=1");
		}
	}

	/** The committed tree holds exactly the legacy files: v0.2 content is generated, never committed (D2). */
	@Test
	void committedTreeHoldsOnlyLegacyFiles() throws IOException {
		Set<String> onDisk = new TreeSet<>();
		try (Stream<Path> walk = Files.walk(PACK)) {
			walk.filter(Files::isRegularFile).forEach(p -> onDisk.add(PACK.relativize(p).toString().replace('\\', '/')));
		}
		assertEquals(new TreeSet<>(legacyFiles().keySet()), onDisk);
	}

	/** Frozen ids and sizes of the v0.1 templates (old saves and CasinoIndex records reference them). */
	@Test
	void legacyIdsAndSizesAreFrozen() {
		Map<String, Vec> expected = new LinkedHashMap<>();
		for (VillageStyle s : VillageStyle.values()) {
			expected.put("village_casino_" + s.id(), new Vec(17, 10, 17));
		}
		expected.put("piglin_parlor", new Vec(21, 12, 21));
		expected.put("high_roller_lounge", new Vec(15, 9, 15));
		Map<String, Vec> actual = new LinkedHashMap<>();
		Layouts.legacy().forEach((id, l) -> actual.put(id, l.size()));
		assertEquals(expected, actual);
	}

	@Test
	void outputIsDeterministic() {
		Map<String, byte[]> a = legacyFiles();
		Map<String, byte[]> b = legacyFiles();
		assertTrue(a.keySet().equals(b.keySet()));
		a.forEach((k, v) -> assertArrayEquals(v, b.get(k), k));
	}
}

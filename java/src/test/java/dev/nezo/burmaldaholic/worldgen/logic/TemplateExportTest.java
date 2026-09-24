package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Generator + drift guard for the committed data-pack files ({@code resources/resourcepacks/casinos}).
 *
 * <pre>
 *   BURMALDAHOLIC_EXPORT_STRUCTURES=1 ./gradlew test --tests '*TemplateExportTest'   # (re)write the files
 *   ./gradlew test                                                                  # fails if they drifted
 * </pre>
 */
class TemplateExportTest {
	private static final Path PACK = Path.of("src/main/resources").resolve(TemplateExporter.PACK_ROOT);

	@Test
	void committedFilesMatchLayouts() throws IOException {
		boolean export = "1".equals(System.getenv("BURMALDAHOLIC_EXPORT_STRUCTURES"));
		for (Map.Entry<String, byte[]> file : TemplateExporter.files().entrySet()) {
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

	@Test
	void outputIsDeterministic() {
		Map<String, byte[]> a = TemplateExporter.files();
		Map<String, byte[]> b = TemplateExporter.files();
		assertTrue(a.keySet().equals(b.keySet()));
		a.forEach((k, v) -> assertArrayEquals(v, b.get(k), k));
	}
}

package dev.nezo.burmaldaholic.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every key of docs/design/CONFIG.md exists in the registered sections with the documented default,
 * and the defaults bind without a single issue.
 */
class ConfigSpecCoverageTest {
	private static final Pattern ROW = Pattern.compile("^\\|\\s*`([^`]+)`\\s*\\|([^|]*)\\|([^|]*)\\|");
	/** Edition-specific default, e.g. {@code 24 (Java) / 12 (Bedrock)} (spaces already removed). */
	private static final Pattern EDITION_DEFAULT = Pattern.compile("^(.+?)\\(Java\\)/(.+?)\\(Bedrock\\)$");

	private static ConfigManager manager() {
		ConfigManager m = new ConfigManager(null);
		CasinoConfig.registerAll(m);
		// sections of modules added after core's list register themselves (ctx.config); same defaults here
		m.register("baccarat", dev.nezo.burmaldaholic.games.baccarat.BaccaratConfig.class, dev.nezo.burmaldaholic.games.baccarat.BaccaratConfig::new);
		return m;
	}

	@Test
	void everyDocumentedKeyExistsWithItsDefault() throws Exception {
		Path spec = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "..", "docs", "design", "CONFIG.md");
		ConfigManager m = manager();
		JsonObject defaults = m.defaultsJson();
		List<String> missing = new ArrayList<>();
		List<String> wrong = new ArrayList<>();
		for (String line : Files.readAllLines(spec)) {
			Matcher r = ROW.matcher(line);
			if (!r.find()) {
				continue;
			}
			String key = r.group(1);
			String type = r.group(2).trim();
			String def = r.group(3).trim().replace(" ", "");
			Matcher edition = EDITION_DEFAULT.matcher(def);
			if (edition.matches()) {
				def = edition.group(1);
			}
			if (key.equals("core.casinoMode")) {
				continue; // per-world saved data (data/burmaldaholic/mode.dat), not in the file
			}
			if (key.contains("<")) {
				String family = key.substring(0, key.indexOf(".<"));
				if (ConfigManager.at(defaults, family) == null) {
					missing.add(key);
				}
				continue;
			}
			var value = ConfigManager.at(defaults, key);
			if (value == null) {
				missing.add(key);
				continue;
			}
			if (type.equals("bool") || type.equals("int") || type.equals("long") || type.equals("double")) {
				String actual = value.getAsJsonPrimitive().getAsString();
				boolean same = type.equals("bool") ? actual.equals(def)
					: new java.math.BigDecimal(actual).compareTo(new java.math.BigDecimal(def)) == 0;
				if (!same) {
					wrong.add(key + " default " + actual + " != spec " + def);
				}
			}
		}
		assertTrue(missing.isEmpty(), "Keys missing from core/config/sections: " + missing);
		assertTrue(wrong.isEmpty(), "Defaults differ from CONFIG.md: " + wrong);
	}

	@Test
	void defaultsBindCleanly() {
		ConfigManager m = manager();
		List<ConfigIssue> issues = m.applyJson(m.defaultsJson());
		assertEquals(List.of(), issues);
	}
}

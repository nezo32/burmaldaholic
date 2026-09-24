package dev.nezo.burmaldaholic.core.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotLines;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Bot names and quips against the lang fragments (BOTS.md §7.1, §11.3, §11.4, §12.6). */
class BotNamesTest {
	private static JsonObject lang(String code) throws IOException {
		Path root = Path.of(System.getProperty("burmaldaholic.projectDir", "."));
		return JsonParser.parseString(Files.readString(root.resolve("src/main/lang/bots/" + code + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	@ParameterizedTest
	@ValueSource(strings = {"en_us", "ru_ru"})
	void everyNameIdIsTranslated(String code) throws IOException {
		JsonObject l = lang(code);
		assertEquals(32, BotRoster.all().size());
		assertEquals(32, new HashSet<>(BotRoster.all()).size(), "unique ids");
		for (String id : BotRoster.all()) {
			assertTrue(l.has(BotRoster.nameKey(id)), code + " lacks " + id);
			assertTrue(id.matches("[a-z0-9_]+"), "stable ASCII id " + id);
		}
		long inLang = l.keySet().stream().filter(k -> k.startsWith("gui.burmaldaholic.bots.name.")).count();
		assertEquals(32, inLang, "no name key without an id in the roster");
	}

	@ParameterizedTest
	@ValueSource(strings = {"en_us", "ru_ru"})
	void quipVariantCountsMatchTheLang(String code) throws IOException {
		JsonObject l = lang(code);
		Pattern p = Pattern.compile("dialog\\.burmaldaholic\\.bots\\.([a-z_]+)\\.(\\d+)");
		java.util.Map<String, Integer> found = new java.util.HashMap<>();
		for (String k : l.keySet()) {
			var m = p.matcher(k);
			if (m.matches()) {
				found.merge(m.group(1), 1, Integer::sum);
			}
		}
		assertEquals(BotLines.VARIANTS, found);
		for (String e : BotLines.events()) {
			for (int v = 1; v <= BotLines.variants(e); v++) {
				assertTrue(l.has(BotLines.key(e, v)), code + " " + BotLines.key(e, v));
			}
		}
	}

	@Test
	void legacyNamesAndProfiles() {
		assertEquals("diamond_dora", BotRoster.canonical("diamond_dave"));
		assertEquals("diamond_dora", BotRoster.canonical("Diamond Dave"));
		assertEquals("creeper42", BotRoster.canonical("creeper42"));
		assertNull(BotRoster.canonical("some_celebrity"));
		BotProfile p = BotRoster.create(BotRng.seeded(5), BotDifficulty.HARD, new int[] {1, 1, 1}, BotRoster.Theme.PIGLIN, Set.of(), true);
		assertTrue(BotRoster.PIGLIN.contains(p.nameId()), "themed pool first");
		assertEquals(BotDifficulty.HARD, p.level());
		assertTrue(p.key().startsWith("bot:b") && p.id().length() == 8);
	}

	@Test
	void tenThousandFillsNeverRepeatANameAtOneTable() {
		BotRng rng = BotRng.seeded(99);
		for (int fill = 0; fill < 10_000; fill++) {
			Set<String> used = new HashSet<>();
			for (int i = 0; i < 7; i++) {
				BotProfile b = BotRoster.create(rng, BotDifficulty.MIXED, new int[] {30, 50, 20}, BotRoster.Theme.values()[fill % 3], used, true);
				assertFalse(used.contains(b.nameId()));
				assertTrue(b.level() != BotDifficulty.MIXED);
				used.add(b.nameId());
			}
		}
	}

	@Test
	void stakeGateMixNeverDrawsEasy() {
		BotRng rng = BotRng.seeded(11);
		int[] counts = new int[3];
		for (int i = 0; i < 10_000; i++) {
			counts[BotDifficulty.MIXED.pick(rng, new int[] {0, 45, 55}).ordinal()]++;
		}
		assertEquals(0, counts[0]);
		List<Integer> micro = List.of(45, 45, 10);
		int[] m = new int[3];
		for (int i = 0; i < 100_000; i++) {
			m[BotDifficulty.MIXED.pick(rng, new int[] {45, 45, 10}).ordinal()]++;
		}
		for (int k = 0; k < 3; k++) {
			assertEquals(micro.get(k) / 100.0, m[k] / 100_000.0, 0.015, "Micro mix 45/45/10 ± 1.5 %");
		}
	}
}

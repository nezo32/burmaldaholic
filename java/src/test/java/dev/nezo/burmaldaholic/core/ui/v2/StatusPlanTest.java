package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Review T-K2 D1 (R3): at the owner's 427 × 240 the status lost words as a PAIR — seated "Сначала сделайте ставку" (line 1)
 * in a 67-px column with line 2 in the strip, acting "Ставка 0 · рука 1 из 1" (line 2) in a 78-px column with no strip.
 * {@link StatusPlan} places the pair; DockLayout opens the strip when the column is under {@link StatusPlan#want}. Here
 * EVERY status / reason string of every game, EN and RU, as line 1 and as line 2 next to the kit's own lines, at every
 * column width the T1 Dock can give (down to its minimum), keeps every word readable.
 */
class StatusPlanTest {
	static final ToIntFunction<String> FONT = StatusFitTest.FONT;
	private static final Pattern STATUS_KEY = Pattern.compile("\\.v2\\.(reason|status)\\.");
	private static final Pattern ARG = Pattern.compile("%(\\d\\$)?s");

	/** T1 427 × 240: the hint strip spans the Dock minus its margins; the status column is 28 px high (two rows). */
	private static final TableGrid T1 = TableGrid.of(427, 240);
	private static final int STRIP_W = T1.dock.w() - 2 * T1.m();
	private static final int BOX_H = T1.dock.h() - 8;

	private static StatusPlan placed(String l1, String l2, int boxW) {
		int want = StatusPlan.want(l1, l2, FONT);
		int strip = boxW < want ? STRIP_W : 0; // DockLayout.hintStrip
		return StatusPlan.of(l1, l2, boxW, BOX_H, strip, FONT);
	}

	private static String lost(String l1, String l2, int boxW) {
		int want = StatusPlan.want(l1, l2, FONT);
		int strip = boxW < want ? STRIP_W : 0;
		return placed(l1, l2, boxW).lost(l1, l2, boxW, strip, FONT);
	}

	@Test
	void theOwnersTwoReportedPairsFit() {
		// seated, no bet: line 1 needs two rows of a 67-px column while line 2 goes up → both go into the strip
		assertEquals("", lost("Сначала сделайте ставку", "Фишки → ставка", 67));
		// acting: line 2 is wider than the 78-px column → it goes into the strip, line 1 stays
		StatusPlan acting = placed("Ваш ход", "Ставка 0 · рука 1 из 1", 78);
		assertEquals(List.of("Ваш ход"), acting.dock());
		assertEquals("Ставка 0 · рука 1 из 1", acting.stripL2());
		assertEquals("", lost("Ваш ход", "Ставка 0 · рука 1 из 1", 78));
	}

	@Test
	void rules() {
		StatusPlan both = placed("Your turn", "Bet 50", 100);
		assertEquals(List.of("Your turn", "Bet 50"), both.dock());
		assertNull(both.stripText());
		StatusPlan wrapped = placed("Place a bet first please", null, 90);
		assertEquals(2, wrapped.dock().size(), "a lone line wraps onto the column's two rows");
		StatusPlan up = placed("A very long lone status line that needs much more room", null, 60);
		assertTrue(up.dock().isEmpty());
		assertEquals("A very long lone status line that needs much more room", up.stripL1());
		assertEquals(StatusPlan.EMPTY, placed(null, null, 80));
		// an overflowing Dock leaves no column: the text still shows, in the strip (T-K2 D4)
		StatusPlan none = StatusPlan.of("Pick the stakes", "Balance 12 500", 0, 28, STRIP_W, FONT);
		assertEquals("Pick the stakes", none.stripL1());
		assertEquals("Balance 12 500", none.stripL2());
	}

	@Test
	void everyStatusStringFitsAsAPairAtTheOwnersSize() throws IOException {
		Map<String, List<String>> byLang = strings();
		List<String> bad = new ArrayList<>();
		for (var e : byLang.entrySet()) {
			List<String> all = e.getValue();
			// the pairs the kit makes: a game / kit line 1 with the kit's line-2 hint (status.*), and a kit reason (the
			// primary's, a flash, the watchdog) as line 1 over a game's line 2
			List<String> kitHints = new ArrayList<>(), kitReasons = new ArrayList<>();
			for (String s : all) {
				if (s.startsWith("core|status|")) kitHints.add(text(s));
				if (s.startsWith("core|reason|")) kitReasons.add(text(s));
			}
			for (String tagged : all) {
				String s = text(tagged);
				for (int w = DockLayout.minStatus(Tier.T1); w <= 200; w += 4) {
					check(bad, e.getKey(), s, null, w);
					check(bad, e.getKey(), null, s, w);
					for (String h : kitHints) check(bad, e.getKey(), s, h, w);
					for (String r : kitReasons) check(bad, e.getKey(), r, s, w);
				}
			}
		}
		assertTrue(bad.isEmpty(), bad.size() + " status pairs lose words at 427 x 240, e.g. " + bad.subList(0, Math.min(8, bad.size())));
	}

	private static void check(List<String> bad, String lang, String l1, String l2, int w) {
		String miss = lost(l1, l2, w);
		if (!miss.isEmpty()) bad.add(lang + " col " + w + ": '" + l1 + "' | '" + l2 + "': " + miss);
	}

	private static String text(String tagged) {
		return tagged.substring(tagged.lastIndexOf('|') + 1);
	}

	/** Every .v2.reason / .v2.status string, per language, tagged "module|reason or status|text" (arguments as 99). */
	private static Map<String, List<String>> strings() throws IOException {
		Path lang = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/main/lang");
		Map<String, List<String>> out = new TreeMap<>();
		List<Path> files;
		try (Stream<Path> s = Files.walk(lang)) {
			files = s.filter(p -> p.getFileName().toString().equals("en_us.json") || p.getFileName().toString().equals("ru_ru.json")).sorted().toList();
		}
		for (Path f : files) {
			String module = f.getParent().getFileName().toString();
			String code = f.getFileName().toString().replace(".json", "");
			JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
			for (var e : o.entrySet()) {
				var m = STATUS_KEY.matcher(e.getKey());
				if (!m.find()) continue;
				out.computeIfAbsent(code, k -> new ArrayList<>()).add(module + "|" + m.group(1) + "|" + ARG.matcher(e.getValue().getAsString()).replaceAll("99"));
			}
		}
		return out;
	}
}

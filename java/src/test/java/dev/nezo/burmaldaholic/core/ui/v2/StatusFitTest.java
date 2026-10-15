package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Status lines never lose words (review T-K2 D1): a long status wraps onto two 1× lines and an overflow ends with "…";
 * and every status / reason string of the kit, EN and RU, fits in two lines of the status width the Dock gives it — or
 * in the hint strip the Dock opens above it when the status column is too narrow — at T1
 * (427 × 240, the owner's 4K GUI Auto) and T2 (480 × 270). Game strings that are still too long are listed in
 * {@link #GAME_OVERLONG} (owned by the game packages, who remove their entries as they shorten them); a NEW over-long
 * string fails.
 */
class StatusFitTest {
	/** A vanilla-like advance width: 6 px, space 4, narrow glyphs 2–5 (Cyrillic is 6 like Latin capitals). */
	static final ToIntFunction<String> FONT = s -> {
		int w = 0;
		for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
			int c = s.codePointAt(i);
			w += c == ' ' ? 4 : "i!.,:;|'".indexOf(c) >= 0 ? 2 : "l`".indexOf(c) >= 0 ? 3 : "tI[]".indexOf(c) >= 0 ? 4 : "fk<>\"()*{}".indexOf(c) >= 0 ? 5 : 6;
		}
		return w;
	};

	private static final Pattern STATUS_KEY = Pattern.compile("\\.v2\\.(reason|status)\\.");
	private static final Pattern ARG = Pattern.compile("%(\\d\\$)?s");

	/** Game status / reason strings over two T1 lines today (lang file, key); the game packages shorten them. */
	static final Set<String> GAME_OVERLONG = Set.of(
		"blackjack/ru_ru gui.burmaldaholic.blackjack.v2.reason.cannot_double",
		"blackjack/ru_ru gui.burmaldaholic.blackjack.v2.status.even_money",
		"craps/ru_ru gui.burmaldaholic.craps.v2.reason.need_line",
		"craps/ru_ru gui.burmaldaholic.craps.v2.reason.no_shooter",
		"craps/ru_ru gui.burmaldaholic.craps.v2.reason.point_box",
		"craps/ru_ru gui.burmaldaholic.craps.v2.reason.point_box_come_out",
		"craps/ru_ru gui.burmaldaholic.craps.v2.reason.odds_need_point",
		"craps/ru_ru gui.burmaldaholic.craps.v2.reason.odds_need_pass",
		"craps/ru_ru gui.burmaldaholic.craps.v2.reason.already_placed",
		"craps/ru_ru gui.burmaldaholic.craps.v2.reason.come_needs_point",
		"craps/en_us gui.burmaldaholic.craps.v2.reason.already_placed",
		"poker/ru_ru gui.burmaldaholic.poker.v2.reason.buy_in_min",
		"poker/ru_ru gui.burmaldaholic.poker.v2.reason.no_top_up",
		"poker/ru_ru gui.burmaldaholic.poker.v2.status.stepper",
		"poker/ru_ru gui.burmaldaholic.poker.v2.status.watching",
		"poker/en_us gui.burmaldaholic.poker.v2.reason.no_top_up",
		"poker/en_us gui.burmaldaholic.poker.v2.status.stepper",
		"poker/en_us gui.burmaldaholic.poker.v2.status.watching",
		"uth/ru_ru gui.burmaldaholic.uth.v2.status.bank_form",
		"uth/en_us gui.burmaldaholic.uth.v2.reason.you_bank",
		"uth/en_us gui.burmaldaholic.uth.v2.status.bank_form");

	@Test
	void wrapKeepsEveryWordOrEndsWithAnEllipsis() {
		assertArrayEquals(new String[] {"Short"}, TextFit.wrap2("Short", 100, FONT));
		String[] two = TextFit.wrap2("Выберите место или нажмите Пробел", 90, FONT);
		assertEquals(2, two.length);
		assertTrue(two[1].endsWith(TextFit.ELLIPSIS), "an overflow ends with an ellipsis: " + String.join(" / ", two));
		assertTrue(FONT.applyAsInt(two[0]) <= 90 && FONT.applyAsInt(two[1]) <= 90);
		String[] fits = TextFit.wrap2("Сначала сделайте ставку", 90, FONT);
		assertEquals("Сначала сделайте ставку", String.join(" ", fits), "nothing dropped: " + String.join(" / ", fits));
		String[] longWord = TextFit.wrap2("Сверхдлинноеслово и ещё", 40, FONT);
		assertTrue(FONT.applyAsInt(longWord[0]) <= 40);
	}

	/**
	 * Where {@code text} does not fit in the kit's Docks at T1 and T2 (betting and actions, EN and RU labels), or null: it
	 * must wrap into two lines of the status box, or — when the Dock opened the hint strip for it — fit the strip.
	 */
	static String misfit(String text) {
		int want = TextFit.twoLineWidth(text, FONT) + 2;
		for (int[] wh : new int[][] {{427, 240}, {480, 270}}) {
			TableGrid g = TableGrid.of(wh[0], wh[1]);
			for (String[] l : new String[][] {{"Bet", "Sit down", "Hit", "Stand", "Double"}, {"Ставка", "Сесть", "Ещё", "Хватит", "Удвоить"}}) {
				int readout = Math.max(FONT.applyAsInt(l[0]), 2 * FONT.applyAsInt("12 500"));
				int readoutC = Math.max(FONT.applyAsInt(l[0]), FONT.applyAsInt("12 500"));
				int primary = FONT.applyAsInt(l[1]) + 16 + 5 + FONT.applyAsInt("Пробел") + 6;
				int primaryC = FONT.applyAsInt(l[1]) + 16;
				int[] full = new int[3];
				int[] compact = new int[3];
				for (int i = 0; i < 3; i++) {
					compact[i] = FONT.applyAsInt(l[2 + i]) + 16;
					full[i] = compact[i] + 5 + 6 + 6;
				}
				for (DockLayout.Dock d : List.of(DockLayout.layout(g, DockLayout.Spec.betting(5, 4, readout, readoutC, 0, primary, primaryC).withStatus(want), 2),
					DockLayout.layout(g, DockLayout.Spec.actions(full, compact, primary, primaryC).withStatus(want)))) {
					boolean ok = TextFit.fitsTwoLines(text, d.status().w(), FONT)
						|| (!d.hintStrip().isEmpty() && FONT.applyAsInt(text) <= d.hintStrip().w() - 8);
					if (!ok) return wh[0] + "x" + wh[1] + " status " + d.status().w() + " px, strip " + d.hintStrip().w() + ": "
						+ String.join(" / ", TextFit.wrap2(text, d.status().w(), FONT));
				}
			}
		}
		return null;
	}

	@Test
	void everyStatusAndReasonFitsTwoLinesAtT1AndT2() throws IOException {
		Path lang = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/main/lang");
		List<String> kit = new ArrayList<>();
		Set<String> game = new TreeSet<>();
		List<Path> files;
		try (Stream<Path> s = Files.walk(lang)) {
			files = s.filter(p -> p.getFileName().toString().equals("en_us.json") || p.getFileName().toString().equals("ru_ru.json")).sorted().toList();
		}
		for (Path f : files) {
			String module = f.getParent().getFileName().toString();
			String code = f.getFileName().toString().replace(".json", "");
			JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, com.google.gson.JsonElement> e : o.entrySet()) {
				if (!STATUS_KEY.matcher(e.getKey()).find()) continue;
				String text = ARG.matcher(e.getValue().getAsString()).replaceAll("99");
				String miss = misfit(text);
				if (miss == null) continue;
				String id = module + "/" + code + " " + e.getKey();
				String why = id + " " + miss;
				if (module.equals("core")) kit.add(why);
				else if (!GAME_OVERLONG.contains(id)) game.add(why);
			}
		}
		assertEquals(List.of(), kit, "kit status / reason strings over two lines at T1/T2");
		assertEquals(Set.of(), game, "NEW game status / reason strings over two lines at T1/T2 (shorten them)");
	}
}

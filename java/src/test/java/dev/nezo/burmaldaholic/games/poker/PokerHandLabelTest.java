package dev.nezo.burmaldaholic.games.poker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.games.poker.logic.Hand;
import dev.nezo.burmaldaholic.games.poker.logic.HandEvaluator;
import dev.nezo.burmaldaholic.games.poker.logic.PokerRng;
import dev.nezo.burmaldaholic.games.poker.logic.PokerTable;
import dev.nezo.burmaldaholic.games.poker.logic.Pots;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/** Specific hand names on the plates ("Three kings", «Три короля»): every key they use exists in EN and RU. */
class PokerHandLabelTest {
	private static int value(int cat, int... r) {
		int v = cat;
		for (int x : r) v = v << 4 | x;
		return v;
	}

	private static void keys(Component c, List<String> out) {
		if (c.getContents() instanceof TranslatableContents t) {
			out.add(t.getKey());
			for (Object a : t.getArgs()) if (a instanceof Component ac) keys(ac, out);
		}
		for (Component s : c.getSiblings()) keys(s, out);
	}

	@Test
	void namesResolveInBothLanguages() throws Exception {
		Path root = Path.of(System.getProperty("burmaldaholic.projectDir", "."));
		JsonObject en = JsonParser.parseString(Files.readString(root.resolve("src/main/lang/poker/en_us.json"))).getAsJsonObject();
		JsonObject ru = JsonParser.parseString(Files.readString(root.resolve("src/main/lang/poker/ru_ru.json"))).getAsJsonObject();
		List<String> keys = new ArrayList<>();
		for (int r = 2; r <= 14; r++) {
			int k = r == 2 ? 3 : 2;
			keys(PokerText.handLabel(value(HandEvaluator.HIGH_CARD, r, k, k, k, k)), keys);
			keys(PokerText.handLabel(value(HandEvaluator.PAIR, r, r, k, k, k)), keys);
			keys(PokerText.handLabel(value(HandEvaluator.TWO_PAIR, r, r, k, k, 4)), keys);
			keys(PokerText.handLabel(value(HandEvaluator.THREE_OF_A_KIND, r, r, r, k, 4)), keys);
			keys(PokerText.handLabel(value(HandEvaluator.STRAIGHT, r, 0, 0, 0, 0)), keys);
			keys(PokerText.handLabel(value(HandEvaluator.FLUSH, r, k, k, k, k)), keys);
			keys(PokerText.handLabel(value(HandEvaluator.FULL_HOUSE, r, r, r, k, k)), keys);
			keys(PokerText.handLabel(value(HandEvaluator.FOUR_OF_A_KIND, r, r, r, r, k)), keys);
			keys(PokerText.handLabel(value(HandEvaluator.STRAIGHT_FLUSH, r, 0, 0, 0, 0)), keys);
		}
		for (String key : keys) {
			assertTrue(en.has(key), "en_us: " + key);
			assertTrue(ru.has(key), "ru_ru: " + key);
		}
		// "Three kings" / «Три короля»
		List<String> kings = new ArrayList<>();
		keys(PokerText.handLabel(value(HandEvaluator.THREE_OF_A_KIND, 13, 13, 13, 9, 4)), kings);
		assertEquals(List.of("gui.burmaldaholic.poker.hand_named.three_of_a_kind", "gui.burmaldaholic.poker.rank.k.three"), kings);
		assertEquals("Three %s", en.get(kings.get(0)).getAsString());
		assertEquals("короля", ru.get(kings.get(1)).getAsString());
		// a royal flush keeps its category name
		List<String> royal = new ArrayList<>();
		keys(PokerText.handLabel(value(HandEvaluator.STRAIGHT_FLUSH, 14, 13, 12, 11, 10)), royal);
		assertEquals(List.of("gui.burmaldaholic.poker.hand.royal_flush"), royal);
	}

	/** v0.1.1: the chat result lines name the hand as the plates do ("Pair of sixes"), not only its category. */
	@Test
	void chatResultLinesUseTheSpecificHandNames() {
		int checked = 0;
		for (int seed = 1; seed <= 40; seed++) {
			PokerTable t = new PokerTable(6, 10, new Pots.RakeConfig(0.05, 3, true));
			t.addHuman("a", "A", 1000);
			t.addHuman("b", "B", 1000);
			Hand h = t.startHand(PokerRng.of(new SplittableRandom(seed)));
			for (int guard = 0; guard < 20 && !h.complete(); guard++) h.apply(h.legal().toCall() > 0 ? Hand.Action.call() : Hand.Action.check());
			if (h.result() == null || h.result().uncontested()) continue;
			int value = h.result().pots().getFirst().value();
			List<String> expected = new ArrayList<>();
			keys(PokerText.handLabel(value), expected);
			List<String> lines = new ArrayList<>();
			for (Component c : PokerText.resultLines(t, h)) keys(c, lines);
			assertTrue(lines.containsAll(expected), "seed " + seed + ": " + lines + " lacks " + expected);
			if (!"royal_flush".equals(HandEvaluator.handName(value))) {
				assertTrue(lines.stream().anyMatch(k -> k.startsWith("gui.burmaldaholic.poker.hand_named.")), "seed " + seed + ": " + lines);
				assertTrue(lines.stream().noneMatch(k -> k.startsWith("gui.burmaldaholic.poker.hand.")), "seed " + seed + ": category name only " + lines);
			}
			checked++;
		}
		assertTrue(checked > 10, "showdowns checked: " + checked);
	}
}

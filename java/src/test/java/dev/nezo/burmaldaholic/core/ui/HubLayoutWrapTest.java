package dev.nezo.burmaldaholic.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** PvP hub card names wrap at spaces and hyphens, never inside a word (final-rc visual pass: "Head-to-he / ad"). */
class HubLayoutWrapTest {
	/** 6 px per character, like the default font's average. */
	private static int w(String s) {
		return s.length() * 6;
	}

	@Test
	void breaksAfterAHyphenInsteadOfInsideTheWord() {
		assertEquals(List.of("Head-to-", "head"), HubLayout.wrapName("Head-to-head", 60, HubLayoutWrapTest::w));
	}

	@Test
	void breaksAtSpacesAndKeepsShortNamesOnOneLine() {
		assertEquals(List.of("Coin Flip", "Duel"), HubLayout.wrapName("Coin Flip Duel", 60, HubLayoutWrapTest::w));
		assertEquals(List.of("Wheel Party"), HubLayout.wrapName("Wheel Party", 80, HubLayoutWrapTest::w));
	}

	@Test
	void aWordWiderThanTheCardStaysWhole() {
		assertEquals(List.of("Scratch", "Showdown"), HubLayout.wrapName("Scratch Showdown", 30, HubLayoutWrapTest::w));
	}
}

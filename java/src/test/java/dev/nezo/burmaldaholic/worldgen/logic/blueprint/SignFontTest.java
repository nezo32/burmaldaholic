package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Sign lettering stays readable: letters never touch, I has serifs, N shows its diagonal (lane CB-3). */
class SignFontTest {
	static final List<String> WORDS = List.of("CASINO", "LUCKY", "BET", "HIGH ROLLER", "PARLOR", "VIP", "WIN");
	static final int[] WIDTHS = {13, 17, 21, 25, 29, 31, 33};

	@Test
	void everyLayoutKeepsAGapBetweenLetters() {
		for (String word : WORDS) {
			for (int w : WIDTHS) {
				SignFont.Plan p = SignFont.plan(word, w).orElse(null);
				if (p == null) {
					continue;
				}
				assertTrue(p.width() <= w, word + " @" + w);
				// per line (rows of the same line share a line index), letter column ranges must be >= 1 apart
				int lineRows = p.font().rows() + SignFont.LINE_GAP;
				Map<Integer, int[]> span = new TreeMap<>();
				for (Layouts.SignPixel px : p.pixels()) {
					int[] s = span.computeIfAbsent(px.letter(), k -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE, px.row() / lineRows});
					s[0] = Math.min(s[0], px.col());
					s[1] = Math.max(s[1], px.col());
				}
				int[] prev = null;
				for (int[] s : span.values()) {
					if (prev != null && prev[2] == s[2]) {
						assertTrue(s[0] - prev[1] >= 2, word + " @" + w + ": letters touch (" + prev[1] + " / " + s[0] + ")");
					}
					prev = s;
				}
				assertTrue(p.pixels().stream().allMatch(px -> px.col() >= 0 && px.col() < p.width() && px.row() < p.height()));
			}
		}
	}

	@Test
	void casinoPicksTheBiggestReadableLayout() {
		assertEquals(SignFont.LARGE, SignFont.plan("CASINO", 31).orElseThrow().font());
		SignFont.Plan narrow = SignFont.plan("CASINO", 13).orElseThrow();
		assertEquals(SignFont.SMALL, narrow.font());
		assertEquals(List.of("CAS", "INO"), narrow.lines(), "17-wide annex front (13 inside the frame) uses two lines");
		assertFalse(SignFont.plan("CASINO", 8).isPresent());
	}

	@Test
	void serifIAndDiagonalN() {
		for (SignFont f : SignFont.values()) {
			String[] i = f.glyph('I');
			assertEquals("###", i[0], f + " I top serif");
			assertEquals("###", i[i.length - 1], f + " I bottom serif");
			String[] n = f.glyph('N');
			assertTrue(n[0].length() >= 4, f + " N wide enough for a diagonal");
			// the diagonal: a lit cell strictly inside the glyph on an upper row and further right on a lower row
			int upper = n[1].indexOf('#', 1), lower = n[n.length - 3].lastIndexOf('#', n[0].length() - 2);
			assertTrue(upper > 0 && lower > upper, f + " N diagonal");
			assertFalse(java.util.Arrays.equals(n, f.glyph('O')));
		}
	}
}

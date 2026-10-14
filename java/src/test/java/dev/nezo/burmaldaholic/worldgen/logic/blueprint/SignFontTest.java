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

	/** CR-1: a 1-wide I (a serif I reads as T / box from afar) and a true stem-to-stem diagonal in N ("CASIO" bug). */
	@Test
	void oneWideIAndDiagonalN() {
		for (SignFont f : SignFont.values()) {
			String[] i = f.glyph('I');
			for (String row : i) {
				assertEquals("#", row, f + " I is one stroke");
			}
			String[] n = f.glyph('N');
			int w = n[0].length();
			assertEquals(5, w, f + " N is 5 wide");
			int prev = 0;
			for (int r = 1; r < n.length - 1; r++) {
				assertEquals('#', n[r].charAt(0), f + " N left stem");
				assertEquals('#', n[r].charAt(w - 1), f + " N right stem");
				String inner = n[r].substring(1, w - 1);
				assertEquals(inner.indexOf('#'), inner.lastIndexOf('#'), f + " N row " + r + " has one diagonal cell");
				int c = inner.indexOf('#') + 1;
				assertTrue(c >= prev, f + " N diagonal runs down to the right");
				prev = c;
			}
			assertEquals(1, n[1].indexOf('#', 1), f + " N diagonal starts at the left stem");
			assertEquals(w - 2, n[n.length - 2].lastIndexOf('#', w - 2), f + " N diagonal ends at the right stem");
			assertFalse(java.util.Arrays.equals(n, f.glyph('O')));
		}
	}

	private static String render(SignFont.Plan p) {
		char[][] g = new char[p.height()][p.width()];
		for (char[] row : g) {
			java.util.Arrays.fill(row, '.');
		}
		for (Layouts.SignPixel px : p.pixels()) {
			g[px.row()][px.col()] = '#';
		}
		StringBuilder sb = new StringBuilder();
		for (char[] row : g) {
			sb.append(row).append('\n');
		}
		return sb.toString();
	}

	/** Golden render of the flagship word: every glyph and every glyph pair, one blank column apart, 23 wide (odd). */
	@Test
	void casinoRendersExactly() {
		SignFont.Plan p = SignFont.SMALL.line("CASINO");
		assertEquals(23, p.width(), "odd width: centres on the facade axis");
		assertEquals("""
			###.###.###.#.#...#.###
			#...#.#.#...#.##..#.#.#
			#...###.###.#.#.#.#.#.#
			#...#.#...#.#.#..##.#.#
			###.#.#.###.#.#...#.###
			""", render(p));
	}
}

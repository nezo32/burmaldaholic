package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Sign lettering stays readable: letters never touch, I has serifs, N shows its diagonal (lane CB-3). */
public class SignFontTest {
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

	/**
	 * The expected 7-row masks of the flagship word (VQA r2 spec: solid 1-block strokes; N = full stems at cols 0 and 4
	 * plus the diagonal (1,1)(1,2)(2,3)(3,4)(3,5)). Written out here independently of the font table.
	 */
	public static final Map<Character, String[]> CASINO_LARGE = Map.of(
		'C', new String[] {".##", "#..", "#..", "#..", "#..", "#..", ".##"},
		'A', new String[] {".#.", "#.#", "#.#", "###", "#.#", "#.#", "#.#"},
		'S', new String[] {".###", "#...", "#...", ".##.", "...#", "...#", "###."},
		'I', new String[] {"#", "#", "#", "#", "#", "#", "#"},
		'N', new String[] {"#...#", "##..#", "##..#", "#.#.#", "#..##", "#..##", "#...#"},
		'O', new String[] {".##.", "#..#", "#..#", "#..#", "#..#", "#..#", ".##."});

	@Test
	void largeCasinoGlyphsMatchTheExpectedLetterMasks() {
		for (Map.Entry<Character, String[]> e : CASINO_LARGE.entrySet()) {
			assertArrayEquals(e.getValue(), SignFont.LARGE.glyph(e.getKey()), "LARGE " + e.getKey());
		}
	}

	/** Cells that differ between two glyphs, the narrower one padded on the right with blanks. */
	static int distance(String[] a, String[] b) {
		int w = Math.max(a[0].length(), b[0].length()), d = 0;
		for (int r = 0; r < a.length; r++) {
			for (int c = 0; c < w; c++) {
				char x = c < a[r].length() ? a[r].charAt(c) : '.', y = c < b[r].length() ? b[r].charAt(c) : '.';
				d += x == y ? 0 : 1;
			}
		}
		return d;
	}

	/** Every letter of a font is its own bitmap (digits may share a letter's shape: O/0, S/5). */
	@Test
	void everyLetterGlyphIsDistinct() {
		for (SignFont f : SignFont.values()) {
			for (char a = 'A'; a <= 'Z'; a++) {
				for (char b = (char) (a + 1); b <= 'Z'; b++) {
					if (f.covers(String.valueOf(a)) && f.covers(String.valueOf(b))) {
						assertTrue(distance(f.glyph(a), f.glyph(b)) > 0, f + ": " + a + " and " + b + " share one bitmap");
					}
				}
			}
		}
	}

	/**
	 * CASINO never reads as CASIRO / CASIND / CASIHO: the N of the LARGE font is at least 4 cells away from R, D, H and
	 * the other wide letters, and each letter of the word is at least 3 cells away from every other letter of its font.
	 */
	@Test
	void casinoLettersCannotBeMistakenForNeighbours() {
		SignFont f = SignFont.LARGE;
		for (char look : "RDHMWUOK".toCharArray()) {
			if (f.covers(String.valueOf(look))) {
				int d = distance(f.glyph('N'), f.glyph(look));
				assertTrue(d >= 4, "LARGE N vs " + look + " differ in only " + d + " cells");
			}
		}
		for (char c : "CASINO".toCharArray()) {
			for (char o = 'A'; o <= 'Z'; o++) {
				if (o != c && f.covers(String.valueOf(o))) {
					int d = distance(f.glyph(c), f.glyph(o));
					assertTrue(d >= 3, "LARGE " + c + " vs " + o + " differ in only " + d + " cells");
				}
			}
		}
		// the N diagonal is one connected stroke from the top of the left stem to the bottom of the right stem
		String[] n = f.glyph('N');
		for (int r = 1; r < n.length - 1; r++) {
			String inner = n[r].substring(1, 4);
			assertEquals(1, inner.chars().filter(ch -> ch == '#').count(), "N row " + r + " has exactly one diagonal cell");
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

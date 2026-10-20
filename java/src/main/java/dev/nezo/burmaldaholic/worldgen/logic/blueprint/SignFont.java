package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Block-letter fonts for casino signs (lane CB-3, shared by every lane's signage). Rules that keep a word
 * readable in game at a distance:
 * <ul>
 *   <li>always one blank column between letters (letters never touch);</li>
 *   <li>I is a single 1-wide stroke (a serif I reads as a T or a box at a distance); N, M and W are 5 wide so the
 *       diagonal is a true diagonal from stem to stem (a 3-wide N reads as "n" or H — the v0.2 "CASIO" bug);</li>
 *   <li>{@link #LARGE} (7 rows) is used when the word fits, else {@link #SMALL} (5 rows) on one line, else
 *       SMALL on two lines (the compact fallback for narrow fronts such as the 17-wide village annex).</li>
 * </ul>
 * Use {@link #plan} to lay out a word for an available width; {@link Facades#litSign} and
 * {@link Facades#marquee} draw a plan.
 */
public enum SignFont {
	/** 3 × 5 base glyphs; N, M, W are 5 wide; I is 1 wide ("CASINO" = 23 columns, odd, so it centres on an axis). */
	SMALL(5, Map.ofEntries(
		Map.entry('A', new String[] {"###", "#.#", "###", "#.#", "#.#"}),
		Map.entry('B', new String[] {"##.", "#.#", "##.", "#.#", "##."}),
		Map.entry('C', new String[] {"###", "#..", "#..", "#..", "###"}),
		Map.entry('D', new String[] {"##.", "#.#", "#.#", "#.#", "##."}),
		Map.entry('E', new String[] {"###", "#..", "##.", "#..", "###"}),
		Map.entry('F', new String[] {"###", "#..", "##.", "#..", "#.."}),
		Map.entry('G', new String[] {"###", "#..", "#.#", "#.#", "###"}),
		Map.entry('H', new String[] {"#.#", "#.#", "###", "#.#", "#.#"}),
		Map.entry('I', new String[] {"#", "#", "#", "#", "#"}),
		Map.entry('J', new String[] {"..#", "..#", "..#", "#.#", "###"}),
		Map.entry('K', new String[] {"#.#", "#.#", "##.", "#.#", "#.#"}),
		Map.entry('L', new String[] {"#..", "#..", "#..", "#..", "###"}),
		Map.entry('M', new String[] {"#...#", "##.##", "#.#.#", "#...#", "#...#"}),
		Map.entry('N', new String[] {"#...#", "##..#", "#.#.#", "#..##", "#...#"}),
		Map.entry('O', new String[] {"###", "#.#", "#.#", "#.#", "###"}),
		Map.entry('P', new String[] {"###", "#.#", "###", "#..", "#.."}),
		Map.entry('Q', new String[] {"###", "#.#", "#.#", "###", "..#"}),
		Map.entry('R', new String[] {"##.", "#.#", "##.", "#.#", "#.#"}),
		Map.entry('S', new String[] {"###", "#..", "###", "..#", "###"}),
		Map.entry('T', new String[] {"###", ".#.", ".#.", ".#.", ".#."}),
		Map.entry('U', new String[] {"#.#", "#.#", "#.#", "#.#", "###"}),
		Map.entry('V', new String[] {"#.#", "#.#", "#.#", "#.#", ".#."}),
		Map.entry('W', new String[] {"#...#", "#...#", "#.#.#", "##.##", "#...#"}),
		Map.entry('X', new String[] {"#.#", "#.#", ".#.", "#.#", "#.#"}),
		Map.entry('Y', new String[] {"#.#", "#.#", ".#.", ".#.", ".#."}),
		Map.entry('Z', new String[] {"###", "..#", ".#.", "#..", "###"}),
		Map.entry('0', new String[] {"###", "#.#", "#.#", "#.#", "###"}),
		Map.entry('1', new String[] {".#.", "##.", ".#.", ".#.", "###"}),
		Map.entry('2', new String[] {"###", "..#", "###", "#..", "###"}),
		Map.entry('3', new String[] {"###", "..#", "###", "..#", "###"}),
		Map.entry('4', new String[] {"#.#", "#.#", "###", "..#", "..#"}),
		Map.entry('5', new String[] {"###", "#..", "###", "..#", "###"}),
		Map.entry('6', new String[] {"###", "#..", "###", "#.#", "###"}),
		Map.entry('7', new String[] {"###", "..#", ".#.", ".#.", ".#."}),
		Map.entry('8', new String[] {"###", "#.#", "###", "#.#", "###"}),
		Map.entry('9', new String[] {"###", "#.#", "###", "..#", "###"}),
		Map.entry('!', new String[] {"#", "#", "#", ".", "#"}),
		Map.entry(' ', new String[] {".", ".", ".", ".", "."}))),
	/**
	 * 4 × 7 glyphs (A and C 3 wide, I 1 wide, N / M / W 5 wide; "CASINO" = 25 columns, odd, the 25 visible columns
	 * between the Grand Casino's towers); covers the letters of the casino words, else falls back to SMALL. D has cut
	 * corners so it never reads as O.
	 */
	LARGE(7, Map.ofEntries(
		Map.entry('A', new String[] {".#.", "#.#", "#.#", "###", "#.#", "#.#", "#.#"}),
		Map.entry('B', new String[] {"###.", "#..#", "#..#", "###.", "#..#", "#..#", "###."}),
		Map.entry('C', new String[] {".##", "#..", "#..", "#..", "#..", "#..", ".##"}),
		Map.entry('D', new String[] {"##..", "#.#.", "#..#", "#..#", "#..#", "#.#.", "##.."}),
		Map.entry('E', new String[] {"####", "#...", "#...", "###.", "#...", "#...", "####"}),
		Map.entry('G', new String[] {".###", "#...", "#...", "#.##", "#..#", "#..#", ".###"}),
		Map.entry('H', new String[] {"#..#", "#..#", "#..#", "####", "#..#", "#..#", "#..#"}),
		Map.entry('I', new String[] {"#", "#", "#", "#", "#", "#", "#"}),
		Map.entry('K', new String[] {"#..#", "#..#", "#.#.", "##..", "#.#.", "#..#", "#..#"}),
		Map.entry('L', new String[] {"#...", "#...", "#...", "#...", "#...", "#...", "####"}),
		Map.entry('M', new String[] {"#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#", "#...#"}),
		Map.entry('N', new String[] {"#...#", "##..#", "##..#", "#.#.#", "#..##", "#..##", "#...#"}),
		Map.entry('O', new String[] {".##.", "#..#", "#..#", "#..#", "#..#", "#..#", ".##."}),
		Map.entry('P', new String[] {"###.", "#..#", "#..#", "###.", "#...", "#...", "#..."}),
		Map.entry('R', new String[] {"###.", "#..#", "#..#", "###.", "#.#.", "#..#", "#..#"}),
		Map.entry('S', new String[] {".###", "#...", "#...", ".##.", "...#", "...#", "###."}),
		Map.entry('T', new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."}),
		Map.entry('U', new String[] {"#..#", "#..#", "#..#", "#..#", "#..#", "#..#", ".##."}),
		Map.entry('V', new String[] {"#...#", "#...#", "#...#", "#...#", ".#.#.", ".#.#.", "..#.."}),
		Map.entry('W', new String[] {"#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"}),
		Map.entry('Y', new String[] {"#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."}),
		Map.entry('7', new String[] {"####", "...#", "..#.", ".#..", ".#..", ".#..", ".#.."}),
		Map.entry(' ', new String[] {"..", "..", "..", "..", "..", "..", ".."})));

	/** Blank columns between letters (never 0). */
	public static final int GAP = 1;
	/** Blank rows between the two lines of a two-line plan. */
	public static final int LINE_GAP = 1;

	private final int rows;
	private final Map<Character, String[]> glyphs;

	SignFont(int rows, Map<Character, String[]> glyphs) {
		this.rows = rows;
		this.glyphs = glyphs;
	}

	public int rows() {
		return rows;
	}

	public boolean covers(String word) {
		for (char c : word.toCharArray()) {
			if (!glyphs.containsKey(c)) {
				return false;
			}
		}
		return true;
	}

	public String[] glyph(char c) {
		String[] g = glyphs.get(c);
		if (g == null) {
			throw new IllegalArgumentException("no glyph for '" + c + "' in " + this);
		}
		return g;
	}

	/** Width of {@code word} on one line, letters {@link #GAP} apart. */
	public int width(String word) {
		int w = 0;
		for (int i = 0; i < word.length(); i++) {
			w += glyph(word.charAt(i)).length > 0 ? glyph(word.charAt(i))[0].length() : 0;
		}
		return w + GAP * Math.max(0, word.length() - 1);
	}

	/**
	 * A laid-out sign: pixels with their column (from the reader's left, already centred per line inside
	 * {@link #width}), row (from the top, 0 .. height-1) and letter index in the whole word.
	 */
	public record Plan(SignFont font, List<String> lines, List<Layouts.SignPixel> pixels, int width, int height) {
		public Plan {
			lines = List.copyOf(lines);
			pixels = List.copyOf(pixels);
		}

		/** The same plan re-centred in a wider text box (for symmetric boards). */
		public Plan widen(int newWidth) {
			if (newWidth <= width) {
				return this;
			}
			return font.layout(lines, newWidth);
		}
	}

	Plan layout(List<String> lines, int boxWidth) {
		List<Layouts.SignPixel> px = new ArrayList<>();
		int letterBase = 0;
		int top = 0;
		for (String line : lines) {
			int lw = width(line);
			int col = (boxWidth - lw) / 2;
			for (int i = 0; i < line.length(); i++) {
				String[] g = glyph(line.charAt(i));
				for (int r = 0; r < rows; r++) {
					for (int c = 0; c < g[r].length(); c++) {
						if (g[r].charAt(c) == '#') {
							px.add(new Layouts.SignPixel(col + c, top + r, letterBase + i));
						}
					}
				}
				col += g[0].length() + GAP;
			}
			letterBase += line.length();
			top += rows + LINE_GAP;
		}
		int height = lines.size() * rows + (lines.size() - 1) * LINE_GAP;
		return new Plan(this, lines, px, boxWidth, height);
	}

	/** One line in this font. */
	public Plan line(String word) {
		return layout(List.of(word), width(word));
	}

	/**
	 * Best plan for {@code word} within {@code maxWidth} columns: LARGE on one line, else SMALL on one line, else
	 * SMALL on two lines split where the longer line is shortest. Empty when even two lines do not fit.
	 */
	public static java.util.Optional<Plan> plan(String word, int maxWidth) {
		if (LARGE.covers(word) && LARGE.width(word) <= maxWidth) {
			return java.util.Optional.of(LARGE.line(word));
		}
		if (SMALL.width(word) <= maxWidth) {
			return java.util.Optional.of(SMALL.line(word));
		}
		Plan best = null;
		for (int split = 1; split < word.length(); split++) {
			String a = word.substring(0, split).strip(), b = word.substring(split).strip();
			if (a.isEmpty() || b.isEmpty()) {
				continue;
			}
			int w = Math.max(SMALL.width(a), SMALL.width(b));
			if (w <= maxWidth && (best == null || w < best.width())) {
				best = SMALL.layout(List.of(a, b), w);
			}
		}
		return java.util.Optional.ofNullable(best);
	}
}

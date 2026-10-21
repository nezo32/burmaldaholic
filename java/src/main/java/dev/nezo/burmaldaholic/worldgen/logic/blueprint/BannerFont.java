package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Letter banners (casino redesign CR-2): a wall banner per letter, the letter drawn with vanilla banner patterns in
 * {@code ink} on a {@code base} cloth and trimmed by a {@code border} in the base colour, so the strokes never touch
 * the edge. A wall banner shows about 0.8 × 1.7 blocks of cloth, i.e. a two-row letter: the only way to stack a whole
 * word into a short facade (a cinema blade: "CASINO" in twelve rows) — block-pixel letters need five rows each.
 *
 * <p>Glyphs (as seen from the front): C = top + bottom + left bar; A = left + right + top + middle; S = top + bottom +
 * the bend (top-left to bottom-right); I = the centre pale; N = left + right + the bend; O = the four bars; 7 = top +
 * the bend sinister; ♦ ({@code '*'}) = the lozenge. Lit banners: hang them on a light-emitting block (the cloth takes
 * the light level of its own cell).
 */
public final class BannerFont {
	private BannerFont() {}

	private static final Map<Character, List<String>> GLYPHS = Map.of(
		'C', List.of("stripe_top", "stripe_bottom", "stripe_left"),
		'A', List.of("stripe_left", "stripe_right", "stripe_top", "stripe_middle"),
		'S', List.of("stripe_top", "stripe_bottom", "stripe_downright"),
		'I', List.of("stripe_center"),
		'N', List.of("stripe_left", "stripe_right", "stripe_downright"),
		'O', List.of("stripe_left", "stripe_right", "stripe_top", "stripe_bottom"),
		'7', List.of("stripe_top", "stripe_downleft"),
		'*', List.of("rhombus"));

	public static boolean covers(char c) {
		return GLYPHS.containsKey(c);
	}

	/** The pattern layers of one glyph: the strokes in {@code ink}, then a border in {@code base}. */
	public static Blueprint.BannerLayer[] layers(char c, String base, String ink) {
		List<String> strokes = GLYPHS.get(c);
		if (strokes == null) {
			throw new IllegalArgumentException("no banner glyph for '" + c + "'");
		}
		List<Blueprint.BannerLayer> out = new ArrayList<>();
		for (String s : strokes) {
			out.add(Blueprint.layer(s, ink));
		}
		out.add(Blueprint.layer("border", base));
		return out.toArray(Blueprint.BannerLayer[]::new);
	}

	/** One letter banner hanging on the block behind it (at {@code -facing}), cloth facing {@code facing}. */
	public static Blueprint letter(Blueprint b, char c, int x, int y, int z, Facing facing, String base, String ink) {
		return b.banner(x, y, z, facing, base, layers(c, base, ink));
	}

	/**
	 * A vertical word: letters top to bottom, one banner every {@code pitch} rows (2 = cloths almost touching), the top
	 * letter's banner block at {@code topY}. Returns the lowest banner row.
	 */
	public static int column(Blueprint b, String word, int x, int topY, int z, Facing facing, int pitch, String base, String ink) {
		int y = topY;
		for (int i = 0; i < word.length(); i++, y -= pitch) {
			letter(b, word.charAt(i), x, y, z, facing, base, ink);
		}
		return y + pitch;
	}

	/** A horizontal word along x (read from +z), centred on {@code centerX}, one banner per cell. */
	public static Blueprint row(Blueprint b, String word, int centerX, int y, int z, Facing facing, String base, String ink) {
		int x0 = centerX - (word.length() - 1) / 2;
		for (int i = 0; i < word.length(); i++) {
			int x = facing == Facing.NORTH ? centerX + (word.length() - 1) / 2 - i : x0 + i;
			letter(b, word.charAt(i), x, y, z, facing, base, ink);
		}
		return b;
	}
}

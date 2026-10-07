package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 8 × 8 pixel glyphs of the kit's icons, the fallback when the {@code core/v2/icon/*} sprites are missing (also used by the
 * gallery's fallback pass). Drawn at the largest whole scale that fits, centred.
 */
final class Glyphs {
	private Glyphs() {}

	private static final Map<String, String[]> GLYPHS = Map.of(
		"undo", new String[] {
			"..#.....",
			".##.....",
			"#######.",
			".##....#",
			"..#....#",
			".......#",
			"...####.",
			"........"},
		"clear", new String[] {
			"#......#",
			".#....#.",
			"..#..#..",
			"...##...",
			"...##...",
			"..#..#..",
			".#....#.",
			"#......#"},
		"rebet", new String[] {
			"..####..",
			".#....#.",
			"#......#",
			"#......#",
			"#....###",
			".#....#.",
			"..###...",
			"........"},
		"double", new String[] {
			".#####..",
			"#.....#.",
			"......#.",
			"....##..",
			"..##....",
			".#......",
			"#######.",
			"........"},
		"rules", new String[] {
			"..####..",
			".#....#.",
			"......#.",
			"....##..",
			"...#....",
			"........",
			"...#....",
			"........"},
		"settings", new String[] {
			"..#..#..",
			"..#..#..",
			"########",
			"..#..#..",
			"..#..#..",
			"########",
			"..#..#..",
			"..#..#.."},
		"leave", new String[] {
			"#####...",
			"#...#...",
			"#...#.#.",
			"#...####",
			"#...#.#.",
			"#...#...",
			"#####...",
			"........"});

	static void draw(GuiGraphicsExtractor g, String name, Box b, int color) {
		String[] rows = GLYPHS.get(name);
		if (rows == null) return;
		int s = Math.max(1, Math.min(b.w(), b.h()) / 8);
		int x0 = b.x() + (b.w() - 8 * s) / 2;
		int y0 = b.y() + (b.h() - 8 * s) / 2;
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				if (rows[y].charAt(x) == '#') g.fill(x0 + x * s, y0 + y * s, x0 + (x + 1) * s, y0 + (y + 1) * s, color);
			}
		}
	}
}

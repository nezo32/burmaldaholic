package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.ui.v2.V2Button;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2Sprites;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Shared drawing of the v2 extras and PvP scenes (REDESIGN.md §7.7, mockup {@code v2_plinko_480.png}) on top of the
 * kit: the tiled scene fill of the PlayArea, the options column (caption, toggle buttons — the selected one gold —,
 * result pills, the 2× "Top prize" value), all by {@link Box} so what is drawn is what the hit map clicks.
 */
public final class SceneKit {
	private SceneKit() {}

	public static final int FILL = 64;

	/** The tiled 64² fill ({@code gui/extras/fill_<name>}) over {@code area}, clipped to it. */
	public static void fill(GuiGraphicsExtractor g, Box area, String name) {
		Identifier tex = Kit.sheet("extras/fill_" + name);
		g.enableScissor(area.x(), area.y(), area.right(), area.bottom());
		for (int y = area.y(); y < area.bottom(); y += FILL) {
			for (int x = area.x(); x < area.right(); x += FILL) {
				Kit.region(g, tex, FILL, FILL, 0, 0, FILL, FILL, x, y);
			}
		}
		g.disableScissor();
		// a soft shade under the TopBar and over the Dock so the object floats on the fill
		g.fillGradient(area.x(), area.y(), area.right(), area.y() + 10, 0x60000000, 0x00000000);
		g.fillGradient(area.x(), area.bottom() - 10, area.right(), area.bottom(), 0x00000000, 0x60000000);
	}

	/** A 1× muted caption with its top at {@code y}. */
	public static void caption(GuiGraphicsExtractor g, Font font, Component text, Box column, int y) {
		V2Draw.left(g, font, text, new Box(column.x(), y, column.w(), 10), 0, 1, CasinoPalette.V2_MUTED, true);
	}

	/** A toggle button: the selected one in the gold primary style (mockup's "Medium"). */
	public static void toggle(GuiGraphicsExtractor g, Font font, Box b, Component label, boolean selected, boolean enabled, int mx, int my) {
		V2Button.draw(g, font, b, label, null, selected ? V2Sprites.ButtonStyle.PRIMARY : V2Sprites.ButtonStyle.NORMAL, enabled || selected,
			b.contains(mx, my), false, false);
	}

	/** A small result pill (dark backing, coloured rim and text): "×2", "×0.6". */
	public static void pill(GuiGraphicsExtractor g, Font font, Box b, Component text, int color) {
		V2Draw.fill(g, b, CasinoPalette.V2_INK);
		V2Draw.outline(g, b, V2Draw.alpha(color, 0.55f));
		V2Draw.centred(g, font, text, b, 1, color, true);
	}

	/** The 2× gold value under a caption ("Top prize" ×33). */
	public static void big(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int maxW, int color) {
		int scale = font.width(text) * 2 <= maxW ? 2 : 1;
		V2Draw.text(g, font, text, x, y, scale, color, true);
	}

	/** Multiplier colour (REDESIGN.md §7.7): gold top, green &gt; 1, cyan = 1, red &lt; 1. */
	public static int multColor(double mult, double top) {
		if (mult >= top && mult > 1) return CasinoPalette.V2_GOLD;
		if (mult > 1) return CasinoPalette.V2_WIN;
		if (mult == 1) return CasinoPalette.V2_YOU;
		return CasinoPalette.V2_LOSE;
	}
}

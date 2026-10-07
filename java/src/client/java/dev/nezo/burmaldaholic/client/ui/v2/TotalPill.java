package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** The hand total in the seat's outcome slot before settle (1× others, 2× you and the dealer). */
public final class TotalPill {
	private TotalPill() {}

	public static int width(Font font, Component text, int scale) {
		return font.width(text) * scale + (scale >= 2 ? 12 : 8);
	}

	public static void draw(GuiGraphicsExtractor g, Font font, Box box, Component text, int scale) {
		if (box.isEmpty()) return;
		V2Draw.sprite(g, V2Sprites.TOTAL_PILL, box, CasinoPalette.V2_PANEL, CasinoPalette.V2_INK);
		V2Draw.centred(g, font, text, box, scale, CasinoPalette.V2_TEXT, true);
	}
}

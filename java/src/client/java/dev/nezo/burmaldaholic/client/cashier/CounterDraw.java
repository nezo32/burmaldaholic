package dev.nezo.burmaldaholic.client.cashier;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.ui.v2.V2Button;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2Sprites;
import dev.nezo.burmaldaholic.core.text.Numbers;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Small drawing helpers shared by the counter tabs (kit buttons by box, panels, compact numbers). */
final class CounterDraw {
	private CounterDraw() {}

	static void button(GuiGraphicsExtractor g, Font font, Box b, Component label, boolean primary, boolean enabled, int mouseX, int mouseY,
			boolean focused) {
		V2Button.draw(g, font, b, label, null, primary ? V2Sprites.ButtonStyle.PRIMARY : V2Sprites.ButtonStyle.NORMAL, enabled,
			b.contains(mouseX, mouseY), false, focused);
	}

	static void panel(GuiGraphicsExtractor g, Box b) {
		V2Draw.fill(g, b, 0x80000000);
		V2Draw.outline(g, b, CasinoPalette.V2_PANEL_HI);
	}

	/** 25000 → "25k", 1500 → "1,500" (chip button labels). */
	static String compact(long n) {
		if (n >= 1000 && n % 1000 == 0) return Numbers.format(n / 1000) + "k"; // literal-ok: number suffix
		return Numbers.format(n);
	}
}

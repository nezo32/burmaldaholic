package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.SeatColumn;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The value of a bet on the felt, beside its stack (1×, {@link SeatColumn#PILL_1X} high). On settle it shows the net:
 * {@code +75} green ({@link Style#WIN}), {@code −50} red, {@code 50} slate (docs/architecture/ui-v2.md §2.1).
 */
public final class ValuePill {
	public enum Style {
		YOU,
		OTHER,
		WIN,
		LOSE,
		PUSH
	}

	/** Horizontal padding inside the pill. */
	public static final int PAD = 3;

	private ValuePill() {}

	/** Width of a pill holding {@code text}. */
	public static int width(Font font, Component text) {
		return font.width(text) + 2 * PAD;
	}

	public static void draw(GuiGraphicsExtractor g, Font font, Box box, Component text, Style style) {
		if (box.isEmpty()) return;
		int fill = switch (style) {
			case YOU -> 0xFF1C4A52;
			case WIN -> CasinoPalette.V2_WIN_FILL;
			case LOSE -> CasinoPalette.V2_LOSE_FILL;
			case PUSH -> CasinoPalette.V2_PUSH_FILL;
			case OTHER -> CasinoPalette.V2_PANEL;
		};
		int rim = switch (style) {
			case YOU -> CasinoPalette.V2_YOU;
			case OTHER -> CasinoPalette.V2_INK;
			default -> CasinoPalette.V2_INK;
		};
		int color = switch (style) {
			case YOU -> CasinoPalette.V2_YOU;
			case WIN -> CasinoPalette.V2_TEXT;
			case LOSE -> CasinoPalette.V2_TEXT;
			case PUSH -> CasinoPalette.V2_TEXT;
			case OTHER -> CasinoPalette.V2_TEXT;
		};
		V2Draw.sprite(g, V2Sprites.valuePill(style), box, fill, rim);
		V2Draw.centred(g, font, text, box, 1, color, true);
	}

	/** The style of a settled net. */
	public static Style ofNet(long net) {
		return net > 0 ? Style.WIN : net < 0 ? Style.LOSE : Style.PUSH;
	}
}

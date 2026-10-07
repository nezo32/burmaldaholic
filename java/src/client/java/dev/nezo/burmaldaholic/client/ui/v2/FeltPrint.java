package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Printed felt words ({@code BLACKJACK PAYS 3 TO 2}, an empty seat's {@code Sit}): decor, drawn only when the text box
 * intersects no content box (docs/architecture/ui-v2.md §2.1). No shadow, {@code felt.print} at 55–80 % alpha.
 */
public final class FeltPrint {
	private FeltPrint() {}

	/** Draws {@code text} centred in {@code box} unless the measured text box touches any of {@code content}. */
	public static boolean draw(GuiGraphicsExtractor g, Font font, Box box, Component text, float alpha, List<Box> content) {
		int w = Math.min(font.width(text), box.w());
		Box t = box.centred(w, font.lineHeight);
		for (Box c : content) if (t.intersects(c)) return false;
		V2Draw.centred(g, font, text, box, 1, CasinoPalette.withAlpha(CasinoPalette.V2_FELT_PRINT, alpha), false);
		return true;
	}

	/** An empty seat's printed betting box (35 % print; hover = gold ring + the word {@code Sit}). */
	public static void seatBox(GuiGraphicsExtractor g, Font font, Box box, boolean hover) {
		int c = hover ? CasinoPalette.V2_GOLD : CasinoPalette.withAlpha(CasinoPalette.V2_FELT_PRINT, 0.35f);
		ring(g, box, c);
		if (hover) {
			ring(g, box.inset(1), CasinoPalette.withAlpha(CasinoPalette.V2_GOLD, 0.5f));
			V2Draw.centred(g, font, Component.translatable(dev.nezo.burmaldaholic.core.ui.v2.V2Keys.SIT), box, 1, CasinoPalette.V2_GOLD, true);
		}
	}

	/** A 1-px rounded ring inside {@code b}. */
	public static void ring(GuiGraphicsExtractor g, Box b, int c) {
		if (b.w() < 6 || b.h() < 6) return;
		int r = Math.min(b.w(), b.h()) / 4;
		g.fill(b.x() + r, b.y(), b.right() - r, b.y() + 1, c);
		g.fill(b.x() + r, b.bottom() - 1, b.right() - r, b.bottom(), c);
		g.fill(b.x(), b.y() + r, b.x() + 1, b.bottom() - r, c);
		g.fill(b.right() - 1, b.y() + r, b.right(), b.bottom() - r, c);
		for (int i = 1; i < r; i++) { // the corner diagonals
			int d = r - i;
			g.fill(b.x() + i, b.y() + d, b.x() + i + 1, b.y() + d + 1, c);
			g.fill(b.right() - i - 1, b.y() + d, b.right() - i, b.y() + d + 1, c);
			g.fill(b.x() + i, b.bottom() - d - 1, b.x() + i + 1, b.bottom() - d, c);
			g.fill(b.right() - i - 1, b.bottom() - d - 1, b.right() - i, b.bottom() - d, c);
		}
	}
}

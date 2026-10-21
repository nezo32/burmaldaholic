package dev.nezo.burmaldaholic.client.table.cards;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The padded crescent rail of the v2 card tables (REDESIGN.md §7.1, the mockups' wooden arc): an elliptic arc from the
 * felt's sides down to just under the viewer's plate, drawn per column (pixel-exact at every GUI size, theme-coloured;
 * no stretched sprite). Decor: drawn first, under every seat.
 */
public final class RailArc {
	private RailArc() {}

	/**
	 * @param play   the PlayArea
	 * @param bottom y of the arc's lowest point (centre column)
	 * @param sideY  y where the arc meets the left / right screen edge
	 */
	public static void draw(GuiGraphicsExtractor g, Box play, Tier t, int bottom, int sideY, int rail, int dark, int hi) {
		int th = t == Tier.T3 ? 10 : t == Tier.T2 ? 8 : 6;
		double cx = play.x() + play.w() / 2.0;
		double a = play.w() * 0.62;
		double k = Math.sqrt(1 - Math.pow((play.x() - cx) / a, 2));
		double b = (bottom - sideY) / (1 - k);
		double cy = bottom - b;
		int prev = -1;
		for (int x = play.x(); x < play.right(); x++) {
			double u = (x + 0.5 - cx) / a;
			int y = (int) Math.round(cy + b * Math.sqrt(Math.max(0, 1 - u * u)));
			if (y < play.y()) continue;
			int top = prev < 0 ? y : Math.min(y, prev);
			int y0 = Math.min(top, y) - th / 2;
			int y1 = Math.max(y, prev < 0 ? y : prev) + th / 2;
			g.fill(x, y0, x + 1, y1, rail);
			g.fill(x, y0, x + 1, y0 + 1, dark);
			g.fill(x, y0 + 1, x + 1, y0 + 2, hi);
			g.fill(x, y0 + th / 2, x + 1, y0 + th / 2 + 1, CasinoPalette.V2_GOLD_SHADE);
			g.fill(x, y1 - 1, x + 1, y1, dark);
			prev = y;
		}
	}
}

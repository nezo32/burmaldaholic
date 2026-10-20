package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A bet on the felt (docs/architecture/ui-v2.md §4.2): up to {@link #MAX_DISCS} top-down discs (12 / 16 px) of the
 * greedy denominations of the amount, stacked 1 px apart, bottom disc at the bottom of the box. No value text: the
 * value pill is drawn by the seat column / spot beside the stack.
 */
public final class ChipStack {
	public static final int MAX_DISCS = 4;

	private ChipStack() {}

	/** Edge / face colours per denomination (the flat fallback and the tray chips' fallback). */
	public static int color(long denom) {
		if (denom >= 25000) return 0xFF1FA8A0;
		if (denom >= 5000) return 0xFFE07A1C;
		if (denom >= 1000) return 0xFFE8C030;
		if (denom >= 500) return 0xFF6A2CA8;
		if (denom >= 100) return 0xFF2A2A30;
		if (denom >= 25) return 0xFF2E8C3C;
		if (denom >= 5) return 0xFFD83440;
		return 0xFFE8E8F0;
	}

	public static int stripe(long denom) {
		if (denom >= 25000) return 0xFFE8FFF8;
		if (denom >= 5000) return 0xFFFFF4B0;
		if (denom >= 1000) return 0xFF3A2010;
		if (denom >= 500) return 0xFFFFD640;
		if (denom == 1) return 0xFF3C78D8;
		return 0xFFF4ECF8;
	}

	/** The greedy denominations of {@code amount}, largest first, at most {@link #MAX_DISCS} (the top ones dropped). */
	public static List<Long> discs(long amount) {
		int[] d = new int[MAX_DISCS];
		int n = dev.nezo.burmaldaholic.core.chips.ChipMath.discs(amount, d);
		List<Long> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) out.add((long) d[i]);
		return out;
	}

	/** Draws the stack of {@code amount} into {@code box} (disc size = box width). Nothing for 0. */
	public static void draw(GuiGraphicsExtractor g, Box box, long amount) {
		draw(g, box, amount, 1f);
	}

	public static void draw(GuiGraphicsExtractor g, Box box, long amount, float alpha) {
		if (amount <= 0 || box.isEmpty()) return;
		List<Long> ds = discs(amount);
		int size = box.w();
		int y = box.bottom() - size;
		for (int i = 0; i < ds.size(); i++) {
			disc(g, box.x(), y - i, size, ds.get(i), alpha);
		}
	}

	/** The pending chip of an action not yet acknowledged (60 % alpha). */
	public static void drawPending(GuiGraphicsExtractor g, Box box, long denom) {
		if (box.isEmpty() || denom <= 0) return;
		disc(g, box.x(), box.bottom() - box.w(), box.w(), denom, 0.6f);
	}

	/** One top-down disc at {@code x, y}, {@code size}². */
	public static void disc(GuiGraphicsExtractor g, int x, int y, int size, long denom, float alpha) {
		Box b = new Box(x, y, size, size);
		if (V2Draw.sprite(g, V2Sprites.disc(denom, size), b, CasinoPalette.withAlpha(0xFFFFFFFF, alpha))) return;
		int c = V2Draw.alpha(color(denom), alpha);
		int ink = V2Draw.alpha(CasinoPalette.V2_INK, alpha);
		int s = V2Draw.alpha(stripe(denom), alpha);
		// a rounded square: ink rim, colour body, light stripe ring, dark centre
		g.fill(x + 2, y, x + size - 2, y + size, ink);
		g.fill(x, y + 2, x + size, y + size - 2, ink);
		g.fill(x + 1, y + 1, x + size - 1, y + size - 1, ink);
		g.fill(x + 2, y + 1, x + size - 2, y + size - 1, c);
		g.fill(x + 1, y + 2, x + size - 1, y + size - 2, c);
		int q = size / 4;
		g.fill(x + q, y + q, x + size - q, y + size - q, s);
		g.fill(x + q + 1, y + q + 1, x + size - q - 1, y + size - q - 1, c);
		g.fill(x + 1, y + size - 2, x + size - 1, y + size - 1, V2Draw.alpha(V2Draw.darker(color(denom), 0.6f), 1f));
	}
}

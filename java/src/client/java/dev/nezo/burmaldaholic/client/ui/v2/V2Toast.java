package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Errors and server notices ON the felt (top-right of the PlayArea), not vanilla toasts over the TopBar
 * (docs/architecture/ui-v2.md §4.2): ≤ {@link #MAX} stacked, {@link #LIFE_MS} each, newest on top, slide in from the right
 * (reduce motion: in place). Replaces the old 60-tick error line.
 */
public final class V2Toast {
	public enum Kind {
		ERROR,
		INFO,
		WIN
	}

	public static final int MAX = 3, LIFE_MS = 3000, IN_MS = 160;

	private record Entry(Kind kind, Component text, long at) {}

	private final List<Entry> entries = new ArrayList<>();
	private Box area = Box.EMPTY;

	public void show(Kind kind, Component text) {
		entries.add(0, new Entry(kind, text, Util.getMillis()));
		while (entries.size() > MAX) entries.remove(entries.size() - 1);
	}

	public void clear() {
		entries.clear();
	}

	/** The newest visible toast's text (tests). */
	public Component latest() {
		prune();
		return entries.isEmpty() ? null : entries.get(0).text();
	}

	void place(Box playArea) {
		this.area = playArea;
	}

	private void prune() {
		long now = Util.getMillis();
		entries.removeIf(e -> now - e.at() >= LIFE_MS);
	}

	public void draw(GuiGraphicsExtractor g, Font font) {
		prune();
		if (area.isEmpty()) return;
		long now = Util.getMillis();
		int y = area.y() + 4;
		int maxW = Math.min(220, area.w() / 2);
		for (Entry e : entries) {
			int w = Math.min(maxW, font.width(e.text()) + 14);
			int h = 15;
			long age = now - e.at();
			int slide = FxSettings.reduceMotion() ? 0 : (int) Math.max(0, (1 - age / (float) IN_MS) * (w + 8));
			float a = FxSettings.reduceMotion() ? 1f : Math.min(1f, (LIFE_MS - age) / 300f);
			Box b = new Box(area.right() - 4 - w + slide, y, w, h);
			int edge = switch (e.kind()) {
				case ERROR -> CasinoPalette.V2_LOSE;
				case WIN -> CasinoPalette.V2_WIN;
				case INFO -> CasinoPalette.V2_GOLD;
			};
			if (!V2Draw.sprite(g, V2Sprites.TOAST, b, CasinoPalette.withAlpha(0xFFFFFFFF, a))) {
				V2Draw.fill(g, b, V2Draw.alpha(CasinoPalette.V2_INK, a * 0.92f));
				V2Draw.outline(g, b, V2Draw.alpha(CasinoPalette.V2_PANEL_HI, a));
			}
			g.fill(b.x(), b.y(), b.x() + 3, b.bottom(), V2Draw.alpha(edge, a));
			V2Draw.left(g, font, e.text(), new Box(b.x() + 6, b.y(), b.w() - 8, b.h()), 0, 1, V2Draw.alpha(CasinoPalette.V2_TEXT, a), true);
			y += h + 2;
		}
	}
}

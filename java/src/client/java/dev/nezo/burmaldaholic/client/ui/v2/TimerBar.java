package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A 3 px countdown bar under the status (gold → red under 5 s); hidden when no timer runs. */
public final class TimerBar {
	private int secondsLeft = -1;
	private int total = 1;

	public void set(int secondsLeft, int total) {
		this.secondsLeft = secondsLeft;
		this.total = Math.max(1, total);
	}

	public void clear() {
		secondsLeft = -1;
	}

	public void draw(GuiGraphicsExtractor g, Box b) {
		if (secondsLeft < 0 || b.isEmpty()) return;
		V2Draw.fill(g, b, CasinoPalette.V2_INK);
		float f = Math.max(0, Math.min(1, secondsLeft / (float) total));
		int w = Math.round((b.w() - 2) * f);
		int c = secondsLeft < 5 ? CasinoPalette.V2_LOSE : CasinoPalette.V2_GOLD;
		g.fill(b.x() + 1, b.y() + 1, b.x() + 1 + w, b.bottom() - 1, c);
	}
}

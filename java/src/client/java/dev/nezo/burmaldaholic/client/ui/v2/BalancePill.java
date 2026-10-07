package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.BalanceTicker;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The TopBar balance: chip icon + the balance ticking to its target over ~0.6 s ({@link BalanceTicker}), and a delta pill
 * left of it ({@code +125} green / {@code −50} red) for {@link #DELTA_MS} after {@link #delta}.
 */
public final class BalancePill {
	public static final int DELTA_MS = 2000;

	private final BalanceTicker ticker = new BalanceTicker();
	private boolean primed;
	private long delta;
	private long deltaAt = -1;
	private Box box = Box.EMPTY;
	private Box deltaBox = Box.EMPTY;

	/** The balance to show (ticks there). The first call snaps. */
	public void target(long balance) {
		if (!primed) {
			ticker.snap(balance);
			primed = true;
		} else if (balance != ticker.target()) {
			ticker.retarget(balance, Util.getMillis(), FxSettings.reduceMotion());
		}
	}

	/** Shows the net of a round in the delta pill for 2 s. */
	public void delta(long net) {
		this.delta = net;
		this.deltaAt = Util.getMillis();
	}

	public long shown() {
		return ticker.value(Util.getMillis());
	}

	public boolean deltaVisible() {
		return deltaAt >= 0 && Util.getMillis() - deltaAt < DELTA_MS;
	}

	public Component deltaText() {
		return V2Draw.net(delta);
	}

	/** Width of the pill for the current target. */
	public int measure(Font font, int scale) {
		return font.width(Texts.number(Math.max(ticker.target(), shown()))) * scale + 12 + 6 + 8;
	}

	public int measureDelta(Font font) {
		return deltaVisible() ? font.width(deltaText()) + 8 : 0;
	}

	void place(Box box, Box deltaBox) {
		this.box = box;
		this.deltaBox = deltaBox;
	}

	public Box box() {
		return box;
	}

	public void draw(GuiGraphicsExtractor g, Font font, int scale) {
		if (box.isEmpty()) return;
		V2Draw.sprite(g, V2Sprites.BALANCE, box, CasinoPalette.V2_INK, CasinoPalette.V2_GOLD_SHADE);
		int icon = 10;
		int ix = box.x() + 4;
		int iy = box.y() + (box.h() - icon) / 2;
		if (!V2Draw.sprite(g, V2Sprites.CHIP_ICON, new Box(ix, iy, icon, icon), 0xFFFFFFFF)) {
			ChipStack.disc(g, ix, iy, icon, 1, 1f);
		}
		long now = Util.getMillis();
		int color = ticker.active(now) ? (ticker.direction(now) > 0 ? CasinoPalette.V2_WIN : CasinoPalette.V2_LOSE) : CasinoPalette.V2_GOLD;
		Component v = Texts.number(ticker.value(now));
		int w = font.width(v) * scale;
		int h = 8 * scale;
		V2Draw.text(g, font, v, box.right() - 5 - w, box.y() + (box.h() - h) / 2, scale, color, true);
		if (deltaVisible() && !deltaBox.isEmpty()) {
			int fill = delta > 0 ? CasinoPalette.V2_WIN_FILL : delta < 0 ? CasinoPalette.V2_LOSE_FILL : CasinoPalette.V2_PUSH_FILL;
			V2Draw.pill(g, deltaBox, fill, CasinoPalette.V2_INK);
			V2Draw.centred(g, font, deltaText(), deltaBox, 1, CasinoPalette.V2_TEXT, true);
		}
	}
}

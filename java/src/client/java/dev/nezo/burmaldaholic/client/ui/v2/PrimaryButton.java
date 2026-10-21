package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The one gold button of every v2 screen, always at the right edge of the Dock (HitMap id {@code primary}). It shows a
 * {@link Gate}: label + key hint plate; disabled = dim and the status line shows the gate's reason; a click on a disabled
 * primary shakes it (3 px, 240 ms; reduce motion: none) and re-shows the reason — never nothing.
 */
public final class PrimaryButton {
	public static final String ID = KeyMap.Ids.PRIMARY;
	public static final int SHAKE_MS = 240;

	private Gate gate = Gate.off("", V2Keys.PRIMARY_WAIT, V2Keys.REASON_ROUND_IN_PROGRESS);
	private @Nullable String key = KeyMap.SPACE;
	private Box box = Box.EMPTY;
	private boolean compact;
	private long shakeAt = -1;
	private long pressedAt = -1;

	/** Sets the gate and the key shown on the hint plate (null = none). */
	public void set(Gate gate, @Nullable String key) {
		this.gate = gate;
		this.key = key;
	}

	public void set(Gate gate) {
		this.gate = gate;
	}

	public Gate gate() {
		return gate;
	}

	public Box box() {
		return box;
	}

	void place(Box box, boolean compact) {
		this.box = box;
		this.compact = compact;
	}

	public Component label() {
		return V2Draw.label(gate);
	}

	/** Width with the key plate. */
	public int measure(Font font) {
		return V2Button.width(font, label(), V2Button.keyName(key));
	}

	/** Width without the key plate. */
	public int measureCompact(Font font) {
		return V2Button.width(font, label(), null);
	}

	public void shake() {
		shakeAt = Util.getMillis();
	}

	void pressed() {
		pressedAt = Util.getMillis();
	}

	public void draw(GuiGraphicsExtractor g, Font font, int mx, int my, boolean focused) {
		long now = Util.getMillis();
		Box b = box;
		if (shakeAt >= 0 && now - shakeAt < SHAKE_MS && !FxSettings.reduceMotion()) {
			double t = (now - shakeAt) / (double) SHAKE_MS;
			b = b.offset((int) Math.round(Math.sin(t * Math.PI * 6) * 3 * (1 - t)), 0);
		}
		boolean hover = box.contains(mx, my);
		boolean pressed = pressedAt >= 0 && now - pressedAt < 120;
		V2Button.draw(g, font, b, label(), compact ? null : V2Button.keyName(key), V2Sprites.ButtonStyle.PRIMARY, gate.enabled(), hover, pressed,
			focused);
	}
}

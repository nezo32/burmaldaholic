package dev.nezo.burmaldaholic.games.slots.client.panels;

import dev.nezo.burmaldaholic.games.slots.client.SlotSounds;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Slot control button (slots.md §4.14): primary / secondary / danger styles, hover lift (1 px), press depress (2 px,
 * 60 ms), the invalid shake (±2 px, 240 ms) and — for the SPIN style — the breathing glow ring (0.5 Hz) and the Stop
 * state during a spin. A real widget (focus, keyboard, narration); the label shrinks (never below ½) to fit so
 * Russian always fits (UI.md §0.1).
 */
public final class SlotButton extends AbstractButton {
	public enum Style {
		PRIMARY(0xFF8A3AAA, 0xFF4A1A6A, 0xFFD696FF),
		SECONDARY(0xFF3A2A4A, 0xFF1E1428, 0xFF8C7AA0),
		DANGER(0xFFD83440, 0xFF8C1834, 0xFFFF6E6A),
		GOLD(0xFFFFC400, 0xFFB07010, 0xFFFFF0A0),
		SPIN(0xFF2E9A4A, 0xFF10501E, 0xFFA0FFB0);

		final int top;
		final int bottom;
		final int rim;

		Style(int top, int bottom, int rim) {
			this.top = top;
			this.bottom = bottom;
			this.rim = rim;
		}
	}

	private final Consumer<SlotButton> onPress;
	private Style style;
	private long pressedAt = -1;
	private long shakeAt = -1;
	private boolean stopMode;
	private boolean toggled;

	public SlotButton(int x, int y, int w, int h, Component label, Style style, Consumer<SlotButton> onPress) {
		super(x, y, w, h, label);
		this.style = style;
		this.onPress = onPress;
	}

	public SlotButton style(Style s) {
		this.style = s;
		return this;
	}

	/** Spin ↔ Stop (red square) during a spin. */
	public void stopMode(boolean stop) {
		this.stopMode = stop;
	}

	public void toggled(boolean on) {
		this.toggled = on;
	}

	/** Invalid feedback: shake ±2 px for 240 ms + {@code ui_deny}. */
	public void shake() {
		shakeAt = Util.getMillis();
		SlotSounds.deny();
	}

	@Override
	public void onPress(InputWithModifiers input) {
		pressedAt = Util.getMillis();
		onPress.accept(this);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		int dy = 0;
		if (active && isHoveredOrFocused()) dy = -1;
		if (pressedAt >= 0 && now - pressedAt < 60) dy = 2;
		int dx = 0;
		if (shakeAt >= 0 && now - shakeAt < 240) dx = (int) Math.round(2 * Math.sin((now - shakeAt) / 240.0 * Math.PI * 6) * (1 - (now - shakeAt) / 240.0));
		x += dx;
		y += dy;
		Style st = stopMode ? Style.DANGER : style;
		int top = active ? st.top : 0xFF4A4A52;
		int bottom = active ? st.bottom : 0xFF2A2A30;
		int rim = active ? (toggled ? 0xFFFFD640 : st.rim) : 0xFF6A6A72;
		if (style == Style.SPIN && active && !stopMode) {
			double breath = 0.35 + 0.35 * (0.5 + 0.5 * Math.sin(now / 1000.0 * Math.PI));
			SlotDraw.glow(g, x, y, w, h, 0xFFA0FFB0, 4, isHoveredOrFocused() ? 1 : breath);
		}
		if (toggled) SlotDraw.glow(g, x, y, w, h, 0xFFFFD640, 2, 0.8);
		if (style == Style.SPIN && CabinetArt.ART) {
			var id = !active ? SlotSprites.SPIN_DISABLED : stopMode ? SlotSprites.SPIN_STOP : dy == 2 ? SlotSprites.SPIN_PRESSED
				: isHoveredOrFocused() ? SlotSprites.SPIN_HOVER : SlotSprites.SPIN;
			SlotSprites.blit(g, id, x, y, w, h);
			if (isFocused()) SlotDraw.frame(g, x - 2, y - 2, w + 4, h + 4, 1, 0xFFFFFFFF);
			var f = Minecraft.getInstance().font;
			SlotDraw.centeredFit(g, f, getMessage(), x + w / 2, y + h - 11, w - 6, active ? 0xFFFFFFFF : 0xFFA0A0A8);
			return;
		}
		SlotDraw.plate(g, x, y, w, h, top, bottom, rim);
		if (isFocused()) SlotDraw.frame(g, x - 2, y - 2, w + 4, h + 4, 1, 0xFFFFFFFF);
		var font = Minecraft.getInstance().font;
		int color = active ? 0xFFFFFFFF : 0xFFA0A0A8;
		if (stopMode) {
			// red square "stop" icon above the label
			g.fill(x + w / 2 - 4, y + h / 2 - 9, x + w / 2 + 4, y + h / 2 - 1, 0xFFFFFFFF);
			SlotDraw.centeredFit(g, font, getMessage(), x + w / 2, y + h / 2 + 2, w - 4, color);
		} else {
			SlotDraw.centeredFit(g, font, getMessage(), x + w / 2, y + (h - 8) / 2, w - 4, color);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}

package dev.nezo.burmaldaholic.games.slots.client.panels;

import dev.nezo.burmaldaholic.games.slots.client.SlotSounds;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Slot control button (slots.md §4.14): casino faces from the generated art (nine-slice {@code slots/button*}: idle,
 * hover, pressed, disabled, toggled-on, gold primary), an optional 12 px icon before the label (or icon only, with the
 * label kept as the tooltip / narration), hover lift (1 px), press depress (2 px, 60 ms), the invalid shake (±2 px,
 * 240 ms). The SPIN style is the round 56 × 40 sprite with its big icon (spin arrow / stop square) and the cost as a
 * readable caption UNDER the button; the breathing glow ring (0.5 Hz) while idle. A real widget (focus, keyboard,
 * narration); labels shrink (never below ½) to fit so Russian always fits (UI.md §0.1).
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

	/** Icon box size and the gap to the label. */
	public static final int ICON = 12;
	public static final int PAD = 5;

	private final Consumer<SlotButton> onPress;
	private Style style;
	private long pressedAt = -1;
	private long shakeAt = -1;
	private boolean stopMode;
	private boolean toggled;
	private SlotSprites.Tex icon;
	private SlotSprites.Tex toggledIcon;
	private boolean iconOnly;
	private Component caption;

	public SlotButton(int x, int y, int w, int h, Component label, Style style, Consumer<SlotButton> onPress) {
		super(x, y, w, h, label);
		this.style = style;
		this.onPress = onPress;
	}

	public SlotButton style(Style s) {
		this.style = s;
		return this;
	}

	/** Icon before the label ({@code on}: the icon while toggled, e.g. the lit turbo bolt; null = same). */
	public SlotButton icon(SlotSprites.Tex icon, SlotSprites.Tex on) {
		this.icon = icon;
		this.toggledIcon = on;
		return this;
	}

	/** Draw the icon only (the label stays the narration / tooltip text). */
	public SlotButton iconOnly(boolean on) {
		this.iconOnly = on;
		return this;
	}

	public boolean iconOnly() {
		return iconOnly && icon != null;
	}

	/** The label is not drawn on the face (icon-only, or SPIN whose caption shows the cost): tooltip / narration only. */
	public boolean labelHidden() {
		return iconOnly() || style == Style.SPIN;
	}

	/** SPIN: the caption drawn under the button (the cost, or Stop). */
	public void caption(Component c) {
		this.caption = c;
	}

	/** Width this button needs for its label (and icon) in {@code font}. */
	public int preferredWidth(Font font) {
		if (iconOnly()) return ICON + 2 * PAD;
		int text = font.width(getMessage());
		return Math.max(22, text + 2 * PAD + (icon != null ? iconW() + 3 : 0));
	}

	private int iconW() {
		return icon == null ? 0 : Math.min(icon.w(), getHeight() - 2);
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
		boolean hover = active && isHoveredOrFocused();
		if (hover) dy = -1;
		boolean pressed = pressedAt >= 0 && now - pressedAt < 60;
		if (pressed) dy = 2;
		int dx = 0;
		if (shakeAt >= 0 && now - shakeAt < 240) dx = (int) Math.round(2 * Math.sin((now - shakeAt) / 240.0 * Math.PI * 6) * (1 - (now - shakeAt) / 240.0));
		x += dx;
		y += dy;
		Font font = Minecraft.getInstance().font;
		if (style == Style.SPIN) {
			drawSpin(g, font, x, y, w, h, now, pressed);
			return;
		}
		boolean gold = style == Style.GOLD || toggled;
		if (CabinetArt.ART) {
			SlotSprites.Tex face = !active ? SlotSprites.BUTTON_DISABLED : pressed ? SlotSprites.BUTTON_PRESSED : toggled ? SlotSprites.BUTTON_ON
				: style == Style.GOLD ? SlotSprites.BUTTON_GOLD : hover ? SlotSprites.BUTTON_HOVER : SlotSprites.BUTTON;
			SlotSprites.blit(g, face, x, y, w, h);
			if (hover && gold) SlotDraw.frame(g, x + 1, y + 1, w - 2, h - 2, 1, 0x60FFFFFF);
		} else {
			Style st = stopMode ? Style.DANGER : style;
			SlotDraw.plate(g, x, y, w, h, active ? st.top : 0xFF4A4A52, active ? st.bottom : 0xFF2A2A30, active ? (toggled ? 0xFFFFD640 : st.rim) : 0xFF6A6A72);
		}
		if (toggled) SlotDraw.glow(g, x, y, w, h, 0xFFFFD640, 2, 0.6);
		if (isFocused()) SlotDraw.frame(g, x - 2, y - 2, w + 4, h + 4, 1, 0xFFFFFFFF);
		int color = !active ? 0xFFA0A0A8 : gold ? 0xFF2A1400 : 0xFFFFFFFF;
		int ty = y + (h - 8) / 2 + (pressed ? 0 : 0);
		SlotSprites.Tex ic = toggled && toggledIcon != null ? toggledIcon : icon;
		if (iconOnly()) {
			int s = iconW();
			SlotSprites.blit(g, ic, x + (w - s) / 2, y + (h - s) / 2, s, s);
			return;
		}
		if (ic != null) {
			int s = iconW();
			int text = Math.min(font.width(getMessage()), w - 2 * PAD - s - 3);
			int cx = x + (w - (s + 3 + text)) / 2;
			SlotSprites.blit(g, ic, cx, y + (h - s) / 2, s, s);
			SlotDraw.textFit(g, font, getMessage(), cx + s + 3, ty, text, color, !gold);
			return;
		}
		if (gold) {
			int tw = Math.min(font.width(getMessage()), w - 2 * PAD);
			SlotDraw.textFit(g, font, getMessage(), x + (w - tw) / 2, ty, tw, color, false);
		} else {
			SlotDraw.centeredFit(g, font, getMessage(), x + w / 2, ty, w - 2 * PAD, color);
		}
	}

	private void drawSpin(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, long now, boolean pressed) {
		if (active && !stopMode) {
			double breath = 0.35 + 0.35 * (0.5 + 0.5 * Math.sin(now / 1000.0 * Math.PI));
			SlotDraw.glow(g, x, y, w, h, 0xFFFFD640, 4, isHoveredOrFocused() ? 1 : breath);
		}
		if (CabinetArt.ART) {
			var id = !active ? SlotSprites.SPIN_DISABLED : stopMode ? SlotSprites.SPIN_STOP : pressed ? SlotSprites.SPIN_PRESSED
				: isHoveredOrFocused() ? SlotSprites.SPIN_HOVER : SlotSprites.SPIN;
			SlotSprites.blit(g, id, x, y, w, h);
		} else {
			Style st = stopMode ? Style.DANGER : style;
			SlotDraw.plate(g, x, y, w, h, st.top, st.bottom, st.rim);
			if (stopMode) g.fill(x + w / 2 - 5, y + h / 2 - 5, x + w / 2 + 5, y + h / 2 + 5, 0xFFFFFFFF);
			else SlotDraw.disc(g, x + w / 2, y + h / 2, 6, 0xFFFFFFFF);
		}
		if (isFocused()) SlotDraw.frame(g, x - 2, y - 2, w + 4, h + 4, 1, 0xFFFFFFFF);
		if (caption != null) {
			// the cost (or Stop), readable at 1×, under the button (never over the icon)
			int cy = getY() + h + 2;
			SlotDraw.centeredFit(g, font, caption, getX() + w / 2, cy, w + 12, !active ? 0xFFA0A0A8 : stopMode ? 0xFFFF9A90 : 0xFFFFE680);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}

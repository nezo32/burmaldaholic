package dev.nezo.burmaldaholic.client.pvp.kit;

import dev.nezo.burmaldaholic.client.fx.FxSounds;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Casino button of the extras / PvP screens (global.md §2.2 {@code CasinoButton}): the generated
 * {@code core/widget/casino_button[_primary|_danger][_highlighted|_disabled]} nine-slices, hover lift (1 px), press
 * (60 ms, 2 px), the invalid shake (±2 px, 240 ms + {@code ui_deny}), an optional icon (a sheet region) and a
 * "selected" state (the chosen call / risk / kind, drawn highlighted). The label shrinks (never below ½) so Russian
 * always fits.
 *
 * <p>THIN LOCAL ADAPTER: lane J-L2 is building the shared {@code client.ui.CasinoButton}; once it lands this class
 * delegates to it (or is replaced by it) without the screens changing.
 */
public class KitButton extends AbstractButton {
	public enum Style {
		SECONDARY(""),
		PRIMARY("_primary"),
		DANGER("_danger");

		final String suffix;

		Style(String suffix) {
			this.suffix = suffix;
		}
	}

	/** Icon drawn left of the label: a sheet region. */
	public record Icon(Identifier tex, int texW, int texH, int u, int v, int w, int h) {}

	private final Consumer<KitButton> onPress;
	private Style style;
	private boolean selected;
	private @Nullable Icon icon;
	private long pressedAt = -1;
	private long shakeAt = -1;
	private boolean breathe;

	public KitButton(int x, int y, int w, int h, Component label, Style style, Consumer<KitButton> onPress) {
		super(x, y, w, h, label);
		this.style = style;
		this.onPress = onPress;
	}

	public static KitButton of(int x, int y, int w, Component label, Style style, Consumer<KitButton> onPress) {
		return new KitButton(x, y, w, 20, label, style, onPress);
	}

	public KitButton style(Style s) {
		this.style = s;
		return this;
	}

	public KitButton selected(boolean on) {
		this.selected = on;
		return this;
	}

	public KitButton icon(@Nullable Icon i) {
		this.icon = i;
		return this;
	}

	/** Breathing brightness (1 Hz): "Drop!", "Scratch!", Start now with ≥ 2 players. */
	public KitButton breathe(boolean on) {
		this.breathe = on;
		return this;
	}

	public KitButton active(boolean on) {
		this.active = on;
		return this;
	}

	public KitButton tooltip(@Nullable Component c) {
		setTooltip(c == null ? null : Tooltip.create(c));
		return this;
	}

	/** Invalid feedback: shake ±2 px for 240 ms + {@code ui_deny}. */
	public void shake() {
		shakeAt = Util.getMillis();
		FxSounds.play("ui_deny", 1f);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		pressedAt = Util.getMillis();
		onPress.accept(this);
	}

	private Identifier sprite(boolean hovered) {
		String state = !active ? "_disabled" : (hovered || selected) ? "_highlighted" : "";
		return Kit.core("widget/casino_button" + style.suffix + state);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		boolean hovered = active && isHoveredOrFocused();
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		boolean motion = !Kit.reduceMotion();
		if (motion && hovered) y -= 1;
		if (motion && pressedAt >= 0 && now - pressedAt < 60) y += 2;
		if (motion && shakeAt >= 0 && now - shakeAt < 240) {
			double u = (now - shakeAt) / 240.0;
			x += (int) Math.round(2 * Math.sin(u * Math.PI * 6) * (1 - u));
		}
		if (!Kit.sprite(g, sprite(hovered), x, y, w, h)) {
			g.fill(x, y, x + w, y + h, style == Style.PRIMARY ? 0xFFE8B830 : 0xFF3A1A5C);
			Kit.frameRect(g, x, y, w, h, Kit.INK);
		}
		if (breathe && active && motion) {
			double b = 0.5 + 0.5 * Math.sin(now / 1000.0 * Math.PI * 2);
			g.fill(x + 2, y + 2, x + w - 2, y + h - 2, Kit.alpha(0x40FFFFFF, b));
		}
		Font font = Minecraft.getInstance().font;
		boolean primary = style == Style.PRIMARY && active;
		int color = !active ? Kit.BONE_SHADE : primary ? Kit.INK : (selected || hovered) && style == Style.SECONDARY ? Kit.GOLD : Kit.BONE;
		int iw = icon == null ? 0 : icon.w() + 3;
		Component label = getMessage();
		int tw = font.width(label);
		int room = w - 6 - iw;
		int shown = Math.min(tw, room);
		int tx = x + (w - shown - iw) / 2 + iw;
		if (icon != null) {
			Kit.region(g, icon.tex(), icon.texW(), icon.texH(), icon.u(), icon.v(), icon.w(), icon.h(), tx - iw, y + (h - icon.h()) / 2);
		}
		if (!label.getString().isEmpty()) {
			Kit.fit(g, font, label, tx, y + (h - 8) / 2 + (h >= 18 ? 1 : 0), room, color, !primary);
		}
		if (isFocused() && !isHovered()) Kit.frameRect(g, x - 1, y - 1, w + 2, h + 2, Kit.WHITE);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}

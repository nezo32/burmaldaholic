package dev.nezo.burmaldaholic.client.ui;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.core.ui.UiLayout;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The casino button (global.md §2.2; lane J-L2 kit): sprite families {@link Style#SECONDARY} (purple),
 * {@link Style#PRIMARY} (gold: "the usual choice" — Spin, Deal, Deposit), {@link Style#DANGER} (red: Pay all, Fold
 * all-in, Soul wager) and the round {@link Style#ACTION} chip (big action button). An optional icon sits left of the
 * label (or alone, centred, for icon buttons in the compact layout — give those a tooltip).
 *
 * <p>Motion: hover lift 1 px ({@code outQuad} 80 ms), press depth 1 px for 60 ms, the invalid {@link #shake} (±2 px, 3
 * cycles, 240 ms + {@code ui_deny}); reduce motion keeps the lift and drops the shake. Disabled: no lift, the
 * {@link #disabledReason} becomes the tooltip. Keyboard focus: 1 px {@code glint} outline. A real vanilla widget
 * (focus, narration, Enter / Space).
 *
 * <pre>{@code
 * addRenderableWidget(CasinoButton.builder(Component.translatable("…deal"), b -> deal())
 *     .style(CasinoButton.Style.PRIMARY).icon(CasinoButton.Icon.tab(UiSprites.TabIcon.CASHIER))
 *     .bounds(x, y, CasinoButton.width(font, label, 60, true), 20).build());
 * }</pre>
 */
public class CasinoButton extends AbstractButton {
	public enum Style {
		SECONDARY(UiSprites.BUTTON_SECONDARY, CasinoPalette.BONE, CasinoPalette.GOLD),
		PRIMARY(UiSprites.BUTTON_PRIMARY, CasinoPalette.INK, CasinoPalette.INK),
		DANGER(UiSprites.BUTTON_DANGER, CasinoPalette.BONE, 0xFFFFFFFF),
		ACTION(UiSprites.BUTTON_ACTION, CasinoPalette.BONE, 0xFFFFFFFF);

		final UiSprites.Family sprites;
		final int label;
		final int labelHover;

		Style(UiSprites.Family sprites, int label, int labelHover) {
			this.sprites = sprites;
			this.label = label;
			this.labelHover = labelHover;
		}
	}

	/** An icon: a cell of a code-indexed sheet or an atlas sprite, drawn at {@code w × h}. */
	public record Icon(UiSprites.@Nullable Sheet sheet, @Nullable Identifier sprite, int u, int v, int w, int h) {
		/** A 16-px menu icon (tab_icons). */
		public static Icon tab(UiSprites.TabIcon icon) {
			return new Icon(UiSprites.TAB_ICONS_16, null, icon.ordinal() * 16, 0, 16, 16);
		}

		/** A cell of a sheet. */
		public static Icon sheet(UiSprites.Sheet sheet, int u, int v, int w, int h) {
			return new Icon(sheet, null, u, v, w, h);
		}

		/** An atlas sprite. */
		public static Icon sprite(Identifier id, int w, int h) {
			return new Icon(null, id, 0, 0, w, h);
		}

		void draw(GuiGraphicsExtractor g, int x, int y, boolean active) {
			if (sheet != null) CasinoUi.sheet(g, sheet, u, v, w, h, x, y);
			else if (sprite != null) CasinoUi.sprite(g, sprite, x, y, w, h, active ? 0xFFFFFFFF : 0x99FFFFFF);
			if (!active) g.fill(x, y, x + w, y + h, 0x66140822);
		}
	}

	/** Width for a label (+ icon) by the kit rule {@code max(minWidth, textWidth + 8)}. */
	public static int width(Font font, Component label, int minWidth, boolean icon) {
		return UiLayout.buttonWidth(minWidth, font.width(label), icon ? 16 : 0);
	}

	private final Consumer<CasinoButton> onPress;
	private Style style;
	private @Nullable Icon icon;
	private boolean selected;
	private @Nullable Component disabledReason;
	private @Nullable Tooltip normalTooltip;
	private long hoverSince = -1;
	private long pressedAt = -1;
	private long shakeAt = -1;

	public CasinoButton(int x, int y, int w, int h, Component label, Style style, Consumer<CasinoButton> onPress) {
		super(x, y, w, h, label);
		this.style = style;
		this.onPress = onPress;
	}

	public static Builder builder(Component label, Consumer<CasinoButton> onPress) {
		return new Builder(label, onPress);
	}

	public Style style() {
		return style;
	}

	public CasinoButton style(Style s) {
		this.style = s;
		return this;
	}

	public CasinoButton icon(@Nullable Icon i) {
		this.icon = i;
		return this;
	}

	/** Toggle / chosen state (e.g. the called side of a coin, the chosen risk): gold ring + highlighted sprite. */
	public CasinoButton selected(boolean on) {
		this.selected = on;
		return this;
	}

	public boolean selected() {
		return selected;
	}

	/** Enables / disables; a disabled button shows {@code reason} (if any) as its tooltip. */
	public CasinoButton enabled(boolean on, @Nullable Component reason) {
		this.active = on;
		this.disabledReason = reason;
		applyTooltip();
		return this;
	}

	/** The normal tooltip (e.g. the words of an icon-only button). */
	public CasinoButton tooltip(@Nullable Component text) {
		this.normalTooltip = text == null ? null : Tooltip.create(text);
		applyTooltip();
		return this;
	}

	private void applyTooltip() {
		setTooltip(!active && disabledReason != null ? Tooltip.create(disabledReason) : normalTooltip);
	}

	/** Invalid feedback (server rejection, disabled click): shake + {@code ui_deny}. */
	public void shake() {
		shakeAt = Util.getMillis();
		FxSounds.play("ui_deny", 0.8f, 1f);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		pressedAt = Util.getMillis();
		onPress.accept(this);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		boolean reduced = FxSettings.reduceMotion();
		boolean hot = active && isHoveredOrFocused();
		if (hot && hoverSince < 0) hoverSince = now;
		if (!hot) hoverSince = -1;
		int dy = -UiLayout.hoverLift(hot ? now - hoverSince : -1, reduced) + UiLayout.pressDepth(pressedAt < 0 ? -1 : now - pressedAt);
		int dx = UiLayout.shake(shakeAt < 0 ? -1 : now - shakeAt, reduced);
		int x = getX() + dx;
		int y = getY() + dy;
		int w = getWidth();
		int h = getHeight();
		int alphaMask = ((int) (this.alpha * 255) << 24) | 0xFFFFFF;
		Identifier sprite = style == Style.ACTION && active && pressedAt >= 0 && now - pressedAt < UiLayout.PRESS_MS ? UiSprites.BUTTON_ACTION_PRESSED
			: style.sprites.get(active, hot || selected);
		if (!CasinoUi.sprite(g, sprite, x, y, w, h, alphaMask)) fallback(g, x, y, w, h, hot);
		if (selected && active) g.outline(x - 1, y - 1, w + 2, h + 2, CasinoPalette.GOLD);
		if (isFocused()) g.outline(x - 2, y - 2, w + 4, h + 4, CasinoPalette.GLINT);
		Font font = Minecraft.getInstance().font;
		int color = !active ? CasinoPalette.BONE_SHADE : hot || selected ? style.labelHover : style.label;
		boolean shadow = style != Style.PRIMARY || !active;
		Component label = getMessage();
		boolean hasLabel = !label.getString().isEmpty();
		int iconW = icon == null ? 0 : icon.w();
		if (!hasLabel) {
			if (icon != null) icon.draw(g, x + (w - icon.w()) / 2, y + (h - icon.h()) / 2, active);
			return;
		}
		int room = w - 8 - (iconW > 0 ? iconW + 3 : 0);
		FormattedCharSequence seq = CasinoUi.fit(font, label, room);
		int tw = font.width(seq) + (iconW > 0 ? iconW + 3 : 0);
		int tx = x + (w - tw) / 2;
		if (icon != null) {
			icon.draw(g, tx, y + (h - icon.h()) / 2, active);
			tx += iconW + 3;
		}
		int ty = style == Style.ACTION ? y + (h - 8) / 2 : y + (h - 8) / 2;
		g.text(font, seq, tx, ty, color, shadow);
	}

	private void fallback(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hot) {
		int fill = switch (style) {
			case PRIMARY -> hot ? CasinoPalette.GOLD : 0xFFE8B830;
			case DANGER -> hot ? CasinoPalette.CHIP_RED_LIGHT : CasinoPalette.CHIP_RED;
			default -> hot ? 0xFF4A2474 : 0xFF3A1A5C;
		};
		if (!active) fill = 0xFF2A1640;
		g.fill(x, y, x + w, y + h, fill);
		g.outline(x, y, w, h, active ? (hot ? CasinoPalette.GLINT : CasinoPalette.FRAME) : 0xFF4A3060);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}

	/** Fluent construction. */
	public static final class Builder {
		private final Component label;
		private final Consumer<CasinoButton> onPress;
		private Style style = Style.SECONDARY;
		private @Nullable Icon icon;
		private @Nullable Component tooltip;
		private int x;
		private int y;
		private int w = 60;
		private int h = 20;

		Builder(Component label, Consumer<CasinoButton> onPress) {
			this.label = label;
			this.onPress = onPress;
		}

		public Builder style(Style s) {
			this.style = s;
			return this;
		}

		public Builder icon(@Nullable Icon i) {
			this.icon = i;
			return this;
		}

		public Builder tooltip(@Nullable Component t) {
			this.tooltip = t;
			return this;
		}

		public Builder bounds(int x, int y, int w, int h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			return this;
		}

		public Builder pos(int x, int y) {
			this.x = x;
			this.y = y;
			return this;
		}

		public Builder size(int w, int h) {
			this.w = w;
			this.h = h;
			return this;
		}

		public CasinoButton build() {
			CasinoButton b = new CasinoButton(x, y, w, h, label, style, onPress);
			b.icon(icon);
			if (tooltip != null) b.tooltip(tooltip);
			return b;
		}
	}
}

package dev.nezo.burmaldaholic.client.table.cards;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Card-table console button (docs/design/visual/cards.md §8.1): the themed secondary family
 * ({@code button/table_<theme>[_highlighted|_disabled]}, nine-slice 4), or the core primary / danger families (a gold
 * button always means "the usual choice"), a 12 × 12 icon left of the label (dimmed when disabled), hover lift, 60 ms
 * press, the invalid shake (±2 px, 240 ms, {@code ui_deny}). Width = icon + label + padding (Russian fits); the label
 * shrinks (never below ½) if the width is capped.
 *
 * <p>Thin local adapter for the shared {@code CasinoButton} (lane J-L2): same families and feedback; switch when the
 * shared kit lands.
 */
public final class CardButton extends AbstractButton {
	public enum Family {
		TABLE,
		PRIMARY,
		DANGER
	}

	private final Consumer<CardButton> onPress;
	private Family family;
	private TableTheme theme;
	private @Nullable Identifier icon;
	private long pressedAt = -1;
	private long shakeAt = -1;
	private @Nullable Component hint;

	public CardButton(int x, int y, int w, Component label, @Nullable String icon, Family family, TableTheme theme, Consumer<CardButton> onPress) {
		super(x, y, w, 20, label);
		this.onPress = onPress;
		this.family = family;
		this.theme = theme;
		this.icon = icon == null ? null : FxSprites.sprite("cards/icon/" + icon);
	}

	/** Natural width for {@code label} with an icon. */
	public static int naturalWidth(Font font, Component label, boolean icon) {
		return font.width(label) + (icon ? 14 : 0) + 16;
	}

	public CardButton family(Family f) {
		this.family = f;
		return this;
	}

	public CardButton theme(TableTheme t) {
		this.theme = t;
		return this;
	}

	/** Tooltip (drawn by the screen in screen coordinates). */
	public CardButton hint(@Nullable Component h) {
		this.hint = h;
		return this;
	}

	public @Nullable Component hint() {
		return hint;
	}

	/** Invalid click: shake + {@code ui_deny}. */
	public void shake() {
		shakeAt = Util.getMillis();
		FxSounds.play("ui_deny", 1f);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		pressedAt = Util.getMillis();
		onPress.accept(this);
	}

	private Identifier background(boolean hover) {
		String state = !active ? "_disabled" : hover ? "_highlighted" : "";
		return switch (family) {
			case TABLE -> FxSprites.sprite("cards/button/table_" + theme.id + state);
			case PRIMARY -> FxSprites.sprite("widget/casino_button_primary" + state);
			case DANGER -> FxSprites.sprite("widget/casino_button_danger" + state);
		};
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		boolean hover = active && isHoveredOrFocused();
		int dy = hover ? -1 : 0;
		if (pressedAt >= 0 && now - pressedAt < 60) dy = 1;
		int dx = 0;
		if (shakeAt >= 0 && now - shakeAt < 240) {
			double u = (now - shakeAt) / 240.0;
			dx = (int) Math.round(2 * Math.sin(u * Math.PI * 6) * (1 - u));
		}
		int x = getX() + dx;
		int y = getY() + dy;
		int w = getWidth();
		int h = getHeight();
		int fallback = family == Family.PRIMARY ? CasinoPalette.GOLD : family == Family.DANGER ? CasinoPalette.CHIP_RED : 0xFF3A2A4A;
		CardGfx.sprite(g, background(hover), x, y, w, h, 0xFFFFFFFF, active ? fallback : 0xFF3A3440);
		if (isFocused()) CardGfx.frame(g, x - 1, y - 1, w + 2, h + 2, 0xFFFFFFFF);
		Font font = Minecraft.getInstance().font;
		Component label = getMessage();
		int iw = icon != null ? 14 : 0;
		int avail = w - 8 - iw;
		int tw = Math.min(font.width(label), avail);
		int tx = x + (w - tw - iw) / 2;
		if (icon != null) {
			CardGfx.sprite(g, icon, tx, y + 4, 12, 12, active ? 0xFFFFFFFF : 0xFF7A6A8A);
			tx += iw;
		}
		int color = !active ? 0xFF8A7A9A : family == Family.PRIMARY ? 0xFF3A1A00 : CasinoPalette.BONE;
		CardGfx.fitted(g, font, label, tx, y + 6, avail, color, active && family != Family.PRIMARY);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}

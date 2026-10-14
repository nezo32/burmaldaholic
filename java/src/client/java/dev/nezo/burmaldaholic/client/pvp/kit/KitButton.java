package dev.nezo.burmaldaholic.client.pvp.kit;

import dev.nezo.burmaldaholic.client.ui.CasinoButton;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.client.ui.v2.V2Button;
import dev.nezo.burmaldaholic.client.ui.v2.V2Sprites;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import dev.nezo.burmaldaholic.client.ui.UiSprites;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The J-L2 kit {@link CasinoButton} with the extras / PvP conveniences: a fluent {@link #active}, a sheet
 * {@link Icon} from any of this lane's code-indexed sheets, and the 1 Hz {@link #breathe} glow of the "advance"
 * buttons (Drop!, Scratch!, Start now with two players; extras-pvp.md §6.2, §8.2, §9.3). Drawn in the table UI v2 look
 * (lane E, ui-v2.md §4.2: the kit's {@code V2Button} sprites — primary gold, normal purple, danger red; selected = gold),
 * so every PvP and extras button matches the v2 Dock; behaviour (press, disabled reason tooltip, focus) is the kit's.
 */
public class KitButton extends CasinoButton {
	/** The kit styles (secondary purple, primary gold, danger red). */
	public enum Style {
		SECONDARY(CasinoButton.Style.SECONDARY),
		PRIMARY(CasinoButton.Style.PRIMARY),
		DANGER(CasinoButton.Style.DANGER);

		final CasinoButton.Style kit;

		Style(CasinoButton.Style kit) {
			this.kit = kit;
		}
	}

	/** An icon left of the label: a region of a code-indexed sheet ({@code texW × texH}). */
	public record Icon(Identifier tex, int texW, int texH, int u, int v, int w, int h) {
		CasinoButton.Icon kit() {
			return CasinoButton.Icon.sheet(new UiSprites.Sheet(tex, texW, texH), u, v, w, h);
		}
	}

	private boolean breathe;
	private @Nullable Icon iconShown;

	public KitButton(int x, int y, int w, int h, Component label, Style style, Consumer<KitButton> onPress) {
		super(x, y, w, h, label, style.kit, b -> onPress.accept((KitButton) b));
	}

	public static KitButton of(int x, int y, int w, Component label, Style style, Consumer<KitButton> onPress) {
		return new KitButton(x, y, w, 20, label, style, onPress);
	}

	public KitButton style(Style s) {
		super.style(s.kit);
		return this;
	}

	@Override
	public KitButton selected(boolean on) {
		super.selected(on);
		return this;
	}

	public KitButton icon(@Nullable Icon i) {
		this.iconShown = i;
		super.icon(i == null ? null : i.kit());
		return this;
	}

	/** Breathing brightness (1 Hz) while the server waits for this button. */
	public KitButton breathe(boolean on) {
		this.breathe = on;
		return this;
	}

	public KitButton active(boolean on) {
		this.active = on;
		return this;
	}

	@Override
	public KitButton tooltip(@Nullable Component c) {
		super.tooltip(c);
		return this;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Box box = new Box(getX(), getY(), getWidth(), getHeight());
		V2Sprites.ButtonStyle st = style() == CasinoButton.Style.DANGER ? V2Sprites.ButtonStyle.DANGER
			: style() == CasinoButton.Style.PRIMARY || (selected() && active) ? V2Sprites.ButtonStyle.PRIMARY : V2Sprites.ButtonStyle.NORMAL;
		boolean hot = active && isHoveredOrFocused();
		Font font = Minecraft.getInstance().font;
		Component label = getMessage();
		if (iconShown == null) {
			V2Button.draw(g, font, box, label, null, st, active, hot, false, isFocused());
		} else {
			// the background only, then the icon and the label centred together
			V2Button.draw(g, font, box, Component.empty(), null, st, active, hot, false, isFocused());
			boolean hasLabel = !label.getString().isEmpty();
			int room = getWidth() - 8 - iconShown.w() - (hasLabel ? 3 : 0);
			FormattedCharSequence seq = CasinoUi.fit(font, label, Math.max(0, room));
			int tw = iconShown.w() + (hasLabel ? 3 + font.width(seq) : 0);
			int x = getX() + (getWidth() - tw) / 2;
			Kit.region(g, iconShown.tex(), iconShown.texW(), iconShown.texH(), iconShown.u(), iconShown.v(), iconShown.w(), iconShown.h(), x,
				getY() + (getHeight() - iconShown.h()) / 2, iconShown.w(), iconShown.h(), active ? 0xFFFFFFFF : 0x80FFFFFF);
			if (hasLabel) {
				int color = !active ? 0xFF8A7A9C : st == V2Sprites.ButtonStyle.PRIMARY ? 0xFF180A28 : 0xFFF4ECF8;
				g.text(font, seq, x + iconShown.w() + 3, getY() + (getHeight() - 8) / 2, color, st != V2Sprites.ButtonStyle.PRIMARY);
			}
		}
		if (breathe && active && !Kit.reduceMotion()) {
			double b = 0.5 + 0.5 * Math.sin(Util.getMillis() / 1000.0 * Math.PI * 2);
			g.fill(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + getHeight() - 2, Kit.alpha(0x38FFFFFF, b));
		}
	}
}

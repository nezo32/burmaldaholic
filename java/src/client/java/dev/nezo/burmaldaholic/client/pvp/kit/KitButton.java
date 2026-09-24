package dev.nezo.burmaldaholic.client.pvp.kit;

import dev.nezo.burmaldaholic.client.ui.CasinoButton;
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
 * buttons (Drop!, Scratch!, Start now with two players; extras-pvp.md §6.2, §8.2, §9.3). Everything else (sprites,
 * hover lift, press, shake, selected ring, disabled reason, focus) is the kit's.
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
		super.extractContents(g, mouseX, mouseY, a);
		if (breathe && active && !Kit.reduceMotion()) {
			double b = 0.5 + 0.5 * Math.sin(Util.getMillis() / 1000.0 * Math.PI * 2);
			g.fill(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + getHeight() - 2, Kit.alpha(0x38FFFFFF, b));
		}
	}
}

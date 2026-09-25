package dev.nezo.burmaldaholic.games.extras.client.pvp.coin;

import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Base of the extras PvP panels (Coin Flip Duel, Wheel Party, their set-ups; visual/extras.md §7): the arena scene
 * (the grudge variant when {@link #grudge} says so) as a 400 × 240 panel that grows downwards on a casino card only
 * when wrapped Russian lines need it, the title at the top left, casino buttons in a wrapping flow and the error line.
 * Server-driven: subclasses render state they were sent and send actions; they never decide.
 */
public abstract class PvpPanel extends dev.nezo.burmaldaholic.client.ui.CasinoScreen {
	public static final int TEXT = Kit.BONE;
	public static final int MUTED = Kit.BONE_SHADE;
	public static final int GOLD = Kit.GOLD;
	public static final int GOOD = Kit.BONUS;
	public static final int ERROR = Kit.RED_LIGHT;
	public static final int PAD = 16;
	public static final int ROW = 22;

	protected int panelWidth;
	protected int panelHeight;
	protected int left;
	protected int top;
	protected int ticks;
	protected float partial;
	protected final long openedAt = Util.getMillis();

	protected PvpPanel(Component title, int width, int height) {
		super(title, Scene.W, Scene.H);
		this.panelWidth = Scene.W;
		this.panelHeight = Scene.H;
	}

	/** Red-lit arena (grudge match). */
	protected boolean grudge() {
		return false;
	}

	@Override
	protected boolean showBanner() {
		return false;
	}

	@Override
	protected boolean showBalance() {
		return false;
	}

	@Override
	protected int errorY() {
		return top + Math.min(panelHeight, Scene.H) - 52;
	}

	/** The compact layout at small GUI sizes: the full 400 × 240 scene drawn at a lower whole GUI scale (FitScaled). */
	@Override
	protected boolean fitToScreen() {
		return true;
	}

	@Override
	protected void init() {
		super.init();
		panelWidth = Scene.W;
		panelHeight = Scene.H;
		left = Scene.left(width);
		top = Scene.top(height);
		int bottom = layout();
		int needed = bottom - top + 14;
		if (needed > panelHeight) {
			panelHeight = needed;
			clearWidgets();
			top = Math.max(0, (height - panelHeight) / 2);
			layout();
		}
	}

	/** Adds the widgets; returns the absolute y below the content. */
	protected abstract int layout();

	/** Draws the content (text, art) above the background and below the widgets. */
	protected abstract void content(GuiGraphicsExtractor g, int mouseX, int mouseY);

	/** Over the widgets (bubbles, banners). */
	protected void overlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {}

	protected void rebuild() {
		if (minecraft != null) {
			rebuildWidgets();
		}
	}

	@Override
	public void tick() {
		super.tick();
		ticks++;
	}

	@Override
	protected void extractScene(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Scene sc = grudge() ? Scene.PVP_GRUDGE : Scene.PVP;
		if (panelHeight > Scene.H) Scene.card(g, left + 6, top + Scene.H - 20, Scene.W - 12, panelHeight - Scene.H + 20);
		sc.backdrop(g, left, top);
		playArea(g, mouseX, mouseY);
		sc.frame(g, font, left, top, null);
	}

	/** Objects on the backdrop under the frame. */
	protected void playArea(GuiGraphicsExtractor g, int mouseX, int mouseY) {}

	@Override
	protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		partial = a;
		Kit.fit(g, font, title, left + PAD, top + 15, panelWidth - 2 * PAD - 120, GOLD, true);
		content(g, mouseX, mouseY);
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		overlay(g, mouseX, mouseY);
	}

	/** Word-wrapped text with shadow; returns the y below it. */
	protected int wrap(GuiGraphicsExtractor g, Component text, int x, int y, int w, int color) {
		for (FormattedCharSequence line : font.split(text, w)) {
			g.text(font, line, x, y, color, true);
			y += font.lineHeight;
		}
		return y;
	}

	protected int wrappedHeight(Component text, int w) {
		return font.split(text, w).size() * font.lineHeight;
	}

	/** A digits-only amount box. */
	protected EditBox amountBox(int x, int y, int w, long value) {
		EditBox box = new EditBox(font, x, y, w, 20, Component.translatable("gui.burmaldaholic.common.amount"));
		box.setMaxLength(12);
		box.setValue(Long.toString(Math.max(0, value)));
		box.setResponder(v -> {
			if (!v.chars().allMatch(Character::isDigit)) {
				box.setValue(v.replaceAll("\\D", ""));
			}
		});
		box.setHint(Component.translatable("gui.burmaldaholic.common.amount"));
		return addRenderableWidget(box);
	}

	protected static long parse(@Nullable EditBox box, long fallback) {
		if (box == null) {
			return fallback;
		}
		try {
			return Long.parseLong(box.getValue().trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	protected static Component chips(long n) {
		return Texts.chips(n);
	}

	/** Seconds label for a tick count ({@code unit.burmaldaholic.second}). */
	protected static Component seconds(long ticks) {
		return Texts.plural("unit.burmaldaholic.second", Math.max(0, (ticks + 19) / 20));
	}

	/** Flow layout for casino buttons: each as wide as its label needs; rows wrap instead of truncating. */
	protected final class Flow {
		private final int x0;
		private final int right;
		private int x;
		private int y;

		public Flow(int y) {
			this(left + PAD, y, panelWidth - 2 * PAD);
		}

		public Flow(int x0, int y, int width) {
			this.x0 = x0;
			this.right = x0 + width;
			this.x = x0;
			this.y = y;
		}

		public KitButton button(Component label, int minWidth, Consumer<KitButton> onPress) {
			return button(label, minWidth, KitButton.Style.SECONDARY, onPress);
		}

		public KitButton button(Component label, int minWidth, KitButton.Style style, Consumer<KitButton> onPress) {
			int w = Math.min(right - x0, Math.max(minWidth, font.width(label) + 12));
			int at = reserve(w);
			KitButton b = KitButton.of(at, y, w, label, style, onPress);
			addRenderableWidget(b);
			return b;
		}

		/** Reserves {@code w}×20 at the current position and returns its x. */
		public int reserve(int w) {
			if (x + w > right && x > x0) {
				newRow();
			}
			int at = x;
			x += w + 3;
			return at;
		}

		public int y() {
			return y;
		}

		public void newRow() {
			if (x > x0) {
				x = x0;
				y += ROW;
			}
		}

		public int bottom() {
			return x > x0 ? y + ROW : y;
		}
	}
}

package dev.nezo.burmaldaholic.games.extras.client.pvp.coin;

import dev.nezo.burmaldaholic.core.text.Texts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Base of the extras PvP panels (Coin Flip Duel, Wheel Party): felt panel, title, an auto-growing height so wrapped
 * Russian lines fit (UI.md §0.1: widths {@code max(min, text + 10)}, rows wrap), a flow layout for buttons and a
 * 60-tick error line. Server-driven: subclasses render state they were sent and send actions; they never decide.
 */
public abstract class PvpPanel extends Screen {
	public static final int FELT = 0xFF1E3A5E;
	public static final int FELT_BORDER = 0xFF0E1C2E;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int MUTED = 0xFFCCCCCC;
	public static final int GOLD = 0xFFFFD700;
	public static final int GOOD = 0xFF55FF55;
	public static final int ERROR = 0xFFFF5555;
	public static final int PAD = 8;
	public static final int ROW = 22;

	protected int panelWidth;
	protected int panelHeight;
	protected int left;
	protected int top;
	protected int ticks;
	protected float partial;
	private @Nullable Component error;
	private int errorTicks;

	protected PvpPanel(Component title, int width, int height) {
		super(title);
		this.panelWidth = width;
		this.panelHeight = height;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(panelWidth, width - 8);
		left = (width - panelWidth) / 2;
		top = Math.max(4, (height - panelHeight) / 2);
		int bottom = layout();
		int needed = bottom - top + 16;
		if (needed > panelHeight) {
			panelHeight = needed;
			clearWidgets();
			top = Math.max(4, (height - panelHeight) / 2);
			layout();
		}
	}

	/** Adds the widgets; returns the absolute y below the content. */
	protected abstract int layout();

	/** Draws the content (text, art) above the background and below the widgets. */
	protected abstract void content(GuiGraphicsExtractor g, int mouseX, int mouseY);

	public void showError(Component message) {
		error = message;
		errorTicks = 80;
	}

	protected void rebuild() {
		if (minecraft != null) {
			rebuildWidgets();
		}
	}

	@Override
	public void tick() {
		ticks++;
		if (errorTicks > 0 && --errorTicks == 0) {
			error = null;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, FELT_BORDER);
		g.fill(left, top, left + panelWidth, top + panelHeight, FELT);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		partial = a;
		g.text(font, title, left + PAD, top + 6, GOLD, true);
		content(g, mouseX, mouseY);
		super.extractRenderState(g, mouseX, mouseY, a);
		if (error != null) {
			wrap(g, error, left + PAD, top + panelHeight - 12, panelWidth - 2 * PAD, ERROR);
		}
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

	/** Flow layout for buttons: each as wide as its label needs; rows wrap instead of truncating. */
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

		public Button button(Component label, int minWidth, Button.OnPress onPress) {
			int w = Math.min(right - x0, Math.max(minWidth, font.width(label) + 10));
			int at = reserve(w);
			return addRenderableWidget(Button.builder(label, onPress).bounds(at, y, w, 20).build());
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

package dev.nezo.burmaldaholic.games.extras.client;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Flow layout for buttons (UI.md §0.1): each button is as wide as its translated label needs and rows wrap
 * instead of truncating, so Russian labels (≈1.45× English) always fit. Coordinates are absolute screen pixels.
 */
final class Flow {
	static final int ROW = 22;
	private final Font font;
	private final Consumer<AbstractWidget> adder;
	private final int left;
	private final int right;
	private int x;
	private int y;

	Flow(Font font, Consumer<AbstractWidget> adder, int left, int top, int width) {
		this.font = font;
		this.adder = adder;
		this.left = left;
		this.right = left + width;
		this.x = left;
		this.y = top;
	}

	int width(Component label, int minWidth) {
		return Math.max(minWidth, font.width(label) + 10);
	}

	Button button(Component label, int minWidth, Button.OnPress onPress) {
		int w = Math.min(right - left, width(label, minWidth));
		if (x + w > right && x > left) {
			newRow();
		}
		Button b = Button.builder(label, onPress).bounds(x, y, w, 20).build();
		adder.accept(b);
		x += w + 3;
		return b;
	}

	/** Reserves {@code w}×20 at the current position (e.g. for a text box) and returns its x. */
	int reserve(int w) {
		if (x + w > right && x > left) {
			newRow();
		}
		int at = x;
		x += w + 3;
		return at;
	}

	void newRow() {
		if (x > left) {
			x = left;
			y += ROW;
		}
	}

	/** Moves down by {@code px} (after finishing the current row). */
	void gap(int px) {
		newRow();
		y += px;
	}

	int y() {
		return y;
	}

	/** Y just below the current row. */
	int bottom() {
		return x > left ? y + ROW : y;
	}
}

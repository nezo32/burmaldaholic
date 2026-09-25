package dev.nezo.burmaldaholic.bots.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Flow layout (UI.md §0.1): every button is as wide as its translated label needs and rows wrap instead of
 * truncating, so Russian labels (≈ 1.45 × English) always fit; text is word-wrapped. Collects the widgets
 * and the text items so the screen can scroll them. Coordinates are absolute screen pixels.
 */
final class FlowLayout {
	static final int ROW = 22;

	/** One text line to draw. */
	record Text(FormattedCharSequence text, int x, int y, int color) {}

	private final Font font;
	private final int left;
	private final int right;
	private int x;
	private int y;
	final List<AbstractWidget> widgets = new ArrayList<>();
	final List<Text> texts = new ArrayList<>();

	FlowLayout(Font font, int left, int top, int width) {
		this.font = font;
		this.left = left;
		this.right = left + width;
		this.x = left;
		this.y = top;
	}

	int width() {
		return right - left;
	}

	Button button(Component label, int minWidth, Button.OnPress onPress) {
		int w = Math.min(width(), Math.max(minWidth, font.width(label) + 10));
		if (x + w > right && x > left) {
			newRow();
		}
		Button b = Button.builder(label, onPress).bounds(x, y, w, 20).build();
		widgets.add(b);
		x += w + 3;
		return b;
	}

	/** Short text on the current button row (vertically centred). */
	void inline(Component text, int color) {
		int w = font.width(text);
		if (x + w > right && x > left) {
			newRow();
		}
		texts.add(new Text(text.getVisualOrderText(), x, y + 6, color));
		x += w + 6;
	}

	/** A word-wrapped paragraph on its own lines. */
	void line(Component text, int color) {
		newRow();
		for (FormattedCharSequence seq : font.split(text, width())) {
			texts.add(new Text(seq, left, y, color));
			y += font.lineHeight + 1;
		}
		y += 2;
	}

	void newRow() {
		if (x > left) {
			x = left;
			y += ROW;
		}
	}

	void gap(int px) {
		newRow();
		y += px;
	}

	int bottom() {
		return x > left ? y + ROW : y;
	}
}

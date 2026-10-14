package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import java.util.function.ToIntFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The {@code − value +} stepper of the Dock (raise amount, buy-in; docs/architecture/ui-v2.md §4.2): a slot of the
 * ACTIONS Dock left of the action buttons, laid out by {@code DockLayout} so its targets survive every relayout (review
 * R-AB M3). HitMap ids {@link #MINUS}, {@link #VALUE}, {@link #PLUS}; a press on − / + reaches the host as
 * {@code Host.onGate("stepper.minus" | "stepper.plus", gate)}, a disabled one shows its reason; the mouse wheel over
 * the value is − / +. Keys: the two logical key names ({@code KeyMap}) given to {@link #set} (null = none).
 */
public final class Stepper {
	public static final String MINUS = "stepper.minus", VALUE = "stepper.value", PLUS = "stepper.plus";
	/** Gap between the − / value / + parts. */
	public static final int GAP = 2;

	private @Nullable Gate minus, plus;
	private Component value = Component.empty();
	private @Nullable String keyMinus, keyPlus;
	private Box box = Box.EMPTY, minusBox = Box.EMPTY, valueBox = Box.EMPTY, plusBox = Box.EMPTY;
	private String pressedId = "";
	private long pressedAt = -1;
	ToIntFunction<String> shake = id -> 0;

	/** Shows the stepper: the − and + gates, the value between them, and the keys of − / + (logical names, null = none). */
	public void set(Gate minus, Gate plus, Component value, @Nullable String keyMinus, @Nullable String keyPlus) {
		this.minus = minus;
		this.plus = plus;
		this.value = value;
		this.keyMinus = keyMinus;
		this.keyPlus = keyPlus;
	}

	/** Hides the stepper (no Dock slot, no targets). */
	public void clear() {
		minus = null;
		plus = null;
		box = minusBox = valueBox = plusBox = Box.EMPTY;
	}

	public boolean visible() {
		return minus != null && plus != null;
	}

	public @Nullable Gate minus() {
		return minus;
	}

	public @Nullable Gate plus() {
		return plus;
	}

	public Component value() {
		return value;
	}

	/** The gate of {@link #MINUS} / {@link #PLUS}, or null. */
	public @Nullable Gate gate(String id) {
		return MINUS.equals(id) ? minus : PLUS.equals(id) ? plus : null;
	}

	/** The id a key (logical name, {@code KeyMap}) presses, or null. */
	public @Nullable String idForKey(@Nullable String key) {
		if (!visible() || key == null) return null;
		if (key.equals(keyMinus)) return MINUS;
		if (key.equals(keyPlus)) return PLUS;
		return null;
	}

	public @Nullable String keyOf(String id) {
		return MINUS.equals(id) ? keyMinus : PLUS.equals(id) ? keyPlus : null;
	}

	/** Slot width: two square buttons of the tier's button height and the value (at least "888 888" wide). */
	public int measure(Font font, Tier tier) {
		if (!visible()) return 0;
		return 2 * tier.buttonH + 2 * GAP + valueW(font);
	}

	private int valueW(Font font) {
		return Math.max(font.width(value), font.width(Texts.raw("888 888"))) + 8;
	}

	public Box box() {
		return box;
	}

	void place(Box slot) {
		box = slot;
		if (slot.isEmpty() || !visible()) {
			minusBox = valueBox = plusBox = Box.EMPTY;
			return;
		}
		int s = slot.h();
		minusBox = new Box(slot.x(), slot.y(), s, s);
		plusBox = new Box(slot.right() - s, slot.y(), s, s);
		valueBox = new Box(minusBox.right() + GAP, slot.y(), Math.max(0, plusBox.x() - GAP - minusBox.right() - GAP), s);
	}

	void hits(HitMap hits) {
		if (!visible() || box.isEmpty()) return;
		hits.add(MINUS, minusBox, minus.enabled());
		hits.add(VALUE, valueBox, true);
		hits.add(PLUS, plusBox, plus.enabled());
	}

	void pressed(String id) {
		pressedId = id;
		pressedAt = Util.getMillis();
	}

	public void draw(GuiGraphicsExtractor g, Font font, int mx, int my, String focusedId) {
		if (!visible() || box.isEmpty()) return;
		long now = Util.getMillis();
		drawButton(g, font, MINUS, minusBox, minus, Texts.raw("−"), mx, my, now, focusedId);
		drawButton(g, font, PLUS, plusBox, plus, Texts.raw("+"), mx, my, now, focusedId);
		Box v = valueBox.offset(shake.applyAsInt(VALUE), 0);
		V2Draw.pill(g, v, CasinoPalette.V2_INK, CasinoPalette.V2_PANEL_HI);
		V2Draw.centred(g, font, value, v, 1, CasinoPalette.V2_GOLD, true);
		if (VALUE.equals(focusedId)) V2Draw.outline(g, valueBox.inset(-1), CasinoPalette.V2_GOLD);
	}

	private void drawButton(GuiGraphicsExtractor g, Font font, String id, Box b, Gate gate, Component glyph, int mx, int my, long now,
			String focusedId) {
		Box at = b.offset(shake.applyAsInt(id), 0);
		V2Button.draw(g, font, at, glyph, null, V2Sprites.ButtonStyle.NORMAL, gate.enabled(), b.contains(mx, my), id.equals(pressedId) && now - pressedAt < 120,
			id.equals(focusedId));
	}
}

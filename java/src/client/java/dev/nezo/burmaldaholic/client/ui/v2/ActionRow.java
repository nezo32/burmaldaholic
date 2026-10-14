package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The decision buttons left of the primary (Hit / Stand / Double …, Fold / Call / Raise): HitMap ids
 * {@code action.<action>}, equal widths (tier minimum), each with its key plate unless the Dock is narrow (compact);
 * danger style for {@code fold} / {@code surrender} (or {@link #danger}). ≤ 5 buttons (docs/architecture/ui-v2.md §4.2).
 */
public final class ActionRow {
	public static final int MAX = 5;

	private List<Gate> gates = List.of();
	private KeyMap keys = new KeyMap();
	private List<Box> boxes = List.of();
	private boolean compact;
	private Set<String> danger = Set.of("fold", "surrender");
	private String pressedId = "";
	private long pressedAt = -1;
	java.util.function.ToIntFunction<String> shake = id -> 0;

	public void set(List<Gate> gates, KeyMap keys) {
		if (gates.size() > MAX) throw new IllegalArgumentException("at most " + MAX + " actions, got " + gates.size());
		this.gates = List.copyOf(gates);
		this.keys = keys;
	}

	public void set(List<Gate> gates) {
		set(gates, keys);
	}

	/** Actions drawn in the danger style (default fold, surrender). */
	public void danger(String... actions) {
		danger = Set.of(actions);
	}

	public List<Gate> gates() {
		return gates;
	}

	public static String id(Gate g) {
		return KeyMap.Ids.action(g.action());
	}

	public List<Box> boxes() {
		return boxes;
	}

	void place(List<Box> boxes, boolean compact) {
		this.boxes = List.copyOf(boxes);
		this.compact = compact;
	}

	/** Full widths (label + key plate). */
	public int[] measure(Font font) {
		int[] w = new int[gates.size()];
		for (int i = 0; i < w.length; i++) w[i] = V2Button.width(font, V2Draw.label(gates.get(i)), V2Button.keyName(keys.keyOf(id(gates.get(i)))));
		return w;
	}

	/** Widths without key plates. */
	public int[] measureCompact(Font font) {
		int[] w = new int[gates.size()];
		for (int i = 0; i < w.length; i++) w[i] = V2Button.width(font, V2Draw.label(gates.get(i)), null);
		return w;
	}

	void pressed(String id) {
		pressedId = id;
		pressedAt = Util.getMillis();
	}

	public void draw(GuiGraphicsExtractor g, Font font, int mx, int my, String focusedId) {
		long now = Util.getMillis();
		for (int i = 0; i < Math.min(gates.size(), boxes.size()); i++) {
			Gate gate = gates.get(i);
			String id = id(gate);
			Box b = boxes.get(i).offset(shake.applyAsInt(id), 0);
			Component hint = compact ? null : V2Button.keyName(keys.keyOf(id));
			V2Button.draw(g, font, b, V2Draw.label(gate), hint, danger.contains(gate.action()) ? V2Sprites.ButtonStyle.DANGER : V2Sprites.ButtonStyle.NORMAL,
				gate.enabled(), b.contains(mx, my), id.equals(pressedId) && now - pressedAt < 120, id.equals(focusedId));
		}
	}

	/** The gates as a mutable list (Flow output → set). */
	public static List<Gate> of(Gate... gates) {
		return new ArrayList<>(List.of(gates));
	}
}

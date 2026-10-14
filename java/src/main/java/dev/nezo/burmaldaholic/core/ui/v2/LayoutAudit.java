package dev.nezo.burmaldaholic.core.ui.v2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a laid-out screen: every box inside its bounds, no two boxes overlapping unless the pair is declared
 * (e.g. a FeltStack on its roulette cell), and every measured text no wider than its box. Used by the pure layout
 * tests at every size (docs/architecture/ui-v2.md §9.1) and by the debug overlay ({@code F3+B} style) of the gallery.
 */
public final class LayoutAudit {
	private final Box bounds;
	private final Map<String, Box> boxes = new LinkedHashMap<>();
	private final List<String> problems = new ArrayList<>();
	private final Set<String> allowed = new java.util.HashSet<>();

	public LayoutAudit(Box bounds) {
		this.bounds = bounds;
	}

	/** Adds a named box (empty boxes are ignored: hidden elements). */
	public LayoutAudit add(String name, Box b) {
		if (b == null || b.isEmpty()) return this;
		if (boxes.put(name, b) != null) problems.add("duplicate name " + name);
		return this;
	}

	/** Declares that boxes whose names start with {@code a} and {@code b} may overlap. */
	public LayoutAudit allow(String a, String b) {
		allowed.add(a + "|" + b);
		allowed.add(b + "|" + a);
		return this;
	}

	/** A text of measured width {@code textW} drawn into {@code name}'s box with {@code pad} px on each side. */
	public LayoutAudit fits(String name, int textW, int pad) {
		Box b = boxes.get(name);
		if (b != null && textW + 2 * pad > b.w()) problems.add(name + ": text " + textW + "+" + 2 * pad + " > " + b.w());
		return this;
	}

	public List<String> problems() {
		List<String> out = new ArrayList<>(problems);
		List<Map.Entry<String, Box>> list = new ArrayList<>(boxes.entrySet());
		for (Map.Entry<String, Box> e : list) {
			if (!e.getValue().inside(bounds)) out.add(e.getKey() + " " + e.getValue() + " outside " + bounds);
		}
		for (int i = 0; i < list.size(); i++) {
			for (int j = i + 1; j < list.size(); j++) {
				var a = list.get(i);
				var b = list.get(j);
				if (a.getValue().intersects(b.getValue()) && !isAllowed(a.getKey(), b.getKey())) {
					out.add(a.getKey() + " " + a.getValue() + " overlaps " + b.getKey() + " " + b.getValue());
				}
			}
		}
		return out;
	}

	private boolean isAllowed(String a, String b) {
		for (String pair : allowed) {
			int bar = pair.indexOf('|');
			if (a.startsWith(pair.substring(0, bar)) && b.startsWith(pair.substring(bar + 1))) return true;
		}
		return false;
	}
}

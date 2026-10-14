package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Block-state mirroring for {@link Blueprint#mirrorX} (PURE; like vanilla {@code Mirror.FRONT_BACK}, which flips
 * the x axis): {@code facing} east ↔ west, stair {@code shape} and door {@code hinge} / chest {@code type} left ↔
 * right, the {@code east} / {@code west} arms of fences, panes and walls swapped, and the 16-step {@code rotation}
 * of standing banners and signs reflected. Axis and every other property are unchanged.
 */
public final class Mirror {
	private Mirror() {}

	/** The state mirrored across a plane x = const; {@code null} (structure void) stays {@code null}. */
	public static BlockSpec x(BlockSpec b) {
		if (b == null || b.properties().isEmpty()) {
			return b;
		}
		Map<String, String> p = new LinkedHashMap<>(b.properties());
		String f = p.get("facing");
		if ("east".equals(f)) {
			p.put("facing", "west");
		} else if ("west".equals(f)) {
			p.put("facing", "east");
		}
		String shape = p.get("shape");
		if (shape != null && (shape.endsWith("_left") || shape.endsWith("_right"))) {
			p.put("shape", shape.endsWith("_left") ? shape.replace("_left", "_right") : shape.replace("_right", "_left"));
		}
		for (String k : new String[] {"hinge", "type"}) {
			String v = p.get(k);
			if ("left".equals(v)) {
				p.put(k, "right");
			} else if ("right".equals(v)) {
				p.put(k, "left");
			}
		}
		if (p.containsKey("east") && p.containsKey("west")) {
			String e = p.get("east");
			p.put("east", p.get("west"));
			p.put("west", e);
		}
		String rot = p.get("rotation");
		if (rot != null) {
			p.put("rotation", Integer.toString(Math.floorMod(16 - Integer.parseInt(rot), 16)));
		}
		return new BlockSpec(b.name(), p);
	}
}

package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import java.util.ArrayList;
import java.util.List;

/**
 * One palette entry: the full block plus its shaped forms (any may be {@code null} when the material has
 * none — the DSL then falls back to the full block). Block ids are plain vanilla ids; the FULL block may carry
 * fixed state properties in brackets ({@code minecraft:waxed_copper_bulb[lit=true,powered=false]}), which
 * {@link #block()} writes into the state.
 */
public record Material(String full, String stairs, String slab) {
	public static Material of(String full) {
		return new Material(full, null, null);
	}

	public static Material of(String full, String stairs, String slab) {
		return new Material(full, stairs, slab);
	}

	/** Block id of the full block without its bracketed properties. */
	public String id() {
		int i = full.indexOf('[');
		return i < 0 ? full : full.substring(0, i);
	}

	public BlockSpec block() {
		int i = full.indexOf('[');
		if (i < 0) {
			return BlockSpec.of(full);
		}
		if (!full.endsWith("]")) {
			throw new IllegalArgumentException("bad material state " + full);
		}
		List<String> kv = new ArrayList<>();
		for (String p : full.substring(i + 1, full.length() - 1).split(",")) {
			String[] e = p.split("=", 2);
			if (e.length != 2) {
				throw new IllegalArgumentException("bad material property '" + p + "' in " + full);
			}
			kv.add(e[0].trim());
			kv.add(e[1].trim());
		}
		return BlockSpec.of(full.substring(0, i), kv.toArray(String[]::new));
	}

	/** Stairs whose back (high side) faces {@code facing}; {@code top} = upside-down. */
	public BlockSpec stairs(Facing facing, boolean top) {
		if (stairs == null) {
			return block();
		}
		return BlockSpec.of(stairs, "facing", facing.id(), "half", top ? "top" : "bottom", "shape", "straight",
			"waterlogged", "false");
	}

	/** Slab ({@code bottom}, {@code top} or {@code double}). */
	public BlockSpec slab(String type) {
		if (slab == null) {
			return block();
		}
		return BlockSpec.of(slab, "type", type, "waterlogged", "false");
	}

	/** True when the material has a real stair form. */
	public boolean hasStairs() {
		return stairs != null;
	}

	/** True when the material has a real slab form. */
	public boolean hasSlab() {
		return slab != null;
	}
}

package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;

/**
 * One palette entry: the full block plus its shaped forms (any may be {@code null} when the material has
 * none — the DSL then falls back to the full block). Block ids are plain vanilla ids WITHOUT properties.
 */
public record Material(String full, String stairs, String slab) {
	public static Material of(String full) {
		return new Material(full, null, null);
	}

	public static Material of(String full, String stairs, String slab) {
		return new Material(full, stairs, slab);
	}

	public BlockSpec block() {
		return BlockSpec.of(full);
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
}

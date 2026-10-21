package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.Facing;

/**
 * A reusable furniture prefab. {@code (x, y, z)} is the kit's anchor on the floor row (y = the first air
 * row above the floor); {@code front} is the side its users approach from. A kit only calls public
 * {@link Blueprint} verbs, so it inherits palette resolution, bounds checks and game bookkeeping.
 */
@FunctionalInterface
public interface Kit {
	void apply(Blueprint b, int x, int y, int z, Facing front);
}

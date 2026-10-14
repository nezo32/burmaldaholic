package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import java.util.List;

/**
 * The Overworld casino styles. STUB owned by lane CB-3 (architecture doc §2): lane CB-2 ships it with
 * TUDOR only so {@link OverworldPieces} compiles; CB-3 replaces this file with all five styles.
 */
public final class OverworldStyles {
	private OverworldStyles() {}

	public static List<Style> all() {
		return List.of(Style.TUDOR);
	}
}

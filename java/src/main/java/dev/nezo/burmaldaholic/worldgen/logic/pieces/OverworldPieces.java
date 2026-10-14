package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import java.util.List;

/**
 * STUB from lane CB-1 (platform) so {@code CasinoPack} compiles before lane CB-2 lands. Lane CB-2 owns this
 * file and replaces it: {@code all()} = OverworldStyles.all() × OverworldCasino pieces (ids
 * {@code casino/<style>/{entrance,floor_a,floor_b,bar,office,vip}}). Keep the signature.
 */
public final class OverworldPieces {
	private OverworldPieces() {}

	public static List<Piece> all() {
		return List.of();
	}
}

package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.List;

/**
 * Output of a {@link Blueprint}: the exportable {@link Layout} (unchanged format, so TemplateExporter,
 * CasinoElements and CasinoMarkers keep working) plus the design metadata the checks and tests need.
 *
 * @param games     every game block with the side its players stand on
 * @param keepClear volumes that must stay air (in-world table boards, aisles, door throats)
 * @param interior  volumes that count as "inside" for the light / mob-spawn check
 */
public record Piece(Layout layout, List<GameSpot> games, List<Geometry.Box> keepClear, List<Geometry.Box> interior) {
	public Piece {
		games = List.copyOf(games);
		keepClear = List.copyOf(keepClear);
		interior = List.copyOf(interior);
	}

	public String id() {
		return layout.id();
	}

	/** A game block at {@code pos}; {@code playerSide} is where players stand (= the block's FACING). */
	public record GameSpot(Game game, Vec pos, Facing playerSide) {}
}

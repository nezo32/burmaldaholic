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
 * @param aisles    walking aisles (2 rows tall boxes on the floor row) that must stay walkable, ≥ 2 wide
 * @param entries   floor-row cells where people enter the piece (door, jigsaw doorways); every game's
 *                  player cell must be reachable on foot from one of them
 */
public record Piece(Layout layout, List<GameSpot> games, List<Geometry.Box> keepClear, List<Geometry.Box> interior,
		List<Geometry.Box> aisles, List<Vec> entries) {
	public Piece {
		games = List.copyOf(games);
		keepClear = List.copyOf(keepClear);
		interior = List.copyOf(interior);
		aisles = List.copyOf(aisles);
		entries = List.copyOf(entries);
	}

	/** Skeleton-era constructor (no aisles, no entries). */
	public Piece(Layout layout, List<GameSpot> games, List<Geometry.Box> keepClear, List<Geometry.Box> interior) {
		this(layout, games, keepClear, interior, List.of(), List.of());
	}

	public String id() {
		return layout.id();
	}

	/** A game block at {@code pos}; {@code playerSide} is where players stand (= the block's FACING). */
	public record GameSpot(Game game, Vec pos, Facing playerSide) {
		/** The cell a player stands in to use the game. */
		public Vec playerCell() {
			return new Vec(pos.x() + playerSide.dx(), pos.y(), pos.z() + playerSide.dz());
		}
	}
}

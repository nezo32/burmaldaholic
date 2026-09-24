package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.List;

/**
 * One casino building template. Local coordinates: x/z in [0, size), y = 0 is the floor layer; the
 * main entrance is on the +z (south) face when unrotated.
 *
 * @param id       template id; exported as {@code burmaldaholic:worldgen/<id>}
 * @param grid     the shell (vanilla blocks + game tables); {@code null} = structure void
 * @param markers  data markers (NPCs, chests, casino anchor) placed as DATA structure blocks
 * @param jigsaws  jigsaw connectors (village casinos attach to village streets)
 */
public record Layout(String id, CasinoKind kind, Grid grid, List<PlacedMarker> markers, List<Jigsaw> jigsaws) {
	public Layout {
		markers = List.copyOf(markers);
		jigsaws = List.copyOf(jigsaws);
	}

	public Vec size() {
		return grid.size();
	}

	/** Structure template / pool id path ({@code worldgen/<id>}). */
	public String templatePath() {
		return "worldgen/" + id;
	}

	public record PlacedMarker(Vec pos, Markers.Marker marker) {}

	/**
	 * A jigsaw block. {@code orientation} is the vanilla jigsaw state value (e.g. {@code south_up});
	 * {@code finalState} replaces the jigsaw after placement.
	 */
	public record Jigsaw(Vec pos, String orientation, String name, String target, String pool, String finalState, String joint) {}
}

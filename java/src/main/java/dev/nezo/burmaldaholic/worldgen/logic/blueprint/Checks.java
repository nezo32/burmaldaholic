package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static design checks of a {@link Piece} (PURE; run by {@code BlueprintChecksTest} for every generated
 * piece — a failing check blocks the merge). The in-game GameTests re-check light and clearance with the
 * real engine; these catch mistakes in milliseconds.
 */
public final class Checks {
	private Checks() {}

	/** Block light emitted by blocks the palettes use (vanilla values). Unknown blocks emit 0. */
	static final Map<String, Integer> EMISSION = Map.ofEntries(
		Map.entry("minecraft:glowstone", 15), Map.entry("minecraft:sea_lantern", 15),
		Map.entry("minecraft:ochre_froglight", 15), Map.entry("minecraft:verdant_froglight", 15),
		Map.entry("minecraft:pearlescent_froglight", 15), Map.entry("minecraft:shroomlight", 15),
		Map.entry("minecraft:lantern", 15), Map.entry("minecraft:soul_lantern", 10),
		Map.entry("minecraft:end_rod", 14), Map.entry("minecraft:torch", 14), Map.entry("minecraft:wall_torch", 14),
		Map.entry("minecraft:jack_o_lantern", 15), Map.entry("minecraft:crying_obsidian", 10),
		Map.entry("minecraft:copper_bulb", 15), Map.entry("minecraft:campfire", 15));

	/** Blocks light passes through (approximation; the GameTest measures the real value). */
	static boolean transparent(BlockSpec b) {
		if (b == null) {
			return false; // structure void: unknown world block, assume solid
		}
		String n = b.name();
		return n.equals("minecraft:air") || n.endsWith("_carpet") || n.endsWith("glass_pane") || n.endsWith("_glass")
			|| n.equals("minecraft:glass") || n.equals("minecraft:iron_bars") || n.endsWith("chain")
			|| n.endsWith("lantern") || n.startsWith("minecraft:potted_") || n.equals("minecraft:end_rod");
	}

	static boolean air(BlockSpec b) {
		return b != null && b.is("minecraft:air");
	}

	/** All problems found; empty = pass. */
	public static List<String> run(Piece p) {
		List<String> out = new ArrayList<>();
		Grid g = p.layout().grid();
		Vec s = g.size();
		for (Piece.GameSpot spot : p.games()) {
			Vec v = spot.pos();
			BlockSpec below = v.y() > 0 ? g.get(v.x(), v.y() - 1, v.z()) : null;
			if (below == null || air(below)) {
				out.add(spot.game() + " at " + v + " floats (no floor below)");
			}
			int fx = v.x() + spot.playerSide().dx(), fz = v.z() + spot.playerSide().dz();
			if (g.inside(fx, v.y(), fz) && !walkable(g.get(fx, v.y(), fz))) {
				out.add(spot.game() + " at " + v + ": player cell " + fx + "," + v.y() + "," + fz + " is blocked by " + g.get(fx, v.y(), fz));
			}
		}
		for (Geometry.Box box : p.keepClear()) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int y = box.minY(); y <= box.maxY(); y++) {
					for (int z = box.minZ(); z <= box.maxZ(); z++) {
						if (g.inside(x, y, z) && !walkable(g.get(x, y, z)) && !isGame(p, x, y, z)) {
							out.add("keep-clear cell " + x + "," + y + "," + z + " holds " + g.get(x, y, z));
						}
					}
				}
			}
		}
		for (Layout.PlacedMarker m : p.layout().markers()) {
			Vec v = m.pos();
			if (!g.inside(v.x(), v.y(), v.z())) {
				out.add("marker outside the piece: " + m.marker().metadata());
			}
		}
		int[][][] light = light(g);
		for (Geometry.Box box : p.interior()) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int y = box.minY(); y <= box.maxY(); y++) {
					for (int z = box.minZ(); z <= box.maxZ(); z++) {
						if (air(g.get(x, y, z)) && light[x][y][z] < 1 && solidBelow(g, x, y, z)) {
							out.add("dark spawnable cell " + x + "," + y + "," + z);
						}
					}
				}
			}
		}
		for (Piece.GameSpot spot : p.games()) {
			Vec v = spot.pos();
			int y = Math.min(v.y() + 1, s.y() - 1);
			if (light[v.x()][y][v.z()] < 10) {
				out.add(spot.game() + " at " + v + " is lit " + light[v.x()][y][v.z()] + " (< 10 above the table)");
			}
		}
		return out;
	}

	/** Families present in a set of pieces (an assembled casino). */
	public static Set<Game.Family> families(Collection<Piece> pieces) {
		Set<Game.Family> f = EnumSet.noneOf(Game.Family.class);
		for (Piece p : pieces) {
			for (Piece.GameSpot spot : p.games()) {
				f.add(spot.game().family());
			}
		}
		return f;
	}

	private static boolean isGame(Piece p, int x, int y, int z) {
		for (Piece.GameSpot spot : p.games()) {
			if (spot.pos().x() == x && spot.pos().y() == y && spot.pos().z() == z) {
				return true;
			}
		}
		return false;
	}

	static boolean walkable(BlockSpec b) {
		return b != null && (air(b) || b.name().endsWith("_carpet"));
	}

	private static boolean solidBelow(Grid g, int x, int y, int z) {
		if (y == 0) {
			return false;
		}
		BlockSpec b = g.get(x, y - 1, z);
		return b != null && !transparent(b);
	}

	/** Flood-filled block light: every source spreads, −1 per step, through transparent cells. */
	static int[][][] light(Grid g) {
		Vec s = g.size();
		int[][][] l = new int[s.x()][s.y()][s.z()];
		ArrayDeque<int[]> q = new ArrayDeque<>();
		for (int x = 0; x < s.x(); x++) {
			for (int y = 0; y < s.y(); y++) {
				for (int z = 0; z < s.z(); z++) {
					BlockSpec b = g.get(x, y, z);
					Integer e = b == null ? null : EMISSION.get(b.name());
					if (e != null && !"false".equals(b.properties().get("lit"))) {
						l[x][y][z] = e;
						q.add(new int[] {x, y, z});
					}
				}
			}
		}
		int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (!q.isEmpty()) {
			int[] c = q.poll();
			int v = l[c[0]][c[1]][c[2]] - 1;
			if (v <= 0) {
				continue;
			}
			for (int[] d : dirs) {
				int x = c[0] + d[0], y = c[1] + d[1], z = c[2] + d[2];
				if (g.inside(x, y, z) && transparent(g.get(x, y, z)) && l[x][y][z] < v) {
					l[x][y][z] = v;
					q.add(new int[] {x, y, z});
				}
			}
		}
		return l;
	}
}

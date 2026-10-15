package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Markers;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static design checks of a {@link Piece} (PURE; run by {@code PieceChecksTest} for every generated
 * piece — a failing check blocks the merge). The in-game GameTests re-check light and clearance with the
 * real engine; these catch mistakes in milliseconds.
 *
 * <p>What is checked (architecture doc §3.4):
 * <ol>
 *   <li>every game block stands on a floor block, its player cell is walkable with head room;</li>
 *   <li>keep-clear volumes (board clearance, door throats) hold only air / carpet / game blocks;</li>
 *   <li>aisles are walkable and at least 2 wide;</li>
 *   <li>light: ≥ 10 above every game block, ≥ 1 on every interior cell a mob could spawn on;</li>
 *   <li>every game's player cell is reachable on foot from an entry (door / jigsaw doorway);</li>
 *   <li>markers inside the piece, NPC feet and chest cells free, decor physically supported
 *       (hanging lanterns, carpets, candles, pots, wall banners) — nothing floats;</li>
 *   <li>clearance of game blocks ({@link TableClearance}): multi-block table rules R1–R6 (integrity, support,
 *       walkway, dealer, spacing, headroom) and the machine side / head cells;</li>
 *   <li>no {@link #FORBIDDEN} (valuable storage) blocks.</li>
 * </ol>
 */
public final class Checks {
	private Checks() {}

	/**
	 * Pieces that still break the clearance rule R1–R6 ({@link TableClearance}) and skip it (only it) until they are
	 * rebuilt: the v0.2 Overworld styles, annexes, Parlor and Lounge predate multi-block tables and are replaced by the
	 * casino redesign (CR). Owners DELETE entries as their pieces pass — never add one.
	 */
	public static final Set<String> PENDING_CLEARANCE = pendingClearance();

	/**
	 * Pieces that still hold {@link #FORBIDDEN} blocks and skip that check until rebuilt (CR / Parlor + Lounge
	 * palettes). Owners DELETE entries — never add one.
	 */
	public static final Set<String> PENDING_VALUABLES = Set.of();

	/**
	 * Storage / trophy blocks players would strip a casino for (resource farming). Waxed copper, chains and bars are
	 * decor and allowed. Exact ids, plus every shulker box ({@link #forbidden}).
	 */
	public static final Set<String> FORBIDDEN = Set.of("minecraft:gold_block", "minecraft:raw_gold_block",
		"minecraft:iron_block", "minecraft:raw_iron_block", "minecraft:diamond_block", "minecraft:emerald_block",
		"minecraft:netherite_block", "minecraft:lapis_block", "minecraft:redstone_block", "minecraft:beacon",
		"minecraft:ancient_debris", "minecraft:dragon_head", "minecraft:dragon_wall_head", "minecraft:dragon_egg",
		"minecraft:light_weighted_pressure_plate", "minecraft:heavy_weighted_pressure_plate");

	public static boolean forbidden(BlockSpec b) {
		return b != null && (FORBIDDEN.contains(b.name()) || b.name().startsWith("minecraft:") && b.name().endsWith("shulker_box"));
	}

	private static Set<String> pendingClearance() {
		Set<String> s = new java.util.LinkedHashSet<>();
		// the retired v0.2 styles (OverworldPieces.legacy, compact by construction); entrance and bar were deleted by CR-1:
		// the Grand Casino reuses those ids and passes R1-R6
		for (String style : new String[] {"tudor", "oasis", "safari", "alpine", "ice_palace"}) {
			for (String part : new String[] {"floor_a", "floor_b", "office", "vip"}) {
				s.add("casino/" + style + "/" + part);
			}
		}
		return java.util.Collections.unmodifiableSet(s);
	}

	/** Block light emitted by blocks the palettes use (vanilla values). Unknown blocks emit 0. */
	static final Map<String, Integer> EMISSION = Map.ofEntries(
		Map.entry("minecraft:glowstone", 15), Map.entry("minecraft:sea_lantern", 15),
		Map.entry("minecraft:ochre_froglight", 15), Map.entry("minecraft:verdant_froglight", 15),
		Map.entry("minecraft:pearlescent_froglight", 15), Map.entry("minecraft:shroomlight", 15),
		Map.entry("minecraft:lantern", 15), Map.entry("minecraft:soul_lantern", 10),
		Map.entry("minecraft:copper_lantern", 15), Map.entry("minecraft:waxed_copper_lantern", 15),
		Map.entry("minecraft:end_rod", 14), Map.entry("minecraft:torch", 14), Map.entry("minecraft:wall_torch", 14),
		Map.entry("minecraft:jack_o_lantern", 15), Map.entry("minecraft:crying_obsidian", 10),
		Map.entry("minecraft:copper_bulb", 15), Map.entry("minecraft:waxed_copper_bulb", 15),
		Map.entry("minecraft:campfire", 15), Map.entry("minecraft:soul_campfire", 10),
		Map.entry("minecraft:glow_lichen", 7), Map.entry("minecraft:magma_block", 3),
		Map.entry("minecraft:beacon", 15), Map.entry("minecraft:conduit", 15), Map.entry("minecraft:lava", 15));

	/** Light emitted by one block state (lit candles: 3 per candle). */
	static int emission(BlockSpec b) {
		if (b == null) {
			return 0;
		}
		if ("false".equals(b.properties().get("lit"))) {
			return 0;
		}
		if (b.name().endsWith("candle")) {
			return "true".equals(b.properties().get("lit")) ? 3 * Integer.parseInt(b.properties().getOrDefault("candles", "1")) : 0;
		}
		return EMISSION.getOrDefault(b.name(), 0);
	}

	/** Blocks light passes through (approximation; the GameTest measures the real value). */
	static boolean transparent(BlockSpec b) {
		return Shapes.transparent(b);
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
			if (below == null || !Shapes.fullCube(below)) {
				out.add(spot.game() + " at " + v + " floats (no floor below)");
			}
			Vec pc = spot.playerCell();
			if (g.inside(pc.x(), pc.y(), pc.z())) {
				if (!walkable(g.get(pc.x(), pc.y(), pc.z()))) {
					out.add(spot.game() + " at " + v + ": player cell " + pc.x() + "," + pc.y() + "," + pc.z() + " is blocked by "
						+ g.get(pc.x(), pc.y(), pc.z()));
				} else if (pc.y() + 1 < s.y() && !Shapes.passable(g.get(pc.x(), pc.y() + 1, pc.z()))) {
					out.add(spot.game() + " at " + v + ": no head room over the player cell (" + g.get(pc.x(), pc.y() + 1, pc.z()) + ")");
				}
			}
		}
		for (Geometry.Box box : p.keepClear()) {
			forEach(box, (x, y, z) -> {
				if (g.inside(x, y, z) && !walkable(g.get(x, y, z)) && !isGame(p, x, y, z)) {
					out.add("keep-clear cell " + x + "," + y + "," + z + " holds " + g.get(x, y, z));
				}
			});
		}
		for (Geometry.Box box : p.aisles()) {
			if (Math.min(box.xSpan(), box.zSpan()) < 2) {
				out.add("aisle " + box + " is narrower than 2");
			}
			forEach(box, (x, y, z) -> {
				if (g.inside(x, y, z) && !walkable(g.get(x, y, z))) {
					out.add("aisle cell " + x + "," + y + "," + z + " holds " + g.get(x, y, z));
				}
			});
		}
		markers(p, g, out);
		support(g, out);
		int[][][] light = light(g);
		for (Geometry.Box box : p.interior()) {
			forEach(box, (x, y, z) -> {
				if (g.inside(x, y, z) && air(g.get(x, y, z)) && light[x][y][z] < 1 && spawnable(g, x, y, z)) {
					out.add("dark spawnable cell " + x + "," + y + "," + z);
				}
			});
		}
		for (Piece.GameSpot spot : p.games()) {
			Vec v = lightProbe(g, spot);
			int y = Math.min(v.y(), s.y() - 1);
			if (g.inside(v.x(), y, v.z()) && light[v.x()][y][v.z()] < 10) {
				out.add(spot.game() + " at " + spot.pos() + " is lit " + light[v.x()][y][v.z()] + " (< 10 at " + v + ")");
			}
		}
		if (!PENDING_CLEARANCE.contains(p.id())) {
			TableClearance.check(p, out);
		}
		if (!PENDING_VALUABLES.contains(p.id())) {
			forEach(new Geometry.Box(0, 0, 0, s.x() - 1, s.y() - 1, s.z() - 1), (x, y, z) -> {
				if (forbidden(g.get(x, y, z))) {
					out.add("forbidden block " + g.get(x, y, z) + " at " + x + "," + y + "," + z);
				}
			});
		}
		if (!p.entries().isEmpty()) {
			boolean[][][] reach = reachable(g, p.entries());
			for (Piece.GameSpot spot : p.games()) {
				Vec pc = spot.playerCell();
				if (g.inside(pc.x(), pc.y(), pc.z()) && !reach[pc.x()][pc.y()][pc.z()]) {
					out.add(spot.game() + " at " + spot.pos() + ": player cell not reachable on foot from the entries");
				}
			}
		}
		return out;
	}

	/**
	 * Where a game's light is measured: the cell above a table's main block (inside the board volume); for an upright
	 * multi-block machine, whose upper cells are the machine itself, the player's head cell in front of it.
	 */
	public static Vec lightProbe(Grid g, Piece.GameSpot spot) {
		Vec v = spot.pos();
		if (spot.game().machineKind() != null && !TableClearance.compact(g, spot)) {
			Vec pc = spot.playerCell();
			return new Vec(pc.x(), pc.y() + 1, pc.z());
		}
		return new Vec(v.x(), v.y() + 1, v.z());
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

	private interface CellVisitor {
		void visit(int x, int y, int z);
	}

	private static void forEach(Geometry.Box box, CellVisitor v) {
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int y = box.minY(); y <= box.maxY(); y++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					v.visit(x, y, z);
				}
			}
		}
	}

	private static void markers(Piece p, Grid g, List<String> out) {
		for (Layout.PlacedMarker m : p.layout().markers()) {
			Vec v = m.pos();
			if (!g.inside(v.x(), v.y(), v.z())) {
				out.add("marker outside the piece: " + m.marker().metadata());
				continue;
			}
			if (m.marker() instanceof Markers.Npc) {
				BlockSpec feet = v.y() > 0 ? g.get(v.x(), v.y() - 1, v.z()) : null;
				BlockSpec head = g.get(v.x(), v.y(), v.z());
				if (!Shapes.passable(feet) || !(head == null || Shapes.passable(head))) {
					out.add("NPC marker " + v + " has no room to stand (" + feet + " / " + head + ")");
				}
			} else if (m.marker() instanceof Markers.Chest) {
				BlockSpec below = v.y() > 0 ? g.get(v.x(), v.y() - 1, v.z()) : null;
				if (!Shapes.fullCube(below)) {
					out.add("chest marker " + v + " has no floor");
				}
			}
		}
	}

	/** Decor that would pop off or float in the world. */
	private static void support(Grid g, List<String> out) {
		Vec s = g.size();
		for (int x = 0; x < s.x(); x++) {
			for (int y = 0; y < s.y(); y++) {
				for (int z = 0; z < s.z(); z++) {
					BlockSpec b = g.get(x, y, z);
					if (b == null) {
						continue;
					}
					String n = b.name();
					BlockSpec below = y > 0 ? g.get(x, y - 1, z) : null;
					BlockSpec above = y + 1 < s.y() ? g.get(x, y + 1, z) : null;
					String problem = null;
					if (n.endsWith("lantern") && !n.equals("minecraft:sea_lantern")) {
						problem = "true".equals(b.properties().get("hanging"))
							? (hangsFrom(above) ? null : "hangs from nothing")
							: (standsOn(below) ? null : "stands on nothing");
					} else if (Shapes.isCarpet(n) || n.endsWith("_pressure_plate")) {
						problem = below == null || air(below) ? "floats" : null;
					} else if (n.endsWith("candle") || n.contains("potted_") || n.equals("minecraft:flower_pot")) {
						problem = standsOn(below) ? null : "stands on nothing";
					} else if (n.endsWith("azalea")) {
						problem = below != null && (below.name().contains("moss") || below.name().contains("dirt")
							|| below.name().contains("grass") || below.name().contains("clay")) ? null : "needs moss / dirt below";
					} else if (n.endsWith("_wall_banner")) {
						Facing f = Facing.byId(b.properties().get("facing"));
						int bx = x - f.dx(), bz = z - f.dz();
						BlockSpec wall = g.inside(bx, y, bz) ? g.get(bx, y, bz) : null;
						problem = wall == null || Shapes.fullCube(wall) ? null : "has no wall behind (" + wall + ")";
					}
					if (problem != null) {
						out.add(n + " at " + x + "," + y + "," + z + " " + problem);
					}
				}
			}
		}
	}

	private static boolean hangsFrom(BlockSpec above) {
		if (above == null) {
			return true; // world block above (void): cannot know, assume the builder meant it
		}
		String n = above.name();
		return Shapes.fullCube(above) || n.endsWith("chain") || Shapes.isFence(n) || n.endsWith("_slab")
			|| n.endsWith("_stairs") || n.endsWith("_wall") || n.endsWith("_trapdoor") || n.endsWith("lightning_rod")
			|| n.equals("minecraft:end_rod") || Shapes.isPane(n);
	}

	private static boolean standsOn(BlockSpec below) {
		if (below == null) {
			return true;
		}
		String n = below.name();
		return Shapes.fullCube(below) || Shapes.isFence(n) || n.endsWith("_wall") || n.endsWith("_slab") && !"bottom".equals(
			below.properties().get("type")) || n.endsWith("_stairs") && "top".equals(below.properties().get("half"))
			|| n.endsWith("chain") || n.startsWith("burmaldaholic:") || n.endsWith("_trapdoor") && "top".equals(
			below.properties().get("half")) && !"true".equals(below.properties().get("open"));
	}

	private static boolean isGame(Piece p, int x, int y, int z) {
		for (Piece.GameSpot spot : p.games()) {
			for (int[] c : TableClearance.cells3d(spot)) {
				if (c[0] == x && c[1] == y && c[2] == z) {
					return true;
				}
			}
		}
		return false;
	}

	static boolean walkable(BlockSpec b) {
		return b != null && (air(b) || Shapes.isCarpet(b.name()));
	}

	/** Mobs spawn on top of a full block (approximation: also the top half of stairs / top slabs). */
	private static boolean spawnable(Grid g, int x, int y, int z) {
		if (y == 0) {
			return false;
		}
		BlockSpec b = g.get(x, y - 1, z);
		return b != null && (Shapes.fullCube(b) && !b.name().endsWith("glass") && !b.name().endsWith("_leaves")
			|| b.name().endsWith("_slab") && "top".equals(b.properties().get("type")));
	}

	/**
	 * Floor-row cells a player can walk to from the entries: stand where the cell and the one above are
	 * passable and the block below is solid; step to a neighbour on the same row or one up / down.
	 */
	static boolean[][][] reachable(Grid g, List<Vec> entries) {
		Vec s = g.size();
		boolean[][][] seen = new boolean[s.x()][s.y()][s.z()];
		ArrayDeque<int[]> q = new ArrayDeque<>();
		for (Vec e : entries) {
			if (g.inside(e.x(), e.y(), e.z())) {
				seen[e.x()][e.y()][e.z()] = true;
				q.add(new int[] {e.x(), e.y(), e.z()});
			}
		}
		while (!q.isEmpty()) {
			int[] c = q.poll();
			for (Facing f : Facing.values()) {
				for (int dy = -1; dy <= 1; dy++) {
					int x = c[0] + f.dx(), y = c[1] + dy, z = c[2] + f.dz();
					if (!g.inside(x, y, z) || seen[x][y][z] || !standable(g, x, y, z)) {
						continue;
					}
					if (dy == 1 && !passableOrOut(g, c[0], c[1] + 2, c[2])) {
						continue; // jumping up needs head room at the start
					}
					if (dy == -1 && !passableOrOut(g, x, y + 2, z)) {
						continue;
					}
					seen[x][y][z] = true;
					q.add(new int[] {x, y, z});
				}
			}
		}
		return seen;
	}

	private static boolean standable(Grid g, int x, int y, int z) {
		if (y == 0) {
			return false;
		}
		BlockSpec below = g.get(x, y - 1, z);
		return Shapes.passable(g.get(x, y, z)) && passableOrOut(g, x, y + 1, z) && below != null && !Shapes.passable(below);
	}

	private static boolean passableOrOut(Grid g, int x, int y, int z) {
		return !g.inside(x, y, z) || Shapes.passable(g.get(x, y, z));
	}

	/** Flood-filled block light: every source spreads, −1 per step, through transparent cells. */
	public static int[][][] light(Grid g) {
		Vec s = g.size();
		int[][][] l = new int[s.x()][s.y()][s.z()];
		ArrayDeque<int[]> q = new ArrayDeque<>();
		for (int x = 0; x < s.x(); x++) {
			for (int y = 0; y < s.y(); y++) {
				for (int z = 0; z < s.z(); z++) {
					int e = emission(g.get(x, y, z));
					if (e > 0) {
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

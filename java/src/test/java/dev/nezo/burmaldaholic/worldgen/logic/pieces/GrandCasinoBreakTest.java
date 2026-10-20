package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.Assembly;
import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.Nbt;
import dev.nezo.burmaldaholic.worldgen.logic.TemplateExporter;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Checks;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Shapes;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.TableClearance;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Break tests from the T-CR1 tester, adopted by CR-1. They are stricter than {@code Checks}: walk reachability with
 * real collision heights and no stuck spots, every felt cell lit >= 12, no floating or unsupported blocks, no
 * valuables within the cell budget, contained fountain water, and byte-identical exports.
 */
class GrandCasinoBreakTest {
	static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** Collision top; doors/gates count as open; game blocks unclimbable (1.5). */
	static double ct(BlockSpec b) {
		if (b == null) {
			return 1.0;
		}
		String n = b.name();
		if (n.endsWith("_door") || n.endsWith("_fence_gate")) {
			return 0;
		}
		if (n.startsWith("burmaldaholic:")) {
			return 1.5;
		}
		return Shapes.collisionTop(b);
	}

	record Node(int x, int z, int f16) {}

	static double ctAt(Grid g, int x, int y, int z) {
		if (y >= g.size().y()) {
			return 0;
		}
		return g.inside(x, y, z) ? ct(g.get(x, y, z)) : 1.0;
	}

	/** Feet heights (in 1/16) of every standable spot of a column. */
	static Map<Long, List<Integer>> nodes(Grid g) {
		Vec s = g.size();
		Map<Long, List<Integer>> out = new HashMap<>();
		for (int x = 0; x < s.x(); x++) {
			for (int z = 0; z < s.z(); z++) {
				Set<Integer> seen = new HashSet<>();
				for (int y = 1; y < s.y(); y++) {
					double c = ct(g.get(x, y, z));
					double feet;
					if (c == 0) {
						double cb = ct(g.get(x, y - 1, z));
						if (cb == 0) {
							continue;
						}
						feet = y - 1 + cb;
					} else if (c <= 0.5) {
						feet = y + c;
					} else {
						continue;
					}
					boolean head = true;
					for (int yy = (int) Math.floor(feet) + 1; yy <= (int) Math.floor(feet + 1.8 - 1e-6); yy++) {
						if (ctAt(g, x, yy, z) != 0) {
							head = false;
						}
					}
					if (feet - Math.floor(feet) > 0 && ctAt(g, x, (int) Math.floor(feet), z) > feet - Math.floor(feet) + 1e-6) {
						head = false;
					}
					int f16 = (int) Math.round(feet * 16);
					if (head && seen.add(f16)) {
						out.computeIfAbsent(key(x, z), k -> new ArrayList<>()).add(f16);
					}
				}
			}
		}
		return out;
	}

	static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xffffffffL);
	}

	static boolean clear(Grid g, int x, int z, double from, double to) {
		for (int yy = (int) Math.floor(from) + 1; yy <= (int) Math.floor(to - 1e-6); yy++) {
			if (ctAt(g, x, yy, z) != 0) {
				return false;
			}
		}
		return true;
	}

	/** Can a player go from a to b (adjacent columns)? Up ≤ 1.25 (jump), down any height. */
	static boolean move(Grid g, Node a, Node b) {
		double fa = a.f16() / 16.0, fb = b.f16() / 16.0;
		double dh = fb - fa;
		if (dh > 1.25 + 1e-6) {
			return false;
		}
		if (dh > 0.6) {
			// jump: head room over the start up to the landing height + body
			if (!clear(g, a.x(), a.z(), fa, fb + 1.8)) {
				return false;
			}
		}
		if (dh < 0) {
			// walk off the edge: the target column must be open from the landing up to the start body
			if (!clear(g, b.x(), b.z(), fb, fa + 1.8)) {
				return false;
			}
		}
		return true;
	}

	static List<Node> neighbours(Grid g, Map<Long, List<Integer>> nodes, Node n) {
		List<Node> out = new ArrayList<>();
		for (int[] d : DIRS) {
			List<Integer> fs = nodes.get(key(n.x() + d[0], n.z() + d[1]));
			if (fs == null) {
				continue;
			}
			for (int f : fs) {
				Node m = new Node(n.x() + d[0], n.z() + d[1], f);
				if (move(g, n, m)) {
					out.add(m);
				}
			}
		}
		return out;
	}

	static Node nodeAt(Map<Long, List<Integer>> nodes, int x, int y, int z) {
		List<Integer> fs = nodes.get(key(x, z));
		if (fs == null) {
			return null;
		}
		for (int f : fs) {
			if (f >= (y - 1) * 16 + 8 && f <= y * 16 + 8) {
				return new Node(x, z, f);
			}
		}
		return null;
	}

	@Test
	void everyGameReachableOnFootAndNoStuckSpots() {
		List<String> bad = new ArrayList<>();
		for (GrandCasino.Variant v : GrandCasino.Variant.values()) {
			Piece p = GrandCasino.whole(v).build();
			Grid g = p.layout().grid();
			Vec s = g.size();
			Map<Long, List<Integer>> nodes = nodes(g);
			Set<Node> fwd = new HashSet<>();
			ArrayDeque<Node> q = new ArrayDeque<>();
			for (Vec e : p.entries()) {
				Node n = nodeAt(nodes, e.x(), e.y(), e.z());
				assertTrue(n != null, v + " entry " + e + " is not standable");
				fwd.add(n);
				q.add(n);
			}
			while (!q.isEmpty()) {
				for (Node m : neighbours(g, nodes, q.poll())) {
					if (fwd.add(m)) {
						q.add(m);
					}
				}
			}
			for (Piece.GameSpot spot : p.games()) {
				Vec pc = spot.playerCell();
				Node n = nodeAt(nodes, pc.x(), pc.y(), pc.z());
				if (n == null || !fwd.contains(n)) {
					bad.add(v + " " + spot.game() + " at " + spot.pos() + ": player cell " + pc + (n == null ? " not standable" : " unreachable (strict)"));
				}
			}
			// back-reachability: from every forward node, can we get to the entry or off the grid edge?
			Map<Node, List<Node>> rev = new HashMap<>();
			for (Node a : fwd) {
				for (Node b : neighbours(g, nodes, a)) {
					rev.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
				}
			}
			Set<Node> home = new HashSet<>();
			for (Node a : fwd) {
				boolean edge = a.x() == 0 || a.z() == 0 || a.x() == s.x() - 1 || a.z() == s.z() - 1;
				if (edge || p.entries().stream().anyMatch(e -> nodeAt(nodes, e.x(), e.y(), e.z()).equals(a))) {
					home.add(a);
					q.add(a);
				}
			}
			while (!q.isEmpty()) {
				for (Node a : rev.getOrDefault(q.poll(), List.of())) {
					if (home.add(a)) {
						q.add(a);
					}
				}
			}
			int stuck = 0;
			for (Node a : fwd) {
				if (!home.contains(a)) {
					if (stuck++ < 15) {
						bad.add(v + " stuck spot " + a.x() + "," + (a.f16() / 16.0) + "," + a.z());
					}
				}
			}
			if (stuck > 15) {
				bad.add(v + " ... " + stuck + " stuck spots total");
			}
			System.out.println("[T-CR1] " + v + " forward nodes " + fwd.size() + ", stuck " + stuck);
		}
		assertEquals(List.of(), bad);
	}

	@Test
	void lightAtLeast12OverEveryFeltCell() {
		List<String> bad = new ArrayList<>();
		for (GrandCasino.Variant v : GrandCasino.Variant.values()) {
			Piece p = GrandCasino.whole(v).build();
			int[][][] light = Checks.light(p.layout().grid());
			int min = 99;
			for (Piece.GameSpot s : p.games()) {
				if (s.game().tableGame() == null) {
					continue;
				}
				for (int[] c : TableClearance.cells3d(s)) {
					int l = light[c[0]][c[1] + 1][c[2]];
					min = Math.min(min, l);
					if (l < 12) {
						bad.add(v + " " + s.game() + " at " + s.pos() + " felt cell " + c[0] + "," + c[1] + "," + c[2] + " lit " + l);
					}
				}
			}
			System.out.println("[T-CR1] " + v + " min felt light " + min);
		}
		assertEquals(List.of(), bad);
	}

	@Test
	void noFloatingIslandsOrUnsupportedGravityBlocks() {
		List<String> bad = new ArrayList<>();
		for (GrandCasino.Variant v : GrandCasino.Variant.values()) {
			List<Piece> all = new ArrayList<>(GrandCasino.pieces(v));
			all.add(0, GrandCasino.whole(v).build());
			for (Piece p : all) {
				Grid g = p.layout().grid();
				Vec s = g.size();
				boolean[][][] seen = new boolean[s.x()][s.y()][s.z()];
				ArrayDeque<int[]> q = new ArrayDeque<>();
				for (int x = 0; x < s.x(); x++) {
					for (int z = 0; z < s.z(); z++) {
						for (int y = 0; y <= 1; y++) {
							BlockSpec b = g.get(x, y, z);
							if (b != null && !b.is("minecraft:air")) {
								seen[x][y][z] = true;
								q.add(new int[] {x, y, z});
							}
						}
					}
				}
				int[][] d6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
				while (!q.isEmpty()) {
					int[] c = q.poll();
					for (int[] d : d6) {
						int x = c[0] + d[0], y = c[1] + d[1], z = c[2] + d[2];
						if (g.inside(x, y, z) && !seen[x][y][z]) {
							BlockSpec b = g.get(x, y, z);
							if (b != null && !b.is("minecraft:air")) {
								seen[x][y][z] = true;
								q.add(new int[] {x, y, z});
							}
						}
					}
				}
				int islands = 0;
				for (int x = 0; x < s.x(); x++) {
					for (int y = 0; y < s.y(); y++) {
						for (int z = 0; z < s.z(); z++) {
							BlockSpec b = g.get(x, y, z);
							if (b == null || b.is("minecraft:air")) {
								continue;
							}
							if (!seen[x][y][z] && p == all.get(0) && islands++ < 10) {
								bad.add(v + " " + p.id() + " floating " + b.name() + " at " + x + "," + y + "," + z);
							}
							String n = b.name();
							boolean gravity = n.equals("minecraft:sand") || n.equals("minecraft:red_sand") || n.equals("minecraft:gravel")
								|| n.endsWith("concrete_powder") || n.endsWith("anvil") || n.startsWith("minecraft:suspicious_")
								|| n.equals("minecraft:pointed_dripstone") || n.equals("minecraft:scaffolding");
							BlockSpec below = y > 0 ? g.get(x, y - 1, z) : null;
							if (gravity && y > 0 && (below != null && (below.is("minecraft:air") || Shapes.passable(below)))) {
								bad.add(v + " " + p.id() + " gravity block " + n + " unsupported at " + x + "," + y + "," + z);
							}
							// blocks that pop off without support below
							boolean needsBelow = Shapes.isCarpet(n) || n.endsWith("candle") || n.contains("potted_") || n.endsWith("_pressure_plate")
								|| n.equals("minecraft:torch") || n.endsWith("_banner") && !n.endsWith("_wall_banner") || n.endsWith("_sapling")
								|| n.endsWith("_rail") || n.equals("minecraft:rail") || n.equals("minecraft:sea_pickle") || n.equals("minecraft:snow")
								|| n.endsWith("_sign") && !n.contains("wall") && !n.contains("hanging");
							if (needsBelow && y > 0 && (below == null ? false : ct(below) == 0 || below.is("minecraft:air"))) {
								bad.add(v + " " + p.id() + " " + n + " has nothing below at " + x + "," + y + "," + z + " (" + below + ")");
							}
							if (n.equals("minecraft:wall_torch") || n.endsWith("_wall_torch") || n.endsWith("_button") || n.endsWith("_wall_sign")
								|| n.equals("minecraft:ladder")) {
								String f = b.properties().getOrDefault("facing", "north");
								int bx = x, bz = z;
								switch (f) {
									case "north" -> bz++;
									case "south" -> bz--;
									case "east" -> bx--;
									default -> bx++;
								}
								BlockSpec w = g.inside(bx, y, bz) ? g.get(bx, y, bz) : null;
								if (w != null && !Shapes.fullCube(w)) {
									bad.add(v + " " + p.id() + " " + n + " at " + x + "," + y + "," + z + " has no wall (" + w + ")");
								}
							}
						}
					}
				}
			}
		}
		assertEquals(List.of(), bad);
	}

	@Test
	void blockInventoryAndValuables() {
		List<String> bad = new ArrayList<>();
		Set<String> extra = Set.of("minecraft:copper_block", "minecraft:raw_copper_block", "minecraft:amethyst_block",
			"minecraft:enchanting_table", "minecraft:ender_chest", "minecraft:jukebox", "minecraft:conduit", "minecraft:obsidian",
			"minecraft:crying_obsidian", "minecraft:respawn_anchor", "minecraft:lodestone", "minecraft:spawner", "minecraft:budding_amethyst",
			"minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:hopper", "minecraft:tnt", "minecraft:water", "minecraft:lava");
		for (GrandCasino.Variant v : GrandCasino.Variant.values()) {
			Piece p = GrandCasino.whole(v).build();
			Grid g = p.layout().grid();
			Vec s = g.size();
			Map<String, Integer> hist = new TreeMap<>();
			for (int x = 0; x < s.x(); x++) {
				for (int y = 0; y < s.y(); y++) {
					for (int z = 0; z < s.z(); z++) {
						BlockSpec b = g.get(x, y, z);
						if (b == null) {
							continue;
						}
						hist.merge(b.name(), 1, Integer::sum);
						String n = b.name();
						if (Checks.forbidden(b) || extra.contains(n) || n.endsWith("_ore") || n.startsWith("minecraft:raw_") || n.contains("diamond")
							|| n.contains("emerald") || n.contains("netherite") || n.equals("minecraft:gold_block")) {
							bad.add(v + " " + n + " at " + x + "," + y + "," + z);
						}
					}
				}
			}
			if (v == GrandCasino.Variant.RIVIERA || v == GrandCasino.Variant.ICE_PALACE) {
				System.out.println("[T-CR1] " + v + " blocks " + hist);
			}
			int cells = 0;
			for (Piece sl : GrandCasino.pieces(v)) {
				Grid sg = sl.layout().grid();
				for (int x = 0; x < sg.size().x(); x++) {
					for (int y = 0; y < sg.size().y(); y++) {
						for (int z = 0; z < sg.size().z(); z++) {
							cells += sg.get(x, y, z) != null ? 1 : 0;
						}
					}
				}
			}
			System.out.println("[T-CR1] " + v + " non-void cells over the 3 slices " + cells + " (budget " + Assembly.MAX_CELLS + ")");
			if (cells > Assembly.MAX_CELLS) {
				bad.add(v + " non-void cells " + cells + " > " + Assembly.MAX_CELLS);
			}
		}
		List<String> real = bad.stream().filter(x -> !x.contains("burmaldaholic:") && !x.contains("minecraft:barrel") && !x.contains("minecraft:water")).toList();
		System.out.println("[T-CR1] valuables (excl. mod blocks, barrels, fountain water) " + real);
		assertEquals(List.of(), real);
	}

	@Test
	void fountainWaterIsContained() {
		List<String> bad = new ArrayList<>();
		for (GrandCasino.Variant v : GrandCasino.Variant.values()) {
			for (Piece p : GrandCasino.pieces(v)) {
				Grid g = p.layout().grid();
				Vec s = g.size();
				for (int x = 0; x < s.x(); x++) {
					for (int y = 0; y < s.y(); y++) {
						for (int z = 0; z < s.z(); z++) {
							BlockSpec b = g.get(x, y, z);
							if (b == null || !b.is("minecraft:water")) {
								continue;
							}
							int[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
							for (int[] d : n) {
								int xx = x + d[0], yy = y + d[1], zz = z + d[2];
								BlockSpec o = g.inside(xx, yy, zz) ? g.get(xx, yy, zz) : null;
								if (!g.inside(xx, yy, zz) || o == null || o.is("minecraft:air") || ct(o) == 0 && !o.is("minecraft:water")) {
									bad.add(v + " " + p.id() + " water at " + x + "," + y + "," + z + " leaks to " + xx + "," + yy + "," + zz + " (" + o + ")");
								}
							}
						}
					}
				}
			}
		}
		assertEquals(List.of(), bad);
	}

	@Test
	void everyVariantDeterministicAcrossBuilds() {
		for (GrandCasino.Variant v : GrandCasino.Variant.values()) {
			List<Piece> a = GrandCasino.pieces(v);
			List<Piece> b = GrandCasino.pieces(v);
			for (int i = 0; i < a.size(); i++) {
				assertArrayEquals(Nbt.writeGzip(TemplateExporter.template(a.get(i).layout())),
					Nbt.writeGzip(TemplateExporter.template(b.get(i).layout())), v + " " + a.get(i).id());
			}
			byte[] w1 = Nbt.writeGzip(TemplateExporter.template(GrandCasino.whole(v).build().layout()));
			byte[] w2 = Nbt.writeGzip(TemplateExporter.template(GrandCasino.whole(v).build().layout()));
			assertArrayEquals(w1, w2, v + " whole");
		}
	}
}

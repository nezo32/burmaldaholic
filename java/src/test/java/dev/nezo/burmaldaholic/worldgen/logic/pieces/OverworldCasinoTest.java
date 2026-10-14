package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Markers;
import dev.nezo.burmaldaholic.worldgen.logic.Nbt;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.TemplateExporter;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Checks;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Shapes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The Tudor casino pieces (architecture doc §4.1, §7.1) and how they assemble. */
class OverworldCasinoTest {
	private static final Style S = Style.TUDOR;

	private static Map<OverworldCasino.Part, Piece> parts() {
		Map<OverworldCasino.Part, Piece> m = new EnumMap<>(OverworldCasino.Part.class);
		for (OverworldCasino.Part p : OverworldCasino.Part.values()) {
			m.put(p, OverworldCasino.piece(S, p));
		}
		return m;
	}

	private static Map<Game, Long> games(Piece p) {
		return p.games().stream().collect(Collectors.groupingBy(Piece.GameSpot::game, Collectors.counting()));
	}

	private static List<NpcRole> npcs(Piece p) {
		return p.layout().markers().stream().filter(m -> m.marker() instanceof Markers.Npc)
			.map(m -> ((Markers.Npc) m.marker()).role()).toList();
	}

	@Test
	void idsPoolsAndSizes() {
		Map<OverworldCasino.Part, Piece> m = parts();
		assertEquals("casino/tudor/entrance", m.get(OverworldCasino.Part.ENTRANCE).id());
		assertEquals("burmaldaholic:worldgen/casino/tudor/rear", OverworldCasino.poolId(S, OverworldCasino.Part.VIP.pool()));
		assertEquals(2, OverworldCasino.Part.OFFICE.weight());
		int[][] footprints = {{31, 17}, {31, 17}, {31, 17}, {17, 13}, {15, 9}, {17, 13}};
		for (OverworldCasino.Part p : OverworldCasino.Part.values()) {
			Vec size = m.get(p).layout().size();
			assertEquals(footprints[p.ordinal()][0], size.x(), p.name());
			assertEquals(footprints[p.ordinal()][1], size.z(), p.name());
			assertTrue(size.y() <= 32, p.name());
		}
		assertEquals(OverworldPieces.legacy().size(), OverworldStyles.all().size() * OverworldCasino.Part.values().length);
		assertEquals(6, OverworldPieces.forStyle(S).size());
	}

	@Test
	void kindIsTheStandaloneCasinoOnceItExists() {
		CasinoKind k = OverworldCasino.kind();
		assertTrue(k.name().equals("CASINO") || k == CasinoKind.VILLAGE_CASINO);
		for (Piece p : OverworldPieces.all()) {
			assertEquals(k, p.layout().kind());
		}
	}

	@Test
	void everyFullCasinoHasEveryGame() {
		Map<OverworldCasino.Part, Piece> m = parts();
		for (OverworldCasino.Part floor : List.of(OverworldCasino.Part.FLOOR_A, OverworldCasino.Part.FLOOR_B)) {
			Set<Game.Family> f = Checks.families(List.of(m.get(OverworldCasino.Part.ENTRANCE), m.get(floor)));
			assertEquals(Game.Family.REQUIRED, f, floor.name());
			Map<Game, Long> g = games(m.get(floor));
			assertEquals(6L, g.get(Game.SLOTS_COPPER), floor.name());
			assertEquals(4L, g.get(Game.SLOTS_GOLD), floor.name());
			for (Game t : List.of(Game.BLACKJACK, Game.ROULETTE, Game.CRAPS, Game.BACCARAT, Game.UTH, Game.POKER)) {
				assertEquals(1L, g.get(t), floor + " " + t);
			}
			assertEquals(List.of(NpcRole.BACCARAT_DEALER), npcs(m.get(floor)));
		}
		Map<Game, Long> entrance = games(m.get(OverworldCasino.Part.ENTRANCE));
		assertEquals(Map.of(Game.CASHIER, 1L, Game.WHEEL, 1L, Game.PLINKO, 1L), entrance);
		assertEquals(List.of(NpcRole.CROUPIER), npcs(m.get(OverworldCasino.Part.ENTRANCE)));
		assertEquals(Map.of(Game.SLOTS_GOLD, 3L), games(m.get(OverworldCasino.Part.BAR)));
		assertEquals(List.of(NpcRole.LOAN_SHARK), npcs(m.get(OverworldCasino.Part.BAR)));
		assertEquals(Map.of(Game.SLOTS_NETHERITE, 2L, Game.POKER, 1L, Game.BACCARAT_PLAYER_BANKED, 1L, Game.UTH_PLAYER_BANKED, 1L),
			games(m.get(OverworldCasino.Part.VIP)));
		assertTrue(games(m.get(OverworldCasino.Part.OFFICE)).isEmpty());
	}

	@Test
	void connectorsPairUpAndDoorwaysAreOpenOnBothSides() {
		Map<OverworldCasino.Part, Piece> m = parts();
		BlockSpec floor = S.palette().block(Role.FLOOR);
		for (Piece parent : m.values()) {
			for (Layout.Jigsaw j : parent.layout().jigsaws()) {
				assertEquals(OverworldCasino.FLOOR_Y, j.pos().y(), parent.id() + " " + j.name());
				assertEquals(floor.toString(), j.finalState());
				assertEquals("aligned", j.joint());
				assertDoorway(parent, j);
				if (j.pool().equals(OverworldCasino.EMPTY_POOL)) {
					continue;
				}
				List<OverworldCasino.Part> children = List.of(OverworldCasino.Part.values()).stream()
					.filter(p -> OverworldCasino.poolId(S, p.pool()).equals(j.pool())).toList();
				assertFalse(children.isEmpty(), j.pool());
				for (OverworldCasino.Part child : children) {
					Layout.Jigsaw c = m.get(child).layout().jigsaws().stream().filter(x -> x.name().equals(j.target())).findFirst()
						.orElse(null);
					assertNotNull(c, child + " has no connector " + j.target());
					assertEquals("south_up", c.orientation(), child + ": entry connectors are on the +z face");
					assertEquals(m.get(child).layout().size().z() - 1, c.pos().z());
					assertEquals(j.name(), c.target());
				}
			}
		}
	}

	private static void assertDoorway(Piece p, Layout.Jigsaw j) {
		Facing out = Facing.byId(j.orientation().replace("_up", ""));
		Facing along = out.rotate(1);
		Grid g = p.layout().grid();
		for (int t = -1; t <= 1; t++) {
			for (int y = OverworldCasino.ROW; y < OverworldCasino.ROW + 3; y++) {
				int x = j.pos().x() + t * along.dx(), z = j.pos().z() + t * along.dz();
				assertTrue(Shapes.passable(g.get(x, y, z)), p.id() + " doorway " + j.name() + " blocked at " + x + "," + y + "," + z
					+ " by " + g.get(x, y, z));
			}
		}
	}

	// ---- assembly (a light version of CB-1's AssemblyTest, to keep the pieces honest) ------------------

	record Placed(Piece piece, Vec origin, int turns, Geometry.Box box) {}

	private static Placed attach(Placed parent, String connector, Piece child) {
		Layout.Jigsaw pj = parent.piece().layout().jigsaws().stream().filter(j -> j.name().equals(connector)).findFirst().orElseThrow();
		Layout.Jigsaw cj = child.layout().jigsaws().stream().filter(j -> j.name().equals(pj.target())).findFirst().orElseThrow();
		Facing pd = Facing.byId(pj.orientation().replace("_up", "")).rotate(parent.turns());
		Vec p = Geometry.toWorld(parent.origin(), pj.pos(), parent.turns());
		Facing cd = Facing.byId(cj.orientation().replace("_up", ""));
		int k = 0;
		while (cd.rotate(k) != pd.opposite()) {
			k++;
		}
		Vec origin = new Vec(p.x() + pd.dx(), p.y(), p.z() + pd.dz()).subtract(Geometry.rotate(cj.pos(), k));
		return new Placed(child, origin, k, Geometry.templateBox(origin, child.layout().size(), k));
	}

	static List<List<Placed>> assemblies() {
		Map<OverworldCasino.Part, Piece> m = parts();
		List<List<Placed>> out = new ArrayList<>();
		for (OverworldCasino.Part floor : List.of(OverworldCasino.Part.FLOOR_A, OverworldCasino.Part.FLOOR_B)) {
			for (OverworldCasino.Part rear : List.of(OverworldCasino.Part.OFFICE, OverworldCasino.Part.VIP)) {
				for (int turns = 0; turns < 4; turns++) {
					Piece e = m.get(OverworldCasino.Part.ENTRANCE);
					Placed start = new Placed(e, Vec.ZERO, turns, Geometry.templateBox(Vec.ZERO, e.layout().size(), turns));
					Placed f = attach(start, OverworldCasino.ENTRANCE_BACK, m.get(floor));
					Placed bar = attach(f, OverworldCasino.FLOOR_SIDE, m.get(OverworldCasino.Part.BAR));
					Placed r = attach(f, OverworldCasino.FLOOR_BACK, m.get(rear));
					out.add(List.of(start, f, bar, r));
				}
			}
		}
		return out;
	}

	@Test
	void assembliesFitWithoutOverlapAndWithinTheBudget() {
		for (List<Placed> a : assemblies()) {
			String name = a.stream().map(p -> p.piece().id()).collect(Collectors.joining("+")) + " r" + a.getFirst().turns();
			for (int i = 0; i < a.size(); i++) {
				for (int j = i + 1; j < a.size(); j++) {
					assertFalse(a.get(i).box().intersects(a.get(j).box()), name + ": " + a.get(i).piece().id() + " overlaps "
						+ a.get(j).piece().id());
				}
				assertEquals(a.getFirst().box().minY(), a.get(i).box().minY(), name + ": floor rows differ");
			}
			int minX = a.stream().mapToInt(p -> p.box().minX()).min().orElseThrow();
			int maxX = a.stream().mapToInt(p -> p.box().maxX()).max().orElseThrow();
			int minZ = a.stream().mapToInt(p -> p.box().minZ()).min().orElseThrow();
			int maxZ = a.stream().mapToInt(p -> p.box().maxZ()).max().orElseThrow();
			assertTrue(maxX - minX + 1 <= 47 && maxZ - minZ + 1 <= 47, name + " footprint " + (maxX - minX + 1) + "x" + (maxZ - minZ + 1));
			Geometry.Box s = a.getFirst().box();
			for (Placed p : a) {
				int reach = Math.max(Math.max(Math.abs(p.box().minX() - s.centerX()), Math.abs(p.box().maxX() - s.centerX())),
					Math.max(Math.abs(p.box().minZ() - s.centerZ()), Math.abs(p.box().maxZ() - s.centerZ())));
				assertTrue(reach <= 48, name + " reach " + reach);
			}
			List<Piece> pieces = a.stream().map(Placed::piece).toList();
			assertEquals(Game.Family.REQUIRED, Checks.families(pieces), name);
			int gamesCount = pieces.stream().mapToInt(p -> p.games().size()).sum();
			long npcCount = pieces.stream().mapToLong(p -> npcs(p).size()).sum();
			long chests = pieces.stream().flatMap(p -> p.layout().markers().stream()).filter(x -> x.marker() instanceof Markers.Chest).count();
			assertTrue(gamesCount <= 40, name + " games " + gamesCount);
			assertTrue(npcCount <= 6, name + " npcs " + npcCount);
			assertTrue(chests <= 2, name + " chests " + chests);
			long cells = 0;
			for (Piece p : pieces) {
				Grid g = p.layout().grid();
				Vec sz = g.size();
				for (int x = 0; x < sz.x(); x++) {
					for (int y = 0; y < sz.y(); y++) {
						for (int z = 0; z < sz.z(); z++) {
							cells += g.get(x, y, z) == null ? 0 : 1;
						}
					}
				}
			}
			assertTrue(cells <= 60_000, name + " cells " + cells);
		}
	}

	@Test
	void silhouetteHasThreeLevels() {
		Piece e = OverworldCasino.piece(S, OverworldCasino.Part.ENTRANCE);
		Piece f = OverworldCasino.piece(S, OverworldCasino.Part.FLOOR_A);
		int row = OverworldCasino.ROW;
		assertTrue(topAt(e, 15, 14) - row <= 6, "portico ≤ 6 above the floor");
		assertTrue(topAt(f, 0, 8) - row >= 10 && topAt(f, 0, 8) - row <= 14, "hall eaves 10..14");
		assertTrue(highest(e) - row >= 15, "entrance crown ≥ 15 above the floor");
		assertTrue(highest(f) - row >= 15, "lantern ≥ 15 above the floor");
	}

	private static int topAt(Piece p, int x, int z) {
		Grid g = p.layout().grid();
		for (int y = g.size().y() - 1; y >= 0; y--) {
			if (g.get(x, y, z) != null && !g.get(x, y, z).is("minecraft:air")) {
				return y;
			}
		}
		return -1;
	}

	private static int highest(Piece p) {
		int best = -1;
		Vec s = p.layout().size();
		for (int x = 0; x < s.x(); x++) {
			for (int z = 0; z < s.z(); z++) {
				best = Math.max(best, topAt(p, x, z));
			}
		}
		return best;
	}

	@Test
	void marqueeSpellsCasinoOnTheFront() {
		Grid g = OverworldCasino.piece(S, OverworldCasino.Part.ENTRANCE).layout().grid();
		int lit = 0;
		for (int x = 0; x < 31; x++) {
			for (int y = 0; y < g.size().y(); y++) {
				BlockSpec b = g.get(x, y, 11);
				if (b != null && (b.equals(S.palette().block(Role.NEON_A)) || b.equals(S.palette().block(Role.NEON_B)))) {
					lit++;
				}
			}
		}
		assertEquals(dev.nezo.burmaldaholic.worldgen.logic.Layouts.signPixels("CASINO").pixels().size(), lit);
	}

	@Test
	void exportIsDeterministic() {
		for (Piece p : OverworldCasino.pieces(S)) {
			byte[] a = Nbt.writeGzip(TemplateExporter.template(p.layout()));
			byte[] b = Nbt.writeGzip(TemplateExporter.template(OverworldCasino.piece(S, OverworldCasino.Part.valueOf(
				p.id().substring(p.id().lastIndexOf('/') + 1).toUpperCase(java.util.Locale.ROOT))).layout()));
			assertArrayEquals(a, b, p.id());
		}
	}
}

package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.Assembly;
import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoPack;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Markers;
import dev.nezo.burmaldaholic.worldgen.logic.Nbt;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.PackFiles;
import dev.nezo.burmaldaholic.worldgen.logic.TemplateExporter;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Checks;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** The flagship Grand Casino (CASINO-REDESIGN §3, CR-1): content, rules, symmetry, slicing, every biome variant. */
class GrandCasinoTest {
	private static Piece whole(GrandCasino.Variant v) {
		return GrandCasino.whole(v).build();
	}

	@TestFactory
	List<DynamicTest> theWholeBuildingPassesEveryCheckInEveryVariant() {
		List<DynamicTest> out = new ArrayList<>();
		for (GrandCasino.Variant v : GrandCasino.Variant.values()) {
			out.add(DynamicTest.dynamicTest(v.name(), () -> assertEquals(List.of(), Checks.run(whole(v)), v.name())));
			for (Piece p : GrandCasino.pieces(v)) {
				out.add(DynamicTest.dynamicTest(p.id(), () -> assertEquals(List.of(), Checks.run(p), p.id())));
			}
		}
		return out;
	}

	@Test
	void twentySixGamesOfEveryFamilyAsFullTables() {
		Piece p = whole(GrandCasino.Variant.RIVIERA);
		Map<Game, Integer> n = new EnumMap<>(Game.class);
		p.games().forEach(s -> n.merge(s.game(), 1, Integer::sum));
		assertEquals(26, p.games().size());
		assertEquals(Map.ofEntries(Map.entry(Game.ROULETTE, 2), Map.entry(Game.CRAPS, 1), Map.entry(Game.BACCARAT, 1),
			Map.entry(Game.BLACKJACK, 2), Map.entry(Game.UTH, 1), Map.entry(Game.POKER, 2), Map.entry(Game.UTH_PLAYER_BANKED, 1),
			Map.entry(Game.BACCARAT_PLAYER_BANKED, 1), Map.entry(Game.SLOTS_COPPER, 6), Map.entry(Game.SLOTS_GOLD, 4),
			Map.entry(Game.SLOTS_NETHERITE, 2), Map.entry(Game.CASHIER, 1), Map.entry(Game.WHEEL, 1), Map.entry(Game.PLINKO, 1)), n);
		assertEquals(Game.Family.REQUIRED, Checks.families(List.of(p)));
		Grid g = p.layout().grid();
		for (Piece.GameSpot s : p.games()) {
			BlockSpec b = g.get(s.pos().x(), s.pos().y(), s.pos().z());
			if (s.game().tableGame() != null) {
				assertEquals("main", b.properties().get("part"), s + " is a full multi-block table");
			} else {
				assertEquals("main", b.properties().get("part"), s + " is a full upright machine (MT-3)");
			}
		}
	}

	@Test
	void npcsChestAndAnchorWithinTheBudget() {
		Piece p = whole(GrandCasino.Variant.RIVIERA);
		List<NpcRole> npcs = new ArrayList<>();
		int chests = 0;
		int anchors = 0;
		for (Layout.PlacedMarker m : p.layout().markers()) {
			switch (m.marker()) {
				case Markers.Npc n -> npcs.add(n.role());
				case Markers.Chest c -> chests++;
				case Markers.Anchor a -> anchors++;
			}
		}
		assertTrue(npcs.containsAll(List.of(NpcRole.CROUPIER, NpcRole.LOAN_SHARK, NpcRole.BACCARAT_DEALER)), npcs.toString());
		assertTrue(npcs.size() <= Assembly.MAX_NPCS);
		assertEquals(1, chests);
		assertEquals(1, anchors);
	}

	@Test
	void slicesAreTheThreeRequiredPoolsAndTheirConnectorsMeet() {
		List<Piece> ps = GrandCasino.pieces(GrandCasino.Variant.RIVIERA);
		assertEquals(List.of("casino/tudor/entrance", "casino/tudor/floor", "casino/tudor/bar"), ps.stream().map(Piece::id).toList());
		for (Piece p : ps) {
			assertEquals(PackFiles.REQUIRED_POOLS.get(ps.indexOf(p)), PackFiles.poolName(PackFiles.pieceName(p.id())));
			assertEquals(GrandCasino.FLOOR_Y, p.layout().jigsaws().getFirst().pos().y(), "connectors in the floor row");
			assertFalse(Checks.PENDING_CLEARANCE.contains(p.id()), p.id() + " must pass R1-R6");
			assertFalse(Checks.PENDING_VALUABLES.contains(p.id()));
		}
		int total = ps.stream().mapToInt(p -> p.layout().size().z()).sum();
		assertEquals(GrandCasino.SZ, total, "the slices tile the building");
		int games = ps.stream().mapToInt(p -> p.games().size()).sum();
		assertEquals(26, games);
		Assembly.Result r = Assembly.all(ps.stream().map(Piece::layout).toList()).getFirst();
		assertEquals(List.of(), r.problems());
		assertEquals(GrandCasino.SX, r.footprint().xSpan());
		assertEquals(GrandCasino.SZ, r.footprint().zSpan());
	}

	@Test
	void theActivePackIsTheGrandCasinoInFiveSlots() {
		List<String> styles = PackFiles.completeStyles(CasinoPack.layouts());
		assertEquals(List.of("tudor", "oasis", "safari", "alpine", "ice_palace"), styles);
		for (Piece p : OverworldPieces.all()) {
			assertTrue(p.layout().size().x() == GrandCasino.SX, p.id());
		}
		assertEquals(15, OverworldPieces.all().size());
	}

	@Test
	void theSkylineIsMirrorSymmetric() {
		Grid g = whole(GrandCasino.Variant.RIVIERA).layout().grid();
		Vec s = g.size();
		for (int x = 0; x < s.x(); x++) {
			for (int z = 0; z < s.z(); z++) {
				assertEquals(top(g, x, z), top(g, GrandCasino.AX2 - x, z), "roofline at " + x + "," + z);
			}
		}
	}

	private static int top(Grid g, int x, int z) {
		for (int y = g.size().y() - 1; y >= 0; y--) {
			BlockSpec b = g.get(x, y, z);
			if (b != null && !b.is("minecraft:air")) {
				return y;
			}
		}
		return -1;
	}

	@Test
	void theDomeAndTowersCrownTheSkyline() {
		Grid g = whole(GrandCasino.Variant.RIVIERA).layout().grid();
		int dome = top(g, GrandCasino.CX, GrandCasino.DOME_Z);
		int tower = top(g, 4, 31);
		int crown = top(g, GrandCasino.CX, GrandCasino.Z_LOGGIA);
		int cornice = GrandCasino.CEIL_Y + 2;
		assertTrue(dome >= cornice + 12, "dome lantern " + dome);
		assertTrue(tower >= cornice + 10, "tower finial " + tower);
		assertTrue(crown > cornice + 6 && crown < dome, "crown " + crown);
	}

	@Test
	void everyVariantKeepsThePlanAndSwapsThePalette() {
		Grid r = whole(GrandCasino.Variant.RIVIERA).layout().grid();
		for (GrandCasino.Variant v : GrandCasino.Variant.values()) {
			Piece p = whole(v);
			Grid g = p.layout().grid();
			int differ = 0;
			for (int x = 0; x < GrandCasino.SX; x++) {
				for (int y = 0; y < GrandCasino.SY; y++) {
					for (int z = 0; z < GrandCasino.SZ; z++) {
						assertEquals(r.get(x, y, z) == null, g.get(x, y, z) == null, v + " shape at " + x + "," + y + "," + z);
						differ += java.util.Objects.equals(r.get(x, y, z), g.get(x, y, z)) ? 0 : 1;
					}
				}
			}
			assertTrue(v == GrandCasino.Variant.RIVIERA || differ > 3000, v + " looks different (" + differ + " cells)");
			assertEquals(26, p.games().size());
		}
	}

	@Test
	void exportIsDeterministic() {
		for (Piece p : GrandCasino.pieces(GrandCasino.Variant.OASIS)) {
			Piece again = GrandCasino.pieces(GrandCasino.Variant.OASIS).stream().filter(q -> q.id().equals(p.id())).findFirst().orElseThrow();
			assertArrayEquals(Nbt.writeGzip(TemplateExporter.template(p.layout())), Nbt.writeGzip(TemplateExporter.template(again.layout())));
		}
	}
}

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
	void thirtyTwoGamesOfEveryFamilyAsFullTables() {
		Piece p = whole(GrandCasino.Variant.RIVIERA);
		Map<Game, Integer> n = new EnumMap<>(Game.class);
		p.games().forEach(s -> n.merge(s.game(), 1, Integer::sum));
		assertEquals(32, p.games().size());
		assertEquals(Map.ofEntries(Map.entry(Game.ROULETTE, 2), Map.entry(Game.CRAPS, 1), Map.entry(Game.BACCARAT, 1),
			Map.entry(Game.BLACKJACK, 2), Map.entry(Game.UTH, 1), Map.entry(Game.POKER, 2), Map.entry(Game.UTH_PLAYER_BANKED, 1),
			Map.entry(Game.BACCARAT_PLAYER_BANKED, 1), Map.entry(Game.SLOTS_COPPER, 6), Map.entry(Game.SLOTS_GOLD, 10),
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

	/** Felt never muddy: every table lit >= 12 over its main block (uplights + a pendant), every machine >= 10. */
	@Test
	void everyFeltIsBrightlyLit() {
		for (GrandCasino.Variant v : GrandCasino.Variant.values()) {
			Piece p = whole(v);
			int[][][] light = Checks.light(p.layout().grid());
			for (Piece.GameSpot s : p.games()) {
				Vec q = Checks.lightProbe(p.layout().grid(), s);
				int need = s.game().tableGame() != null ? 12 : 10;
				assertTrue(light[q.x()][q.y()][q.z()] >= need, v + " " + s + " lit " + light[q.x()][q.y()][q.z()]);
			}
		}
	}

	/**
	 * The CASINO board as built in every variant: each cell of the 7 text rows is the light-emitting NEON_A block
	 * exactly where the expected letter mask has a stroke, and the solid SIGN_BACK board everywhere else (no bulbs
	 * inside strokes), so it cannot read as CASIRO / CASIND.
	 */
	@Test
	void theMarqueeSpellsCasinoInSolidStrokes() {
		StringBuilder[] want = new StringBuilder[7];
		for (int r = 0; r < 7; r++) {
			want[r] = new StringBuilder();
			for (char c : "CASINO".toCharArray()) {
				if (want[r].length() > 0) {
					want[r].append('.');
				}
				want[r].append(dev.nezo.burmaldaholic.worldgen.logic.blueprint.SignFontTest.CASINO_LARGE.get(c)[r]);
			}
		}
		int width = want[0].length();
		assertEquals(25, width, "fits the 25 visible columns between the towers (x8..32)");
		int x0 = GrandCasino.CX - width / 2;
		int top = GrandCasino.ROW2 + 1 + 7; // the text row under the top bulb row
		for (GrandCasino.Variant v : GrandCasino.Variant.values()) {
			Grid g = whole(v).layout().grid();
			BlockSpec neon = v.palette().block(dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role.NEON_A);
			BlockSpec back = v.palette().block(dev.nezo.burmaldaholic.worldgen.logic.blueprint.Role.SIGN_BACK);
			assertFalse(neon.equals(back), v + ": letters contrast with the board");
			StringBuilder got = new StringBuilder();
			for (int r = 0; r < 7; r++) {
				for (int c = 0; c < width; c++) {
					BlockSpec b = g.get(x0 + c, top - r, GrandCasino.Z_SIGN);
					got.append(neon.equals(b) ? '#' : back.equals(b) ? '.' : '?');
				}
				got.append('\n');
			}
			assertEquals(String.join("\n", (CharSequence[]) want) + "\n", got.toString(), v + " marquee");
		}
	}

	/**
	 * Package DL: every table's dealer cell ({@code TableFootprint.dealerCell}) is two blocks of air (no lectern, carpet
	 * or decor), so the house dealer NPC can stand there; in the whole building and in each jigsaw slice.
	 */
	@Test
	void everyTableDealerCellIsTwoBlocksOfAir() {
		for (GrandCasino.Variant v : GrandCasino.Variant.values()) {
			List<Piece> all = new ArrayList<>(GrandCasino.pieces(v));
			all.add(whole(v));
			int tables = 0;
			for (Piece p : all) {
				Grid g = p.layout().grid();
				for (Piece.GameSpot s : p.games()) {
					if (s.game().tableGame() == null) {
						continue;
					}
					int[] d = dev.nezo.burmaldaholic.worldgen.logic.blueprint.TableClearance.dealer(s);
					int y = s.pos().y();
					for (int dy = 0; dy <= 1; dy++) {
						BlockSpec b = g.get(d[0], y + dy, d[1]);
						assertTrue(b != null && b.is("minecraft:air"), v + " " + p.id() + " " + s + ": dealer cell +" + dy + " holds " + b);
					}
					tables++;
				}
			}
			assertEquals(22, tables, v + ": 11 tables in the whole building + 11 across the slices");
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
		assertTrue(npcs.containsAll(List.of(NpcRole.CROUPIER, NpcRole.LOAN_SHARK, NpcRole.HOUSE_DEALER, NpcRole.MERCHANT)), npcs.toString());
		assertTrue(npcs.stream().filter(r -> r != NpcRole.HOUSE_DEALER).count() <= Assembly.MAX_NPCS);
		assertTrue(npcs.stream().filter(r -> r == NpcRole.HOUSE_DEALER).count() <= Assembly.MAX_HOUSE_DEALERS);
		assertEquals(1, chests);
		assertEquals(1, anchors);
	}

	@Test
	void slicesAreTheThreeRequiredPoolsAndTheirConnectorsMeet() {
		List<Piece> ps = GrandCasino.pieces(GrandCasino.Variant.RIVIERA);
		assertEquals(List.of("casino/riviera/entrance", "casino/riviera/floor", "casino/riviera/bar"), ps.stream().map(Piece::id).toList());
		for (Piece p : ps) {
			assertEquals(PackFiles.REQUIRED_POOLS.get(ps.indexOf(p)), PackFiles.poolName(PackFiles.pieceName(p.id())));
			assertEquals(GrandCasino.FLOOR_Y, p.layout().jigsaws().getFirst().pos().y(), "connectors in the floor row");
			assertFalse(Checks.PENDING_CLEARANCE.contains(p.id()), p.id() + " must pass R1-R6");
			assertFalse(Checks.PENDING_VALUABLES.contains(p.id()));
		}
		int total = ps.stream().mapToInt(p -> p.layout().size().z()).sum();
		assertEquals(GrandCasino.SZ, total, "the slices tile the building");
		int games = ps.stream().mapToInt(p -> p.games().size()).sum();
		assertEquals(32, games);
		Assembly.Result r = Assembly.all(ps.stream().map(Piece::layout).toList()).getFirst();
		assertEquals(List.of(), r.problems());
		assertEquals(GrandCasino.SX, r.footprint().xSpan());
		assertEquals(GrandCasino.SZ, r.footprint().zSpan());
	}

	@Test
	void theActivePackIsTheGrandCasinoInFiveSlots() {
		List<String> styles = PackFiles.completeStyles(CasinoPack.layouts());
		assertEquals(List.of("riviera", "oasis", "safari", "alpine", "ice_palace", "cherry"), styles);
		for (Piece p : OverworldPieces.all()) {
			assertTrue(p.layout().size().x() == GrandCasino.SX, p.id());
		}
		assertEquals(18, OverworldPieces.all().size());
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
		int tower = top(g, 4, GrandCasino.Z_TOWER + 3);
		int crown = top(g, GrandCasino.CX, GrandCasino.Z_SIGN);
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
			assertEquals(32, p.games().size());
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

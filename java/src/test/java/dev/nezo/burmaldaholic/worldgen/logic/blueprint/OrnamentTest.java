package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.Markers;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.logic.pieces.GrandPalettes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** CR-1 DSL verbs ({@link Ornament}, {@link Blueprint#mirrorX}, {@link Blueprint#slice}, uplights, palette roles). */
class OrnamentTest {
	static final Palette P = GrandPalettes.RIVIERA;

	static Blueprint bp(int sx, int sy, int sz) {
		return Blueprint.of("casino/test/ornament", CasinoKind.VILLAGE_CASINO, sx, sy, sz, P).compactTables(false);
	}

	static String name(Blueprint b, int x, int y, int z) {
		BlockSpec s = b.at(x, y, z);
		return s == null ? null : s.name();
	}

	// ---- palette roles -------------------------------------------------------------------------------------------

	@Test
	void architecturalRolesFallBackAndDeriveOverrides() {
		Palette tudor = Palettes.TUDOR;
		assertFalse(tudor.maps(Role.COLUMN));
		assertEquals(tudor.get(Role.FRAME), tudor.get(Role.COLUMN), "COLUMN falls back to FRAME");
		assertEquals(tudor.get(Role.ACCENT), tudor.get(Role.CAPITAL), "CAPITAL falls back to ACCENT");
		Palette d = P.derive("x").put(Role.WALL, Material.of("minecraft:mud_bricks")).build();
		assertEquals("minecraft:mud_bricks", d.block(Role.WALL).name());
		assertEquals(P.block(Role.DOME), d.block(Role.DOME), "derive keeps every other mapping");
		for (Palette g : GrandPalettes.all()) {
			g.check();
		}
	}

	@Test
	void materialCarriesFixedStateProperties() {
		BlockSpec bulb = P.block(Role.BULB);
		assertEquals("minecraft:waxed_copper_bulb", bulb.name());
		assertEquals("true", bulb.properties().get("lit"));
		assertEquals("false", bulb.properties().get("powered"));
		assertEquals(15, Checks.emission(bulb), "a lit bulb is a light");
	}

	@Test
	void neonAndLightRolesOfEveryGrandPaletteEmitLight() {
		for (Palette g : GrandPalettes.all()) {
			for (Role r : List.of(Role.NEON_A, Role.NEON_B, Role.BULB, Role.LIGHT, Role.UPLIGHT)) {
				assertTrue(Checks.emission(g.block(r)) >= 14, g.id() + " " + r + " = " + g.block(r));
			}
			for (Role r : Role.values()) {
				assertFalse(Checks.forbidden(g.block(r)), g.id() + " " + r + " is a valuable block");
			}
		}
	}

	// ---- columns -------------------------------------------------------------------------------------------------

	@Test
	void columnHasBaseShaftCapitalAndFlaresOnlyIntoFreeCells() {
		Blueprint b = bp(5, 8, 5);
		b.fill(0, 0, 0, 4, 0, 4, Role.FLOOR);
		b.set(1, 1, 2, Role.WALL); // a wall on the west side of the base
		Ornament.column(b, 2, 2, 1, 6, Ornament.Flare.BOTH);
		assertEquals(P.block(Role.CAPITAL), b.at(2, 1, 2));
		assertEquals(P.block(Role.CAPITAL), b.at(2, 6, 2));
		for (int y = 2; y <= 5; y++) {
			assertEquals("y", b.at(2, y, 2).properties().get("axis"), "shaft upright");
		}
		assertEquals(P.block(Role.WALL), b.at(1, 1, 2), "no flare into the wall");
		BlockSpec flare = b.at(3, 1, 2);
		assertEquals(P.get(Role.PLINTH).stairs(), flare.name());
		assertEquals("west", flare.properties().get("facing"), "base flare climbs toward the shaft");
		assertEquals("top", b.at(2, 6, 3).properties().get("half"), "capital flare is upside-down");
		assertEquals("north", b.at(2, 6, 3).properties().get("facing"));
	}

	// ---- arches --------------------------------------------------------------------------------------------------

	@Test
	void archProfilesAreSymmetricAndRise() {
		assertArrayEquals(new int[] {2, 3, 2}, Ornament.archProfile(3, 1.5));
		assertArrayEquals(new int[] {2, 4, 4, 4, 2}, Ornament.archProfile(5, 2));
		for (int w = 1; w <= 9; w++) {
			int[] k = Ornament.archProfile(w, 0);
			for (int i = 0; i < w; i++) {
				assertEquals(k[i], k[w - 1 - i], "w " + w);
				assertTrue(i == 0 || i > w / 2 || k[i] >= k[i - 1], "rises to the crown");
			}
		}
	}

	@Test
	void archClearsTheOpeningAndLinesItWithVoussoirs() {
		Blueprint b = bp(7, 8, 3);
		b.fill(0, 0, 0, 6, 0, 2, Role.FLOOR);
		b.fill(0, 1, 1, 6, 6, 1, Role.WALL);
		Ornament.arch(b, true, 2, 4, 1, 1, 1, 3, 1.5, Role.WALL);
		for (int x = 2; x <= 4; x++) {
			for (int y = 1; y <= 3; y++) {
				assertEquals("minecraft:air", name(b, x, y, 1), "opening " + x + "," + y);
			}
		}
		// k = {2, 3, 2}: the side cells are solid at the spring + 1 (full voussoirs), the crown is half open (a top slab)
		assertEquals(P.get(Role.ARCH).full(), name(b, 2, 4, 1));
		assertEquals("top", b.at(3, 4, 1).properties().get("type"), "crown voussoir is a top slab");
		assertEquals(P.block(Role.CAPITAL), b.at(3, 5, 1), "keystone");
		assertEquals(P.get(Role.ARCH).full(), name(b, 1, 3, 1), "archivolt on the jamb");
		// a 5-wide arch: stairs step toward the jambs where the curve steps
		Blueprint c = bp(9, 8, 3);
		c.fill(0, 1, 1, 8, 6, 1, Role.WALL);
		Ornament.arch(c, true, 2, 6, 1, 1, 1, 2, 0, Role.WALL);
		int[] k = Ornament.archProfile(5, 0);
		assertEquals(k[0] % 2 == 1, c.at(2, 2 + k[0] / 2, 1).name().endsWith("_stairs"));
	}

	// ---- cornices, entablatures, balustrades -----------------------------------------------------------------------

	@Test
	void corniceRingOverhangsEverySideWithOuterCorners() {
		Blueprint b = bp(9, 5, 9);
		b.fill(2, 0, 2, 6, 3, 6, Role.WALL);
		Ornament.cornice(b, 2, 2, 6, 6, 3);
		BlockSpec north = b.at(4, 3, 1), west = b.at(1, 3, 4);
		assertEquals("south", north.properties().get("facing"), "high side against the wall");
		assertEquals("top", north.properties().get("half"));
		assertEquals("east", west.properties().get("facing"));
		b.finish();
		assertTrue(b.at(1, 3, 1).properties().get("shape").startsWith("outer"), "corner " + b.at(1, 3, 1));
	}

	@Test
	void entablatureStacksArchitraveFriezeCornice() {
		Blueprint b = bp(5, 6, 3);
		Ornament.entablature(b, 0, 1, 4, 1, 1, Facing.SOUTH);
		assertEquals(P.block(Role.BAND), b.at(2, 1, 1));
		assertEquals(P.block(Role.GILT), b.at(0, 2, 1));
		assertEquals(P.block(Role.WALL), b.at(1, 2, 1));
		assertEquals("north", b.at(2, 3, 2).properties().get("facing"));
	}

	@Test
	void balustradeIsWallsUnderARailAndConnects() {
		Blueprint b = bp(7, 4, 3);
		Ornament.balustrade(b, 0, 1, 6, 1, 1, 3);
		b.finish();
		assertEquals(P.block(Role.CAPITAL), b.at(0, 1, 1), "end pier");
		assertEquals(P.block(Role.CAPITAL), b.at(3, 1, 1), "pier every 3");
		BlockSpec post = b.at(1, 1, 1);
		assertEquals(P.get(Role.BALUSTER).full(), post.name());
		assertEquals("tall", post.properties().get("east"), "covered by the rail");
		assertEquals("none", post.properties().get("north"));
		assertEquals("false", post.properties().get("up"), "straight run: no post");
		assertEquals("bottom", b.at(1, 2, 1).properties().get("type"));
	}

	// ---- staircases ----------------------------------------------------------------------------------------------

	@Test
	void flightClimbsOnePerCellWithHeadRoomAndIsWalkable() {
		Blueprint b = bp(4, 12, 10);
		b.fill(0, 0, 0, 3, 0, 9, Role.FLOOR);
		b.fill(0, 1, 0, 3, 10, 9, Role.WALL); // solid: the flight must carve its own head room
		b.clear(0, 1, 9, 3, 3, 9); // the start landing
		int[] top = Ornament.flight(b, 1, 1, 8, Facing.NORTH, Facing.EAST, 2, 6, Role.FLOOR, 1);
		assertArrayEquals(new int[] {1, 7, 2}, top);
		for (int s = 0; s < 6; s++) {
			BlockSpec st = b.at(1, 1 + s, 8 - s);
			assertEquals("north", st.properties().get("facing"));
			assertEquals("bottom", st.properties().get("half"));
			assertEquals("minecraft:air", name(b, 2, 2 + s, 8 - s), "head room");
			if (s > 0) {
				assertEquals(P.get(Role.FLOOR).full(), name(b, 1, s, 8 - s), "stringer");
			}
		}
		b.clear(0, 7, 0, 3, 8, 2); // top landing
		boolean[][][] reach = Checks.reachable(b.build().layout().grid(), List.of(new Vec(1, 1, 9)));
		assertTrue(reach[1][7][2], "top landing reachable on foot");
	}

	// ---- circles and domes ---------------------------------------------------------------------------------------

	@Test
	void discsAndRingsAreRoundSymmetricAndClosed() {
		for (double r : new double[] {1, 2, 3.5, 5, 6}) {
			List<int[]> disc = Ornament.discCells(10, 10, r);
			assertEquals(Math.PI * (r + 0.5) * (r + 0.5), disc.size(), 0.3 * Math.PI * (r + 0.5) * (r + 0.5), "area r=" + r);
			Set<Long> in = new HashSet<>();
			disc.forEach(c -> in.add(c[0] * 1000L + c[1]));
			for (int[] c : disc) {
				assertTrue(in.contains((20 - c[0]) * 1000L + c[1]) && in.contains(c[0] * 1000L + (20 - c[1])), "4-fold symmetric");
			}
			List<int[]> ring = Ornament.ringCells(10, 10, r);
			Set<Long> rs = new HashSet<>();
			ring.forEach(c -> rs.add(c[0] * 1000L + c[1]));
			for (int[] c : ring) {
				int n = 0;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						n += (dx != 0 || dz != 0) && rs.contains((c[0] + dx) * 1000L + c[1] + dz) ? 1 : 0;
					}
				}
				assertTrue(n >= 2, "ring is closed at " + c[0] + "," + c[1]);
			}
		}
	}

	@Test
	void domeIsAHollowRibbedShellWithALantern() {
		Blueprint b = bp(15, 14, 15);
		int crown = Ornament.dome(b, 7, 7, 5, 2, 5);
		assertTrue(crown >= 6 && crown <= 7, "crown " + crown);
		assertEquals("minecraft:air", name(b, 7, 3, 7), "hollow");
		assertEquals("minecraft:air", name(b, 5, 2, 7), "hollow near the base");
		assertTrue(b.at(7, crown, 7) != null && !name(b, 7, crown, 7).equals("minecraft:air"), "closed at the crown");
		assertTrue(name(b, 2, 2, 7).startsWith(P.get(Role.RIB).full()), "rib on the axis");
		assertTrue(name(b, 2, 2, 8).startsWith(P.get(Role.DOME).full()), "copper between the ribs");
		// symmetric
		for (int x = 0; x < 15; x++) {
			for (int y = 0; y < 14; y++) {
				for (int z = 0; z < 15; z++) {
					assertEquals(name(b, x, y, z) == null, name(b, 14 - x, y, z) == null, x + "," + y + "," + z);
				}
			}
		}
		int top = Ornament.lantern(b, 7, crown + 1, 7);
		assertEquals("minecraft:lightning_rod", name(b, 7, top, 7));
		assertTrue(Checks.emission(b.at(7, crown + 2, 7)) > 0, "the lantern glows");
	}

	@Test
	void drumIsGlassBetweenCompassPiers() {
		Blueprint b = bp(15, 6, 15);
		Ornament.drum(b, 7, 7, 5, 2, 3);
		assertEquals("y", b.at(2, 2, 7).properties().get("axis"), "pier on the west axis");
		assertEquals(P.block(Role.GLASS), b.at(3, 2, 4), "glass between");
		assertEquals(P.block(Role.BAND), b.at(2, 1, 7), "band course under the drum");
	}

	// ---- gradients, patterns -------------------------------------------------------------------------------------

	@Test
	void hashIsDeterministicAndUniform() {
		assertEquals(Ornament.hash(1, 2, 3, 9), Ornament.hash(1, 2, 3, 9));
		assertNotEquals(Ornament.hash(1, 2, 3, 9), Ornament.hash(1, 2, 3, 10));
		double sum = 0;
		for (int i = 0; i < 4000; i++) {
			double h = Ornament.hash(i, i * 7, -i, 42);
			assertTrue(h >= 0 && h < 1);
			sum += h;
		}
		assertEquals(0.5, sum / 4000, 0.03);
	}

	@Test
	void patternsAreMirrorSymmetric() {
		Blueprint b = bp(21, 1, 21);
		Ornament.diamond(b, 10, 10, 4, 0, P.block(Role.GILT), true);
		Ornament.sunburst(b, 10, 0, 0, 5, 2, 9, -60, 60, P.block(Role.FLOOR_ACCENT));
		for (int x = 0; x < 21; x++) {
			for (int z = 0; z < 21; z++) {
				assertEquals(b.at(x, 0, z), b.at(20 - x, 0, z), "mirror " + x + "," + z);
			}
		}
		assertEquals(P.block(Role.GILT), b.at(6, 0, 10));
		assertNull(b.at(10, 0, 10), "outline only");
		Blueprint c = bp(9, 1, 9);
		Ornament.checker(c, 0, 0, 8, 8, 0, P.block(Role.FLOOR), P.block(Role.FLOOR_ACCENT));
		assertEquals(P.block(Role.FLOOR), c.at(0, 0, 0));
		assertEquals(P.block(Role.FLOOR_ACCENT), c.at(1, 0, 0));
	}

	@Test
	void coffersPutBeamsOnTheGridAndLightsInAlternateCoffers() {
		Blueprint b = bp(9, 1, 9);
		BlockSpec light = P.block(Role.LIGHT);
		Ornament.coffers(b, 0, 0, 8, 8, 0, 4, light);
		assertEquals(P.block(Role.CEILING), b.at(0, 0, 3));
		assertEquals(P.block(Role.CEILING), b.at(4, 0, 4));
		assertEquals(light, b.at(2, 0, 2));
		assertEquals(P.block(Role.COFFER), b.at(6, 0, 2), "the neighbour coffer is plain");
		assertEquals(light, b.at(6, 0, 6));
	}

	// ---- neon signage --------------------------------------------------------------------------------------------

	@Test
	void neonLettersAreLightBlocksOnTheBoardWithABulbChaser() {
		SignFont.Plan plan = SignFont.SMALL.line("CASINO");
		Blueprint b = bp(27, 9, 4);
		Facades.SignBox box = Ornament.neon(b, plan, Facing.SOUTH, 13, 1, 1, 1, true);
		assertEquals(25, box.width());
		assertEquals(1, box.alongFrom());
		for (dev.nezo.burmaldaholic.worldgen.logic.Layouts.SignPixel px : plan.pixels()) {
			BlockSpec s = b.at(2 + px.col(), 6 - px.row(), 2);
			assertTrue(Checks.emission(s) > 0, "letter pixel " + px + " is a light: " + s);
		}
		assertEquals(P.block(Role.SIGN_BACK), b.at(5, 3, 1), "board behind");
		assertEquals(P.block(Role.BULB), b.at(1, 1, 2), "bulb on the raised border");
		assertEquals(P.block(Role.SIGN_BACK), b.at(2, 1, 2), "dark socket between bulbs");
	}

	// ---- mirror, slice, uplights ---------------------------------------------------------------------------------

	@Test
	void mirrorStateFlipsHandedState() {
		BlockSpec st = BlockSpec.mc("oak_stairs", "facing", "east", "half", "top", "shape", "inner_left");
		BlockSpec m = Mirror.x(st);
		assertEquals("west", m.properties().get("facing"));
		assertEquals("inner_right", m.properties().get("shape"));
		BlockSpec fence = BlockSpec.mc("oak_fence", "east", "true", "west", "false", "north", "true", "south", "false");
		assertEquals("false", Mirror.x(fence).properties().get("east"));
		assertEquals("true", Mirror.x(fence).properties().get("west"));
		assertEquals("12", Mirror.x(BlockSpec.mc("white_banner", "rotation", "4")).properties().get("rotation"));
		assertEquals("south", Mirror.x(BlockSpec.mc("chest", "facing", "south", "type", "left")).properties().get("facing"));
		assertEquals("right", Mirror.x(BlockSpec.mc("chest", "facing", "south", "type", "left")).properties().get("type"));
		assertEquals(st, Mirror.x(m));
	}

	@Test
	void mirrorXDrawsBothHalvesWithGamesAndMarkers() {
		Blueprint b = bp(11, 5, 7);
		b.fill(0, 0, 0, 10, 0, 6, Role.FLOOR);
		b.mirrorX(10, m -> {
			m.stairs(1, 1, 1, Role.WALL, Facing.EAST, false);
			m.game(Game.SLOTS_COPPER, 2, 1, 3, Facing.EAST);
			m.npc(NpcRole.CROUPIER, 1, 1, 5, Facing.WEST);
			m.keepClear(1, 1, 1, 2, 2, 2);
		});
		assertEquals("east", b.at(1, 1, 1).properties().get("facing"));
		assertEquals("west", b.at(9, 1, 1).properties().get("facing"));
		Piece p = b.build();
		assertEquals(2, p.games().size());
		assertEquals(new Vec(8, 1, 3), p.games().get(1).pos());
		assertEquals(Facing.WEST, p.games().get(1).playerSide());
		assertEquals("west", p.layout().grid().get(8, 1, 3).properties().get("facing"));
		Markers.Npc npc = (Markers.Npc) p.layout().markers().get(1).marker();
		assertEquals(Facing.EAST, npc.facing());
		assertEquals(new Vec(9, 2, 5), p.layout().markers().get(1).pos());
		assertEquals(new Geometry.Box(8, 1, 1, 9, 2, 2), p.keepClear().get(1));
		assertThrows(IllegalStateException.class, () -> b.mirrorX(10, m -> m.mirrorX(10, n -> {})));
	}

	@Test
	void mirroredTablesAreStampedAsRealTables() {
		Blueprint b = bp(15, 5, 7);
		b.fill(0, 0, 0, 14, 0, 6, Role.FLOOR);
		b.mirrorX(14, m -> m.game(Game.ROULETTE, 3, 1, 3, Facing.EAST));
		List<String> problems = new java.util.ArrayList<>();
		TableClearance.check(b.build(), problems);
		assertTrue(problems.stream().noneMatch(s -> s.contains("R1")), problems.toString());
	}

	@Test
	void sliceCutsAPieceAndKeepsItsMetadata() {
		Blueprint b = bp(9, 6, 12);
		b.fill(0, 0, 0, 8, 0, 11, Role.FLOOR);
		b.game(Game.SLOTS_GOLD, 4, 1, 9, Facing.NORTH);
		b.game(Game.SLOTS_GOLD, 4, 1, 2, Facing.SOUTH);
		b.npc(NpcRole.LOAN_SHARK, 2, 1, 8, Facing.NORTH);
		b.interior(1, 1, 1, 7, 4, 10);
		b.entry(4, 1, 11);
		Blueprint front = b.slice("casino/test/front", 6, 11);
		Piece p = front.build();
		assertEquals(new Vec(9, 6, 6), p.layout().size());
		assertEquals(1, p.games().size());
		assertEquals(new Vec(4, 1, 3), p.games().getFirst().pos());
		assertEquals(new Vec(2, 2, 2), p.layout().markers().getFirst().pos());
		assertEquals(new Geometry.Box(1, 1, 0, 7, 4, 4), p.interior().getFirst());
		assertEquals(List.of(new Vec(4, 1, 5)), p.entries());
		assertEquals(P.block(Role.FLOOR), p.layout().grid().get(0, 0, 0));
		Blueprint t = bp(9, 6, 12);
		t.fill(0, 0, 0, 8, 0, 11, Role.FLOOR);
		t.game(Game.BLACKJACK, 4, 1, 6, Facing.SOUTH);
		assertThrows(IllegalStateException.class, () -> t.slice("casino/test/cut", 6, 11), "the table's back row is at z5");
	}

	@Test
	void uplightTilesLightEveryGameWithoutEnteringItsHeadroom() {
		Blueprint b = bp(13, 8, 9);
		b.fill(0, 0, 0, 12, 0, 8, Role.FLOOR);
		b.fill(0, 1, 0, 12, 6, 8, Blueprint.AIR);
		b.fill(0, 7, 0, 12, 7, 8, Role.CEILING); // dark ceiling: no other light
		Kits.tablePod(Game.BLACKJACK, null).apply(b, 3, 1, 3, Facing.SOUTH);
		Kits.slotBank(Game.SLOTS_COPPER, 2).apply(b, 9, 1, 3, Facing.SOUTH);
		b.set(10, 3, 3, Blueprint.AIR); // the bank's own neon strip
		b.set(10, 4, 3, Blueprint.AIR);
		b.set(9, 3, 3, Blueprint.AIR);
		b.set(9, 4, 3, Blueprint.AIR);
		b.uplightGames();
		assertEquals(P.block(Role.UPLIGHT), b.at(2, 0, 4), "tile under the walkway's left end");
		assertEquals(P.block(Role.UPLIGHT), b.at(4, 0, 4));
		assertEquals("minecraft:air", name(b, 2, 1, 4), "carpet taken off the tile");
		Piece p = b.build();
		int[][][] light = Checks.light(p.layout().grid());
		for (Piece.GameSpot s : p.games()) {
			Vec v = Checks.lightProbe(p.layout().grid(), s); // over a table; a full slot's player head cell
			assertTrue(light[v.x()][v.y()][v.z()] >= 10, s + " lit " + light[v.x()][v.y()][v.z()]);
		}
		List<String> out = new java.util.ArrayList<>();
		TableClearance.check(p, out);
		assertTrue(out.isEmpty(), out.toString());
	}

	@Test
	void wallsGetConnectionsInTheFinishingPass() {
		Blueprint b = bp(5, 3, 5);
		b.set(1, 1, 2, BlockSpec.mc("cobblestone_wall"));
		b.set(2, 1, 2, BlockSpec.mc("cobblestone_wall"));
		b.set(3, 1, 2, BlockSpec.mc("cobblestone_wall"));
		b.set(2, 1, 1, BlockSpec.mc("stone"));
		Grid g = b.build().layout().grid();
		BlockSpec mid = g.get(2, 1, 2);
		assertEquals("low", mid.properties().get("east"));
		assertEquals("low", mid.properties().get("north"), "connects to the full block");
		assertEquals("true", mid.properties().get("up"), "a T junction keeps its post");
		assertEquals("none", g.get(1, 1, 2).properties().get("west"));
		assertEquals("true", g.get(1, 1, 2).properties().get("up"), "an end keeps its post");
	}
}

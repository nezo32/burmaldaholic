package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import dev.nezo.burmaldaholic.worldgen.logic.Nbt;
import dev.nezo.burmaldaholic.worldgen.logic.TemplateExporter;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The DSL verbs of lane CB-2 (architecture doc §3.3) and the shape-finishing pass. */
class BlueprintDslTest {
	private static Blueprint floor(int sx, int sy, int sz) {
		Blueprint b = Blueprint.of("casino/test/dsl", CasinoKind.VILLAGE_CASINO, sx, sy, sz, Palettes.TUDOR);
		b.fill(0, 0, 0, sx - 1, 0, sz - 1, Role.FLOOR).clear(0, 1, 0, sx - 1, sy - 1, sz - 1);
		return b;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> blockAt(Map<String, Object> template, int x, int y, int z) {
		for (Object o : ((Nbt.TagList) template.get("blocks")).items()) {
			Map<String, Object> block = (Map<String, Object>) o;
			if (((Nbt.TagList) block.get("pos")).items().equals(List.of(x, y, z))) {
				return block;
			}
		}
		return null;
	}

	@Test
	@SuppressWarnings("unchecked")
	void bannerWritesPatternNbtThroughTheExporter() {
		Blueprint b = floor(3, 3, 3);
		b.set(1, 1, 0, Role.WALL);
		b.banner(1, 1, 1, Facing.SOUTH, "red", Blueprint.layer("rhombus", "yellow"), Blueprint.layer("border", "black"));
		Piece p = b.build();
		assertEquals(BlockSpec.mc("red_wall_banner", "facing", "south"), p.layout().grid().get(1, 1, 1));
		Map<String, Object> nbt = p.layout().blockNbt().get(new Vec(1, 1, 1));
		assertEquals("minecraft:banner", nbt.get("id"));
		List<Object> patterns = ((Nbt.TagList) nbt.get("patterns")).items();
		assertEquals(2, patterns.size());
		assertEquals("minecraft:rhombus", ((Map<String, Object>) patterns.get(0)).get("pattern"));
		assertEquals("yellow", ((Map<String, Object>) patterns.get(0)).get("color"));
		// exported into blocks[].nbt and survives an NBT round trip
		Map<String, Object> template = Nbt.readGzip(Nbt.writeGzip(TemplateExporter.template(p.layout())));
		Map<String, Object> block = blockAt(template, 1, 1, 1);
		assertEquals("minecraft:banner", ((Map<String, Object>) block.get("nbt")).get("id"));
		assertNull(blockAt(template, 1, 0, 1).get("nbt"));
		assertTrue(Checks.run(p).isEmpty(), Checks.run(p).toString());
	}

	@Test
	void bannerRejectsBadColoursAndNamespacedPatterns() {
		assertThrows(IllegalArgumentException.class, () -> Blueprint.layer("rhombus", "golden"));
		assertThrows(IllegalArgumentException.class, () -> Blueprint.layer("minecraft:rhombus", "red"));
		assertThrows(IllegalArgumentException.class, () -> floor(3, 3, 3).banner(1, 1, 1, Facing.SOUTH, "teal"));
	}

	@Test
	void overwritingACellDropsItsBlockNbt() {
		Blueprint b = floor(3, 3, 3);
		b.banner(1, 1, 1, Facing.SOUTH, "red");
		b.set(1, 1, 1, Blueprint.AIR);
		assertTrue(b.build().layout().blockNbt().isEmpty());
	}

	@Test
	void legacyLayoutsHaveNoBlockNbt() {
		for (Layout l : Layouts.all().values()) {
			assertTrue(l.blockNbt().isEmpty(), l.id());
		}
	}

	@Test
	void spacedMarqueeCentresOnAnOddFacade() {
		Blueprint b = floor(31, 12, 3);
		b.marquee("CASINO", 15, 8, 1, true, 1);
		Grid g = b.build().layout().grid();
		// "CASINO" = 16 columns + 5 gaps = 21, board 23 wide: x 4..26, symmetric around 15
		assertEquals(Palettes.TUDOR.block(Role.SIGN_BACK), g.get(4, 5, 1));
		assertEquals(Palettes.TUDOR.block(Role.SIGN_BACK), g.get(26, 5, 1));
		assertTrue(Checks.air(g.get(3, 5, 1)));
		assertTrue(Checks.air(g.get(27, 5, 1)));
		// raised letters stand at z + 1: the C's top-left pixel and the O's top-right pixel
		assertEquals(Palettes.TUDOR.block(Role.NEON_A), g.get(5, 8, 2));
		assertEquals(Palettes.TUDOR.block(Role.NEON_B), g.get(25, 8, 2));
		// the gap column after C stays empty in front of the board
		assertTrue(g.get(8, 8, 2) == null || Checks.air(g.get(8, 8, 2)));
		// bulb chaser at the corners
		assertEquals(Palettes.TUDOR.block(Role.BULB), g.get(4, 3, 2));
	}

	@Test
	void inlayReadsUprightFromTheFront() {
		Blueprint b = floor(9, 2, 9);
		b.inlay(Blueprint.Suit.SPADE, 1, 1, 1, Role.CARPET);
		Grid g = b.build().layout().grid();
		BlockSpec c = Palettes.TUDOR.block(Role.CARPET);
		assertEquals(c, g.get(4, 1, 1)); // the spade's tip is at the far (north) side
		assertTrue(Checks.air(g.get(1, 1, 1)));
		assertEquals(c, g.get(4, 1, 7)); // stem foot towards the viewer
		assertEquals(c, g.get(3, 1, 7));
	}

	@Test
	void beamsLieAlongTheirAxis() {
		Blueprint b = floor(5, 3, 5);
		b.beam(0, 1, 2, 4, 1, 2, Role.BEAM).beam(2, 2, 0, 2, 2, 4, Role.BEAM);
		Grid g = b.build().layout().grid();
		assertEquals("x", g.get(1, 1, 2).properties().get("axis"));
		assertEquals("z", g.get(2, 2, 1).properties().get("axis"));
	}

	@Test
	void hipRoofCanLeaveTheLastRingOpen() {
		Blueprint capped = floor(9, 6, 9);
		capped.hipRoof(0, 0, 8, 8, 2, 2, Role.ROOF, true);
		Blueprint open = floor(9, 6, 9);
		open.hipRoof(0, 0, 8, 8, 2, 2, Role.ROOF, false);
		assertEquals(Palettes.TUDOR.block(Role.ROOF), capped.build().layout().grid().get(4, 3, 4));
		assertTrue(Checks.air(open.build().layout().grid().get(4, 3, 4)));
	}

	@Test
	void gableRoofClimbsToASlabRidge() {
		Blueprint b = floor(5, 8, 7);
		int ridge = b.gableRoof(0, 0, 4, 6, 2, true, Role.ROOF, Role.WALL);
		Grid g = b.build().layout().grid();
		assertEquals(5, ridge);
		assertEquals("bottom", g.get(2, 5, 3).properties().get("type"));
		assertEquals("south", g.get(2, 2, 0).properties().get("facing"));
		assertEquals("north", g.get(2, 2, 6).properties().get("facing"));
		assertEquals(Palettes.TUDOR.block(Role.WALL), g.get(0, 3, 3)); // gable end filled
	}

	@Test
	void clerestoryOpensALitWell() {
		Blueprint b = floor(11, 12, 11);
		b.fill(0, 5, 0, 10, 5, 10, Role.CEILING);
		b.clerestory(2, 2, 8, 8, 6, 8);
		Piece p = b.build();
		Grid g = p.layout().grid();
		assertTrue(Checks.air(g.get(5, 5, 5)));
		assertEquals(Palettes.TUDOR.block(Role.GLASS), g.get(3, 7, 2));
		assertEquals(Palettes.TUDOR.block(Role.FRAME), g.get(2, 7, 2));
		assertTrue(p.interior().stream().anyMatch(box -> box.contains(5, 7, 5)));
	}

	@Test
	void finishingPassConnectsFencesAndPanes() {
		Blueprint b = floor(5, 3, 5);
		BlockSpec fence = BlockSpec.mc("dark_oak_fence");
		b.set(1, 1, 2, fence).set(2, 1, 2, fence).set(3, 1, 2, Role.WALL);
		b.set(2, 2, 0, BlockSpec.mc("glass_pane")).set(2, 2, 1, BlockSpec.mc("glass_pane"));
		Grid g = b.build().layout().grid();
		BlockSpec mid = g.get(2, 1, 2);
		assertEquals("true", mid.properties().get("west"));
		assertEquals("true", mid.properties().get("east")); // to the wall
		assertEquals("false", mid.properties().get("north"));
		assertEquals("false", g.get(1, 1, 2).properties().get("west"));
		assertEquals("true", g.get(2, 2, 0).properties().get("south"));
		assertEquals("false", g.get(2, 2, 0).properties().get("east"));
	}

	@Test
	void finishingPassShapesStairCorners() {
		Blueprint b = floor(9, 6, 9);
		b.hipRoof(0, 0, 8, 8, 1, 1, Role.ROOF, false);
		Grid g = b.build().layout().grid();
		// every corner of a hip ring is an outer corner, the edges stay straight
		for (int[] c : new int[][] {{0, 0}, {8, 0}, {0, 8}, {8, 8}}) {
			assertTrue(g.get(c[0], 1, c[1]).properties().get("shape").startsWith("outer"), c[0] + "," + c[1]);
		}
		assertEquals("straight", g.get(4, 1, 0).properties().get("shape"));
	}

	@Test
	void buildIsDeterministic() {
		Map<String, Object> a = TemplateExporter.template(BlueprintSkeletonTest.demoFloor().layout());
		Map<String, Object> c = TemplateExporter.template(BlueprintSkeletonTest.demoFloor().layout());
		assertArrayEquals(Nbt.writeGzip(a), Nbt.writeGzip(c));
	}

	@Test
	void outOfBoundsVerbsThrow() {
		Blueprint b = floor(3, 3, 3);
		assertThrows(IllegalArgumentException.class, () -> b.set(3, 0, 0, Role.WALL));
		assertThrows(IllegalArgumentException.class, () -> b.fill(0, 0, 0, 0, 0, 5, Role.WALL));
	}
}

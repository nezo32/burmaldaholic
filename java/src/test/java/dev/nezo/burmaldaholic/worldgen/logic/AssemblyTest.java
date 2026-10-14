package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Game;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Every pool combination of every style assembles like vanilla's rigid jigsaw would: all families present,
 * no overlaps, connectors pair up, footprint ≤ 47×47, reach ≤ 48, content budget (architecture doc §4.1,
 * §9.1, §9.5). Runs on the real pack (vacuous until lane CB-2 delivers pieces) and on the placeholders.
 */
class AssemblyTest {
	private static void assertGood(List<Assembly.Result> results) {
		for (Assembly.Result r : results) {
			String where = r.style() + "/" + r.name();
			assertEquals(List.of(), r.problems(), where);
			assertTrue(r.pools().containsAll(PackFiles.REQUIRED_POOLS), where + " pools " + r.pools());
			assertTrue(r.families().containsAll(Game.Family.REQUIRED), where + " lacks " + missing(r));
		}
	}

	private static List<Game.Family> missing(Assembly.Result r) {
		List<Game.Family> m = new ArrayList<>(Game.Family.REQUIRED);
		m.removeAll(r.families());
		return m;
	}

	@Test
	void realPackAssembliesAreComplete() {
		assertGood(Assembly.all(CasinoPack.layouts()));
	}

	@Test
	void placeholderAssembliesAreCompleteForEveryCombination() {
		List<Assembly.Result> results = Assembly.all(PackFilesTest.placeholderPack());
		assertEquals(PackFiles.STYLE_BIOMES.size() * 4, results.size(), "2 floors × 2 rears per style");
		assertGood(results);
		assertEquals(List.of("floor_a+office", "floor_a+vip", "floor_b+office", "floor_b+vip"),
			results.stream().filter(r -> r.style().equals("tudor")).map(Assembly.Result::name).toList());
	}

	/** Connector math: entrance back (north) → floor front; floor east → bar entry (+z face) rotated a quarter turn. */
	@Test
	void connectorsMatchVanillaRigidPlacement() {
		Assembly.Result r = Assembly.all(PackFilesTest.placeholderPack()).getFirst();
		Map<String, Assembly.Placed> by = new java.util.HashMap<>();
		r.pieces().forEach(p -> by.put(PackFiles.pieceName(p.layout().id()), p));
		Assembly.Placed entrance = by.get("entrance");
		Assembly.Placed floor = by.get("floor_a");
		Assembly.Placed bar = by.get("bar");
		assertEquals(0, entrance.turns());
		assertEquals(0, floor.turns());
		assertEquals(new Vec(0, 0, -17), floor.origin());
		assertEquals(1, bar.turns(), "bar entry on its +z face turned to face west");
		// the two connector cells of a joint are neighbours, facing each other
		for (Assembly.Placed child : r.pieces()) {
			if (child == entrance) {
				continue;
			}
			Layout.Jigsaw entry = child.layout().jigsaws().stream().filter(j -> j.pool().equals("minecraft:empty")).findFirst().orElseThrow();
			Vec world = Geometry.toWorld(child.origin(), entry.pos(), child.turns());
			Facing out = Assembly.horizontal(entry.orientation()).orElseThrow().rotate(child.turns());
			Vec neighbour = world.add(new Vec(out.dx(), 0, out.dz()));
			boolean paired = false;
			for (Assembly.Placed parent : r.pieces()) {
				for (Layout.Jigsaw j : parent.layout().jigsaws()) {
					if (j.target().equals(entry.name()) && Geometry.toWorld(parent.origin(), j.pos(), parent.turns()).equals(neighbour)) {
						paired = true;
					}
				}
			}
			assertTrue(paired, child.layout().id() + " entry connector has no partner");
			assertEquals(entrance.box().minY(), child.box().minY(), "every piece shares the floor row");
		}
	}

	@Test
	void brokenAssembliesAreReported() {
		List<Layout> pack = new ArrayList<>(PlaceholderPieces.style("tudor").stream().map(Piece::layout).toList());
		// a second bar-entry connector makes the joint ambiguous
		Layout bar = pack.stream().filter(l -> l.id().endsWith("/bar")).findFirst().orElseThrow();
		List<Layout.Jigsaw> js = new ArrayList<>(bar.jigsaws());
		Layout.Jigsaw j = js.getFirst();
		js.add(new Layout.Jigsaw(new Vec(1, j.pos().y(), j.pos().z()), j.orientation(), j.name(), j.target(), j.pool(), j.finalState(), j.joint()));
		pack.replaceAll(l -> l == bar ? new Layout(bar.id(), bar.kind(), bar.grid(), bar.markers(), js) : l);
		List<Assembly.Result> results = Assembly.all(pack);
		assertFalse(results.isEmpty());
		assertTrue(results.stream().allMatch(r -> r.problems().stream().anyMatch(p -> p.contains("need exactly 1"))), results.getFirst().problems().toString());
	}
}

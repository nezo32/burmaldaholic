package dev.nezo.burmaldaholic.worldgen.logic.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.List;
import org.junit.jupiter.api.Test;

/** High Roller Lounge beside an End City (owner playtest W2). */
class EndSiteTest {
	static final Vec SIZE = new Vec(25, 42, 25);
	static final Vec ENTRY = new Vec(12, 4, 24);
	static final List<Geometry.Box> CITY = List.of(new Geometry.Box(0, 60, 0, 20, 140, 20), new Geometry.Box(15, 70, 15, 30, 160, 30));

	/** A flat round island at y 60, radius 130 around the city. */
	static final EndSite.Surface ISLAND = (x, z) -> (x - 15) * (x - 15) + (z - 15) * (z - 15) <= 130 * 130 ? 60 : EndSite.Surface.VOID;

	@Test
	void standsOnTheIslandClearOfEveryCityPiece() {
		EndSite.Plan p = EndSite.plan(CITY, SIZE, ENTRY, 3, ISLAND, 1, 0, 0).orElseThrow();
		Geometry.Box fp = p.placement().box();
		for (Geometry.Box b : CITY) {
			assertTrue(Geometry.gapXZ(fp, b) >= EndSite.CLEAR, "clear of " + b + ": " + fp);
		}
		assertEquals(60, p.plateY());
		assertEquals(57, fp.minY(), "plate row 3 on the surface");
		assertEquals(0, p.steps(), "flat island: no steps");
		// the door faces the city
		int dx = 15 - fp.centerX();
		int dz = 15 - fp.centerZ();
		assertTrue(p.door().dx() * dx + p.door().dz() * dz > 0, "door " + p.door() + " looks at the city");
		assertTrue(Geometry.chunkReach(fp, 0, 0) <= 7);
	}

	@Test
	void tooSteepOrNoIslandFindsNothing() {
		assertTrue(EndSite.plan(CITY, SIZE, ENTRY, 3, (x, z) -> 60 + Math.abs(x) / 2 + Math.abs(z) / 2, 1, 0, 0).isEmpty());
		assertTrue(EndSite.plan(CITY, SIZE, ENTRY, 3, (x, z) -> EndSite.Surface.VOID, 1, 0, 0).isEmpty());
		// a small island: nothing 24 blocks away from the city has land
		EndSite.Surface small = (x, z) -> x >= -10 && x <= 40 && z >= -10 && z <= 40 ? 60 : EndSite.Surface.VOID;
		assertTrue(EndSite.plan(CITY, SIZE, ENTRY, 3, small, 1, 0, 0).isEmpty());
	}

	@Test
	void stepsLeadDownToLowerGround() {
		Geometry.Placement p0 = Geometry.atMin(new Vec(100, 0, 0), SIZE, Facing.SOUTH);
		Geometry.Box b = p0.box();
		EndSite.Surface s = (x, z) -> z <= b.maxZ() ? 62 : 59;
		EndSite.Plan p = EndSite.fit(p0, Facing.SOUTH, SIZE, ENTRY, 3, s, 1);
		assertEquals(3, p.steps());
		assertEquals(62, p.plateY());
		// the ground in front is higher than the plate: refused
		assertNull(EndSite.fit(p0, Facing.SOUTH, SIZE, ENTRY, 3, (x, z) -> z <= b.maxZ() ? 62 : 63, 1));
		// spread over the footprint up to 4 is fine, 5 is not
		assertTrue(EndSite.fit(p0, Facing.SOUTH, SIZE, ENTRY, 3, (x, z) -> x < 112 ? 62 : 58, 1) != null);
		assertNull(EndSite.fit(p0, Facing.SOUTH, SIZE, ENTRY, 3, (x, z) -> x < 112 ? 62 : 57, 1));
	}
}

package dev.nezo.burmaldaholic.worldgen.logic.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.logic.pieces.HighRollerLounge;
import java.util.List;
import org.junit.jupiter.api.Test;

/** High Roller Lounge beside an End City (owner playtest W2). */
class EndSiteTest {
	/** The v0.2 Lounge (CR-2 contract). */
	static final EndSite.Shape SHAPE = new EndSite.Shape(new Vec(HighRollerLounge.SX, HighRollerLounge.SY, HighRollerLounge.SZ),
		HighRollerLounge.PZ, HighRollerLounge.FLOOR, HighRollerLounge.STAIR_FOOT);
	static final List<Geometry.Box> CITY = List.of(new Geometry.Box(0, 60, 0, 20, 140, 20), new Geometry.Box(15, 70, 15, 30, 160, 30));

	/** A flat round island at y 60, radius 130 around the city. */
	static final EndSite.Surface ISLAND = (x, z) -> (x - 15) * (x - 15) + (z - 15) * (z - 15) <= 130 * 130 ? 60 : EndSite.Surface.VOID;

	@Test
	void standsOnTheIslandClearOfEveryCityPiece() {
		EndSite.Plan p = EndSite.plan(CITY, SHAPE, ISLAND, 1, 0, 0).orElseThrow();
		Geometry.Box fp = p.placement().box();
		for (Geometry.Box b : CITY) {
			assertTrue(Geometry.gapXZ(fp, b) >= EndSite.CLEAR, "clear of " + b + ": " + fp);
		}
		assertEquals(60, p.plateY());
		assertEquals(60 - HighRollerLounge.FLOOR, fp.minY(), "plate row on the surface");
		int dx = 15 - fp.centerX();
		int dz = 15 - fp.centerZ();
		assertTrue(p.door().dx() * dx + p.door().dz() * dz > 0, "door " + p.door() + " looks at the city");
		assertTrue(Geometry.chunkReach(fp, 0, 0) <= 7);
	}

	@Test
	void tooSteepOrNoIslandFindsNothing() {
		assertTrue(EndSite.plan(CITY, SHAPE, (x, z) -> 60 + Math.abs(x) / 2 + Math.abs(z) / 2, 1, 0, 0).isEmpty());
		assertTrue(EndSite.plan(CITY, SHAPE, (x, z) -> EndSite.Surface.VOID, 1, 0, 0).isEmpty());
		EndSite.Surface small = (x, z) -> x >= -10 && x <= 40 && z >= -10 && z <= 40 ? 60 : EndSite.Surface.VOID;
		assertTrue(EndSite.plan(CITY, SHAPE, small, 1, 0, 0).isEmpty(), "nothing 24 blocks from the city has land");
	}

	@Test
	void plateOnTheHighestGroundAndTheStairFootOnTheGround() {
		Geometry.Placement p0 = Geometry.atMin(new Vec(100, 0, 0), SHAPE.size(), Facing.SOUTH);
		int plateEnd = p0.box().minZ() + HighRollerLounge.PZ;
		// spread over the plate up to 4 is fine, 5 is not
		EndSite.Plan ok = EndSite.fit(p0, Facing.SOUTH, SHAPE, (x, z) -> z > plateEnd ? 58 : x < 112 ? 62 : 58, 1);
		assertNotNull(ok);
		assertEquals(62, ok.plateY());
		assertNull(EndSite.fit(p0, Facing.SOUTH, SHAPE, (x, z) -> z > plateEnd ? 57 : x < 112 ? 62 : 57, 1));
		// ground at the stair foot: from one below the foot's ground row up to the plate
		assertNotNull(EndSite.fit(p0, Facing.SOUTH, SHAPE, (x, z) -> z > plateEnd ? 57 : 62, 1), "foot ground row 58, ground 57");
		assertNull(EndSite.fit(p0, Facing.SOUTH, SHAPE, (x, z) -> z > plateEnd ? 56 : 62, 1), "two below: a ledge");
		assertNotNull(EndSite.fit(p0, Facing.SOUTH, SHAPE, (x, z) -> 62, 1), "flat: the stair sinks");
		assertNull(EndSite.fit(p0, Facing.SOUTH, SHAPE, (x, z) -> z > plateEnd ? 63 : 62, 1), "a bank in front of the door");
	}
}

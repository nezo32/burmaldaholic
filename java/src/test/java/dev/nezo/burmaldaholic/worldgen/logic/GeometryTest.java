package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeometryTest {
	@Test
	void rotationMatchesMinecraftTransform() {
		Vec p = new Vec(3, 1, 5);
		assertEquals(p, Geometry.rotate(p, 0));
		assertEquals(new Vec(-5, 1, 3), Geometry.rotate(p, 1));   // CLOCKWISE_90: (-z, x)
		assertEquals(new Vec(-3, 1, -5), Geometry.rotate(p, 2));  // 180
		assertEquals(new Vec(5, 1, -3), Geometry.rotate(p, 3));   // COUNTERCLOCKWISE_90: (z, -x)
		assertEquals(Geometry.rotate(p, 3), Geometry.rotate(p, -1));
	}

	@Test
	void facingRotationAgreesWithPositionRotation() {
		for (Facing f : Facing.values()) {
			for (int t = 0; t < 4; t++) {
				Vec step = Geometry.rotate(new Vec(f.dx(), 0, f.dz()), t);
				Facing r = f.rotate(t);
				assertEquals(new Vec(r.dx(), 0, r.dz()), step, f + " turns " + t);
			}
		}
		assertEquals(Facing.WEST, Facing.SOUTH.rotate(1));
		assertEquals(Facing.NORTH, Facing.SOUTH.opposite());
	}

	@Test
	void entranceFacesRequestedSide() {
		for (Facing f : Facing.values()) {
			assertEquals(f, Facing.SOUTH.rotate(Geometry.turnsToFace(f)));
		}
	}

	@Test
	void templateBoxSizeAndOriginForMin() {
		Vec size = new Vec(21, 12, 13);
		for (int t = 0; t < 4; t++) {
			Geometry.Box b = Geometry.templateBox(new Vec(100, 60, -40), size, t);
			boolean swap = t % 2 == 1;
			assertEquals(swap ? 13 : 21, b.xSpan());
			assertEquals(swap ? 21 : 13, b.zSpan());
			assertEquals(12, b.maxY() - b.minY() + 1);
			Vec want = new Vec(7, 30, 9);
			Vec origin = Geometry.originForMin(want, size, t);
			assertEquals(want, Geometry.templateBox(origin, size, t).min(), "turns " + t);
		}
	}

	@Test
	void besidePlacesAdjacentOutsideWithEntranceOutward() {
		Geometry.Box host = new Geometry.Box(0, 33, 0, 79, 70, 59);
		Vec size = new Vec(21, 12, 21);
		for (Facing side : Facing.values()) {
			Geometry.Placement p = Geometry.beside(host, size, side, 0);
			assertFalse(p.box().intersects(host), side.toString());
			assertEquals(host.minY(), p.box().minY());
			// touching: one step towards the host from the parlor's facing edge enters the host
			switch (side) {
				case EAST -> assertEquals(host.maxX() + 1, p.box().minX());
				case WEST -> assertEquals(host.minX() - 1, p.box().maxX());
				case SOUTH -> assertEquals(host.maxZ() + 1, p.box().minZ());
				case NORTH -> assertEquals(host.minZ() - 1, p.box().maxZ());
			}
			// entrance (template +z) faces away from the host
			assertEquals(side, Facing.SOUTH.rotate(p.quarterTurns()));
			// entrance cell (template local (10, 1, 20)) is on the far side
			Vec door = Geometry.toWorld(p.origin(), new Vec(10, 1, 20), p.quarterTurns());
			Vec back = Geometry.toWorld(p.origin(), new Vec(10, 1, 0), p.quarterTurns());
			int doorDist = Math.abs(door.x() - host.centerX()) + Math.abs(door.z() - host.centerZ());
			int backDist = Math.abs(back.x() - host.centerX()) + Math.abs(back.z() - host.centerZ());
			assertTrue(doorDist > backDist, side.toString());
		}
	}

	@Test
	void onTopSitsAboveCentred() {
		Geometry.Box tower = new Geometry.Box(10, 100, 10, 18, 110, 18);
		Geometry.Placement p = Geometry.onTop(tower, new Vec(13, 9, 13), Facing.EAST);
		assertEquals(111, p.box().minY());
		assertEquals(tower.centerX(), p.box().centerX());
		assertEquals(tower.centerZ(), p.box().centerZ());
		assertEquals(Facing.EAST, Facing.SOUTH.rotate(p.quarterTurns()));
	}

	@Test
	void inFrontFacesTheViewer() {
		Vec feet = new Vec(0, 64, 0);
		for (Facing look : Facing.values()) {
			Geometry.Placement p = Geometry.inFront(feet, new Vec(17, 10, 17), look, 2);
			assertFalse(p.box().contains(0, 64, 0));
			assertEquals(63, p.box().minY());
			assertEquals(look.opposite(), Facing.SOUTH.rotate(p.quarterTurns()));
			int nearX = look == Facing.EAST ? p.box().minX() : look == Facing.WEST ? p.box().maxX() : 0;
			if (look == Facing.EAST || look == Facing.WEST) {
				assertEquals(3, Math.abs(nearX));
			}
		}
	}

	@Test
	void chunkReach() {
		Geometry.Box b = new Geometry.Box(-20, 0, 100, 40, 10, 130);
		assertEquals(8, Geometry.chunkReach(b, 0, 0));
		assertEquals(2, Geometry.chunkReach(b, 0, 6));
	}

	@Test
	void boxContainsEntityPositions() {
		Geometry.Box b = new Geometry.Box(0, 0, 0, 2, 2, 2);
		assertTrue(b.contains(2.99, 0, 0, 0));
		assertFalse(b.contains(3.0, 0, 0, 0));
		assertTrue(b.contains(3.5, 0, 0, 1));
	}
}

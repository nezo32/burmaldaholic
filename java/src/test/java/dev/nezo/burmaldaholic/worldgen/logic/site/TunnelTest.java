package dev.nezo.burmaldaholic.worldgen.logic.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import org.junit.jupiter.api.Test;

class TunnelTest {
	@Test
	void stairsFaceTheWayUp() {
		Tunnel t = new Tunnel(0, 0, Facing.EAST, new int[] {40, 41, 42, 42, 41, 40, 40});
		assertNull(t.stair(0));
		assertEquals(Facing.EAST, t.stair(1));
		assertEquals(Facing.EAST, t.stair(2));
		assertEquals(Facing.WEST, t.stair(3), "top of the ridge, next step goes down: the stair faces back");
		assertEquals(Facing.WEST, t.stair(4));
		assertNull(t.stair(5));
		assertNull(t.stair(6));
	}

	@Test
	void boxCoversShellAndSteps() {
		Tunnel t = new Tunnel(10, 5, Facing.NORTH, new int[] {40, 41, 42});
		Geometry.Box b = t.box();
		assertEquals(new Geometry.Box(8, 38, 3, 12, 46, 5), b);
		assertEquals(new Geometry.Box(9, 40, 3, 11, 45, 5), t.walkBox(0, 2));
	}

	@Test
	void noCliffs() {
		assertThrows(IllegalArgumentException.class, () -> new Tunnel(0, 0, Facing.EAST, new int[] {40, 42}));
	}
}

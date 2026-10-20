package dev.nezo.burmaldaholic.worldgen.logic.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Nether site probe on synthetic terrain (owner playtest W1). */
class NetherSiteTest {
	static final int LAVA = 32;
	static final Geometry.Box HOST = new Geometry.Box(0, 30, 0, 40, 60, 40);
	static final Vec SIZE = new Vec(27, 20, 28);
	static final NetherSite.Doors DOORS = new NetherSite.Doors(new Vec(13, 3, 27), new Vec(18, 4, -1));
	/** Bastion rooms: template air at x 30..40, y 36..45 inside the host box. */
	static final NetherSite.Host BASTION = (x, y, z) -> !HOST.contains(x, y, z) ? NetherSite.Host.OUTSIDE
		: x >= 30 && y >= 36 && y <= 45 ? NetherSite.Host.AIR : NetherSite.Host.SOLID;

	/** Netherrack below y 70, except a cavern east of x 75 (ground at y 35, open 36..69). */
	static final NetherSite.Terrain CAVERN_EAST = (x, y, z) -> y < 70 && !(x >= 75 && y >= 36);

	private static Optional<NetherSite.Plan> plan(NetherSite.Terrain t) {
		return NetherSite.plan(HOST, List.of(HOST), SIZE, DOORS, t, BASTION, LAVA, 1, 1, 0);
	}

	@Test
	void picksTheSideFacingTheCavernAboveTheLavaSea() {
		NetherSite.Plan p = plan(CAVERN_EAST).orElseThrow();
		Geometry.Box env = p.placement().box();
		assertTrue(env.minY() >= LAVA, "floor at or above the lava sea: " + env);
		assertFalse(env.intersects(HOST), "beside the bastion, not in it");
		assertEquals(41, env.minX(), "east side");
		Tunnel front = p.front();
		assertEquals(Facing.EAST, front.dir());
		assertEquals(69, front.x(), "front tunnel starts right outside the door");
		assertEquals(env.minY() + 3, front.feet(0), "threshold height");
		int lastX = front.cx(front.length() - 1);
		assertTrue(lastX >= 75 && lastX <= 76, "ends where the cavern opens: " + front);
		for (int f : front.feet()) {
			assertTrue(f > LAVA, "walk above the lava sea");
		}
		assertNotNull(p.back(), "back door opened");
		assertTrue(p.backIntoHost(), "back tunnel ends in a bastion room");
		assertEquals(Facing.WEST, p.back().dir());
		int end = p.back().cx(p.back().length() - 1) + p.back().dir().dx();
		assertEquals(NetherSite.Host.AIR, BASTION.cell(end, p.back().feet(p.back().length() - 1), p.back().cz(0)), "next step is a room");
	}

	@Test
	void solidRockEverywhereRejectsTheBastion() {
		assertTrue(plan((x, y, z) -> true).isEmpty());
	}

	@Test
	void openAirWithoutGroundIsNotAnExit() {
		// a lava sea under open air east of x 75: the tunnel would end over lava, the walk never reaches ground
		NetherSite.Terrain t = (x, y, z) -> x < 75;
		assertTrue(plan(t).isEmpty());
	}

	@Test
	void lowBastionIsLiftedAboveTheLavaSea() {
		Geometry.Box low = new Geometry.Box(0, 10, 0, 40, 60, 40);
		NetherSite.Plan p = NetherSite.plan(low, List.of(low), SIZE, DOORS, CAVERN_EAST, BASTION, LAVA, 1, 1, 0).orElseThrow();
		assertTrue(p.placement().box().minY() >= LAVA);
	}

	@Test
	void tunnelsClimbOneBlockPerStep() {
		// cavern floor at y 44: the tunnel ramps up from the threshold
		NetherSite.Terrain t = (x, y, z) -> y < 70 && !(x >= 75 && y >= 45);
		NetherSite.Plan p = plan(t).orElseThrow();
		int[] f = p.front().feet();
		for (int i = 1; i < f.length; i++) {
			assertTrue(Math.abs(f[i] - f[i - 1]) <= 1);
		}
		assertEquals(45, f[f.length - 1], "ends on the cavern floor");
	}

	@Test
	void walkNeedsGroundAndAvoidsLava() {
		Geometry.Box env = new Geometry.Box(0, 40, 0, 10, 50, 10);
		// a ledge at y 40 runs east forever
		assertTrue(NetherSite.walksAway((x, y, z) -> y < 40, LAVA, 11, 40, 5, env, List.of()));
		// only lava below the start
		assertFalse(NetherSite.walksAway((x, y, z) -> false, LAVA, 11, 40, 5, env, List.of()));
	}
}

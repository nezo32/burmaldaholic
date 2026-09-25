package dev.nezo.burmaldaholic.games.extras.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.dice.DiceFaces;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Lane J-L6 review: the in-world PvP duel (tables.md §3.4) ends on the server's faces for every pair and side, rests on
 * the ground between the players on opposite sides of the centre line, follows the screens' round beats (hidden before
 * the throw, a tied round picked up at the sweep, the last round lingering) and the face-up rotation of the die model
 * really turns each face up.
 */
class DuelStagePlanTest {
	private static final DuelStagePlan.Point A = new DuelStagePlan.Point(0.5, 65.1, 0.5);
	private static final DuelStagePlan.Point B = new DuelStagePlan.Point(4.5, 65.1, 2.5);

	@Test
	void everyPairEndsOnTheServerFacesOnTheGround() {
		DuelStagePlan.Sample s = new DuelStagePlan.Sample();
		for (int seed = 0; seed < 16; seed++) {
			for (int a = 1; a <= 6; a++) {
				for (int b = 1; b <= 6; b++) {
					int[][] round = {{a, b}, {7 - a, 7 - b}};
					DuelStagePlan plan = new DuelStagePlan(seed, List.<int[][]>of(round), A, B, 64.0);
					double t = DuelTimeline.reveal(0) + 10;
					for (int side = 0; side < 2; side++) {
						for (int die = 0; die < 2; die++) {
							plan.sample(side, die, t, s);
							assertTrue(s.visible && s.resting && s.faceShown, "at rest after the reveal");
							assertEquals(round[side][die], s.face, "face");
							assertEquals(64.0 + DuelStagePlan.DIE / 2, s.y, 1e-9, "on the ground");
							assertEquals(0, Math.floorMod(Math.round(s.yaw - headingOf(side)), 90), "square to its throw at rest");
						}
					}
				}
			}
		}
	}

	private static double headingOf(int side) {
		double dx = (B.x() - A.x()) * (side == 0 ? 1 : -1);
		double dz = (B.z() - A.z()) * (side == 0 ? 1 : -1);
		double len = Math.hypot(dx, dz);
		return Math.toDegrees(Math.atan2(-dx / len, dz / len));
	}

	@Test
	void pairsLandBetweenThePlayersOnOppositeSides() {
		DuelStagePlan plan = new DuelStagePlan(7, List.<int[][]>of(new int[][] {{3, 4}, {6, 1}}), A, B, 64.0);
		DuelStagePlan.Point ca = plan.restCentre(0, 0);
		DuelStagePlan.Point cb = plan.restCentre(0, 1);
		double mx = (A.x() + B.x()) / 2;
		double mz = (A.z() + B.z()) / 2;
		double half = Math.hypot(B.x() - A.x(), B.z() - A.z()) / 2;
		for (DuelStagePlan.Point c : List.of(ca, cb)) {
			assertTrue(Math.hypot(c.x() - mx, c.z() - mz) < half, "near the midpoint: " + c);
		}
		// opposite sides of the line A→B
		double ux = B.x() - A.x();
		double uz = B.z() - A.z();
		double sa = ux * (ca.z() - A.z()) - uz * (ca.x() - A.x());
		double sb = ux * (cb.z() - A.z()) - uz * (cb.x() - A.x());
		assertTrue(sa * sb < 0, "the pairs do not overlap: " + sa + " / " + sb);
		// the dice of one pair never overlap
		DuelStagePlan.Sample d0 = new DuelStagePlan.Sample();
		DuelStagePlan.Sample d1 = new DuelStagePlan.Sample();
		plan.sample(0, 0, DuelTimeline.reveal(0) + 10, d0);
		plan.sample(0, 1, DuelTimeline.reveal(0) + 10, d1);
		assertTrue(Math.hypot(d0.x - d1.x, d0.z - d1.z) >= DuelStagePlan.DIE, "dice apart");
	}

	@Test
	void roundBeatsMatchTheScreens() {
		List<int[][]> rounds = List.of(new int[][] {{3, 4}, {2, 5}}, new int[][] {{6, 6}, {1, 2}});
		DuelStagePlan plan = new DuelStagePlan(3, rounds, A, B, 64.0);
		DuelStagePlan.Sample s = new DuelStagePlan.Sample();
		plan.sample(0, 0, DuelTimeline.throwAt(0, 0) - 1, s);
		assertFalse(s.visible, "in the hand before the throw");
		plan.sample(1, 0, DuelTimeline.throwAt(0, 1) - 1, s);
		assertFalse(s.visible, "their pair is thrown 350 ms later");
		plan.sample(0, 0, DuelTimeline.throwAt(0, 0) + 100, s);
		assertTrue(s.visible && !s.faceShown && s.y > 64.3, "in flight, tumbling (no readable face)");
		assertTrue(plan.totalVisible(0, DuelTimeline.reveal(0)), "tie round total shown");
		// the tie: picked up at the sweep, gone after 250 ms, thrown again with round 2
		double sweep = DuelTimeline.roundStart(0) + DuelTimeline.SWEEP_AT;
		plan.sample(0, 0, sweep + 100, s);
		assertTrue(s.visible && s.scale < 1 && s.scale > 0, "shrinking");
		plan.sample(0, 0, sweep + DuelStagePlan.PICKUP_MS + 1, s);
		assertFalse(s.visible, "picked up");
		assertFalse(plan.totalVisible(0, sweep + 10), "total gone with the dice");
		plan.sample(0, 1, DuelTimeline.reveal(1) + 5, s);
		assertTrue(s.visible && s.round == 1 && s.face == 6, "round 2 lands on its faces");
		assertEquals(7, plan.total(0, 0));
		assertEquals(12, plan.total(1, 0));
		plan.sample(0, 1, plan.endMs() - 1, s);
		assertTrue(s.visible, "the last round lingers");
		plan.sample(0, 1, plan.endMs(), s);
		assertFalse(s.visible, "then goes");
		assertEquals(DuelTimeline.reveal(1) + DuelStagePlan.LINGER_MS, plan.endMs());
	}

	/** Rodrigues rotation of v about the unit axis by deg. */
	private static long[] rotate(int[] v, double[] axisDeg) {
		double a = Math.toRadians(axisDeg[3]);
		double kx = axisDeg[0];
		double ky = axisDeg[1];
		double kz = axisDeg[2];
		double c = Math.cos(a);
		double s = Math.sin(a);
		double dot = kx * v[0] + ky * v[1] + kz * v[2];
		double cx = ky * v[2] - kz * v[1];
		double cy = kz * v[0] - kx * v[2];
		double cz = kx * v[1] - ky * v[0];
		return new long[] {Math.round(v[0] * c + cx * s + kx * dot * (1 - c)), Math.round(v[1] * c + cy * s + ky * dot * (1 - c)),
			Math.round(v[2] * c + cz * s + kz * dot * (1 - c))};
	}

	@Test
	void theModelRotationPutsEachFaceUp() {
		int[] layout = DiceFaces.layout(1);
		assertArrayEquals(new int[] {1, 6, 2, 5, 3, 4}, layout, "the dice_display model: up 1, down 6, north 2, south 5, east 3, west 4");
		for (int face = 1; face <= 6; face++) {
			assertArrayEquals(new long[] {0, 1, 0}, rotate(DiceFaces.modelNormal(face), DiceFaces.upRotation(face)), "face " + face + " up");
		}
	}
}

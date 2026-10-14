package dev.nezo.burmaldaholic.games.extras.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.dice.CubeGroup;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DiceSolver;
import dev.nezo.burmaldaholic.core.anim.dice.DiceTray;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import dev.nezo.burmaldaholic.core.anim.dice.Quat;
import dev.nezo.burmaldaholic.core.anim.dice.ThrowSpec;
import dev.nezo.burmaldaholic.core.anim.motion.StageMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The in-world PvP duel on the new solver (DI §6, §10.1-11, §10.1-13): every pair ends on the server's faces on the
 * ground between the players, on its own half; the beats follow the screens (hidden in the hand before the throw, a tied
 * round picked up at the sweep, the last round lingering); the item-display fallback never turns a die by 170° or more
 * in one 2-tick step; PvP rolls are keyed by player side and the opponent's lane is the exact mirror.
 */
class DuelStagePlanTest {
	private static final DuelStagePlan.Point A = new DuelStagePlan.Point(0.5, 65.1, 0.5);
	private static final DuelStagePlan.Point B = new DuelStagePlan.Point(3.5, 65.1, 2.5);

	/** The MC "up" face of a world quaternion: the world yaw is about +Y, so the tray top face is the MC +Y face. */
	private static int mcFaceUp(DuelStagePlan.Sample s) {
		return CubeGroup.faceUp(s.tray.q);
	}

	@Test
	void everyPairEndsOnTheServerFacesOnTheGround() {
		DuelStagePlan.Sample s = new DuelStagePlan.Sample();
		for (int seed = 0; seed < 4; seed++) {
			for (int a = 1; a <= 6; a++) {
				for (int b = 1; b <= 6; b++) {
					int[][] round = {{a, b}, {7 - a, 7 - b}};
					DuelStagePlan plan = new DuelStagePlan(seed, List.<int[][]>of(round), A, B, 64.0);
					double t = DuelTimeline.reveal(0) + 10;
					for (int side = 0; side < 2; side++) {
						for (int die = 0; die < 2; die++) {
							plan.sample(side, die, t, s);
							assertTrue(s.visible && s.resting, "at rest after the reveal");
							assertEquals(round[side][die], s.face);
							assertEquals(round[side][die], mcFaceUp(s), "top face == the server's face");
							assertEquals(64.0 + DuelStagePlan.DIE / 2, s.y, 1e-9, "on the ground");
							// the world quaternion is a unit quaternion and keeps the tray's up axis (yaw only)
							assertEquals(1, Quat.norm(s.q), 1e-9);
						}
					}
				}
			}
		}
	}

	@Test
	void pairsLandBetweenThePlayersOnTheirOwnHalf() {
		for (int seed = 0; seed < 64; seed++) {
			DuelStagePlan plan = new DuelStagePlan(seed, List.<int[][]>of(new int[][] {{3, 4}, {6, 1}}), A, B, 64.0);
			double dx = B.x() - A.x();
			double dz = B.z() - A.z();
			double len = Math.hypot(dx, dz);
			for (int side = 0; side < 2; side++) {
				DuelStagePlan.Point c = plan.restCentre(0, side);
				// projection on the A→B line (0 = A, 1 = B)
				double u = ((c.x() - A.x()) * dx + (c.z() - A.z()) * dz) / (len * len);
				double off = Math.abs((c.x() - A.x()) * dz - (c.z() - A.z()) * dx) / len;
				if (side == 0) assertTrue(u > 0.1 && u < 0.5, "A's pair on A's half: " + u);
				else assertTrue(u > 0.5 && u < 0.9, "B's pair on B's half: " + u);
				assertTrue(off < 0.4, "near the line between the players: " + off);
			}
		}
	}

	@Test
	void beatsFollowTheScreens() {
		List<int[][]> rounds = List.of(new int[][] {{3, 4}, {5, 2}}, new int[][] {{6, 5}, {2, 1}});
		DuelStagePlan plan = new DuelStagePlan(7, rounds, A, B, 64.0);
		DuelStagePlan.Sample s = new DuelStagePlan.Sample();
		plan.sample(0, 0, DuelTimeline.throwAt(0, 0) - 1, s);
		assertFalse(s.visible, "in the hand before A's throw");
		plan.sample(1, 0, DuelTimeline.throwAt(0, 1) - 1, s);
		assertFalse(s.visible, "B throws 350 ms later");
		plan.sample(0, 0, DuelTimeline.throwAt(0, 0) + 30, s);
		assertTrue(s.visible && !s.resting);
		// the tied round is picked up at the sweep and the next round throws again
		plan.sample(0, 0, DuelTimeline.SWEEP_AT + DuelStagePlan.PICKUP_MS + 1, s);
		assertFalse(s.visible, "picked up");
		plan.sample(0, 0, DuelTimeline.roundStart(1) + DuelTimeline.THROW + 20, s);
		assertTrue(s.visible && s.round == 1);
		assertEquals(DuelTimeline.reveal(1) + DuelStagePlan.LINGER_MS, plan.endMs());
		plan.sample(0, 0, plan.endMs() - 1, s);
		assertTrue(s.visible && s.resting, "the last round lingers");
		// totals appear after the rest + count-up, never before
		assertFalse(plan.totalVisible(0, plan.restAt(0, 0) + 1));
		assertTrue(plan.totalVisible(0, plan.totalAt(0, 0) + 1));
		assertTrue(plan.totalAt(0, 1) <= DuelTimeline.reveal(0), "both totals by the compare");
	}

	@Test
	void displayEntityStepsNeverTakeTheLongWayRound() {
		// DI §10.1-13: the server poses the item displays every 2 ticks; the client slerps between two poses, so each
		// step must turn the die by less than 180° (here < 170°) or it would spin backwards
		DuelStagePlan.Sample s = new DuelStagePlan.Sample();
		double[] prev = new double[4];
		double worst = 0;
		for (int seed = 0; seed < 2000; seed++) {
			DuelStagePlan plan = new DuelStagePlan(seed * 7919, List.<int[][]>of(new int[][] {{1 + seed % 6, 1 + seed / 6 % 6}, {1 + seed / 36 % 6, 1 + seed % 5}}),
				A, B, 64.0);
			for (int side = 0; side < 2; side++) {
				for (int die = 0; die < 2; die++) {
					boolean have = false;
					for (int tick = 0; tick * 50 <= DuelTimeline.landAt(0, 1) + 100; tick += 2) {
						plan.sample(side, die, tick * 50.0, s);
						if (!s.visible) {
							have = false;
							continue;
						}
						if (have) {
							double dot = Math.abs(Quat.dot(prev, s.q));
							double ang = Math.toDegrees(2 * Math.acos(Math.min(1, dot)));
							worst = Math.max(worst, ang);
						}
						Quat.copy(s.q, prev);
						have = true;
					}
				}
			}
		}
		assertTrue(worst < 170, "largest 2-tick turn " + worst + "°");
	}

	@Test
	void pvpRollsAreKeyedByPlayerSideAndMirroredForTheOpponent() {
		// A's roll: the same key on A's screen (left lane), on B's screen (the same roll, mirrored) and in the world
		int seed = 0xBEEF;
		int[] faces = {4, 2};
		DiceRoll a = DiceSolver.solve(DiceTray.duelLane(DiceTray.Side.LEFT), ThrowSpec.cup(DiceTray.Side.LEFT), DuelTimeline.rollSeed(seed, 0, 0), faces,
			DuelTimeline.T_DUEL);
		DiceRoll again = DiceSolver.solve(DiceTray.duelLane(DiceTray.Side.LEFT), ThrowSpec.cup(DiceTray.Side.LEFT), DuelTimeline.rollSeed(seed, 0, 0), faces,
			DuelTimeline.T_DUEL);
		StageMap left = StageMap.of(0, 0, 16, 16, 14.4);
		StageMap right = left.mirrored(DiceTray.DUEL_W);
		DieSample s1 = new DieSample();
		DieSample s2 = new DieSample();
		double[] m = new double[4];
		for (double ms = 0; ms <= DuelTimeline.T_DUEL + 50; ms += 5) {
			for (int d = 0; d < 2; d++) {
				a.sample(d, ms, s1);
				again.sample(d, ms, s2);
				assertEquals(s1.x, s2.x, 0, "deterministic");
				assertEquals(s1.zc, s2.zc, 0);
				assertEquals(360, left.x(s1.x) + right.x(s1.x), 1e-9, "x' = 360 − x");
				assertEquals(left.yLifted(s1.y, s1.zc), right.yLifted(s1.y, s1.zc), 0, "same height and depth");
				assertEquals(CubeGroup.faceUp(s1.q), CubeGroup.faceUp(Quat.mirrorXKeepUp(s1.q, m)), "the mirror keeps the top number");
			}
		}
		// the world plan solves A's pair with the same key
		DuelStagePlan plan = new DuelStagePlan(seed, List.<int[][]>of(new int[][] {faces, {1, 1}}), A, B, 64.0);
		assertEquals(4, plan.roll(0, 0).topFace(0, 1e6));
		assertEquals(2, plan.roll(0, 0).topFace(1, 1e6));
	}
}

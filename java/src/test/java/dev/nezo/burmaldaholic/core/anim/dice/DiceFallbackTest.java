package dev.nezo.burmaldaholic.core.anim.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R-DCa #1/#2: {@link DiceSolver#solve} never throws and never lets two dice penetrate, also on trays where no planned
 * throw exists (the scripted drop takes over); the scripted drop itself is exact and separated. R-DCa #3/#6: the cache
 * key covers faces, target and tray; {@code events()} is a copy.
 */
class DiceFallbackTest {
	/** No two dice closer than {@code min} (xy) while both are near the floor; faces exact; never below the floor. */
	static void checkRoll(DiceRoll r, int[] faces, int targetMs, double min, String at) {
		int n = faces.length;
		DieSample[] s = new DieSample[n];
		for (int i = 0; i < n; i++) s[i] = new DieSample();
		double end = 0;
		for (int i = 0; i < n; i++) end = Math.max(end, r.restTime(i));
		if (targetMs != DiceSolver.NATURAL) assertEquals(targetMs, end, 0.5, at + " target");
		for (double t = -20; t <= end + 20; t += 1) {
			for (int i = 0; i < n; i++) {
				r.sample(i, t, s[i]);
				assertTrue(s[i].zLow >= 0 && Double.isFinite(s[i].x) && Double.isFinite(s[i].y), at + " state t" + t);
			}
			for (int i = 0; i < n; i++)
				for (int j = 0; j < i; j++)
					if (s[i].zLow < DiceSolver.NEAR_FLOOR && s[j].zLow < DiceSolver.NEAR_FLOOR && s[i].phase != DieSample.HELD && s[j].phase != DieSample.HELD)
						assertTrue(Math.hypot(s[i].x - s[j].x, s[i].y - s[j].y) >= min - 1e-9, at + " overlap " + i + "/" + j + " t" + t);
		}
		for (int i = 0; i < n; i++) {
			r.sample(i, r.restTime(i) + 1, s[i]);
			assertEquals(DieSample.REST, s[i].phase, at + " rest phase");
			assertEquals(faces[i], CubeGroup.faceUp(s[i].q), at + " face " + i);
		}
	}

	@Test
	void scriptedDropIsExactSeparatedAndOnTime() {
		for (DiceFixtures.Case c : DiceFixtures.all()) {
			DiceSolver.Ctx ctx = new DiceSolver.Ctx(c.tray());
			for (int n = 1; n <= 4; n++)
				for (int seed = 0; seed < 6; seed++)
					for (int target : new int[] {DiceSolver.NATURAL, 500, c.targetMs()}) {
						int[] faces = new int[n];
						for (int i = 0; i < n; i++) faces[i] = 1 + (seed * 5 + i * 3) % 6;
						DiceRoll r = DiceSolver.scripted(ctx, c.spec(), seed, faces, target, 99);
						String at = c.name() + " n" + n + " s" + seed + " t" + target;
						assertTrue(r.fallback(), at);
						checkRoll(r, faces, target, DiceSolver.DIE_GAP, at);
					}
		}
	}

	/** Trays on which the planner cannot place two dice (tiny rest zone / everything kept out): solve still returns. */
	static List<DiceFixtures.Case> adversarial() {
		List<DiceFixtures.Case> l = new ArrayList<>();
		l.add(new DiceFixtures.Case("tinyZone", DiceFixtures.craps(52, 21, new DiceTray.Box(30, 6, 30.4, 6.4), List.of()), ThrowSpec.hand(0, 47.5, 18.6), 1700));
		l.add(new DiceFixtures.Case("keptOut",
			DiceFixtures.craps(52, 21, new DiceTray.Box(23, 4.6, 43.5, 8.2), List.of(new DiceTray.Box(20, 2, 47, 11))), ThrowSpec.hand(0, 47.5, 18.6), 1700));
		l.add(new DiceFixtures.Case("narrow", DiceFixtures.craps(8, 6, new DiceTray.Box(3, 2, 5, 4), List.of()), ThrowSpec.hand(1, 4, 5), 900));
		return l;
	}

	@Test
	void solveNeverThrowsNorPenetratesOnAdversarialTrays() {
		int scripted = 0;
		List<DiceFixtures.Case> cases = new ArrayList<>(adversarial());
		cases.addAll(DiceFixtures.all());
		for (DiceFixtures.Case c : cases) {
			for (int si = 0; si < 24; si++) {
				int seed = si * 0x2545F491 ^ 0xBADC0DE;
				int[] faces = {1 + si % 6, 1 + (si * 5 + 2) % 6};
				for (int target : new int[] {DiceSolver.NATURAL, c.targetMs()}) {
					DiceRoll r = DiceSolver.solve(c.tray(), c.spec(), seed, faces, target);
					String at = c.name() + " s" + si + " t" + target;
					checkRoll(r, faces, target, DiceSolver.CLACK_MIN - 0.1, at); // the solver samples contacts every 3 ms
					if (r.attempt() == DiceSolver.ATTEMPTS + 2 * DiceSolver.FALLBACK_ATTEMPTS) scripted++;
				}
			}
		}
		assertTrue(scripted > 0, "the adversarial trays reach the scripted drop");
	}

	@Test
	void cacheKeyCoversFacesTargetAndTray() {
		DiceTray a = DiceFixtures.crapsBig();
		DiceTray b = DiceFixtures.crapsCompact();
		long k = DiceRollCache.key(5, 0, 77, new int[] {3, 4}, 1700, a);
		assertEquals(k, DiceRollCache.key(5, 0, 77, new int[] {3, 4}, 1700, DiceFixtures.crapsBig()));
		assertNotEquals(k, DiceRollCache.key(5, 0, 77, new int[] {4, 3}, 1700, a));
		assertNotEquals(k, DiceRollCache.key(5, 0, 77, new int[] {3, 4}, 1701, a));
		assertNotEquals(k, DiceRollCache.key(5, 0, 77, new int[] {3, 4}, 1700, b));
		assertNotEquals(k, DiceRollCache.key(5, 1, 77, new int[] {3, 4}, 1700, a));
		DiceRoll r = DiceSolver.solve(a, ThrowSpec.hand(0, 47.5, 18.6), 77, new int[] {3, 4}, 1700);
		r.events()[0] = null;
		assertTrue(r.events()[0] != null && r.eventList().get(0) == r.events()[0]);
	}
}

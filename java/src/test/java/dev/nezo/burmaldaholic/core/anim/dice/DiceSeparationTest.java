package dev.nezo.burmaldaholic.core.anim.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CM report: two released dice never pass through each other, in the air as well as on the floor. Craps and duel trays
 (the hand spaces the dice 3.5 u, as CM's) × many seeds: the 3D centre distance is ≥ {@link DiceSolver#MIN_CENTRE} at every
 * sampled time (0.5 ms) while both dice are out of the hand; faces stay exact.
 */
class DiceSeparationTest {
	static final int SEEDS = System.getenv("DICE_SEEDS") != null ? Integer.parseInt(System.getenv("DICE_SEEDS")) : 160;

	static List<DiceFixtures.Case> cases() {
		List<DiceFixtures.Case> l = new ArrayList<>();
		for (DiceFixtures.Case c : DiceFixtures.all()) if (c.name().startsWith("craps") || c.name().startsWith("duel")) l.add(c);
		return l;
	}

	@Test
	void releasedDiceKeepTheirCentresApartTheWholeFlight() {
		DieSample a = new DieSample();
		DieSample b = new DieSample();
		int rolls = 0;
		int scripted = 0;
		double worst = Double.POSITIVE_INFINITY;
		StringBuilder bad = new StringBuilder();
		int failures = 0;
		for (DiceFixtures.Case c : cases()) {
			for (int si = 0; si < SEEDS; si++) {
				int seed = si * 7919 + 13;
				int[] faces = {1 + si % 6, 1 + (si / 6) % 6};
				for (int target : new int[] {c.targetMs(), DiceSolver.NATURAL}) {
					DiceRoll r = DiceSolver.solve(c.tray(), c.spec(), seed, faces, target);
					rolls++;
					if (r.attempt() >= DiceSolver.ATTEMPTS + 2 * DiceSolver.FALLBACK_ATTEMPTS) scripted++;
					double end = Math.max(r.restTime(0), r.restTime(1)) + 50;
					for (double t = 0; t <= end; t += 0.5) {
						r.sample(0, t, a);
						r.sample(1, t, b);
						if (a.phase == DieSample.HELD || b.phase == DieSample.HELD) continue;
						double d = Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.zc - b.zc) * (a.zc - b.zc));
						worst = Math.min(worst, d);
						if (d < DiceSolver.MIN_CENTRE - 1e-9) {
							if (failures++ < 10) bad.append(c.name()).append(" seed ").append(seed).append(" t ").append(t).append(" d ").append(d).append('\n');
							break;
						}
					}
					for (int d = 0; d < 2; d++) assertEquals(faces[d], r.topFace(d, r.restTime(d) + 1), c.name() + " seed " + seed + " face");
				}
			}
		}
		System.out.println("separation: rolls " + rolls + ", scripted " + scripted + ", worst centre distance " + worst);
		assertTrue(failures == 0, failures + " rolls pass through:\n" + bad);
	}
}

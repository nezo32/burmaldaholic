package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class GatesTest {
	@Test
	void gateFrequencyMatchesChance() {
		for (double chance : new double[] {0.2, 0.3, 0.35}) {
			int n = 200_000;
			int pass = 0;
			for (int i = 0; i < n; i++) {
				if (Gates.pass(chance, 12345L, i * 37 - 5000, i / 3 * 91, Gates.SALT_VILLAGE)) {
					pass++;
				}
			}
			assertEquals(chance, pass / (double) n, 0.005, "chance " + chance);
		}
	}

	@Test
	void gateIsDeterministicAndSaltsAreIndependent() {
		assertEquals(Gates.unit(1, 2, 3, Gates.SALT_PARLOR), Gates.unit(1, 2, 3, Gates.SALT_PARLOR));
		int both = 0;
		int n = 100_000;
		for (int i = 0; i < n; i++) {
			boolean a = Gates.pass(0.5, 99, i, -i, Gates.SALT_PARLOR);
			boolean b = Gates.pass(0.5, 99, i, -i, Gates.SALT_LOUNGE);
			if (a && b) {
				both++;
			}
		}
		assertEquals(0.25, both / (double) n, 0.01);
		assertFalse(Gates.pass(0, 1, 2, 3, 4));
		assertTrue(Gates.pass(1, 1, 2, 3, 4));
		// different seeds -> different decisions
		int differ = 0;
		for (int i = 0; i < 1000; i++) {
			if (Gates.pass(0.5, 1, i, i, 7) != Gates.pass(0.5, 2, i, i, 7)) {
				differ++;
			}
		}
		assertTrue(differ > 400);
	}

	@Test
	void unitRange() {
		SplittableRandom r = new SplittableRandom(3);
		for (int i = 0; i < 100_000; i++) {
			double u = Gates.unit(r.nextLong(), r.nextInt(), r.nextInt(), r.nextLong());
			assertTrue(u >= 0 && u < 1);
		}
	}

	@Test
	void villageWeight() {
		assertEquals(4, Gates.villageWeight(4)); // vanilla terminators pools: 4 × weight 1
		assertEquals(1, Gates.villageWeight(1));
	}

	/** The single-element insert must land where the first of W random copies would (Monte-Carlo). */
	@Test
	void insertIndexMatchesFirstOfWeightCopies() {
		int n = 20;
		int w = 10;
		int trials = 200_000;
		long[] fast = new long[n + 1];
		long[] slow = new long[n + 1];
		Random r1 = new Random(1);
		Random r2 = new Random(2);
		for (int t = 0; t < trials; t++) {
			fast[Gates.insertIndex(n, w, r1::nextInt)]++;
			List<Boolean> list = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				list.add(false);
			}
			for (int i = 0; i < w; i++) {
				list.add(true);
			}
			Collections.shuffle(list, r2);
			slow[list.indexOf(true)]++;
		}
		for (int i = 0; i <= n; i++) {
			assertEquals(slow[i] / (double) trials, fast[i] / (double) trials, 0.006, "index " + i);
		}
	}

	/**
	 * A gated village draws the terminators pool at several street ends; the casino is tried first at
	 * half of them, so with ≥ 4 street ends it is tried first somewhere with probability ≥ 15/16.
	 */
	@Test
	void gatedVillageAlmostAlwaysTriesTheCasinoFirstSomewhere() {
		int n = 4;
		int weight = Gates.villageWeight(n);
		Random r = new Random(4);
		int trials = 100_000;
		int firstAtLeastOnce = 0;
		for (int t = 0; t < trials; t++) {
			boolean first = false;
			for (int end = 0; end < 4; end++) {
				first |= Gates.insertIndex(n, weight, r::nextInt) == 0;
			}
			if (first) {
				firstAtLeastOnce++;
			}
		}
		assertEquals(15 / 16.0, firstAtLeastOnce / (double) trials, 0.01);
	}

	@Test
	void insertIndexEdgeCases() {
		assertEquals(0, Gates.insertIndex(0, 5, b -> 0));
		assertEquals(3, Gates.insertIndex(3, 1, b -> b - 1)); // never picks the copy -> goes last
		assertEquals(0, Gates.insertIndex(3, 1, b -> 0));
	}
}

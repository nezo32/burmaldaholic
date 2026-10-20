package dev.nezo.burmaldaholic.core.anim.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.SeedMix;
import org.junit.jupiter.api.Test;

/** MotionRng: deterministic, SeedMix-based (Bedrock parity), independent streams, values in range. */
class MotionRngTest {
	@Test
	void isSeedMixFxRngOfTheMixedSeed() {
		MotionRng m = MotionRng.of(12345, "PEG", 3);
		SeedMix.FxRng ref = new SeedMix.FxRng(SeedMix.mix(12345, SeedMix.hash("PEG"), 3));
		for (int i = 0; i < 100; i++) assertEquals(ref.nextU32(), m.nextU32());
		MotionRng m2 = MotionRng.of(12345, "DSIM", 1, 0);
		SeedMix.FxRng ref2 = new SeedMix.FxRng(SeedMix.mix(12345, SeedMix.hash("DSIM"), 1, 0));
		for (int i = 0; i < 100; i++) assertEquals(ref2.nextU32(), m2.nextU32());
	}

	@Test
	void deterministicAndIndependent() {
		assertEquals(MotionRng.of(1, "A", 0).nextU32(), MotionRng.of(1, "A", 0).nextU32());
		assertNotEquals(MotionRng.of(1, "A", 0).nextU32(), MotionRng.of(1, "B", 0).nextU32());
		assertNotEquals(MotionRng.of(1, "A", 0).nextU32(), MotionRng.of(1, "A", 1).nextU32());
		assertNotEquals(MotionRng.of(1, "A", 0).nextU32(), MotionRng.of(2, "A", 0).nextU32());
	}

	@Test
	void rangesHold() {
		MotionRng r = MotionRng.of(99, "T", 0);
		int[] hist = new int[3];
		int[] ints = new int[4];
		for (int i = 0; i < 40_000; i++) {
			double d = r.range(-1.5, 1.5);
			assertTrue(d >= -1.5 && d < 1.5);
			int k = r.rangeInt(5, 8);
			assertTrue(k >= 5 && k <= 8);
			ints[k - 5]++;
			int s = r.sign();
			assertTrue(s == 1 || s == -1);
			double j = r.jitter(220, 0.15);
			assertTrue(j >= 187 - 1e-9 && j <= 253 + 1e-9);
			hist[r.weighted(1, 2, 1)]++;
		}
		for (int c : ints) assertTrue(c > 9000, "rangeInt inclusive and uniform");
		assertTrue(Math.abs(hist[1] / 40_000.0 - 0.5) < 0.02, "weighted 1:2:1");
		assertTrue(Math.abs(hist[0] / 40_000.0 - 0.25) < 0.02);
	}
}

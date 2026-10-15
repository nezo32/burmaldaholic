package dev.nezo.burmaldaholic.core.anim.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** DetMath: accurate (vs StrictMath), and allocation-free where StrictMath's argument reduction allocates. */
class DetMathTest {
	@Test
	void matchesStrictMath() {
		SplittableRandom r = new SplittableRandom(3);
		for (int i = 0; i < 200_000; i++) {
			double x = i % 4 == 0 ? r.nextDouble(-7, 7) : r.nextDouble(-50_000, 50_000);
			assertEquals(StrictMath.sin(x), DetMath.sin(x), 1e-15 * Math.max(1, Math.abs(x) / 1000), "sin " + x);
			assertEquals(StrictMath.cos(x), DetMath.cos(x), 1e-15 * Math.max(1, Math.abs(x) / 1000), "cos " + x);
		}
		assertEquals(0, DetMath.sin(0), 0);
		assertEquals(1, DetMath.cos(0), 0);
		assertEquals(StrictMath.sin(3e6), DetMath.sin(3e6), 0, "fallback beyond the exact range");
		assertTrue(Double.isNaN(DetMath.sin(Double.NaN)));
	}

	@Test
	void allocatesNothing() {
		double sink = 0;
		for (int i = 0; i < 200_000; i++) sink += DetMath.sin(i * 0.37) + DetMath.cos(i * 0.91) + StrictMath.exp(-i * 1e-5);
		com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		long tid = Thread.currentThread().threadId();
		long before = mx.getThreadAllocatedBytes(tid);
		for (int i = 0; i < 10_000; i++) sink += DetMath.sin(i * 1.7 - 900) + DetMath.cos(i * 2.3) + StrictMath.exp(-i * 1e-4) + StrictMath.expm1(-i * 1e-4);
		long after = mx.getThreadAllocatedBytes(tid);
		assertTrue(Double.isFinite(sink));
		assertEquals(0, after - before, "bytes allocated");
	}
}

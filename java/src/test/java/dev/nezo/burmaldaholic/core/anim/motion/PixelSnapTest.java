package dev.nezo.burmaldaholic.core.anim.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** PHASE2B §4 MK PixelSnapTest: k 1..12. */
class PixelSnapTest {
	@Test
	void snapsToPhysicalPixels() {
		SplittableRandom r = new SplittableRandom(7);
		for (int k = 1; k <= 12; k++) {
			double prev = Double.NEGATIVE_INFINITY;
			double prevX = Double.NEGATIVE_INFINITY;
			for (int i = 0; i < 20_000; i++) {
				double x = r.nextDouble(-2000, 2000);
				double s = PixelSnap.snap(x, k);
				assertEquals(Math.rint(s * k), s * k, 1e-9, "on the 1/k grid");
				assertTrue(Math.abs(s - x) <= 0.5 / k + 1e-12, "nearest");
				assertEquals(s, PixelSnap.snap(s, k), 0, "idempotent");
				int m = r.nextInt(-50, 50);
				assertEquals(s + (double) m / k, PixelSnap.snap(x + (double) m / k, k), 1e-9, "translation by whole physical px");
				double f = PixelSnap.floor(x, k);
				assertTrue(f <= x + 1e-12 && x - f < 1.0 / k + 1e-12, "floor");
				assertEquals(PixelSnap.phys(x, k), Math.round(s * k), "phys index");
			}
			// monotone along a sorted sweep
			for (double x = -50; x < 50; x += 0.0137) {
				double s = PixelSnap.snap(x, k);
				assertTrue(s >= prev, "monotone");
				assertTrue(x > prevX);
				prev = s;
				prevX = x;
			}
		}
	}

	@Test
	void subGuiPixelMotion() {
		// a ball moving 1/8 art px per frame at n = 8 phys px per art px (k = 9, s = 8/9) moves 1 physical px per frame
		for (int k = 1; k <= 12; k++) {
			for (int n = 1; n <= 12; n++) {
				double scale = (double) n / k;
				long last = PixelSnap.phys(PixelSnap.at(3, 0, scale, k), k);
				for (int i = 1; i <= 64; i++) {
					long p = PixelSnap.phys(PixelSnap.at(3, i / (double) n, scale, k), k);
					assertEquals(1, p - last, "k" + k + " n" + n + " step " + i);
					last = p;
				}
			}
		}
	}

	@Test
	void scaledSpritesKeepTexelEdgesOnPhysicalPixels() {
		// T-MK D2: SnapDraw's scale overloads snap first, then scale by s = n/k (Plinko 8/9, dice 8/9 or 5/2, wheel ×2)
		SplittableRandom r = new SplittableRandom(11);
		for (int k = 1; k <= 12; k++) {
			for (int n = 1; n <= 12; n++) {
				double s = (double) n / k;
				for (int t = 0; t < 200; t++) {
					double x = r.nextDouble(-500, 500);
					long left = PixelSnap.phys(x, k);
					for (int i = 0; i <= 16; i++) {
						double e = PixelSnap.texelEdge(x, i, s, k);
						assertEquals(Math.rint(e), e, 1e-6, "k" + k + " n" + n + " edge " + i);
						assertEquals(left + (long) i * n, Math.round(e), "every texel exactly n physical px");
					}
				}
			}
		}
	}

	@Test
	void floatFormMatches() {
		for (int k = 1; k <= 12; k++) {
			assertEquals((float) PixelSnap.snap(12.3456, k), PixelSnap.snapF(12.3456, k));
		}
	}
}

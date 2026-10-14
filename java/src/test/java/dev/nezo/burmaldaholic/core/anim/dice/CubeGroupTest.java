package dev.nezo.burmaldaholic.core.anim.dice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Quat maths and the 24-element cube group (PHASE2B §3.3, DCa-A). */
class CubeGroupTest {
	private static final double EPS = 1e-12;

	static double[] randomQ(Random r) {
		double u1 = r.nextDouble();
		double u2 = r.nextDouble();
		double u3 = r.nextDouble();
		return new double[] {Math.sqrt(1 - u1) * Math.sin(2 * Math.PI * u2), Math.sqrt(1 - u1) * Math.cos(2 * Math.PI * u2),
			Math.sqrt(u1) * Math.sin(2 * Math.PI * u3), Math.sqrt(u1) * Math.cos(2 * Math.PI * u3)};
	}

	@Test
	void quatBasics() {
		double[] a = Quat.axisAngle(0, 0, 1, Math.PI / 2, new double[4]);
		double[] v = Quat.rotate(a, new double[] {1, 0, 0}, new double[3]);
		assertArrayEquals(new double[] {0, 1, 0}, v, EPS);
		double[] b = Quat.axisAngle(1, 0, 0, Math.PI / 2, new double[4]);
		// mul(b, a): first a (x→y), then b (y→z)
		double[] ab = Quat.mul(b, a, new double[4]);
		assertArrayEquals(new double[] {0, 0, 1}, Quat.rotate(ab, new double[] {1, 0, 0}, new double[3]), EPS);
		assertEquals(Math.PI / 2, Quat.angleTo(Quat.identity(), a), 1e-12);
		assertEquals(Math.PI / 4, Quat.arcTo(Quat.identity(), a), 1e-12);
		double[] half = Quat.slerp(Quat.identity(), a, 0.5, new double[4]);
		assertEquals(Math.PI / 4, Quat.angleTo(Quat.identity(), half), 1e-12);
		// slerp takes the short way when the target has the other sign
		double[] neg = {-a[0], -a[1], -a[2], -a[3]};
		assertEquals(Math.PI / 4, Quat.angleTo(Quat.identity(), Quat.slerp(Quat.identity(), neg, 0.5, new double[4])), 1e-12);
		double[] e = Quat.expMap(0, 0, 3, 0.5, new double[4]);
		assertEquals(1.5, Quat.angle(e), 1e-12);
		// conj is the inverse
		double[] r = Quat.mul(a, Quat.conj(a, new double[4]), new double[4]);
		assertEquals(1, Math.abs(r[0]), EPS);
		// aliasing is allowed
		double[] c = a.clone();
		Quat.mul(c, c, c);
		assertEquals(Math.PI, Quat.angle(c), 1e-12);
	}

	@Test
	void mirrorAndMcFrameAreConjugations() {
		Random r = new Random(3);
		for (int i = 0; i < 500; i++) {
			double[] q = randomQ(r);
			double[] v = {r.nextGaussian(), r.nextGaussian(), r.nextGaussian()};
			double[] rv = Quat.rotate(q, v, new double[3]);
			// mirror x → −x
			double[] m = Quat.mirrorX(q, new double[4]);
			double[] mv = Quat.rotate(m, new double[] {-v[0], v[1], v[2]}, new double[3]);
			assertArrayEquals(new double[] {-rv[0], rv[1], rv[2]}, mv, 1e-12);
			double[] keep = Quat.mirrorXKeepUp(q, new double[4]);
			assertEquals(CubeGroup.faceUp(q), CubeGroup.faceUp(keep));
			double[] turned = Quat.mul(m, new double[] {0, 0, 1, 0}, new double[4]);
			assertTrue(Math.abs(Quat.dot(keep, m)) > 1 - 1e-12 || Math.abs(Quat.dot(keep, turned)) > 1 - 1e-12);
			// tray (x, y, z) → MC (x, z, y)
			double[] mc = Quat.trayToMc(q, new double[4]);
			double[] cv = Quat.rotate(mc, new double[] {v[0], v[2], v[1]}, new double[3]);
			assertArrayEquals(new double[] {rv[0], rv[2], rv[1]}, cv, 1e-12);
		}
	}

	@Test
	void supportHeights() {
		assertEquals(1, Quat.support(Quat.identity()), EPS);
		assertEquals(Math.sqrt(2), Quat.support(Quat.axisAngle(1, 0, 0, Math.PI / 4, new double[4])), 1e-12);
		// corner down: rotate so that (1,1,1)/√3 points up
		double[] q = Quat.axisAngle(1, -1, 0, Math.acos(1 / Math.sqrt(3)), new double[4]);
		assertEquals(Math.sqrt(3), Quat.support(q), 1e-12);
		assertEquals(1, Quat.supportAlong(Quat.identity(), 1, 0), EPS);
		assertEquals(Math.sqrt(2), Quat.supportAlong(Quat.axisAngle(0, 0, 1, Math.PI / 4, new double[4]), 1, 0), 1e-12);
	}

	@Test
	void twentyFourDistinctUnitElements() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < CubeGroup.SIZE; i++) {
			double[] e = CubeGroup.element(i);
			assertEquals(1, Quat.norm(e), EPS, "unit " + i);
			for (double c : e) {
				double a = Math.abs(c);
				assertTrue(a == 0 || a == 0.5 || a == Math.sqrt(0.5) || a == 1, "exact component " + c);
			}
			seen.add(String.format("%.6f,%.6f,%.6f,%.6f", e[0], e[1], e[2], e[3]));
			assertEquals(i, CubeGroup.nearest(e));
			assertEquals(i / 4 + 1, CubeGroup.faceUp(e), "faceUp " + i);
			assertTrue(CubeGroup.isElement(e, EPS));
		}
		assertEquals(24, seen.size());
		assertArrayEquals(new double[] {1, 0, 0, 0}, CubeGroup.element(0), 0);
	}

	@Test
	void closedUnderComposition() {
		for (int a = 0; a < 24; a++)
			for (int b = 0; b < 24; b++) {
				double[] p = Quat.mul(CubeGroup.element(a), CubeGroup.element(b), new double[4]);
				assertTrue(CubeGroup.isElement(p, 1e-12), a + "·" + b);
			}
	}

	@Test
	void axesMapToAxes() {
		for (int i = 0; i < 24; i++) {
			double[] e = CubeGroup.element(i);
			for (int f = 1; f <= 6; f++) {
				int[] n = CubeGroup.normal(f);
				double[] v = Quat.rotate(e, new double[] {n[0], n[1], n[2]}, new double[3]);
				for (double c : v) assertTrue(Math.abs(c) < 1e-12 || Math.abs(Math.abs(c) - 1) < 1e-12);
			}
		}
	}

	@Test
	void canonicalLabelsSumToSevenAndMatchDiceFaces() {
		for (int f = 1; f <= 6; f++) {
			int[] n = CubeGroup.normal(f);
			int[] m = CubeGroup.normal(7 - f);
			assertArrayEquals(new int[] {-n[0], -n[1], -n[2]}, m);
			// DiceFaces.modelNormal is in MC (x, y up, z south): tray (x, y south, z up) → MC (x, z, y)
			int[] mc = DiceFaces.modelNormal(f);
			assertArrayEquals(new int[] {mc[0], mc[2], mc[1]}, n, "face " + f);
		}
	}

	@Test
	void faceUpOfArbitraryOrientations() {
		Random r = new Random(5);
		for (int i = 0; i < 2000; i++) {
			double[] q = randomQ(r);
			int up = CubeGroup.faceUp(q);
			int[] n = CubeGroup.normal(up);
			double[] v = Quat.rotate(q, new double[] {n[0], n[1], n[2]}, new double[3]);
			for (int f = 1; f <= 6; f++) {
				int[] m = CubeGroup.normal(f);
				double[] w = Quat.rotate(q, new double[] {m[0], m[1], m[2]}, new double[3]);
				assertTrue(v[2] >= w[2] - 1e-12);
			}
			// nearest is the element with the largest |dot|
			int k = CubeGroup.nearest(q);
			for (int j = 0; j < 24; j++) assertTrue(Math.abs(Quat.dot(q, CubeGroup.element(k))) >= Math.abs(Quat.dot(q, CubeGroup.element(j))) - 1e-15);
		}
	}

	@Test
	void groupCoveringRadiusIsAbout63Degrees() {
		Random r = new Random(9);
		double worst = 0;
		for (int i = 0; i < 20000; i++) {
			double[] q = randomQ(r);
			worst = Math.max(worst, Math.toDegrees(Quat.angleTo(q, CubeGroup.element(CubeGroup.nearest(q)))));
		}
		assertTrue(worst < 62.81 && worst > 55, "covering radius " + worst);
	}

	@Test
	void relabelPutsTheWantedFaceUp() {
		Random r = new Random(1);
		for (int i = 0; i < 24; i++) {
			int bodyUp = CubeGroup.faceUp(CubeGroup.element(i));
			for (int want = 1; want <= 6; want++) {
				Set<Integer> ls = new HashSet<>();
				for (int k = 0; k < 4; k++) {
					int l = CubeGroup.relabel(bodyUp, want, k);
					ls.add(l);
					double[] m = Quat.mul(CubeGroup.element(i), CubeGroup.element(l), new double[4]);
					assertEquals(want, CubeGroup.faceUp(m));
					assertTrue(CubeGroup.isElement(m, 1e-12));
					// a slightly perturbed rest pose relabels the same way
					double[] p = Quat.mul(Quat.axisAngle(r.nextDouble(), r.nextDouble(), r.nextDouble(), 0.2, new double[4]), CubeGroup.element(i), new double[4]);
					assertEquals(want, CubeGroup.faceUp(Quat.mul(p, CubeGroup.element(l), new double[4])));
				}
				assertEquals(4, ls.size(), "four spins about the wanted face");
			}
		}
		assertFalse(CubeGroup.isElement(Quat.axisAngle(0, 0, 1, 0.1, new double[4]), 1e-6));
	}

	@Test
	void diceFacesIsAViewOnTheGroup() {
		for (int top = 1; top <= 6; top++) {
			int i = DiceFaces.groupIndex(top);
			assertEquals(top, CubeGroup.faceUp(CubeGroup.element(i)));
			int[] layout = DiceFaces.layout(top);
			int[] n = CubeGroup.normal(layout[DiceFaces.NORTH]);
			double[] v = Quat.rotate(CubeGroup.element(i), new double[] {n[0], n[1], n[2]}, new double[3]);
			assertEquals(-1, v[1], 1e-12, "north of " + top);
		}
	}

	@Test
	void indexAndCompose() {
		assertEquals(0, CubeGroup.index(1, 0));
		assertEquals(23, CubeGroup.index(6, 3));
		assertEquals(CubeGroup.index(1, 1), CubeGroup.index(1, 5));
		for (int a = 0; a < 24; a++) assertEquals(a, CubeGroup.compose(a, 0));
		// quarter turn about z four times is the identity
		int q = CubeGroup.index(1, 1);
		assertEquals(0, CubeGroup.compose(q, CubeGroup.compose(q, CubeGroup.compose(q, q))));
	}
}

package dev.nezo.burmaldaholic.core.anim.motion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;

/** PHASE2B §4 MK TrackTest: C0 continuity, pure in t over 4.2/8.3/16.7/33/500 ms steps, 0 allocation over 10k samples. */
class TrackTest {
	private static final double[] STEPS = {4.2, 8.3, 16.7, 33, 500};

	/** A track using every segment kind on 3 channels (x, z, angle). */
	static Track everyKind() {
		Track.Builder b = Track.builder().start(100, 0, 0, 0);
		b.hold(50);
		b.linearTo(120, 10, 0, 30);
		b.ballisticTo(200, new double[] {40, 0, 60}, new double[] {0, -900, 0});
		b.seg(300).accel(0, 120, -200).ballistic(1, 90, -900).expDecay(2, 700, 36, 400).done();
		b.seg(250).osc(0, 3, 40, 90, 0).linear(1, -5).expDecay(2, b.velocity(2), 0, 1000).done();
		// a die tipping over its leading edge: centre (u, z) about the pivot (u + 0.5, z − 0.5)
		double u = b.value(0);
		double z = b.value(1);
		b.seg(120).pivotBy(0, 1, u + 0.5, z - 0.5, -Math.PI / 2, 0).done();
		b.seg(80).accelToStop(0, b.value(0) + 1, 0).done();
		return b.build();
	}

	@Test
	void c0ContinuousAtEverySegmentBoundary() {
		Track tr = everyKind();
		double[] a = new double[3];
		double[] c = new double[3];
		for (int s = 0; s + 1 < tr.segments(); s++) {
			double t = tr.segEnd(s);
			sampleSeg(tr, s, t, a);
			tr.sample(t, c);
			for (int ch = 0; ch < 3; ch++) assertEquals(a[ch], c[ch], 1e-9, "segment " + s + " ch " + ch);
		}
		// fine sampling: no jump anywhere
		double[] prev = new double[3];
		tr.sample(tr.start(), prev);
		double[] v = new double[3];
		for (double t = tr.start(); t <= tr.end() + 5; t += 0.25) {
			tr.sample(t, c);
			tr.velocity(t, v);
			for (int ch = 0; ch < 3; ch++) {
				double bound = 0.25 / 1000 * (Math.abs(v[ch]) + 2000) + 1e-9;
				assertTrue(Math.abs(c[ch] - prev[ch]) <= bound, "jump at " + t + " ch " + ch);
			}
			System.arraycopy(c, 0, prev, 0, 3);
		}
	}

	/** Evaluates segment s at time t from its own closed form (left limit at a boundary). */
	private static void sampleSeg(Track tr, int s, double t, double[] out) {
		// sampling just before the boundary converges to the left limit; closed forms are smooth inside a segment
		double eps = 1e-7;
		double[] x = new double[3];
		tr.sample(t - eps, x);
		double[] v = new double[3];
		tr.velocity(t - eps, v);
		for (int ch = 0; ch < 3; ch++) out[ch] = x[ch] + v[ch] * eps / 1000;
	}

	@Test
	void endpointsAndClosedForms() {
		Track tr = everyKind();
		double[] o = new double[3];
		tr.sample(0, o);
		assertArrayEquals(new double[] {0, 0, 0}, o, 0, "holds the first value before the start");
		tr.sample(tr.segEnd(2), o);
		assertEquals(40, o[0], 1e-9, "ballistic lands exactly");
		assertEquals(0, o[1], 1e-9);
		assertEquals(60, o[2], 1e-9);
		// velocity is the derivative of the value (numeric check inside every segment)
		double[] p = new double[3];
		double[] q = new double[3];
		double[] v = new double[3];
		for (int s = 0; s < tr.segments(); s++) {
			double t = 0.5 * (tr.segStart(s) + tr.segEnd(s));
			tr.sample(t - 0.001, p);
			tr.sample(t + 0.001, q);
			tr.velocity(t, v);
			for (int ch = 0; ch < 3; ch++) assertEquals((q[ch] - p[ch]) / 0.000002, v[ch], 1e-3 * Math.max(1, Math.abs(v[ch])), "rate s" + s + " ch" + ch);
		}
		// pivot: a quarter tip, the centre rises to √2/2 (pivot radius) at 45° and lands one edge further
		int pivot = 5;
		assertEquals(Track.PIVOT, tr.kind(pivot, 0));
		double t0 = tr.segStart(pivot);
		double t1 = tr.segEnd(pivot);
		double[] a = new double[3];
		double[] b = new double[3];
		tr.sample(t0, a);
		tr.sample(t1, b);
		assertEquals(a[0] + 1, b[0], 1e-9, "one edge length");
		assertEquals(a[1], b[1], 1e-9, "same height after the tip");
		double maxRise = 0;
		for (double t = t0; t <= t1; t += 0.05) {
			tr.sample(t, o);
			maxRise = Math.max(maxRise, o[1] - (a[1] - 0.5));
		}
		assertEquals(Math.sqrt(0.5), maxRise, 1e-6, "pivot centre height √2/2");
		tr.sample(1e9, o);
		assertEquals(tr.last(0), o[0], 0);
	}

	@Test
	void pureInTimeWhateverTheStepHistory() {
		Track tr = everyKind();
		double[] ref = new double[3];
		double[] o = new double[3];
		for (double step : STEPS) {
			for (double t = tr.start() - 20; t < tr.end() + 20; t += step) {
				tr.sample(t, o);
				tr.sample(t - step * 0.37, ref); // unrelated sample in between
				tr.sample(t, ref);
				assertArrayEquals(ref, o, 0, "t " + t);
				for (int ch = 0; ch < 3; ch++) assertEquals(tr.sample(t, ch), o[ch], 0);
			}
		}
	}

	@Test
	void sampleAllocatesNothing() {
		Track tr = everyKind();
		double[] o = new double[3];
		double[] v = new double[3];
		double sink = 0;
		for (int i = 0; i < 200_000; i++) { // warm up (JIT)
			tr.sample(tr.start() + (i % 1300), o);
			tr.velocity(tr.start() + (i % 1300), v);
			sink += o[0] + v[1];
		}
		com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		long tid = Thread.currentThread().threadId();
		long before = mx.getThreadAllocatedBytes(tid);
		for (int i = 0; i < 10_000; i++) {
			double t = tr.start() + i * 0.137;
			tr.sample(t, o);
			tr.velocity(t, v);
			sink += o[0] + v[1] + tr.sample(t, 2);
		}
		long after = mx.getThreadAllocatedBytes(tid);
		assertTrue(Double.isFinite(sink));
		assertEquals(0, after - before, "bytes allocated by 10k samples");
	}

	@Test
	void builderGuards() {
		assertThrows(IllegalStateException.class, () -> Track.builder().hold(10));
		Track.Builder b = Track.builder().start(0, 1);
		assertThrows(IllegalArgumentException.class, () -> b.seg(0));
		b.seg(10);
		assertThrows(IllegalStateException.class, b::build);
		Track empty = Track.builder().start(5, 7, 8).build();
		double[] o = new double[2];
		empty.sample(100, o);
		assertArrayEquals(new double[] {7, 8}, o, 0);
		assertEquals(-1, empty.segmentAt(5));
	}
}

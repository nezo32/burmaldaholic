package dev.nezo.burmaldaholic.core.anim.motion;

/**
 * Shutter-sampled trail of a moving thing (RO §3.5, PL §3.3): positions of the same object at earlier times inside
 * the frame's "shutter", spaced so that consecutive dots are at most {@code maxGapPx} apart. Draw them as shrinking,
 * fading dots behind the object: motion blur, not ghost copies. Works with any pure sampler.
 *
 * <p>The shutter is {@code clamp(frameDt, minShutterMs, maxShutterMs)}. No trail is produced when the arc covered in
 * the shutter is shorter than {@code minArcPx} (the object is slow: the streak vanishes during hops). Allocation-free
 * after construction (scratch arrays); one instance per renderer, not thread-safe.
 */
public final class Streak {
	/** Writes the object's position at {@code ms} into {@code xy[0..1]} (viewer px). Must be pure in {@code ms}. */
	@FunctionalInterface
	public interface Sampler {
		void at(double ms, double[] xy);
	}

	private final int maxSamples;
	private final double minShutterMs;
	private final double maxShutterMs;
	private final double minArcPx;
	private final double[] a = new double[2];
	private final double[] b = new double[2];

	/** RO §3.5 defaults: ≤ 8 samples, shutter 8–33 ms, no streak below a 3 px arc. */
	public Streak() {
		this(8, 8, 33, 3);
	}

	/**
	 * @param maxSamples   most trail samples (≥ 1)
	 * @param minShutterMs shortest shutter (ms)
	 * @param maxShutterMs longest shutter (ms)
	 * @param minArcPx     no streak below this arc length (px)
	 */
	public Streak(int maxSamples, double minShutterMs, double maxShutterMs, double minArcPx) {
		if (maxSamples < 1 || !(minShutterMs > 0) || maxShutterMs < minShutterMs) throw new IllegalArgumentException("streak");
		this.maxSamples = maxSamples;
		this.minShutterMs = minShutterMs;
		this.maxShutterMs = maxShutterMs;
		this.minArcPx = minArcPx;
	}

	/**
	 * Trail positions behind the object at time {@code t}.
	 *
	 * @param s        position sampler
	 * @param t        current time (ms)
	 * @param frameDt  duration of the last frame (ms)
	 * @param maxGapPx largest spacing between consecutive dots (px), e.g. 1.5
	 * @param outXY    receives x0, y0, x1, y1, … (newest first: sample i at {@code t − i·shutter/count}, i = 1..count)
	 * @return the number of samples written (0 = no streak)
	 */
	public int samples(Sampler s, double t, double frameDt, double maxGapPx, double[] outXY) {
		int cap = Math.min(maxSamples, outXY.length / 2);
		if (cap <= 0 || !(maxGapPx > 0)) return 0;
		double shutter = Math.max(minShutterMs, Math.min(maxShutterMs, frameDt));
		double arc = arc(s, t, shutter);
		if (arc < minArcPx) return 0;
		int count = (int) Math.min(cap, Math.max(1, Math.ceil(arc / maxGapPx)));
		for (int i = 1; i <= count; i++) {
			s.at(t - i * shutter / count, a);
			outXY[2 * (i - 1)] = a[0];
			outXY[2 * (i - 1) + 1] = a[1];
		}
		return count;
	}

	/** Arc length (px) covered during {@code [t − shutterMs, t]}, estimated with {@code maxSamples} chords. */
	public double arc(Sampler s, double t, double shutterMs) {
		s.at(t, a);
		double len = 0;
		for (int i = 1; i <= maxSamples; i++) {
			s.at(t - i * shutterMs / maxSamples, b);
			double dx = b[0] - a[0];
			double dy = b[1] - a[1];
			len += Math.sqrt(dx * dx + dy * dy);
			a[0] = b[0];
			a[1] = b[1];
		}
		return len;
	}

	/** Linear fade from {@code a0} (newest, i = 0) to {@code a1} (oldest, i = count − 1). */
	public static double alpha(int i, int count, double a0, double a1) {
		if (count <= 1) return a0;
		return a0 + (a1 - a0) * i / (count - 1.0);
	}
}

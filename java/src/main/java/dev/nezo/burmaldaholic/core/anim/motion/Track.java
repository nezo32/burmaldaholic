package dev.nezo.burmaldaholic.core.anim.motion;

import java.util.Arrays;

/**
 * Keyframed multi-channel motion with closed-form segments (PHASE2B §3.2; RO §8, PL §2.3). A track has
 * {@link #channels()} scalar channels (e.g. x, y, roll; or angle, radius, z) and a list of contiguous segments; in
 * every segment each channel follows its own closed form:
 *
 * <table>
 *   <caption>Segment kinds (τ = seconds since the segment start)</caption>
 *   <tr><th>kind</th><th>value</th></tr>
 *   <tr><td>{@link #HOLD}</td><td>{@code p0}</td></tr>
 *   <tr><td>{@link #LINEAR}</td><td>{@code p0 + v·τ}</td></tr>
 *   <tr><td>{@link #CONST_ACCEL}</td><td>{@code p0 + v0·τ + ½·a·τ²}</td></tr>
 *   <tr><td>{@link #BALLISTIC}</td><td>as CONST_ACCEL, built from its end point and gravity (exact landing)</td></tr>
 *   <tr><td>{@link #EXP_DECAY}</td><td>{@code p0 + v∞·τ + (v0 − v∞)·τc·(1 − e^(−τ/τc))}</td></tr>
 *   <tr><td>{@link #DAMPED_OSC}</td><td>{@code c + A·e^(−τ/τd)·cos(2π·τ/P + φ)}</td></tr>
 *   <tr><td>{@link #PIVOT}</td><td>{@code c + ρ·cos|sin(φ0 + ω0·τ + ½·α·τ²)}: two channels rotating about a pivot
 *       (a die tipping over an edge: the centre rises to √2/2)</td></tr>
 * </table>
 *
 * <p>The {@link Builder} starts every segment at the previous end value, so a track is C0-continuous by construction.
 * {@link #sample} binary-searches the segment and evaluates the closed form: O(log n), allocation-free, and a pure
 * function of time (frame-rate independent; identical on the server and every client). Before {@link #start()} the
 * track holds its first value, after {@link #end()} its last value.
 *
 * <p>Time in ms; rates (velocity, acceleration, angular speed) per SECOND; decay constants and periods in ms.
 */
public final class Track {
	public static final int HOLD = 0;
	public static final int LINEAR = 1;
	public static final int CONST_ACCEL = 2;
	public static final int BALLISTIC = 3;
	public static final int EXP_DECAY = 4;
	public static final int DAMPED_OSC = 5;
	public static final int PIVOT = 6;

	/** Parameters per (segment, channel). */
	static final int STRIDE = 6;
	private static final double TWO_PI = 2 * Math.PI;

	private final int channels;
	/** Segment boundaries: segment i spans [t[i], t[i+1]); length = segments + 1. */
	private final double[] t;
	private final int[] kind;
	private final double[] prm;
	private final double[] first;
	private final double[] last;

	private Track(int channels, double[] t, int[] kind, double[] prm, double[] first, double[] last) {
		this.channels = channels;
		this.t = t;
		this.kind = kind;
		this.prm = prm;
		this.first = first;
		this.last = last;
	}

	/** A new builder; call {@link Builder#start} first. */
	public static Builder builder() {
		return new Builder();
	}

	/** Number of channels. */
	public int channels() {
		return channels;
	}

	/** Number of segments. */
	public int segments() {
		return t.length - 1;
	}

	/** Start time (ms). */
	public double start() {
		return t[0];
	}

	/** End time (ms). */
	public double end() {
		return t[t.length - 1];
	}

	/** Start time of segment {@code i}. */
	public double segStart(int i) {
		return t[i];
	}

	/** End time of segment {@code i}. */
	public double segEnd(int i) {
		return t[i + 1];
	}

	/** Kind of channel {@code ch} in segment {@code seg}. */
	public int kind(int seg, int ch) {
		return kind[seg * channels + ch];
	}

	/**
	 * Index of the segment containing {@code ms}: the last segment whose start ≤ ms (clamped to [0, segments−1]);
	 * −1 for a track without segments.
	 */
	public int segmentAt(double ms) {
		int n = t.length - 1;
		if (n <= 0) return -1;
		int lo = 0;
		int hi = n - 1;
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (t[mid] <= ms) lo = mid;
			else hi = mid - 1;
		}
		return lo;
	}

	/** Writes every channel's value at {@code ms} into {@code out} (length ≥ channels). Allocation-free. */
	public void sample(double ms, double[] out) {
		int seg = segmentAt(ms);
		if (seg < 0) {
			System.arraycopy(last, 0, out, 0, channels);
			return;
		}
		double tau = tauOf(seg, ms);
		int base = seg * channels;
		for (int c = 0; c < channels; c++) out[c] = value(kind[base + c], prm, (base + c) * STRIDE, tau);
	}

	/** Value of channel {@code ch} at {@code ms}. */
	public double sample(double ms, int ch) {
		int seg = segmentAt(ms);
		if (seg < 0) return last[ch];
		int i = seg * channels + ch;
		return value(kind[i], prm, i * STRIDE, tauOf(seg, ms));
	}

	/** Writes every channel's rate (units per second) at {@code ms}; 0 before the start and after the end. */
	public void velocity(double ms, double[] out) {
		int seg = segmentAt(ms);
		if (seg < 0 || ms < t[0] || ms > t[t.length - 1]) {
			Arrays.fill(out, 0, channels, 0);
			return;
		}
		double tau = tauOf(seg, ms);
		int base = seg * channels;
		for (int c = 0; c < channels; c++) out[c] = rate(kind[base + c], prm, (base + c) * STRIDE, tau);
	}

	/** Rate of channel {@code ch} at {@code ms} (units per second). */
	public double velocity(double ms, int ch) {
		int seg = segmentAt(ms);
		if (seg < 0 || ms < t[0] || ms > t[t.length - 1]) return 0;
		int i = seg * channels + ch;
		return rate(kind[i], prm, i * STRIDE, tauOf(seg, ms));
	}

	/** First value of channel {@code ch}. */
	public double first(int ch) {
		return first[ch];
	}

	/** Last value of channel {@code ch}. */
	public double last(int ch) {
		return last[ch];
	}

	/** Seconds into segment {@code seg}, clamped to the segment. */
	private double tauOf(int seg, double ms) {
		double c = Math.max(t[seg], Math.min(t[seg + 1], ms));
		return (c - t[seg]) / 1000.0;
	}

	static double value(int kind, double[] p, int o, double tau) {
		switch (kind) {
			case HOLD:
				return p[o];
			case LINEAR:
				return p[o] + p[o + 1] * tau;
			case CONST_ACCEL:
			case BALLISTIC:
				return p[o] + p[o + 1] * tau + 0.5 * p[o + 2] * tau * tau;
			case EXP_DECAY: {
				double tc = p[o + 3];
				return p[o] + p[o + 2] * tau + (p[o + 1] - p[o + 2]) * tc * -Math.expm1(-tau / tc);
			}
			case DAMPED_OSC: {
				double env = p[o + 2] > 0 ? Math.exp(-tau / p[o + 2]) : 1;
				return p[o] + p[o + 1] * env * Math.cos(TWO_PI * tau / p[o + 3] + p[o + 4]);
			}
			case PIVOT: {
				double a = p[o + 2] + p[o + 3] * tau + 0.5 * p[o + 4] * tau * tau;
				return p[o] + p[o + 1] * (p[o + 5] != 0 ? Math.sin(a) : Math.cos(a));
			}
			default:
				throw new IllegalStateException("kind " + kind);
		}
	}

	static double rate(int kind, double[] p, int o, double tau) {
		switch (kind) {
			case HOLD:
				return 0;
			case LINEAR:
				return p[o + 1];
			case CONST_ACCEL:
			case BALLISTIC:
				return p[o + 1] + p[o + 2] * tau;
			case EXP_DECAY:
				return p[o + 2] + (p[o + 1] - p[o + 2]) * Math.exp(-tau / p[o + 3]);
			case DAMPED_OSC: {
				double td = p[o + 2];
				double env = td > 0 ? Math.exp(-tau / td) : 1;
				double w = TWO_PI / p[o + 3];
				double ph = w * tau + p[o + 4];
				double dEnv = td > 0 ? -env / td : 0;
				return p[o + 1] * (dEnv * Math.cos(ph) - env * w * Math.sin(ph));
			}
			case PIVOT: {
				double a = p[o + 2] + p[o + 3] * tau + 0.5 * p[o + 4] * tau * tau;
				double da = p[o + 3] + p[o + 4] * tau;
				return p[o + 1] * da * (p[o + 5] != 0 ? Math.cos(a) : -Math.sin(a));
			}
			default:
				throw new IllegalStateException("kind " + kind);
		}
	}

	/**
	 * Builds a {@link Track} segment by segment. Each segment starts at the current end value of every channel, so the
	 * result is C0-continuous. Not reusable after {@link #build()}.
	 */
	public static final class Builder {
		private int channels = -1;
		private double now;
		private double[] cur;
		private double[] vel;
		private double[] first;
		private double[] times = new double[17];
		private int[] kinds = new int[16];
		private double[] params = new double[16 * STRIDE];
		private int segs;
		private Seg open;

		private Builder() {}

		/** Starts the track at time {@code ms} with the initial value of every channel (defines the channel count). */
		public Builder start(double ms, double... p0) {
			if (channels >= 0) throw new IllegalStateException("already started");
			if (p0.length == 0) throw new IllegalArgumentException("no channel");
			channels = p0.length;
			now = ms;
			cur = p0.clone();
			first = p0.clone();
			vel = new double[channels];
			times[0] = ms;
			kinds = new int[16 * channels];
			params = new double[16 * channels * STRIDE];
			return this;
		}

		/** Current end time (ms). */
		public double time() {
			return now;
		}

		/** Current end value of channel {@code ch}. */
		public double value(int ch) {
			return cur[ch];
		}

		/** Rate of channel {@code ch} at the current end (units per second; 0 at the start). */
		public double velocity(int ch) {
			return vel[ch];
		}

		/** Number of channels. */
		public int channels() {
			return channels;
		}

		/**
		 * Opens a segment of {@code durMs} (> 0). Every channel HOLDs unless set on the returned {@link Seg}; close it
		 * with {@link Seg#done()}.
		 */
		public Seg seg(double durMs) {
			if (channels < 0) throw new IllegalStateException("start() first");
			if (open != null) throw new IllegalStateException("previous segment not done()");
			if (!(durMs > 0) || Double.isInfinite(durMs)) throw new IllegalArgumentException("duration " + durMs);
			ensure(segs + 1);
			int base = segs * channels;
			for (int c = 0; c < channels; c++) {
				kinds[base + c] = HOLD;
				int o = (base + c) * STRIDE;
				Arrays.fill(params, o, o + STRIDE, 0);
				params[o] = cur[c];
			}
			open = new Seg(this, durMs, base);
			return open;
		}

		/** Holds every channel for {@code durMs}. */
		public Builder hold(double durMs) {
			return seg(durMs).done();
		}

		/** Every channel moves linearly to {@code p1} in {@code durMs}. */
		public Builder linearTo(double durMs, double... p1) {
			Seg s = seg(durMs);
			for (int c = 0; c < channels; c++) s.linearTo(c, p1[c]);
			return s.done();
		}

		/**
		 * Every channel flies to {@code p1} in {@code durMs} under the constant acceleration {@code accel[c]} (gravity:
		 * e.g. {@code {0, 900}} px/s² for screen-down y): the exact parabola through both end points.
		 */
		public Builder ballisticTo(double durMs, double[] p1, double[] accel) {
			Seg s = seg(durMs);
			for (int c = 0; c < channels; c++) s.ballisticTo(c, p1[c], accel[c]);
			return s.done();
		}

		/** Builds the track (at least zero segments; a track without segments holds its start values). */
		public Track build() {
			if (channels < 0) throw new IllegalStateException("start() first");
			if (open != null) throw new IllegalStateException("segment not done()");
			return new Track(channels, Arrays.copyOf(times, segs + 1), Arrays.copyOf(kinds, segs * channels),
				Arrays.copyOf(params, segs * channels * STRIDE), first.clone(), cur.clone());
		}

		private void ensure(int nSegs) {
			if (times.length < nSegs + 1) times = Arrays.copyOf(times, Math.max(nSegs + 1, times.length * 2));
			if (kinds.length < nSegs * channels) {
				int cap = Math.max(nSegs, kinds.length / channels * 2);
				kinds = Arrays.copyOf(kinds, cap * channels);
				params = Arrays.copyOf(params, cap * channels * STRIDE);
			}
		}

		void close(Seg s) {
			double tau = s.durMs / 1000.0;
			for (int c = 0; c < channels; c++) {
				int i = s.base + c;
				double v = Track.value(kinds[i], params, i * STRIDE, tau);
				if (!Double.isFinite(v)) throw new IllegalStateException("channel " + c + " not finite at segment end");
				cur[c] = v;
				vel[c] = Track.rate(kinds[i], params, i * STRIDE, tau);
			}
			now += s.durMs;
			segs++;
			times[segs] = now;
			open = null;
		}
	}

	/**
	 * One open segment: set each channel's closed form (all start at the channel's current value), then
	 * {@link #done()}.
	 */
	public static final class Seg {
		private final Builder b;
		private final double durMs;
		private final int base;

		private Seg(Builder b, double durMs, int base) {
			this.b = b;
			this.durMs = durMs;
			this.base = base;
		}

		/** Segment duration (ms). */
		public double durationMs() {
			return durMs;
		}

		private int o(int ch, int kind) {
			if (ch < 0 || ch >= b.channels) throw new IllegalArgumentException("channel " + ch);
			b.kinds[base + ch] = kind;
			int o = (base + ch) * STRIDE;
			double p0 = b.cur[ch];
			Arrays.fill(b.params, o, o + STRIDE, 0);
			b.params[o] = p0;
			return o;
		}

		/** Channel {@code ch} holds its value. */
		public Seg hold(int ch) {
			o(ch, HOLD);
			return this;
		}

		/** Channel {@code ch} moves at constant rate {@code v} (per second). */
		public Seg linear(int ch, double v) {
			int o = o(ch, LINEAR);
			b.params[o + 1] = v;
			return this;
		}

		/** Channel {@code ch} moves linearly to {@code p1}. */
		public Seg linearTo(int ch, double p1) {
			return linear(ch, (p1 - b.cur[ch]) / (durMs / 1000.0));
		}

		/** Channel {@code ch} starts at rate {@code v0} with constant acceleration {@code a} (per second²). */
		public Seg accel(int ch, double v0, double a) {
			int o = o(ch, CONST_ACCEL);
			b.params[o + 1] = v0;
			b.params[o + 2] = a;
			return this;
		}

		/** Channel {@code ch} reaches {@code p1} with end rate {@code v1} under constant acceleration (solved). */
		public Seg accelToStop(int ch, double p1, double v1) {
			double tt = durMs / 1000.0;
			double dp = p1 - b.cur[ch];
			// dp = (v0 + v1)/2 · T  →  v0 = 2dp/T − v1 ; a = (v1 − v0)/T
			double v0 = 2 * dp / tt - v1;
			return accel(ch, v0, (v1 - v0) / tt);
		}

		/** Channel {@code ch} flies to {@code p1} under acceleration {@code a} (exact parabola through both ends). */
		public Seg ballisticTo(int ch, double p1, double a) {
			double tt = durMs / 1000.0;
			int o = o(ch, BALLISTIC);
			b.params[o + 1] = Ballistic.v0For(p1 - b.cur[ch], tt, a);
			b.params[o + 2] = a;
			return this;
		}

		/** Channel {@code ch} starts at rate {@code v0} under acceleration {@code a} (BALLISTIC, free end). */
		public Seg ballistic(int ch, double v0, double a) {
			int o = o(ch, BALLISTIC);
			b.params[o + 1] = v0;
			b.params[o + 2] = a;
			return this;
		}

		/**
		 * Channel {@code ch}'s rate decays exponentially from {@code v0} toward {@code vInf} with time constant
		 * {@code tauMs} (a roulette orbit, a rotor easing to idle).
		 */
		public Seg expDecay(int ch, double v0, double vInf, double tauMs) {
			if (!(tauMs > 0)) throw new IllegalArgumentException("tau " + tauMs);
			int o = o(ch, EXP_DECAY);
			b.params[o + 1] = v0;
			b.params[o + 2] = vInf;
			b.params[o + 3] = tauMs / 1000.0;
			return this;
		}

		/**
		 * Channel {@code ch} oscillates {@code A·e^(−τ/τd)·cos(2π·τ/P + φ)} around a centre chosen so that the
		 * segment starts at the current value (C0). {@code tauMs ≤ 0} = no decay.
		 *
		 * @param amp      amplitude A
		 * @param tauMs    decay time constant τd (ms)
		 * @param periodMs period P (ms)
		 * @param phase    phase φ (rad); 0 starts at the crest (centre = value − A)
		 */
		public Seg osc(int ch, double amp, double tauMs, double periodMs, double phase) {
			if (!(periodMs > 0)) throw new IllegalArgumentException("period " + periodMs);
			int o = o(ch, DAMPED_OSC);
			b.params[o] = b.cur[ch] - amp * Math.cos(phase);
			b.params[o + 1] = amp;
			b.params[o + 2] = tauMs > 0 ? tauMs / 1000.0 : 0;
			b.params[o + 3] = periodMs / 1000.0;
			b.params[o + 4] = phase;
			return this;
		}

		/**
		 * Channels {@code chU} and {@code chZ} rotate about the pivot {@code (pu, pz)} in their plane: angle
		 * {@code φ(τ) = φ0 + ω0·τ + ½·α·τ²} where φ0 and the radius come from the current values (C0).
		 *
		 * @param omega0 angular speed at the start (rad/s; positive = counter-clockwise in (u, z))
		 * @param alpha  angular acceleration (rad/s²)
		 */
		public Seg pivot(int chU, int chZ, double pu, double pz, double omega0, double alpha) {
			if (chU == chZ) throw new IllegalArgumentException("same channel");
			double du = b.cur[chU] - pu;
			double dz = b.cur[chZ] - pz;
			double rho = Math.sqrt(du * du + dz * dz);
			double phi0 = Math.atan2(dz, du);
			int ou = o(chU, PIVOT);
			int oz = o(chZ, PIVOT);
			double[] p = b.params;
			p[ou] = pu;
			p[ou + 1] = rho;
			p[ou + 2] = phi0;
			p[ou + 3] = omega0;
			p[ou + 4] = alpha;
			p[ou + 5] = 0;
			p[oz] = pz;
			p[oz + 1] = rho;
			p[oz + 2] = phi0;
			p[oz + 3] = omega0;
			p[oz + 4] = alpha;
			p[oz + 5] = 1;
			return this;
		}

		/**
		 * Pivot that turns by exactly {@code dTheta} rad over the segment, starting at {@code omega0} rad/s (the
		 * angular acceleration is solved): a quarter tip is {@code dTheta = ∓π/2}.
		 */
		public Seg pivotBy(int chU, int chZ, double pu, double pz, double dTheta, double omega0) {
			double tt = durMs / 1000.0;
			return pivot(chU, chZ, pu, pz, omega0, 2 * (dTheta - omega0 * tt) / (tt * tt));
		}

		/** Closes the segment; returns the builder. */
		public Builder done() {
			b.close(this);
			return b;
		}
	}
}

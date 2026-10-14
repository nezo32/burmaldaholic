package dev.nezo.burmaldaholic.core.anim.motion;

/**
 * A chain of decaying ballistic hops along one coordinate, plus height, FORCED to end exactly at a target (RO §2.3
 * rattle, PL §2.2 bin settle; dice/craps floor bounces, chip landing settle, coin flip).
 *
 * <p>The chain starts with a contact at {@code start} (t = 0). Hop j (1-based) is: an optional skid on the floor
 * (0–{@code skidMaxMs}, the along speed carries on), then an airborne hop of airtime {@code T_j} (along: linear; height:
 * an exact parabola of apex {@code g·T_j²/8}), landing at {@code L_j}. Airtimes decay {@code T_{j+1} = T_j·e_n}; the
 * natural travel of each hop decays with {@code e_t}. The natural travel fractions are then scaled to the required
 * distance, and the landings are snapped to a {@link Lattice}: alternately a FRET (a sharp tick, half-integer pocket)
 * and a FLOOR (a dull knock, integer pocket), monotonic, the last landing exactly on {@code target}. With probability
 * {@code kickProb} one hop among the last {@code kickWindow} is a kick-back of −1 lattice step off a fret.
 *
 * <p>All random draws happen in a fixed order (independent of the retries), from the caller's {@link MotionRng}, so
 * a chain is a pure function of its inputs. {@link #sample} is allocation-free and pure in time; {@link #appendTo}
 * emits the same motion as {@link Track} segments.
 */
public final class BounceChain {
	/** Lattice class of a landing: an integer point (pocket floor). */
	public static final int FLOOR = 0;
	/** Lattice class of a landing: a half point (fret). */
	public static final int FRET = 1;

	/**
	 * Landing lattice: FLOOR points at {@code floorOffset + i·step}, FRET points at {@code fretOffset + i·step};
	 * {@link #NONE} = continuous (no snapping, no classes).
	 */
	public static final class Lattice {
		/** No lattice: landings are not snapped (Plinko bin settle, chip settle). */
		public static final Lattice NONE = new Lattice(0, 0, 0);

		private final double step;
		private final double floorOffset;
		private final double fretOffset;

		private Lattice(double step, double floorOffset, double fretOffset) {
			this.step = step;
			this.floorOffset = floorOffset;
			this.fretOffset = fretOffset;
		}

		/** Roulette pockets (pocket units): floors at integers, frets at half-integers. */
		public static Lattice pockets() {
			return new Lattice(1, 0, 0.5);
		}

		/** A custom lattice ({@code step > 0}). */
		public static Lattice of(double step, double floorOffset, double fretOffset) {
			if (!(step > 0)) throw new IllegalArgumentException("step " + step);
			return new Lattice(step, floorOffset, fretOffset);
		}

		/** True for {@link #NONE}. */
		public boolean continuous() {
			return step <= 0;
		}

		/** Lattice step (0 when continuous). */
		public double step() {
			return step;
		}

		/** Nearest point of class {@code cls} to {@code x}. */
		public double nearest(double x, int cls) {
			if (continuous()) return x;
			double off = cls == FLOOR ? floorOffset : fretOffset;
			return off + Math.round((x - off) / step) * step;
		}

		/** First point of class {@code cls} strictly beyond {@code x} in direction {@code dir} (±1). */
		public double next(double x, int cls, int dir) {
			if (continuous()) return x;
			double off = cls == FLOOR ? floorOffset : fretOffset;
			double i = (x - off) / step;
			double j = dir > 0 ? Math.floor(i + 1e-9) + 1 : Math.ceil(i - 1e-9) - 1;
			return off + j * step;
		}

		/** True when {@code x} is a point of class {@code cls} (±1e-9). */
		public boolean on(double x, int cls) {
			return continuous() || Math.abs(nearest(x, cls) - x) <= 1e-9;
		}
	}

	/**
	 * Chain parameters.
	 *
	 * @param minHops    fewest hops (≥ 1)
	 * @param maxHops    most hops
	 * @param airMs      first airtime T_1 (ms)
	 * @param airJitter  ± fraction of T_1
	 * @param enMin      airtime ratio range low (T_{j+1}/T_j, &lt; 1)
	 * @param enMax      airtime ratio range high
	 * @param etMin      travel-speed ratio range low (v_{j+1}/v_j)
	 * @param etMax      travel-speed ratio range high
	 * @param z0         apex of the first hop (height units; fixes g = 8·z0/T_1²)
	 * @param skidMaxMs  longest skid before a hop (0 = none)
	 * @param kickProb   probability of one kick-back hop (lattice only)
	 * @param kickWindow the kick-back is among the last this many hops
	 * @param timeScale  multiplies every airtime and skid (RO §2.4 tail compression {@code c})
	 */
	public record Params(int minHops, int maxHops, double airMs, double airJitter, double enMin, double enMax, double etMin, double etMax,
			double z0, double skidMaxMs, double kickProb, int kickWindow, double timeScale) {
		public Params {
			if (minHops < 1 || maxHops < minHops) throw new IllegalArgumentException("hops " + minHops + ".." + maxHops);
			if (!(airMs > 0) || !(timeScale > 0) || !(enMax <= 1) || !(enMin > 0) || enMax < enMin || etMax < etMin || !(etMin > 0)) {
				throw new IllegalArgumentException("params");
			}
		}

		/** RO §2.3 rattle: 4–7 hops, T_1 220 ms ±15 %, e_n 0.66–0.76, e_t 0.45–0.62, z_0 4 px, skid 0–90 ms, kick 0.3. */
		public static Params roulette() {
			return new Params(4, 7, 220, 0.15, 0.66, 0.76, 0.45, 0.62, 4, 90, 0.3, 3, 1);
		}

		/** PL §2.2 bin settle: 2 bounces with e = 0.35 (≈ 3.5 px then ≈ 0.4 px at g 900 px/s²), no skid, no kick. */
		public static Params plinkoBin() {
			return new Params(2, 2, 176, 0, 0.35, 0.35, 0.5, 0.5, 3.5, 0, 0, 0, 1);
		}

		/** A chip / small object landing: 1–2 tiny hops. */
		public static Params chipSettle() {
			return new Params(1, 2, 80, 0.1, 0.4, 0.5, 0.4, 0.6, 1, 0, 0, 0, 1);
		}

		/** The same parameters with another time scale. */
		public Params withTimeScale(double c) {
			return new Params(minHops, maxHops, airMs, airJitter, enMin, enMax, etMin, etMax, z0, skidMaxMs, kickProb, kickWindow, c);
		}
	}

	private final double start;
	private final double target;
	private final int hops;
	private final int kick; // 0-based hop index of the kick-back, -1 = none
	private final double g; // height units per s²
	private final double[] landAt;
	private final double[] landMs;
	private final double[] takeoffAt;
	private final double[] takeoffMs;
	private final double[] skidStartMs;
	private final double[] airMs;
	private final int[] cls;

	private BounceChain(double start, double target, int hops, int kick, double g, double[] landAt, double[] landMs, double[] takeoffAt,
			double[] takeoffMs, double[] skidStartMs, double[] airMs, int[] cls) {
		this.start = start;
		this.target = target;
		this.hops = hops;
		this.kick = kick;
		this.g = g;
		this.landAt = landAt;
		this.landMs = landMs;
		this.takeoffAt = takeoffAt;
		this.takeoffMs = takeoffMs;
		this.skidStartMs = skidStartMs;
		this.airMs = airMs;
		this.cls = cls;
	}

	/**
	 * Plans a chain from a contact at {@code start} to a rest landing exactly at {@code target}.
	 *
	 * @param start   along coordinate of the first contact (t = 0)
	 * @param v0      along speed just after the first contact (units/s); its sign is the direction when
	 *                {@code target == start}; its magnitude only shapes the skids
	 * @param target  final landing (with a lattice: a FLOOR point)
	 * @param lattice landing lattice ({@link Lattice#NONE} = continuous)
	 * @param p       parameters
	 * @param rng     cosmetic randomness (consumed in a fixed order)
	 */
	public static BounceChain plan(double start, double v0, double target, Lattice lattice, Params p, MotionRng rng) {
		Lattice lat = lattice == null ? Lattice.NONE : lattice;
		if (!lat.continuous() && !lat.on(target, FLOOR)) throw new IllegalArgumentException("target " + target + " is not a FLOOR point");
		// ---- every draw, in a fixed order -------------------------------------------------------------------------
		int nDraw = rng.rangeInt(p.minHops(), p.maxHops());
		boolean wantKick = rng.chance(p.kickProb());
		double t1 = p.airMs() * (1 + rng.range(-p.airJitter(), p.airJitter())) * p.timeScale();
		int m = p.maxHops();
		double[] en = new double[m];
		double[] et = new double[m];
		double[] skid = new double[m];
		for (int j = 0; j < m; j++) {
			en[j] = rng.range(p.enMin(), p.enMax());
			et[j] = rng.range(p.etMin(), p.etMax());
			skid[j] = p.skidMaxMs() > 0 ? rng.range(0, p.skidMaxMs()) * p.timeScale() : 0;
		}
		int dir = target > start ? 1 : target < start ? -1 : (v0 >= 0 ? 1 : -1);
		for (int n = nDraw; n >= 1; n--) {
			boolean kick = wantKick && !lat.continuous() && n >= 3 && p.kickWindow() >= 2;
			BounceChain c = build(start, target, dir, n, kick, t1, en, et, skid, Math.abs(v0), p.z0(), lat);
			if (c != null) return c;
		}
		throw new IllegalStateException("unreachable: a single hop always fits");
	}

	private static BounceChain build(double start, double target, int dir, int n, boolean kick, double t1, double[] en, double[] et,
			double[] skid, double speed, double z0, Lattice lat) {
		int kj = kick ? n - 2 : -1; // 0-based: the second-to-last hop (lands on a fret; n−1−kj odd)
		int[] cls = new int[n];
		cls[n - 1] = FLOOR;
		for (int j = n - 2; j >= 0; j--) cls[j] = (j + 1 == kj) ? cls[j + 1] : 1 - cls[j + 1];
		double[] air = new double[n];
		air[0] = t1;
		for (int j = 1; j < n; j++) air[j] = air[j - 1] * en[j - 1];
		// natural forward travel of each non-kick hop
		double[] d = new double[n];
		double v = 1;
		double sum = 0;
		for (int j = 0; j < n; j++) {
			if (j > 0) v *= et[j - 1];
			if (j == kj) continue;
			d[j] = v * air[j];
			sum += d[j];
		}
		double dist = Math.abs(target - start);
		double step = lat.step();
		double distF = dist + (kj >= 0 ? step : 0);
		double[] land = new double[n];
		double prog = 0;
		double back = 0;
		double prev = start;
		for (int j = 0; j < n; j++) {
			double l;
			if (j == kj) {
				l = prev - dir * step;
				back = step;
			} else {
				prog += sum > 0 ? d[j] / sum * distF : 0;
				if (j == n - 1) {
					l = target;
				} else if (lat.continuous()) {
					l = start + dir * (prog - back);
				} else {
					l = lat.nearest(start + dir * (prog - back), cls[j]);
					if ((l - prev) * dir <= 1e-9) l = lat.next(prev, cls[j], dir);
					// leave room for the remaining hops: each later non-kick hop advances at least half a step
					int later = 0;
					for (int q = j + 1; q < n; q++) if (q != kj) later++;
					double maxPos = target - dir * 0.5 * step * later;
					if ((l - maxPos) * dir > 1e-9) l = lat.nearest(maxPos, cls[j]);
				}
				double adv = (l - prev) * dir;
				if (lat.continuous() ? adv < -1e-12 : adv <= 1e-9) {
					if (n > 1) return null;
				}
				if (j < n - 1 && (target - l) * dir < (lat.continuous() ? -1e-12 : 1e-9)) {
					if (n > 1) return null;
				}
			}
			land[j] = l;
			prev = l;
		}
		// skids, take-off points and times
		double[] takeAt = new double[n];
		double[] takeMs = new double[n];
		double[] skidStart = new double[n];
		double[] landMs = new double[n];
		double t = 0;
		double from = start;
		for (int j = 0; j < n; j++) {
			double s = j == kj || speed <= 0 ? 0 : skid[j];
			double sigma = s > 0 ? 0.5 * s / (s + air[j]) : 0;
			skidStart[j] = t;
			takeAt[j] = from + sigma * (land[j] - from);
			t += s;
			takeMs[j] = t;
			t += air[j];
			landMs[j] = t;
			from = land[j];
		}
		double t1s = t1 / 1000.0;
		double g = 8 * z0 / (t1s * t1s);
		return new BounceChain(start, target, n, kj, g, land, landMs, takeAt, takeMs, skidStart, air, cls);
	}

	/** Number of hops. */
	public int hops() {
		return hops;
	}

	/** Start (first contact) coordinate. */
	public double start() {
		return start;
	}

	/** Final landing coordinate. */
	public double target() {
		return target;
	}

	/** Time of the final landing (ms after the first contact). */
	public double endMs() {
		return landMs[hops - 1];
	}

	/** Gravity of the chain (height units per s²). */
	public double g() {
		return g;
	}

	/** Landing coordinate of hop {@code j} (0-based). */
	public double landAt(int j) {
		return landAt[j];
	}

	/** Landing time of hop {@code j} (ms). */
	public double landMs(int j) {
		return landMs[j];
	}

	/** Take-off coordinate of hop {@code j} (end of its skid). */
	public double takeoffAt(int j) {
		return takeoffAt[j];
	}

	/** Take-off time of hop {@code j} (ms). */
	public double takeoffMs(int j) {
		return takeoffMs[j];
	}

	/** Airtime of hop {@code j} (ms). */
	public double airMs(int j) {
		return airMs[j];
	}

	/** Apex height of hop {@code j}: {@code g·T²/8}. */
	public double apex(int j) {
		double s = airMs[j] / 1000.0;
		return g * s * s / 8;
	}

	/** Lattice class of landing {@code j} ({@link #FLOOR} / {@link #FRET}; FLOOR for a continuous lattice). */
	public int landClass(int j) {
		return cls[j];
	}

	/** True when hop {@code j} is the kick-back. */
	public boolean isKick(int j) {
		return j == kick;
	}

	/** Index of the kick-back hop, −1 when none. */
	public int kickIndex() {
		return kick;
	}

	/** Along speed of hop {@code j} in the air (units/s, signed). */
	public double airSpeed(int j) {
		return (landAt[j] - takeoffAt[j]) / (airMs[j] / 1000.0);
	}

	/** Vertical landing speed of hop {@code j} (units/s, ≥ 0): {@code g·T/2}. */
	public double landVz(int j) {
		return g * airMs[j] / 2000.0;
	}

	/**
	 * Writes the state at {@code ms} (0 = first contact): {@code out[0]} along, {@code out[1]} height ≥ 0,
	 * {@code out[2]} along speed (/s), {@code out[3]} vertical speed (/s). Before 0: at start; after the end: at
	 * target, at rest. Allocation-free; {@code out.length ≥ 4}.
	 */
	public void sample(double ms, double[] out) {
		if (ms <= 0) {
			set(out, start, 0, 0, 0);
			return;
		}
		if (ms >= landMs[hops - 1]) {
			set(out, target, 0, 0, 0);
			return;
		}
		for (int j = 0; j < hops; j++) {
			if (ms >= landMs[j]) continue;
			double from = j == 0 ? start : landAt[j - 1];
			if (ms < takeoffMs[j]) {
				double dur = takeoffMs[j] - skidStartMs[j];
				double u = (ms - skidStartMs[j]) / dur;
				set(out, from + u * (takeoffAt[j] - from), 0, (takeoffAt[j] - from) / (dur / 1000.0), 0);
				return;
			}
			double tt = airMs[j] / 1000.0;
			double tau = (ms - takeoffMs[j]) / 1000.0;
			double vz0 = g * tt / 2;
			double z = Math.max(0, vz0 * tau - 0.5 * g * tau * tau);
			set(out, takeoffAt[j] + (landAt[j] - takeoffAt[j]) * (tau / tt), z, (landAt[j] - takeoffAt[j]) / tt, vz0 - g * tau);
			return;
		}
		set(out, target, 0, 0, 0);
	}

	private static void set(double[] out, double a, double z, double va, double vz) {
		out[0] = a;
		out[1] = z;
		out[2] = va;
		out[3] = vz;
	}

	/**
	 * Appends the chain to a track whose channel {@code chAlong} is at {@code start} and channel {@code chZ} at 0: per
	 * hop a LINEAR skid (if any) then an airborne segment (along LINEAR, height BALLISTIC under −g). Other channels hold.
	 */
	public Track.Builder appendTo(Track.Builder b, int chAlong, int chZ) {
		if (Math.abs(b.value(chAlong) - start) > 1e-9 || Math.abs(b.value(chZ)) > 1e-9) {
			throw new IllegalStateException("track is not at the chain start");
		}
		for (int j = 0; j < hops; j++) {
			double skid = takeoffMs[j] - skidStartMs[j];
			if (skid > 0) b.seg(skid).linearTo(chAlong, takeoffAt[j]).done();
			double tt = airMs[j] / 1000.0;
			b.seg(airMs[j]).linearTo(chAlong, landAt[j]).ballistic(chZ, g * tt / 2, -g).done();
		}
		return b;
	}
}

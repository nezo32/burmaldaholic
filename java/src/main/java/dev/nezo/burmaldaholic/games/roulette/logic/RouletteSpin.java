package dev.nezo.burmaldaholic.games.roulette.logic;

import dev.nezo.burmaldaholic.core.anim.motion.BounceChain;
import dev.nezo.burmaldaholic.core.anim.motion.DecaySolver;
import dev.nezo.burmaldaholic.core.anim.motion.DetMath;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import dev.nezo.burmaldaholic.core.anim.motion.MotionRng;
import dev.nezo.burmaldaholic.core.anim.motion.Track;
import java.util.ArrayList;
import java.util.List;

/**
 * One deterministic roulette spin (ROULETTE-ANIM-SPEC §2; replaces {@link RouletteBallPath}). PURE: every viewer (the
 * player's screen, the other bettors, the in-world board) builds the same spin from the server's public data and samples
 * it at the shared clock, so the ball lands in the drawn pocket for everybody at the same moment.
 *
 * <p><b>Contract.</b> Time {@code t} is ms since {@code launchTick·50} (the NO_MORE_BETS start). For {@code t ≥ restMs}
 * the ball's angle relative to the rotor is exactly {@code pocketAngle(result)} (mod 360) and stays there until the next
 * pickup; for {@code t < spinStartMs} {@link #sample} does not depend on the result (a spin built before the draw,
 * {@code result = -1}, samples bit-identically); {@code restMs ≤ spinEndMs − 300}. The rotor never resets
 * ({@link RotorModel}).
 *
 * <p><b>Phases.</b> Pickup (the old ball lifts out of its pocket, 0–150 ms) · launch at 250 ms against the rotor, flung to
 * the wall · stage-A orbit (exponential decay, result-free) · stage-B orbit to the drop ({@link DecaySolver}, solved so the
 * contact point lands the rattle in the result pocket) · hunting (last 700 ms) · drop spiral down the stator with an
 * optional deflector hit · rotor contact and rattle ({@link BounceChain} in pocket units, landings alternating frets and
 * pocket floors, ending exactly in the result pocket) · settle rock · riding the rotor.
 *
 * <p><b>Units.</b> Angles in degrees, clockwise positive, 0 = top (the dealer side); radii are fractions of {@code Rw}
 * (the track wall, 92 px on the 208 wheel); {@code z} is height in 208-wheel art px. "Angle is law, radius and height are
 * decoration": the deflector, hops and wobbles never move the angle at the solved contact times.
 */
public final class RouletteSpin {
	/** Degrees per pocket. */
	public static final double STEP = 360.0 / Wheel.POCKETS;
	// radii (fractions of Rw)
	public static final double R_LAUNCH = 0.90, R_WALL = 0.965, R_HUNT = 0.95, R_DIAMOND = 0.913, R_LIP = 0.80;
	public static final double R_NUMBER = 0.745, R_FRET_RING = 0.635, R_POCKET = 0.603;
	/** Outer edge of the pocket ring (61.5 / 92 px): below it (and low) the ball is "in a pocket". */
	public static final double R_POCKET_RIM = 0.668;
	/** Deflector diamonds: 8 at 22.5° + k·45° (absolute; they are on the static bowl). */
	public static final double DIAMOND0 = 22.5, DIAMOND_PITCH = 45;
	// timing (ms)
	public static final int LAUNCH_MS = 250;
	public static final int PICKUP_FADE_MS = 150;
	public static final int FLING_MS = 150;
	public static final int HUNT_MS = 700;
	public static final int SPIRAL_MS = 600;
	public static final int SETTLE_MS = 260;
	public static final int END_MARGIN_MS = 300;
	/** Shortest stage-B orbit. */
	public static final int MIN_STAGE_B_MS = 60;
	/** Stage-B drop-time search: ± this around the natural drop, in steps of {@link #TD_STEP}. */
	public static final int TD_JITTER_MS = 150, TD_STEP = 10;
	/** Default NO_MORE_BETS length (ticks) when only a v1 sync is known. */
	public static final int NMB_TICKS = 20;
	// ball speeds (deg/s) and decay
	public static final double OMEGA_LAUNCH = 756, OMEGA_DROP = 306, TAU_BALL_MS = 4200;
	public static final double K_MIN = 0.2, K_MAX = 6;
	/** Scatter window (pockets) of the rattle. */
	public static final int SCATTER_LO_MIN = 5, SCATTER_LO_MAX = 8, SCATTER_W = 8;
	/** Speed kept through the first rotor contact. */
	public static final double CONTACT_KEEP = 0.55;
	/** Spiral speed factor at contact: decays 15 %, whips by (r_D / r_C)^0.6. */
	static final double SPIRAL_F = 0.85 * Math.pow(R_HUNT / R_LIP, 0.6);
	public static final double HIT_FLASH_MS = 40;

	// event kinds
	public static final int EV_PICKUP = 1, EV_PUSH = 2, EV_LAUNCH = 3, EV_HUNT = 4, EV_ROLL = 5, EV_DROP = 6, EV_DEFLECTOR = 7, EV_CONTACT = 8,
		EV_FRET = 9, EV_FLOOR = 10, EV_KICKBACK = 11, EV_SETTLE_CLICK = 12, EV_REST = 13;

	// phases of a sample
	public static final int PH_PICKUP = 0, PH_HIDDEN = 1, PH_LAUNCH = 2, PH_ORBIT = 3, PH_HUNT = 4, PH_SPIRAL = 5, PH_RATTLE = 6, PH_SETTLE = 7,
		PH_REST = 8;
	// ball frames
	public static final int FRAME_NORMAL = 0, FRAME_HIT = 1, FRAME_POCKET = 2;

	/** A sample of the spin (reused by callers; all fields written by {@link #sample}). */
	public static final class Sample {
		/** Ball angle relative to the rotor (unwrapped). */
		public double rel;
		/** Absolute ball angle. */
		public double abs;
		/** Rotor angle. */
		public double rotor;
		/** Radius (fraction of Rw). */
		public double r;
		/** Height (208-wheel art px, ≥ 0). */
		public double z;
		/** Absolute ball speed (deg/s, ≥ 0). */
		public double omega;
		/** Ball alpha 0..1 and whether it is drawn at all. */
		public double alpha;
		public boolean visible;
		public int phase;
		public int frame;
	}

	public final int result;
	public final int seed;
	public final int lastResult;
	public final long launchTick;
	public final long spinStart;
	public final int spinTicks;
	public final RotorModel rotor;
	public final int dir;
	/** Direction of the ball (absolute and relative to the rotor): {@code −dir}. */
	public final int sigma;

	// stage A (seeded, result-free)
	private final double phiL;
	private final double omegaB0;
	private final double tauB;
	private final double tS;
	private final double tEnd;
	// stage B + tail (result builds only)
	private final boolean solved;
	private final boolean fallback;
	private final double omegaD;
	private final double tD;
	private final double tH;
	private final double tC;
	private final double tR;
	private final double rest;
	private final double tsp;
	private final double c;
	private final double thetaS; // stage-A distance at tS
	private final double ballS; // absolute angle at tS
	private final double ballD; // absolute angle at tD
	private final double rD; // radius at tD
	private final double tailScale;
	private final @org.jspecify.annotations.Nullable Track orbit;
	private final DecaySolver.@org.jspecify.annotations.Nullable Solution stageB;
	private final @org.jspecify.annotations.Nullable BounceChain chain;
	private final double pC; // contact (pocket units, relative)
	private final double pT; // final landing (integer pocket units)
	private final double scatter;
	// deflector
	private final boolean deflector;
	private final double xHit; // spiral fraction of the hit
	private final int diamond;
	private final double popMs, hopMs, dipA, dipTau;
	// rattle radii per landing
	private final double[] landR;
	private final MotionEvent[] events;
	private final double[] hitTimes;
	private final double[] scratch = new double[4];

	private RouletteSpin(int result, int seed, int lastResult, long launchTick, long spinStart, int spinTicks, RotorModel rotor) {
		if (result != -1 && !Wheel.isPocket(result)) throw new IllegalArgumentException("not a pocket: " + result);
		this.result = result;
		this.seed = seed;
		this.lastResult = Wheel.isPocket(lastResult) ? lastResult : -1;
		this.launchTick = launchTick;
		this.spinStart = spinStart;
		this.spinTicks = Math.max(1, spinTicks);
		this.rotor = rotor;
		this.dir = rotor.dir();
		this.sigma = -dir;
		MotionRng ln = MotionRng.of(seed, "RO_LAUNCH", 0);
		this.phiL = ln.range(-20, 20);
		this.omegaB0 = OMEGA_LAUNCH * (1 + ln.range(-0.06, 0.06));
		this.tauB = TAU_BALL_MS * (1 + ln.range(-0.08, 0.08));
		this.tS = Math.max(LAUNCH_MS + 50, (spinStart - launchTick) * 50.0);
		this.tEnd = tS + this.spinTicks * 50.0;
		this.thetaS = stageA(tS);
		this.ballS = phiL + sigma * thetaS;
		List<MotionEvent> ev = new ArrayList<>();
		ev.add(new MotionEvent(0, EV_PUSH, 0, 1f, 0, 0, dir));
		if (this.lastResult >= 0) ev.add(new MotionEvent(0, EV_PICKUP, 0, 1f, 0, 0, this.lastResult));
		ev.add(new MotionEvent(LAUNCH_MS, EV_LAUNCH, 0, 1f, phiL, 0, 0));
		if (result < 0) {
			solved = false;
			fallback = false;
			omegaD = OMEGA_DROP;
			tD = tH = tC = tR = rest = Double.POSITIVE_INFINITY;
			tsp = SPIRAL_MS;
			c = 1;
			ballD = rD = tailScale = pC = pT = scatter = 0;
			orbit = null;
			stageB = null;
			chain = null;
			deflector = false;
			xHit = 0;
			diamond = -1;
			popMs = hopMs = dipA = dipTau = 0;
			landR = new double[0];
			events = MotionEvent.sort(ev.toArray(new MotionEvent[0]));
			hitTimes = new double[0];
			return;
		}
		this.solved = true;
		// ---- seeded tail shape (result-free draws) --------------------------------------------------------------------
		MotionRng tail = MotionRng.of(seed, "RO_TAIL", 0);
		this.omegaD = OMEGA_DROP * (1 + tail.range(-0.05, 0.05));
		int sLo = tail.rangeInt(SCATTER_LO_MIN, SCATTER_LO_MAX);
		double sPref = tail.range(sLo + 0.5, sLo + SCATTER_W - 0.5);
		double avail = tEnd - END_MARGIN_MS - tS - SPIRAL_MS;
		double c0 = Math.max(0.5, Math.min(1, avail / 2400));
		double tdMin = tS + MIN_STAGE_B_MS;
		// ---- solve: try drop times nearest the natural one first ---------------------------------------------------
		// pass 0: the natural window; 1: any drop time whose actual rattle still rests in time (the rattle is often
		// shorter than its bound); 2–3: a tighter tail (the shortest spins); 4: a wider scatter (the fallback, never
		// needed in the tested sweep)
		Plan plan = null;
		double[] scales = {c0, c0, c0 * 0.85, c0 * 0.72, c0};
		for (int pass = 0; pass < scales.length && plan == null; pass++) {
			double cc = scales[pass];
			boolean wide = pass == scales.length - 1;
			double ts = SPIRAL_MS * cc;
			BounceChain.Params params = BounceChain.Params.roulette().withTimeScale(cc);
			double chainMax = chainUpperBound(MotionRng.of(seed, "RO_RATTLE", 0), params);
			double tailMax = ts + chainMax + SETTLE_MS;
			double tdMax = Math.max(tdMin, tEnd - END_MARGIN_MS - tailMax);
			double tdNat = Math.max(tdMin, tdMax - TD_JITTER_MS);
			double hi = pass == 0 ? tdMax : Math.max(tdMax, tEnd - END_MARGIN_MS - ts - SETTLE_MS - 0.25 * chainMax);
			int nSteps = (int) Math.ceil(Math.max(Math.max(hi - tdNat, tdNat - tdMin), 0) / TD_STEP) + 1;
			for (int i = 0; i <= 2 * nSteps && plan == null; i++) {
				double off = (i + 1) / 2 * TD_STEP * (i % 2 == 0 ? 1 : -1);
				double td = tdNat + off;
				if (td < tdMin - 1e-9 || td > hi + 1e-9) continue;
				plan = tryPlan(td, sLo, sPref, wide, params, cc);
			}
		}
		if (plan == null) {
			// unreachable in practice (RouletteSpinTest sweeps every result × seed × spin length); last resort: the
			// shortest stage B, any scatter
			plan = tryPlan(tdMin, sLo, sPref, true, BounceChain.Params.roulette().withTimeScale(c0), c0);
			if (plan == null) throw new IllegalStateException("roulette spin unsolvable: result " + result + " seed " + seed);
		}
		this.c = plan.c;
		this.tailScale = c;
		this.tsp = SPIRAL_MS * c;
		this.fallback = plan.wide;
		this.tD = plan.td;
		this.tH = Math.max(tS, tD - HUNT_MS);
		this.tC = tD + tsp;
		this.stageB = plan.sol;
		this.chain = plan.chain;
		this.pC = plan.pC;
		this.pT = plan.pT;
		this.scatter = Math.abs(pT - pC);
		this.tR = tC + chain.endMs();
		this.rest = tR + SETTLE_MS;
		this.orbit = Track.builder().start(tS, 0).seg(plan.sol.T() * 1000.0).decay(0, plan.sol).done().build();
		this.ballD = ballS + sigma * orbit.sample(tD, 0);
		this.rD = huntR(tD);
		// ---- deflector (decoration, drawn after the solve) ----------------------------------------------------------
		MotionRng df = MotionRng.of(seed, "RO_DEFLECT", 0);
		boolean wantHit = df.chance(0.55);
		double xNat = Math.sqrt(Math.max(0, (rD - R_DIAMOND) / (rD - R_LIP)));
		int dk = -1;
		double xh = 0;
		if (wantHit) {
			double best = Double.MAX_VALUE;
			double aNat = spiralAngle(xNat);
			for (int k = -8; k <= 8; k++) {
				double base = Math.floor((aNat - DIAMOND0) / DIAMOND_PITCH) * DIAMOND_PITCH + DIAMOND0 + k * DIAMOND_PITCH;
				double x = solveSpiralX(base);
				if (x < 0.2 || x > 0.62) continue;
				double d = Math.abs(x - xNat);
				if (d < best) {
					best = d;
					xh = x;
					dk = (int) Math.floorMod(Math.round((base - DIAMOND0) / DIAMOND_PITCH), 8L);
				}
			}
		}
		this.deflector = dk >= 0;
		this.diamond = dk;
		this.xHit = deflector ? xh : xNat;
		double remain = (1 - xHit) * tsp;
		this.popMs = Math.min(120 * c, 0.5 * remain);
		this.hopMs = Math.min(140 * c, 0.6 * remain);
		this.dipTau = 30 * c;
		// A·τ·e⁻¹ ≤ 3° at the dip's extreme, and the dip never outruns the ball (it only slows, never reverses it)
		this.dipA = Math.min(3 * Math.E / dipTau, 0.6 * omegaD / 1000);
		// ---- rattle radii: fret landings on the fret tops, the other knocks up on the number ring, the last in the pocket --
		int n = chain.hops();
		landR = new double[n];
		for (int j = 0; j < n; j++) {
			landR[j] = j == n - 1 ? R_POCKET : chain.landClass(j) == BounceChain.FRET ? R_FRET_RING : R_NUMBER;
		}
		// ---- events -------------------------------------------------------------------------------------------------
		ev.add(new MotionEvent(tH, EV_HUNT, 0, 1f, 0, 0, 0));
		MotionRng roll = MotionRng.of(seed, "RO_ROLL", 0);
		for (double t = tH + roll.range(60, 140); t < tD - 40; ) {
			double h = (t - tH) / Math.max(1, tD - tH);
			ev.add(new MotionEvent(t, EV_ROLL, 0, (float) (0.6 + 0.4 * h), 0, 0, 0));
			double rate = 5 + 4 * h;
			t += 1000 / rate * (1 + roll.range(-0.25, 0.25));
		}
		ev.add(new MotionEvent(tD, EV_DROP, 0, 1f, 0, 0, 0));
		List<Double> hits = new ArrayList<>();
		if (deflector) {
			double th = tD + xHit * tsp;
			ev.add(new MotionEvent(th, EV_DEFLECTOR, 0, 1f, DIAMOND0 + diamond * DIAMOND_PITCH, R_DIAMOND, diamond));
			hits.add(th);
		}
		ev.add(new MotionEvent(tC, EV_CONTACT, 0, 1f, pC, R_LIP, 0));
		hits.add(tC);
		for (int j = 0; j < n; j++) {
			double tl = tC + chain.landMs(j);
			float strength = (float) Math.max(0.25, Math.min(1, chain.landVz(j) / Math.max(1e-9, chain.landVz(0))));
			int kind = chain.isKick(j) ? EV_KICKBACK : chain.landClass(j) == BounceChain.FRET ? EV_FRET : EV_FLOOR;
			ev.add(new MotionEvent(tl, kind, 0, strength, chain.landAt(j), landR[j], j));
			hits.add(tl);
		}
		// settle clicks at the swing's extremes (at most 2)
		int clicks = 0;
		for (int q = 0; q < 6 && clicks < 2; q++) {
			double s = 27.5 + 55 * q;
			if (s >= SETTLE_MS) break;
			if (Math.abs(settleSwing(s)) > 0.05) {
				ev.add(new MotionEvent(tR + s, EV_SETTLE_CLICK, 0, (float) Math.min(1, Math.abs(settleSwing(s)) * 4), 0, 0, clicks));
				clicks++;
			}
		}
		ev.add(new MotionEvent(rest, EV_REST, 0, 1f, 0, 0, result));
		this.events = MotionEvent.sort(ev.toArray(new MotionEvent[0]));
		this.hitTimes = hits.stream().mapToDouble(Double::doubleValue).sorted().toArray();
	}

	/**
	 * Builds a spin.
	 *
	 * @param result     the drawn pocket, or -1 before the draw (only the result-free part is valid then)
	 * @param seed       cosmetic seed (drawn at NO_MORE_BETS)
	 * @param lastResult the pocket the previous ball rests in (-1 = none)
	 * @param launchTick game tick of the launch (NO_MORE_BETS start)
	 * @param spinStart  game tick of the SPIN start (the draw)
	 * @param spinTicks  SPIN length in ticks
	 * @param rotor      the rotor model launched at {@code launchTick} (its {@code dir} is this spin's direction)
	 */
	public static RouletteSpin build(int result, int seed, int lastResult, long launchTick, long spinStart, int spinTicks, RotorModel rotor) {
		return new RouletteSpin(result, seed, lastResult, launchTick, spinStart, spinTicks, rotor);
	}

	// ---- planning -------------------------------------------------------------------------------------------------------

	private record Plan(double td, DecaySolver.Solution sol, BounceChain chain, double pC, double pT, boolean wide, double c) {}

	private @org.jspecify.annotations.Nullable Plan tryPlan(double td, int sLo, double sPref, boolean wide, BounceChain.Params params, double cc) {
		double tsp = SPIRAL_MS * cc;
		double T = (td - tS) / 1000.0;
		if (!(T > 0)) return null;
		double omegaS = omegaB0 * StrictMath.exp(-(tS - LAUNCH_MS) / tauB);
		double decelS = omegaS / (tauB / 1000.0);
		double kNat = DecaySolver.kForDecel(omegaS, omegaD, T, decelS, K_MIN, K_MAX);
		double thNat = DecaySolver.theta(kNat, omegaS, omegaD, T);
		double thA = DecaySolver.theta(K_MAX, omegaS, omegaD, T);
		double thB = DecaySolver.theta(K_MIN, omegaS, omegaD, T);
		double thMin = Math.min(thA, thB);
		double thMax = Math.max(thA, thB);
		// keep 0.5 % of the reach off the bounds (the bisection then never sits on a bound)
		double margin = 0.005 * (thMax - thMin);
		thMin += margin;
		thMax -= margin;
		double tc = td + tsp;
		double phi = spiralDistance(1, tsp);
		double rotC = rotor.angle(tc);
		// progress coordinate u = σ·rel/STEP at contact = base + (θ + Φ)/STEP
		double base = sigma * (ballS - rotC) / STEP;
		double uMin = base + (thMin + phi) / STEP;
		double uMax = base + (thMax + phi) / STEP;
		double uNat = base + (thNat + phi) / STEP;
		int idx = Wheel.wheelIndex(result);
		int res = Math.floorMod(sigma * idx, Wheel.POCKETS);
		double sHi = wide ? sLo + SCATTER_W + Wheel.POCKETS : sLo + SCATTER_W;
		double sMaxIn = sHi - 0.02;
		double sMinIn = sLo + 0.02;
		// candidate final pockets u_T ≡ res (mod 37) whose window [u_T − sHi, u_T − sLo] meets [uMin, uMax]
		long first = (long) Math.floor(uMin + sMinIn);
		long last = (long) Math.ceil(uMax + sMaxIn);
		double bestCost = Double.MAX_VALUE;
		double bestU = 0;
		long bestT = 0;
		for (long uT = first; uT <= last; uT++) {
			if (Math.floorMod(uT, (long) Wheel.POCKETS) != res) continue;
			double lo = Math.max(sMinIn, uT - uMax);
			double hi = Math.min(sMaxIn, uT - uMin);
			if (lo > hi) continue;
			double s = Math.max(lo, Math.min(hi, sPref));
			double uC = uT - s;
			double cost = Math.abs(s - sPref) * 3 + Math.abs(uC - uNat);
			if (cost < bestCost) {
				bestCost = cost;
				bestU = uC;
				bestT = uT;
			}
		}
		if (bestCost == Double.MAX_VALUE) return null;
		double theta = bestU * STEP - sigma * (ballS - rotC) - phi;
		DecaySolver.Solution sol = DecaySolver.solve(omegaS, omegaD, T, theta, kNat, K_MIN, K_MAX);
		if (!sol.ok()) return null;
		double pc = sigma * bestU;
		double pt = sigma * (double) bestT;
		// relative speed just after contact (pocket units / s)
		double ballSpeed = omegaD * SPIRAL_F;
		double relSpeed = (ballSpeed + Math.abs(rotor.omega(tc))) * CONTACT_KEEP / STEP;
		BounceChain ch = BounceChain.plan(pc, sigma * relSpeed, pt, BounceChain.Lattice.pockets(), params, MotionRng.of(seed, "RO_RATTLE", 0));
		double restAt = tc + ch.endMs() + SETTLE_MS;
		if (restAt > tEnd - END_MARGIN_MS + 1e-6) return null;
		return new Plan(td, sol, ch, pc, pt, wide, cc);
	}

	/** Longest the rattle can take: the draws of {@link BounceChain#plan} replayed (all drawn hops with their skids). */
	static double chainUpperBound(MotionRng rng, BounceChain.Params p) {
		int nDraw = rng.rangeInt(p.minHops(), p.maxHops());
		rng.chance(p.kickProb());
		double t1 = p.airMs() * (1 + rng.range(-p.airJitter(), p.airJitter())) * p.timeScale();
		int m = p.maxHops();
		double sum = 0;
		double air = t1;
		for (int j = 0; j < m; j++) {
			double en = rng.range(p.enMin(), p.enMax());
			rng.range(p.etMin(), p.etMax());
			double skid = p.skidMaxMs() > 0 ? rng.range(0, p.skidMaxMs()) * p.timeScale() : 0;
			if (j < nDraw) sum += air + skid;
			air *= en;
		}
		return sum;
	}

	// ---- closed forms -----------------------------------------------------------------------------------------------------

	/** Stage-A distance (deg, ≥ 0) at {@code t} (from the launch). */
	private double stageA(double t) {
		double s = Math.max(0, t - LAUNCH_MS);
		return omegaB0 * tauB / 1000.0 * -StrictMath.expm1(-s / tauB);
	}

	private double stageASpeed(double t) {
		return omegaB0 * StrictMath.exp(-Math.max(0, t - LAUNCH_MS) / tauB);
	}

	/** Spiral distance (deg) at fraction x: ω_D·T·(x + (f − 1)·x³/3). */
	private double spiralDistance(double x) {
		return spiralDistance(x, tsp);
	}

	private double spiralDistance(double x, double tsp) {
		return omegaD * tsp / 1000.0 * (x + (SPIRAL_F - 1) * x * x * x / 3);
	}

	/** Absolute angle on the spiral (without the deflector dip) at fraction x. */
	private double spiralAngle(double x) {
		return ballD + sigma * spiralDistance(x);
	}

	/** The spiral fraction where the absolute angle reaches {@code target} (−1 when outside [0, 1]). */
	private double solveSpiralX(double target) {
		double want = sigma * (target - ballD);
		if (want < 0 || want > spiralDistance(1)) return -1;
		double lo = 0;
		double hi = 1;
		for (int i = 0; i < 60; i++) {
			double mid = 0.5 * (lo + hi);
			if (spiralDistance(mid) < want) lo = mid;
			else hi = mid;
		}
		return 0.5 * (lo + hi);
	}

	/** Radius on the track up to the drop (launch fling, wall wobble, hunting). */
	private double huntR(double t) {
		double s = t - LAUNCH_MS;
		if (s < FLING_MS) {
			double x = Math.max(0, s / FLING_MS);
			if (x < 0.6) {
				double y = x / 0.6;
				return R_LAUNCH + (R_WALL - R_LAUNCH) * y * y;
			}
			if (x < 0.8) return R_WALL - 0.006 * Math.abs(DetMath.sin(Math.PI * (x - 0.6) / 0.2));
			return R_WALL - 0.0024 * Math.abs(DetMath.sin(Math.PI * (x - 0.8) / 0.2));
		}
		double w = s - FLING_MS;
		double base = 0.002 * DetMath.sin(2 * Math.PI * 7 * w / 1000);
		if (!solved || t < tH) return R_WALL + base;
		double h = Math.min(1, (t - tH) / Math.max(1, tD - tH));
		double ease = h * h * (3 - 2 * h);
		return R_WALL - (R_WALL - R_HUNT) * ease + base * (1 - h) + 0.004 * h * DetMath.sin(2 * Math.PI * 9 * (t - tH) / 1000);
	}

	private double settleSwing(double s) {
		if (s <= 0 || s >= SETTLE_MS) return 0;
		return 0.26 * StrictMath.exp(-s / 70) * DetMath.sin(2 * Math.PI * s / 110) * (1 - s / SETTLE_MS);
	}

	// ---- sampling ----------------------------------------------------------------------------------------------------------

	/** Rotor angle at {@code tMs}. */
	public double rotor(double tMs) {
		return rotor.angle(tMs);
	}

	/**
	 * Samples the spin at {@code tMs} (ms since the launch) into {@code out}. Pure in t and allocation-free (a
	 * {@code scratch} array is reused, so one spin must not be sampled from two threads at once).
	 */
	public Sample sample(double tMs, Sample out) {
		double t = Math.max(0, tMs);
		double rot = rotor.angle(t);
		out.rotor = rot;
		out.frame = FRAME_NORMAL;
		out.alpha = 1;
		out.visible = true;
		out.z = 0;
		if (t < LAUNCH_MS) {
			// pickup: the old ball lifts out of its pocket and fades; then the dealer's hand hides it
			out.rel = lastResult >= 0 ? RouletteBallPath.pocketAngle(lastResult) : 0;
			out.abs = rot + out.rel;
			out.r = R_POCKET;
			out.omega = Math.abs(rotor.omega(t));
			if (lastResult < 0 || t >= PICKUP_FADE_MS) {
				out.visible = false;
				out.alpha = 0;
				out.phase = PH_HIDDEN;
				out.z = 3;
			} else {
				double x = t / PICKUP_FADE_MS;
				out.z = 3 * x * x;
				out.alpha = 1 - x * x;
				out.phase = PH_PICKUP;
				out.frame = FRAME_POCKET;
			}
			return out;
		}
		if (!solved || t < tD) {
			// stage A is the same closed form in every build (bit-identical before the SPIN start); stage B on its Track
			if (orbit != null && t >= tS) {
				double tt = Math.min(t, tD);
				out.abs = ballS + sigma * orbit.sample(tt, 0);
				out.omega = Math.abs(orbit.velocity(tt, 0));
			} else {
				out.abs = phiL + sigma * stageA(t);
				out.omega = stageASpeed(t);
			}
			out.rel = out.abs - rot;
			out.r = huntR(t);
			double s = t - LAUNCH_MS;
			out.phase = s < FLING_MS ? PH_LAUNCH : solved && t >= tH ? PH_HUNT : PH_ORBIT;
			if (s < FLING_MS) out.z = 3 * (1 - s / FLING_MS) * (1 - s / FLING_MS);
			return out;
		}
		if (t < tC) {
			double u = t - tD;
			double x = u / tsp;
			double ang = spiralAngle(x);
			double r;
			double z = 1.5 * 4 * x * (1 - x);
			if (!deflector) {
				r = rD - (rD - R_LIP) * x * x; // one constant-acceleration fall down the stator
			} else if (x < xHit) {
				double y = x / xHit;
				r = rD - (rD - R_DIAMOND) * y * y;
			} else {
				double y = (x - xHit) / (1 - xHit);
				r = R_DIAMOND - (R_DIAMOND - R_LIP) * y * y;
				{
					double v = u - xHit * tsp;
					double win = (1 - xHit) * tsp;
					if (v < popMs) r += 0.035 * DetMath.sin(Math.PI * v / popMs);
					else if (v < popMs * 1.4) r += 0.014 * DetMath.sin(Math.PI * (v - popMs) / (0.4 * popMs));
					if (v < hopMs) z += 4 * DetMath.sin(Math.PI * v / hopMs);
					ang -= sigma * dipA * v * StrictMath.exp(-v / dipTau) * Math.max(0, 1 - v / win);
				}
			}
			out.abs = ang;
			out.rel = ang - rot;
			out.r = r;
			out.z = z;
			out.omega = omegaD * (1 + (SPIRAL_F - 1) * x * x);
			out.phase = PH_SPIRAL;
			out.frame = hitFrame(t);
			return out;
		}
		if (t < tR) {
			chain.sample(t - tC, scratch);
			double p = scratch[0];
			out.rel = p * STEP;
			out.abs = rot + out.rel;
			out.z = scratch[1];
			out.r = rattleR(t - tC);
			out.omega = Math.abs(scratch[2] * STEP + rotor.omega(t));
			out.phase = PH_RATTLE;
			out.frame = hitFrame(t);
			if (out.frame == FRAME_NORMAL && out.r < R_POCKET_RIM && out.z < 1) out.frame = FRAME_POCKET;
			return out;
		}
		double s = t - tR;
		double p = pT + sigma * settleSwing(s);
		out.rel = p * STEP;
		out.abs = rot + out.rel;
		out.r = R_POCKET;
		out.omega = Math.abs(rotor.omega(t));
		out.phase = t < rest ? PH_SETTLE : PH_REST;
		out.frame = hitFrame(t);
		if (out.frame == FRAME_NORMAL) out.frame = FRAME_POCKET;
		return out;
	}

	private double rattleR(double ms) {
		int n = chain.hops();
		for (int j = 0; j < n; j++) {
			if (ms >= chain.landMs(j)) continue;
			double from = j == 0 ? R_LIP : landR[j - 1];
			if (ms < chain.takeoffMs(j)) return from;
			double v = (ms - chain.takeoffMs(j)) / chain.airMs(j);
			double r = from + (landR[j] - from) * v;
			// a hop arcs slightly outward over the frets
			return r + 0.02 * DetMath.sin(Math.PI * v) * Math.min(1, chain.airMs(j) / 120);
		}
		return landR[n - 1];
	}

	private int hitFrame(double t) {
		for (double h : hitTimes) {
			if (t >= h && t < h + HIT_FLASH_MS) return FRAME_HIT;
			if (h > t) break;
		}
		return FRAME_NORMAL;
	}

	/** The pocket (number) the ball lies in at {@code tMs}, or -1 when it is above the frets / on the track. */
	public int pocketUnder(double tMs) {
		Sample s = sample(tMs, new Sample());
		if (s.r > R_POCKET_RIM || s.z > 1 || !s.visible) return -1;
		double p = s.rel / STEP;
		if (Math.abs(p - Math.floor(p) - 0.5) < 0.12) return -1; // on top of a fret, between two pockets
		int k = (int) Math.floorMod(Math.round(p), (long) Wheel.POCKETS);
		return Wheel.ORDER.get(k);
	}

	// ---- accessors -----------------------------------------------------------------------------------------------------------

	/** Whether this spin knows its result (built after the draw). */
	public boolean solved() {
		return solved;
	}

	/** Whether the solver had to widen the scatter window (never in the tested sweep). */
	public boolean usedFallback() {
		return fallback;
	}

	public MotionEvent[] events() {
		return events;
	}

	public int launchMs() {
		return LAUNCH_MS;
	}

	public double spinStartMs() {
		return tS;
	}

	public double spinEndMs() {
		return tEnd;
	}

	public double huntMs() {
		return tH;
	}

	public double dropMs() {
		return tD;
	}

	public double contactMs() {
		return tC;
	}

	public double rattleEndMs() {
		return tR;
	}

	public double restMs() {
		return rest;
	}

	/** Whether the ball is at rest (riding its pocket) at {@code tMs}. */
	public boolean settled(double tMs) {
		return solved && tMs >= rest;
	}

	public boolean deflectorHit() {
		return deflector;
	}

	/** Diamond index (0..7, at 22.5° + k·45°) of the deflector hit, -1 = none. */
	public int diamond() {
		return diamond;
	}

	public double deflectorMs() {
		return deflector ? tD + xHit * tsp : Double.NaN;
	}

	public double scatter() {
		return scatter;
	}

	public double contactPocket() {
		return pC;
	}

	public double finalPocket() {
		return pT;
	}

	public @org.jspecify.annotations.Nullable BounceChain rattle() {
		return chain;
	}

	public DecaySolver.@org.jspecify.annotations.Nullable Solution stageB() {
		return stageB;
	}

	/** Tail time scale c (1 = the default spin; 0.5–0.6 for the shortest spins). */
	public double tailScale() {
		return tailScale;
	}

	/** Natural drop-time stage A speed at the SPIN start (deg/s). */
	public double omegaAtSpinStart() {
		return stageASpeed(tS);
	}

	/** Absolute ball distance covered by stage A at the SPIN start (deg). */
	public double thetaAtSpinStart() {
		return thetaS;
	}

	public double omegaDrop() {
		return omegaD;
	}

	/** Head-relative angle of pocket {@code n} (pocket 0 at 0°, clockwise in wheel order). */
	public static double pocketAngle(int n) {
		return RouletteBallPath.pocketAngle(n);
	}

	/** Wraps an angle to [0, 360). */
	public static double wrap360(double a) {
		double x = a % 360;
		return x < 0 ? x + 360 : x;
	}
}

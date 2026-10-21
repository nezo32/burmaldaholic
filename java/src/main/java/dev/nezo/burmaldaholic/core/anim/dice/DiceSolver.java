package dev.nezo.burmaldaholic.core.anim.dice;

import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import dev.nezo.burmaldaholic.core.anim.motion.MotionRng;
import java.util.ArrayList;
import java.util.List;

/**
 * Contacts-first dice solver (PHASE2B §3.1, §3.3; replaces DI's 1-ms forward rigid-body simulation). Pure,
 * deterministic and identical on the server and every client: the result depends only on
 * {@code (tray, spec, seed, targetMs)}; the faces only choose the relabelling element at the very end.
 *
 * <p><b>Plan per die</b> (all decisions from {@code MotionRng.of(seed, "DSIM", attempt, die + 1)}):
 * <ol>
 *   <li>Path: release P0 → 0–2 banks → rest point R. The first leg leaves along the seeded throw direction
 *       ({@code dir ± [coneMin, coneMax]}); each bank is the first obstacle on the leg (rail, divider, bumper; the
 *       {@code mustHit} wall must be the first bank when the tray has one), reflected about an effective normal
 *       jittered within the wall's range (rail ±15°, pyramids ±35°, bumper ±25°), speed × the restitution. The last
 *       leg is chosen so that it crosses the rest zone and R is seeded on it, away from keep-outs, walls (R_PLANE + the
 *       rock's lean) and the dice already placed ({@code restSep}, Chebyshev). Banks happen in the air.</li>
 *   <li>Vertical: a release flight from {@code z0} with {@code vz0}, then 1–3 floor bounces with e_f 0.32–0.45 (hop
 *       apexes strictly decrease, one g per tray); horizontal speed × 0.78–0.88 per landing.</li>
 *   <li>Ground: a settle to flat aligned with the travel direction (≈100 ms, zLow = 0), 1–3 quarter-rolls over the
 *       leading edge (the centre rises to √2 at 45°; speed × 0.8 per roll; the last one teeters with p = 0.1), a
 *       slide at constant deceleration a while the yaw squares up to 90°, a damped rock (12–18°), REST at an exact
 *       {@link CubeGroup} element on R.</li>
 *   <li>Timing: the distance equation is quadratic in the launch speed v0 (every speed scales with v0, every airtime
 *       is fixed by the vertical plan), so for a deceleration a, v0 is closed-form. {@code targetMs} is met by
 *       bisecting a in [0.10 g, 0.40 g], then trying other roll counts, then other bounce apexes. No time scaling.</li>
 * </ol>
 * An attempt is accepted when every die meets the preferences (launch speed in the spec range, rolls have speed, the
 * slide is long enough to square up, landings avoid keep-outs, dice stay ≥ {@link #DIE_GAP} apart near the floor).
 * After {@link #ATTEMPTS} failed attempts the loop runs again with relaxed preferences; the hard guarantees (exact
 * faces, rest pose and point, exact target, no penetration, one g) hold in every pass.
 */
public final class DiceSolver {
	/** {@code targetMs} value: no target, the roll rests at its natural time. */
	public static final int NATURAL = -1;
	/** Hard cap of seeded attempts before the deterministic fallback. */
	public static final int ATTEMPTS = 16;
	/** Minimum separation of two dice centres whenever both are near the floor (see {@link #NEAR_FLOOR}). */
	public static final double DIE_GAP = 2 * DiceTray.R_PLANE;
	/** "Near the floor" for the die–die separation rule: both lowest points below this. */
	public static final double NEAR_FLOOR = 0.35;
	/** Speed kept by each quarter-roll. */
	static final double TIP_KEEP = 0.8;
	/** Speed kept by a teetering roll. */
	static final double TEETER_KEEP = 0.4;
	/** How far the rock leans the centre past R (u). */
	static final double ROCK_LEAN = 0.4;
	static final int MAX_BANKS = 2;

	private DiceSolver() {}

	/**
	 * Solves one throw of {@code faces.length} dice on {@code tray}.
	 *
	 * @param tray     where the dice land
	 * @param spec     how they leave the cup / hand / chest
	 * @param seed     cosmetic seed (public data, never CasinoRng)
	 * @param faces    the server's faces (1–6) per die; only the relabelling reads them
	 * @param targetMs rest time of the last die on the roll clock (0 = die 0 released), or {@link #NATURAL}
	 */
	public static DiceRoll solve(DiceTray tray, ThrowSpec spec, int seed, int[] faces, int targetMs) {
		int n = faces.length;
		if (n < 1 || n > 4) throw new IllegalArgumentException("dice " + n);
		for (int f : faces) if (f < 1 || f > 6) throw new IllegalArgumentException("face " + f);
		if (targetMs != NATURAL && targetMs < 500) throw new IllegalArgumentException("targetMs " + targetMs);
		SOLVES.incrementAndGet();
		Ctx ctx = new Ctx(tray);
		int a = 0;
		for (int pass = 0; pass < PASS_ATTEMPTS.length; pass++) {
			for (int k = 0; k < PASS_ATTEMPTS[pass]; k++, a++) {
				DiePlan[] plans = attempt(ctx, spec, seed, n, targetMs, a, pass);
				if (plans != null) return build(ctx, plans, seed, faces, a, pass > 0);
			}
		}
		// deterministic fallback after the capped attempts: first the most permissive targeted plan (a brush between the
		// dice is accepted), then the natural pace with the last die held in its rest pose until the target
		for (int k = 0; k < FALLBACK_ATTEMPTS; k++, a++) {
			DiePlan[] plans = attempt(ctx, spec, seed, n, targetMs, a, 3);
			if (plans != null) return build(ctx, plans, seed, faces, a, true);
		}
		for (int k = 0; k < FALLBACK_ATTEMPTS; k++, a++) {
			DiePlan[] plans = attempt(ctx, spec, seed, n, NATURAL, a, 3);
			if (plans == null) continue;
			if (targetMs != NATURAL) {
				int last = 0;
				double end = -1;
				for (DiePlan p : plans) {
					double e = naturalEnd(p);
					if (e > end) {
						end = e;
						last = p.die;
					}
				}
				if (end <= targetMs) plans[last].tTarget = targetMs;
			}
			return build(ctx, plans, seed, faces, a, true);
		}
		return scripted(ctx, spec, seed, faces, targetMs, a);
	}

	/** Release stagger (ms) and landing spacing before the target (ms) of the scripted drop. */
	static final double DROP_STAGGER = 40;
	static final double DROP_SPACING = 120;
	/** Rest time of the last die of a scripted drop without a target (a plausible throw length). */
	static final int SCRIPTED_NATURAL_MS = 1000;

	/**
	 * The last plan, which cannot fail: each die is HELD above a fixed, separated rest spot, drops straight down with no
	 * spin and RESTs there as an exact {@link CubeGroup} element (relabelled to its face). The spots are ≥
	 * {@link #DIE_GAP} apart and the motion is vertical, so the dice never overlap; the last die rests exactly at
	 * {@code targetMs}.
	 */
	static DiceRoll scripted(Ctx ctx, ThrowSpec spec, int seed, int[] faces, int targetMs, int attempt) {
		int n = faces.length;
		double g = ctx.tray.g();
		double h = Math.max(1, spec.z0());
		double dropMs = StrictMath.sqrt(2 * h / g) * 1000;
		DiePlan[] placed = new DiePlan[n];
		double[][] spot = new double[n][];
		for (int i = 0; i < n; i++) {
			spot[i] = restSpot(ctx, i, placed, spot);
			DiePlan p = new DiePlan();
			p.die = i;
			p.rx = spot[i][0];
			p.ry = spot[i][1];
			placed[i] = p;
		}
		Seg[][] segs = new Seg[n][];
		double[][] label = new double[n][];
		double[] restT = new double[n];
		double[] relT = new double[n];
		double[] wallT = new double[n];
		boolean[] teeter = new boolean[n];
		List<MotionEvent> ev = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			int target = targetMs == NATURAL ? SCRIPTED_NATURAL_MS : targetMs;
			double land = Math.max(DROP_STAGGER * i + dropMs, target - DROP_SPACING * (n - 1 - i));
			if (i == n - 1) land = Math.max(land, target);
			double rel = land - dropMs;
			MotionRng r = MotionRng.of(seed, "DFALL", i);
			double[] q = CubeGroup.element(r.nextInt(24)).clone();
			double x = spot[i][0];
			double y = spot[i][1];
			Seg held = new Seg();
			held.kind = DieSample.HELD;
			held.t0 = Double.NEGATIVE_INFINITY;
			held.t1 = rel;
			held.x0 = x;
			held.y0 = y;
			held.z0 = h;
			Quat.copy(q, held.q0);
			Seg fall = air(rel, dropMs / 1000.0, x, y, 0, 0, h, 0, q, 0, 0, 0);
			fall.t1 = land;
			Seg rest = new Seg();
			rest.kind = DieSample.REST;
			rest.t0 = land;
			rest.t1 = Double.POSITIVE_INFINITY;
			rest.x0 = x;
			rest.y0 = y;
			Quat.copy(q, rest.q0);
			segs[i] = new Seg[] {held, fall, rest};
			restT[i] = land;
			relT[i] = rel;
			wallT[i] = -1;
			ev.add(new MotionEvent(rel, DiceEvent.RELEASE, i, 0.5f, x, y, 0));
			ev.add(new MotionEvent(land, DiceEvent.FLOOR, i, strength(StrictMath.sqrt(2 * g * h)), x, y, 1));
			ev.add(new MotionEvent(land, DiceEvent.REST, i, 0.3f, x, y, 0));
			int k = MotionRng.of(seed, "DLABEL", i).nextInt(4);
			label[i] = CubeGroup.element(CubeGroup.relabel(CubeGroup.faceUp(q), faces[i], k)).clone();
		}
		double[][] rest = new double[n][];
		for (int i = 0; i < n; i++) rest[i] = spot[i].clone();
		MotionEvent[] arr = MotionEvent.sort(ev.toArray(new MotionEvent[0]));
		return new DiceRoll(segs, label, faces.clone(), restT, relT, wallT, rest, arr, g, attempt, true, teeter);
	}

	/**
	 * A rest spot for die {@code i}: the first point of a fixed grid over its rest zone (nearest the zone centre first)
	 * that passes {@link #restOk} and is ≥ {@link #DIE_GAP} + 0.1 from every earlier spot; failing that, on the zone's
	 * centre line {@code DIE_GAP + 0.5} right of every earlier spot.
	 */
	static double[] restSpot(Ctx ctx, int i, DiePlan[] placed, double[][] spot) {
		DiceTray.Box z = ctx.tray.restZone(i);
		double cx = 0.5 * (z.x0() + z.x1());
		double cy = 0.5 * (z.y0() + z.y1());
		List<double[]> grid = new ArrayList<>();
		for (int u = 0; u <= 8; u++)
			for (int v = 0; v <= 4; v++) grid.add(new double[] {z.x0() + z.width() * u / 8.0, z.y0() + z.height() * v / 4.0});
		grid.sort((p, q) -> Double.compare(StrictMath.hypot(p[0] - cx, p[1] - cy), StrictMath.hypot(q[0] - cx, q[1] - cy)));
		for (double[] c : grid) {
			if (!restOk(ctx, c[0], c[1], placed, i)) continue;
			boolean far = true;
			for (int j = 0; j < i; j++) far &= StrictMath.hypot(c[0] - spot[j][0], c[1] - spot[j][1]) >= DIE_GAP + 0.1;
			if (far) return c;
		}
		double x = cx;
		for (int j = 0; j < i; j++) x = Math.max(x, spot[j][0] + DIE_GAP + 0.5);
		return new double[] {x, cy};
	}

	/** Seeded attempts per pass (strict, relaxed, very relaxed): {@link #ATTEMPTS} in all. */
	static final int[] PASS_ATTEMPTS = {5, 6, 5};
	/** Deterministic natural-pace fallback attempts after the capped ones. */
	static final int FALLBACK_ATTEMPTS = 2;
	/** Number of {@link #solve} calls (tests prove that sampling never solves). */
	static final java.util.concurrent.atomic.AtomicLong SOLVES = new java.util.concurrent.atomic.AtomicLong();

	private static double naturalEnd(DiePlan p) {
		return p.segs[p.segs.length - 1].t0;
	}

	// ---- per-tray cache ---------------------------------------------------------------------------------------------
	static final class Ctx {
		final DiceTray tray;
		final DiceTray.Wall[] walls;
		final DiceTray.Bumper[] bumpers;
		final boolean mustHit;
		final boolean bankable;

		Ctx(DiceTray t) {
			tray = t;
			walls = t.allWalls().toArray(new DiceTray.Wall[0]);
			bumpers = t.bumpers().toArray(new DiceTray.Bumper[0]);
			boolean m = false;
			for (DiceTray.Wall w : walls) m |= w.mustHit();
			mustHit = m;
			bankable = !t.walls().isEmpty() || !t.bumpers().isEmpty();
		}
	}

	/** Everything needed to build one die's segments. */
	static final class DiePlan {
		int die;
		double tRel, tTarget;
		double z0;
		// path: vertex 0 = P0, 1..banks = contacts, banks + 1 = R
		int banks;
		final double[] vx = new double[MAX_BANKS + 2];
		final double[] vy = new double[MAX_BANKS + 2];
		final double[] lx = new double[MAX_BANKS + 1];
		final double[] ly = new double[MAX_BANKS + 1];
		final double[] cum = new double[MAX_BANKS + 2];
		final int[] bKind = new int[MAX_BANKS]; // 0 wall, 1 bumper
		final int[] bIdx = new int[MAX_BANKS];
		final double[] bE = new double[MAX_BANKS];
		final double[] bNx = new double[MAX_BANKS];
		final double[] bNy = new double[MAX_BANKS];
		/** Unit-speed travel (before retentions) at which bank j is reached. */
		final double[] raw = new double[MAX_BANKS];
		double eTot = 1;
		double rx, ry, dist;
		// the last leg: R = (lastX, lastY) + rt·(lastDx, lastDy); rt ∈ [zt0, zt1] stays in the rest zone
		double lastX, lastY, lastDx, lastDy, zt0, zt1, rt;
		DiePlan[] placed;
		/** Canonical segments, built when the die is accepted (for the separation check of later dice). */
		Seg[] segs;
		/** Time and place of this die's brush with an earlier die (−1: none). */
		double clackT = -1, clackX, clackY;
		// vertical
		double vz0, ef, landV;
		int nb;
		double[] flightT; // seconds: release flight, then hops
		final double[] mu = new double[4]; // per landing
		// ground
		double tSet; // s
		/** Speed kept through the settle (the flat landing absorbs the rest), 0.3–1. */
		double kSet = 1;
		int tips;
		boolean teeter;
		int teeterSign;
		double teeterHold; // ms
		double tRock; // ms
		double rockAmp; // rad
		// solution
		double v0, a;
		// release state
		final double[] qRel = new double[4];
		double wx, wy, wz;

		double p0x() {
			return vx[0];
		}

		double p0y() {
			return vy[0];
		}
	}

	// ---- attempt ----------------------------------------------------------------------------------------------------
	static DiePlan[] attempt(Ctx ctx, ThrowSpec spec, int seed, int n, int targetMs, int attempt, int pass) {
		MotionRng rng = MotionRng.of(seed, "DSIM", attempt);
		double[] tRel = new double[n];
		double[] tTarget = new double[n];
		for (int i = 1; i < n; i++) tRel[i] = tRel[i - 1] + rng.range(spec.delayMin(), spec.delayMax());
		for (int i = 0; i < n; i++) tTarget[i] = targetMs == NATURAL ? -1 : (i == n - 1 ? targetMs : targetMs - rng.range(60, 240) * (n - 1 - i));
		DiePlan[] plans = new DiePlan[n];
		double perpX = -StrictMath.sin(spec.dir());
		double perpY = StrictMath.cos(spec.dir());
		for (int i = 0; i < n; i++) {
			MotionRng r = MotionRng.of(seed, "DSIM", attempt, i + 1);
			DiePlan p = new DiePlan();
			p.die = i;
			p.tRel = tRel[i];
			p.tTarget = tTarget[i];
			p.vx[0] = spec.x() + perpX * spec.side() * i;
			p.vy[0] = spec.y() + perpY * spec.side() * i;
			p.z0 = spec.z0();
			p.placed = plans;
			if (!planDie(ctx, spec, r, p, plans, pass)) return null;
			plans[i] = p;
		}
		return plans;
	}

	static boolean planDie(Ctx ctx, ThrowSpec spec, MotionRng r, DiePlan p, DiePlan[] placed, int pass) {
		// fixed stream order: vertical, ground, spin, then geometry draws
		p.vz0 = r.range(spec.vzMin(), spec.vzMax());
		p.ef = r.range(0.32, 0.45);
		p.nb = 1 + r.weighted(1, 2, 1);
		for (int k = 0; k < 4; k++) p.mu[k] = r.range(0.78, 0.88);
		p.tSet = r.range(0.09, 0.11);
		int tipPref = 1 + r.weighted(1, 2, 1);
		p.teeter = r.chance(0.2);
		p.teeterSign = r.sign();
		p.teeterHold = r.range(140, 240);
		p.tRock = r.range(110, 150);
		p.rockAmp = Math.toRadians(r.range(12, 18));
		uniformQ(r, p.qRel);
		double spin = r.range(spec.spinMin(), spec.spinMax());
		double spinSign = r.sign();
		double ax = r.range(-0.35, 0.35);
		double ay = r.range(-0.35, 0.35);
		double az = r.range(-0.35, 0.35);
		double vzBase = p.vz0;
		double efBase = p.ef;
		int nbBase = p.nb;
		double g = ctx.tray.g();
		List<Path> cand = new ArrayList<>();
		DiceTray.Box zone = ctx.tray.restZone(p.die);
		for (int tryN = 0; tryN < (p.die == 0 ? 4 : 10); tryN++) {
			double ux = r.nextDouble();
			double ry = zone.y0() + zone.height() * r.nextDouble();
			double rx = zone.x0() + zone.width() * ux;

			int jitterSeed = (int) r.nextU32();
			if (!restOk(ctx, rx, ry, placed, p.die)) {
				continue;
			}
			cand.clear();
			paths(ctx, p.vx[0], p.vy[0], rx, ry, cand);
			if (cand.isEmpty()) {
				continue;
			}
			// score: throw direction inside the cone, natural time close to the target, launch speed, a seeded jitter
			vertical(p, vzBase, efBase, nbBase, g);
			MotionRng jr = MotionRng.of(jitterSeed, "DPATH", 0);
			double aN = 0.22 * g;
			int tn = Math.min(tipPref, 2);
			for (Path c : cand) {
				setPath(p, c);
				double dev = Math.abs(StrictMath.IEEEremainder(StrictMath.atan2(p.ly[0], p.lx[0]) - spec.dir(), 2 * Math.PI));
				double cone = Math.max(0, dev - spec.coneMax() - Math.toRadians(4)) / Math.toRadians(10);
				double v0 = v0For(p, tn, aN, airM(p), groundFactor(p));
				double tNat = v0 > 0 ? restTime(p, tn, v0, aN, groundFactor(p)) : Double.NaN;
				double speed = v0 > 0 ? Math.max(0, spec.speedMin() * 0.8 - v0) / 4 + Math.max(0, v0 - spec.speedMax() * 1.2) / 8 : 3;
				double time = p.tTarget >= 0 ? (Double.isNaN(tNat) ? 4 : Math.abs(tNat - p.tTarget) / 150) : 0;
				// a later die never rolls through the spot where an earlier die already rests
				double same = 0;
				for (int j = 0; j < p.die; j++)
					if (segDist(c.x[c.banks], c.y[c.banks], c.x[c.banks + 1], c.y[c.banks + 1], placed[j].rx, placed[j].ry) < DIE_GAP + 0.1) same += 8;
				c.score = cone * 2 + speed + time + same + jr.range(0, 0.6) + (c.banks == 0 && ctx.bankable ? 0.4 : 0);
			}
			cand.sort((u, v) -> Double.compare(u.score, v.score));
			for (int k = 0; k < Math.min(4, cand.size()); k++) {
				Path c = cand.get(k);
				if (pass == 0 && c.score > 6) break;
				setPath(p, c);
				zoneInterval(ctx, p);
				vertical(p, vzBase, efBase, nbBase, g);
				// spin: biased horizontal, perpendicular to the first leg (top/back spin)
				double sx = -p.ly[0] * spinSign + ax;
				double sy = p.lx[0] * spinSign + ay;
				double sn = Math.sqrt(sx * sx + sy * sy + az * az);
				p.wx = sx / sn * spin;
				p.wy = sy / sn * spin;
				p.wz = az / sn * spin;
				if (!timing(ctx, spec, p, tipPref, pass)) continue;
				p.segs = buildSegs(ctx, p, null);
				// the last-resort pass accepts a closer brush between the dice, never a penetration
				if (clearOf(ctx, p, placed, pass == 3)) return true;
			}
		}
		return false;
	}

	/** One candidate path P0 → 0–2 specular banks → R. */
	static final class Path {
		int banks;
		final double[] x = new double[MAX_BANKS + 2];
		final double[] y = new double[MAX_BANKS + 2];
		final int[] kind = new int[MAX_BANKS];
		final int[] idx = new int[MAX_BANKS];
		final double[] nx = new double[MAX_BANKS];
		final double[] ny = new double[MAX_BANKS];
		final double[] e = new double[MAX_BANKS];
		double score;
	}

	/** Copies a path into the plan (vertices, legs, cumulative and unit-speed distances, retentions). */
	static void setPath(DiePlan p, Path c) {
		p.banks = c.banks;
		p.cum[0] = 0;
		double eAcc = 1;
		double raw = 0;
		for (int j = 0; j <= c.banks; j++) {
			double dx = c.x[j + 1] - c.x[j];
			double dy = c.y[j + 1] - c.y[j];
			double len = StrictMath.hypot(dx, dy);
			p.vx[j] = c.x[j];
			p.vy[j] = c.y[j];
			p.lx[j] = dx / len;
			p.ly[j] = dy / len;
			p.cum[j + 1] = p.cum[j] + len;
			if (j < c.banks) {
				raw += len / eAcc;
				p.raw[j] = raw;
				p.bKind[j] = c.kind[j];
				p.bIdx[j] = c.idx[j];
				p.bE[j] = c.e[j];
				p.bNx[j] = c.nx[j];
				p.bNy[j] = c.ny[j];
				eAcc *= c.e[j];
			}
		}
		p.vx[c.banks + 1] = c.x[c.banks + 1];
		p.vy[c.banks + 1] = c.y[c.banks + 1];
		p.eTot = eAcc;
		p.rx = c.x[c.banks + 1];
		p.ry = c.y[c.banks + 1];
		p.dist = p.cum[c.banks + 1];
		p.lastX = c.x[c.banks];
		p.lastY = c.y[c.banks];
		p.lastDx = p.lx[c.banks];
		p.lastDy = p.ly[c.banks];
		p.rt = p.dist - p.cum[c.banks];
		p.zt0 = p.rt;
		p.zt1 = p.rt;
	}

	/** The interval of the last leg inside the rest zone and before the next obstacle (needs ctx; after setPath). */
	static void zoneInterval(Ctx ctx, DiePlan p) {
		DiceTray.Box z = ctx.tray.restZone(p.die);
		double t0 = 0.8;
		double t1 = Double.POSITIVE_INFINITY;
		double[] lo = {z.x0() - p.lastX, z.y0() - p.lastY};
		double[] hi = {z.x1() - p.lastX, z.y1() - p.lastY};
		double[] d = {p.lastDx, p.lastDy};
		for (int i = 0; i < 2; i++) {
			if (Math.abs(d[i]) < 1e-12) {
				if (lo[i] > 0 || hi[i] < 0) {
					t0 = 1;
					t1 = 0;
				}
				continue;
			}
			double a = lo[i] / d[i];
			double b = hi[i] / d[i];
			t0 = Math.max(t0, Math.min(a, b));
			t1 = Math.min(t1, Math.max(a, b));
		}
		double[] next = cast(ctx, p.lastX, p.lastY, p.lastDx, p.lastDy);
		if (next != null) t1 = Math.min(t1, next[0] - 0.05);
		// shrink to the stretch around the seeded R where R is a valid rest point (keep-outs, walls, other dice)
		double step = 0.2;
		double a = p.rt;
		while (a - step >= t0 && restAt(ctx, p, a - step)) a -= step;
		double b = p.rt;
		while (b + step <= t1 && restAt(ctx, p, b + step)) b += step;
		p.zt0 = a;
		p.zt1 = b;
	}

	static boolean restAt(Ctx ctx, DiePlan p, double t) {
		double rx = p.lastX + p.lastDx * t;
		double ry = p.lastY + p.lastDy * t;
		int skip = p.banks > 0 && p.bKind[p.banks - 1] == 1 ? p.bIdx[p.banks - 1] : -1;
		return restOk(ctx, rx, ry, p.placed, p.die) && legClear(ctx, p.lastX, p.lastY, rx, ry, skip);
	}

	static void setR(DiePlan p, double t) {
		p.rt = t;
		p.rx = p.lastX + p.lastDx * t;
		p.ry = p.lastY + p.lastDy * t;
		p.vx[p.banks + 1] = p.rx;
		p.vy[p.banks + 1] = p.ry;
		p.cum[p.banks + 1] = p.cum[p.banks] + t;
		p.dist = p.cum[p.banks + 1];
	}

	/**
	 * Every valid specular path from P0 to R: direct, one bank (each wall by the mirror image of R, the bumper by a
	 * root search on its circle) and two wall banks (double mirror image). A path is valid when each leg reaches its
	 * contact before any other obstacle, the last leg reaches R unobstructed, and the first bank is the mustHit wall
	 * when the tray has one.
	 */
	static void paths(Ctx ctx, double px, double py, double rx, double ry, List<Path> out) {
		int nw = ctx.walls.length;
		if (!ctx.mustHit) {
			Path d = new Path();
			d.banks = 0;
			d.x[0] = px;
			d.y[0] = py;
			d.x[1] = rx;
			d.y[1] = ry;
			if (legOk(ctx, px, py, rx, ry, -1, -1)) out.add(d);
		}
		if (!ctx.bankable && !ctx.mustHit) return;
		for (int i = 0; i < nw; i++) {
			if (ctx.mustHit && !ctx.walls[i].mustHit()) continue;
			// one bank: mirror R across wall i's contact line
			double[] r1 = mirror(ctx.walls[i], rx, ry);
			Path c = new Path();
			c.banks = 1;
			c.x[0] = px;
			c.y[0] = py;
			if (wallContact(ctx, i, px, py, r1[0], r1[1], c, 0) && legOk(ctx, px, py, c.x[1], c.y[1], 0, i) && legOk(ctx, c.x[1], c.y[1], rx, ry, -1, -1)) {
				c.x[2] = rx;
				c.y[2] = ry;
				out.add(c);
			}
			for (int k = 0; k < nw && ctx.mustHit; k++) {
				if (k == i) continue;
				double[] r2 = mirror(ctx.walls[k], rx, ry);
				double[] r21 = mirror(ctx.walls[i], r2[0], r2[1]);
				Path b = new Path();
				b.banks = 2;
				b.x[0] = px;
				b.y[0] = py;
				if (!wallContact(ctx, i, px, py, r21[0], r21[1], b, 0)) continue;
				if (!wallContact(ctx, k, b.x[1], b.y[1], r2[0], r2[1], b, 1)) continue;
				if (!legOk(ctx, px, py, b.x[1], b.y[1], 0, i) || !legOk(ctx, b.x[1], b.y[1], b.x[2], b.y[2], 0, k) || !legOk(ctx, b.x[2], b.y[2], rx, ry, -1, -1))
					continue;
				b.x[3] = rx;
				b.y[3] = ry;
				out.add(b);
			}
		}
		if (!ctx.mustHit) {
			for (int i = 0; i < ctx.bumpers.length; i++) {
				Path c = bumperPath(ctx, i, px, py, rx, ry);
				if (c != null) out.add(c);
			}
		}
	}

	/** Mirror image of (x, y) across wall w's contact line (the wall line moved R_PLANE inward). */
	static double[] mirror(DiceTray.Wall w, double x, double y) {
		double d = w.distance(x, y) - DiceTray.R_PLANE;
		return new double[] {x - 2 * d * w.nx(), y - 2 * d * w.ny()};
	}

	/** Contact of the segment (ax, ay) → (bx, by) (b = a mirrored target) with wall i's contact line; fills bank j. */
	static boolean wallContact(Ctx ctx, int i, double ax, double ay, double bx, double by, Path c, int j) {
		DiceTray.Wall w = ctx.walls[i];
		double da = w.distance(ax, ay) - DiceTray.R_PLANE;
		double db = w.distance(bx, by) - DiceTray.R_PLANE;
		if (da <= 1e-6 || db >= -1e-6) return false;
		double t = da / (da - db);
		double cx = ax + (bx - ax) * t;
		double cy = ay + (by - ay) * t;
		double L = w.length();
		double along = ((cx - w.ax()) * (w.bx() - w.ax()) + (cy - w.ay()) * (w.by() - w.ay())) / L;
		if (along < 0 || along > L) return false;
		if (!ctx.tray.floor().contains(cx, cy)) return false;
		for (DiceTray.Wall o : ctx.walls) if (o != w && o.distance(cx, cy) < DiceTray.R_PLANE - 1e-9 && onSpan(o, cx, cy)) return false;
		// the leg must arrive steeply enough to read as a bank, not a graze
		double lx = cx - ax;
		double ly = cy - ay;
		double ll = StrictMath.hypot(lx, ly);
		if (ll < 0.8 || -(lx * w.nx() + ly * w.ny()) / ll < 0.2) return false;
		c.x[j + 1] = cx;
		c.y[j + 1] = cy;
		c.kind[j] = 0;
		c.idx[j] = i;
		c.nx[j] = w.nx();
		c.ny[j] = w.ny();
		c.e[j] = Math.min(0.75, w.restitution());
		return true;
	}

	/** The leg a → b is unobstructed: its first obstacle is the expected contact (kind, idx) at b, or none before b. */
	static boolean legOk(Ctx ctx, double ax, double ay, double bx, double by, int kind, int idx) {
		double len = StrictMath.hypot(bx - ax, by - ay);
		if (len < 0.8) return false;
		double[] hit = cast(ctx, ax, ay, (bx - ax) / len, (by - ay) / len);
		if (kind < 0) return hit == null || hit[0] >= len - 1e-7;
		return hit != null && (int) hit[1] == kind && (int) hit[2] == idx && Math.abs(hit[0] - len) < 1e-6 * Math.max(1, len);
	}

	/** One bank off bumper i: the contact on its inflated circle where the normal bisects the two legs. */
	static Path bumperPath(Ctx ctx, int i, double px, double py, double rx, double ry) {
		DiceTray.Bumper b = ctx.bumpers[i];
		double rho = b.r() + DiceTray.R_PLANE;
		int steps = 24;
		double prev = Double.NaN;
		double prevTh = 0;
		for (int k = 0; k <= steps; k++) {
			double th = 2 * Math.PI * k / steps;
			double f = bisectorErr(b, rho, th, px, py, rx, ry);
			if (!Double.isNaN(f) && !Double.isNaN(prev) && f * prev <= 0) {
				double lo = prevTh;
				double hi = th;
				double flo = prev;
				for (int it = 0; it < 50; it++) {
					double mid = 0.5 * (lo + hi);
					double fm = bisectorErr(b, rho, mid, px, py, rx, ry);
					if (Double.isNaN(fm)) break;
					if (fm * flo > 0) {
						lo = mid;
						flo = fm;
					} else {
						hi = mid;
					}
				}
				double t = 0.5 * (lo + hi);
				double cx = b.cx() + rho * StrictMath.cos(t);
				double cy = b.cy() + rho * StrictMath.sin(t);
				Path c = new Path();
				c.banks = 1;
				c.x[0] = px;
				c.y[0] = py;
				c.x[1] = cx;
				c.y[1] = cy;
				c.x[2] = rx;
				c.y[2] = ry;
				c.kind[0] = 1;
				c.idx[0] = i;
				c.nx[0] = StrictMath.cos(t);
				c.ny[0] = StrictMath.sin(t);
				c.e[0] = Math.min(0.75, b.restitution());
				if (ctx.tray.floor().contains(cx, cy) && legOk(ctx, px, py, cx, cy, 1, i) && legOk(ctx, cx, cy, rx, ry, -1, -1)) {
					double ll = StrictMath.hypot(cx - px, cy - py);
					if (-((cx - px) * c.nx[0] + (cy - py) * c.ny[0]) / ll >= 0.2) return c;
				}
			}
			prev = f;
			prevTh = th;
		}
		return null;
	}

	/** Cross product of the normal at θ with the sum of the unit vectors toward P0 and R (NaN when not both visible). */
	private static double bisectorErr(DiceTray.Bumper b, double rho, double th, double px, double py, double rx, double ry) {
		double nx = StrictMath.cos(th);
		double ny = StrictMath.sin(th);
		double cx = b.cx() + rho * nx;
		double cy = b.cy() + rho * ny;
		double ux = px - cx;
		double uy = py - cy;
		double ul = StrictMath.hypot(ux, uy);
		double vx = rx - cx;
		double vy = ry - cy;
		double vl = StrictMath.hypot(vx, vy);
		ux /= ul;
		uy /= ul;
		vx /= vl;
		vy /= vl;
		if (ux * nx + uy * ny <= 0.05 || vx * nx + vy * ny <= 0.05) return Double.NaN;
		double sx = ux + vx;
		double sy = uy + vy;
		return nx * sy - ny * sx;
	}

	static boolean restOk(Ctx ctx, double rx, double ry, DiePlan[] placed, int die) {
		for (DiceTray.Wall w : ctx.walls) if (w.distance(rx, ry) < DiceTray.R_PLANE + ROCK_LEAN && onSpan(w, rx, ry)) return false;
		for (DiceTray.Bumper b : ctx.bumpers) if (StrictMath.hypot(rx - b.cx(), ry - b.cy()) < b.r() + DiceTray.R_PLANE + ROCK_LEAN) return false;
		for (DiceTray.Box k : ctx.tray.keepOuts()) if (k.distance(rx, ry) < 1.2 + ROCK_LEAN) return false;
		if (!ctx.tray.restZone(die).contains(rx, ry)) return false;
		if (!ctx.tray.floor().contains(rx, ry)) return false;
		for (int j = 0; j < die; j++) {
			DiePlan o = placed[j];
			if (Math.max(Math.abs(o.rx - rx), Math.abs(o.ry - ry)) < ctx.tray.restSep()) return false;
		}
		return true;
	}

	static boolean onSpan(DiceTray.Wall w, double x, double y) {
		double L = w.length();
		double t = ((x - w.ax()) * (w.bx() - w.ax()) + (y - w.ay()) * (w.by() - w.ay())) / L;
		return t > -DiceTray.R_PLANE && t < L + DiceTray.R_PLANE;
	}

	/** First obstacle along a ray: {@code {t, kind, index}} or null. */
	static double[] cast(Ctx ctx, double x, double y, double ux, double uy) {
		double best = Double.POSITIVE_INFINITY;
		int kind = -1;
		int idx = -1;
		for (int i = 0; i < ctx.walls.length; i++) {
			DiceTray.Wall w = ctx.walls[i];
			double un = ux * w.nx() + uy * w.ny();
			if (un >= -1e-9) continue;
			double t = (w.distance(x, y) - DiceTray.R_PLANE) / -un;
			if (t <= 0.3) continue;
			double hx = x + ux * t;
			double hy = y + uy * t;
			double L = w.length();
			double along = ((hx - w.ax()) * (w.bx() - w.ax()) + (hy - w.ay()) * (w.by() - w.ay())) / L;
			if (along < -0.2 || along > L + 0.2) continue;
			if (t < best) {
				best = t;
				kind = 0;
				idx = i;
			}
		}
		for (int i = 0; i < ctx.bumpers.length; i++) {
			DiceTray.Bumper b = ctx.bumpers[i];
			double t = rayCircle(x, y, ux, uy, b.cx(), b.cy(), b.r() + DiceTray.R_PLANE);
			if (t > 0.3 && t < best) {
				best = t;
				kind = 1;
				idx = i;
			}
		}
		return kind < 0 ? null : new double[] {best, kind, idx};
	}

	static double rayCircle(double px, double py, double ux, double uy, double cx, double cy, double r) {
		double fx = px - cx;
		double fy = py - cy;
		double b = fx * ux + fy * uy;
		double c = fx * fx + fy * fy - r * r;
		double disc = b * b - c;
		if (disc < 0) return Double.POSITIVE_INFINITY;
		double t = -b - Math.sqrt(disc);
		return t > 0 ? t : Double.POSITIVE_INFINITY;
	}

	/** True when the segment a → b keeps clear of every bumper (except {@code skip}) by R_PLANE. */
	static boolean legClear(Ctx ctx, double ax, double ay, double bx, double by, int skip) {
		for (int i = 0; i < ctx.bumpers.length; i++) {
			if (i == skip) continue;
			DiceTray.Bumper b = ctx.bumpers[i];
			if (segDist(ax, ay, bx, by, b.cx(), b.cy()) < b.r() + DiceTray.R_PLANE + 0.05) return false;
		}
		return true;
	}

	static double segDist(double ax, double ay, double bx, double by, double px, double py) {
		double dx = bx - ax;
		double dy = by - ay;
		double l2 = dx * dx + dy * dy;
		double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / l2));
		return StrictMath.hypot(ax + dx * t - px, ay + dy * t - py);
	}

	// ---- timing ---------------------------------------------------------------------------------------------------
	/** Release-apex options tried in order when no launch speed meets the target: {vz0 scale, e_f scale, max bounces}. */
	private static final double[][] APEX = {{1, 1, 3}, {0.6, 0.8, 3}, {1.5, 1.1, 3}, {0.35, 0.65, 2}, {2.0, 1.2, 3}, {0.2, 0.6, 1}};

	/**
	 * Meets the timing of one geometry. With a target: for each roll count, the launch speed v0 and the slide
	 * deceleration are solved together (the slide takes exactly the time left, R slides along the last leg to where
	 * the distance ends); then other bounce apexes. NATURAL: the natural deceleration with R where it was seeded.
	 */
	static boolean timing(Ctx ctx, ThrowSpec spec, DiePlan p, int tipPref, int pass) {
		double g = ctx.tray.g();
		int[] tipOrder = {tipPref, tipPref == 2 ? 1 : 2, tipPref == 3 ? 1 : 3, 0};
		double vzBase = p.vz0;
		double efBase = p.ef;
		int nbBase = p.nb;
		double rt0 = p.rt;
		int opts = p.tTarget < 0 ? 1 : APEX.length;
		for (int o = 0; o < opts; o++) {
			double ef = Math.max(0.22, Math.min(0.5, efBase * APEX[o][1]));
			if (!vertical(p, Math.max(0.2, vzBase * APEX[o][0]), ef, Math.min(nbBase, (int) APEX[o][2]), g)) continue;
			for (int ti = 0; ti < (pass == 0 ? 3 : tipOrder.length); ti++) {
				int tips = tipOrder[ti];
				setR(p, rt0);
				if (p.tTarget < 0 ? solveNatural(ctx, spec, p, tips, pass) : solveV(ctx, spec, p, tips, pass)) return true;
			}
		}
		setR(p, rt0);
		vertical(p, vzBase, efBase, nbBase, g);
		return false;
	}

	/** Fills the flight times; false when no hop would have a visible apex. */
	static boolean vertical(DiePlan p, double vz0, double ef, int nbMax, double g) {
		p.vz0 = vz0;
		p.ef = ef;
		p.landV = Math.sqrt(vz0 * vz0 + 2 * g * p.z0);
		int nb = nbMax;
		while (nb > 1 && powi(ef, 2 * nb) * p.landV * p.landV / (2 * g) < 0.03) nb--;
		p.nb = nb;
		p.flightT = new double[nb + 1];
		p.flightT[0] = (vz0 + p.landV) / g;
		double v = p.landV;
		for (int k = 1; k <= nb; k++) {
			v *= ef;
			p.flightT[k] = 2 * v / g;
		}
		return p.flightT[nb] > 0.02;
	}

	/** Unit-speed horizontal travel over all flights (landing retentions applied, bank retentions not). */
	static double airM(DiePlan p) {
		double m = 1;
		double s = 0;
		for (int k = 0; k < p.flightT.length; k++) {
			s += m * p.flightT[k];
			m *= p.mu[k];
		}
		return s;
	}

	static double groundFactor(DiePlan p) {
		double m = 1;
		for (int k = 0; k < p.flightT.length; k++) m *= p.mu[k];
		return m;
	}

	static double airTime(DiePlan p) {
		double t = 0;
		for (double f : p.flightT) t += f;
		return t;
	}

	/** {@code b^n} for small n ≥ 0 by repeated multiplication (exact IEEE, no transcendental). */
	static double powi(double b, int n) {
		double r = 1;
		for (int i = 0; i < n; i++) r *= b;
		return r;
	}

	static double tipTime(double v) {
		return Math.max(0.11, Math.min(0.24, 3.2 / Math.max(1e-6, v)));
	}

	static double rho(DiePlan p, int tips) {
		boolean t = p.teeter && tips > 0;
		return powi(TIP_KEEP, t ? tips - 1 : tips) * (t ? TEETER_KEEP : 1);
	}

	static int advance(DiePlan p, int tips) {
		return tips - (p.teeter && tips > 0 && p.teeterSign < 0 ? 1 : 0);
	}

	/** Time of the rolls (s) for ground speed vg. */
	static double tipsTime(DiePlan p, int tips, double vg) {
		double t = 0;
		double v = vg;
		for (int j = 0; j < tips; j++) {
			double tau = tipTime(v);
			if (p.teeter && j == tips - 1) {
				t += teeterMs(p, tau) / 1000.0;
				v *= TEETER_KEEP;
			} else {
				t += tau;
				v *= TIP_KEEP;
			}
		}
		return t;
	}

	/** Launch speed for deceleration a (closed form of the distance equation), NaN when impossible. */
	static double v0For(DiePlan p, int tips, double a, double MA, double mg) {
		double e = p.eTot;
		double m = e * mg * p.kSet * rho(p, tips);
		double c2 = m * m / (2 * a);
		double c1 = e * MA + e * mg * p.tSet * (1 + p.kSet) / 2;
		double c0 = (p.banks > 0 ? p.cum[p.banks] - e * p.raw[p.banks - 1] : 0) + 2 * advance(p, tips) - p.dist;
		if (c0 >= 0) return Double.NaN;
		return (-c1 + Math.sqrt(c1 * c1 - 4 * c2 * c0)) / (2 * c2);
	}

	/** Rest time (ms on the roll clock) of the plan with launch speed v0 and deceleration a. */
	static double restTime(DiePlan p, int tips, double v0, double a, double mg) {
		double vk = p.eTot * v0 * mg * p.kSet;
		double t = airTime(p) + p.tSet + tipsTime(p, tips, vk) + vk * rho(p, tips) / a + p.tRock / 1000.0;
		return p.tRel + t * 1000;
	}

	static double teeterMs(DiePlan p, double tauS) {
		return tauS * 1000 * 0.6 + p.teeterHold + 90;
	}

	/** NATURAL timing: the natural deceleration, R where it was seeded, v0 from the distance. */
	static boolean solveNatural(Ctx ctx, ThrowSpec spec, DiePlan p, int tips, int pass) {
		double g = ctx.tray.g();
		boolean teeterSave = p.teeter;
		if (tips == 0) p.teeter = false;
		double MA = airM(p);
		double mg = groundFactor(p);
		double a = 0.22 * g;
		double v0 = v0For(p, tips, a, MA, mg);
		if (v0 > 0) {
			p.v0 = v0;
			p.a = a;
			p.tips = tips;
			if (plausible(ctx, spec, p, MA, mg, pass)) return true;
		}
		p.teeter = teeterSave;
		return false;
	}

	/**
	 * Path parameter of R on the last leg for launch speed v0 when the slide takes exactly the time left before the
	 * target; NaN when less than {@link #MIN_SLIDE} s is left. Writes the deceleration into {@code aOut[0]}.
	 */
	static double restParam(DiePlan p, int tips, double v0, double MA, double mg, double[] aOut) {
		double vg = p.eTot * v0 * mg;
		double vk = vg * p.kSet;
		double fixed = airTime(p) + p.tSet + tipsTime(p, tips, vk) + p.tRock / 1000.0;
		double tSlide = (p.tTarget - p.tRel) / 1000.0 - fixed;
		if (tSlide < MIN_SLIDE) return Double.NaN;
		double vs = vk * rho(p, tips);
		aOut[0] = vs / tSlide;
		double sAir = along(p, v0 * MA);
		double d = sAir + (vg + vk) / 2 * p.tSet + 2 * advance(p, tips) + vs * tSlide / 2;
		return d - p.cum[p.banks];
	}

	/** Speeds kept through the settle, tried in order. */
	private static final double[] KSET = {1, 0.65, 0.45, 0.3};

	/** Shortest slide (s): long enough to square the yaw up and keep the last face change ≥ 120 ms before rest. */
	static final double MIN_SLIDE = 0.09;

	/**
	 * Target timing: bisects the launch speed so that R (on the last leg) is the seeded point; the slide deceleration
	 * follows in closed form. When the seeded point is out of reach, R is clamped to the nearest reachable point of
	 * the leg inside the rest zone.
	 */
	static boolean solveV(Ctx ctx, ThrowSpec spec, DiePlan p, int tips, int pass) {
		double g = ctx.tray.g();
		boolean teeterSave = p.teeter;
		if (tips == 0) p.teeter = false;
		double MA = airM(p);
		double mg = groundFactor(p);
		double slack = pass == 0 ? 0.7 : pass == 1 ? 0.45 : pass == 2 ? 0.15 : 0.03;
		double lo0 = spec.speedMin() * slack;
		double hi = spec.speedMax() / slack;
		if (p.banks > 0) lo0 = Math.max(lo0, (p.raw[p.banks - 1] + 0.3 / p.eTot) / MA);
		double[] a = new double[1];
		boolean ok = false;
		for (int kk = 0; kk < KSET.length && lo0 < hi; kk++) {
			p.kSet = KSET[kk];
			double lo = lo0;
			double fHi = restParam(p, tips, hi, MA, mg, a);
			if (Double.isNaN(fHi)) {
				break; // no time left even at the fastest launch; a softer landing only slows the rolls
			}
			double fLo = restParam(p, tips, lo, MA, mg, a);
			if (Double.isNaN(fLo)) {
				// the slowest valid launch: where the slide time reaches its floor
				double l = lo;
				double h = hi;
				for (int it = 0; it < 30; it++) {
					double m = 0.5 * (l + h);
					if (Double.isNaN(restParam(p, tips, m, MA, mg, a))) l = m;
					else h = m;
				}
				lo = h;
				fLo = restParam(p, tips, lo, MA, mg, a);
				if (Double.isNaN(fLo)) break;
			}
			double rMin = Math.min(fLo, fHi);
			double rMax = Math.max(fLo, fHi);
			if (rMin > p.zt1) {
				continue; // R would lie past the zone even at the slowest launch: keep less speed through the settle
			}
			if (rMax < p.zt0) {
				break;
			}
			double t = Math.max(Math.max(p.zt0, rMin), Math.min(Math.min(p.zt1, rMax), p.rt));
			// Illinois regula falsi on v0 ∈ [lo, hi] for restParam(v0) = t
			double l = lo;
			double h = hi;
			double fl = fLo - t;
			double fh = fHi - t;
			double v0 = l;
			int side = 0;
			for (int it = 0; it < 50; it++) {
				v0 = (fh - fl) != 0 ? (l * fh - h * fl) / (fh - fl) : 0.5 * (l + h);
				if (!(v0 > l && v0 < h)) v0 = 0.5 * (l + h);
				double f = restParam(p, tips, v0, MA, mg, a) - t;
				if (Double.isNaN(f)) {
					l = v0;
					fl = restParam(p, tips, l, MA, mg, a) - t;
					continue;
				}
				if (Math.abs(f) < 1e-10) break;
				if (f * fl > 0) {
					l = v0;
					fl = f;
					if (side == -1) fh /= 2;
					side = -1;
				} else {
					h = v0;
					fh = f;
					if (side == 1) fl /= 2;
					side = 1;
				}
			}
			double rt = restParam(p, tips, v0, MA, mg, a);
			if (Double.isNaN(rt) || rt < p.zt0 - 1e-6 || rt > p.zt1 + 1e-6 || !restAt(ctx, p, rt)) break;
			if (a[0] < g * (pass == 0 ? 0.03 : 0.015) || a[0] > g * (pass == 0 ? 0.9 : 1.6)) {
				break;
			}
			setR(p, rt);
			p.v0 = v0;
			p.a = a[0];
			p.tips = tips;
			ok = plausible(ctx, spec, p, MA, mg, pass);
			break;
		}
		if (!ok) {
			p.teeter = teeterSave;
			p.kSet = 1;
		}
		return ok;
	}

	static boolean plausible(Ctx ctx, ThrowSpec spec, DiePlan p, double MA, double mg, int pass) {
		double slack = pass == 0 ? 0.6 : pass == 1 ? 0.35 : pass == 2 ? 0.1 : 0.02;
		if (p.v0 < spec.speedMin() * slack || p.v0 > spec.speedMax() / slack) {
			return false;
		}
		// every bank happens in the air, well before the last landing
		double rawAir = p.v0 * MA;
		if (p.banks > 0 && rawAir < p.raw[p.banks - 1] + 0.3 / p.eTot) {
			return false;
		}
		double sAir = along(p, rawAir);
		if (sAir > p.dist - 2 * advance(p, p.tips) - 0.05) {
			return false;
		}
		double vg = p.eTot * p.v0 * mg * p.kSet;
		if (p.tips > 0 && vg < (pass == 0 ? 2.0 : 0.8)) {
			return false;
		}
		double vs = vg * rho(p, p.tips);
		double tSlide = vs / p.a;
		// a teeter that falls back leans ~0.95 u forward first: that stretch must still be on the path
		if (p.teeter && p.tips > 0 && p.teeterSign < 0 && vs * tSlide / 2 < 1.0) {
			return false;
		}
		if (tSlide < 0.08) {
			return false;
		}
		double lastTip = p.tips == 0 ? 0 : tipTime(vg * powi(TIP_KEEP, p.tips - 1));
		if (!(p.teeter && p.tips > 0) && tSlide + p.tRock / 1000.0 + lastTip / 2 < 0.13) {
			return false;
		}
		// landings and the ground path avoid keep-outs
		if (!ctx.tray.keepOuts().isEmpty()) {
			double m = 1;
			double M = 0;
			double[] xy = new double[2];
			for (int k = 0; k < p.flightT.length; k++) {
				M += m * p.flightT[k];
				m *= p.mu[k];
				pointAt(p, along(p, p.v0 * M), xy);
				for (DiceTray.Box b : ctx.tray.keepOuts()) if (b.distance(xy[0], xy[1]) < 1.2) {
					return false;
				}
			}
			pointAt(p, sAir, xy);
			for (DiceTray.Box b : ctx.tray.keepOuts()) if (segBoxClose(xy[0], xy[1], p.rx, p.ry, b, 1.2)) {
				return false;
			}
		}
		return true;
	}

	static boolean segBoxClose(double ax, double ay, double bx, double by, DiceTray.Box b, double d) {
		for (int i = 0; i <= 16; i++) {
			double t = i / 16.0;
			if (b.distance(ax + (bx - ax) * t, ay + (by - ay) * t) < d) return true;
		}
		return false;
	}

	/** Path distance for unit-speed travel {@code raw} (= v0·M) with the bank retentions applied. */
	static double along(DiePlan p, double raw) {
		double e = 1;
		for (int j = 0; j < p.banks; j++) {
			if (raw < p.raw[j]) return p.cum[j] + e * (raw - (j == 0 ? 0 : p.raw[j - 1]));
			e *= p.bE[j];
		}
		return p.banks == 0 ? raw : p.cum[p.banks] + e * (raw - p.raw[p.banks - 1]);
	}

	static void pointAt(DiePlan p, double s, double[] out) {
		int j = 0;
		while (j < p.banks && s > p.cum[j + 1]) j++;
		out[0] = p.vx[j] + p.lx[j] * (s - p.cum[j]);
		out[1] = p.vy[j] + p.ly[j] * (s - p.cum[j]);
	}

	// ---- die–die separation -----------------------------------------------------------------------------------------
	/** Longest allowed brush between two dice near the floor (a CLACK), ms. */
	public static final double CLACK_MS = 50;
	/** Closest allowed centre distance during a CLACK (the dice touch, they do not pass through). */
	public static final double CLACK_MIN = 1.75;

	/**
	 * True when die {@code p} keeps {@link #DIE_GAP} from every die placed before it whenever both are near the floor,
	 * except for at most one brief brush (≤ {@link #CLACK_MS}, never closer than {@link #CLACK_MIN}) which becomes a
	 * CLACK event.
	 */
	static boolean clearOf(Ctx ctx, DiePlan p, DiePlan[] placed) {
		return clearOf(ctx, p, placed, false);
	}

	/** {@code lenient}: the brush may come down to {@link #CLACK_MIN} (no margin); still one short brush, never a penetration. */
	static boolean clearOf(Ctx ctx, DiePlan p, DiePlan[] placed, boolean lenient) {
		p.clackT = -1;
		if (p.die == 0) return true;
		double g = ctx.tray.g();
		if (!apart(ctx, p, placed)) return false;
		double[] a = new double[3];
		double[] b = new double[3];
		int[] hint = new int[2];
		double end = p.segs[p.segs.length - 1].t0;
		int runs = 0;
		for (int j = 0; j < p.die; j++) {
			DiePlan o = placed[j];
			hint[0] = 0;
			hint[1] = 0;
			double runStart = -1;
			double best = Double.POSITIVE_INFINITY;
			for (double t = p.tRel; t <= end + 1e-9; t += 3) {
				pos(p.segs, t, a, hint, 0, g);
				boolean near = a[2] < NEAR_FLOOR + 0.25;
				double d = Double.POSITIVE_INFINITY;
				if (near) {
					pos(o.segs, t, b, hint, 1, g);
					if (b[2] < NEAR_FLOOR + 0.25) d = StrictMath.hypot(a[0] - b[0], a[1] - b[1]);
				}
				if (d < DIE_GAP + 0.12) {
					if (d < (lenient ? CLACK_MIN : CLACK_MIN + 0.12)) return false;
					if (runStart < 0) {
						runStart = t;
						if (++runs > 1) return false;
					}
					if (t - runStart > CLACK_MS - 4) return false;
					if (d < best) {
						best = d;
						p.clackT = t;
						p.clackX = 0.5 * (a[0] + b[0]);
						p.clackY = 0.5 * (a[1] + b[1]);
					}
				} else {
					runStart = -1;
				}
			}
		}
		return true;
	}

	/** Smallest centre distance of two released dice at any time, in every pass (no pass-through in the air either). */
	public static final double MIN_CENTRE = 2.0;
	/** Margin over {@link #MIN_CENTRE} for the 1-ms sampling of {@link #apart}. */
	static final double APART_MARGIN = 0.05;
	/** Spread of the centre height above the lowest point: support(q) − 1 ∈ [0, √3 − 1]. */
	static final double SUPPORT_SPREAD = 0.7320508075688772;

	/**
	 * Whole-flight separation: from the later release to both rests, every 1 ms, the smallest possible centre distance
	 * (xy exact; z from the lowest points, less the support spread, so no orientation and no non-strict maths) stays ≥
	 * {@link #MIN_CENTRE} + {@link #APART_MARGIN}.
	 */
	static boolean apart(Ctx ctx, DiePlan p, DiePlan[] placed) {
		double g = ctx.tray.g();
		double[] a = new double[3];
		double[] b = new double[3];
		int[] hint = new int[2];
		double endP = p.segs[p.segs.length - 1].t0;
		double min2 = (MIN_CENTRE + APART_MARGIN) * (MIN_CENTRE + APART_MARGIN);
		for (int j = 0; j < p.die; j++) {
			DiePlan o = placed[j];
			double end = Math.max(endP, o.segs[o.segs.length - 1].t0);
			hint[0] = 0;
			hint[1] = 0;
			for (double t = Math.max(p.tRel, o.tRel); t <= end + 1e-9; t += 1) {
				pos(p.segs, t, a, hint, 0, g);
				pos(o.segs, t, b, hint, 1, g);
				double dx = a[0] - b[0];
				double dy = a[1] - b[1];
				double dz = Math.max(0, Math.abs(Math.max(0, a[2]) - Math.max(0, b[2])) - SUPPORT_SPREAD);
				if (dx * dx + dy * dy + dz * dz < min2) return false;
			}
		}
		return true;
	}

	/** Position-only evaluation {@code {x, y, zLow}} (no orientation), with a forward-moving segment hint. */
	static void pos(Seg[] s, double t, double[] out, int[] hint, int die, double gravity) {
		int k = hint[die];
		while (k + 1 < s.length && s[k + 1].t0 <= t) k++;
		hint[die] = k;
		Seg g = s[k];
		double local = Math.max(0, Math.min(t, g.t1) - g.t0);
		double tau = local / 1000.0;
		switch (g.kind) {
			case DieSample.AIR -> {
				out[0] = g.x0 + g.vx * tau;
				out[1] = g.y0 + g.vy * tau;
				out[2] = g.z0 + g.vz * tau - 0.5 * gravity * tau * tau;
			}
			case DieSample.ROLL, DieSample.TEETER, DieSample.ROCK -> {
				if (g.kind == DieSample.ROLL && g.sub == Seg.SETTLE) {
					out[0] = g.x0 + g.vx * tau + 0.5 * g.ax * tau * tau;
					out[1] = g.y0 + g.vy * tau + 0.5 * g.ay * tau * tau;
				} else {
					double th = g.theta(local);
					double al = -StrictMath.cos(th) + StrictMath.sin(th);
					out[0] = g.px + g.dx * al;
					out[1] = g.py + g.dy * al;
				}
				out[2] = 0;
			}
			case DieSample.HELD -> {
				out[0] = g.x0;
				out[1] = g.y0;
				out[2] = g.z0;
			}
			default -> {
				out[0] = g.x0 + g.vx * tau + 0.5 * g.ax * tau * tau;
				out[1] = g.y0 + g.vy * tau + 0.5 * g.ay * tau * tau;
				out[2] = 0;
			}
		}
	}

	// ---- build ------------------------------------------------------------------------------------------------------
	static DiceRoll build(Ctx ctx, DiePlan[] plans, int seed, int[] faces, int attempt, boolean fallback) {
		int n = plans.length;
		Seg[][] segs = new Seg[n][];
		double[][] label = new double[n][];
		double[] restT = new double[n];
		double[] relT = new double[n];
		double[] wallT = new double[n];
		double[][] rest = new double[n][];
		boolean[] teeter = new boolean[n];
		List<MotionEvent> ev = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			DiePlan p = plans[i];
			List<MotionEvent> mine = new ArrayList<>();
			segs[i] = buildSegs(ctx, p, mine);
			ev.addAll(mine);
			if (p.clackT >= 0) ev.add(new MotionEvent(p.clackT, DiceEvent.CLACK, -1, 0.5f, p.clackX, p.clackY, i));
			Seg last = segs[i][segs[i].length - 1];
			restT[i] = last.t0;
			relT[i] = p.tRel;
			rest[i] = new double[] {p.rx, p.ry};
			teeter[i] = p.teeter && p.tips > 0;
			wallT[i] = -1;
			for (MotionEvent e : mine) {
				if (e.kind() == DiceEvent.WALL || e.kind() == DiceEvent.BUMPER) {
					wallT[i] = e.tMs();
					break;
				}
			}
			// relabelling: the only use of the faces
			int bodyUp = CubeGroup.faceUp(last.q0);
			int k = MotionRng.of(seed, "DLABEL", i).nextInt(4);
			label[i] = CubeGroup.element(CubeGroup.relabel(bodyUp, faces[i], k)).clone();
		}
		MotionEvent[] arr = MotionEvent.sort(ev.toArray(new MotionEvent[0]));
		return new DiceRoll(segs, label, faces.clone(), restT, relT, wallT, rest, arr, ctx.tray.g(), attempt, fallback, teeter);
	}

	/** Builds the canonical segments of one die (and its events when {@code ev != null}). */
	static Seg[] buildSegs(Ctx ctx, DiePlan p, List<MotionEvent> ev) {
		double g = ctx.tray.g();
		List<Seg> out = new ArrayList<>(16);
		Seg held = new Seg();
		held.kind = DieSample.HELD;
		held.t0 = Double.NEGATIVE_INFINITY;
		held.t1 = p.tRel;
		held.x0 = p.p0x();
		held.y0 = p.p0y();
		held.z0 = p.z0;
		Quat.copy(p.qRel, held.q0);
		out.add(held);
		if (ev != null) ev.add(new MotionEvent(p.tRel, DiceEvent.RELEASE, p.die, 0.5f, p.p0x(), p.p0y(), 0));

		double t = p.tRel;
		double x = p.p0x();
		double y = p.p0y();
		int leg = 0;
		double dx = p.lx[0];
		double dy = p.ly[0];
		double v = p.v0;
		double travelled = 0; // path distance
		double[] q = p.qRel.clone();
		double wx = p.wx;
		double wy = p.wy;
		double wz = p.wz;
		double[] tmp = new double[4];
		for (int k = 0; k < p.flightT.length; k++) {
			double dur = p.flightT[k];
			double z0 = k == 0 ? p.z0 : 0;
			double vz = k == 0 ? p.vz0 : p.landV * powi(p.ef, k);
			// banks inside this flight
			while (leg < p.banks && travelled + v * dur > p.cum[leg + 1]) {
				double tw = (p.cum[leg + 1] - travelled) / v;
				Seg a = air(t, tw, x, y, dx * v, dy * v, z0, vz, q, wx, wy, wz);
				out.add(a);
				endOf(a, q, tmp);
				x = p.vx[leg + 1];
				y = p.vy[leg + 1];
				double zw = z0 + vz * tw - 0.5 * g * tw * tw;
				double vzw = vz - g * tw;
				if (ev != null) {
					double vn = v * Math.abs(dx * p.bNx[leg] + dy * p.bNy[leg]);
					int aux = p.bKind[leg] == 0 ? p.bIdx[leg] * 1000 + diamond(ctx, p, leg) : p.bIdx[leg];
					ev.add(new MotionEvent(t + tw * 1000, p.bKind[leg] == 0 ? DiceEvent.WALL : DiceEvent.BUMPER, p.die, strength(vn), x, y, aux));
				}
				t += tw * 1000;
				travelled = p.cum[leg + 1];
				v *= p.bE[leg];
				leg++;
				dx = p.lx[leg];
				dy = p.ly[leg];
				wx *= 0.85;
				wy *= 0.85;
				wz *= 0.85;
				z0 = zw;
				vz = vzw;
				dur -= tw;
			}
			Seg a = air(t, dur, x, y, dx * v, dy * v, z0, vz, q, wx, wy, wz);
			out.add(a);
			endOf(a, q, tmp);
			x = a.x0 + a.vx * dur;
			y = a.y0 + a.vy * dur;
			travelled += v * dur;
			t += dur * 1000;
			double vzIn = vz - g * dur;
			if (ev != null) ev.add(new MotionEvent(t, DiceEvent.FLOOR, p.die, strength(StrictMath.hypot(vzIn, v * 0.3)), x, y, k + 1));
			v *= p.mu[k];
			// spin after a floor contact: biased toward rolling about z × d, never faster than before
			double om = Math.sqrt(wx * wx + wy * wy + wz * wz);
			if (om > 1e-9) {
				double nx = 0.7 * -dy + 0.3 * wx / om;
				double ny = 0.7 * dx + 0.3 * wy / om;
				double nz = 0.3 * wz / om;
				double nn = Math.sqrt(nx * nx + ny * ny + nz * nz);
				double mag = 0.8 * om;
				wx = nx / nn * mag;
				wy = ny / nn * mag;
				wz = nz / nn * mag;
			}
		}
		// ---- ground: settle flat, aligned with the travel direction ----
		double vg = v;
		double psi = StrictMath.atan2(dy, dx);
		double[] rz = Quat.axisAngle(0, 0, 1, psi, new double[4]);
		double[] local = Quat.mul(Quat.conj(rz, new double[4]), q, new double[4]);
		double[] target = Quat.mul(rz, CubeGroup.element(CubeGroup.nearest(local)), new double[4]);
		Seg set = new Seg();
		set.kind = DieSample.ROLL;
		set.sub = Seg.SETTLE;
		set.t0 = t;
		set.t1 = t + p.tSet * 1000;
		set.x0 = x;
		set.y0 = y;
		set.vx = dx * vg;
		set.vy = dy * vg;
		double vk = vg * p.kSet;
		set.ax = -dx * (vg - vk) / p.tSet;
		set.ay = -dy * (vg - vk) / p.tSet;
		Quat.copy(q, set.q0);
		Quat.copy(target, set.q1);
		if (Quat.dot(set.q0, set.q1) < 0) for (int i = 0; i < 4; i++) set.q1[i] = -set.q1[i];
		set.p0 = Quat.angleTo(q, target);
		out.add(set);
		x += dx * (vg + vk) / 2 * p.tSet;
		y += dy * (vg + vk) / 2 * p.tSet;
		t = set.t1;
		Quat.copy(set.q1, q);
		// ---- quarter-rolls ----
		double vt = vk;
		for (int j = 0; j < p.tips; j++) {
			double tauS = tipTime(vt);
			boolean teet = p.teeter && j == p.tips - 1;
			Seg s = new Seg();
			s.kind = teet ? DieSample.TEETER : DieSample.ROLL;
			s.sub = teet ? p.teeterSign : Seg.TIP;
			s.t0 = t;
			s.t1 = t + (teet ? teeterMs(p, tauS) : tauS * 1000);
			if (teet) {
				s.p0 = tauS * 1000 * 0.6;
				s.p1 = p.teeterHold;
				s.p2 = 90;
			}
			s.px = x + dx;
			s.py = y + dy;
			s.dx = dx;
			s.dy = dy;
			Quat.copy(q, s.q0);
			out.add(s);
			if (ev != null && teet) ev.add(new MotionEvent(t + s.p0, DiceEvent.TEETER, p.die, 0.2f, x + dx, y + dy, p.teeterSign));
			if (!teet || p.teeterSign > 0) {
				Quat.axisAngle(-dy, dx, 0, Math.PI / 2, tmp);
				Quat.mul(tmp, q, q);
				x += 2 * dx;
				y += 2 * dy;
			}
			t = s.t1;
			if (ev != null) ev.add(new MotionEvent(t, DiceEvent.TIP, p.die, strength(vt * 0.6), x, y, j + 1));
			vt *= teet ? TEETER_KEEP : TIP_KEEP;
		}
		// ---- slide to R at constant deceleration while the yaw squares up ----
		double ddx = p.rx - x;
		double ddy = p.ry - y;
		double ds = StrictMath.hypot(ddx, ddy);
		double tSlide = ds > 1e-12 ? 2 * ds / vt : vt / p.a;
		Seg sl = new Seg();
		sl.kind = DieSample.SLIDE;
		sl.t0 = t;
		sl.t1 = t + tSlide * 1000;
		sl.x0 = x;
		sl.y0 = y;
		double ux = ds > 1e-12 ? ddx / ds : dx;
		double uy = ds > 1e-12 ? ddy / ds : dy;
		sl.vx = ux * vt;
		sl.vy = uy * vt;
		double acc = tSlide > 0 ? vt / tSlide : 0;
		sl.ax = -ux * acc;
		sl.ay = -uy * acc;
		double quarter = Math.PI / 2;
		double yaw = -(psi - quarter * Math.rint(psi / quarter));
		sl.p0 = yaw;
		Quat.copy(q, sl.q0);
		out.add(sl);
		t = sl.t1;
		Quat.axisAngle(0, 0, 1, yaw, tmp);
		Quat.mul(tmp, q, q);
		double[] qRest = CubeGroup.element(CubeGroup.nearest(q)).clone();
		// ---- rock about the leading edge (the axis-aligned direction nearest to the travel direction) ----
		double rdx = Math.abs(ux) >= Math.abs(uy) ? Math.signum(ux) : 0;
		double rdy = Math.abs(ux) >= Math.abs(uy) ? 0 : Math.signum(uy);
		if (rdx == 0 && rdy == 0) rdx = 1;
		Seg rk = new Seg();
		rk.kind = DieSample.ROCK;
		rk.t0 = t;
		rk.px = p.rx + rdx;
		rk.py = p.ry + rdy;
		rk.dx = rdx;
		rk.dy = rdy;
		rk.p0 = p.rockAmp;
		Quat.copy(qRest, rk.q0);
		out.add(rk);
		if (ev != null) ev.add(new MotionEvent(t, DiceEvent.ROCK, p.die, 0.1f, p.rx, p.ry, 0));
		Seg rs = new Seg();
		rs.kind = DieSample.REST;
		// the solved end equals the target to ~1e-9 ms: pin the rest exactly (the residue goes into the rock)
		rs.t0 = p.tTarget >= 0 ? p.tTarget : t + p.tRock;
		rs.t1 = Double.POSITIVE_INFINITY;
		rk.t1 = rs.t0;
		rs.x0 = p.rx;
		rs.y0 = p.ry;
		Quat.copy(qRest, rs.q0);
		out.add(rs);
		if (ev != null) ev.add(new MotionEvent(rs.t0, DiceEvent.REST, p.die, 0.3f, p.rx, p.ry, 0));
		return out.toArray(new Seg[0]);
	}

	static int diamond(Ctx ctx, DiePlan p, int leg) {
		DiceTray.Wall w = ctx.walls[p.bIdx[leg]];
		if (w.pitch() <= 0) return 0;
		double cx = p.vx[leg + 1];
		double cy = p.vy[leg + 1];
		double along = ((cx - w.ax()) * (w.bx() - w.ax()) + (cy - w.ay()) * (w.by() - w.ay())) / w.length();
		return Math.max(0, Math.min(999, (int) Math.floor(along / w.pitch())));
	}

	static float strength(double speed) {
		return (float) Math.max(0, Math.min(1, speed / DiceEvent.STRENGTH_SPEED));
	}

	static Seg air(double t0, double dur, double x, double y, double vx, double vy, double z0, double vz, double[] q, double wx, double wy, double wz) {
		Seg s = new Seg();
		s.kind = DieSample.AIR;
		s.t0 = t0;
		s.t1 = t0 + dur * 1000;
		s.x0 = x;
		s.y0 = y;
		s.vx = vx;
		s.vy = vy;
		s.z0 = z0;
		s.vz = vz;
		Quat.copy(q, s.q0);
		s.wx = wx;
		s.wy = wy;
		s.wz = wz;
		return s;
	}

	/** Orientation at the end of an AIR segment into {@code q}. */
	static void endOf(Seg a, double[] q, double[] tmp) {
		Quat.expMap(a.wx, a.wy, a.wz, a.dur(), tmp);
		Quat.mul(tmp, a.q0, q);
		Quat.normalize(q, q);
	}

	static void uniformQ(MotionRng r, double[] out) {
		double u1 = r.nextDouble();
		double u2 = r.nextDouble();
		double u3 = r.nextDouble();
		Quat.set(out, Math.sqrt(1 - u1) * StrictMath.sin(2 * Math.PI * u2), Math.sqrt(1 - u1) * StrictMath.cos(2 * Math.PI * u2),
			Math.sqrt(u1) * StrictMath.sin(2 * Math.PI * u3), Math.sqrt(u1) * StrictMath.cos(2 * Math.PI * u3));
	}
}

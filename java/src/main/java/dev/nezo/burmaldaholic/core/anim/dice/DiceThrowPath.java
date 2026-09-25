package dev.nezo.burmaldaholic.core.anim.dice;

import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import java.util.ArrayList;
import java.util.List;

/**
 * The shared dice throw (docs/design/animation/tables.md §2.3; craps and Dice Duel). PURE and deterministic: the
 * screen, the other players and the in-world renderers build the same path from {@code (seed, d1, d2, params)} and
 * sample it on the shared clock; the dice ALWAYS come to rest showing {@code (d1, d2)}.
 *
 * <p>Wall mode (craps, T = 1350 ms, times scale with T):
 * <pre>
 *   0 –  450   flight: parabola start → wall contact W (20–80 % along the wall, from the seed), apex 22 px, θ 720°/s,
 *              tumble frame every 50 ms; die 2 six px to the side with a 40 ms lag
 *   450 – 530  wall hit: squash 0.8 / 1.15 across the wall, {@code dice_wall}
 *   530 – 1100 rebound (restitution 0.55, ±15° jitter): two floor bounces of 9 and 4 px ({@code dice_bounce})
 *   1100 – 1350 final skid to the rest point (outCubic), θ eases to a multiple of 90°; the REAL face shows from
 *              82 % (1110 ms) on — before that only blank tumble frames (no false readouts, §0.6.2)
 * </pre>
 * No-wall mode (Dice Duel, T = 900 ms): a short arc (apex 14 px) to a floor contact at 45 %, one bounce (apex 5 px)
 * to 80 %, a skid to rest; real faces from 82 %. Rest points are ≥ 18 px apart (the second die is pushed away).
 */
public final class DiceThrowPath {
	/** Share of the throw after which the real faces show. */
	public static final double FACE_AT = 0.82;
	public static final int TUMBLE_FRAMES = 8;
	public static final int CRAPS_MS = 1350;
	public static final int DUEL_MS = 900;
	/** Minimum distance between the two rest points (px). */
	public static final double MIN_REST_GAP = 18;

	/**
	 * Geometry in the caller's pixel space (y down).
	 *
	 * @param startX    throw origin (die 1)
	 * @param startY    throw origin
	 * @param wall      true: craps back wall at {@code wallY} from {@code wallX0} to {@code wallX1}
	 * @param restX0    rest region of die 1 (centre); die 2 rests {@code restGap} + 4–10 px to its side
	 * @param durationMs T
	 * @param restGap   minimum distance between the two rest points (the die size: 18 px small, 36 px duel)
	 */
	public record Params(double startX, double startY, boolean wall, double wallY, double wallX0, double wallX1, double restX0,
			double restY0, double restX1, double restY1, int durationMs, double restGap) {
		public static Params craps(double startX, double startY, double wallY, double wallX0, double wallX1, double restX0, double restY0,
				double restX1, double restY1) {
			return new Params(startX, startY, true, wallY, wallX0, wallX1, restX0, restY0, restX1, restY1, CRAPS_MS, MIN_REST_GAP);
		}

		public static Params duel(double startX, double startY, double restX0, double restY0, double restX1, double restY1, double restGap) {
			return new Params(startX, startY, false, 0, 0, 0, restX0, restY0, restX1, restY1, DUEL_MS, restGap);
		}
	}

	/** One sample of one die (mutable, reused: no allocation per frame). */
	public static final class Sample {
		public double x;
		public double y;
		/** Height above the felt (px, ≥ 0). */
		public double z;
		/** Spin angle (degrees). */
		public double theta;
		/** Tumble frame 0–7, or -1 when the real face shows. */
		public int frame;
		/** The face shown when {@link #frame} is -1. */
		public int face;
		public double squashX = 1;
		public double squashY = 1;
		/** The die is at rest (final state). */
		public boolean resting;
	}

	/** A sound cue (ms from the throw). */
	public record Cue(int atMs, String sound, float pitch) {}

	private final Params p;
	private final int[] faces;
	private final double[] wallX = new double[2];
	private final double[] wallYs = new double[2];
	private final double[] midX = new double[2];
	private final double[] midY = new double[2];
	private final double[] restX = new double[2];
	private final double[] restY = new double[2];
	private final double[] spin = new double[2];
	private final int[] frame0 = new int[2];
	private final List<Cue> cues;

	private DiceThrowPath(int seed, int d1, int d2, Params p) {
		if (d1 < 1 || d1 > 6 || d2 < 1 || d2 > 6) {
			throw new IllegalArgumentException("faces " + d1 + "," + d2);
		}
		this.p = p;
		this.faces = new int[] {d1, d2};
		SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix(seed, 0x44_49_43_45)); // "DICE"
		double rx = p.restX0() + (p.restX1() - p.restX0()) * rng.nextDouble();
		double ry = p.restY0() + (p.restY1() - p.restY0()) * rng.nextDouble();
		restX[0] = rx;
		restY[0] = ry;
		double gap = p.restGap() + 4 + 6 * rng.nextDouble();
		double dy = (rng.nextDouble() - 0.5) * 8;
		restX[1] = rx + gap;
		restY[1] = clamp(ry + dy, p.restY0(), p.restY1());
		// the solver: never overlapping (≥ restGap apart)
		double dist = Math.hypot(restX[1] - restX[0], restY[1] - restY[0]);
		if (dist < p.restGap()) {
			restX[1] = restX[0] + p.restGap();
			restY[1] = restY[0];
		}
		double along = 0.2 + 0.6 * rng.nextDouble();
		for (int i = 0; i < 2; i++) {
			double jitter = Math.toRadians((rng.nextDouble() * 2 - 1) * 15);
			if (p.wall()) {
				wallX[i] = p.wallX0() + (p.wallX1() - p.wallX0()) * along + i * 6;
				wallYs[i] = p.wallY();
			} else {
				// duel: the floor contact at 60 % of the way from the start to the rest point
				wallX[i] = p.startX() + (restX[i] - p.startX()) * 0.6;
				wallYs[i] = p.startY() + (restY[i] - p.startY()) * 0.6;
			}
			// skid start: 85 % of the way from the contact to the rest point, turned by the rebound jitter
			double vx = (restX[i] - wallX[i]) * 0.85;
			double vy = (restY[i] - wallYs[i]) * 0.85;
			double c = Math.cos(jitter);
			double s = Math.sin(jitter);
			midX[i] = wallX[i] + vx * c - vy * s * 0.3;
			midY[i] = wallYs[i] + vx * s * 0.3 + vy * c;
			spin[i] = (rng.nextDouble() < 0.5 ? -1 : 1) * (600 + 240 * rng.nextDouble());
			frame0[i] = rng.nextInt(TUMBLE_FRAMES);
		}
		this.cues = buildCues();
	}

	public static DiceThrowPath of(int seed, int d1, int d2, Params p) {
		return new DiceThrowPath(seed, d1, d2, p);
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	public Params params() {
		return p;
	}

	public int durationMs() {
		return p.durationMs();
	}

	/** Rest point of die {@code i} (0 / 1). */
	public double restX(int i) {
		return restX[i];
	}

	public double restY(int i) {
		return restY[i];
	}

	/** Time scale relative to the craps storyboard (1350 ms). */
	private double k() {
		return p.durationMs() / (double) (p.wall() ? CRAPS_MS : DUEL_MS);
	}

	/** Die 2 lags 40 ms (wall mode). */
	private double lag(int die) {
		return die == 1 ? 40 * k() : 0;
	}

	/** Samples die {@code die} at {@code tMs} after the throw into {@code out}. */
	public void sample(int die, double tMs, Sample out) {
		double T = p.durationMs();
		out.face = faces[die];
		out.squashX = 1;
		out.squashY = 1;
		out.resting = false;
		double t = tMs - lag(die) * (1 - Math.min(1, Math.max(0, tMs / T)));
		if (t >= T) {
			out.x = restX[die];
			out.y = restY[die];
			out.z = 0;
			out.theta = Math.round((spinAt(die, T)) / 90.0) * 90.0;
			out.frame = -1;
			out.resting = true;
			return;
		}
		if (t < 0) {
			t = 0;
		}
		double sx = p.startX() + (die == 1 ? 6 : 0);
		double sy = p.startY();
		if (p.wall()) {
			double k = k();
			double tFlight = 450 * k;
			double tWall = 530 * k;
			double tB1 = 850 * k;
			double tRest = 1100 * k;
			if (t < tFlight) {
				double s = t / tFlight;
				out.x = sx + (wallX[die] - sx) * s;
				out.y = sy + (wallYs[die] - sy) * s;
				out.z = 4 * 22 * s * (1 - s);
			} else if (t < tWall) {
				double s = (t - tFlight) / (tWall - tFlight);
				out.x = wallX[die];
				out.y = wallYs[die] + 2 * Math.sin(Math.PI * s); // pressed into the wall, then off it
				out.z = 0;
				double q = Math.sin(Math.PI * s);
				out.squashX = 1 + 0.15 * q;
				out.squashY = 1 - 0.2 * q;
			} else if (t < tRest) {
				double s = (t - tWall) / (tRest - tWall);
				double e = Ease.OUT_QUAD.apply(s);
				out.x = wallX[die] + (midX[die] - wallX[die]) * e;
				out.y = wallYs[die] + (midY[die] - wallYs[die]) * e;
				if (t < tB1) {
					double b = (t - tWall) / (tB1 - tWall);
					out.z = 4 * 9 * b * (1 - b);
				} else {
					double b = (t - tB1) / (tRest - tB1);
					out.z = 4 * 4 * b * (1 - b);
				}
			} else {
				double s = (t - tRest) / (T - tRest);
				double e = Ease.OUT_CUBIC.apply(s);
				out.x = midX[die] + (restX[die] - midX[die]) * e;
				out.y = midY[die] + (restY[die] - midY[die]) * e;
				out.z = 0;
			}
		} else {
			double tHit = 0.45 * T;
			double tB = 0.80 * T;
			if (t < tHit) {
				double s = t / tHit;
				out.x = sx + (wallX[die] - sx) * s;
				out.y = sy + (wallYs[die] - sy) * s;
				out.z = 4 * 14 * s * (1 - s);
			} else if (t < tB) {
				double s = (t - tHit) / (tB - tHit);
				out.x = wallX[die] + (midX[die] - wallX[die]) * s;
				out.y = wallYs[die] + (midY[die] - wallYs[die]) * s;
				out.z = 4 * 5 * s * (1 - s);
				if (s < 0.12) {
					double q = Math.sin(Math.PI * s / 0.12);
					out.squashX = 1 + 0.1 * q;
					out.squashY = 1 - 0.15 * q;
				}
			} else {
				double s = (t - tB) / (T - tB);
				double e = Ease.OUT_CUBIC.apply(s);
				out.x = midX[die] + (restX[die] - midX[die]) * e;
				out.y = midY[die] + (restY[die] - midY[die]) * e;
				out.z = 0;
			}
		}
		out.theta = spinAt(die, t);
		out.frame = t >= FACE_AT * T ? -1 : tumbleFrame(die, t);
	}

	/** Spin angle: fast in flight, slowing, easing to the nearest 90° in the skid. */
	private double spinAt(int die, double t) {
		double T = p.durationMs();
		double skid = p.wall() ? 1100 * k() : 0.8 * T;
		if (t <= skid) {
			return spin[die] * t / 1000.0 * (1 - 0.4 * t / T);
		}
		double at = spin[die] * skid / 1000.0 * (1 - 0.4 * skid / T);
		double target = Math.round(at / 90.0) * 90.0;
		double s = Math.min(1, (t - skid) / (T - skid));
		return at + (target - at) * Ease.OUT_CUBIC.apply(s);
	}

	/** Tumble frame: every 50 ms in flight, slowing to 90 ms per frame before the real face shows. */
	private int tumbleFrame(int die, double t) {
		double T = p.durationMs();
		double k = k();
		double fast = (p.wall() ? 530 : 0.45 * T / k) * k;
		double steps;
		if (t < fast) {
			steps = t / (50 * k);
		} else {
			// frame time grows linearly 50 → 90 ms: integral of 1 / (50 + 40 s) over the rest of the tumble
			double span = FACE_AT * T - fast;
			double s = Math.min(1, (t - fast) / span);
			steps = fast / (50 * k) + span / (40 * k) * Math.log((50 + 40 * s) / 50.0);
		}
		return (frame0[die] + (int) Math.floor(steps)) % TUMBLE_FRAMES;
	}

	/** Sound cues: throw, wall, bounces, rest. */
	public List<Cue> cues() {
		return cues;
	}

	private List<Cue> buildCues() {
		List<Cue> out = new ArrayList<>();
		double T = p.durationMs();
		out.add(new Cue(0, "dice_throw", 1f));
		if (p.wall()) {
			double k = k();
			out.add(new Cue((int) Math.round(450 * k), "dice_wall", 1f));
			out.add(new Cue((int) Math.round(850 * k), "dice_bounce", 1.2f));
			out.add(new Cue((int) Math.round(1100 * k), "dice_bounce", 1.0f));
		} else {
			out.add(new Cue((int) Math.round(0.45 * T), "dice_bounce", 1.2f));
			out.add(new Cue((int) Math.round(0.80 * T), "dice_bounce", 1.0f));
		}
		return List.copyOf(out);
	}
}

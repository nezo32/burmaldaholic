package dev.nezo.burmaldaholic.games.roulette.logic;

import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import java.util.ArrayList;
import java.util.List;

/**
 * The shared roulette ball path (docs/design/animation/tables.md §1.3). PURE and deterministic: every viewer (the
 * player's screen, the other bettors, the in-world {@code RouletteTableRenderer}) builds the same path from the
 * server's {@code (result, seed, spinMs)} and samples it at the shared clock, so the ball lands in the drawn pocket
 * {@link #result} for everybody at the same tick.
 *
 * <p>Angles are degrees, clockwise positive, 0 = the top of the screen. The head (pockets) turns clockwise; the ball
 * runs counter-clockwise relative to it ({@link #rel}). Pocket {@code n} sits at {@code head + P(n)} with
 * {@code P(n) = wheelIndex(n) × 360/37}; {@code A(k)} below is the pocket {@code k} steps along the wheel order from
 * the result (never the number {@code result + k}).
 *
 * <pre>
 *  u = t / S     phase      rel(u)                                              r(u) (fraction of Rw)
 *  0.00 – 0.60   orbit      A(h1+h2) + D + 360·laps·(1 − expDecay(3.2, v))       0.96 → 0.93
 *  0.60 – 0.68   drop       A(h1+h2) + D·(1 − w)²   (70 % of the orbit speed)     0.93 → 0.70 (inQuad), deflector kick at 0.64
 *  0.68 – 0.78   hop 1      A(h1+h2) → A(h2), inOutCubic                          0.70 → 0.62 + 0.08·sin(πv)
 *  0.78 – 0.86   hop 2      A(h2) → A(0), inOutCubic                              0.62 + 0.08·sin(πv)
 *  0.86 – 0.90   settle     A(0) + 1.5·sin(6πv)·(1 − v)                           0.62
 *  0.90 –        settled    A(0) = P(result), riding the head                     0.62
 * </pre>
 * Below {@code S = 3000} ms hop 2 is dropped ({@code h2 = 0}) and hop 1 covers 0.68–0.86. {@code h1 + h2 ≠ 0}, so the
 * ball never rests in the result pocket before its last hop; with the 0.08 arcs a hop keeps the ball above the frets
 * except near its ends, so it never sits in a non-result pocket for more than 120 ms at S = 2000 (tested).
 *
 * <p>The head turns {@code H0 + 540·outQuad(u)} (1.5 turns) and stops at {@code u = 1}; the reduced-motion variant
 * ({@link #headReduced}) turns half a turn and hides the ball until it fades into the result pocket (§0.5).
 */
public final class RouletteBallPath {
	/** Degrees per pocket. */
	public static final double STEP = 360.0 / Wheel.POCKETS;
	public static final double R_ORBIT = 0.96;
	public static final double R_ORBIT_END = 0.93;
	public static final double R_DROP_END = 0.70;
	public static final double R_POCKET = 0.62;
	/** Outer edge of the pocket ring (61.5 / 92 px on the big wheel): below it the ball is "in a pocket". */
	public static final double R_POCKET_RIM = 0.668;
	public static final double U_DROP = 0.60;
	public static final double U_KICK = 0.64;
	public static final double U_HOP1 = 0.68;
	public static final double U_HOP2 = 0.78;
	public static final double U_SETTLE = 0.86;
	public static final double U_SETTLED = 0.90;
	/** Settle click (roulette_ball_settle). */
	public static final double U_CLICK = 0.88;
	/** Head turn over the spin, degrees. */
	public static final double HEAD_TURN = 540;
	public static final double HEAD_TURN_REDUCED = 180;
	/** Below this spin length hop 2 is dropped. */
	public static final int TWO_HOPS_MIN_MS = 3000;
	private static final double K = 3.2;
	private static final int[] H1 = {-3, -2, -1, 1, 2, 3};
	private static final int[] H2 = {-2, -1, 1, 2};

	/** A sound cue of the spin (shared time, ms from spin start). */
	public record Cue(int atMs, String sound, float pitch) {}

	public final int result;
	public final int seed;
	public final int spinMs;
	public final int h1;
	public final int h2;
	public final int laps;
	/** Head angle at u = 0. */
	public final double h0;
	/** Extra angle the orbit ends before the drop, so the drop continues at 70 % of the orbit's speed. */
	public final double dropLead;
	private final int resultIndex;
	private final List<Cue> cues;

	private RouletteBallPath(int result, int seed, int spinMs) {
		if (!Wheel.isPocket(result)) {
			throw new IllegalArgumentException("not a pocket: " + result);
		}
		this.result = result;
		this.seed = seed;
		this.spinMs = Math.max(1, spinMs);
		SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix(seed, 0x52_4F_55_4C)); // "ROUL"
		this.h0 = rng.nextInt(360);
		this.laps = 5 + rng.nextInt(2);
		int a = H1[rng.nextInt(H1.length)];
		int b = H2[rng.nextInt(H2.length)];
		if (a + b == 0) {
			b = -b;
		}
		if (this.spinMs < TWO_HOPS_MIN_MS) {
			b = 0;
		}
		this.h1 = a;
		this.h2 = b;
		this.resultIndex = Wheel.wheelIndex(result);
		// orbit end speed (deg per u): d/du [360·laps·(1 − E(v))] at v = 1, v = u / U_DROP
		double eEnd = K * Math.exp(-K) / (1 - Math.exp(-K));
		double orbitEnd = 360.0 * laps * eEnd / U_DROP;
		// drop: rel = A + D·(1 − w)², speed at w = 0 = 2D / (U_HOP1 − U_DROP) = 70 % of the orbit's
		this.dropLead = 0.7 * orbitEnd * (U_HOP1 - U_DROP) / 2;
		this.cues = buildCues();
	}

	public static RouletteBallPath of(int result, int seed, int spinMs) {
		return new RouletteBallPath(result, seed, spinMs);
	}

	// ---- geometry -------------------------------------------------------------------------------------------------

	/** Head-relative angle of pocket {@code n}. */
	public static double pocketAngle(int n) {
		return Wheel.wheelIndex(n) * STEP;
	}

	/** Head-relative angle of the pocket {@code k} wheel steps from the result. */
	public double pocketStep(int k) {
		return (resultIndex + k) * STEP;
	}

	/** Frame of an {@code frames}-frame head set (frame f = head rotated f·360/frames°, frame 0 = pocket 0 at the top). */
	public static int headFrame(double headAngle, int frames) {
		double pitch = 360.0 / frames;
		double a = ((headAngle % 360) + 360) % 360;
		return (int) Math.floor(a / pitch + 0.5) % frames;
	}

	/** The angle frame {@code f} shows. */
	public static double frameAngle(int f, int frames) {
		return f * 360.0 / frames;
	}

	public boolean twoHops() {
		return h2 != 0;
	}

	/** End of hop 1 (u). */
	public double hop1End() {
		return twoHops() ? U_HOP2 : U_SETTLE;
	}

	private double u(double tMs) {
		return tMs / spinMs;
	}

	// ---- head -----------------------------------------------------------------------------------------------------

	/** Head angle (degrees, unwrapped) at {@code tMs}; still after the spin. */
	public double head(double tMs) {
		double u = Math.max(0, Math.min(1, u(tMs)));
		return h0 + HEAD_TURN * Ease.OUT_QUAD.apply(u);
	}

	/** Reduced motion: half a turn (≤ 0.4 rev/s at every supported spin length). */
	public double headReduced(double tMs) {
		double u = Math.max(0, Math.min(1, u(tMs)));
		return h0 + HEAD_TURN_REDUCED * Ease.OUT_QUAD.apply(u);
	}

	/** Head angular speed, degrees per second (0 after the spin). */
	public double headSpeed(double tMs) {
		double u = u(tMs);
		if (u < 0 || u >= 1) {
			return 0;
		}
		return HEAD_TURN * 2 * (1 - u) * 1000.0 / spinMs;
	}

	// ---- ball -----------------------------------------------------------------------------------------------------

	/** Ball angle relative to the head at {@code tMs} (exactly {@link #pocketAngle}(result) from u = 0.90 on). */
	public double rel(double tMs) {
		double u = u(tMs);
		if (u < 0) {
			u = 0;
		}
		double aStart = pocketStep(h1 + h2);
		if (u < U_DROP) {
			double v = u / U_DROP;
			return aStart + dropLead + 360.0 * laps * (1 - Ease.expDecay(K, v));
		}
		if (u < U_HOP1) {
			double w = (u - U_DROP) / (U_HOP1 - U_DROP);
			return aStart + dropLead * (1 - w) * (1 - w);
		}
		double e1 = hop1End();
		if (u < e1) {
			double v = (u - U_HOP1) / (e1 - U_HOP1);
			return aStart - h1 * STEP * Ease.IN_OUT_CUBIC.apply(v);
		}
		if (u < U_SETTLE) {
			double v = (u - U_HOP2) / (U_SETTLE - U_HOP2);
			return pocketStep(h2) - h2 * STEP * Ease.IN_OUT_CUBIC.apply(v);
		}
		if (u < U_SETTLED) {
			double v = (u - U_SETTLE) / (U_SETTLED - U_SETTLE);
			return pocketStep(0) + 1.5 * Math.sin(6 * Math.PI * v) * (1 - v);
		}
		return pocketStep(0);
	}

	/** Ball radius as a fraction of Rw (continuous). */
	public double radius(double tMs) {
		double u = Math.max(0, u(tMs));
		if (u < U_DROP) {
			return R_ORBIT - (R_ORBIT - R_ORBIT_END) * (u / U_DROP);
		}
		if (u < U_HOP1) {
			double w = (u - U_DROP) / (U_HOP1 - U_DROP);
			double r = R_ORBIT_END + (R_DROP_END - R_ORBIT_END) * w * w;
			double half = 40.0 / spinMs; // deflector kick: +0.03 for 80 ms
			double d = Math.abs(u - U_KICK);
			if (d < half) {
				r += 0.03 * (1 - d / half);
			}
			return r;
		}
		double e1 = hop1End();
		if (u < e1) {
			double v = (u - U_HOP1) / (e1 - U_HOP1);
			return R_DROP_END + (R_POCKET - R_DROP_END) * v + 0.08 * Math.sin(Math.PI * v);
		}
		if (u < U_SETTLE) {
			double v = (u - U_HOP2) / (U_SETTLE - U_HOP2);
			return R_POCKET + 0.08 * Math.sin(Math.PI * v);
		}
		return R_POCKET;
	}

	/** Absolute ball angle (head + rel). */
	public double ball(double tMs) {
		return head(tMs) + rel(tMs);
	}

	/** Absolute ball angular speed, degrees per second (numerical, 1 ms). */
	public double ballSpeed(double tMs) {
		return Math.abs(ball(tMs + 0.5) - ball(tMs - 0.5)) * 1000.0;
	}

	/** The ball has settled: highlights, number and dolly may show from here (§0.6.2). */
	public boolean settled(double tMs) {
		return u(tMs) >= U_SETTLED;
	}

	/** The pocket (number) the ball is over at {@code tMs}, or -1 when it is above the frets / on the track. */
	public int pocketUnder(double tMs) {
		if (radius(tMs) > R_POCKET_RIM) {
			return -1;
		}
		int k = (int) Math.floor(((rel(tMs) / STEP + 0.5) % Wheel.POCKETS + Wheel.POCKETS) % Wheel.POCKETS);
		return Wheel.ORDER.get(k);
	}

	/** Shared end of the spin in ms. */
	public int endMs() {
		return spinMs;
	}

	// ---- sounds ---------------------------------------------------------------------------------------------------

	/** Sound cues in time order (spin whoosh, ball rolls, deflector, bounces, settle). */
	public List<Cue> cues() {
		return cues;
	}

	private List<Cue> buildCues() {
		List<Cue> out = new ArrayList<>();
		out.add(new Cue(0, "roulette_spin", 1f));
		// a roll tick each time rel passes a multiple of 360° during the orbit: 360·laps·(1 − E(v)) = 360·m
		int last = -1000;
		for (int m = laps - 1; m >= 1; m--) {
			double y = 1 - (double) m / laps;
			double v = -Math.log(1 - y * (1 - Math.exp(-K))) / K;
			int at = (int) Math.round(v * U_DROP * spinMs);
			if (at - last >= 250) {
				float pitch = (float) (1.9 - 0.6 * v);
				out.add(new Cue(at, "roulette_ball_roll", pitch));
				last = at;
			}
		}
		out.add(new Cue((int) Math.round(U_KICK * spinMs), "roulette_ball_drop", 1f));
		if (twoHops()) {
			out.add(new Cue((int) Math.round(U_HOP2 * spinMs), "roulette_ball_bounce", 1f));
		}
		out.add(new Cue((int) Math.round(U_SETTLE * spinMs), "roulette_ball_bounce", 1.2f));
		out.add(new Cue((int) Math.round(U_CLICK * spinMs), "roulette_ball_settle", 1f));
		out.sort((p, q) -> Integer.compare(p.atMs(), q.atMs()));
		return List.copyOf(out);
	}
}

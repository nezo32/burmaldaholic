package dev.nezo.burmaldaholic.games.craps.present;

import dev.nezo.burmaldaholic.core.anim.dice.CubeGroup;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.Quat;
import org.jspecify.annotations.Nullable;

/**
 * The shooter's hand (S1, owner playtest 1; the Dice house-mode pattern): the dice rattle at dice home from the Roll
 * press until the server's result is in, with no time cap, and the throw starts at the loop's next RELEASE POSE with
 * the plain deterministic {@code DiceSolver} throw — no flight blend, so the solved flight (and its rest) is the same
 * on every client and the dice never pass through each other. Pure; every query is a closed function of the shake ms
 * (0 = Roll pressed).
 *
 * <pre>
 *   loop     the dice lift 0.4 u over 100 ms; the hand moves ±0.12 u along x and each die rocks ±8° yaw (counter-phase) on a
 *            {@link #CYCLE} ms cycle; at every whole cycle the loop passes through ONE pose (yaw 0, x on home): the exit
 *   wait     the result arrives at shake ms a: the throw's t = 0 is the first exit at or after max(a + {@link #SETTLE},
 *            {@link #SHAKE_MIN}), so the hand waits at most one cycle + SETTLE
 *   settle   from a to the exit the hand closes on the throw's start: the loop pose eases (smoothstep) into the solver's
 *            pose at t = 0 (position and turn), so the exit pose IS the solver's start pose (C0), with no jump
 * </pre>
 */
public final class CrapsHand {
	/** The hand shakes at least this long (two full rattles) before it can throw. */
	public static final int SHAKE_MIN = 240;
	/** One full rattle; the loop passes through the release pose once per cycle. */
	public static final double CYCLE = 120;
	/** The shortest settle into the throw's start pose after the result is in. */
	public static final double SETTLE = 80;
	public static final double YAW_DEG = 8;
	public static final double JIT_U = 0.12;
	public static final double LIFT_U = 0.4;
	public static final double LIFT_MS = 100;
	public static final double RAMP_MS = 120;

	/** How far each die spreads out in the middle of the settle, u. */
	public static final double APART = 0.6;

	private CrapsHand() {}

	/** Shake ms of the release (the throw's t = 0) when the result reached the client {@code readyMs} after the press. */
	public static double releaseAt(double readyMs) {
		return releaseAt(readyMs, SETTLE);
	}

	/** {@link #releaseAt(double)} with a settle of at least {@code settleMs} (a long move into a throw from elsewhere). */
	public static double releaseAt(double readyMs, double settleMs) {
		double m = Math.max(Math.max(0, readyMs) + Math.max(SETTLE, settleMs), SHAKE_MIN);
		return Math.ceil(m / CYCLE - 1e-9) * CYCLE;
	}

	// render-thread scratch (the hand is drawn by the screen and the in-world board; tests are single-threaded)
	private static final ThreadLocal<double[][]> SCRATCH = ThreadLocal.withInitial(() -> new double[][] {new double[4], new double[4], new double[4], new double[4], new double[2]});

	/**
	 * The dice at rest at {@code side}'s dice home: the faces of {@code last} squared up (the stick's drag leaves them so),
	 * else {@code fallbackFaces}. Positions are tray units.
	 */
	public static void home(int side, @Nullable DiceRoll last, int[] fallbackFaces, int die, DieSample out) {
		double[] p = CrapsThrow.release(side, die, SCRATCH.get()[4]);
		home(p[0], p[1], last, fallbackFaces, die, out);
	}

	/** {@link #home(int, DiceRoll, int[], int, DieSample)} at the centre ({@code x}, {@code y}) (a dice home on the move). */
	public static void home(double x, double y, @Nullable DiceRoll last, int[] fallbackFaces, int die, DieSample out) {
		if (last != null) {
			last.sample(die, 1e9, out);
			CubeGroup.element(CubeGroup.nearest(out.q), out.q);
		} else {
			CubeGroup.element(CubeGroup.index(fallbackFaces[die], die), out.q);
			out.zLow = 0;
			out.zc = 1;
		}
		out.phase = DieSample.REST;
		out.omega = 0;
		out.speed = 0;
		out.vz = 0;
		out.x = x;
		out.y = y;
	}

	/** {@code out} = the home pose {@code base} of die {@code die} on the rattle loop at shake ms {@code ms}. */
	public static void shake(DieSample base, int die, double ms, DieSample out) {
		if (out != base) out.set(base);
		if (ms <= 0) return;
		double p = Math.min(1, ms / LIFT_MS);
		double lift = LIFT_U * (1 - (1 - p) * (1 - p) * (1 - p));
		out.zc += lift;
		out.zLow += lift;
		double s = StrictMath.sin(2 * Math.PI * ms / CYCLE) * Math.min(1, ms / RAMP_MS);
		// the hand moves both dice together along x; they rock in counter-phase (never toward each other)
		out.x += JIT_U * s;
		double[] r = Quat.axisAngle(0, 0, 1, Math.toRadians(YAW_DEG * s * (die == 0 ? 1 : -1)), SCRATCH.get()[0]);
		Quat.mul(r, out.q, out.q);
		out.phase = DieSample.HELD;
	}

	/**
	 * The settle: {@code loop} (the rattle pose now) eased into {@code start} (the solver's pose at t = 0) by
	 * {@code u} ∈ [0, 1] (0 = the result just arrived, 1 = the exit). The turn is ONE fixed rotation
	 * {@code start · exit⁻¹} ({@code exit} = the loop's pose at the release) applied by a growing share on top of the
	 * rattle, so it never flips arcs; the die is pushed {@link #APART} u along the unit vector {@code (ax, ay)} in the
	 * middle of the settle (0 at both ends) so a turning pair never touches. {@code out} may be {@code loop}.
	 */
	public static void settle(DieSample loop, DieSample exit, DieSample start, double u, double ax, double ay, DieSample out) {
		settle(loop, exit, start, u, ax, ay, 0, 0, out);
	}

	/**
	 * {@link #settle(DieSample, DieSample, DieSample, double, double, double, DieSample)} plus a push ({@code px},
	 * {@code py}) · sin(π w) on the eased share w (a {@link CrapsPair} push that keeps a crossing pair apart).
	 */
	public static void settle(DieSample loop, DieSample exit, DieSample start, double u, double ax, double ay, double px, double py, DieSample out) {
		double c = Math.max(0, Math.min(1, u));
		if (c >= 1) {
			out.set(start);
			return;
		}
		double w = c * c * (3 - 2 * c);
		double push = APART * StrictMath.sin(Math.PI * c);
		double cross = StrictMath.sin(Math.PI * w);
		if (out != loop) out.set(loop);
		out.x += (start.x - exit.x) * w + ax * push + px * cross;
		out.y += (start.y - exit.y) * w + ay * push + py * cross;
		out.zc += (start.zc - exit.zc) * w;
		out.zLow = Math.max(0, out.zLow + (start.zLow - exit.zLow) * w);
		double[][] sc = SCRATCH.get();
		double[] turn = Quat.mul(start.q, Quat.conj(exit.q, sc[1]), sc[2]);
		double[] part = Quat.slerp(Quat.set(sc[3], 1, 0, 0, 0), turn, w, sc[0]);
		Quat.mul(part, out.q, out.q);
	}
}

package dev.nezo.burmaldaholic.games.craps.present;

import dev.nezo.burmaldaholic.core.anim.dice.CubeGroup;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.Quat;
import org.jspecify.annotations.Nullable;

/**
 * The shooter's hand (S1, owner playtest 1): the dice rattle at dice home from the Roll press until the server's result
 * is in, with no time cap, and the throw leaves the hand from the pose the hand shows at that instant. Pure; every
 * query is a closed function of the shake ms (0 = Roll pressed) or the roll ms (0 = die 0 leaves the hand).
 *
 * <pre>
 *   shake    the dice lift 0.4 u over 100 ms; each die rocks ±8° yaw and ±0.12 u along x on a 120 ms cycle (the two in
 *            counter-phase); every 60 ms stroke ends at a zero crossing (yaw 0, x on home)
 *   release  the first stroke end at or after max(result in, {@link #SHAKE_MIN}) (the Dice house-mode rule: the
 *            flick waits for a reversal)
 *   hand-off for {@link #BLEND_MS} after the release the solved path is drawn offset by (hand − path(0)) and turned
 *            by (hand · path(0)⁻¹), both fading to nothing: t = 0 shows the hand pose exactly, the rest is the solve
 * </pre>
 */
public final class CrapsHand {
	/** The hand shakes at least this long (two full rattles) before it can throw. */
	public static final int SHAKE_MIN = 240;
	/** One rattle stroke, ms (a full cycle is two). */
	public static final double STROKE = 60;
	public static final double YAW_DEG = 8;
	public static final double JIT_U = 0.12;
	public static final double LIFT_U = 0.4;
	public static final double LIFT_MS = 100;
	public static final double RAMP_MS = 120;
	/** The hand-off from the hand pose to the solved path, ms (well before the dice reach the wall). */
	public static final double BLEND_MS = 220;

	private CrapsHand() {}

	/** Shake ms of the release when the result reached the client {@code readyMs} after the press. */
	public static double releaseAt(double readyMs) {
		double m = Math.max(Math.max(0, readyMs), SHAKE_MIN);
		return Math.ceil(m / STROKE - 1e-9) * STROKE;
	}

	/**
	 * The dice at rest at {@code side}'s dice home: the faces of {@code last} squared up (the stick's drag leaves them so),
	 * else {@code fallbackFaces}. Positions are tray units.
	 */
	public static void home(int side, @Nullable DiceRoll last, int[] fallbackFaces, int die, DieSample out) {
		double[] tmp = new double[4];
		if (last != null) {
			last.sample(die, 1e9, out);
			CubeGroup.element(CubeGroup.nearest(out.q), tmp);
			Quat.copy(tmp, out.q);
		} else {
			CubeGroup.element(CubeGroup.index(fallbackFaces[die], die), tmp);
			Quat.copy(tmp, out.q);
			out.zLow = 0;
			out.zc = 1;
		}
		out.phase = DieSample.REST;
		out.omega = 0;
		out.speed = 0;
		out.vz = 0;
		double[] p = CrapsThrow.release(side, die);
		out.x = p[0];
		out.y = p[1];
	}

	/** {@code out} = the home pose {@code base} of die {@code die} rattled at shake ms {@code ms} ({@code out} may be {@code base}). */
	public static void shake(DieSample base, int die, double ms, DieSample out) {
		if (out != base) out.set(base);
		if (ms <= 0) return;
		double p = Math.min(1, ms / LIFT_MS);
		double lift = LIFT_U * (1 - (1 - p) * (1 - p) * (1 - p));
		out.zc += lift;
		out.zLow += lift;
		double s = StrictMath.sin(Math.PI * ms / STROKE) * (die == 0 ? 1 : -1) * Math.min(1, ms / RAMP_MS);
		out.x += JIT_U * s;
		double[] r = Quat.axisAngle(0, 0, 1, Math.toRadians(YAW_DEG * s), new double[4]);
		Quat.mul(r, out.q, out.q);
		out.phase = DieSample.HELD;
	}

	/**
	 * The solved path {@code io} (sampled at roll ms {@code t}) handed off from the {@code hand} pose; {@code at0} is the
	 * same path at t = 0. From {@link #BLEND_MS} on {@code io} is left exactly as solved (the server's faces at rest).
	 */
	public static void handoff(DieSample hand, DieSample at0, double t, DieSample io) {
		if (t >= BLEND_MS) return;
		double u = Math.max(0, t / BLEND_MS);
		double w = 1 - u * u * (3 - 2 * u);
		io.x += (hand.x - at0.x) * w;
		io.y += (hand.y - at0.y) * w;
		io.zc += (hand.zc - at0.zc) * w;
		io.zLow = Math.max(0, io.zLow + (hand.zLow - at0.zLow) * w);
		double[] c = Quat.mul(hand.q, Quat.conj(at0.q, new double[4]), new double[4]);
		double[] k = Quat.slerp(Quat.identity(), c, w, new double[4]);
		Quat.mul(k, io.q, io.q);
	}
}

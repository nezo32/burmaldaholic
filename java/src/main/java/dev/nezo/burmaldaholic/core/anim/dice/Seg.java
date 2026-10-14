package dev.nezo.burmaldaholic.core.anim.dice;

/**
 * One closed-form segment of a die's CANONICAL motion (package-private; built by {@link DiceSolver}, evaluated by
 * {@link DiceRoll}). Times in ms, rates per second. {@code sin}/{@code cos} appear only here, evaluated directly from
 * the segment's stored start state (never chained), so sampling is pure in t.
 */
final class Seg {
	/** {@link #sub} of a ROLL segment: the settle from the landing pose to flat. */
	static final int SETTLE = 0;
	/** {@link #sub} of a ROLL segment: a quarter-roll over the leading edge. */
	static final int TIP = 1;
	/** Mid-roll slowdown of the quarter-roll profile (speed ratio middle/ends = (1−A)/(1+A)). */
	static final double TIP_A = 0.35;
	/** Teeter: hold angle below 45° (radians) and wobble amplitude. */
	static final double TEETER_HOLD = Math.toRadians(42);
	static final double TEETER_WOBBLE = Math.toRadians(2);
	static final double TEETER_WOBBLE_MS = 80;
	/** Rock decay time constant, ms. */
	static final double ROCK_TAU = 35;

	int kind;
	int sub;
	double t0;
	double t1;
	// position (HELD/AIR/SETTLE/SLIDE/REST): x = x0 + vx·τ + ½·ax·τ²
	double x0, y0, vx, vy, ax, ay;
	// AIR: zLow = z0 + vz·τ − ½·g·τ² (HELD: z0)
	double z0, vz;
	final double[] q0 = new double[4];
	final double[] q1 = new double[4];
	// AIR angular velocity (rad/s)
	double wx, wy, wz;
	// pivot (TIP/TEETER/ROCK): leading-edge point and unit roll direction
	double px, py, dx, dy;
	// profile parameters: SETTLE p0 = angle; TIP —; TEETER p0 rise ms, p1 hold ms, p2 fall ms, sub ±1; ROCK p0 amplitude;
	// SLIDE p0 = yaw change (radians)
	double p0, p1, p2;

	double dur() {
		return (t1 - t0) / 1000.0;
	}

	private static double sin(double x, boolean strict) {
		return strict ? StrictMath.sin(x) : Math.sin(x);
	}

	private static double exp(double x, boolean strict) {
		return strict ? StrictMath.exp(x) : Math.exp(x);
	}

	/** Pivot angle (strict maths: the solver's decisions) of a TIP / TEETER / ROCK segment at local {@code ms}. */
	double theta(double ms) {
		return theta(ms, true);
	}

	/** Pivot angle at local {@code ms} (ms since t0), radians; {@code strict = false} for per-frame sampling. */
	double theta(double ms, boolean strict) {
		double tt = (t1 - t0);
		switch (kind) {
			case DieSample.TEETER: {
				if (ms <= p0) {
					double s = ms / p0;
					return TEETER_HOLD * (1 - (1 - s) * (1 - s));
				}
				if (ms <= p0 + p1) {
					double h = ms - p0;
					return TEETER_HOLD + TEETER_WOBBLE * sin(2 * Math.PI * h / TEETER_WOBBLE_MS, strict) * exp(-h / 160.0, strict);
				}
				double th = TEETER_HOLD + TEETER_WOBBLE * sin(2 * Math.PI * p1 / TEETER_WOBBLE_MS, strict) * exp(-p1 / 160.0, strict);
				double s = Math.min(1, (ms - p0 - p1) / p2);
				double target = sub > 0 ? Math.PI / 2 : 0;
				return th + (target - th) * s * s;
			}
			case DieSample.ROCK:
				return p0 * exp(-ms / ROCK_TAU, strict) * sin(Math.PI * Math.min(1, ms / tt), strict);
			default: {
				double s = Math.min(1, ms / tt);
				return Math.PI / 2 * (s + TIP_A * sin(2 * Math.PI * s, strict) / (2 * Math.PI));
			}
		}
	}

	/** Angular speed of a pivot segment at local {@code ms} (rad/s), by a central difference of {@link #theta}. */
	private double thetaRate(double ms) {
		double h = 0.05;
		double a = theta(Math.max(0, ms - h), false);
		double b = theta(Math.min(t1 - t0, ms + h), false);
		double dt = (Math.min(t1 - t0, ms + h) - Math.max(0, ms - h)) / 1000.0;
		return dt > 0 ? Math.abs(b - a) / dt : 0;
	}

	void eval(double ms, DieSample o, double g) {
		double local = Math.max(0, Math.min(ms, t1) - t0);
		if (kind == DieSample.HELD) local = 0;
		double tau = local / 1000.0;
		o.phase = kind;
		switch (kind) {
			case DieSample.HELD -> {
				o.x = x0;
				o.y = y0;
				o.zLow = z0;
				o.vz = 0;
				Quat.copy(q0, o.q);
				o.zc = z0 + Quat.support(o.q);
				o.omega = 0;
				o.speed = 0;
			}
			case DieSample.AIR -> {
				o.x = x0 + vx * tau;
				o.y = y0 + vy * tau;
				o.zLow = Math.max(0, z0 + vz * tau - 0.5 * g * tau * tau);
				o.vz = vz - g * tau;
				Quat.expMapFast(wx, wy, wz, tau, o.q);
				Quat.mul(o.q, q0, o.q);
				o.zc = o.zLow + Quat.support(o.q);
				o.omega = Math.sqrt(wx * wx + wy * wy + wz * wz);
				o.speed = Math.sqrt(vx * vx + vy * vy);
			}
			case DieSample.ROLL -> {
				if (sub == SETTLE) {
					double tt = t1 - t0;
					double s = tt > 0 ? local / tt : 1;
					Quat.slerpFast(q0, q1, s * s, o.q);
					o.x = x0 + vx * tau + 0.5 * ax * tau * tau;
					o.y = y0 + vy * tau + 0.5 * ay * tau * tau;
					o.zLow = 0;
					o.vz = 0;
					o.zc = Quat.support(o.q);
					o.omega = tt > 0 ? p0 * 2 * s / (tt / 1000.0) : 0;
					double sx = vx + ax * tau;
					double sy = vy + ay * tau;
					o.speed = Math.sqrt(sx * sx + sy * sy);
				} else {
					pivot(local, o);
				}
			}
			case DieSample.TEETER, DieSample.ROCK -> pivot(local, o);
			case DieSample.SLIDE -> {
				double tt = dur();
				double s = tt > 0 ? tau / tt : 1;
				o.x = x0 + vx * tau + 0.5 * ax * tau * tau;
				o.y = y0 + vy * tau + 0.5 * ay * tau * tau;
				o.zLow = 0;
				o.vz = 0;
				double yaw = p0 * (1 - (1 - s) * (1 - s));
				Quat.axisAngleFast(0, 0, 1, yaw, o.q);
				Quat.mul(o.q, q0, o.q);
				o.zc = Quat.support(o.q);
				o.omega = tt > 0 ? Math.abs(p0) * 2 * (1 - s) / tt : 0;
				double vxx = vx + ax * tau;
				double vyy = vy + ay * tau;
				o.speed = Math.sqrt(vxx * vxx + vyy * vyy);
			}
			default -> { // REST
				o.x = x0;
				o.y = y0;
				o.zLow = 0;
				o.vz = 0;
				o.zc = 1;
				Quat.copy(q0, o.q);
				o.omega = 0;
				o.speed = 0;
			}
		}
	}

	private void pivot(double local, DieSample o) {
		double th = theta(local, false);
		double c = Math.cos(th);
		double s = Math.sin(th);
		double along = -c + s;
		o.x = px + dx * along;
		o.y = py + dy * along;
		o.zLow = 0;
		o.vz = 0;
		// rotation about a = z × d = (−dy, dx, 0): top tips toward d
		Quat.axisAngleFast(-dy, dx, 0, th, o.q);
		Quat.mul(o.q, q0, o.q);
		o.zc = Quat.support(o.q);
		double rate = thetaRate(local);
		o.omega = rate;
		o.speed = rate * Math.abs(s + c);
	}
}

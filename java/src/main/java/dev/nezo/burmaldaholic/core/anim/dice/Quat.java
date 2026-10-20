package dev.nezo.burmaldaholic.core.anim.dice;

/**
 * Double-precision unit quaternions as {@code double[4] = {w, x, y, z}} (PHASE2B §3.3, DI §2.1). Pure, static and
 * allocation-free: every operation writes into a caller-owned {@code out} array, which may alias an input.
 *
 * <p>Convention: a quaternion {@code q} rotates a vector {@code v} as {@code q·v·q*}; {@code mul(a, b)} is the
 * rotation "first {@code b}, then {@code a}". The dice tray frame is {@code x, y} on the floor and {@code z} up
 * (tray units: 1 = the die half-edge {@code h}).
 *
 * <p>Transcendentals use {@link StrictMath} (bit-identical on every JVM and CPU, so the solver makes the same choices
 * on the server and every client). The {@code …Fast} variants use {@link Math} (intrinsics, never allocating; within
 * 1 ulp) and are meant for per-frame sampling only.
 *
 * <p>Two angle measures are used and named distinctly:
 * <ul>
 *   <li>{@link #angleTo}: the rotation angle between two orientations (radians, 0–π), the "how far did the die turn"
 *       measure used by the continuity tests;</li>
 *   <li>{@link #arcTo}: the arc between the two unit 4-vectors on S³ (radians, 0–π/2) = half the rotation angle; the
 *       orientation-atlas covering radius (CS §4.2, ≤ 22°) is measured with it.</li>
 * </ul>
 */
public final class Quat {
	/** Index of the scalar part. */
	public static final int W = 0;

	private Quat() {}

	/** A new identity quaternion (allocation; for setup code). */
	public static double[] identity() {
		return new double[] {1, 0, 0, 0};
	}

	/** Writes {@code (w, x, y, z)} into {@code out}; returns {@code out}. */
	public static double[] set(double[] out, double w, double x, double y, double z) {
		out[0] = w;
		out[1] = x;
		out[2] = y;
		out[3] = z;
		return out;
	}

	/** Copies {@code a} into {@code out}; returns {@code out}. */
	public static double[] copy(double[] a, double[] out) {
		out[0] = a[0];
		out[1] = a[1];
		out[2] = a[2];
		out[3] = a[3];
		return out;
	}

	/** {@code out = a·b} (apply {@code b}, then {@code a}). {@code out} may alias {@code a} or {@code b}. */
	public static double[] mul(double[] a, double[] b, double[] out) {
		double w = a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3];
		double x = a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2];
		double y = a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1];
		double z = a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0];
		return set(out, w, x, y, z);
	}

	/** Conjugate (the inverse of a unit quaternion). */
	public static double[] conj(double[] a, double[] out) {
		return set(out, a[0], -a[1], -a[2], -a[3]);
	}

	/** Euclidean norm of the 4-vector. */
	public static double norm(double[] a) {
		return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2] + a[3] * a[3]);
	}

	/** {@code out = a / |a|}; the identity when {@code |a| = 0}. */
	public static double[] normalize(double[] a, double[] out) {
		double n = norm(a);
		if (n == 0) return set(out, 1, 0, 0, 0);
		return set(out, a[0] / n, a[1] / n, a[2] / n, a[3] / n);
	}

	/** 4-D dot product. */
	public static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
	}

	/**
	 * Rotates the vector {@code (vx, vy, vz)} by {@code q} into {@code out[off..off+2]} (q·v·q*, expanded; no
	 * allocation).
	 */
	public static void rotate(double[] q, double vx, double vy, double vz, double[] out, int off) {
		double w = q[0];
		double x = q[1];
		double y = q[2];
		double z = q[3];
		// t = 2 · (q.xyz × v)
		double tx = 2 * (y * vz - z * vy);
		double ty = 2 * (z * vx - x * vz);
		double tz = 2 * (x * vy - y * vx);
		out[off] = vx + w * tx + (y * tz - z * ty);
		out[off + 1] = vy + w * ty + (z * tx - x * tz);
		out[off + 2] = vz + w * tz + (x * ty - y * tx);
	}

	/** {@link #rotate(double[], double, double, double, double[], int)} into {@code out[0..2]}. */
	public static double[] rotate(double[] q, double[] v, double[] out) {
		rotate(q, v[0], v[1], v[2], out, 0);
		return out;
	}

	/**
	 * Rotation by {@code angle} radians about the axis {@code (ax, ay, az)} (normalised here; a zero axis gives the
	 * identity). Uses {@code sin}/{@code cos}: sampling and setup only, never inside a solver recurrence.
	 */
	public static double[] axisAngle(double ax, double ay, double az, double angle, double[] out) {
		double n = Math.sqrt(ax * ax + ay * ay + az * az);
		if (n == 0 || angle == 0) return set(out, 1, 0, 0, 0);
		double s = StrictMath.sin(angle / 2) / n;
		return set(out, StrictMath.cos(angle / 2), ax * s, ay * s, az * s);
	}

	/**
	 * Exponential map of a rotation vector: rotation by {@code |ω|·t} about {@code ω/|ω|} (angular velocity {@code ω}
	 * rad/s held for {@code t} s).
	 */
	public static double[] expMap(double wx, double wy, double wz, double t, double[] out) {
		double n = Math.sqrt(wx * wx + wy * wy + wz * wz);
		if (n == 0 || t == 0) return set(out, 1, 0, 0, 0);
		double half = n * t / 2;
		double s = StrictMath.sin(half) / n;
		return set(out, StrictMath.cos(half), wx * s, wy * s, wz * s);
	}

	/** {@link #axisAngle} with {@link Math} (sampling). */
	public static double[] axisAngleFast(double ax, double ay, double az, double angle, double[] out) {
		double n = Math.sqrt(ax * ax + ay * ay + az * az);
		if (n == 0 || angle == 0) return set(out, 1, 0, 0, 0);
		double s = Math.sin(angle / 2) / n;
		return set(out, Math.cos(angle / 2), ax * s, ay * s, az * s);
	}

	/** {@link #expMap} with {@link Math} (sampling). */
	public static double[] expMapFast(double wx, double wy, double wz, double t, double[] out) {
		double n = Math.sqrt(wx * wx + wy * wy + wz * wz);
		if (n == 0 || t == 0) return set(out, 1, 0, 0, 0);
		double half = n * t / 2;
		double s = Math.sin(half) / n;
		return set(out, Math.cos(half), wx * s, wy * s, wz * s);
	}

	/** {@link #slerp} with {@link Math} (sampling). */
	public static double[] slerpFast(double[] a, double[] b, double u, double[] out) {
		return slerp(a, b, u, out, false);
	}

	/**
	 * Spherical interpolation from {@code a} (u = 0) to {@code b} (u = 1) along the shorter arc (the sign of
	 * {@code b} is flipped when {@code a·b < 0}). Falls back to a normalised lerp for nearly equal inputs.
	 */
	public static double[] slerp(double[] a, double[] b, double u, double[] out) {
		return slerp(a, b, u, out, true);
	}

	private static double[] slerp(double[] a, double[] b, double u, double[] out, boolean strict) {
		double d = dot(a, b);
		double s = 1;
		if (d < 0) {
			d = -d;
			s = -1;
		}
		double ka;
		double kb;
		if (d > 0.9999995) {
			ka = 1 - u;
			kb = u;
		} else {
			double th = strict ? StrictMath.acos(Math.min(1, d)) : Math.acos(Math.min(1, d));
			double sn = strict ? StrictMath.sin(th) : Math.sin(th);
			ka = (strict ? StrictMath.sin((1 - u) * th) : Math.sin((1 - u) * th)) / sn;
			kb = (strict ? StrictMath.sin(u * th) : Math.sin(u * th)) / sn;
		}
		kb *= s;
		set(out, ka * a[0] + kb * b[0], ka * a[1] + kb * b[1], ka * a[2] + kb * b[2], ka * a[3] + kb * b[3]);
		return normalize(out, out);
	}

	/** Rotation angle between the orientations {@code a} and {@code b}, radians in [0, π] (sign-insensitive). */
	public static double angleTo(double[] a, double[] b) {
		return 2 * arcTo(a, b);
	}

	/** Arc between {@code ±a} and {@code b} on S³, radians in [0, π/2] (= half of {@link #angleTo}). */
	public static double arcTo(double[] a, double[] b) {
		double d = Math.abs(dot(a, b));
		return StrictMath.acos(Math.min(1, d));
	}

	/** Rotation angle of {@code q} itself (radians, 0–π). */
	public static double angle(double[] q) {
		return 2 * StrictMath.acos(Math.min(1, Math.abs(q[0]) / Math.max(1e-300, norm(q))));
	}

	/** Makes the sign canonical (first non-zero component positive) so equal rotations compare equal. */
	public static double[] canonical(double[] a, double[] out) {
		double s = 1;
		for (int i = 0; i < 4; i++) {
			if (Math.abs(a[i]) > 1e-12) {
				s = a[i] < 0 ? -1 : 1;
				break;
			}
		}
		return set(out, s * a[0], s * a[1], s * a[2], s * a[3]);
	}

	/**
	 * Support height of a cube of half-edge 1 with orientation {@code q}: the height of its centre above the plane
	 * it rests on = the cube's extent toward {@code +z} = {@code |R₃₁| + |R₃₂| + |R₃₃|} (1 face down, √2 on an edge,
	 * √3 on a corner). DI §2.2.
	 */
	public static double support(double[] q) {
		double w = q[0];
		double x = q[1];
		double y = q[2];
		double z = q[3];
		// third row of the rotation matrix
		double r31 = 2 * (x * z - w * y);
		double r32 = 2 * (y * z + w * x);
		double r33 = 1 - 2 * (x * x + y * y);
		return Math.abs(r31) + Math.abs(r32) + Math.abs(r33);
	}

	/**
	 * In-plane half-extent of a cube of half-edge 1 along the horizontal unit direction {@code (dx, dy)}: the
	 * support function {@code Σ |R·e_i · d|} (1 to √3).
	 */
	public static double supportAlong(double[] q, double dx, double dy) {
		double w = q[0];
		double x = q[1];
		double y = q[2];
		double z = q[3];
		double r11 = 1 - 2 * (y * y + z * z);
		double r12 = 2 * (x * y - w * z);
		double r13 = 2 * (x * z + w * y);
		double r21 = 2 * (x * y + w * z);
		double r22 = 1 - 2 * (x * x + z * z);
		double r23 = 2 * (y * z - w * x);
		return Math.abs(r11 * dx + r21 * dy) + Math.abs(r12 * dx + r22 * dy) + Math.abs(r13 * dx + r23 * dy);
	}

	/**
	 * The orientation seen in a mirror {@code x → −x}: the reflection conjugates the rotation,
	 * {@code (w, x, y, z) → (w, x, −y, −z)}. A reflected die is chiral, so the body's ±x faces trade places; see
	 * {@link #mirrorXKeepUp} to draw a mirrored die that still shows the same number on top.
	 */
	public static double[] mirrorX(double[] q, double[] out) {
		return set(out, q[0], q[1], -q[2], -q[3]);
	}

	/**
	 * {@link #mirrorX} for drawing (the right duel lane from a left-lane roll): when the mirror would turn a ±x body face
	 * (labels 3 / 4) up, a 180° body turn about y swaps them back, so the top face keeps its number (the hidden
	 * bottom face is the one that changes).
	 */
	public static double[] mirrorXKeepUp(double[] q, double[] out) {
		int up = CubeGroup.faceUp(q);
		mirrorX(q, out);
		if (CubeGroup.faceUp(out) != up) {
			// out · Ry(180°) = out · (0, 0, 1, 0)
			double w = out[0], x = out[1], y = out[2], z = out[3];
			set(out, -y, -z, w, x);
		}
		return out;
	}

	/**
	 * Tray frame (x east, y south, z up) → Minecraft model frame (X east, Y up, Z south): the axis swap y ↔ z is a
	 * reflection, so {@code (w, x, y, z) → (w, −x, −z, −y)}. Rotating a tray vector and swapping equals swapping and
	 * rotating with the result.
	 */
	public static double[] trayToMc(double[] q, double[] out) {
		return set(out, q[0], -q[1], -q[3], -q[2]);
	}
}

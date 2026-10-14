package dev.nezo.burmaldaholic.core.anim.dice;

/**
 * The 24 rotations of a cube (the chiral octahedral group) and the canonical die model (PHASE2B §3.3, DI §2.3). Pure.
 *
 * <p><b>Canonical model</b> (tray frame: {@code x} east/right, {@code y} south/toward the viewer, {@code z} up; the
 * same labels as {@link DiceFaces#layout layout(1)} and the {@code extras/dice_display} item model): face 1 on
 * {@code +z}, 6 on {@code −z}, 2 on {@code −y} (north), 5 on {@code +y} (south), 3 on {@code +x} (east), 4 on
 * {@code −x} (west). Opposite faces sum to 7.
 *
 * <p><b>Element order</b>: {@code element(4·(u−1) + k) = Rz(k·90°) · Up(u)}, where {@code Up(u)} is the shortest
 * rotation that turns face {@code u}'s normal to {@code +z}. So {@code faceUp(element(i)) == i/4 + 1} and the
 * quarter turn about the vertical is {@code i % 4}. Element 0 is the identity. Components are exactly
 * {@code 0, ±½, ±√½, ±1} (snapped), sign-canonical (first non-zero component positive).
 *
 * <p><b>Relabelling</b> (DI §2.3): a solved canonical motion {@code Q(t)} ending with body face {@code b} up is shown
 * as the model {@code M(t) = Q(t)·L} with {@code L = element(relabel(b, want, k))}; then
 * {@code faceUp(Q_end·L) == want}. Right-multiplying by a cube symmetry only changes which pips are painted on which
 * body face: positions, contacts and timing are untouched.
 */
public final class CubeGroup {
	/** Number of elements. */
	public static final int SIZE = 24;

	private static final double S = Math.sqrt(0.5);
	/** Unit normal (x, y, z) of face label 1..6 on the canonical model (index 0 unused). */
	private static final int[][] NORMAL = {null, {0, 0, 1}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, 0, -1}};
	private static final double[][] ELEMENTS = new double[SIZE][];

	static {
		double[] up = new double[4];
		double[] yaw = new double[4];
		for (int u = 1; u <= 6; u++) {
			upRotation(u, up);
			for (int k = 0; k < 4; k++) {
				Quat.axisAngle(0, 0, 1, k * Math.PI / 2, yaw);
				double[] e = Quat.mul(yaw, up, new double[4]);
				snap(e);
				Quat.canonical(e, e);
				ELEMENTS[4 * (u - 1) + k] = e;
			}
		}
	}

	private CubeGroup() {}

	private static void upRotation(int u, double[] out) {
		int[] n = NORMAL[u];
		if (n[2] == 1) {
			Quat.set(out, 1, 0, 0, 0);
		} else if (n[2] == -1) {
			Quat.axisAngle(1, 0, 0, Math.PI, out);
		} else {
			// axis n × z, angle 90°
			Quat.axisAngle(n[1], -n[0], 0, Math.PI / 2, out);
		}
	}

	private static void snap(double[] q) {
		for (int i = 0; i < 4; i++) {
			double a = Math.abs(q[i]);
			double s = Math.signum(q[i]);
			if (a < 0.25) q[i] = 0;
			else if (a < 0.6) q[i] = s * 0.5;
			else if (a < 0.85) q[i] = s * S;
			else q[i] = s;
		}
	}

	/** Element {@code i} (0–23) as {@code {w, x, y, z}}. Returns a shared array: do not modify. */
	public static double[] element(int i) {
		return ELEMENTS[i];
	}

	/** Copies element {@code i} into {@code out}. */
	public static double[] element(int i, double[] out) {
		return Quat.copy(ELEMENTS[i], out);
	}

	/** Index of the element with face {@code face} (1–6) up, turned {@code quarter}·90° about the vertical. */
	public static int index(int face, int quarter) {
		if (face < 1 || face > 6) throw new IllegalArgumentException("face " + face);
		return 4 * (face - 1) + Math.floorMod(quarter, 4);
	}

	/** Outward normal of face label {@code face} on the canonical model (shared array: do not modify). */
	public static int[] normal(int face) {
		return NORMAL[face];
	}

	/** Index of the group element nearest to {@code q} (largest {@code |q·e|}; ties keep the lower index). */
	public static int nearest(double[] q) {
		int best = 0;
		double bd = -1;
		for (int i = 0; i < SIZE; i++) {
			double d = Math.abs(Quat.dot(q, ELEMENTS[i]));
			if (d > bd + 1e-15) {
				bd = d;
				best = i;
			}
		}
		return best;
	}

	/** True when {@code q} is a group element within {@code eps} ({@code ||q·e| − 1| ≤ eps} for some e). */
	public static boolean isElement(double[] q, double eps) {
		return Math.abs(Math.abs(Quat.dot(q, ELEMENTS[nearest(q)])) - 1) <= eps;
	}

	/**
	 * Label (1–6) of the canonical model's face that points most nearly up ({@code +z}) under orientation {@code q}.
	 * Works for any orientation (not only group elements): the face whose rotated normal has the largest
	 * {@code z}.
	 */
	public static int faceUp(double[] q) {
		double w = q[0];
		double x = q[1];
		double y = q[2];
		double z = q[3];
		double n = w * w + x * x + y * y + z * z;
		// third row of the rotation matrix (scaled by |q|² so un-normalised input still ranks correctly)
		double r31 = 2 * (x * z - w * y);
		double r32 = 2 * (y * z + w * x);
		double r33 = n - 2 * (x * x + y * y);
		int best = 1;
		double bz = r33;
		if (-r33 > bz) {
			bz = -r33;
			best = 6;
		}
		if (-r32 > bz) {
			bz = -r32;
			best = 2;
		}
		if (r32 > bz) {
			bz = r32;
			best = 5;
		}
		if (r31 > bz) {
			bz = r31;
			best = 3;
		}
		if (-r31 > bz) {
			best = 4;
		}
		return best;
	}

	/**
	 * The relabelling element (DI §2.3): index of {@code L} such that for every {@code Q} with
	 * {@code faceUp(Q) == bodyUp} (at rest), {@code faceUp(Q·L) == want}. The four such {@code L} are the spins about
	 * the wanted face; {@code k} (0–3, taken mod 4) picks one. {@code L = Up(bodyUp)⁻¹ · Rz(k·90°) · Up(want)}.
	 */
	public static int relabel(int bodyUp, int want, int k) {
		double[] a = ELEMENTS[index(bodyUp, 0)];
		double[] b = ELEMENTS[index(want, k)];
		double[] inv = Quat.conj(a, new double[4]);
		double[] l = Quat.mul(inv, b, new double[4]);
		return nearest(l);
	}

	/** Index of {@code element(a)·element(b)}. */
	public static int compose(int a, int b) {
		return nearest(Quat.mul(ELEMENTS[a], ELEMENTS[b], new double[4]));
	}
}

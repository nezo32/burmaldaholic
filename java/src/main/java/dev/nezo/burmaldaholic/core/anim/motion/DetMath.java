package dev.nezo.burmaldaholic.core.anim.motion;

/**
 * Deterministic, allocation-free sin/cos for the motion kit's per-frame sampling.
 *
 * <p>{@link StrictMath} is bit-identical on every CPU and JVM (unlike {@link Math}'s intrinsics), but on JDK 25 its
 * pure-Java FdLibm {@code sin}/{@code cos} allocate a reduction buffer on EVERY call, which breaks the kit's
 * zero-allocation sampling. Here the argument is reduced with Cody–Waite steps (fdlibm's three-part π/2, exact for
 * |x| &lt; ~1.6e6 rad) to |r| ≤ π/4 and evaluated with fdlibm's {@code __kernel_sin}/{@code __kernel_cos}
 * polynomials. Only correctly rounded IEEE {@code + − ×} are used (no FMA, no intrinsics), so results are identical
 * everywhere and within a few ulp of {@link StrictMath}. Beyond the exact range it falls back to {@link StrictMath}
 * (deterministic, but allocating).
 */
public final class DetMath {
	private static final double INV_PIO2 = 6.36619772367581382433e-01;
	private static final double PIO2_1 = 1.57079632673412561417e+00;
	private static final double PIO2_2 = 6.07710050630396597660e-11;
	private static final double PIO2_3 = 2.02226624879595063154e-21;
	private static final double LIMIT = 1.0e6;

	private DetMath() {}

	/** sin(x), deterministic and allocation-free for |x| &lt; 1e6. */
	public static double sin(double x) {
		if (!(Math.abs(x) < LIMIT)) return StrictMath.sin(x);
		double n = Math.rint(x * INV_PIO2);
		double r = reduce(x, n);
		switch ((int) ((long) n & 3)) {
			case 0:
				return kSin(r);
			case 1:
				return kCos(r);
			case 2:
				return -kSin(r);
			default:
				return -kCos(r);
		}
	}

	/** cos(x), deterministic and allocation-free for |x| &lt; 1e6. */
	public static double cos(double x) {
		if (!(Math.abs(x) < LIMIT)) return StrictMath.cos(x);
		double n = Math.rint(x * INV_PIO2);
		double r = reduce(x, n);
		switch ((int) ((long) n & 3)) {
			case 0:
				return kCos(r);
			case 1:
				return -kSin(r);
			case 2:
				return -kCos(r);
			default:
				return kSin(r);
		}
	}

	private static final double S1 = -1.66666666666666324348e-01;
	private static final double S2 = 8.33333333332248946124e-03;
	private static final double S3 = -1.98412698298579493134e-04;
	private static final double S4 = 2.75573137070700676789e-06;
	private static final double S5 = -2.50507602534068634195e-08;
	private static final double S6 = 1.58969099521155010221e-10;
	private static final double C1 = 4.16666666666666019037e-02;
	private static final double C2 = -1.38888888888741095749e-03;
	private static final double C3 = 2.48015872894767294178e-05;
	private static final double C4 = -2.75573143513906633035e-07;
	private static final double C5 = 2.08757232129817482790e-09;
	private static final double C6 = -1.13596475577881948265e-11;

	/** fdlibm {@code __kernel_sin(x, 0, 0)} for |x| ≤ ~π/4. */
	static double kSin(double x) {
		if (Math.abs(x) < 0x1p-27) return x;
		double z = x * x;
		double v = z * x;
		double r = S2 + z * (S3 + z * (S4 + z * (S5 + z * S6)));
		return x + v * (S1 + z * r);
	}

	/** fdlibm {@code __kernel_cos(x, 0)} for |x| ≤ ~π/4. */
	static double kCos(double x) {
		int ix = (int) (Double.doubleToRawLongBits(x) >>> 32) & 0x7fffffff;
		if (ix < 0x3e400000) return 1.0;
		double z = x * x;
		double r = z * (C1 + z * (C2 + z * (C3 + z * (C4 + z * (C5 + z * C6)))));
		if (ix < 0x3FD33333) return 1.0 - (0.5 * z - z * r);
		double qx = ix > 0x3fe90000 ? 0.28125 : Double.longBitsToDouble((long) (ix - 0x00200000) << 32);
		double hz = 0.5 * z - qx;
		double a = 1.0 - qx;
		return a - (hz - z * r);
	}

	/** x − n·π/2 in three exact steps (n·PIO2_1 is exact: PIO2_1 has 33 significant bits and |n| &lt; 2^20). */
	private static double reduce(double x, double n) {
		double r = x - n * PIO2_1;
		r -= n * PIO2_2;
		return r - n * PIO2_3;
	}
}

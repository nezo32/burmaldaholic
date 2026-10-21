package dev.nezo.burmaldaholic.client.fx.motion;

import dev.nezo.burmaldaholic.core.anim.motion.Oscillation;
import dev.nezo.burmaldaholic.core.anim.motion.PixelSnap;
import org.joml.Matrix3x2fStack;

/**
 * A damped GUI shake (PL §3.5 bin impact {@code A·e^(−t/τ)·sin(2π·22·t)} on x and half of that on y; DI §5.2 stamp;
 * CS §4.5 seven-out pill), snapped to physical px. Gated by {@link MotionFx#shake}: with reduced motion the pose is
 * pushed unchanged, so push/pop stay balanced.
 *
 * <p>The GUI pose of 26.x is a {@code Matrix3x2fStack} ({@code GuiGraphicsExtractor.pose()}), which is what the
 * spec's "PoseStack" means here.
 */
public final class GuiShake {
	private GuiShake() {}

	/**
	 * Pushes the pose and translates it by the shake offset at {@code tMs}; the caller pops with
	 * {@link #pop(Matrix3x2fStack)} after drawing the shaken group.
	 *
	 * @param p     the GUI pose
	 * @param a     amplitude in layout px (x; y gets half)
	 * @param tauMs decay time constant (ms, > 0)
	 * @param hz    frequency in Hz (the spec's 22 Hz; {@code Oscillation} itself takes periods in ms)
	 * @param tMs   ms since the impact (negative = not started)
	 * @param k     physical px per layout px (snapping)
	 */
	public static void push(Matrix3x2fStack p, double a, double tauMs, double hz, double tMs, int k) {
		push(p, a, tauMs, hz, tMs, k, "gui_shake");
	}

	/** {@link #push(Matrix3x2fStack, double, double, double, double, int)} recorded under {@code what} (test recorder). */
	public static void push(Matrix3x2fStack p, double a, double tauMs, double hz, double tMs, int k, String what) {
		p.pushMatrix();
		double raw = raw(a, tauMs, hz, tMs, what);
		if (raw == 0) return;
		int kk = Math.max(1, k);
		// x and y are snapped from the UNSNAPPED value, so the y shake stays symmetric
		p.translate(PixelSnap.snapF(raw, kk), PixelSnap.snapF(raw / 2, kk));
	}

	/** Pops what {@link #push} pushed. */
	public static void pop(Matrix3x2fStack p) {
		p.popMatrix();
	}

	/** The snapped x offset of the shake (0 when finished, not started, or gated off). */
	public static double offset(double a, double tauMs, double hz, double tMs, int k, String what) {
		double raw = raw(a, tauMs, hz, tMs, what);
		return raw == 0 ? 0 : PixelSnap.snap(raw, Math.max(1, k));
	}

	/** Unsnapped x offset; 0 when finished, not started, gated off, or with a non-positive frequency / decay. */
	private static double raw(double a, double tauMs, double hz, double tMs, String what) {
		if (!(hz > 0)) return 0;
		double raw = Oscillation.shake(a, tauMs, Oscillation.periodOfHz(hz), tMs);
		if (raw == 0 || !MotionFx.shake(what)) return 0;
		return raw;
	}
}

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
	 * @param tauMs decay time constant (ms)
	 * @param hz    frequency
	 * @param tMs   ms since the impact (negative = not started)
	 * @param k     physical px per layout px (snapping)
	 */
	public static void push(Matrix3x2fStack p, double a, double tauMs, double hz, double tMs, int k) {
		p.pushMatrix();
		double dx = offset(a, tauMs, hz, tMs, k, "gui_shake");
		if (dx != 0) p.translate((float) dx, PixelSnap.snapF(dx / 2, k));
	}

	/** Pops what {@link #push} pushed. */
	public static void pop(Matrix3x2fStack p) {
		p.popMatrix();
	}

	/** The snapped x offset of the shake (0 when finished, not started, or gated off). */
	public static double offset(double a, double tauMs, double hz, double tMs, int k, String what) {
		double raw = Oscillation.shake(a, tauMs, hz, tMs);
		if (raw == 0 || !MotionFx.shake(what)) return 0;
		return PixelSnap.snap(raw, Math.max(1, k));
	}
}

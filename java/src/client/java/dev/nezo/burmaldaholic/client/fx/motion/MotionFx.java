package dev.nezo.burmaldaholic.client.fx.motion;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.anim.motion.FxGate;
import dev.nezo.burmaldaholic.core.anim.motion.FxGate.Kind;

/**
 * Client facade of {@link FxGate} (PHASE2B §3.2): every decorative motion effect asks here first, with the player's
 * {@link FxSettings#reduceMotion()} and {@link FxSettings#flashes()}:
 *
 * <pre>{@code
 * if (MotionFx.shake("plinko_bin_top")) GuiShake.push(g.pose(), 2, 450, 22, t, k);
 * if (MotionFx.particles("plinko_spark", 24)) pool.spawn(...);
 * }</pre>
 *
 * <p>Tests install a {@link #recorder} (e.g. {@link FxGate.Counter}) that sees every effect that PASSED the gate, and
 * assert zero shake/particle/streak calls under reduced motion. Game facades ({@code PlinkoFx}, {@code RouletteFx},
 * {@code DiceFx}) may wrap this class.
 */
public final class MotionFx {
	/** Test hook: receives every effect that passed the gate (null in production). */
	public static volatile FxGate.Recorder recorder;

	private MotionFx() {}

	/** A shake may run. */
	public static boolean shake(String what) {
		return pass(Kind.SHAKE, what);
	}

	/** {@code count} particles may spawn (0 still asks the gate, so a test sees intent). */
	public static boolean particles(String what, int count) {
		return pass(Kind.PARTICLES, what);
	}

	/** A flash (white frame, overlay, blink) may run: flashes on and no reduced motion. */
	public static boolean flash(String what) {
		return pass(Kind.FLASH, what);
	}

	/** A streak / trail / after-image may be drawn. */
	public static boolean streak(String what) {
		return pass(Kind.STREAK, what);
	}

	/** Other decorative motion (pops, wobble, bob, slides, loupe) may run. */
	public static boolean motion(String what) {
		return pass(Kind.MOTION, what);
	}

	/** True when reduced motion is on (the effect-free path of each game). */
	public static boolean reduced() {
		return FxSettings.reduceMotion();
	}

	/** Gate of any kind. */
	public static boolean pass(Kind kind, String what) {
		return FxGate.pass(kind, what, FxSettings.reduceMotion(), kind == Kind.FLASH && FxSettings.flashes(), recorder);
	}
}

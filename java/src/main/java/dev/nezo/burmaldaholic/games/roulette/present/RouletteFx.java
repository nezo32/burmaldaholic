package dev.nezo.burmaldaholic.games.roulette.present;

import dev.nezo.burmaldaholic.core.anim.motion.FxGate;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteSpin;
import org.jspecify.annotations.Nullable;

/**
 * What the roulette wheel draws in one frame (ROULETTE-ANIM-SPEC §3.5, §3.10, §9.1-10): the ball (and its alpha), the
 * rotor angle, and which decorations pass the {@link FxGate} (streak, sparks, diamond flash, loupe, focus zoom, chip
 * shimmer). Pure, so reduced motion is unit-tested with a {@link FxGate.Counter}: with reduced motion nothing decorative
 * passes, the wheel is static (the last result at the top) and the ball is invisible until it fades into the result
 * pocket at {@code restMs}.
 */
public final class RouletteFx {
	/** Fade-in of the ball at rest with reduced motion. */
	public static final double REDUCED_FADE_MS = 300;

	/** A frame plan (reused). */
	public static final class Frame {
		public boolean ball;
		public double ballAlpha;
		/** Rotor angle to draw (the spin's model, or static with reduced motion). */
		public double rotor;
		/** Ball angle to draw (absolute). */
		public double ballAbs;
		public boolean streak;
		public boolean loupe;
		public boolean focus;
		public boolean flash;
	}

	private RouletteFx() {}

	/**
	 * Plans a frame of {@code spin} at {@code tMs} (ms since the launch) from its sample {@code s} (already sampled at
	 * {@code tMs}).
	 *
	 * @param last the number the previous ball rests in (-1 = none): the static wheel of reduced motion shows it on top
	 */
	public static Frame plan(RouletteSpin spin, RouletteSpin.Sample s, double tMs, int last, boolean reduce, boolean flashes,
			FxGate.@Nullable Recorder rec, Frame out) {
		boolean rest = spin.settled(tMs);
		if (reduce) {
			int top = rest ? spin.result : last;
			out.rotor = top >= 0 ? -RouletteSpin.pocketAngle(top) : 0;
			out.ball = rest;
			out.ballAlpha = rest ? Math.min(1, (tMs - spin.restMs()) / REDUCED_FADE_MS) : 0;
			out.ballAbs = out.rotor + (rest ? RouletteSpin.pocketAngle(spin.result) : 0);
			out.streak = out.loupe = out.focus = out.flash = false;
			return out;
		}
		out.rotor = s.rotor;
		out.ball = s.visible;
		out.ballAlpha = s.alpha;
		out.ballAbs = s.abs;
		out.streak = s.visible && s.omega > 200 && FxGate.pass(FxGate.Kind.STREAK, "streak", false, flashes, rec);
		out.loupe = rest && FxGate.pass(FxGate.Kind.MOTION, "loupe", false, flashes, rec);
		out.focus = FxGate.pass(FxGate.Kind.MOTION, "focus", false, flashes, rec);
		out.flash = FxGate.allows(FxGate.Kind.FLASH, false, flashes);
		return out;
	}

	/** Whether a spark / particle burst may fire (contacts, deflector). */
	public static boolean spark(String what, boolean reduce, boolean flashes, FxGate.@Nullable Recorder rec) {
		return FxGate.pass(FxGate.Kind.PARTICLES, what, reduce, flashes, rec);
	}

	/** Whether the diamond flash overlay may play. */
	public static boolean diamondFlash(boolean reduce, boolean flashes, FxGate.@Nullable Recorder rec) {
		return FxGate.pass(FxGate.Kind.FLASH, "diamond_flash", reduce, flashes, rec);
	}

	/** Whether a decorative motion (shimmer, chip flights, dolly drop, rake sweep, payout slide) may run. */
	public static boolean motion(String what, boolean reduce, FxGate.@Nullable Recorder rec) {
		return FxGate.pass(FxGate.Kind.MOTION, what, reduce, true, rec);
	}
}

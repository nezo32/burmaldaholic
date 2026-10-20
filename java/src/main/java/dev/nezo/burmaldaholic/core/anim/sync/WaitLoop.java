package dev.nezo.burmaldaholic.core.anim.sync;

/**
 * The result-free motion a game shows while it WAITS for the server's result (SY-SPEC §3): a reel cruising, dice
 * rattling in the hand, the roulette ball's stage-A orbit. Closed form in loop time {@code t} (ms since the press).
 *
 * @param <P> the pose type (an angle, a reel offset, a dice transform ...)
 */
public interface WaitLoop<P> {
	P pose(double t);

	/** d pose / dt in pose units per ms. */
	P velocity(double t);

	/** The first loop time ≥ {@code t} at which the loop may hand off (e.g. the next cell boundary); default now. */
	default double nextExit(double t) {
		return t;
	}
}

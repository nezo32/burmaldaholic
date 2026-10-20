package dev.nezo.burmaldaholic.core.anim.motion;

/**
 * How the player's animation speed setting ({@code FxSettings.speed()}: SLOW 0.5, NORMAL 1, TURBO 1.5) applies to a
 * piece of motion (PHASE2B §3.1, decision D1).
 *
 * <ul>
 *   <li>{@link #SHARED}: motion the server reveals against or other viewers see at the same time (ball flight, wheel
 *       spin, dice throw, puck, PvP). NEVER slowed. TURBO compresses it only where the client itself posts the result
 *       (Plinko, house dice), else it runs at 1×.</li>
 *   <li>{@link #LOCAL}: decoration only this client sees (chip beats, stick, pops, idle). SLOW stretches and TURBO
 *       compresses it.</li>
 * </ul>
 */
public enum SpeedPolicy {
	SHARED,
	LOCAL;

	/**
	 * Animation time after {@code ms} of real time.
	 *
	 * @param ms                real (clock) time since the motion started
	 * @param speed             speed factor (0.5 / 1 / 1.5; {@code pct/100})
	 * @param clientPostsResult true when this client posts the result itself (Plinko, house dice), so TURBO may
	 *                          compress shared time
	 */
	public double scale(double ms, double speed, boolean clientPostsResult) {
		return ms * factor(speed, clientPostsResult);
	}

	/** Real duration of a beat that lasts {@code animMs} of animation time (inverse of {@link #scale}). */
	public double duration(double animMs, double speed, boolean clientPostsResult) {
		return animMs / factor(speed, clientPostsResult);
	}

	/** The effective time factor (animation ms per real ms). */
	public double factor(double speed, boolean clientPostsResult) {
		if (!(speed > 0)) throw new IllegalArgumentException("speed " + speed);
		return switch (this) {
			case LOCAL -> speed;
			case SHARED -> speed > 1 && clientPostsResult ? speed : 1;
		};
	}

	/** Speed factor of a percentage ({@code FxSettings.Speed.pct}: 50 / 100 / 150). */
	public static double speedOf(int pct) {
		return pct / 100.0;
	}
}

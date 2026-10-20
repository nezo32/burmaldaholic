package dev.nezo.burmaldaholic.core.anim.sync;

/**
 * When a server result began and when THIS client learned it (SY-SPEC §2): the time base a game samples its result
 * animation on.
 *
 * <ul>
 *   <li><b>Actor</b> (the player who pressed): {@code t = handoffT + (nowWall − arrivalWall)}, local time from the
 *       moment the result arrived, so the actor's own spin is never "late" and never skips.</li>
 *   <li><b>Spectator</b>: {@code t = sharedNow − startTick·50} on the shared {@code SmoothClock}, so every viewer and
 *       the in-world renderer agree. A late joiner ({@link #lateJoin}) draws only the settled state.</li>
 * </ul>
 * Either way {@link #tMs} never steps backward. Create ONE anchor per result key {@code (epoch, resultSeq)}
 * ({@link AnchorSlot}); later packets with the same key never rebuild it.
 *
 * <p>A class rather than a record because of the monotonic {@code lastT}; the components are fixed at creation.
 */
public final class ResultAnchor {
	private final long startTick;
	private final double arrivalWallMs;
	private final double sharedAtArrivalMs;
	private final boolean actor;
	private final int epoch;
	private final double handoffT;
	private double lastT = Double.NEGATIVE_INFINITY;

	public ResultAnchor(long startTick, double arrivalWallMs, double sharedAtArrivalMs, boolean actor, int epoch, double handoffT) {
		this.startTick = startTick;
		this.arrivalWallMs = arrivalWallMs;
		this.sharedAtArrivalMs = sharedAtArrivalMs;
		this.actor = actor;
		this.epoch = epoch;
		this.handoffT = handoffT;
	}

	/** A spectator anchor whose shared time at arrival is unknown (no late-join detection). */
	public static ResultAnchor of(long serverStartTick, double arrivalWallMs) {
		return new ResultAnchor(serverStartTick, arrivalWallMs, Double.NaN, false, 0, 0);
	}

	/** A spectator anchor; {@code sharedAtArrivalMs} is {@code SmoothClock.nowMs()} when the result arrived. */
	public static ResultAnchor of(long serverStartTick, double arrivalWallMs, double sharedAtArrivalMs, int epoch) {
		return new ResultAnchor(serverStartTick, arrivalWallMs, sharedAtArrivalMs, false, epoch, 0);
	}

	/** This anchor for the actor, whose result time is 0 at arrival. */
	public ResultAnchor asActor() {
		return asActor(0);
	}

	/** This anchor for the actor, whose result time is {@code handoffT} at arrival (e.g. the wait loop's time then). */
	public ResultAnchor asActor(double handoffT) {
		return new ResultAnchor(startTick, arrivalWallMs, sharedAtArrivalMs, true, epoch, handoffT);
	}

	/** Result time in ms at this frame: monotonic; actor = local since arrival, spectator = shared since startTick. */
	public double tMs(double nowWallMs, double sharedNowMs) {
		double raw = actor ? handoffT + (nowWallMs - arrivalWallMs) : sharedNowMs - startTick * 50.0;
		if (raw > lastT) lastT = raw;
		return lastT;
	}

	/** True for a spectator who arrived after a result of {@code durationMs} already ended: draw the settled state only. */
	public boolean lateJoin(double durationMs) {
		return !actor && !Double.isNaN(sharedAtArrivalMs) && sharedAtArrivalMs - startTick * 50.0 >= durationMs;
	}

	public long startTick() {
		return startTick;
	}

	public double arrivalWallMs() {
		return arrivalWallMs;
	}

	public double sharedAtArrivalMs() {
		return sharedAtArrivalMs;
	}

	public boolean actor() {
		return actor;
	}

	public int epoch() {
		return epoch;
	}

	public double handoffT() {
		return handoffT;
	}
}

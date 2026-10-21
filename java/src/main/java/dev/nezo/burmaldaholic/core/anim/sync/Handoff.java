package dev.nezo.burmaldaholic.core.anim.sync;

/**
 * A wait loop that hands off, without a jump, to the solved result path (SY-SPEC §3): before the result the loop runs;
 * when it arrives at loop time {@code tArrive}, the handoff happens at {@code tH = loop.nextExit(tArrive)} and the path
 * is {@code solver.solve(loop.pose(tH), loop.velocity(tH), result, minMs)}. {@link #sample} is C0 and C1 continuous at
 * {@code tH} (given a solver that honours its contract). Only the FIRST {@link #arrive} counts: bunched or repeated
 * packets never rebuild the path.
 */
public final class Handoff<P, R> {
	private final WaitLoop<P> loop;
	private final HandoffSolver<P, R> solver;
	private Track<P> track;
	private R result;
	private double tH = Double.NaN;

	public Handoff(WaitLoop<P> loop, HandoffSolver<P, R> solver) {
		this.loop = loop;
		this.solver = solver;
	}

	/**
	 * The result arrived at loop time {@code tArrive}; the path lasts at least {@code minMs}. Returns false (and changes
	 * nothing) when a result was already handed off.
	 */
	public boolean arrive(R result, double tArrive, double minMs) {
		if (track != null) return false;
		double exit = Math.max(tArrive, loop.nextExit(tArrive));
		this.tH = exit;
		this.result = result;
		this.track = solver.solve(loop.pose(exit), loop.velocity(exit), result, minMs);
		return true;
	}

	/** The pose at loop time {@code t}: the loop before the handoff, then the path, then the exact final pose. */
	public P sample(double t) {
		if (track == null || t < tH) return loop.pose(t);
		return track.sample(Math.min(t - tH, track.durationMs()));
	}

	public boolean arrived() {
		return track != null;
	}

	/** True once the path ended (the pose is the server's result). */
	public boolean done(double t) {
		return track != null && t >= endT();
	}

	/** Loop time of the handoff (NaN before {@link #arrive}). */
	public double handoffT() {
		return tH;
	}

	/** Loop time at which the path ends (NaN before {@link #arrive}). */
	public double endT() {
		return track == null ? Double.NaN : tH + track.durationMs();
	}

	public R result() {
		return result;
	}

	public Track<P> track() {
		return track;
	}

	public WaitLoop<P> loop() {
		return loop;
	}
}

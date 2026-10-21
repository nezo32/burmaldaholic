package dev.nezo.burmaldaholic.core.anim.sync;

/**
 * Solves the path from the wait loop's pose and velocity at the handoff to the server's result (SY-SPEC §3). The track
 * MUST start at {@code pose0} with velocity {@code vel0} (C0 + C1) and end exactly on the result's pose.
 *
 * @param <P> pose type
 * @param <R> result type (target stop, faces, pocket ...)
 */
public interface HandoffSolver<P, R> {
	Track<P> solve(P pose0, P vel0, R result, double minMs);
}

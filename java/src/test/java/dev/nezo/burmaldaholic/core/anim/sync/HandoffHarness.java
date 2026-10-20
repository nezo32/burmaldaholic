package dev.nezo.burmaldaholic.core.anim.sync;

import java.util.function.ToDoubleBiFunction;

/**
 * SY-SPEC §5: drives a {@link Handoff} frame by frame the way a screen does, with the result arriving at
 * {@code arrivalMs[0]} (loop time) and repeated / bunched copies at the later entries (which must not rebuild the path),
 * and measures the largest per-frame pose change. Each game owner writes one test with it; the acceptance bound is
 * {@code maxFrameDelta ≤ 1.15 · maxLoopSpeed · frameMs} and {@code finalPose} equal to the server result.
 */
public final class HandoffHarness {
	/** What a run did. */
	public record Result<P>(double maxFrameDelta, P finalPose, double handoffT, double endT, int rebuilds) {}

	private HandoffHarness() {}

	/** Scalar poses (an angle, a reel offset). */
	public static <R> Result<Double> run(WaitLoop<Double> loop, HandoffSolver<Double, R> solver, R result, double[] arrivalMs,
			double frameMs) {
		return run(loop, solver, result, arrivalMs, frameMs, 0, (a, b) -> Math.abs(a - b));
	}

	/** The default 16.7 ms frame. */
	public static <R> Result<Double> run(WaitLoop<Double> loop, HandoffSolver<Double, R> solver, R result, double[] arrivalMs) {
		return run(loop, solver, result, arrivalMs, 16.7);
	}

	/** Any pose type with a distance; {@code minMs} is passed to the solver. */
	public static <P, R> Result<P> run(WaitLoop<P> loop, HandoffSolver<P, R> solver, R result, double[] arrivalMs, double frameMs,
			double minMs, ToDoubleBiFunction<P, P> dist) {
		Handoff<P, R> h = new Handoff<>(loop, solver);
		int next = 0;
		int rebuilds = 0;
		double maxDelta = 0;
		P prev = h.sample(0);
		double t = 0;
		for (int frame = 1; frame < 1_000_000; frame++) {
			t = frame * frameMs;
			while (next < arrivalMs.length && arrivalMs[next] <= t) {
				boolean built = h.arrive(result, arrivalMs[next], minMs);
				if (built && next > 0) rebuilds++;
				if (!built && next == 0) throw new IllegalStateException("first arrival did not build");
				next++;
			}
			P pose = h.sample(t);
			maxDelta = Math.max(maxDelta, dist.applyAsDouble(prev, pose));
			prev = pose;
			if (next >= arrivalMs.length && h.done(t - frameMs)) break;
		}
		return new Result<>(maxDelta, h.sample(t), h.handoffT(), h.endT(), rebuilds);
	}
}

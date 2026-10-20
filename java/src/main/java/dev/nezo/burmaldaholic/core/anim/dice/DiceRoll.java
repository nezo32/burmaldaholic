package dev.nezo.burmaldaholic.core.anim.dice;

import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;

/**
 * One solved throw of N dice (PHASE2B §3.3): per die a list of closed-form segments (HELD, AIR, ROLL, TEETER, SLIDE,
 * ROCK, REST) and the sorted {@link MotionEvent}s of all dice. Immutable after {@link DiceSolver#solve}; build once per
 * roll and cache by the round id ({@code seq}).
 *
 * <p>The roll clock: {@code ms = 0} is the release of die 0; negative times are HELD. {@link #sample} is a pure,
 * allocation-free function of {@code ms} (binary search + closed forms), so it is frame-rate independent.
 *
 * <p>Orientation: the solver plans a CANONICAL motion {@code Q(t)} that never looks at the faces; the sample is the
 * MODEL orientation {@code M(t) = Q(t)·L} with the per-die relabelling element {@code L} (DI §2.3). Positions,
 * heights, phases, speeds and events are therefore bit-identical for all 36 outcomes of one seed.
 *
 * <p>Not thread-safe only in the sense that a {@link DieSample} must not be shared between threads; the roll itself is
 * read-only and may be sampled from several threads.
 */
public final class DiceRoll {
	final Seg[][] segs;
	final double[][] label;
	final int[] faces;
	final double[] restT;
	final double[] releaseT;
	final double[] wallT;
	final double[][] rest;
	final MotionEvent[] events;
	private final java.util.List<MotionEvent> eventList;
	final double g;
	final int attempt;
	final boolean fallback;
	final boolean[] teeter;

	DiceRoll(Seg[][] segs, double[][] label, int[] faces, double[] restT, double[] releaseT, double[] wallT, double[][] rest, MotionEvent[] events,
			double g, int attempt, boolean fallback, boolean[] teeter) {
		this.segs = segs;
		this.label = label;
		this.faces = faces;
		this.restT = restT;
		this.releaseT = releaseT;
		this.wallT = wallT;
		this.rest = rest;
		this.events = events;
		this.eventList = java.util.Collections.unmodifiableList(java.util.Arrays.asList(events));
		this.g = g;
		this.attempt = attempt;
		this.fallback = fallback;
		this.teeter = teeter;
	}

	/** Number of dice. */
	public int dice() {
		return segs.length;
	}

	/** Rest time of the last die, ms (rounded; exactly the target when one was given). */
	public int restMs() {
		double m = 0;
		for (double t : restT) m = Math.max(m, t);
		return (int) Math.round(m);
	}

	/** Rest time of die {@code die}, ms (rounded). */
	public int restMs(int die) {
		return (int) Math.round(restT[die]);
	}

	/** Exact rest time of die {@code die}, ms. */
	public double restTime(int die) {
		return restT[die];
	}

	/** Release time of die {@code die}, ms (die 0: 0). */
	public double releaseMs(int die) {
		return releaseT[die];
	}

	/** Time of die {@code die}'s first WALL or BUMPER contact, ms (rounded); −1 when it never banks. */
	public int wallMs(int die) {
		return wallT[die] < 0 ? -1 : (int) Math.round(wallT[die]);
	}

	/** Rest point {@code {x, y}} of die {@code die} in tray units (a copy). */
	public double[] restPoint(int die) {
		return rest[die].clone();
	}

	/** The server faces this roll shows at rest (a copy). */
	public int[] faces() {
		return faces.clone();
	}

	/** All events of all dice sorted by time; the first is a RELEASE. A copy: call once (per cursor), not per frame. */
	public MotionEvent[] events() {
		return events.clone();
	}

	/** The same events as an unmodifiable view (no copy; fine per frame). */
	public java.util.List<MotionEvent> eventList() {
		return eventList;
	}

	/** Gravity of the tray this roll was solved on, u/s². */
	public double g() {
		return g;
	}

	/** Seeded attempt that was accepted (0-based). */
	public int attempt() {
		return attempt;
	}

	/** True when no attempt met every preference and the relaxed fallback plan was used (still exact). */
	public boolean fallback() {
		return fallback;
	}

	/** True when die {@code die}'s last roll teeters on an edge. */
	public boolean teeters(int die) {
		return teeter[die];
	}

	/** The relabelling element {@code L} of die {@code die} (a copy). */
	public double[] label(int die) {
		return label[die].clone();
	}

	/** Model top face (1–6) of die {@code die} at {@code ms}: the face nearest up, as drawn. */
	public int topFace(int die, double ms) {
		DieSample s = new DieSample();
		sample(die, ms, s);
		return CubeGroup.faceUp(s.q);
	}

	/** Allocation-free variant of {@link #topFace(int, double)} using a caller sample. */
	public int topFace(int die, double ms, DieSample scratch) {
		sample(die, ms, scratch);
		return CubeGroup.faceUp(scratch.q);
	}

	/** Samples die {@code die} at roll time {@code ms} into {@code out} (MODEL orientation). */
	public void sample(int die, double ms, DieSample out) {
		sampleCanonical(die, ms, out);
		Quat.mul(out.q, label[die], out.q);
	}

	/** Samples the CANONICAL motion (before relabelling; for tests and the solver). */
	void sampleCanonical(int die, double ms, DieSample out) {
		Seg[] s = segs[die];
		int lo = 0;
		int hi = s.length - 1;
		// segment 0 starts at −∞ (HELD); find the last segment with t0 ≤ ms
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (s[mid].t0 <= ms) lo = mid;
			else hi = mid - 1;
		}
		s[lo].eval(ms, out, g);
	}

	/** Number of segments of die {@code die} (tests). */
	int segments(int die) {
		return segs[die].length;
	}

	Seg seg(int die, int i) {
		return segs[die][i];
	}
}

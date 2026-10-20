package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;

/**
 * S1 (owner playtest, SY-SPEC §3 "Slots"): the spinning player's reels keep the outcome-free pre-roll as their WAIT
 * LOOP; when the result arrives every reel decelerates FROM ITS CURRENT POSITION AND SPEED onto the server's landing,
 * never jumping back and never snapping (PURE).
 *
 * <p>Time is the actor's timeline time {@code τ} (ms of the spin timeline; the actor anchors it at the arrival:
 * {@code τ = tH + (wall − arrival)}, SY-SPEC §2). For reel {@code r} of the first reel phase, on
 * {@code [tH, landAt_r)} the top index follows a cubic Hermite in the travelled distance {@code x} from the pre-roll
 * pose {@code (top0, v0)} to the landing pose of the timeline ({@code top = stop + landDistance}, speed
 * {@link ReelMotion#landStartSpeed()}); the distance is the strip distance plus whole strip turns, chosen so the speed
 * stays inside {@code [0.4, 3.5] × V} (no reverse, bounded per-frame movement). From {@code landAt_r} the existing
 * {@link ReelMotion} landing (with its bounce) runs unchanged, so the final stops are exactly the server's.
 *
 * <p>{@code tH = min(preRollElapsed, landAt_0 + spinUpAt − minMs(L))}: a short pre-roll keeps the timeline pace; a
 * result that arrives late (300 / 800 / 1 500 ms …) just extends the wait loop, it is never "late" for the actor.
 */
public final class ReelHandoff {
	/** Lower bound of the handoff speed (× the landing speed): never slower than this, never backwards. */
	public static final double MIN_RATIO = 0.6;

	private final double tH;
	private final double top0;
	private final double v0;
	private final double v1;
	private final double dist;
	private final double dur;

	private ReelHandoff(double tH, double top0, double v0, double v1, double dist, double dur) {
		this.tH = tH;
		this.top0 = top0;
		this.v0 = v0;
		this.v1 = v1;
		this.dist = dist;
		this.dur = dur;
	}

	/**
	 * Shortest handoff for a strip of {@code length} cells: the window must be able to absorb a whole strip turn at
	 * up to ~3 × V ({@code L / (2V) + 100} ms: Overworld 900, Nether 740, End 1 000).
	 */
	public static int minMs(int length) {
		return (int) Math.ceil(length / (2 * ReelMotion.V)) + 100;
	}

	/** The plan of one arrival: the actor's timeline time at arrival and one handoff per reel (null = no motion). */
	public record Plan(double tH, ReelHandoff[] reels) {
		/** The reel's handoff covers {@code tMs} (else the normal phase motion applies). */
		public ReelHandoff active(int r, double tMs, int spinUpAt, ReelMotion m) {
			ReelHandoff h = reels[r];
			return h != null && m != null && tMs >= tH && tMs - spinUpAt < m.landAt() ? h : null;
		}
	}

	/**
	 * @param script         the spin's script (phase 0 = the first reel phase)
	 * @param top0           per reel: the pre-roll's top index at arrival (unwrapped, decreasing over time)
	 * @param v0             per reel: the pre-roll's speed at arrival (cells/ms, ≥ 0)
	 * @param preRollElapsed ms the pre-roll has run at arrival
	 * @return the plan, or {@code null} when there is no reel phase
	 */
	public static Plan plan(SlotScript script, double[] top0, double[] v0, double preRollElapsed) {
		if (script.phases().isEmpty()) return null;
		SlotScript.Phase p = script.phase(0);
		MachineDef def = script.def();
		double firstLand = Double.POSITIVE_INFINITY;
		int longest = 1;
		for (int r = 0; r < MachineDef.REELS; r++) {
			if (p.reels[r] != null) firstLand = Math.min(firstLand, p.spinUpAt + p.reels[r].landAt());
			longest = Math.max(longest, def.stripLength(r));
		}
		if (Double.isInfinite(firstLand)) return null;
		double tH = Math.min(Math.max(0, preRollElapsed), firstLand - minMs(longest));
		ReelHandoff[] out = new ReelHandoff[MachineDef.REELS];
		for (int r = 0; r < MachineDef.REELS; r++) {
			ReelMotion m = p.reels[r];
			if (m == null) continue;
			double localH = tH - p.spinUpAt;
			double dur = m.landAt() - localH;
			double v1 = m.landStartSpeed();
			double vs = Math.max(0, v0[r]);
			double target = m.top(m.landAt()); // stop + landDistance
			int len = def.stripLength(r);
			double gap = top0[r] - target;
			gap -= len * Math.floor(gap / len);
			// smallest whole-turn count keeping the mean speed ≥ MIN_RATIO of the reference speed
			double ref = (vs + v1) / 2;
			double d = gap;
			// Fritsch–Carlson: v0·T/D ≤ 3 and v1·T/D ≤ 3 keep the Hermite monotone (never backwards)
			while (d < MIN_RATIO * ref * dur || d * 3 < Math.max(vs, v1) * dur * 1.0001) d += len;
			out[r] = new ReelHandoff(localH, top0[r], vs, v1, d, dur);
		}
		return new Plan(tH, out);
	}

	/** Top index at phase-local time {@code local} ∈ [start, landAt]. */
	public double top(double local) {
		return top0 - travelled(local);
	}

	/** Cells travelled since the arrival (monotonic). */
	public double travelled(double local) {
		double u = Math.max(0, Math.min(1, (local - tH) / dur));
		double u2 = u * u;
		double u3 = u2 * u;
		return (u3 - 2 * u2 + u) * v0 * dur + (3 * u2 - 2 * u3) * dist + (u3 - u2) * v1 * dur;
	}

	/** Speed (cells/ms) at {@code local}. */
	public double speed(double local) {
		double u = Math.max(0, Math.min(1, (local - tH) / dur));
		return (3 * u * u - 4 * u + 1) * v0 + (6 * u - 6 * u * u) * dist / dur + (3 * u * u - 2 * u) * v1;
	}

	/** Distance planned (cells, incl. whole strip turns). */
	public double distance() {
		return dist;
	}
}

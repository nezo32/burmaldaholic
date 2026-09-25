package dev.nezo.burmaldaholic.games.extras.pvp.plinko;

import dev.nezo.burmaldaholic.core.pvp.logic.PvpEvent;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.games.extras.logic.Plinko;
import dev.nezo.burmaldaholic.games.extras.pvp.ModeRanking;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure rules of Plinko Battle (PVP.md §7). A ball is exactly a solo Plinko drop ({@link Plinko}: 12 fair
 * left/right choices, bin = number of rights); its path is a 12-bit mask (bit i = row i went right, the
 * solo wire encoding {@link Plinko#encode}). Points per ball = {@code round(multiplier × 10)} of the
 * risk's live multiplier row; the Underdog Boost doubles the final ball of whoever is strictly last before
 * it; highest total wins, tie-break = best single (scored) ball, then split.
 */
public final class PlinkoBattle {
	public static final int ROWS = Plinko.ROWS;
	public static final int BINS = Plinko.BINS;
	public static final int MASK = (1 << ROWS) - 1;

	private PlinkoBattle() {}

	/** §7.1 points row: {@code round(multiplier × 10)} per bin (half up; negative / NaN → 0). */
	public static long[] pointsRow(double[] multipliers) {
		long[] out = new long[multipliers.length];
		for (int i = 0; i < multipliers.length; i++) {
			double v = multipliers[i] * 10;
			out[i] = Double.isFinite(v) && v > 0 ? Math.round(v) : 0;
		}
		return out;
	}

	/** Bin of a path mask (number of rights among the 12 rows). */
	public static int bin(int mask) {
		return Integer.bitCount(mask & MASK);
	}

	/** Edge bin (0 or 12): the EDGE! call-out. */
	public static boolean edge(int bin) {
		return bin == 0 || bin == ROWS;
	}

	/** One ball, drawn exactly like the solo drop: one fair {@code nextInt(2)} per row, row 0 first. */
	public static int drawPath(PvpRng rng) {
		boolean[] path = new boolean[ROWS];
		for (int i = 0; i < ROWS; i++) {
			path[i] = rng.nextBoolean(); // right = "heads" of the fair stream, like Bedrock's dropBall (next() < ½)
		}
		return Plinko.encode(path);
	}

	/** Exact mean points per ball over the 4096 equally likely paths (§16.5 P2). */
	public static double meanPoints(long[] row) {
		double s = 0;
		for (int b = 0; b < BINS; b++) {
			s += (double) row[b] * Plinko.BIN_WEIGHTS[b];
		}
		return s / Plinko.PATHS;
	}

	/**
	 * Participants who are strictly last (§7.1 Underdog Boost, same rule as Slot Showdown S9): everyone
	 * at the minimum total, unless everybody is level.
	 */
	public static boolean[] underdogs(long[] totals) {
		boolean[] out = new boolean[totals.length];
		if (totals.length == 0) {
			return out;
		}
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (long t : totals) {
			min = Math.min(min, t);
			max = Math.max(max, t);
		}
		if (min == max) {
			return out;
		}
		for (int i = 0; i < totals.length; i++) {
			out[i] = totals[i] == min;
		}
		return out;
	}

	/**
	 * Full scoring of a battle.
	 *
	 * @param ballPoints scored points of every ball [player][ball] (final ball already ×2 for underdogs)
	 * @param totals     final totals
	 * @param best       best single scored ball (the tie-break)
	 * @param underdog   who got the Underdog Boost on the final ball
	 * @param before     totals before the final ball (the standings the boost was decided on)
	 */
	public record Scored(long[][] ballPoints, long[] totals, long[] best, boolean[] underdog, long[] before, int[] rankOrder, int[] winners,
			List<PvpEvent> events) {}

	/**
	 * @param paths         [player][ball] 12-bit masks
	 * @param seatOrder     tape seat order
	 * @param row           points row (13 bins)
	 * @param underdogBoost {@code pvp.plinko.underdogBoost} (as recorded in the tape)
	 */
	public static Scored score(int[][] paths, int[] seatOrder, long[] row, boolean underdogBoost) {
		int n = paths.length;
		int balls = n == 0 ? 0 : paths[0].length;
		long[][] pts = new long[n][balls];
		long[] before = new long[n];
		for (int i = 0; i < n; i++) {
			for (int b = 0; b < balls - 1; b++) {
				pts[i][b] = row[bin(paths[i][b])];
				before[i] += pts[i][b];
			}
		}
		boolean[] boosted = underdogBoost && balls >= 2 ? underdogs(before) : new boolean[n];
		long[] totals = new long[n];
		long[] best = new long[n];
		List<PvpEvent> events = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (balls > 0) {
				long last = row[bin(paths[i][balls - 1])];
				pts[i][balls - 1] = boosted[i] ? Math.multiplyExact(last, 2) : last;
			}
			for (int b = 0; b < balls; b++) {
				totals[i] = Math.addExact(totals[i], pts[i][b]);
				best[i] = Math.max(best[i], pts[i][b]);
			}
		}
		for (int b = 0; b < balls; b++) {
			if (b == balls - 1) {
				for (int i = 0; i < n; i++) {
					if (boosted[i]) {
						events.add(PvpEvent.of("underdog", i, b));
					}
				}
			}
			for (int i = 0; i < n; i++) {
				int bin = bin(paths[i][b]);
				if (edge(bin)) {
					events.add(new PvpEvent("edge", i, b, Map.of("bin", (long) bin, "points", pts[i][b])));
				}
			}
		}
		for (int i = 0; i < n && balls > 0; i++) {
			int mask = paths[i][balls - 1];
			events.add(new PvpEvent("final_ball", i, balls - 1, Map.of("mask", (long) mask, "bin", (long) bin(mask), "points", pts[i][balls - 1])));
		}
		ModeRanking.Ranked r = ModeRanking.rank(totals, best, seatOrder);
		return new Scored(pts, totals, best, boosted, before, r.rankOrder(), r.winners(), events);
	}
}

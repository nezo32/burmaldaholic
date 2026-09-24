package dev.nezo.burmaldaholic.games.extras.pvp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Pure ranking helper shared by the extras PvP modes with "highest points, then a tie-break value, then
 * split" rules (Plinko Battle §7.1, Scratch Showdown §8.1). Symmetric in the participants: only the
 * tape's seat order separates exact ties in {@code rankOrder} (display order); the pot is then split
 * among every participant equal on both keys.
 */
public final class ModeRanking {
	private ModeRanking() {}

	/**
	 * @param rankOrder participant indices best → worst (exact ties adjacent, in seat order)
	 * @param winners   every participant equal to the best on both keys (≥ 1), ascending participant index
	 */
	public record Ranked(int[] rankOrder, int[] winners) {}

	/**
	 * @param primary   higher is better (points)
	 * @param secondary higher is better (tie-break value)
	 * @param seatOrder tape seat order (permutation of participant indices)
	 */
	public static Ranked rank(long[] primary, long[] secondary, int[] seatOrder) {
		int n = primary.length;
		int[] seatPos = new int[n];
		for (int k = 0; k < seatOrder.length; k++) {
			seatPos[seatOrder[k]] = k;
		}
		Integer[] idx = new Integer[n];
		for (int i = 0; i < n; i++) {
			idx[i] = i;
		}
		Arrays.sort(idx, Comparator.<Integer>comparingLong(i -> -primary[i]).thenComparingLong(i -> -secondary[i]).thenComparingInt(i -> seatPos[i]));
		int[] order = new int[n];
		for (int i = 0; i < n; i++) {
			order[i] = idx[i];
		}
		List<Integer> winners = new ArrayList<>();
		if (n > 0) {
			int top = order[0];
			for (int i : order) {
				if (primary[i] == primary[top] && secondary[i] == secondary[top]) {
					winners.add(i);
				}
			}
		}
		// winners by participant index (same as Bedrock); the odd-chip order of a split comes from the seat order
		return new Ranked(order, winners.stream().mapToInt(Integer::intValue).sorted().toArray());
	}
}

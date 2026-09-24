package dev.nezo.burmaldaholic.core.pvp.logic;

import java.util.List;

/**
 * Result of {@link PvpMode#score} (PVP.md §3.14).
 *
 * @param points    final points per participant (Wheel Party: 1 for the winner, 0 otherwise)
 * @param rankOrder participant indices best → worst (ties adjacent, broken by the mode's tie-breaks)
 * @param winners   participant indices sharing the pot (≥ 1); the engine splits W with {@link PvpMath#split}
 * @param seatOrder the tape's seat order (permutation of participant indices; odd chips, tie-breaks)
 * @param events    notable events for reveal, advancements and chat (kaboom, swap, creeper, …)
 */
public record Outcome(long[] points, int[] rankOrder, int[] winners, int[] seatOrder, List<PvpEvent> events) {
	public Outcome {
		events = List.copyOf(events);
	}
}

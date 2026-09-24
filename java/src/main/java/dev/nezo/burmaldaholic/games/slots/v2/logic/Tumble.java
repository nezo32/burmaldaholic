package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.List;

/** Nether tumbling reels (SLOTS.md §3.2, exact algorithm). The chain is a pure function of the stops. */
public final class Tumble {
	private Tumble() {}

	/**
	 * One evaluation of the chain.
	 *
	 * @param step       0 = first evaluation, 1 = first tumble, …
	 * @param window     window evaluated at this step
	 * @param result     its evaluation
	 * @param multiplier ladder value applied at this step
	 * @param payFifths  result.payFifths × multiplier
	 * @param tops       strip index of each reel's top cell at this step (refill cursor)
	 */
	public record Step(int step, Window window, Ways.Result result, int multiplier, long payFifths, int[] tops) {}

	/** Whole chain; {@code finalWindow} is where triggers are checked. */
	public record Chain(List<Step> steps, Window finalWindow, long payFifths) {}

	/** SKELETON — lane S-J1 / S-B1. */
	public static Chain run(MachineDef def, int[] stops, int[] ladder) {
		throw new UnsupportedOperationException("slots v2 tumble chain: lane S-J1");
	}
}

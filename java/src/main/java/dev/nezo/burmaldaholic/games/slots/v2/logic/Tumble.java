package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.ArrayList;
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

	/**
	 * Whole chain; {@code finalWindow} is where triggers are checked. {@code steps} holds every evaluation: the
	 * winning ones (each followed by a tumble) and the last, non-winning one — so the number of tumbles is
	 * {@link #tumbles()}. A machine without tumbles has exactly one step.
	 */
	public record Chain(List<Step> steps, Window finalWindow, long payFifths, int tumbles) {}

	/**
	 * Safety bound on the evaluations of one chain (twin of Bedrock {@code MAX_TUMBLE_STEPS}). The default strips end after
	 * at most 9 evaluations (8 tumbles, SLOTS.md §7.5, proven by the full enumeration); a configured strip set can tumble
	 * forever (e.g. a reel of one symbol), which would hang the server tick. The chain stops after this many evaluations;
	 * the last one still pays. Never reached by the defaults, so §7.5 and the shared vectors are unchanged.
	 */
	public static final int MAX_STEPS = 100;

	/** Ladder value of a step: {@code ladder[min(step, len − 1)]} (×5 / ×10 for every later step); 1 without a ladder. */
	public static int multiplier(int[] ladder, int step) {
		return ladder.length == 0 ? 1 : ladder[Math.min(step, ladder.length - 1)];
	}

	/**
	 * Runs the chain from the stops. With an empty {@code ladder} the machine does not tumble: one evaluation.
	 */
	public static Chain run(MachineDef def, int[] stops, int[] ladder) {
		int[] cells = Window.fromStops(def, stops).cells();
		int[] top = stops.clone();
		for (int r = 0; r < 5; r++) top[r] = Math.floorMod(top[r], def.stripLength(r));
		List<Step> steps = new ArrayList<>(2);
		long total = 0;
		for (int step = 0; ; step++) {
			Ways.Result res = Ways.evaluate(def, cells, 0);
			int mult = multiplier(ladder, step);
			long pay = res.payFifths() * mult;
			steps.add(new Step(step, new Window(cells), res, mult, pay, top.clone()));
			if (res.payFifths() == 0 || ladder.length == 0) break;
			total += pay;
			if (step + 1 >= MAX_STEPS) break;
			refill(def, cells, top, res.winMask());
		}
		if (ladder.length == 0) total = steps.getFirst().payFifths();
		return new Chain(List.copyOf(steps), steps.getLast().window(), total, ladder.length == 0 ? 0 : steps.size() - 1);
	}

	/**
	 * Removes the cells of {@code removeMask}, lets the rest fall and refills each reel from the strip ABOVE the
	 * window ({@code S_r[top_r − m] … S_r[top_r − 1]}, then {@code top_r −= m}). Mutates {@code cells} and
	 * {@code top}. Scatters and coins never take part in a win, so they never explode.
	 */
	public static void refill(MachineDef def, int[] cells, int[] top, int removeMask) {
		for (int r = 0; r < 5; r++) {
			int[] keep = new int[3];
			int nk = 0;
			int nr = 0;
			for (int y = 0; y < 3; y++) {
				if ((removeMask >> (r * 3 + y) & 1) != 0) nr++;
				else keep[nk++] = cells[r * 3 + y];
			}
			if (nr == 0) continue;
			int len = def.stripLength(r);
			int ci = 0;
			int[] col = new int[3];
			for (int j = nr; j >= 1; j--) col[ci++] = def.strips()[r][Math.floorMod(top[r] - j, len)];
			for (int j = 0; j < nk; j++) col[ci++] = keep[j];
			top[r] = Math.floorMod(top[r] - nr, len);
			for (int y = 0; y < 3; y++) cells[r * 3 + y] = col[y];
		}
	}
}

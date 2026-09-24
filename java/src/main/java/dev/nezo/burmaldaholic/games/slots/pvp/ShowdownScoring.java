package dev.nezo.burmaldaholic.games.slots.pvp;

import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpEvent;
import dev.nezo.burmaldaholic.games.slots.logic.Paylines;
import dev.nezo.burmaldaholic.games.slots.logic.SlotEngine;
import dev.nezo.burmaldaholic.games.slots.logic.SlotEngine.LineKind;
import dev.nezo.burmaldaholic.games.slots.logic.SlotEngine.LineResult;
import dev.nezo.burmaldaholic.games.slots.logic.SlotTable;
import dev.nezo.burmaldaholic.games.slots.logic.Symbol;
import dev.nezo.burmaldaholic.games.slots.pvp.SlotShowdownMode.Rules;
import dev.nezo.burmaldaholic.games.slots.pvp.SlotShowdownMode.Tape;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Slot Showdown scoring (PVP.md §5.2). PURE and deterministic: the same tape always gives the same
 * {@link Outcome}. Lines are evaluated with the solo evaluator {@link SlotEngine#evaluateLine}; the line's
 * solo multiplier becomes its points, with the PvP values for specials.
 */
public final class ShowdownScoring {
	/** The six regular paying symbols the HOT symbol is drawn from (uniformly). */
	public static final List<Symbol> HOT_CANDIDATES = List.of(Symbol.BERRIES, Symbol.APPLE, Symbol.GOLDEN_CARROT, Symbol.EMERALD,
		Symbol.DIAMOND, Symbol.SEVEN);
	public static final long PEARL_POINTS = 10;
	public static final long CLOCK_POINTS = 50;

	// event kinds (Outcome.events / timeline)
	public static final String KABOOM = "kaboom";
	public static final String SWAP = "swap";
	public static final String TIME_WARP = "time_warp";
	public static final String STAR = "star";
	public static final String UNDERDOG = "underdog";

	private ShowdownScoring() {}

	/** One evaluated payline: base points (before HOT) and whether HOT doubles it. */
	public record LineScore(int line, LineKind kind, Symbol symbol, long points, boolean hot) {
		public long scored() {
			return hot ? points * 2 : points;
		}
	}

	/** One player's spin. */
	public record SpinScore(List<LineScore> lines, long basePoints, boolean kaboom, boolean pearl, boolean clock, boolean star) {
		/** Lines worth more than 0 points (the first tie-break). */
		public int payingLines() {
			int n = 0;
			for (LineScore l : lines) {
				if (l.points() > 0) {
					n++;
				}
			}
			return n;
		}
	}

	/**
	 * One round (everyone's r-th spin).
	 *
	 * @param spinPoints   points of this spin incl. HOT, TIME WARP and Underdog multipliers
	 * @param totalsBefore totals before the round
	 * @param totals       totals after the round (after KABOOM, add, SWAPs)
	 * @param multiplier   the TIME WARP × Underdog multiplier applied to each player's spin (1, 2 or 4)
	 * @param underdogs    players boosted this round (final round only)
	 */
	public record Round(int round, Symbol hot, SpinScore[] spins, long[] spinPoints, long[] totalsBefore, long[] totals, int[] multiplier,
			int[] underdogs, List<PvpEvent> events) {}

	/** Full play-out of a tape. */
	public record Match(List<Round> rounds, long[] totals, int[] payingLines, long[] bestSpin, Outcome outcome) {}

	/** Scores one line of three cells (left→right); null when the line has no result at all. */
	public static @Nullable LineScore line(int lineNo, Symbol a, Symbol b, Symbol c, SlotTable table, Rules rules, @Nullable Symbol hot) {
		LineResult r = SlotEngine.evaluateLine(a, b, c, table);
		if (r == null) {
			return null;
		}
		long points;
		if (r.kind() == LineKind.SPECIAL) {
			points = switch (r.symbol()) {
				case PEARL -> PEARL_POINTS;
				case CLOCK -> CLOCK_POINTS;
				case STAR -> rules.starPoints();
				default -> 0; // Creeper, TNT
			};
		} else {
			points = SlotEngine.linePayout(r.multiplier(), 1);
		}
		boolean isHot = rules.hotSymbol() && hot != null && r.kind() != LineKind.SPECIAL && r.kind() != LineKind.WILD && r.symbol() == hot;
		return new LineScore(lineNo, r.kind(), r.symbol(), points, isHot);
	}

	/** Scores a {@code cells[r * 3 + c]} grid (symbol ordinals) on the table's paylines. */
	public static SpinScore spin(int[] cells, SlotTable table, Rules rules, @Nullable Symbol hot) {
		List<LineScore> lines = new ArrayList<>();
		long base = 0;
		boolean kaboom = false, pearl = false, clock = false, star = false;
		for (int line = 1; line <= table.lines(); line++) {
			Symbol a = cell(cells, Paylines.row(line, 0), 0);
			Symbol b = cell(cells, Paylines.row(line, 1), 1);
			Symbol c = cell(cells, Paylines.row(line, 2), 2);
			LineScore s = line(line, a, b, c, table, rules, hot);
			if (s == null) {
				continue;
			}
			lines.add(s);
			base += s.scored();
			if (s.kind() == LineKind.SPECIAL) {
				switch (s.symbol()) {
					case CREEPER, TNT -> kaboom |= rules.kaboom();
					case PEARL -> pearl |= rules.pearlSwap();
					case CLOCK -> clock = true;
					case STAR -> star = true;
					default -> {
					}
				}
			}
		}
		return new SpinScore(List.copyOf(lines), base, kaboom, pearl, clock, star);
	}

	private static Symbol cell(int[] cells, int row, int col) {
		return Symbol.byOrdinal(cells[row * 3 + col]);
	}

	/** Mean points of one line without HOT/boosts (exact, by enumerating the three cells). */
	public static double meanLinePoints(SlotTable table, Rules rules) {
		double total = table.totalWeight();
		double e = 0;
		for (Symbol a : table.present()) {
			for (Symbol b : table.present()) {
				for (Symbol c : table.present()) {
					LineScore s = line(1, a, b, c, table, rules, null);
					if (s != null && s.points() > 0) {
						e += table.weight(a) * (double) table.weight(b) * table.weight(c) / (total * total * total) * s.points();
					}
				}
			}
		}
		return e;
	}

	/** Plays the whole tape (PVP.md §5.2 round resolution order). */
	public static Match play(Tape tape, SlotTable table, Rules rules) {
		int n = tape.seatOrder().length;
		int rounds = tape.hot().length;
		long[] totals = new long[n];
		int[] paying = new int[n];
		long[] best = new long[n];
		boolean[] warp = new boolean[n];
		int[] seatPos = new int[n];
		for (int k = 0; k < n; k++) {
			seatPos[tape.seatOrder()[k]] = k;
		}
		List<Round> out = new ArrayList<>();
		List<PvpEvent> all = new ArrayList<>();
		for (int r = 0; r < rounds; r++) {
			boolean last = r == rounds - 1;
			Symbol hot = Symbol.byOrdinal(tape.hot()[r]);
			List<PvpEvent> events = new ArrayList<>();
			long[] before = totals.clone();
			int[] underdogs = last && rules.underdogBoost() ? underdogs(totals) : new int[0];
			boolean[] boosted = new boolean[n];
			for (int u : underdogs) {
				boosted[u] = true;
				events.add(PvpEvent.of(UNDERDOG, u, r));
			}
			SpinScore[] spins = new SpinScore[n];
			long[] points = new long[n];
			int[] mult = new int[n];
			// 1. spin points
			for (int i = 0; i < n; i++) {
				spins[i] = spin(tape.grids()[i][r], table, rules, hot);
				mult[i] = (warp[i] ? 2 : 1) * (boosted[i] ? 2 : 1);
				warp[i] = false;
				points[i] = spins[i].basePoints() * mult[i];
				paying[i] += spins[i].payingLines();
				best[i] = Math.max(best[i], points[i]);
				if (spins[i].star()) {
					events.add(new PvpEvent(STAR, i, r, Map.of("points", (long) rules.starPoints())));
				}
			}
			// 2. KABOOM halves the total before this spin
			for (int i = 0; i < n; i++) {
				if (spins[i].kaboom()) {
					long b = totals[i];
					totals[i] = b / 2;
					events.add(new PvpEvent(KABOOM, i, r, Map.of("before", b, "after", totals[i])));
				}
			}
			// 3. add
			for (int i = 0; i < n; i++) {
				totals[i] += points[i];
			}
			// 4. SWAPs in seat order against the current leader
			for (int k = 0; k < n; k++) {
				int i = tape.seatOrder()[k];
				if (!spins[i].pearl()) {
					continue;
				}
				int leader = -1;
				for (int kk = 0; kk < n; kk++) {
					int j = tape.seatOrder()[kk];
					if (j != i && (leader < 0 || totals[j] > totals[leader])) {
						leader = j;
					}
				}
				if (leader >= 0 && totals[leader] > totals[i]) {
					long mine = totals[i];
					long theirs = totals[leader];
					totals[i] = theirs;
					totals[leader] = mine;
					events.add(new PvpEvent(SWAP, i, r, Map.of("other", (long) leader, "from", mine, "to", theirs)));
				}
			}
			// 5. TIME WARP for the next spin (no effect on the last one)
			for (int i = 0; i < n; i++) {
				if (spins[i].clock() && !last) {
					warp[i] = true;
					events.add(PvpEvent.of(TIME_WARP, i, r));
				}
			}
			all.addAll(events);
			out.add(new Round(r, hot, spins, points, before, totals.clone(), mult, underdogs, List.copyOf(events)));
		}
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) {
			order[i] = i;
		}
		Comparator<Integer> better = Comparator.<Integer>comparingLong(i -> -totals[i])
			.thenComparingInt(i -> -paying[i])
			.thenComparingLong(i -> -best[i]);
		Arrays.sort(order, better.thenComparingInt(i -> seatPos[i]));
		int[] rank = new int[n];
		List<Integer> winners = new ArrayList<>();
		for (int k = 0; k < n; k++) {
			rank[k] = order[k];
			if (better.compare(order[k], order[0]) == 0) {
				winners.add(order[k]);
			}
		}
		Outcome outcome = new Outcome(totals.clone(), rank, winners.stream().mapToInt(Integer::intValue).toArray(), tape.seatOrder().clone(), all);
		return new Match(List.copyOf(out), totals.clone(), paying, best, outcome);
	}

	/** {@code pvp_phoenix} (PVP.md §11): {@code seat} won (or split) after a KABOOM hit them in this match. */
	public static boolean phoenix(Outcome outcome, int seat) {
		boolean won = false;
		for (int w : outcome.winners()) {
			won |= w == seat;
		}
		if (!won) {
			return false;
		}
		for (PvpEvent e : outcome.events()) {
			if (e.kind().equals(KABOOM) && e.seat() == seat && e.data().getOrDefault("before", 0L) > 0) {
				return true;
			}
		}
		return false;
	}

	/** Players whose total is strictly the lowest (nobody when everyone is tied). */
	public static int[] underdogs(long[] totals) {
		long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		for (long t : totals) {
			min = Math.min(min, t);
			max = Math.max(max, t);
		}
		if (totals.length < 2 || min == max) {
			return new int[0];
		}
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < totals.length; i++) {
			if (totals[i] == min) {
				out.add(i);
			}
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}
}

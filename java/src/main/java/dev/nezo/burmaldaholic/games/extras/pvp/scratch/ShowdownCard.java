package dev.nezo.burmaldaholic.games.extras.pvp.scratch;

import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Scratch Showdown card (PVP.md §8.1). Pure. 3 × 3 cells, each i.i.d. from the weighted symbols
 * (Coal … Nether Star, Creeper, Rabbit's Foot), revealed in the fixed order 1…9 (row-major):
 * <ul>
 *   <li>a Creeper burns the highest-value surviving cell revealed so far (ties: the earliest revealed
 *       one); the burned cell is worth 0 and no longer counts for trios; with no surviving cell it fizzles;</li>
 *   <li>Rabbit's Feet multiply the card by 2 per foot, at most ×4 (a third foot does nothing).</li>
 * </ul>
 * Score = (Σ surviving values, every symbol with ≥ 3 surviving copies counting double) × 2^min(feet, 2);
 * tie-break = highest single surviving cell value.
 */
public final class ShowdownCard {
	public static final int CELLS = 9;
	public static final int MAX_FEET_COUNTED = 2;

	private ShowdownCard() {}

	/** Cell symbols; the first six have values. Ordinal = the tape's cell code. */
	public enum Sym {
		COAL, IRON, GOLD, EMERALD, DIAMOND, STAR, CREEPER, FOOT;

		public static final int VALUED = 6;
		private static final Sym[] ALL = values();

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public boolean valued() {
			return ordinal() < VALUED;
		}

		/** {@code gui.burmaldaholic.pvp.scratch.symbol.<id>}. */
		public String key() {
			return "gui.burmaldaholic.pvp.scratch.symbol." + id();
		}

		public static Sym of(int code) {
			return ALL[code];
		}
	}

	public static final int[] DEFAULT_WEIGHTS = {30, 25, 18, 12, 6, 1, 5, 3};
	public static final int[] DEFAULT_VALUES = {1, 2, 3, 5, 10, 25};

	/** {@code pvp.scratch.weights} → 8 weights in {@link Sym} order; missing = 0; Σ &lt; 1 → the defaults. */
	public static int[] weights(Map<String, Integer> configured) {
		if (configured == null) {
			return DEFAULT_WEIGHTS.clone();
		}
		int[] w = new int[Sym.values().length];
		long sum = 0;
		for (Sym s : Sym.values()) {
			Integer v = configured.get(s.id());
			w[s.ordinal()] = v == null ? 0 : Math.max(0, Math.min(1000, v));
			sum += w[s.ordinal()];
		}
		return sum < 1 ? DEFAULT_WEIGHTS.clone() : w;
	}

	/** {@code pvp.scratch.values} → 6 values (Coal … Star); missing → default. */
	public static int[] values(Map<String, Integer> configured) {
		int[] v = DEFAULT_VALUES.clone();
		if (configured != null) {
			for (int i = 0; i < Sym.VALUED; i++) {
				Integer x = configured.get(Sym.of(i).id());
				if (x != null) {
					v[i] = Math.max(0, Math.min(1000, x));
				}
			}
		}
		return v;
	}

	/** One cell: {@code u = nextInt(Σw)} walked over the cumulative weights in {@link Sym} order. */
	public static int drawCell(PvpRng rng, int[] weights) {
		int total = 0;
		for (int w : weights) {
			total += w;
		}
		int u = rng.nextInt(total);
		for (int s = 0; s < weights.length; s++) {
			if (u < weights[s]) {
				return s;
			}
			u -= weights[s];
		}
		throw new IllegalStateException("weights");
	}

	public static int[] drawCard(PvpRng rng, int[] weights) {
		int[] cells = new int[CELLS];
		for (int c = 0; c < CELLS; c++) {
			cells[c] = drawCell(rng, weights);
		}
		return cells;
	}

	/** {@code 2^min(feet, 2)}. */
	public static int multiplier(int feet) {
		return 1 << Math.min(feet, MAX_FEET_COUNTED);
	}

	/** Score from surviving counts per valued symbol and the number of feet. */
	public static long score(int[] survivingCounts, int feet, int[] values) {
		long s = 0;
		for (int k = 0; k < Sym.VALUED; k++) {
			long part = (long) survivingCounts[k] * values[k];
			s += survivingCounts[k] >= 3 ? part * 2 : part;
		}
		return s * multiplier(feet);
	}

	/**
	 * A card after the reveal of its first {@code revealed} cells.
	 *
	 * @param burnedAt     per cell: index of the Creeper cell that burned it, −1 = not burned
	 * @param creeperBurns per cell: for a Creeper, the cell it burned (−1 = fizzled); −2 otherwise
	 * @param running      score after cells 1…k (index k−1)
	 * @param feet         Rabbit's Feet among the revealed cells (uncapped)
	 * @param best         highest surviving cell value (the tie-break)
	 */
	public record Evaluation(int[] cells, int revealed, int[] burnedAt, int[] creeperBurns, long[] running, int feet, long score, long best) {
		public boolean burned(int cell) {
			return burnedAt[cell] >= 0;
		}

		public int multiplier() {
			return ShowdownCard.multiplier(feet);
		}
	}

	public static Evaluation evaluate(int[] cells, int[] values) {
		return evaluate(cells, values, CELLS);
	}

	/** Reveals cells 0…revealed−1 in order and applies creepers / feet as they appear. */
	public static Evaluation evaluate(int[] cells, int[] values, int revealed) {
		int[] burnedAt = new int[CELLS];
		int[] creeper = new int[CELLS];
		long[] running = new long[CELLS];
		java.util.Arrays.fill(burnedAt, -1);
		java.util.Arrays.fill(creeper, -2);
		int[] counts = new int[Sym.VALUED];
		int feet = 0;
		for (int c = 0; c < revealed; c++) {
			int s = cells[c];
			if (s < Sym.VALUED) {
				counts[s]++;
			} else if (s == Sym.CREEPER.ordinal()) {
				int target = -1;
				for (int j = 0; j < c; j++) {
					if (cells[j] < Sym.VALUED && burnedAt[j] < 0 && (target < 0 || values[cells[j]] > values[cells[target]])) {
						target = j;
					}
				}
				creeper[c] = target;
				if (target >= 0) {
					burnedAt[target] = c;
					counts[cells[target]]--;
				}
			} else {
				feet++;
			}
			running[c] = score(counts, feet, values);
		}
		long best = 0;
		for (int c = 0; c < revealed; c++) {
			if (cells[c] < Sym.VALUED && burnedAt[c] < 0) {
				best = Math.max(best, values[cells[c]]);
			}
		}
		long score = revealed == 0 ? 0 : running[revealed - 1];
		return new Evaluation(cells.clone(), revealed, burnedAt, creeper, running, feet, score, best);
	}

	/**
	 * Exact distribution of a card (PVP.md §8.4 / §16.6 R5): dynamic programme over the reveal, state =
	 * surviving count per valued symbol + feet (capped at 2). Needs distinct values for the Creeper rule to
	 * depend only on the counts (true for the defaults); otherwise the result is an approximation.
	 *
	 * @param scores probability per score
	 * @param feet   P(0 feet), P(1 foot), P(≥ 2 feet)
	 */
	public record Distribution(TreeMap<Long, Double> scores, double[] feet) {
		public double mean() {
			double m = 0;
			for (Map.Entry<Long, Double> e : scores.entrySet()) {
				m += e.getKey() * e.getValue();
			}
			return m;
		}

		/** P(two independent cards score the same before the tie-break) = Σ p². */
		public double tieProbability() {
			double t = 0;
			for (double p : scores.values()) {
				t += p * p;
			}
			return t;
		}
	}

	public static Distribution exact(int[] weights, int[] values) {
		double total = 0;
		for (int w : weights) {
			total += w;
		}
		// valued symbols by value, highest first (a creeper burns the first present one)
		Integer[] byValue = new Integer[Sym.VALUED];
		for (int i = 0; i < Sym.VALUED; i++) {
			byValue[i] = i;
		}
		java.util.Arrays.sort(byValue, (a, b) -> Integer.compare(values[b], values[a]));
		Map<Long, Double> states = new HashMap<>();
		states.put(0L, 1.0);
		for (int c = 0; c < CELLS; c++) {
			Map<Long, Double> next = new HashMap<>();
			for (Map.Entry<Long, Double> e : states.entrySet()) {
				long key = e.getKey();
				for (int s = 0; s < weights.length; s++) {
					if (weights[s] == 0) {
						continue;
					}
					double p = e.getValue() * weights[s] / total;
					long k = key;
					if (s < Sym.VALUED) {
						k += 1L << (4 * s);
					} else if (s == Sym.CREEPER.ordinal()) {
						for (int v : byValue) {
							if ((k >> (4 * v) & 0xF) > 0) {
								k -= 1L << (4 * v);
								break;
							}
						}
					} else if ((k >> 24 & 0xF) < MAX_FEET_COUNTED) {
						k += 1L << 24;
					}
					next.merge(k, p, Double::sum);
				}
			}
			states = next;
		}
		TreeMap<Long, Double> scores = new TreeMap<>();
		double[] feet = new double[MAX_FEET_COUNTED + 1];
		int[] counts = new int[Sym.VALUED];
		for (Map.Entry<Long, Double> e : states.entrySet()) {
			long k = e.getKey();
			for (int s = 0; s < Sym.VALUED; s++) {
				counts[s] = (int) (k >> (4 * s) & 0xF);
			}
			int f = (int) (k >> 24 & 0xF);
			scores.merge(score(counts, f, values), e.getValue(), Double::sum);
			feet[f] += e.getValue();
		}
		return new Distribution(scores, feet);
	}
}

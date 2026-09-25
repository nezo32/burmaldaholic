package dev.nezo.burmaldaholic.games.extras.logic;

import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Scratch Cards (GAME_DESIGN.md §11.3). The outcome is drawn once from the card's prize table, then a
 * 3×3 face is built to match it:
 * <ul>
 *   <li>winning card: exactly 3 cells show the prize symbol; the other 6 show other symbols, each at most twice;</li>
 *   <li>losing card: no symbol appears 3+ times;</li>
 *   <li>Creeper card (share {@code creeperChance} of losing cards): 3 creeper cells, no prize.</li>
 * </ul>
 * A symbol is a prize amount (&gt; 0) shown as a number; {@link #CREEPER} (0) is the creeper symbol. Pure Java.
 */
public final class Scratch {
	public static final int CREEPER = 0;
	public static final int CELLS = 9;

	private Scratch() {}

	public enum Kind {
		BASIC("basic", 0), GOLD("gold", 1);

		private final String id;
		private final int minTier;

		Kind(String id, int minTier) {
			this.id = id;
			this.minTier = minTier;
		}

		public String id() {
			return id;
		}

		/** Minimum VIP tier to buy (Basic: Bronze, Gold: Silver). */
		public int minTier() {
			return minTier;
		}

		public static Kind parse(String s) {
			for (Kind k : values()) {
				if (k.id.equals(s)) {
					return k;
				}
			}
			return null;
		}
	}

	public record Prize(long amount, double probability) {}

	/**
	 * Config {@code extras.scratch.<kind>.prizes} → a clean table: integer prize ≥ 1 with p &gt; 0, merged by
	 * prize, ascending; if Σp &gt; 1 the probabilities are scaled to sum to 1.
	 */
	public static List<Prize> table(double[][] configured) {
		Map<Long, Double> merged = new TreeMap<>();
		if (configured != null) {
			for (double[] row : configured) {
				if (row == null || row.length != 2 || !Double.isFinite(row[0]) || !Double.isFinite(row[1])) {
					continue;
				}
				long amount = (long) Math.floor(row[0]);
				if (amount >= 1 && row[1] > 0) {
					merged.merge(amount, row[1], Double::sum);
				}
			}
		}
		double total = merged.values().stream().mapToDouble(Double::doubleValue).sum();
		double scale = total > 1 ? 1 / total : 1;
		List<Prize> out = new ArrayList<>();
		merged.forEach((a, p) -> out.add(new Prize(a, p * scale)));
		return List.copyOf(out);
	}

	/** RTP of a card: Σ prize × p / price. */
	public static double rtp(List<Prize> table, long price) {
		double s = 0;
		for (Prize p : table) {
			s += p.amount() * p.probability();
		}
		return price <= 0 ? 0 : s / price;
	}

	public static long topPrize(List<Prize> table) {
		long m = 0;
		for (Prize p : table) {
			m = Math.max(m, p.amount());
		}
		return m;
	}

	/** @param prize chips won (0 = losing card); @param creeper losing card with three creepers (chaos mob_wave) */
	public record Outcome(long prize, boolean creeper) {
		public static final Outcome LOSE = new Outcome(0, false);
	}

	/**
	 * Draws the outcome of one card with {@link CasinoRng#weighted} (a prize is the player-favourable group,
	 * so VIP/chaos/Last Chance modifiers apply; without them it is the plain table). The streak re-draw is
	 * applied by the caller ({@code OddsService.play}).
	 */
	public static Outcome draw(CasinoRng rng, List<Prize> table, double creeperChance) {
		List<CasinoRng.Weighted<Long>> outcomes = new ArrayList<>();
		double sum = 0;
		for (Prize p : table) {
			outcomes.add(new CasinoRng.Weighted<>(p.amount(), p.probability()));
			sum += p.probability();
		}
		if (sum < 1) {
			outcomes.add(new CasinoRng.Weighted<>(0L, 1 - sum));
		}
		long prize = outcomes.isEmpty() ? 0 : rng.weighted(outcomes, a -> a > 0);
		if (prize > 0) {
			return new Outcome(prize, false);
		}
		return new Outcome(0, rng.nextDouble() < creeperChance);
	}

	/**
	 * Symbols a card of this table may show. Tables with fewer than 4 prizes get decoy amounts (never a
	 * prize of this table) so a losing card can still avoid any triple.
	 */
	public static List<Long> symbolsFor(List<Prize> table) {
		List<Long> s = new ArrayList<>();
		for (Prize p : table) {
			s.add(p.amount());
		}
		long base = s.isEmpty() ? 1 : Math.max(1, s.get(0));
		for (long k = 2; s.size() < 4; k++) {
			if (!s.contains(base * k)) {
				s.add(base * k);
			}
		}
		return s;
	}

	/** Take {@code n} symbols from {@code pool}, each at most twice, in random order. */
	private static List<Long> upToTwice(CasinoRng rng, List<Long> pool, int n) {
		List<Long> doubled = new ArrayList<>(pool);
		doubled.addAll(pool);
		rng.shuffle(doubled);
		return new ArrayList<>(doubled.subList(0, n));
	}

	/** Builds the 3×3 face (row-major) for an outcome. */
	public static long[] buildFace(CasinoRng rng, List<Prize> table, Outcome o) {
		List<Long> symbols = symbolsFor(table);
		List<Long> cells = new ArrayList<>();
		if (o.prize() > 0) {
			List<Long> others = new ArrayList<>();
			for (Long s : symbols) {
				if (s != o.prize()) {
					others.add(s);
				}
			}
			others.add((long) CREEPER);
			cells.add(o.prize());
			cells.add(o.prize());
			cells.add(o.prize());
			cells.addAll(upToTwice(rng, others, CELLS - 3));
		} else if (o.creeper()) {
			cells.add((long) CREEPER);
			cells.add((long) CREEPER);
			cells.add((long) CREEPER);
			cells.addAll(upToTwice(rng, symbols, CELLS - 3));
		} else {
			List<Long> pool = new ArrayList<>(symbols);
			pool.add((long) CREEPER);
			cells.addAll(upToTwice(rng, pool, CELLS));
		}
		rng.shuffle(cells);
		long[] out = new long[CELLS];
		for (int i = 0; i < CELLS; i++) {
			out[i] = cells.get(i);
		}
		return out;
	}

	public static Map<Long, Integer> counts(long[] cells) {
		Map<Long, Integer> m = new TreeMap<>();
		for (long c : cells) {
			m.merge(c, 1, Integer::sum);
		}
		return m;
	}

	/** Checks a face against the §11.3 rules for its outcome. */
	public static boolean faceMatches(long[] cells, Outcome o) {
		if (cells.length != CELLS) {
			return false;
		}
		List<Map.Entry<Long, Integer>> triples = counts(cells).entrySet().stream().filter(e -> e.getValue() >= 3).toList();
		if (o.prize() > 0) {
			return triples.size() == 1 && triples.get(0).getKey() == o.prize() && triples.get(0).getValue() == 3;
		}
		if (o.creeper()) {
			return triples.size() == 1 && triples.get(0).getKey() == CREEPER && triples.get(0).getValue() == 3;
		}
		return triples.isEmpty();
	}

	/** Number of revealed cells in a 9-bit mask. */
	public static int revealedCount(int mask) {
		return Integer.bitCount(mask & ((1 << CELLS) - 1));
	}

	public static boolean fullyRevealed(int mask) {
		return revealedCount(mask) >= CELLS;
	}
}

package dev.nezo.burmaldaholic.games.slots.logic;

import java.util.Map;
import java.util.TreeMap;

/**
 * Exact per-line statistics by enumerating every (a, b, c) of the machine's symbols (PURE). Cells are
 * independent, so every payline has the same distribution and the per-line RTP is the machine RTP
 * (§8.1). A progressive jackpot returns exactly its contribution rate in the long run (the pool pays
 * out what the spins put in), so total RTP = base + contribution.
 */
public final class SlotRtp {
	private SlotRtp() {}

	/**
	 * @param baseRtp      expected line return / line bet, jackpot excluded
	 * @param hitRate      P(line pays &gt; 0)
	 * @param pJackpot     P(three stars) per line on a progressive machine
	 * @param outcomes     probability per label: {@code berry1}, {@code berry2}, {@code three:<sym>}, {@code wild}, {@code special:<sym>}
	 * @param secondMoment E[(line multiplier)²] (Monte-Carlo tolerances)
	 */
	public record LineStats(double baseRtp, double hitRate, double pJackpot, Map<String, Double> outcomes, double secondMoment) {
		public double pNoWin() {
			return 1 - hitRate;
		}

		public double p(String label) {
			return outcomes.getOrDefault(label, 0.0);
		}
	}

	public static String label(SlotEngine.LineKind kind, Symbol symbol) {
		return switch (kind) {
			case BERRY1 -> "berry1";
			case BERRY2 -> "berry2";
			case WILD -> "wild";
			case THREE -> "three:" + symbol.id();
			case SPECIAL -> "special:" + symbol.id();
		};
	}

	public static LineStats lineStats(SlotTable table) {
		Map<String, Double> outcomes = new TreeMap<>();
		double total = table.totalWeight();
		if (total <= 0) {
			return new LineStats(0, 0, 0, outcomes, 0);
		}
		double rtp = 0;
		double hit = 0;
		double second = 0;
		for (Symbol a : table.present()) {
			for (Symbol b : table.present()) {
				for (Symbol c : table.present()) {
					SlotEngine.LineResult r = SlotEngine.evaluateLine(a, b, c, table);
					if (r == null) {
						continue;
					}
					double p = table.weight(a) / total * (table.weight(b) / total) * (table.weight(c) / total);
					if (r.multiplier() > 0) {
						hit += p;
					}
					rtp += p * r.multiplier();
					second += p * r.multiplier() * r.multiplier();
					outcomes.merge(label(r.kind(), r.symbol()), p, Double::sum);
				}
			}
		}
		double pJackpot = table.progressive() ? outcomes.getOrDefault("special:star", 0.0) : 0;
		return new LineStats(rtp, hit, pJackpot, Map.copyOf(outcomes), second);
	}

	/** Total RTP including the jackpot contribution (progressive machines only). */
	public static double totalRtp(SlotTable table, double contribution) {
		return lineStats(table).baseRtp() + (table.progressive() ? Math.max(0, contribution) : 0);
	}
}

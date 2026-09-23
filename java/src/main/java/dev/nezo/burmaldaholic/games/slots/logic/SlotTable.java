package dev.nezo.burmaldaholic.games.slots.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * One machine's math (PURE): symbol weights, 3-of-a-kind multipliers, berry partial pays, number of
 * paylines and whether three Nether Stars award the progressive jackpot. Built from (possibly
 * admin-edited) config maps; unknown symbols are ignored and invalid/negative values become 0.
 */
public final class SlotTable {
	/** Default star pay at owned machines (config {@code slots.ownedStarPays}). */
	public static final double DEFAULT_OWNED_STAR_PAYS = 1000;

	private final int[] weights = new int[Symbol.count()];
	private final double[] pays = new double[Symbol.count()];
	private final double[] berryPartial = new double[2];
	private final int lines;
	private final boolean progressive;
	private final int totalWeight;
	private final List<Symbol> present;

	private SlotTable(Map<String, ? extends Number> weightMap, Map<String, ? extends Number> payMap, double[] partial,
			int lines, boolean progressive) {
		if (weightMap != null) {
			weightMap.forEach((k, v) -> {
				Symbol s = Symbol.byId(k);
				if (s != null && v != null && Double.isFinite(v.doubleValue()) && v.intValue() > 0) {
					weights[s.ordinal()] = v.intValue();
				}
			});
		}
		if (payMap != null) {
			payMap.forEach((k, v) -> {
				Symbol s = Symbol.byId(k);
				if (s != null && v != null && Double.isFinite(v.doubleValue()) && v.doubleValue() > 0) {
					pays[s.ordinal()] = v.doubleValue();
				}
			});
		}
		for (int i = 0; i < 2; i++) {
			double v = partial != null && partial.length > i ? partial[i] : 0;
			berryPartial[i] = Double.isFinite(v) && v > 0 ? v : 0;
		}
		this.lines = Math.max(1, Math.min(Paylines.MAX, lines));
		this.progressive = progressive;
		int total = 0;
		List<Symbol> list = new ArrayList<>();
		for (Symbol s : Symbol.values()) {
			if (weights[s.ordinal()] > 0) {
				total += weights[s.ordinal()];
				list.add(s);
			}
		}
		this.totalWeight = total;
		this.present = List.copyOf(list);
	}

	/**
	 * @param progressive three stars award the jackpot (their pay is ignored); when false a star pay
	 *                    must be present in {@code pays} (owned machines: {@code slots.ownedStarPays})
	 */
	public static SlotTable of(Map<String, ? extends Number> weights, Map<String, ? extends Number> pays, double[] berryPartial,
			int lines, boolean progressive) {
		return new SlotTable(weights, pays, berryPartial, lines, progressive);
	}

	/** Defaults of GAME_DESIGN.md §8.2–8.4 (same as the CONFIG.md defaults). */
	public static SlotTable defaults(Tier tier) {
		return defaults(tier, false, DEFAULT_OWNED_STAR_PAYS);
	}

	/** Defaults; {@code owned} machines have no progressive: 3 stars pay {@code ownedStarPays}× (§8.5). */
	public static SlotTable defaults(Tier tier, boolean owned, double ownedStarPays) {
		Map<String, Integer> w = switch (tier) {
			case COPPER -> Map.of("berries", 24, "apple", 20, "golden_carrot", 16, "emerald", 12, "diamond", 8, "seven", 5, "creeper", 15);
			case GOLD -> Map.of("berries", 22, "apple", 19, "golden_carrot", 16, "emerald", 12, "diamond", 8, "seven", 5, "wild", 3,
				"creeper", 8, "pearl", 5, "star", 2);
			case NETHERITE -> Map.ofEntries(Map.entry("berries", 20), Map.entry("apple", 19), Map.entry("golden_carrot", 16),
				Map.entry("emerald", 12), Map.entry("diamond", 9), Map.entry("seven", 6), Map.entry("wild", 3), Map.entry("tnt", 6),
				Map.entry("pearl", 5), Map.entry("clock", 2), Map.entry("star", 2));
		};
		Map<String, Double> p = new java.util.HashMap<>(switch (tier) {
			case COPPER -> Map.of("berries", 10.0, "apple", 10.0, "golden_carrot", 20.0, "emerald", 30.0, "diamond", 60.0, "seven", 150.0);
			case GOLD -> Map.of("berries", 8.0, "apple", 7.0, "golden_carrot", 11.0, "emerald", 25.0, "diamond", 50.0, "seven", 100.0,
				"wild", 200.0, "pearl", 10.0);
			case NETHERITE -> Map.of("berries", 8.0, "apple", 9.0, "golden_carrot", 14.0, "emerald", 25.0, "diamond", 50.0, "seven", 100.0,
				"wild", 250.0, "pearl", 10.0, "clock", 50.0);
		});
		boolean progressive = tier.progressive() && !owned;
		if (owned && tier.progressive()) {
			p.put("star", ownedStarPays);
		}
		return of(w, p, new double[] {2, 3}, tier.lines(), progressive);
	}

	public int weight(Symbol s) {
		return weights[s.ordinal()];
	}

	/** 3-of-a-kind multiplier (stake included: "pays 10×" = 10 × line bet). */
	public double pay(Symbol s) {
		return pays[s.ordinal()];
	}

	/** Multiplier for {@code count} (1 or 2) leading Sweet Berries. */
	public double berryPartial(int count) {
		return count == 1 ? berryPartial[0] : count == 2 ? berryPartial[1] : 0;
	}

	public int lines() {
		return lines;
	}

	public boolean progressive() {
		return progressive;
	}

	public int totalWeight() {
		return totalWeight;
	}

	/** Symbols with weight &gt; 0, in enum order. */
	public List<Symbol> present() {
		return present;
	}

	/** No symbol has a weight (machine cannot be played). */
	public boolean empty() {
		return totalWeight <= 0;
	}

	/** Highest multiplier any single line can pay (bankroll worst case, §18.2). */
	public double bestLineMultiplier() {
		double best = Math.max(berryPartial[0], berryPartial[1]);
		for (Symbol s : present) {
			if (s == Symbol.STAR && progressive) {
				continue;
			}
			best = Math.max(best, pays[s.ordinal()]);
		}
		return best;
	}

	@Override
	public String toString() {
		return "SlotTable{weights=" + Arrays.toString(weights) + ", pays=" + Arrays.toString(pays) + ", berry="
			+ Arrays.toString(berryPartial) + ", lines=" + lines + ", progressive=" + progressive + "}";
	}
}

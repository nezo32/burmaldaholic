package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Maps;
import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.Validatable;
import dev.nezo.burmaldaholic.core.config.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Config section `extras` — keys, defaults and ranges from docs/design/CONFIG.md. */
public final class ExtrasConfig implements Validatable {
	public static final class CoinFlip {
		public boolean enabled = true;
		/** Win pays X:1. */
		@Range(min = 0.5, max = 1) public double payout = 0.96;
	}
	public CoinFlip coinFlip = new CoinFlip();

	public static final class Wheel {
		public boolean enabled = true;
		/** × tier max. */
		@Range(min = 0.01, max = 1) public double maxBetFraction = 0.5;
		/** Segment codes in order. */
		/** Segment codes in order (GAME_DESIGN.md appendix B). */
		@Size(min = 2, max = 100) public List<String> segments = new ArrayList<>(List.of(
			"X", "B", "M", "B", "D", "B", "M", "B", "H", "B", "D", "B", "M", "B", "T", "B", "M", "B", "D", "B", "H", "B", "M", "B", "D", "B", "E",
			"B", "M", "B", "D", "B", "H", "B", "M", "B", "T", "B", "M", "B", "D", "B", "H", "B", "M", "B", "T", "B", "C", "M", "H", "D", "M", "B"));
		@Range(min = 0, max = 1000) public Map<String, Double> multipliers = Maps.of("B", 0.0, "C", 0.0, "H", 0.5, "M", 1.0, "D", 2.0, "T", 3.0, "E", 5.0, "X", 10.0);
	}
	public Wheel wheel = new Wheel();

	public static final class Scratch {
		public boolean enabled = true;
		public static final class Basic {
			@Range(min = 1, max = 1000000) public int price = 10;
			/** (prize, probability) pairs; Σp ≤ 1. */
			/** (prize, probability) pairs; Σp ≤ 1. */
			public double[][] prizes = {{10, 0.22}, {20, 0.10}, {50, 0.03}, {100, 0.01}, {500, 0.002}, {2500, 0.0001}};
		}
		public Basic basic = new Basic();

		public static final class Gold {
			@Range(min = 1, max = 1000000) public int price = 100;
			/** (prize, probability) pairs; Σp ≤ 1. */
			public double[][] prizes = {{100, 0.20}, {200, 0.10}, {500, 0.05}, {1000, 0.01}, {5000, 0.001}, {25000, 0.0002}};
		}
		public Gold gold = new Gold();

		/** Share of losing cards that are Creeper cards. */
		@Range(min = 0, max = 0.5) public double creeperChance = 0.01;
	}
	public Scratch scratch = new Scratch();

	public static final class Plinko {
		public boolean enabled = true;
		@Range(min = 0.01, max = 1) public double maxBetFraction = 0.2;
		@Range(min = 0, max = 100000) @Size(min = 13, max = 13) public double[] low = {10, 3, 1.6, 1.4, 1.0, 1.0, 0.5, 1.0, 1.0, 1.4, 1.6, 3, 10};
		@Range(min = 0, max = 100000) @Size(min = 13, max = 13) public double[] medium = {33, 11, 4, 2, 1.0, 0.6, 0.3, 0.6, 1.0, 2, 4, 11, 33};
		@Range(min = 0, max = 100000) @Size(min = 13, max = 13) public double[] high = {170, 24, 8.1, 2, 0.6, 0.2, 0.2, 0.2, 0.6, 2, 8.1, 24, 170};
	}
	public Plinko plinko = new Plinko();

	public static final class DiceDuel {
		public boolean enabled = true;
		/** Tie totals the house wins (others push). */
		@Range(min = 2, max = 12) @Size(min = 0, max = 11) public int[] houseWinsTieOn = {7};
		public boolean pvpEnabled = true;
		@Range(min = 0, max = 20) public int pvpRakePercent = 0;
		@Range(min = 100, max = 6000) public int challengeTimeoutTicks = 600;
		@Range(min = 2, max = 128) public int maxDistance = 16;
	}
	public DiceDuel diceDuel = new DiceDuel();


	@Override
	public void validate(Issues issues) {
		scratch.basic.prizes = checkPrizes(issues, "scratch.basic.prizes", scratch.basic.prizes, new Scratch.Basic().prizes);
		scratch.gold.prizes = checkPrizes(issues, "scratch.gold.prizes", scratch.gold.prizes, new Scratch.Gold().prizes);
		for (String code : wheel.segments) {
			if (!wheel.multipliers.containsKey(code)) {
				List<String> def = new Wheel().segments;
				issues.invalid("wheel.segments", wheel.segments, def);
				wheel.segments = def;
				break;
			}
		}
	}

	private static double[][] checkPrizes(Issues issues, String key, double[][] prizes, double[][] def) {
		double sum = 0;
		for (double[] p : prizes) {
			if (p.length != 2 || p[0] < 0 || p[1] < 0 || p[0] != Math.floor(p[0])) {
				issues.invalid(key, java.util.Arrays.deepToString(prizes), java.util.Arrays.deepToString(def));
				return def;
			}
			sum += p[1];
		}
		if (sum > 1.0 + 1e-9) {
			issues.invalid(key, java.util.Arrays.deepToString(prizes), java.util.Arrays.deepToString(def));
			return def;
		}
		return prizes;
	}
}

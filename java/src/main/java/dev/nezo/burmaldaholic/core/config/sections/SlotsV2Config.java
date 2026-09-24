package dev.nezo.burmaldaholic.core.config.sections;

import dev.nezo.burmaldaholic.core.config.Maps;
import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.Size;
import dev.nezo.burmaldaholic.core.config.Validatable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Slots v2 config objects (SLOTS.md §12), bound under {@code slots.<m>.*} by {@link SlotsConfig}. Defaults are the
 * normative numbers of SLOTS.md; the module turns them into engine definitions ({@code SlotMachinesV2}) and rejects
 * invalid strips/pays there (default kept, logged). Deviation from the SLOTS.md §12 key list: JSON cannot hold
 * {@code slots.<m>.freeSpins} as both a list and an object, so the spin awards live in {@code freeSpins.awards}
 * next to {@code freeSpins.retrigger / cap / multiplier}.
 */
public final class SlotsV2Config {
	private SlotsV2Config() {}

	/** Fields shared by the three machines ({@code slots.<m>.*}). */
	public static class Machine implements Validatable {
		public boolean enabled = true;
		/** Bet ladder: 1–8 entries, multiples of 5, sorted. */
		@Range(min = 5, max = 1000000) @Size(min = 1, max = 8) public int[] bets;
		@Range(min = 5, max = 1000000) public int defaultBet;
		@Range(min = 0, max = 5) public int minVipTier;
		/** Max-win cap and owned reservation (× bet). */
		@Range(min = 50, max = 100000) public int maxWinMultiple;
		/** Advanced: 5 strings of symbol codes (Appendix A). */
		@Size(min = 5, max = 5) public String[] strips;
		/** 3/4/5-of-a-kind × bet per way (multiples of 0.2). */
		@Range(min = 0, max = 10000) public Map<String, double[]> pays;
		/** × bet for 3/4/5 scatters. */
		@Range(min = 0, max = 10000) @Size(min = 3, max = 3) public double[] scatterPays;
		public FreeSpins freeSpins = new FreeSpins();
		public Jackpot jackpot = new Jackpot();

		Machine(int[] bets, int defaultBet, int minVip, int cap, String[] strips, Map<String, double[]> pays, double[] scatterPays) {
			this.bets = bets;
			this.defaultBet = defaultBet;
			this.minVipTier = minVip;
			this.maxWinMultiple = cap;
			this.strips = strips;
			this.pays = pays;
			this.scatterPays = scatterPays;
		}

		@Override
		public void validate(Issues issues) {
			int[] sorted = bets.clone();
			Arrays.sort(sorted);
			boolean ok = Arrays.equals(sorted, bets);
			for (int b : bets) ok &= b % 5 == 0;
			if (!ok) {
				int[] def = defaultBets();
				issues.invalid("bets", Arrays.toString(bets), Arrays.toString(def));
				bets = def;
			}
			int nearest = bets[0];
			for (int b : bets) if (Math.abs(b - defaultBet) < Math.abs(nearest - defaultBet)) nearest = b;
			if (nearest != defaultBet) {
				issues.clamped("defaultBet", defaultBet, nearest);
				defaultBet = nearest;
			}
			for (Map.Entry<String, double[]> e : pays.entrySet()) {
				double[] v = e.getValue();
				boolean good = v.length == 3;
				for (double x : v) good &= Math.abs(x * 5 - Math.rint(x * 5)) < 1e-9;
				if (!good) {
					issues.invalid("pays." + e.getKey(), Arrays.toString(v), "0");
					e.setValue(new double[] {0, 0, 0});
				}
			}
			for (double x : scatterPays) {
				if (Math.abs(x - Math.rint(x)) > 1e-9) {
					issues.invalid("scatterPays", Arrays.toString(scatterPays), "integers");
					break;
				}
			}
		}

		int[] defaultBets() {
			return new int[] {5, 10, 20, 50, 100};
		}
	}

	public static final class FreeSpins {
		/** Spins for 3 / 4 / 5 scatters. */
		@Range(min = 0, max = 100) @Size(min = 3, max = 3) public int[] awards = {8, 10, 15};
		@Range(min = 0, max = 100) public int retrigger = 8;
		/** Max spins awarded per feature. */
		@Range(min = 1, max = 500) public int cap = 50;
		/** Overworld: all wins × this in free spins (ignored elsewhere). */
		@Range(min = 1, max = 10) public int multiplier = 1;

		public FreeSpins() {}

		FreeSpins(int[] awards, int retrigger, int cap, int multiplier) {
			this.awards = awards;
			this.retrigger = retrigger;
			this.cap = cap;
			this.multiplier = multiplier;
		}
	}

	public static final class Jackpot {
		/** Full jackpot at this bet or above. */
		@Range(min = 5, max = 1000000) public int refBet = 100;
		/** Pool seeds, multiples of {@code refBet}. */
		@Range(min = 0, max = 100000) public Map<String, Integer> seed = Maps.of("mini", 10, "minor", 25, "major", 100, "grand", 500);
		/** Fraction of every stake. */
		@Range(min = 0, max = 0.05) public Map<String, Double> contribution = Maps.of("mini", 0.004, "minor", 0.003, "major", 0.002, "grand", 0.001);
		/** Fixed × bet at owned machines. */
		@Range(min = 0, max = 100000) public Map<String, Integer> owned = Maps.of("mini", 10, "minor", 25, "major", 100, "grand", 250);

		public Jackpot() {}

		Jackpot(int refBet, int[] seed, double[] contribution, int[] owned) {
			this.refBet = refBet;
			this.seed = Maps.of("mini", seed[0], "minor", seed[1], "major", seed[2], "grand", seed[3]);
			this.contribution = Maps.of("mini", contribution[0], "minor", contribution[1], "major", contribution[2], "grand", contribution[3]);
			this.owned = Maps.of("mini", owned[0], "minor", owned[1], "major", owned[2], "grand", owned[3]);
		}
	}

	private static Map<String, double[]> pays(String[] ids, double[][] rows) {
		Map<String, double[]> m = new LinkedHashMap<>();
		for (int i = 0; i < ids.length; i++) m.put(ids[i], rows[i]);
		return m;
	}

	/** {@code slots.overworld} — Overworld Riches. */
	public static final class Overworld extends Machine {
		public Pick pick = new Pick();

		public Overworld() {
			super(new int[] {5, 10, 20, 50, 100}, 10, 0, 500, new String[] {
				"AP CA GO BE BE AP AP CA IR CA AP SC DI BN GO WH GO BN BE CA BE EM IR DI EM EM AP BN BE BE WH CA IR WH DI SC WH IR GO WH",
				"CA AP CA BE WH BE WH GO WH GO IR SC GO CA EM AP EM BE BE IR IR WD WD WH WH CA EM DI DI CA IR WD WD BE AP DI AP AP GO BE",
				"AP EM BE IR WH WH BN DI CA DI DI BE GO EM WD WD IR CA BE AP GO AP CA IR CA WD WD GO WH IR WH BE GO AP EM SC AP BE CA BN",
				"AP WH BE AP CA EM BE WH GO AP GO BE WD WD AP WD WD AP CA GO CA IR GO BE EM WH IR IR DI DI CA BE WH DI SC CA WH BE EM IR",
				"BE WH WH BE BE WH EM GO CA BN BE AP IR CA IR AP SC DI EM BE AP DI AP BN GO CA CA BE AP WH GO DI BN EM IR IR SC GO WH CA"},
				pays(new String[] {"diamond", "emerald", "gold_ingot", "iron_ingot", "apple", "carrot", "wheat", "sweet_berries"},
					new double[][] {{0.8, 2, 4}, {0.6, 1.2, 2.4}, {0.4, 0.8, 1.6}, {0.4, 0.8, 1.6}, {0.2, 0.4, 0.8}, {0.2, 0.4, 0.8}, {0.2, 0.4, 0.8},
						{0.2, 0.4, 0.8}}),
				new double[] {1, 10, 50});
			freeSpins = new FreeSpins(new int[] {8, 10, 15}, 8, 50, 2);
			jackpot = new Jackpot(100, new int[] {10, 25, 100, 500}, new double[] {0.004, 0.003, 0.002, 0.001}, new int[] {10, 25, 100, 250});
		}
	}

	public static final class Pick {
		/** Chests on the board. */
		@Range(min = 3, max = 30) public int board = 15;
		@Range(min = 0, max = 10000000) public Map<String, Integer> weights = Maps.of("x1", 30000, "x2", 22000, "x3", 14000, "x5", 9000, "x10", 3500,
			"x25", 800, "mini", 600, "minor", 150, "major", 20, "grand", 3, "creeper", 22000);
	}

	/** {@code slots.nether} — Nether Inferno. */
	public static final class Nether extends Machine {
		public Tumble tumble = new Tumble();
		public Hold hold = new Hold();
		public Buy buy = new Buy(18.4);

		public Nether() {
			super(new int[] {10, 20, 50, 100, 250, 500}, 20, 0, 2000, new String[] {
				"NW BR BR WF CF SC NW WF GD BR CF CF MC QZ NW NW MC QZ GD QZ CF WF WF MC GD CN CN NW GD SK SK CF",
				"SK SC BR MC MC WF CF NW WF NW MC NW QZ CF GD GD BR WF WD WF QZ CF GD WD NW SK QZ CN CN CF WF GD",
				"WF NW MC MC CF WF CN QZ QZ NW CF WD CF WF WD GD BR CF SK NW GD CN CN SC GD MC GD WF SK NW QZ BR",
				"CF BR NW QZ NW WF BR QZ CF MC SK CF NW SC WF GD MC SK WD CN CN WF WD WF CF GD WF GD GD QZ MC NW",
				"SK WF CF CN CN SK GD NW WF NW BR CF MC NW BR GD QZ BR CF MC GD QZ WF QZ GD MC NW CF NW CF SC WF"},
				pays(new String[] {"wither_skull", "blaze_rod", "magma_cream", "quartz", "nether_wart", "crimson_fungus", "warped_fungus", "glowstone"},
					new double[][] {{0.8, 2, 8}, {0.6, 1.2, 4}, {0.4, 0.8, 2}, {0.4, 0.8, 1.6}, {0.2, 0.4, 0.6}, {0.2, 0.4, 0.6}, {0.2, 0.2, 0.6},
						{0.2, 0.2, 0.6}}),
				new double[] {0, 0, 0});
			freeSpins = new FreeSpins(new int[] {12, 15, 20}, 5, 60, 1);
			jackpot = new Jackpot(500, new int[] {10, 30, 150, 1000}, new double[] {0.005, 0.004, 0.0035, 0.0025}, new int[] {10, 30, 150, 500});
		}

		@Override
		int[] defaultBets() {
			return new int[] {10, 20, 50, 100, 250, 500};
		}
	}

	public static final class Tumble {
		/** Base game ladder. */
		@Range(min = 1, max = 100) @Size(min = 4, max = 4) public int[] ladder = {1, 2, 3, 5};
		/** Free-spin ladder. */
		@Range(min = 1, max = 100) @Size(min = 4, max = 4) public int[] ladderFree = {2, 4, 6, 10};
	}

	public static final class Hold {
		@Range(min = 3, max = 15) public int trigger = 6;
		@Range(min = 1, max = 10) public int respins = 3;
		/** Per empty cell per respin. */
		@Range(min = 0.0, max = 0.5) public double coinChance = 0.04;
		@Range(min = 0, max = 10000000) public Map<String, Integer> coinWeights = Maps.of("x1", 4000, "x2", 2500, "x3", 1500, "x5", 1000, "x10", 500,
			"x25", 120, "mini", 80, "minor", 20, "major", 3);
	}

	public static final class Buy {
		/** × bet (multiples of 0.2). */
		@Range(min = 1, max = 10000) public double price;

		public Buy() {}

		Buy(double price) {
			this.price = price;
		}
	}

	/** {@code slots.end} — End Void. */
	public static final class End extends Machine {
		public Wheel wheel = new Wheel();
		public Buy buy = new Buy(109);

		public End() {
			super(new int[] {50, 100, 250, 500, 1000, 2500, 5000}, 100, 2, 5000, new String[] {
				"EL PU ES CH SS PU EP CH ES ES PU SC EP EP SS ER PU EP ER CH CH EP ER ER ES DH DH PU EL EP CH ES PU EP ER SS ER ER PU EL ER ES SS ES ES",
				"ER EP ES SS PU PU ER SS ER PU PU CH EL CH ES EP ER EL PU ES ES BN PU ES ES EP ER SS EL DH DH CH ES EP EP BN ES ER CH EP CH WD SC SS ER",
				"CH ER ES ER EP BN PU PU EP CH PU ES EP ES BN ER SS EP EL PU ES CH SS DH DH PU ER ES PU ES BN ES EP WD PU SS EL CH EL EP ER SS ER SC ER",
				"DH DH PU PU CH EL SS PU ES ES ER EP ES ER EL ES SS SS PU EL ES ER ES ER BN CH ER BN SS ER EP PU CH EP EP CH PU CH SC ER ES EP WD ES EP",
				"DH DH PU ER PU CH SC SS PU ER SS EP PU ES EP SS ER CH ER CH ER EP SS EL CH EP EL EP PU ER CH PU ES EP EP ES EL ES ES ER ES PU ES ES ER"},
				pays(new String[] {"dragon_head", "elytra", "shulker_shell", "chorus_fruit", "ender_pearl", "purpur", "end_rod", "end_stone"},
					new double[][] {{2, 8, 30}, {1, 4, 12}, {0.8, 2, 6}, {0.6, 1.6, 5}, {0.4, 0.6, 2}, {0.4, 0.6, 2}, {0.2, 0.6, 1.2}, {0.2, 0.6, 1.2}}),
				new double[] {2, 10, 50});
			freeSpins = new FreeSpins(new int[] {9, 11, 14}, 4, 40, 1);
			jackpot = new Jackpot(5000, new int[] {15, 50, 250, 2500}, new double[] {0.005, 0.005, 0.006, 0.009}, new int[] {15, 50, 250, 1000});
		}

		@Override
		int[] defaultBets() {
			return new int[] {50, 100, 250, 500, 1000, 2500, 5000};
		}
	}

	public static final class Wheel {
		/** Tokens: integer multiples, MINI MINOR MAJOR GRAND, UP (not in core). */
		@Size(min = 4, max = 32) public String[] outer = "10 UP 12 15 MINI 10 20 12 25 10 40 15 UP 12 20 MINI 15 10 75 25".split(" ");
		@Size(min = 4, max = 32) public String[] middle = "30 50 MINOR 75 30 100 50 UP 30 75 MINOR 50 100 30 75 50".split(" ");
		@Size(min = 4, max = 32) public String[] core = "150 MAJOR 250 150 GRAND 250 MAJOR 150 500 250 MAJOR 150".split(" ");
	}

	public static final class BuyFeature {
		public boolean enabled = true;
		/** Price ≤ VIP tier max × this. */
		@Range(min = 1, max = 1000) public int tierMaxMultiple = 25;
	}

	public static final class Autoplay {
		public boolean enabled = true;
		@Range(min = 1, max = 1000) @Size(min = 1, max = 8) public int[] counts = {10, 25, 50, 100};
		/** × bet; one is mandatory. */
		@Range(min = 1, max = 10000) @Size(min = 1, max = 8) public int[] lossLimits = {10, 25, 50, 100};
	}

	public static final class InWorld {
		/** Java BER reels on the cabinets. */
		public boolean enabled = true;
		@Range(min = 0, max = 64) public int radius = 24;
	}

	/** {@code slots.jackpot.announceMinTier}. */
	public enum AnnounceTier {
		MINI,
		MINOR,
		MAJOR,
		GRAND
	}
}

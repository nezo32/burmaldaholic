package dev.nezo.burmaldaholic.games.slots.v2.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SLOTS.md §7.1 table, §7.3 closed forms and §6.3 buy RTP reproduced from the default machines (§15 tests 5–7). */
public class SlotRtpV2Test {
	private static final Map<Machine, SlotRtpV2.Report> CACHE = new EnumMap<>(Machine.class);

	public static synchronized SlotRtpV2.Report report(Machine m) {
		return CACHE.computeIfAbsent(m, k -> SlotRtpV2.compute(SlotDefaults.def(k)));
	}

	/** §7.1 lists percent with 6 decimals. */
	private static void pct(double expectedPercent, double fraction, String what) {
		assertEquals(expectedPercent, fraction * 100, 6e-7, what);
	}

	@Test
	void overworldTable() {
		SlotRtpV2.Report r = report(Machine.OVERWORLD);
		pct(73.159400, r.baseWays(), "base ways");
		pct(1.367930, r.scatterPay(), "scatter");
		pct(14.764273, r.freeSpins(), "free spins 10/12/19");
		pct(7.260196, r.bonus(), "treasure hunt");
		pct(0.445359, r.jackpotSeed(), "jackpot seeds (hunt)");
		pct(0.375, r.jackpotGem(), "jackpot gem: 250 ppm × 10 + 50 ppm × 25");
		pct(1.0, r.jackpotContribution(), "contributions");
		pct(98.372158, r.total(), "total");
		pct(97.346949, r.totalOwned(), "owned");
		assertEquals(0.006382, r.streakCap(), 5e-7);
		assertEquals(9.560752, r.bonusPerTrigger(), 5e-7);
		assertEquals(10.7837, r.fsExpectedSpins()[0], 5e-5);
		assertEquals(20.4880, r.fsExpectedSpins()[2], 5e-5);
		assertEquals(16.0737, r.fsPerTrigger()[0], 5e-5);
		assertEquals(0.39212, r.hitPay(), 5e-6);
		assertTrue(Double.isNaN(r.buyRtp()));
		assertTrue(SlotRtpV2.warnings(r).isEmpty());
	}

	@Test
	void netherTable() {
		SlotRtpV2.Report r = report(Machine.NETHER);
		pct(68.222821, r.baseWays(), "base ways incl. tumbles");
		pct(0, r.scatterPay(), "scatter");
		pct(19.460985, r.freeSpins(), "free spins 14/17/23");
		pct(8.216694, r.bonus(), "hoard");
		pct(0.744099, r.jackpotSeed(), "jackpot seeds (hoard)");
		pct(0.275, r.jackpotGem(), "jackpot gem: 200 ppm × 10 + 25 ppm × 30");
		pct(98.419598, r.total(), "total");
		pct(96.834358, r.totalOwned(), "owned");
		assertEquals(21.386522, r.bonusPerTrigger(), 5e-7);
		assertEquals(20.064491, r.fsPerTrigger()[0], 5e-7);
		assertEquals(0.005897, r.streakCap(), 5e-7);
		assertTrue(SlotRtpV2.warnings(r).isEmpty());
	}

	@Test
	void endTable() {
		SlotRtpV2.Report r = report(Machine.END);
		pct(56.211147, r.baseWays(), "base ways");
		pct(0.614979, r.scatterPay(), "scatter");
		pct(28.057181, r.freeSpins(), "void walker");
		pct(9.340370, r.bonus(), "dragon wheel, outer ring 12…100");
		pct(1.357407, r.jackpotSeed(), "jackpot seeds (wheel)");
		pct(0.325, r.jackpotGem(), "jackpot gem: 150 ppm × 15 + 20 ppm × 50");
		pct(2.5, r.jackpotContribution(), "contributions");
		pct(98.406086, r.total(), "total");
		pct(95.628308, r.totalOwned(), "owned");
		assertEquals(102.466892, r.fsPerTrigger()[0], 5e-7);
		assertEquals(170.175953, r.fsPerTrigger()[1], 5e-7);
		assertEquals(311.794284, r.fsPerTrigger()[2], 5e-7);
		assertEquals(26.269792, r.bonusPerTrigger(), 5e-7);
		assertEquals(0.006035, r.streakCap(), 5e-7);
		assertTrue(SlotRtpV2.warnings(r).isEmpty());
	}

	@Test
	void buyRtpParity() {
		// Nether 20.8 × bet, End 107.2 × bet: buy RTP ≤ machine RTP and within 0.5 % of it (SLOTS.md §6.3)
		assertEquals(97.963900, report(Machine.NETHER).buyRtp() * 100, 5e-6);
		assertEquals(98.084787, report(Machine.END).buyRtp() * 100, 5e-6);
	}

	/**
	 * Owner R3: about 98 % per machine, everything funded inside a 98.5 % budget. The gem is an independent draw on every
	 * paid base spin, so its exact RTP is {@code Σ ppm/10^6 × seedMult}, and removing it gives back the lever-only numbers
	 * (97.997 / 98.145 / 98.081 %, exact enumeration).
	 */
	@Test
	void jackpotGemIsFundedInsideTheBudget() {
		double[] levers = {97.997158, 98.144598, 98.081086};
		for (Machine m : Machine.values()) {
			SlotRtpV2.Report r = report(m);
			assertTrue(r.total() <= 0.985 && r.totalOwned() <= 0.985, m + " " + r.total());
			assertTrue(r.total() >= 0.979, m + " about 98 %");
			MachineDef.Features f = SlotDefaults.def(m).features();
			double gem = f.gemPpm(1) / 1e6 * f.jackpotSeedMult()[0] + f.gemPpm(2) / 1e6 * f.jackpotSeedMult()[1];
			assertEquals(gem, r.jackpotGem(), 1e-15);
			pct(levers[m.ordinal()], r.total() - gem, m + " without the gem");
			assertTrue(f.gemPpm(1) >= 150, m + ": a Mini gem is at least 1 in 6 667 spins");
			// combined Mini (gem + bonus game) at most 1 in 2 300 spins: seen in a normal session
			assertTrue(1 / r.jackpotHits()[0] < 2300, m + " Mini 1 in " + 1 / r.jackpotHits()[0]);
		}
	}

	@Test
	void hoardChainAndWheelClosedForms() {
		// §7.3: P(all 15) per trigger and mean final coins; wheel 20.65 + 0.1 × (46.5625 + 0.0625 × 154.1667)
		double[] j = new double[5];
		assertEquals(26.269792, SlotRtpV2.ringEv(SlotDefaults.def(Machine.END).features().wheelRings(), 0, 1, 0, j), 5e-7);
		long[] start = {0, 0, 0, 0, 0, 0, 108_845, 16_483, 3_316, 240, 32};
		double tot = 0;
		double fin = 0;
		double full = 0;
		Map<Integer, double[]> memo = new HashMap<>();
		for (int n = 6; n <= 10; n++) {
			double[] ab = SlotRtpV2.hold(n, 3, 3, 0.04, memo);
			tot += start[n];
			fin += start[n] * ab[0];
			full += start[n] * ab[1];
		}
		assertEquals(7.846836, fin / tot, 5e-7);
		assertEquals(0.00044373, full / tot, 5e-9);
	}

	@Test
	void changingAPayChangesTheResultAndWarns() {
		MachineDef d = SlotDefaults.def(Machine.OVERWORLD);
		int[][] pays = d.paysFifths().clone();
		pays[3] = new int[] {40, 100, 200}; // Diamond ×10
		MachineDef hot = new MachineDef(d.machine(), d.codes(), d.roles(), d.strips(), pays, d.scatterFifths(), d.bonusReelsMask(),
			d.freeSpins(), d.retrigger(), d.fsCap(), d.fsMultiplier(), d.ladder(), d.ladderFree(), d.capMultiple(), d.buyPriceFifths(),
			d.features());
		SlotRtpV2.Report r = SlotRtpV2.compute(hot);
		assertTrue(r.total() > 0.99, "RTP " + r.total());
		assertFalse(SlotRtpV2.warnings(r).isEmpty());
	}
}

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
		pct(11.840624, r.freeSpins(), "free spins");
		pct(7.260196, r.bonus(), "treasure hunt");
		pct(0.445359, r.jackpotSeed(), "jackpot seeds");
		pct(1.0, r.jackpotContribution(), "contributions");
		pct(95.073509, r.total(), "total");
		pct(94.048300, r.totalOwned(), "owned");
		// SLOTS.md §7.1: 0.99 / 0.950 735 09 − 1 = 0.041 300 (the doc printed 0.041 282 before the J-L8 review; fixed)
		assertEquals(0.041300, r.streakCap(), 5e-7);
		assertEquals(9.560752, r.bonusPerTrigger(), 5e-7);
		assertEquals(8.6270, r.fsExpectedSpins()[0], 5e-5);
		assertEquals(16.1754, r.fsExpectedSpins()[2], 5e-5);
		assertEquals(12.8589, r.fsPerTrigger()[0], 5e-5);
		assertEquals(0.39212, r.hitPay(), 5e-6);
		assertTrue(Double.isNaN(r.buyRtp()));
		assertTrue(SlotRtpV2.warnings(r).isEmpty());
	}

	@Test
	void netherTable() {
		SlotRtpV2.Report r = report(Machine.NETHER);
		pct(68.222821, r.baseWays(), "base ways incl. tumbles");
		pct(0, r.scatterPay(), "scatter");
		pct(16.710770, r.freeSpins(), "free spins");
		pct(8.216694, r.bonus(), "hoard");
		pct(0.744099, r.jackpotSeed(), "jackpot seeds");
		pct(95.394383, r.total(), "total");
		pct(93.809143, r.totalOwned(), "owned");
		assertEquals(21.386522, r.bonusPerTrigger(), 5e-7);
		assertEquals(17.1981, r.fsPerTrigger()[0], 5e-5);
		assertEquals(0.037797, r.streakCap(), 5e-7);
		assertTrue(SlotRtpV2.warnings(r).isEmpty());
	}

	@Test
	void endTable() {
		SlotRtpV2.Report r = report(Machine.END);
		pct(56.211147, r.baseWays(), "base ways");
		pct(0.614979, r.scatterPay(), "scatter");
		pct(28.057181, r.freeSpins(), "void walker");
		pct(7.793704, r.bonus(), "dragon wheel");
		pct(1.357407, r.jackpotSeed(), "jackpot seeds");
		pct(2.5, r.jackpotContribution(), "contributions");
		pct(96.534419, r.total(), "total");
		pct(93.756641, r.totalOwned(), "owned");
		assertEquals(102.466892, r.fsPerTrigger()[0], 5e-7);
		assertEquals(170.175953, r.fsPerTrigger()[1], 5e-7);
		assertEquals(311.794284, r.fsPerTrigger()[2], 5e-7);
		assertEquals(21.919792, r.bonusPerTrigger(), 5e-7);
		assertEquals(0.025541, r.streakCap(), 5e-7);
		assertTrue(SlotRtpV2.warnings(r).isEmpty());
	}

	@Test
	void buyRtpParity() {
		assertEquals(94.968, report(Machine.NETHER).buyRtp() * 100, 5e-4);
		assertEquals(96.506, report(Machine.END).buyRtp() * 100, 5e-4);
	}

	@Test
	void hoardChainAndWheelClosedForms() {
		// §7.3: P(all 15) per trigger and mean final coins; wheel 16.3 + 0.1 × (46.5625 + 0.0625 × 154.1667)
		double[] j = new double[5];
		assertEquals(21.919792, SlotRtpV2.ringEv(SlotDefaults.def(Machine.END).features().wheelRings(), 0, 1, 0, j), 5e-7);
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

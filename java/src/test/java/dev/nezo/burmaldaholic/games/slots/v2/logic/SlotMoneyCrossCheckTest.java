package dev.nezo.burmaldaholic.games.slots.v2.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * T-SLT independent cross-checks of the R3 money changes: the gem part of the RTP by hand, the buy parity rule
 * (SLOTS.md §6.3), a Monte-Carlo of the real draw against the exact report, and pool bounds for gem awards.
 */
class SlotMoneyCrossCheckTest {
	private static final Machine[] ALL = Machine.values();

	@Test
	void gemRtpAndBuyParityByHand() {
		double[] gemExpected = {250e-6 * 10 + 50e-6 * 25, 200e-6 * 10 + 25e-6 * 30, 150e-6 * 15 + 20e-6 * 50};
		for (Machine m : ALL) {
			MachineDef d = SlotDefaults.def(m);
			SlotRtpV2.Report r = SlotRtpV2.compute(d);
			assertEquals(gemExpected[m.ordinal()], r.jackpotGem(), 1e-12, m + " gem seed part");
			double sum = r.baseWays() + r.scatterPay() + r.freeSpins() + r.bonus() + r.jackpotSeed() + r.jackpotGem() + r.jackpotContribution();
			assertEquals(r.total(), sum, 1e-12);
			assertTrue(r.total() > 0.98 && r.total() < 0.985, m + " total " + r.total());
			if (d.buyPriceFifths() > 0) {
				double buy = r.fsPerTrigger()[0] / (d.buyPriceFifths() / 5.0) + r.jackpotContribution();
				assertEquals(r.buyRtp(), buy, 1e-12);
				assertTrue(r.buyRtp() <= r.total() && r.total() - r.buyRtp() <= 0.005, m + " buy parity " + r.buyRtp() + " vs " + r.total());
			}
		}
	}

	/** Real draw with a real RNG, pools pinned at seed and bet = ref: mean payout ~ total − contribution (4 SE). */
	@Test
	void monteCarloMatchesTheExactReport() {
		for (Machine m : ALL) {
			MachineDef d = SlotDefaults.def(m);
			SlotRtpV2.Report r = SlotRtpV2.compute(d);
			long ref = d.features().jackpotRef();
			SplittableRandom rnd = new SplittableRandom(0x5107L + m.ordinal());
			SlotRng rng = rnd::nextInt;
			int n = 400_000;
			double sum = 0, sq = 0;
			long[] gems = new long[3];
			for (int i = 0; i < n; i++) {
				SpinTape t = SlotDraw.draw(new SlotDraw.Request(d, ref, false, false, null), rng);
				double x = t.payoutChips() / (double) ref;
				sum += x;
				sq += x * x;
				gems[t.gem()]++;
			}
			double mean = sum / n;
			double se = Math.sqrt((sq / n - mean * mean) / n);
			double expect = r.total() - r.jackpotContribution();
			assertTrue(Math.abs(mean - expect) < 4 * se, m + " MC " + mean + " ± " + se + " vs exact " + expect);
			double pMini = gems[1] / (double) n;
			double ppm = d.features().gemPpm(1) / 1e6;
			assertTrue(Math.abs(pMini - ppm) < 5 * Math.sqrt(ppm / n), m + " gem Mini rate " + pMini + " vs " + ppm);
		}
	}

	private static SlotRng gemAt(int u) {
		Deque<Integer> q = new ArrayDeque<>(java.util.List.of(0, 0, 0, 0, 0));
		return bound -> q.isEmpty() ? u : q.poll() % bound;
	}

	/** A gem award never exceeds its pool, never leaves it below seed, never goes negative, at any bet / pool size. */
	@Test
	void gemAwardsStayInsideThePool() {
		SplittableRandom rnd = new SplittableRandom(7);
		for (Machine m : ALL) {
			MachineDef d = SlotDefaults.def(m);
			MachineDef.Features f = d.features();
			for (int i = 0; i < 20_000; i++) {
				long[] pools = new long[5];
				for (int t = 1; t <= 4; t++) pools[t] = f.seedChips(t) + (rnd.nextInt(4) == 0 ? 0 : rnd.nextLong(1L << 40));
				long bet = d.ladder().length > 0 && rnd.nextBoolean() ? d.ladder()[rnd.nextInt(d.ladder().length)] : 1 + rnd.nextLong(1_000_000);
				int tier = 1 + rnd.nextInt(2);
				int u = tier == 1 ? 0 : f.gemPpm(1);
				long[] before = pools.clone();
				SpinTape tp = SlotDraw.draw(new SlotDraw.Request(d, bet, false, false, pools), gemAt(u));
				assertEquals(tier, tp.gem());
				long chips = tp.gemAward().chips();
				assertTrue(chips >= 0 && chips <= before[tier], m + " award " + chips + " pool " + before[tier]);
				long after = Jackpots.poolAfter(before[tier], f.seedChips(tier), bet, f.jackpotRef());
				assertTrue(after >= f.seedChips(tier) && after <= before[tier], "pool after " + after);
				assertTrue(tp.payoutChips() >= chips);
			}
		}
	}

	/** A bought spin never draws the gem (buy RTP excludes it): the draw count is unchanged vs no gem. */
	@Test
	void buyDrawsNoGem() {
		for (Machine m : ALL) {
			MachineDef d = SlotDefaults.def(m);
			if (d.buyPriceFifths() <= 0) continue;
			SplittableRandom rnd = new SplittableRandom(11);
			for (int i = 0; i < 2_000; i++) {
				SpinTape t = SlotDraw.draw(new SlotDraw.Request(d, 100, true, false, null), rnd::nextInt);
				assertEquals(0, t.gem());
			}
		}
	}
}

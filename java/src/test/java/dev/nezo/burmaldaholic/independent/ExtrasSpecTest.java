package dev.nezo.burmaldaholic.independent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.games.extras.logic.CoinFlip;
import dev.nezo.burmaldaholic.games.extras.logic.DiceDuel;
import dev.nezo.burmaldaholic.games.extras.logic.Plinko;
import dev.nezo.burmaldaholic.games.extras.logic.Scratch;
import dev.nezo.burmaldaholic.games.extras.logic.Wheel;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** GAME_DESIGN.md §11 re-derived: coin flip, wheel (appendix B), scratch cards, plinko, dice duel. */
class ExtrasSpecTest {
	private static CasinoRng fair(long seed) {
		return new OddsService(new SplittableRandom(seed)).fair();
	}

	@Test
	void coinFlip() {
		assertEquals(0.98, CoinFlip.rtp(CoinFlip.DEFAULT_PAYOUT), 1e-12, "RTP 98 %");
		assertEquals(196, CoinFlip.totalReturn(100, true, 0.96), "1.96× total");
		assertEquals(49, CoinFlip.totalReturn(25, true, 0.96), "floor(25 × 0.96) = 24");
		assertEquals(1, CoinFlip.totalReturn(1, true, 0.96), "a 1-chip win returns the stake only");
		assertEquals(0, CoinFlip.totalReturn(100, false, 0.96));
		assertEquals(2000, CoinFlip.soulReturn(1000, true), "Soul Wager pays 1:1");
		CasinoRng rng = fair(3);
		int wins = 0, n = 400_000;
		for (int i = 0; i < n; i++) {
			CoinFlip.Result r = CoinFlip.flip(rng, CoinFlip.Side.HEADS);
			assertEquals(r.win(), r.landed() == r.pick());
			wins += r.win() ? 1 : 0;
		}
		assertEquals(0.5, (double) wins / n, 0.004, "fair coin");
	}

	@Test
	void wheelAppendixB() {
		String spec = "X B M B D B M B H B D B M B T B M B D B H B M B D B E B M B D B H B M B T B M B D B H B M B T B C M H D M B";
		Wheel w = Wheel.standard();
		assertEquals(spec, String.join(" ", w.segments()));
		assertEquals(Map.of("B", 25, "C", 1, "H", 5, "M", 11, "D", 7, "T", 3, "E", 1, "X", 1), w.counts());
		assertEquals(54, w.size());
		assertEquals(51.5 / 54, w.rtp(), 1e-12, "RTP 95.37 %");
		assertEquals(0.9537, w.rtp(), 5e-5);
		assertEquals(10, w.maxMultiplier());
		assertEquals(7, Wheel.totalReturn(15, w.at(8)), "half back floors: 15 × 0.5 = 7");
		assertTrue(w.at(48).creeper());
		assertEquals(0, Wheel.totalReturn(100, w.at(48)));
		CasinoRng rng = fair(9);
		int[] hits = new int[54];
		int n = 540_000;
		for (int i = 0; i < n; i++) {
			hits[w.spin(rng).index()]++;
		}
		for (int h : hits) {
			assertEquals(10_000, h, 450, "uniform segments");
		}
	}

	@Test
	void scratchCards() {
		List<Scratch.Prize> basic = Scratch.table(new double[][] {{10, 0.22}, {20, 0.10}, {50, 0.03}, {100, 0.01}, {500, 0.002}, {2500, 0.0001}});
		List<Scratch.Prize> gold = Scratch.table(new double[][] {{100, 0.20}, {200, 0.10}, {500, 0.05}, {1000, 0.01}, {5000, 0.001}, {25000, 0.0002}});
		assertEquals(0.795, Scratch.rtp(basic, 10), 1e-12, "Basic 79.5 %");
		assertEquals(0.85, Scratch.rtp(gold, 100), 1e-12, "Gold 85 %");
		assertEquals(2500, Scratch.topPrize(basic));
		CasinoRng rng = fair(21);
		long paid = 0;
		int n = 1_000_000, creepers = 0, losers = 0;
		for (int i = 0; i < n; i++) {
			Scratch.Outcome o = Scratch.draw(rng, gold, 0.01);
			paid += o.prize();
			if (o.prize() == 0) {
				losers++;
				creepers += o.creeper() ? 1 : 0;
			}
			if (i % 50 == 0) {
				long[] face = Scratch.buildFace(rng, gold, o);
				assertTrue(Scratch.faceMatches(face, o), "face rules for " + o);
				Map<Long, Integer> counts = Scratch.counts(face);
				counts.forEach((sym, c) -> assertTrue(c <= 3 && (c < 3 || sym == o.prize() || (o.creeper() && sym == Scratch.CREEPER)), "at most twice"));
			}
		}
		// gold σ per card ≈ 400 chips → SE ≈ 0.4 at 1M cards; RTP 0.85 → mean 85
		assertEquals(85.0, (double) paid / n, 2.5);
		assertEquals(0.01, (double) creepers / losers, 0.001, "1 % of losing cards are creeper cards");
	}

	@Test
	void plinko() {
		assertEquals(List.of(1, 12, 66, 220, 495, 792, 924, 792, 495, 220, 66, 12, 1), java.util.Arrays.stream(Plinko.BIN_WEIGHTS).boxed().toList());
		// binomial(12, k)
		for (int k = 0; k <= 12; k++) {
			assertEquals(binom(12, k), Plinko.BIN_WEIGHTS[k]);
		}
		double[] low = {10, 3, 1.6, 1.4, 1.0, 1.0, 0.5, 1.0, 1.0, 1.4, 1.6, 3, 10};
		double[] med = {33, 11, 4, 2, 1.0, 0.6, 0.3, 0.6, 1.0, 2, 4, 11, 33};
		double[] high = {170, 24, 8.1, 2, 0.6, 0.2, 0.2, 0.2, 0.6, 2, 8.1, 24, 170};
		assertEquals(0.9656, rtp(low), 5e-5);
		assertEquals(0.9657, rtp(med), 5e-5);
		assertEquals(0.9670, rtp(high), 5e-5);
		assertEquals(rtp(low), Plinko.rtp(Plinko.Risk.LOW.defaults()), 1e-12);
		assertEquals(rtp(med), Plinko.rtp(Plinko.Risk.MEDIUM.defaults()), 1e-12);
		assertEquals(rtp(high), Plinko.rtp(Plinko.Risk.HIGH.defaults()), 1e-12);
		assertEquals(21, Plinko.totalReturn(15, Plinko.fromPath(path(3), Plinko.Risk.LOW.defaults())), "floor(15 × 1.4) = 21 despite float noise");
		assertEquals(3, Plinko.fromPath(path(3), Plinko.Risk.LOW.defaults()).bin());
	}

	private static boolean[] path(int rights) {
		boolean[] p = new boolean[12];
		for (int i = 0; i < rights; i++) {
			p[i] = true;
		}
		return p;
	}

	private static double rtp(double[] m) {
		double s = 0;
		for (int k = 0; k <= 12; k++) {
			s += m[k] * binom(12, k);
		}
		return s / 4096;
	}

	private static int binom(int n, int k) {
		long r = 1;
		for (int i = 1; i <= k; i++) {
			r = r * (n - k + i) / i;
		}
		return (int) r;
	}

	@Test
	void diceDuelVsHouse() {
		int[] tie7 = {7};
		// exact enumeration of 36 × 36 outcomes
		long ret = 0;
		for (int a = 1; a <= 6; a++) for (int b = 1; b <= 6; b++) for (int c = 1; c <= 6; c++) for (int d = 1; d <= 6; d++) {
			ret += DiceDuel.houseReturn(1, DiceDuel.judge(new DiceDuel.Roll(a, b), new DiceDuel.Roll(c, d), tie7));
		}
		assertEquals(1 - 1 / 36.0, ret / 1296.0, 1e-12, "HE = (6/36)² = 2.78 %");
		assertEquals(1 - 1 / 36.0, DiceDuel.houseRtp(tie7), 1e-12);
		assertEquals(DiceDuel.HouseOutcome.PUSH, DiceDuel.judge(new DiceDuel.Roll(3, 3), new DiceDuel.Roll(2, 4), tie7));
		assertEquals(DiceDuel.HouseOutcome.HOUSE_TIE, DiceDuel.judge(new DiceDuel.Roll(3, 4), new DiceDuel.Roll(2, 5), tie7));
		DiceDuel.PvpPayout p = DiceDuel.pvpPayout(50, 0);
		assertEquals(100, p.winnerGets(), "PvP rake 0 % by default");
	}
}

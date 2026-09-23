package dev.nezo.burmaldaholic.games.extras.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsContext;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.rng.StreakRules;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * GAME_DESIGN.md §17: every RNG game gets a 10⁷-round Monte-Carlo test asserting |RTP − expected| &lt; 0.3 %
 * (Plinko High: exact enumeration). Draws go through {@link OddsService#play} exactly like the server does,
 * with a streak of 0 (the §14 re-draw is tested separately below).
 */
class ExtrasRtpTest {
	private static final int ROUNDS = 10_000_000;
	private static final double TOLERANCE = 0.003;
	private static final UUID PLAYER = UUID.randomUUID();

	private static OddsService service(long seed, int streak) {
		OddsService s = new OddsService(new SplittableRandom(seed));
		s.setStreakSource(id -> streak, () -> StreakRules.Settings.DEFAULTS);
		return s;
	}

	private static double coinRtp(OddsService s, int rounds) {
		OddsContext ctx = new OddsContext(PLAYER, "extras", 100);
		CasinoRng rng = s.rng(ctx);
		long bet = 100;
		long returned = 0;
		for (int i = 0; i < rounds; i++) {
			CoinFlip.Result r = s.play(ctx, CoinFlip.rtp(0.96), () -> CoinFlip.flip(rng, CoinFlip.Side.HEADS), x -> !x.win());
			returned += CoinFlip.totalReturn(bet, r.win(), 0.96);
		}
		return returned / (double) (bet * rounds);
	}

	@Test
	void coinFlipRtp() {
		assertEquals(0.98, coinRtp(service(1, 0), ROUNDS), TOLERANCE);
	}

	private static double wheelRtp(OddsService s, int rounds) {
		Wheel w = Wheel.standard();
		OddsContext ctx = new OddsContext(PLAYER, "extras", 100);
		CasinoRng rng = s.rng(ctx);
		long bet = 100;
		long returned = 0;
		for (int i = 0; i < rounds; i++) {
			Wheel.Spin spin = s.play(ctx, w.rtp(), () -> w.spin(rng), x -> Wheel.totalReturn(bet, x) < bet);
			returned += Wheel.totalReturn(bet, spin);
		}
		return returned / (double) (bet * rounds);
	}

	@Test
	void wheelRtpMonteCarlo() {
		assertEquals(51.5 / 54, wheelRtp(service(2, 0), ROUNDS), TOLERANCE);
	}

	@Test
	void wheelSpinIsUniform() {
		Wheel w = Wheel.standard();
		CasinoRng rng = service(12, 0).fair();
		int[] hits = new int[w.size()];
		int n = 2_700_000;
		for (int i = 0; i < n; i++) {
			hits[w.spin(rng).index()]++;
		}
		for (int h : hits) {
			assertEquals(n / 54.0, h, n / 54.0 * 0.03);
		}
	}

	private static double scratchRtp(double[][] cfg, long price, long seed) {
		OddsService s = service(seed, 0);
		OddsContext ctx = new OddsContext(PLAYER, "extras", price);
		CasinoRng rng = s.rng(ctx);
		List<Scratch.Prize> table = Scratch.table(cfg);
		double rtp = Scratch.rtp(table, price);
		long returned = 0;
		for (int i = 0; i < ROUNDS; i++) {
			Scratch.Outcome o = s.play(ctx, rtp, () -> Scratch.draw(rng, table, 0.01), x -> x.prize() < price);
			returned += o.prize();
		}
		return returned / (double) (price * (long) ROUNDS);
	}

	@Test
	void scratchBasicRtp() {
		double[][] basic = {{10, 0.22}, {20, 0.10}, {50, 0.03}, {100, 0.01}, {500, 0.002}, {2500, 0.0001}};
		assertEquals(0.795, scratchRtp(basic, 10, 3), TOLERANCE);
	}

	@Test
	void scratchGoldRtp() {
		double[][] gold = {{100, 0.20}, {200, 0.10}, {500, 0.05}, {1000, 0.01}, {5000, 0.001}, {25000, 0.0002}};
		assertEquals(0.85, scratchRtp(gold, 100, 4), TOLERANCE);
	}

	@Test
	void plinkoExactRtpByEnumeration() {
		double[] expected = {0.9656, 0.9657, 0.9670};
		for (Plinko.Risk risk : Plinko.Risk.values()) {
			double[] table = risk.defaults();
			assertEquals(expected[risk.ordinal()], Plinko.rtp(table), 0.00005, risk.id());
			// every one of the 4096 equally likely paths through the real drop logic, with flooring at bet 100
			long bet = 100;
			long returned = 0;
			for (int bits = 0; bits < Plinko.PATHS; bits++) {
				returned += Plinko.totalReturn(bet, Plinko.fromPath(Plinko.decode(bits), table));
			}
			assertEquals(expected[risk.ordinal()], returned / (double) (bet * Plinko.PATHS), 0.0001, risk.id());
		}
	}

	@Test
	void plinkoLowAndMediumMonteCarlo() {
		for (Plinko.Risk risk : List.of(Plinko.Risk.LOW, Plinko.Risk.MEDIUM)) {
			OddsService s = service(5 + risk.ordinal(), 0);
			OddsContext ctx = new OddsContext(PLAYER, "extras", 100);
			CasinoRng rng = s.rng(ctx);
			double[] table = risk.defaults();
			long bet = 100;
			long returned = 0;
			for (int i = 0; i < ROUNDS; i++) {
				Plinko.Drop d = s.play(ctx, Plinko.rtp(table), () -> Plinko.drop(rng, table), x -> Plinko.totalReturn(bet, x) < bet);
				returned += Plinko.totalReturn(bet, d);
			}
			assertEquals(Plinko.rtp(table), returned / (double) (bet * (long) ROUNDS), TOLERANCE, risk.id());
		}
	}

	@Test
	void diceHouseRtpMonteCarlo() {
		CasinoRng rng = service(9, 0).fair();
		int[] tie = {7};
		long returned = 0;
		for (int i = 0; i < ROUNDS; i++) {
			returned += DiceDuel.houseReturn(1, DiceDuel.duelHouse(rng, tie).outcome());
		}
		assertEquals(DiceDuel.houseRtp(tie), returned / (double) ROUNDS, TOLERANCE);
		assertEquals(0.97222, DiceDuel.houseRtp(tie), 0.00001);
	}

	/** §14: the streak re-draw may raise RTP but never above 1 − minHouseEdge (99 %). */
	@Test
	void luckyStreakIsCappedAtOnePercentHouseEdge() {
		int rounds = 4_000_000;
		double coinHot = coinRtp(service(31, 10), rounds);
		assertTrue(coinHot > 0.982, "lucky streak should help: " + coinHot);
		assertTrue(coinHot <= 0.99 + TOLERANCE, "capped: " + coinHot);
		double wheelHot = wheelRtp(service(32, 10), rounds);
		// r = min(0.05, 0.99/0.9537 − 1 = 0.038) → RTP' ≈ RTP + r × P(lose) × RTP
		assertTrue(wheelHot > 51.5 / 54 + 0.01, "wheel lucky: " + wheelHot);
		assertTrue(wheelHot <= 0.99 + TOLERANCE, "wheel capped: " + wheelHot);
		double coinCold = coinRtp(service(33, -10), rounds);
		assertTrue(coinCold <= 0.99 + TOLERANCE && coinCold > 0.982, "pity streak: " + coinCold);
	}
}

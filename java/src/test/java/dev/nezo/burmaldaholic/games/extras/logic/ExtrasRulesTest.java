package dev.nezo.burmaldaholic.games.extras.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Payout tables, edge cases and invariants of the extras games (GAME_DESIGN.md §11). */
class ExtrasRulesTest {
	private static CasinoRng rng(long seed) {
		return new OddsService(new SplittableRandom(seed)).fair();
	}

	// ---- payouts ------------------------------------------------------------------------------

	@Test
	void floorPayIsRobustToFloatingPoint() {
		assertEquals(21, Payouts.floorPay(15, 1.4));
		assertEquals(96, Payouts.floorPay(100, 0.96));
		assertEquals(0, Payouts.floorPay(1, 0.96));
		assertEquals(0, Payouts.floorPay(0, 5));
		assertEquals(0, Payouts.floorPay(10, 0));
		assertEquals(81, Payouts.floorPay(10, 8.1));
		assertEquals(3, Payouts.floorPay(7, 0.5));
		assertEquals("10", Payouts.formatMultiplier(10));
		assertEquals("0.5", Payouts.formatMultiplier(0.5));
		assertEquals("8.1", Payouts.formatMultiplier(8.1));
		assertEquals("1.4", Payouts.formatMultiplier(1.4));
	}

	// ---- coin flip ----------------------------------------------------------------------------

	@Test
	void coinFlipPaysPoint96ToOne() {
		assertEquals(196, CoinFlip.totalReturn(100, true, 0.96));
		assertEquals(96, CoinFlip.winnings(100, true, 0.96));
		assertEquals(0, CoinFlip.totalReturn(100, false, 0.96));
		assertEquals(1, CoinFlip.totalReturn(1, true, 0.96)); // floor(0.96) = 0 winnings
		assertEquals(2000, CoinFlip.soulReturn(1000, true));
		assertEquals(0, CoinFlip.soulReturn(1000, false));
		assertEquals(0.98, CoinFlip.rtp(0.96), 1e-12);
		assertEquals(CoinFlip.Side.TAILS, CoinFlip.Side.HEADS.other());
		assertEquals(CoinFlip.Side.HEADS, CoinFlip.Side.parse("heads"));
		assertNull(CoinFlip.Side.parse("edge"));
	}

	@Test
	void coinLandedSideFollowsTheWin() {
		CasinoRng r = rng(5);
		for (int i = 0; i < 1000; i++) {
			CoinFlip.Result res = CoinFlip.flip(r, CoinFlip.Side.TAILS);
			assertEquals(res.win(), res.landed() == CoinFlip.Side.TAILS);
		}
	}

	// ---- wheel --------------------------------------------------------------------------------

	@Test
	void wheelAppendixBCounts() {
		Wheel w = Wheel.standard();
		assertEquals(54, w.size());
		Map<String, Integer> c = w.counts();
		assertEquals(25, c.get("B"));
		assertEquals(1, c.get("C"));
		assertEquals(5, c.get("H"));
		assertEquals(11, c.get("M"));
		assertEquals(7, c.get("D"));
		assertEquals(3, c.get("T"));
		assertEquals(1, c.get("E"));
		assertEquals(1, c.get("X"));
		assertEquals("X", w.segments().get(0));
		assertEquals("B", w.segments().get(53));
		assertEquals(51.5 / 54, w.rtp(), 1e-12);
		assertEquals(10, w.maxMultiplier());
	}

	@Test
	void wheelLegendAndConfigFallbacks() {
		List<Wheel.LegendRow> legend = Wheel.standard().legend();
		assertEquals("B", legend.get(0).code());
		assertEquals("C", legend.get(1).code());
		assertEquals("X", legend.get(legend.size() - 1).code());
		// fewer than 2 valid segments -> appendix B; unknown codes dropped; negative multipliers -> 0
		Wheel broken = new Wheel(List.of("Z", "X"), Map.of("X", -3.0));
		assertEquals(54, broken.size());
		assertEquals(0, broken.multiplier("X"));
		Wheel tiny = new Wheel(List.of("M", "D", "Q"), Map.of());
		assertEquals(List.of("M", "D"), tiny.segments());
		assertEquals(1.5, tiny.rtp(), 1e-12);
	}

	@Test
	void wheelReturns() {
		Wheel w = Wheel.standard();
		assertEquals(1000, Wheel.totalReturn(100, w.at(0)));   // X
		assertEquals(0, Wheel.totalReturn(100, w.at(1)));      // B
		assertEquals(3, Wheel.totalReturn(7, w.at(8)));        // H: floor(3.5)
		assertTrue(w.at(48).creeper());
		assertEquals(0, Wheel.totalReturn(100, w.at(48)));
		assertEquals(w.at(0), w.at(54));
		assertEquals("gui.burmaldaholic.extras.wheel.segment.money_back", Wheel.segmentKey("M"));
	}

	// ---- plinko -------------------------------------------------------------------------------

	@Test
	void plinkoWeightsAreBinomial() {
		int sum = 0;
		for (int w : Plinko.BIN_WEIGHTS) {
			sum += w;
		}
		assertEquals(4096, sum);
		int[] counts = new int[Plinko.BINS];
		for (int bits = 0; bits < Plinko.PATHS; bits++) {
			counts[Plinko.fromPath(Plinko.decode(bits), Plinko.Risk.LOW.defaults()).bin()]++;
		}
		assertArrayEquals(Plinko.BIN_WEIGHTS, counts);
	}

	@Test
	void plinkoPathEncodingRoundTrips() {
		CasinoRng r = rng(11);
		for (int i = 0; i < 500; i++) {
			Plinko.Drop d = Plinko.drop(r, Plinko.Risk.HIGH.defaults());
			assertArrayEquals(d.path(), Plinko.decode(Plinko.encode(d.path())));
			assertEquals(Integer.bitCount(Plinko.encode(d.path())), d.bin());
		}
	}

	@Test
	void plinkoTableFallback() {
		assertArrayEquals(Plinko.Risk.MEDIUM.defaults(), Plinko.table(new double[] {1, 2}, Plinko.Risk.MEDIUM));
		double[] bad = Plinko.Risk.LOW.defaults();
		bad[3] = Double.NaN;
		assertArrayEquals(Plinko.Risk.LOW.defaults(), Plinko.table(bad, Plinko.Risk.LOW));
		assertEquals(170, Plinko.maxMultiplier(Plinko.Risk.HIGH.defaults()));
		assertEquals(Plinko.Risk.HIGH, Plinko.Risk.parse("high"));
	}

	// ---- scratch ------------------------------------------------------------------------------

	private static final double[][] BASIC = {{10, 0.22}, {20, 0.10}, {50, 0.03}, {100, 0.01}, {500, 0.002}, {2500, 0.0001}};
	private static final double[][] GOLD = {{100, 0.20}, {200, 0.10}, {500, 0.05}, {1000, 0.01}, {5000, 0.001}, {25000, 0.0002}};

	@Test
	void scratchTablesMatchSpecRtp() {
		assertEquals(0.795, Scratch.rtp(Scratch.table(BASIC), 10), 1e-9);
		assertEquals(0.85, Scratch.rtp(Scratch.table(GOLD), 100), 1e-9);
		assertEquals(2500, Scratch.topPrize(Scratch.table(BASIC)));
		assertEquals(25000, Scratch.topPrize(Scratch.table(GOLD)));
	}

	@Test
	void scratchTableCleanup() {
		List<Scratch.Prize> t = Scratch.table(new double[][] {{20, 0.6}, {10.7, 0.3}, {10, 0.3}, {0, 0.5}, {5, -1}});
		assertEquals(2, t.size());
		assertEquals(10, t.get(0).amount());
		assertEquals(0.5, t.get(0).probability(), 1e-12); // (0.3+0.3)/1.2
		assertEquals(0.5, t.get(1).probability(), 1e-12);
		assertTrue(Scratch.table(null).isEmpty());
	}

	@Test
	void scratchFacesFollowTheRules() {
		CasinoRng r = rng(3);
		for (double[][] cfg : List.of(BASIC, GOLD, new double[][] {{50, 0.5}})) {
			List<Scratch.Prize> table = Scratch.table(cfg);
			for (int i = 0; i < 3000; i++) {
				Scratch.Outcome o = Scratch.draw(r, table, 0.3);
				long[] face = Scratch.buildFace(r, table, o);
				assertTrue(Scratch.faceMatches(face, o), () -> java.util.Arrays.toString(face) + " " + o);
			}
			for (Scratch.Prize p : table) {
				Scratch.Outcome win = new Scratch.Outcome(p.amount(), false);
				assertTrue(Scratch.faceMatches(Scratch.buildFace(r, table, win), win));
			}
			Scratch.Outcome creeper = new Scratch.Outcome(0, true);
			assertTrue(Scratch.faceMatches(Scratch.buildFace(r, table, creeper), creeper));
			assertTrue(Scratch.faceMatches(Scratch.buildFace(r, table, Scratch.Outcome.LOSE), Scratch.Outcome.LOSE));
		}
		assertFalse(Scratch.faceMatches(new long[] {10, 10, 10, 20, 20, 50, 50, 0, 0}, Scratch.Outcome.LOSE));
		assertFalse(Scratch.faceMatches(new long[] {10, 10, 10, 20, 20, 20, 50, 0, 0}, new Scratch.Outcome(10, false)));
	}

	@Test
	void scratchCreeperShareOfLosingCards() {
		CasinoRng r = rng(8);
		List<Scratch.Prize> table = Scratch.table(BASIC);
		int losing = 0;
		int creepers = 0;
		for (int i = 0; i < 400_000; i++) {
			Scratch.Outcome o = Scratch.draw(r, table, 0.01);
			if (o.prize() == 0) {
				losing++;
				if (o.creeper()) {
					creepers++;
				}
			} else {
				assertFalse(o.creeper());
			}
		}
		assertEquals(0.01, creepers / (double) losing, 0.001);
	}

	@Test
	void scratchMaskHelpers() {
		assertEquals(0, Scratch.revealedCount(0));
		assertEquals(3, Scratch.revealedCount(0b101001));
		assertTrue(Scratch.fullyRevealed(0x1FF));
		assertFalse(Scratch.fullyRevealed(0xFF));
		assertEquals(Scratch.Kind.GOLD, Scratch.Kind.parse("gold"));
		assertEquals(1, Scratch.Kind.GOLD.minTier());
	}

	// ---- dice ---------------------------------------------------------------------------------

	@Test
	void diceHouseJudging() {
		int[] seven = {7};
		assertEquals(DiceDuel.HouseOutcome.WIN, DiceDuel.judge(new DiceDuel.Roll(6, 6), new DiceDuel.Roll(1, 1), seven));
		assertEquals(DiceDuel.HouseOutcome.LOSE, DiceDuel.judge(new DiceDuel.Roll(1, 2), new DiceDuel.Roll(2, 2), seven));
		assertEquals(DiceDuel.HouseOutcome.PUSH, DiceDuel.judge(new DiceDuel.Roll(4, 4), new DiceDuel.Roll(2, 6), seven));
		assertEquals(DiceDuel.HouseOutcome.HOUSE_TIE, DiceDuel.judge(new DiceDuel.Roll(3, 4), new DiceDuel.Roll(1, 6), seven));
		assertEquals(DiceDuel.HouseOutcome.PUSH, DiceDuel.judge(new DiceDuel.Roll(3, 4), new DiceDuel.Roll(1, 6), new int[0]));
		assertEquals(200, DiceDuel.houseReturn(100, DiceDuel.HouseOutcome.WIN));
		assertEquals(100, DiceDuel.houseReturn(100, DiceDuel.HouseOutcome.PUSH));
		assertEquals(0, DiceDuel.houseReturn(100, DiceDuel.HouseOutcome.HOUSE_TIE));
		assertEquals(0, DiceDuel.houseReturn(100, DiceDuel.HouseOutcome.LOSE));
	}

	@Test
	void diceHouseEdgeIsSixThirtySixthsSquared() {
		assertEquals(1 - Math.pow(6 / 36.0, 2), DiceDuel.houseRtp(new int[] {7}), 1e-12);
		assertEquals(1.0, DiceDuel.houseRtp(new int[0]), 1e-12);
	}

	@Test
	void pvpDuelAndPayout() {
		CasinoRng r = rng(21);
		int refunds = 0;
		for (int i = 0; i < 20_000; i++) {
			DiceDuel.PvpDuel d = DiceDuel.duelPvp(r, DiceDuel.PVP_MAX_ROLLS);
			assertTrue(d.rounds().size() >= 1 && d.rounds().size() <= 3);
			DiceDuel.PvpRound last = d.rounds().get(d.rounds().size() - 1);
			switch (d.result()) {
				case A -> assertTrue(last.a().total() > last.b().total());
				case B -> assertTrue(last.a().total() < last.b().total());
				case REFUND -> {
					refunds++;
					assertEquals(3, d.rounds().size());
					d.rounds().forEach(rd -> assertEquals(rd.a().total(), rd.b().total()));
				}
			}
		}
		// P(tie) = 146/1296 ≈ 0.1127 → P(3 ties) ≈ 0.00143
		assertEquals(0.00143, refunds / 20_000.0, 0.001);
		DiceDuel.PvpPayout p = DiceDuel.pvpPayout(100, 5);
		assertEquals(200, p.pot());
		assertEquals(10, p.rake());
		assertEquals(190, p.winnerGets());
		assertEquals(200, DiceDuel.pvpPayout(100, 0).winnerGets());
		assertEquals(0, DiceDuel.pvpPayout(7, 5).rake()); // floor(0.7)
	}

	// ---- challenges ---------------------------------------------------------------------------

	@Test
	void challengeBookLifecycle() {
		ChallengeBook book = new ChallengeBook();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		assertEquals(ChallengeBook.Error.SELF, book.create(a, a, 10, 0, 600).error());
		ChallengeBook.Created first = book.create(a, b, 10, 0, 600);
		assertTrue(first.ok());
		assertEquals(ChallengeBook.Error.ALREADY_PENDING, book.create(a, c, 10, 5, 600).error());
		assertTrue(book.create(c, b, 20, 5, 600).ok());
		assertEquals(2, book.incoming(b, 10).size());
		assertTrue(book.take(first.challenge().id(), 100).isPresent());
		assertTrue(book.take(first.challenge().id(), 100).isEmpty());
		assertTrue(book.create(a, c, 10, 100, 600).ok()); // previous one gone
		assertEquals(2, book.expire(700).size());
		assertEquals(0, book.size());
		ChallengeBook.Created late = book.create(a, b, 1, 0, 10);
		assertTrue(book.take(late.challenge().id(), 10).isEmpty()); // expired at 10
		book.create(a, b, 1, 0, 10);
		book.create(b, c, 1, 0, 10);
		assertEquals(2, book.dropPlayer(b).size());
		assertEquals(0, book.size());
	}
}

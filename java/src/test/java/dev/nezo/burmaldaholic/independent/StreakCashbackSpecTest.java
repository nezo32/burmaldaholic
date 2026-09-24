package dev.nezo.burmaldaholic.independent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.rng.OddsContext;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.rng.StreakRules;
import dev.nezo.burmaldaholic.core.wager.HouseEdges;
import dev.nezo.burmaldaholic.games.extras.logic.CoinFlip;
import dev.nezo.burmaldaholic.vip.logic.CashbackRules;
import dev.nezo.burmaldaholic.vip.logic.VipRules;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** GAME_DESIGN.md §14 (streak re-draw cap) and §12 (cashback never +EV), re-derived from the spec. */
class StreakCashbackSpecTest {
	private static final StreakRules.Settings S = StreakRules.Settings.DEFAULTS;

	/** §17 RTPs of the streak-adjustable games. */
	private static final double[] RNG_RTPS = {0.8976, 0.9371, 0.9604, 0.98, 51.5 / 54, 0.795, 0.85, 0.9656, 0.9657, 0.9670};

	@Test
	void streakUpdateAndCaps() {
		assertEquals(1, StreakRules.update(-4, 5, 10), "win after losses → +1");
		assertEquals(-1, StreakRules.update(3, -5, 10), "loss after wins → −1");
		assertEquals(10, StreakRules.update(10, 1, 10));
		assertEquals(-10, StreakRules.update(-10, -1, 10));
		assertEquals(4, StreakRules.update(4, 0, 10), "push unchanged");
		assertEquals(3, StreakRules.decay(5, 0, 24_000, 12_000).streak(), "two decay steps toward 0");
		assertEquals(0, StreakRules.decay(-1, 0, 100_000, 12_000).streak());
	}

	@Test
	void redrawProbabilitySpecExamples() {
		assertEquals(0.0102, StreakRules.redrawProbability(10, 0.98, S), 1e-4, "coin flip r_cap");
		assertEquals(0.05, StreakRules.redrawProbability(10, 0.8976, S), 1e-12, "copper: full 5 % (cap 0.103)");
		assertEquals(0.103, (1 - 0.01) / 0.8976 - 1, 5e-4);
		assertEquals(0.03, StreakRules.redrawProbability(-10, 0.795, S), 1e-12, "pity max 3 %");
		assertEquals(0.015, StreakRules.redrawProbability(3, 0.795, S), 1e-12);
		assertEquals(0, StreakRules.redrawProbability(0, 0.9, S));
	}

	/** RTP' ≤ RTP × (1 + r) ≤ 0.99 for every game and every streak value. */
	@Test
	void edgeNeverBelowOnePercent() {
		for (double rtp : RNG_RTPS) {
			for (int s = -10; s <= 10; s++) {
				double r = StreakRules.redrawProbability(s, rtp, S);
				assertTrue(rtp * (1 + r) <= 0.99 + 1e-12, "RTP " + rtp + " S " + s);
			}
		}
	}

	/** The re-draw really runs through OddsService.play: coin flip at S = +10 lands at 98 % + ½·r·98 %. */
	@Test
	void coinFlipAtMaxStreakMeasured() {
		OddsService odds = new OddsService(new SplittableRandom(77));
		odds.setStreakSource(id -> 10, () -> S);
		UUID p = new UUID(9, 9);
		OddsContext ctx = new OddsContext(p, "coin_flip", 100);
		long staked = 0, back = 0;
		int n = 3_000_000;
		for (int i = 0; i < n; i++) {
			var rng = odds.rng(ctx);
			CoinFlip.Result r = odds.play(ctx, CoinFlip.rtp(0.96), () -> CoinFlip.flip(rng, CoinFlip.Side.TAILS), x -> !x.win());
			staked += 100;
			back += CoinFlip.totalReturn(100, r.win(), 0.96);
		}
		double expected = 0.98 + 0.5 * StreakRules.redrawProbability(10, 0.98, S) * 0.98;
		assertEquals(expected, (double) back / staked, 0.0015);
		assertTrue((double) back / staked < 0.99, "still ≥ 1 % edge");
	}

	/** §17 table vs the constants the cashback uses. */
	@Test
	void houseEdgeConstantsMatchSpec() {
		assertEquals(0.0041, HouseEdges.of("blackjack"));
		assertEquals(0.0270, HouseEdges.of("roulette"), 1e-4);
		assertEquals(0.0136, HouseEdges.of("craps"), 1e-4, "lowest craps flat bet");
		assertEquals(0.02, HouseEdges.of("coin_flip"));
		assertEquals(0.0463, HouseEdges.of("wheel_of_fortune"), 1e-4);
		assertEquals(0.0278, HouseEdges.of("dice_duel"), 1e-4);
		assertEquals(0.0396, HouseEdges.of("slots"), 1e-4, "lowest slots edge (Netherite)");
		assertEquals(0, HouseEdges.of("poker"), "PvP: no cashback");
		assertEquals(0, HouseEdges.of("dice_duel_pvp"));
		assertEquals(1 - 0.9656, HouseEdges.PLINKO_LOW, 1e-4);
		assertEquals(1 - 0.9670, HouseEdges.PLINKO_HIGH, 1e-4);
		assertEquals(0.1024, HouseEdges.SLOTS_COPPER);
		assertEquals(0.0629, HouseEdges.SLOTS_GOLD);
		assertEquals(0.205, HouseEdges.SCRATCH_BASIC);
	}

	@Test
	void cashbackRatesAndEligibility() {
		double[] rates = {0.02, 0.03, 0.04, 0.05};
		assertEquals(0, VipRules.cashbackRate(VipRules.SILVER, rates));
		assertEquals(0.02, VipRules.cashbackRate(VipRules.GOLD, rates));
		assertEquals(0.05, VipRules.cashbackRate(VipRules.NETHERITE, rates));
		assertTrue(CashbackRules.eligible(true, false, false));
		assertTrue(!CashbackRules.eligible(false, false, false), "PvP never");
		assertTrue(!CashbackRules.eligible(true, true, false), "owned casinos never");
		assertTrue(!CashbackRules.eligible(true, false, true), "pawn stakes never");
		// 5 × 1 000-chip blackjack hands at Netherite: 5 000 × 0.41 % × 5 % = 1.025 → 1 chip, whatever the result
		CashbackRules.Roll r = null;
		CashbackRules.DayLedger l = null;
		for (int i = 0; i < 5; i++) {
			r = CashbackRules.record(l, 7, 1000, i % 2 == 0 ? 0 : 2000, HouseEdges.BLACKJACK, true);
			l = r.ledger();
		}
		assertEquals(1, CashbackRules.amount(l.theo(), 0.05));
		CashbackRules.Roll next = CashbackRules.record(l, 8, 10, 0, 0.02, true);
		assertEquals(7, next.closed().day(), "day rollover hands back the closed day");
	}

	/**
	 * Never +EV: for every tier and RNG game the cashback (rate × nominal HE) stays below the edge that
	 * remains after the worst-case streak re-draw (RTP × (1 + r_max)); for table games (no streak) below the HE.
	 */
	@Test
	void cashbackNeverMakesAGamePositive() {
		double[] rates = {0.02, 0.03, 0.04, 0.05};
		for (int tier = VipRules.BRONZE; tier <= VipRules.NETHERITE; tier++) {
			double rate = VipRules.cashbackRate(tier, rates);
			for (double rtp : RNG_RTPS) {
				double worst = rtp * (1 + StreakRules.redrawProbability(10, rtp, S));
				double he = 1 - rtp;
				assertTrue(1 - worst - rate * he > 0, "tier " + tier + " rtp " + rtp);
			}
			for (double he : new double[] {0.0041, 0.027, 0.0141, 0.0136, 0.0278}) {
				assertTrue(he * (1 - rate) > 0);
			}
			// the extreme of the config range (rate 0.5) is still below every nominal edge
			assertTrue(VipRules.cashbackRate(tier, new double[] {9, 9, 9, 9}) <= 0.5);
		}
	}
}

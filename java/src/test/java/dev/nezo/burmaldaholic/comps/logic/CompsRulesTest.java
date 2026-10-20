package dev.nezo.burmaldaholic.comps.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** EC-SPEC §4.1 / §4.2: milli-point rounding, per-tier rates, the comp + cashback ≤ 0.35 cap, redemption gates. */
class CompsRulesTest {
	private static final double[] RATES = {0.10, 0.12, 0.15, 0.18, 0.22, 0.25};

	@Test
	void milliPointsRoundToNearest() {
		// 1000 chips at roulette 2.7 % edge, Bronze 10 %: theo 27 -> 2.7 points = 2700 milli
		assertEquals(2700, CompsRules.pointsMilli(1000, 0.027, 0.10));
		// 1 chip at 0.5 % edge, 10 %: 0.0005 points = 0.5 milli -> rounds half up to 1
		assertEquals(1, CompsRules.pointsMilli(1, 0.005, 0.10));
		assertEquals(0, CompsRules.pointsMilli(1, 0.004, 0.10), "0.4 milli rounds to 0");
		assertEquals(0, CompsRules.pointsMilli(0, 0.05, 0.1));
		assertEquals(0, CompsRules.pointsMilli(100, 0, 0.1), "PvP / no edge earns nothing");
		assertEquals(0, CompsRules.pointsMilli(100, 0.05, 0));
		assertEquals(0, CompsRules.pointsMilli(-5, 0.05, 0.1));
		assertEquals(2, CompsRules.points(2999));
	}

	@Test
	void perTierRatesAndTheCap() {
		for (int t = 0; t <= 5; t++) {
			assertEquals(RATES[t], CompsRules.rate(t, RATES, 0), 1e-12);
		}
		// default cashback (Gold 2 % .. Netherite 5 %) leaves the default comp rates untouched (all <= 0.35 combined)
		double[] cb = {0, 0, 0.02, 0.03, 0.04, 0.05};
		for (int t = 0; t <= 5; t++) {
			double r = CompsRules.rate(t, RATES, cb[t]);
			assertEquals(RATES[t], r, 1e-12);
			assertTrue(r + cb[t] <= 0.35 + 1e-12, "tier " + t + " keeps >= 65 % of the edge");
		}
		// a misconfigured 30 % comp rate with 10 % cashback is clamped to 25 %
		assertEquals(0.25, CompsRules.rate(5, new double[] {0.3, 0.3, 0.3, 0.3, 0.3, 0.3}, 0.10), 1e-12);
		assertEquals(0, CompsRules.rate(2, new double[] {0.1, 0.1, 0.1}, 0.5), 1e-12, "cashback alone over the cap: no comps");
		assertEquals(0.10, CompsRules.rate(9, new double[] {0.1}, 0), 1e-12, "short table: last entry");
	}

	@Test
	void balanceIsCappedAtTenMillion() {
		long nearCap = CompsRules.CAP_MILLI - 5;
		assertEquals(CompsRules.CAP_MILLI, CompsRules.addCapped(nearCap, 10));
		assertEquals(CompsRules.CAP_MILLI, CompsRules.addCapped(CompsRules.CAP_MILLI, Long.MAX_VALUE));
		CompsRules.State s = CompsRules.State.empty(3).earn(CompsRules.CAP_MILLI).earn(1000);
		assertEquals(CompsRules.CAP_MILLI, s.pointsMilli());
	}

	@Test
	void stateRollsDailyFieldsOnly() {
		CompsRules.State s = CompsRules.State.empty(5).earn(5000).active(400).spend("snack", 1).claimed(5);
		CompsRules.State next = s.roll(6);
		assertEquals(4000, next.pointsMilli(), "points carry over");
		assertEquals(0, next.earnedTodayMilli());
		assertEquals(0, next.activeTicksToday());
		assertEquals(0, next.redeemed("snack"));
		assertEquals(5, next.dailyClaimDay());
		assertSame(s, s.roll(5), "same day = same state");
	}

	@Test
	void redemptionGates() {
		CompsRules.Reward feast = CompsRules.REWARDS.get("feast");
		CompsRules.State rich = CompsRules.State.empty(1).earn(1_000_000);
		assertEquals("tier", CompsRules.lock(feast, CompsRules.SILVER, rich, CompsRules.Dim.ANY));
		assertNull(CompsRules.lock(feast, CompsRules.GOLD, rich, CompsRules.Dim.ANY));
		CompsRules.Reward t10 = CompsRules.REWARDS.get("token_10");
		CompsRules.State s = rich;
		for (int i = 0; i < 5; i++) {
			assertNull(CompsRules.lock(t10, 0, s, CompsRules.Dim.ANY));
			s = s.spend("token_10", CompsRules.cost(t10, 0, s));
		}
		assertEquals("limit", CompsRules.lock(t10, 0, s, CompsRules.Dim.ANY), "L5");
		assertEquals(1_000_000 - 5 * 15 * 1000, s.pointsMilli());
		assertEquals("points", CompsRules.lock(CompsRules.REWARDS.get("snack"), 0, CompsRules.State.empty(1).earn(24_999), CompsRules.Dim.ANY));
		assertEquals("dimension", CompsRules.lock(CompsRules.REWARDS.get("horse"), 0, rich, CompsRules.Dim.NETHER));
		assertEquals("dimension", CompsRules.lock(CompsRules.REWARDS.get("strider"), 0, rich, CompsRules.Dim.OVERWORLD));
		assertNull(CompsRules.lock(CompsRules.REWARDS.get("strider"), 0, rich, CompsRules.Dim.NETHER));
	}

	@Test
	void suiteNightPrices() {
		CompsRules.Reward suite = CompsRules.REWARDS.get("suite");
		CompsRules.State s = CompsRules.State.empty(1);
		assertEquals(50, CompsRules.cost(suite, CompsRules.SILVER, s));
		assertEquals(30, CompsRules.cost(suite, CompsRules.GOLD, s));
		assertEquals(0, CompsRules.cost(suite, CompsRules.DIAMOND, s), "Diamond+: one free a day");
		assertEquals(30, CompsRules.cost(suite, CompsRules.DIAMOND, s.spend("suite", 0)));
		assertEquals("limit", CompsRules.lock(suite, CompsRules.DIAMOND, s.earn(100_000).spend("suite", 0), CompsRules.Dim.ANY));
	}

	@Test
	void catalogMatchesTheSpec() {
		assertEquals(10, CompsRules.REWARDS.size());
		assertEquals(325, CompsRules.REWARDS.get("token_250").cost());
		assertEquals(CompsRules.PLATINUM, CompsRules.REWARDS.get("token_250").minTier());
		assertEquals(2, CompsRules.REWARDS.get("token_250").limit());
		assertEquals(250, CompsRules.REWARDS.get("token_250").tokenValue());
		for (String id : new String[] {"token_10", "token_50", "token_250"}) {
			CompsRules.Reward r = CompsRules.REWARDS.get(id);
			assertTrue(r.cost() >= r.tokenValue() * 1.3, id + ": >= 1.3 points per chip of free bet");
		}
	}
}

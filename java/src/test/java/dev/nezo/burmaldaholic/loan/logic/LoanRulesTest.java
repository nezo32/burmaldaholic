package dev.nezo.burmaldaholic.loan.logic;

import static dev.nezo.burmaldaholic.loan.logic.LoanRules.MCD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.loan.logic.LoanRecord.Status;
import dev.nezo.burmaldaholic.loan.logic.LoanRules.AdvanceConfig;
import dev.nezo.burmaldaholic.loan.logic.LoanRules.AdvanceEvents;
import dev.nezo.burmaldaholic.loan.logic.LoanRules.Band;
import dev.nezo.burmaldaholic.loan.logic.LoanRules.Product;
import dev.nezo.burmaldaholic.loan.logic.LoanRules.RateConfig;
import dev.nezo.burmaldaholic.loan.logic.LoanRules.TakeError;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoanRulesTest {
	private static final int[][] DEFAULT_PRODUCTS = {{100, 3, 0}, {500, 3, 0}, {2000, 5, 1}, {10000, 7, 2}, {50000, 7, 4}};
	private static final AdvanceConfig NORMAL = new AdvanceConfig(0.10, 2.0, new int[] {24000, 2400});

	private static RateConfig rates(double base) {
		return new RateConfig(base, 0.02, 5, 0.10, 0.02);
	}

	private static LoanRecord taken(long principal, int days, double rate, long now) {
		LoanRecord r = new LoanRecord();
		LoanRules.take(r, new Product(1, "rent", principal, days, 0), rate, now);
		return r;
	}

	@Test
	void difficultyBands() {
		assertEquals(Band.EASY, LoanRules.band(0, false)); // Peaceful
		assertEquals(Band.EASY, LoanRules.band(1, false));
		assertEquals(Band.NORMAL, LoanRules.band(2, false));
		assertEquals(Band.HARD, LoanRules.band(3, false));
		assertEquals(Band.HARD, LoanRules.band(2, true)); // Hardcore always Hard
	}

	@Test
	void productsFromConfig() {
		List<Product> p = LoanRules.products(DEFAULT_PRODUCTS);
		assertEquals(5, p.size());
		assertEquals("pocket", p.get(0).id());
		assertEquals("life_changing", p.get(4).id());
		assertEquals(50000, p.get(4).principal());
		assertEquals(4, p.get(4).minTier());
		assertEquals(2, LoanRules.lockedCount(p, 1)); // Silver: Serious + Life-changing locked
		assertEquals(1, LoanRules.lockedCount(p, 2));
		assertEquals(0, LoanRules.lockedCount(p, 5));
		// extra rows reuse the last name, broken rows are skipped
		List<Product> extra = LoanRules.products(new int[][] {{1, 1, 0}, {2}, {3, 1, 0}, {4, 1, 0}, {5, 1, 0}, {6, 1, 0}, {7, 1, 0}});
		assertEquals(6, extra.size());
		assertEquals("life_changing", extra.get(5).id());
	}

	@Test
	void interestRateFormula() {
		assertEquals(0.20, LoanRules.rate(rates(0.20), 0, 0), 1e-9);
		assertEquals(0.16, LoanRules.rate(rates(0.20), 2, 0), 1e-9);
		assertEquals(0.10, LoanRules.rate(rates(0.20), 9, 0), 1e-9); // capped at 5 steps: 0.20-0.10 = 0.10
		assertEquals(0.13, LoanRules.rate(rates(0.25), 5, 3), 1e-9); // 0.25-0.10-0.02 Platinum
		assertEquals(0.10, LoanRules.rate(rates(0.15), 5, 5), 1e-9); // floored at minRate
		assertEquals(0.15, LoanRules.rate(rates(0.15), 0, 2), 1e-9); // Gold has no discount
	}

	@Test
	void dueIsCeiledWithoutFloatNoise() {
		assertEquals(600, LoanRules.due(500, 0.20));
		assertEquals(115, LoanRules.due(100, 0.15));
		assertEquals(2500, LoanRules.due(2000, 0.25));
		assertEquals(12, LoanRules.due(10, 0.11)); // 11.1 -> 12
		assertEquals("20", LoanRules.percent(0.20));
		assertEquals("17.5", LoanRules.percent(0.175));
	}

	@Test
	void takeRules() {
		Product rent = new Product(1, "rent", 500, 3, 0);
		Product serious = new Product(3, "serious", 10000, 7, 2);
		LoanRecord r = new LoanRecord();
		assertNull(LoanRules.takeError(r, rent, 0, 0));
		assertEquals(TakeError.VIP, LoanRules.takeError(r, serious, 1, 0));
		LoanRules.take(r, rent, 0.2, 1000);
		assertEquals(Status.ACTIVE, r.status);
		assertEquals(600, r.owed);
		assertEquals(1000 + 3 * MCD, r.deadlineTick);
		assertEquals(TakeError.ONE_AT_A_TIME, LoanRules.takeError(r, rent, 0, 1000));
		r.status = Status.DEFAULT;
		assertEquals(TakeError.IN_DEFAULT, LoanRules.takeError(r, rent, 0, 1000));
		LoanRecord cd = new LoanRecord();
		cd.cooldownUntil = 5000;
		assertEquals(TakeError.COOLDOWN, LoanRules.takeError(cd, rent, 0, 4999));
		assertNull(LoanRules.takeError(cd, rent, 0, 5000));
	}

	@Test
	void payingOnTimeRaisesGoodStanding() {
		LoanRecord r = taken(500, 3, 0.2, 0);
		LoanRules.PayResult p = LoanRules.pay(r, 100, 10, 5);
		assertEquals(100, p.paid());
		assertFalse(p.closed());
		assertEquals(500, r.owed);
		p = LoanRules.pay(r, 9999, 20, 5); // overpay is clamped
		assertEquals(500, p.paid());
		assertTrue(p.closed() && p.onTime());
		assertEquals(Status.NONE, r.status);
		assertEquals(1, r.goodStanding);
		assertEquals(0, LoanRules.pay(r, 10, 30, 5).paid()); // nothing to pay
	}

	@Test
	void payingFromDefaultResetsStandingAndStartsCooldown() {
		LoanRecord r = taken(500, 3, 0.2, 0);
		r.goodStanding = 4;
		LoanRules.advance(r, 3 * MCD, NORMAL);
		assertEquals(Status.DEFAULT, r.status);
		LoanRules.PayResult p = LoanRules.pay(r, r.owed, 3 * MCD + 50, 5);
		assertTrue(p.fromDefault());
		assertEquals(0, r.goodStanding);
		assertEquals(3 * MCD + 50 + 5 * MCD, r.cooldownUntil);
	}

	@Test
	void warningsOnlyMostUrgentAndNotForShortTerms() {
		LoanRecord r = taken(500, 3, 0.2, 0);
		long deadline = r.deadlineTick;
		assertEquals(-1, LoanRules.advance(r, deadline - 30000, NORMAL).warning());
		assertEquals(24000, LoanRules.advance(r, deadline - 24000, NORMAL).warning());
		assertEquals(-1, LoanRules.advance(r, deadline - 20000, NORMAL).warning()); // already sent
		assertEquals(2400, LoanRules.advance(r, deadline - 100, NORMAL).warning());
		// offline across both thresholds: only the final one is told
		LoanRecord r2 = taken(500, 3, 0.2, 0);
		AdvanceEvents ev = LoanRules.advance(r2, r2.deadlineTick - 10, NORMAL);
		assertEquals(2400, ev.warning());
		assertTrue(r2.warned.contains(24000L));
		// 1-day loan: the 1-day warning would fire at signing, so it is skipped
		LoanRecord r3 = taken(100, 1, 0.2, 0);
		assertEquals(-1, LoanRules.advance(r3, 1, NORMAL).warning());
	}

	@Test
	void defaultAndLateFeesWithCap() {
		LoanRecord r = taken(500, 3, 0.2, 0); // due 600
		long d = r.deadlineTick;
		AdvanceEvents ev = LoanRules.advance(r, d, NORMAL);
		assertTrue(ev.defaulted());
		assertEquals(600, r.owedAtDeadline);
		assertTrue(ev.lateFees().isEmpty());
		ev = LoanRules.advance(r, d + MCD, NORMAL);
		assertEquals(List.of(60L), ev.lateFees());
		assertEquals(660, r.owed);
		// simple, not compound; offline catch-up charges several at once
		ev = LoanRules.advance(r, d + 4 * MCD + 5, NORMAL);
		assertEquals(List.of(60L, 60L, 60L), ev.lateFees());
		assertEquals(840, r.owed);
		// cap: 2 × 600 = 1200
		LoanRules.advance(r, d + 100 * MCD, NORMAL);
		assertEquals(1200, r.owed);
		assertTrue(LoanRules.advance(r, d + 101 * MCD, NORMAL).lateFees().isEmpty());
	}

	@Test
	void lateFeesAreBasedOnOwedAtDeadlineAfterPartialPayment() {
		LoanRecord r = taken(500, 3, 0.2, 0);
		LoanRules.advance(r, r.deadlineTick, NORMAL);
		LoanRules.pay(r, 300, r.deadlineTick + 1, 5);
		assertEquals(300, r.owed);
		LoanRules.advance(r, r.deadlineTick + MCD, NORMAL);
		assertEquals(360, r.owed); // + ceil(600 × 0.10)
	}

	@Test
	void boundaries() {
		assertEquals(0, LoanRules.boundariesPassed(1000, 999));
		assertEquals(0, LoanRules.boundariesPassed(1000, 1000 + MCD - 1));
		assertEquals(1, LoanRules.boundariesPassed(1000, 1000 + MCD));
		assertEquals(1000 + MCD, LoanRules.nextBoundary(1000, 1000));
		assertEquals(1000 + 3 * MCD, LoanRules.nextBoundary(1000, 1000 + 2 * MCD));
		assertEquals(3, LoanRules.dayCount(2 * MCD + 1));
		assertEquals(1, LoanRules.dayCount(5));
	}

	@Test
	void assetFreezeSchedule() {
		LoanRecord r = taken(500, 3, 0.2, 0);
		LoanRules.advance(r, r.deadlineTick, NORMAL);
		assertEquals(1, LoanRules.freezeSteps(r, r.deadlineTick, true)); // at DEFAULT
		assertEquals(0, LoanRules.freezeSteps(r, r.deadlineTick + 100, false));
		assertEquals(1, LoanRules.freezeSteps(r, r.deadlineTick + MCD, false));
		assertEquals(2, LoanRules.freezeSteps(r, r.deadlineTick + 3 * MCD, false)); // offline catch-up
		// freeze starting mid-default (switched to Peaceful): first seizure at the next boundary
		LoanRecord m = taken(500, 3, 0.2, 0);
		LoanRules.advance(m, m.deadlineTick, NORMAL);
		assertEquals(0, LoanRules.freezeSteps(m, m.deadlineTick + MCD + 5, false));
		assertEquals(1, LoanRules.freezeSteps(m, m.deadlineTick + 2 * MCD, false));
		// offline through the default itself: one per boundary plus the one at DEFAULT
		LoanRecord o = taken(500, 3, 0.2, 0);
		LoanRules.advance(o, o.deadlineTick + 2 * MCD, NORMAL);
		assertEquals(3, LoanRules.freezeSteps(o, o.deadlineTick + 2 * MCD, true));
	}

	@Test
	void garnishAndSeize() {
		assertEquals(50, LoanRules.garnishAmount(100, 50, 1000));
		assertEquals(49, LoanRules.garnishAmount(99, 50, 1000));
		assertEquals(30, LoanRules.garnishAmount(100, 50, 30)); // never more than owed
		assertEquals(100, LoanRules.garnishAmount(100, 100, 1000));
		assertEquals(0, LoanRules.garnishAmount(100, 0, 1000));
		assertEquals(0, LoanRules.garnishAmount(100, 50, 0));
		assertEquals(500, LoanRules.seizeAmount(1001, 50, 9999));
		assertEquals(40, LoanRules.seizeAmount(1001, 50, 40));
		assertEquals(0, LoanRules.seizeAmount(0, 50, 40));
	}

	@Test
	void adminEdits() {
		LoanRecord r = new LoanRecord();
		r.goodStanding = 2;
		LoanRules.adminSetDebt(r, 700, 100, 3);
		assertEquals(Status.ACTIVE, r.status);
		assertEquals(700, r.owed);
		assertEquals(100 + 3 * MCD, r.deadlineTick);
		assertEquals(2, r.goodStanding);
		LoanRules.adminForceDefault(r, 500);
		LoanRules.advance(r, 500, NORMAL);
		assertEquals(Status.DEFAULT, r.status);
		LoanRules.adminSetDebt(r, 50, 600, 3);
		assertEquals(Status.DEFAULT, r.status);
		assertEquals(50, r.owed);
		LoanRules.adminSetDebt(r, 0, 700, 3);
		assertEquals(Status.NONE, r.status);
		assertEquals(2, r.goodStanding); // clear keeps history
	}

	@Test
	void dormancyShiftsEveryTimer() {
		LoanRecord r = taken(500, 3, 0.2, 1000);
		r.nextWaveTick = 5000;
		r.cooldownUntil = 7000;
		LoanRules.shiftTimers(r, 10_000);
		assertEquals(11_000, r.issueTick);
		assertEquals(11_000 + 3 * MCD, r.deadlineTick);
		assertEquals(15_000, r.nextWaveTick);
		assertEquals(17_000, r.cooldownUntil);
		LoanRecord none = new LoanRecord();
		LoanRules.shiftTimers(none, 500);
		assertEquals(0, none.deadlineTick);
		assertEquals(0, none.nextWaveTick);
	}

	@Test
	void normalizeRepairsBrokenRecords() {
		LoanRecord r = taken(500, 3, 0.2, 0);
		r.owed = -5;
		r.goodStanding = 3;
		r.normalize();
		assertEquals(Status.NONE, r.status);
		assertEquals(3, r.goodStanding);
		assertTrue(new LoanRecord().isBlank());
	}

	/** A whole default episode: every day a fee until the cap, garnishment pays it off, cooldown follows. */
	@Test
	void fullEpisode() {
		LoanRecord r = taken(2000, 5, 0.25, 0); // due 2500, cap 5000
		long d = r.deadlineTick;
		long t = 0;
		int fees = 0;
		while (t <= d + 40 * MCD) {
			fees += LoanRules.advance(r, t, new AdvanceConfig(0.15, 2.0, new int[] {24000, 2400})).lateFees().size();
			t += 1200;
		}
		assertEquals(Status.DEFAULT, r.status);
		assertEquals(5000, r.owed);
		assertEquals(7, fees); // 6 × 375 = 2250, the 7th capped at 250
		long credits = 0;
		while (r.status != Status.NONE) {
			long g = LoanRules.garnishAmount(333, 50, r.owed);
			LoanRules.pay(r, g, t, 5);
			credits++;
		}
		assertEquals(31, credits); // 30 × 166 = 4980, then 20
		assertEquals(t + 5 * MCD, r.cooldownUntil);
	}
}

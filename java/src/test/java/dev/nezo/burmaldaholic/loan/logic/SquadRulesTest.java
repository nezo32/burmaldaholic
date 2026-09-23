package dev.nezo.burmaldaholic.loan.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.loan.logic.LoanRecord.Status;
import dev.nezo.burmaldaholic.loan.logic.LoanRules.Band;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.Choice;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.Effect;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.EffectType;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.EndReason;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.Machine;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.Observation;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.State;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.Timers;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.Unit;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class SquadRulesTest {
	private static final Timers T = Timers.DEFAULT;

	private static int[] counts(Map<Unit, Integer> c) {
		return new int[] {c.get(Unit.DEBT_COLLECTOR), c.get(Unit.REPO_MAN), c.get(Unit.ACCOUNTANT), c.get(Unit.ENFORCER)};
	}

	@Test
	void compositionTable() {
		assertEquals(List.of(2, 0, 0, 0), List.of(box(counts(SquadRules.composition(499, 1, Band.NORMAL, 10, 3)))));
		assertEquals(List.of(2, 1, 0, 0), List.of(box(counts(SquadRules.composition(500, 1, Band.NORMAL, 10, 3)))));
		assertEquals(List.of(2, 1, 0, 0), List.of(box(counts(SquadRules.composition(2999, 1, Band.NORMAL, 10, 3)))));
		assertEquals(List.of(3, 2, 1, 0), List.of(box(counts(SquadRules.composition(3000, 1, Band.NORMAL, 10, 3)))));
		assertEquals(List.of(4, 2, 1, 1), List.of(box(counts(SquadRules.composition(15000, 1, Band.NORMAL, 10, 3)))));
	}

	private static Integer[] box(int[] a) {
		Integer[] out = new Integer[a.length];
		for (int i = 0; i < a.length; i++) {
			out[i] = a[i];
		}
		return out;
	}

	@Test
	void difficultyEscalationAndCap() {
		assertEquals(1, SquadRules.composition(100, 1, Band.EASY, 10, 3).get(Unit.DEBT_COLLECTOR));
		assertEquals(3, SquadRules.composition(100, 1, Band.HARD, 10, 3).get(Unit.DEBT_COLLECTOR));
		assertEquals(4, SquadRules.composition(100, 3, Band.NORMAL, 10, 3).get(Unit.DEBT_COLLECTOR)); // +2 from wave 3
		assertEquals(5, SquadRules.composition(100, 9, Band.NORMAL, 10, 3).get(Unit.DEBT_COLLECTOR)); // escalation max 3
		// 15 000+, Hard, wave 4: 4+1+3 = 8 Collectors + 2 + 1 + 1 = 12 -> capped at 10 by dropping Collectors
		Map<Unit, Integer> big = SquadRules.composition(20000, 4, Band.HARD, 10, 3);
		assertEquals(10, SquadRules.total(big));
		assertEquals(6, big.get(Unit.DEBT_COLLECTOR));
		assertEquals(1, big.get(Unit.ENFORCER));
		// tiny cap keeps the leader and at least one member
		Map<Unit, Integer> tiny = SquadRules.composition(20000, 1, Band.NORMAL, 1, 3);
		assertEquals(1, SquadRules.total(tiny));
	}

	@Test
	void leaderFirstAndScaledHealth() {
		List<Unit> m = SquadRules.members(SquadRules.composition(15000, 1, Band.NORMAL, 10, 3));
		assertEquals(Unit.ACCOUNTANT, m.get(0));
		assertEquals(8, m.size());
		assertEquals(Unit.DEBT_COLLECTOR, SquadRules.members(SquadRules.composition(100, 1, Band.NORMAL, 10, 3)).get(0));
		assertEquals(18, SquadRules.scaledHealth(Unit.DEBT_COLLECTOR, 0.75));
		assertEquals(100, SquadRules.scaledHealth(Unit.ENFORCER, 1.25));
		assertEquals(28, SquadRules.scaledHealth(Unit.ACCOUNTANT, 1.0));
	}

	private static Observation obs(long now, double leader, double nearest, long owed, boolean attacked) {
		return new Observation(now, true, true, false, leader, nearest, 3, owed, attacked);
	}

	@Test
	void approachNegotiateRefuseHostile() {
		Machine m = new Machine(0);
		assertTrue(SquadRules.step(m, obs(10, 20, 20, 500, false), T).isEmpty());
		List<Effect> fx = SquadRules.step(m, obs(20, 5, 5, 500, false), T);
		assertEquals(EffectType.NEGOTIATE, fx.get(0).type());
		assertEquals(State.NEGOTIATE, m.state);
		fx = SquadRules.answer(m, Choice.REFUSE, false, 30);
		assertEquals(EffectType.HOSTILE, fx.get(0).type());
		assertEquals(State.HOSTILE, m.state);
		assertTrue(SquadRules.answer(m, Choice.PAY_ALL, true, 31).isEmpty()); // only in NEGOTIATE
	}

	@Test
	void timeoutsTurnHostile() {
		Machine m = new Machine(0);
		SquadRules.step(m, obs(T.approachTicks(), 30, 30, 500, false), T);
		assertEquals(State.HOSTILE, m.state);
		Machine n = new Machine(0);
		SquadRules.step(n, obs(10, 3, 3, 500, false), T);
		SquadRules.step(n, obs(10 + T.negotiateTicks(), 3, 3, 500, false), T);
		assertEquals(State.HOSTILE, n.state);
		Machine a = new Machine(0);
		SquadRules.step(a, obs(5, 30, 30, 500, true), T); // debtor hit a member
		assertEquals(State.HOSTILE, a.state);
	}

	@Test
	void paymentsSendTheSquadAway() {
		Machine m = new Machine(0);
		SquadRules.step(m, obs(1, 3, 3, 500, false), T);
		List<Effect> fx = SquadRules.answer(m, Choice.PAY_PART, true, 5);
		assertEquals(new Effect(EffectType.LEAVE, EndReason.PARTIAL), fx.get(0));
		assertTrue(SquadRules.step(m, obs(50, 3, 3, 250, false), T).isEmpty());
		fx = SquadRules.step(m, obs(5 + T.leaveTicks(), 3, 3, 250, false), T);
		assertEquals(new Effect(EffectType.DESPAWN, EndReason.PARTIAL), fx.get(0));
		assertEquals(State.DONE, m.state);
		// failed payment -> hostile
		Machine f = new Machine(0);
		SquadRules.step(f, obs(1, 3, 3, 500, false), T);
		SquadRules.answer(f, Choice.PAY_ALL, false, 2);
		assertEquals(State.HOSTILE, f.state);
	}

	@Test
	void debtZeroAlwaysMeansPaid() {
		Machine m = new Machine(0);
		m.state = State.HOSTILE;
		List<Effect> fx = SquadRules.step(m, obs(10, 3, 3, 0, false), T);
		assertEquals(new Effect(EffectType.LEAVE, EndReason.PAID), fx.get(0));
		assertTrue(SquadRules.paid(m, 11).isEmpty()); // already leaving
		Machine n = new Machine(0);
		assertEquals(EndReason.PAID, SquadRules.paid(n, 1).get(0).reason());
	}

	@Test
	void hostileEndings() {
		Machine m = new Machine(0);
		m.state = State.HOSTILE;
		m.since = 0;
		// far for 200 ticks -> despawn
		assertTrue(SquadRules.step(m, obs(10, 100, 100, 500, false), T).isEmpty());
		assertTrue(SquadRules.step(m, obs(100, 100, 100, 500, false), T).isEmpty());
		assertTrue(SquadRules.step(m, obs(150, 20, 20, 500, false), T).isEmpty()); // came back: timer resets
		assertTrue(SquadRules.step(m, obs(300, 100, 100, 500, false), T).isEmpty());
		assertEquals(EndReason.FAR, SquadRules.step(m, obs(500, 100, 100, 500, false), T).get(0).reason());
		// hostile timeout -> leave
		Machine t = new Machine(0);
		t.state = State.HOSTILE;
		assertEquals(new Effect(EffectType.LEAVE, EndReason.TIMEOUT), SquadRules.step(t, obs(T.hostileTicks(), 5, 5, 500, false), T).get(0));
		// debtor died
		Machine d = new Machine(0);
		d.state = State.HOSTILE;
		assertEquals(EndReason.DEBTOR_DIED,
			SquadRules.step(d, new Observation(5, true, true, true, 5, 5, 3, 500, false), T).get(0).reason());
		// offline / other dimension / all dead
		assertEquals(EndReason.OFFLINE, SquadRules.step(new Machine(0), new Observation(5, false, false, false, Double.NaN, Double.NaN, 3, 1, false), T).get(0).reason());
		assertEquals(EndReason.DIMENSION, SquadRules.step(new Machine(0), new Observation(5, true, false, false, Double.NaN, Double.NaN, 3, 1, false), T).get(0).reason());
		assertEquals(EndReason.DEFEATED, SquadRules.step(new Machine(0), new Observation(5, true, true, false, Double.NaN, Double.NaN, 0, 1, false), T).get(0).reason());
	}

	@Test
	void partialPaymentShare() {
		assertEquals(300, SquadRules.partialPayment(600, 0.5));
		assertEquals(301, SquadRules.partialPayment(601, 0.5));
		assertEquals(1, SquadRules.partialPayment(600, 0.0));
		assertEquals(600, SquadRules.partialPayment(600, 1.0));
	}

	@Test
	void waveSchedule() {
		LoanRecord r = new LoanRecord();
		r.status = Status.DEFAULT;
		r.deadlineTick = 1000;
		SquadRules.scheduleFirstWave(r, 1000, 600);
		assertFalse(SquadRules.waveDue(1599, r.nextWaveTick, false));
		assertTrue(SquadRules.waveDue(1600, r.nextWaveTick, false));
		assertFalse(SquadRules.waveDue(1600, r.nextWaveTick, true)); // one live wave at a time
		SquadRules.waveSpawned(r, 1600);
		assertEquals(1, r.wave);
		assertEquals(1000 + LoanRules.MCD, r.nextWaveTick);
		SquadRules.waveRetry(r, 2000, 600);
		assertEquals(2600, r.nextWaveTick);
		// offline: queued wave comes 1200 ticks after the join, max one
		SquadRules.waveQueued(r);
		assertTrue(SquadRules.onDebtorJoin(r, 90_000, 1200));
		assertEquals(91_200, r.nextWaveTick);
		assertFalse(r.queued);
		assertFalse(SquadRules.onDebtorJoin(r, 90_001, 1200));
		LoanRecord none = new LoanRecord();
		SquadRules.waveQueued(none);
		assertFalse(none.queued);
	}

	@Test
	void repossession() {
		SquadRules.Repossession r = SquadRules.repossession(1000, 800, 50, 64);
		assertEquals(500, r.seized());
		assertEquals(64, r.itemCredit());
		r = SquadRules.repossession(1000, 520, 50, 64);
		assertEquals(20, r.itemCredit()); // item only covers the rest
		r = SquadRules.repossession(10_000, 300, 50, 64);
		assertEquals(300, r.seized());
		assertEquals(0, r.itemCredit());
		assertEquals(2, SquadRules.bestAppraised(new long[] {0, 5, 9, 9}));
		assertEquals(-1, SquadRules.bestAppraised(new long[] {0, 0}));
	}

	@Test
	void dialogueVariants() {
		SplittableRandom rng = new SplittableRandom(1);
		for (int i = 0; i < 200; i++) {
			String k = Dialogue.pick(Dialogue.DEMAND, rng::nextInt);
			int n = Integer.parseInt(k.substring(k.lastIndexOf('.') + 1));
			assertTrue(n >= 1 && n <= 5, k);
		}
		assertEquals("dialog.burmaldaholic.loan.piglin_greeting.2", Dialogue.pick(Dialogue.PIGLIN_GREETING, n -> 1));
	}

	@Test
	void spawnSearchFindsSafeGround() {
		// flat world: stone at y=63 and below, air above; a lake in x < -30
		SpawnSearch.CellReader flat = (x, y, z) -> {
			if (x < -30 && y == 63) {
				return SpawnSearch.Cell.LIQUID;
			}
			return y <= 63 ? SpawnSearch.Cell.SOLID : SpawnSearch.Cell.AIR;
		};
		SplittableRandom rng = new SplittableRandom(7);
		SpawnSearch.Rules rules = SpawnSearch.Rules.standard(-64, 320);
		for (int i = 0; i < 100; i++) {
			SpawnSearch.Spot s = SpawnSearch.find(rng::nextDouble, 0.5, 64, 0.5, flat, (x, y, z) -> true, rules);
			assertNotNull(s);
			assertEquals(64, s.y());
			double d = Math.hypot(s.x() + 0.5 - 0.5, s.z() + 0.5 - 0.5);
			assertTrue(d >= 22 && d <= 41, "distance " + d);
			assertTrue(s.x() >= -30);
		}
		// filter rejects everything (claims / border) -> no spot
		assertNull(SpawnSearch.find(rng::nextDouble, 0, 64, 0, flat, (x, y, z) -> false, rules));
		// all water -> no spot
		assertNull(SpawnSearch.find(rng::nextDouble, 0, 64, 0, (x, y, z) -> SpawnSearch.Cell.LIQUID, (x, y, z) -> true, rules));
		// needs 2 blocks of head room
		SpawnSearch.CellReader lowCeiling = (x, y, z) -> y <= 63 || y == 65 ? SpawnSearch.Cell.SOLID : SpawnSearch.Cell.AIR;
		SpawnSearch.Spot s = SpawnSearch.find(rng::nextDouble, 0, 64, 0, lowCeiling, (x, y, z) -> true, rules);
		assertNotNull(s);
		assertEquals(66, s.y()); // on top of the ceiling, not squeezed under it
	}
}

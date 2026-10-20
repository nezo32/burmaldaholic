package dev.nezo.burmaldaholic.core.anim.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import org.junit.jupiter.api.Test;

/** DI §10.1-10 / 12 for the duel: beats, chat ticks, the cup alignment and the per-side cup phases. */
class DuelTimelineTest {
	@Test
	void beatsMatchThePhase2bValues() {
		assertEquals(480, DuelTimeline.THROW);
		assertEquals(1680, DuelTimeline.YOU_LAND);
		assertEquals(2030, DuelTimeline.THEM_LAND);
		assertEquals(2230, DuelTimeline.COMPARE);
		assertEquals(2380, DuelTimeline.CHIPS);
		assertEquals(3080, DuelTimeline.END);
		assertEquals(2900, DuelTimeline.ROUND_PERIOD);
		assertEquals(DuelTimeline.COMPARE + 350, DuelTimeline.SWEEP_AT);
		assertTrue(DuelTimeline.SWEEP_AT + DuelTimeline.SWEEP_MS <= DuelTimeline.ROUND_PERIOD, "the sweep ends before the next round");
	}

	@Test
	void chatTicksNeverRunAheadOfTheCompare() {
		for (int i = 0; i < 3; i++) {
			assertTrue(DuelTimeline.revealTick(i) * 50 >= DuelTimeline.reveal(i), "round " + i);
			assertTrue(DuelTimeline.revealTick(i) * 50 < DuelTimeline.reveal(i) + 50, "round " + i);
			assertEquals(DuelTimeline.roundStart(i) + DuelTimeline.COMPARE, DuelTimeline.reveal(i));
		}
	}

	@Test
	void theirPairRestsBeforeTheSilentBeat() {
		for (int i = 0; i < 3; i++) {
			assertTrue(DuelTimeline.throwAt(i, 1) + DuelTimeline.T_DUEL <= DuelTimeline.reveal(i) - DuelTimeline.SILENT_BEAT, "round " + i);
			assertEquals(DuelTimeline.landAt(i, 0), DuelTimeline.roundStart(i) + DuelTimeline.YOU_LAND);
			assertEquals(DuelTimeline.landAt(i, 1), DuelTimeline.roundStart(i) + DuelTimeline.THEM_LAND);
		}
	}

	@Test
	void countUpsEndBeforeTheNextBeat() {
		for (int a = 1; a <= 6; a++) {
			for (int b = 1; b <= 6; b++) {
				assertTrue(DuelTimeline.YOU_LAND + DuelTimeline.countUpMs(0, a, b) <= DuelTimeline.THEM_LAND + 1e-9, a + "," + b);
				assertTrue(DuelTimeline.THEM_LAND + DuelTimeline.countUpMs(1, a, b) <= DuelTimeline.COMPARE - 20 + 1e-9, a + "," + b);
				assertTrue(DuelTimeline.pipMs(0, a + b) <= DuelTimeline.PIP_MS);
			}
		}
	}

	@Test
	void theFlickReleasesExactlyAtThrowOnAShakeReversal() {
		double off = DuelTimeline.SHAKE_LEAD;
		double windUp0 = DuelTimeline.windUp(0, off);
		assertEquals(Shake.reversal(2), windUp0, 1e-9, "your wind-up starts on a reversal");
		assertEquals(DuelTimeline.THROW + off, Shake.releaseAt(windUp0), 1e-9, "die 0 leaves at THROW");
		assertTrue(off >= 0 && off < 60, "the cups start almost at rest: " + off);
		// house: a late result moves the wind-up to a later reversal, never mid-stroke
		for (double ready = 0; ready < 3000; ready += 37) {
			double o = DuelTimeline.shakeOffset(ready);
			double w = DuelTimeline.windUp(0, o);
			assertTrue(w >= ready - 1e-9 && w >= Shake.reversal(2) - 1e-9, "after the result: " + ready);
			assertEquals(Shake.reversal(Shake.reversalAtOrAfter(w)), w, 1e-9, "on a reversal: " + ready);
		}
		assertEquals(DuelTimeline.SHAKE_LEAD, DuelTimeline.shakeOffset(0), 1e-9);
	}

	@Test
	void theirCupKeepsShakingUntilItsOwnThrow() {
		// DI §0.6 regression: the dealer / opponent cup is tipped only around its own release
		double off = DuelTimeline.SHAKE_LEAD;
		DuelTimeline.Cup you = new DuelTimeline.Cup(Shake.of(1, false), DuelTimeline.windUp(0, off), 170);
		DuelTimeline.Cup them = new DuelTimeline.Cup(Shake.of(2, true), DuelTimeline.windUp(1, off), 170);
		for (double rt = 0; rt < DuelTimeline.END; rt += 1) {
			double ms = rt + off;
			if (them.tipped(ms)) {
				assertTrue(rt >= DuelTimeline.throwAt(0, 1) - Shake.RELEASE_IN_FLICK - 1e-9, "tipped before its flick: " + rt);
				assertTrue(rt <= DuelTimeline.throwAt(0, 1) + 170 + DuelTimeline.Cup.POUR_TAIL + Shake.RECOVER, "tipped long after: " + rt);
			}
		}
		// at THROW + 100 your cup is tipped and theirs is still shaking (a SHAKE frame or passing through rest)
		double at = DuelTimeline.THROW + 100 + off;
		assertTrue(you.tipped(at), "your cup pours");
		int f = them.frame(at);
		assertTrue(f >= Shake.F_REST && f <= Shake.F_SHAKE_R2, "their cup shakes: frame " + f);
		assertNotEquals(0.0, Math.abs(them.offsetX(at)) + Math.abs(them.offsetX(at + 20)), "and moves");
		// the cup holds its tip until die 1 has poured out
		double pour = DuelTimeline.THROW + off + 170;
		assertEquals(Shake.F_TIP70, you.frame(pour), "still tipped while die 1 leaves");
		assertEquals(Shake.F_REST, you.frame(pour + DuelTimeline.Cup.POUR_TAIL + Shake.RECOVER + 1));
		assertEquals(0, you.lift(pour + DuelTimeline.Cup.POUR_TAIL + Shake.RECOVER + 1), 1e-9);
	}

	@Test
	void cupsShakeInCounterPhase() {
		Shake a = Shake.of(11, false);
		Shake b = Shake.of(12, true);
		for (double ms = 0; ms < 400; ms += 3) assertEquals(-a.offsetX(ms, 1e9), b.offsetX(ms, 1e9), 1e-12);
		MotionEvent[] r = a.rattle(DuelTimeline.windUp(0, DuelTimeline.SHAKE_LEAD), 2);
		assertTrue(r.length >= 4, "the cup rattles before the wind-up");
		for (MotionEvent e : r) assertTrue(e.tMs() < DuelTimeline.windUp(0, DuelTimeline.SHAKE_LEAD), "silent wind-up");
	}

	@Test
	void rollSeedsAreKeyedByPlayerSideAndRound() {
		int seed = 0x1234;
		assertEquals(DuelTimeline.rollSeed(seed, 0, 0), DuelTimeline.rollSeed(seed, 0, 0));
		assertNotEquals(DuelTimeline.rollSeed(seed, 0, 0), DuelTimeline.rollSeed(seed, 0, 1));
		assertNotEquals(DuelTimeline.rollSeed(seed, 0, 0), DuelTimeline.rollSeed(seed, 1, 0));
	}

	@Test
	void roundsAndEnd() {
		assertEquals(DuelTimeline.END, DuelTimeline.endMs(1));
		assertEquals(2 * DuelTimeline.ROUND_PERIOD + DuelTimeline.END, DuelTimeline.endMs(3));
		assertEquals(0, DuelTimeline.roundAt(-5, 3));
		assertEquals(1, DuelTimeline.roundAt(DuelTimeline.ROUND_PERIOD + 1, 3));
		assertEquals(2, DuelTimeline.roundAt(1e9, 3));
	}
}

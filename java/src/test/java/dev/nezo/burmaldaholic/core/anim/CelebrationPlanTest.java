package dev.nezo.burmaldaholic.core.anim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Fidelity of the celebration overlay maths (global.md §2.6, §6; docs/architecture/animation.md §4). */
class CelebrationPlanTest {
	private static final TimingProfile NORMAL = new TimingProfile(100, false, true);
	private static final TimingProfile REDUCED = new TimingProfile(100, true, false);

	private static CelebrationPlan plan(long ret, long stake, WinTierTable table) {
		return CelebrationPlan.of(WinTier.of(ret, stake, table), ret, stake, table, NORMAL, false);
	}

	@Test
	void rollUpIsMonotonicAndEndsExact() {
		for (long[] c : new long[][] {{150, 100}, {1100, 100}, {2600, 100}, {5100, 100}, {123457, 10}, {7, 5}, {600, 5}}) {
			for (WinTierTable table : new WinTierTable[] {WinTierTable.DEFAULT, WinTierTable.SLOTS}) {
				for (TimingProfile p : new TimingProfile[] {NORMAL, REDUCED, NORMAL.withSpeed(150), NORMAL.withSpeed(50)}) {
					CelebrationPlan plan = CelebrationPlan.of(WinTier.of(c[0], c[1], table), c[0], c[1], table, p, false);
					long prev = -1;
					for (int t = 0; t <= plan.endMs(); t += 7) {
						long a = plan.amountAt(t);
						assertTrue(a >= prev, "monotonic");
						assertTrue(a <= c[0], "never overshoots");
						prev = a;
					}
					assertEquals(c[0], plan.amountAt(plan.rollMs()), "exact at the end of the roll-up");
					assertEquals(c[0], plan.amountAt(plan.endMs()), "exact final frame");
					assertEquals(plan.tier(), plan.wordAt(plan.endMs()), "final word = server tier");
				}
			}
		}
	}

	@Test
	void wordOnlyUpgradesAndEndsOnServerTier() {
		for (long ret : new long[] {1100, 1500, 2600, 3000, 5100, 9000, 60000}) {
			CelebrationPlan plan = plan(ret, 100, WinTierTable.DEFAULT);
			WinTier prev = plan.wordAt(0);
			for (int t = 0; t <= plan.endMs(); t++) {
				WinTier w = plan.wordAt(t);
				assertTrue(w.ordinal() >= prev.ordinal(), "no downgrade at " + t);
				assertTrue(w.ordinal() <= plan.tier().ordinal(), "never above the server tier");
				prev = w;
			}
			assertEquals(plan.tier(), prev);
		}
	}

	@Test
	void defaultTableStartsOnBigAndUpgradesAtThresholds() {
		CelebrationPlan epic = plan(6000, 100, WinTierTable.DEFAULT); // 60x
		assertEquals(WinTier.EPIC, epic.tier());
		assertEquals(WinTier.BIG, epic.startWord());
		assertEquals(2, epic.upgradeCount(), "BIG → MEGA → EPIC");
		assertEquals(WinTier.MEGA, epic.wordAt(epic.upgradeTime(0)));
		assertEquals(WinTier.BIG, epic.wordAt(epic.upgradeTime(0) - 1));
		assertTrue(epic.amountAt(epic.upgradeTime(0)) >= 2500);
		assertTrue(epic.amountAt(epic.upgradeTime(0) - 1) < 2500);
		assertTrue(epic.amountAt(epic.upgradeTime(1)) >= 5000);
	}

	@Test
	void slotsTableStartsOnNice() {
		CelebrationPlan mega = plan(250, 5, WinTierTable.SLOTS); // 50x
		assertEquals(WinTier.MEGA, mega.tier());
		assertEquals(WinTier.NICE, mega.startWord());
		assertArrayEquals(new int[] {mega.upgradeTime(0), mega.upgradeTime(1)}, mega.upgradeTimes());
		assertEquals(WinTier.MEGA, mega.wordAt(mega.rollMs()));
	}

	@Test
	void epicByNetRuleEndsOnEpicWithoutThresholdUpgrade() {
		// 12x stake but net ≥ 5 000: EPIC by the bigWinThreshold rule; the rolling word never passes 25x
		CelebrationPlan p = plan(60000, 5000, WinTierTable.DEFAULT);
		assertEquals(WinTier.EPIC, p.tier());
		assertEquals(0, p.upgradeCount());
		assertEquals(WinTier.BIG, p.wordAt(p.rollMs() / 2));
		assertEquals(WinTier.EPIC, p.wordAt(p.rollMs()));
	}

	@Test
	void skipShowsFinalFrameHoldsThenExits() {
		CelebrationPlan p = plan(6000, 100, WinTierTable.DEFAULT).withSkipAt(100);
		assertEquals(6000, p.amountAt(100));
		assertEquals(WinTier.EPIC, p.wordAt(100));
		assertEquals(100 + CelebrationPlan.SKIP_HOLD_MS, p.exitStart());
		assertEquals(p.exitStart() + p.exitMs(), p.endMs());
		assertEquals(-1, p.tickIndex(150), "no count ticks after a skip");
		assertEquals(p, p.withSkipAt(500), "second skip keeps the first");
	}

	@Test
	void tierLengthsMatchTheSpecTable() {
		// BIG 2.0 s, MEGA 2.6 s, EPIC 3.2 s, JACKPOT 4.0 s at the tier maxima (large multiples)
		CelebrationPlan big = CelebrationPlan.of(WinTier.BIG, 1_000_000, 100, WinTierTable.DEFAULT, NORMAL, false);
		assertEquals(2000, big.endMs());
		CelebrationPlan mega = CelebrationPlan.of(WinTier.MEGA, 1_000_000, 100, WinTierTable.DEFAULT, NORMAL, false);
		assertEquals(2600, mega.endMs());
		CelebrationPlan epic = CelebrationPlan.of(WinTier.EPIC, 1_000_000, 100, WinTierTable.DEFAULT, NORMAL, false);
		assertEquals(3200, epic.endMs());
		CelebrationPlan jp = CelebrationPlan.of(WinTier.JACKPOT, 1_000_000, 100, WinTierTable.DEFAULT, NORMAL, false);
		assertEquals(4000, jp.endMs());
		assertEquals(WinTier.JACKPOT, jp.wordAt(0));
		CelebrationPlan win = CelebrationPlan.of(WinTier.WIN, 150, 100, WinTierTable.DEFAULT, NORMAL, false);
		assertEquals(800, win.endMs());
	}

	@Test
	void speedScalesDurations() {
		CelebrationPlan n = CelebrationPlan.of(WinTier.EPIC, 1_000_000, 100, WinTierTable.DEFAULT, NORMAL, false);
		CelebrationPlan turbo = CelebrationPlan.of(WinTier.EPIC, 1_000_000, 100, WinTierTable.DEFAULT, NORMAL.withSpeed(150), false);
		assertEquals(NORMAL.withSpeed(150).scale(n.rollMs()), turbo.rollMs());
		assertTrue(turbo.endMs() < n.endMs());
	}

	@Test
	void reduceMotionIsStaticAndShort() {
		CelebrationPlan p = CelebrationPlan.of(WinTier.EPIC, 1_000_000, 100, WinTierTable.DEFAULT, REDUCED, false);
		assertTrue(p.rollMs() <= 300);
		assertEquals(1f, p.wordScale(0));
		assertEquals(0f, p.bannerOffset(0));
		assertEquals(0f, p.raysDegrees(1000));
	}

	@Test
	void celebrationsOffUsesWinBannerButKeepsWordAndAmount() {
		CelebrationPlan p = CelebrationPlan.of(WinTier.MEGA, 3000, 100, WinTierTable.DEFAULT, NORMAL, true);
		assertEquals(WinTier.WIN, p.style());
		assertTrue(!p.overlay());
		assertEquals(WinTier.MEGA, p.wordAt(0));
		assertEquals(3000, p.amountAt(p.endMs()));
		assertEquals(0f, p.backdrop(500));
	}

	@Test
	void nonWinsDoNotRoll() {
		for (WinTier t : new WinTier[] {WinTier.LOSS, WinTier.RETURN, WinTier.PUSH}) {
			CelebrationPlan p = CelebrationPlan.of(t, 40, 100, WinTierTable.DEFAULT, NORMAL, false);
			assertEquals(0, p.rollMs());
			assertEquals(40, p.amountAt(0));
			assertEquals(t, p.wordAt(0));
			assertEquals(0, p.tickCount());
		}
	}

	@Test
	void countTicksAreRateLimitedAndPitchRises() {
		CelebrationPlan p = CelebrationPlan.of(WinTier.JACKPOT, 1_000_000, 100, WinTierTable.DEFAULT, NORMAL, false);
		assertTrue(p.tickCount() <= p.rollMs() * CelebrationPlan.MAX_TICKS_PER_S / 1000);
		assertEquals(0.9f, p.tickPitch(0), 1e-6);
		assertEquals(1.4f, p.tickPitch(p.tickCount() - 1), 1e-6);
		int prev = -1;
		for (int t = 0; t < p.rollMs(); t++) {
			int i = p.tickIndex(t);
			assertTrue(i >= prev && i - prev <= 1);
			prev = i;
		}
		assertEquals(p.tickCount() - 1, prev);
		assertEquals(0, CelebrationPlan.of(WinTier.WIN, 150, 100, WinTierTable.DEFAULT, NORMAL, false).tickCount(), "WIN has no ticks");
	}

	@Test
	void alphaFadesOut() {
		CelebrationPlan p = plan(1100, 100, WinTierTable.DEFAULT);
		assertEquals(1f, p.alpha(p.exitStart()));
		assertEquals(0f, p.alpha(p.endMs()));
		assertTrue(p.done(p.endMs()));
	}
}

package dev.nezo.burmaldaholic.core.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.fx.BigWinBroadcast;
import org.junit.jupiter.api.Test;

/**
 * Adversarial review tests of lane J-L1 (presentation core): the Bedrock review findings must be absent in Java
 * (skip before the first frame, per-key budgets, MAX WIN plate) and the budgets must survive clock resets and level
 * changes.
 */
class PresentationReviewTest {
	private static final TimingProfile[] PROFILES = {new TimingProfile(50, false, true), new TimingProfile(100, false, true),
		new TimingProfile(150, false, true), new TimingProfile(100, true, false), new TimingProfile(150, true, false)};

	@Test
	void skipBeforeTheFirstFrameShowsFinalTierExactAmountAndPlateInEveryProfile() {
		for (TimingProfile prof : PROFILES) {
			for (WinTier tier : WinTier.values()) {
				for (WinTierTable table : new WinTierTable[] {WinTierTable.DEFAULT, WinTierTable.SLOTS}) {
					for (boolean off : new boolean[] {false, true}) {
						long ret = tier.isWin() ? 123_457 : tier == WinTier.RETURN ? 40 : 0;
						CelebrationPlan base = CelebrationPlan.of(tier, ret, 100, table, prof, off, true);
						// a skip "before" the first frame (negative local ms: input handled before the overlay's start)
						CelebrationPlan p = base.withSkipAt(-50);
						assertTrue(p.finalFrame(0), "final frame drawn at once");
						assertEquals(tier, p.wordAt(0), "final word " + tier);
						assertEquals(ret, p.amountAt(0), "exact amount " + tier);
						assertTrue(p.maxWinPlate(0), "MAX WIN plate kept on a skip " + tier);
						assertEquals(1f, p.alpha(0));
						assertTrue(p.endMs() >= CelebrationPlan.SKIP_HOLD_MS || p.endMs() == base.endMs(), "held " + tier);
						assertTrue(p.withSkipAt(10) == p, "a second skip keeps the first");
					}
				}
			}
		}
	}

	@Test
	void skipNeverExtendsACelebrationAndNeverHidesTheFinalFrameEarly() {
		for (TimingProfile prof : PROFILES) {
			CelebrationPlan p = CelebrationPlan.of(WinTier.EPIC, 80_000, 100, WinTierTable.DEFAULT, prof, false, false);
			for (int at = 0; at < p.endMs(); at += 97) {
				CelebrationPlan s = p.withSkipAt(at);
				assertTrue(s.endMs() <= p.endMs(), "skip at " + at + " lengthened the overlay");
				for (int t = at; t < s.exitStart(); t += 13) {
					assertEquals(80_000, s.amountAt(t));
					assertEquals(WinTier.EPIC, s.wordAt(t));
				}
			}
		}
	}

	@Test
	void wordNeverDowngradesAndAmountIsMonotonicForRandomRounds() {
		SeedMix.FxRng rng = new SeedMix.FxRng(42);
		WinTier[] overlay = {WinTier.BIG, WinTier.MEGA, WinTier.EPIC};
		for (int i = 0; i < 400; i++) {
			long stake = 1 + rng.nextInt(5000);
			long ret = stake * (10 + rng.nextInt(200)) + rng.nextInt(1000);
			WinTier tier = overlay[rng.nextInt(overlay.length)];
			WinTierTable table = rng.nextInt(2) == 0 ? WinTierTable.DEFAULT : WinTierTable.SLOTS;
			CelebrationPlan p = CelebrationPlan.of(tier, ret, stake, table, PROFILES[rng.nextInt(PROFILES.length)], false, false);
			long last = -1;
			int word = -1;
			for (int t = 0; t <= p.endMs(); t += 7) {
				long a = p.amountAt(t);
				assertTrue(a >= last && a <= ret, "monotonic, never above the server amount");
				last = a;
				int w = p.wordAt(t).ordinal();
				assertTrue(w >= word, "no downgrade");
				word = w;
			}
			assertEquals(ret, p.amountAt(p.rollMs()));
			assertEquals(tier, p.wordAt(p.rollMs()));
		}
	}

	@Test
	void rateBudgetIsPerKeyAndSurvivesAClockThatGoesBackwards() {
		RateBudget b = new RateBudget(2, 4, 1000);
		long t0 = 5_000_000;
		assertTrue(b.tryAcquire("WIN", t0) && b.tryAcquire("WIN", t0 + 1));
		assertFalse(b.tryAcquire("WIN", t0 + 2), "per key");
		assertTrue(b.tryAcquire("TOAST", t0 + 3), "another key is not starved");
		// the server restarts (singleplayer: a new world), its tick clock starts from 0 again
		assertTrue(b.tryAcquire("WIN", 0), "a restarted clock must not block the key until it catches up");
		assertTrue(b.tryAcquire("WIN", 1));
		assertFalse(b.tryAcquire("WIN", 2), "and the budget still applies afterwards");
		assertTrue(b.tryAcquire("TOAST", 3), "the overall cap forgot the future stamps too");
	}

	@Test
	void particleBudgetCapsAndDoesNotLeakAcrossLevelChanges() {
		ParticleBudget b = new ParticleBudget(400, 60, 150);
		Object overworld = new Object();
		Object nether = new Object();
		int[] tokens = new int[500];
		int granted = 0;
		for (int i = 0; i < 500; i++) {
			tokens[i] = b.acquire(overworld);
			if (tokens[i] >= 0) granted++;
		}
		assertEquals(400, granted, "≤ 400 alive");
		assertEquals(0, b.allowance(overworld, 60, false, false), "a full budget allows no burst");
		// dimension change: the engine drops the particles without remove()
		assertEquals(60, b.allowance(nether, 100, false, false), "new level: budget is free again");
		assertEquals(150, b.allowance(nether, 1000, true, false), "jackpot cap");
		assertEquals(18, b.allowance(nether, 60, false, true), "reduce motion × 0.3");
		int t = b.acquire(nether);
		assertTrue(t >= 0);
		// late removes of the old level's particles must not push the count below the live ones
		for (int tok : tokens) b.release(tok);
		assertEquals(1, b.alive());
		b.release(t);
		b.release(t);
		assertEquals(0, b.alive(), "never negative");
		b.reset();
		assertEquals(0, b.alive());
		assertEquals(0, b.allowance(nether, -5, false, false));
	}

	@Test
	void broadcastRuleIsTheEpicTierOfTheDefaultTable() {
		// global.md §2.4/§4.7: EPIC = return ≥ 50 × stake and net ≥ 500, or net ≥ core.bigWinThreshold
		assertEquals(WinTier.EPIC, BigWinBroadcast.tierOf(1000, 20, 5000), "50× with net 980 is announced (Bedrock's net-only rule misses it)");
		assertTrue(BigWinBroadcast.tierOf(500, 10, 5000).ordinal() < WinTier.EPIC.ordinal(), "50× with net 490 is not EPIC");
		assertEquals(WinTier.EPIC, BigWinBroadcast.tierOf(5100, 100, 5000), "net ≥ threshold at 51×");
		assertEquals(WinTier.EPIC, BigWinBroadcast.tierOf(10_000, 5000, 5000), "net = threshold at 2×");
		assertTrue(BigWinBroadcast.tierOf(9_999, 5000, 5000).ordinal() < WinTier.EPIC.ordinal(), "net 4 999 at 2× is not");
	}
}

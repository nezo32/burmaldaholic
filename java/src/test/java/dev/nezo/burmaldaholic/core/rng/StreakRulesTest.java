package dev.nezo.burmaldaholic.core.rng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** GAME_DESIGN.md §14 vectors. */
class StreakRulesTest {
	private static final StreakRules.Settings S = StreakRules.Settings.DEFAULTS;

	@Test
	void updateFollowsSpec() {
		assertEquals(1, StreakRules.update(0, 5, 10));
		assertEquals(4, StreakRules.update(3, 1, 10));
		assertEquals(1, StreakRules.update(-4, 1, 10), "a win after losses restarts at +1");
		assertEquals(-1, StreakRules.update(6, -1, 10), "a loss after wins restarts at -1");
		assertEquals(-5, StreakRules.update(-4, -2, 10));
		assertEquals(10, StreakRules.update(10, 3, 10), "capped");
		assertEquals(-10, StreakRules.update(-10, -3, 10));
		assertEquals(7, StreakRules.update(7, 0, 10), "push leaves it unchanged");
	}

	@Test
	void decayMovesTowardZeroPerPeriod() {
		assertEquals(new StreakRules.Decayed(5, 0), StreakRules.decay(5, 0, 11_999, 12_000));
		assertEquals(new StreakRules.Decayed(4, 12_000), StreakRules.decay(5, 0, 12_000, 12_000));
		assertEquals(new StreakRules.Decayed(-3, 24_000), StreakRules.decay(-5, 0, 30_000, 12_000));
		assertEquals(0, StreakRules.decay(2, 0, 1_000_000, 12_000).streak());
		assertEquals(5, StreakRules.decay(5, 0, 1_000_000, 0).streak(), "decayTicks 0 disables decay");
	}

	@Test
	void redrawProbabilityAndCap() {
		assertEquals(0.02, StreakRules.redrawProbability(4, 0.8976, S), 1e-12, "copper slots: full 0.005·S");
		assertEquals(0.05, StreakRules.redrawProbability(10, 0.8976, S), 1e-12);
		assertEquals(0.009, StreakRules.redrawProbability(-3, 0.8976, S), 1e-12, "pity 0.003·|S|");
		double coinCap = (1 - 0.01) / 0.98 - 1;
		assertEquals(coinCap, StreakRules.redrawProbability(10, 0.98, S), 1e-12, "coin flip capped at r_cap ≈ 0.0102");
		assertEquals(0.0, StreakRules.redrawProbability(5, 0.995, S), 1e-12, "RTP above 1 − minHouseEdge: no re-draw");
		assertEquals(0.0, StreakRules.redrawProbability(0, 0.9, S));
		StreakRules.Settings off = new StreakRules.Settings(false, 10, 0.005, 0.003, 0.01, 12000);
		assertEquals(0.0, StreakRules.redrawProbability(10, 0.8, off));
	}

	@Test
	void playNeverPushesRtpAboveOneMinusHouseEdge() {
		// Coin flip, RTP 0.98, max lucky streak: the re-draw must keep RTP ≤ 0.99 (§14 proof).
		OddsService service = new OddsService(new SplittableRandom(99));
		UUID player = UUID.randomUUID();
		service.setStreakSource(id -> 10, () -> S);
		OddsContext ctx = new OddsContext(player, "coin_flip", 100);
		CasinoRng rng = service.rng(ctx);
		long staked = 0;
		long returned = 0;
		for (int i = 0; i < 2_000_000; i++) {
			boolean win = service.play(ctx, 0.98, () -> rng.nextInt(2) == 0, w -> !w);
			staked += 100;
			returned += win ? 196 : 0;
		}
		double rtp = returned / (double) staked;
		assertTrue(rtp < 0.9905 && rtp > 0.981, "RTP " + rtp); // expected 0.98 + 0.5·r·0.98 ≈ 0.985, never above 0.99
	}
}

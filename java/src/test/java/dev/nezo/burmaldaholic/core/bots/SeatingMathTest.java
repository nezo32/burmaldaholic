package dev.nezo.burmaldaholic.core.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotEconomyMath;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.SeatingMath;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** BOTS.md §12.4 S1/S2 and §12.5 vectors (same as bedrock src/core/logic/bots/seating.test.ts). */
class SeatingMathTest {
	private static final BotSettings POKER = new BotSettings(SeatPolicy.MIXED, 5, BotDifficulty.MIXED, true, true, BotSpeed.NORMAL);

	@Test
	void mixedKeepFree() {
		assertEquals(4, SeatingMath.wantedBots(POKER, 6, 1, 0)); // S1
		assertEquals(3, SeatingMath.wantedBots(POKER, 6, 2, 0)); // S2
		assertEquals(0, SeatingMath.wantedBots(POKER, 6, 0, 0)); // nobody plays with bots alone
		assertEquals(3, SeatingMath.wantedBots(POKER.withPolicy(SeatPolicy.BOTS_ONLY).withCount(3), 6, 1, 0));
		assertEquals(0, SeatingMath.wantedBots(POKER.withPolicy(SeatPolicy.HUMANS_ONLY), 6, 1, 0));
	}

	@Test
	void economyVectors() {
		assertEquals(96, BotEconomyMath.pokerPot(290, 100, 100, 0, 300)); // fromBots_h (§12.5)
		assertEquals(250, BotEconomyMath.vipCredit(400, 0.5, 0.75));
		assertEquals(5000, BotEconomyMath.dailyCap(1000, 500, 5));
		assertEquals(3, BotEconomyMath.pvpRakeToBank(6, 100, 200));
	}

	@Test
	void namesAreUniquePerTable() {
		BotRng rng = BotRng.seeded(42);
		Set<String> used = new HashSet<>();
		for (int i = 0; i < 7; i++) {
			String id = BotRoster.drawName(rng, BotRoster.Theme.ANY, used);
			assertFalse(used.contains(id));
			used.add(id);
		}
	}
}

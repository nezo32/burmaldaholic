package dev.nezo.burmaldaholic.games.extras.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.WinTier;
import org.junit.jupiter.api.Test;

/** The server-side extras tiers (extras-pvp.md §0.4): the celebration is computed on the server, never on the client. */
class ExtrasTiersTest {
	@Test
	void nothingForLossesPushesReturnsAndNonChipStakes() {
		assertNull(ExtrasTiers.celebrated(100, 0, false, true)); // loss
		assertNull(ExtrasTiers.celebrated(100, 50, false, true)); // Half back
		assertNull(ExtrasTiers.celebrated(100, 100, false, true)); // Money back
		assertNull(ExtrasTiers.celebrated(100, 196, false, false)); // a pawn win
		assertNull(ExtrasTiers.celebrated(0, 196, false, true));
	}

	@Test
	void winsUseTheDefaultLadder() {
		assertEquals(WinTier.WIN, ExtrasTiers.celebrated(100, 196, false, true)); // Coin Flip 1.96×
		assertEquals(WinTier.WIN, ExtrasTiers.celebrated(100, 300, false, true)); // Triple
		assertEquals(WinTier.of(1000, 100, dev.nezo.burmaldaholic.core.anim.WinTierTable.DEFAULT), ExtrasTiers.celebrated(100, 1000, false, true));
		assertEquals(WinTier.JACKPOT, ExtrasTiers.celebrated(10, 10_000, true, true)); // Plinko edge / Scratch top prize
		assertTrue(ExtrasTiers.celebrated(10, 3300, false, true).isOverlay()); // Medium 33×
	}

	@Test
	void plinkoJackpotIsTheHighEdge() {
		assertTrue(ExtrasTiers.plinkoJackpot(Plinko.Risk.HIGH, 0, 13));
		assertTrue(ExtrasTiers.plinkoJackpot(Plinko.Risk.HIGH, 12, 13));
		assertFalse(ExtrasTiers.plinkoJackpot(Plinko.Risk.HIGH, 6, 13));
		assertFalse(ExtrasTiers.plinkoJackpot(Plinko.Risk.MEDIUM, 0, 13));
	}
}

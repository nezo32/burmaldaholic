package dev.nezo.burmaldaholic.core.wager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** EC-SPEC §4.3: win pays max(0, total return - value), push returns the token, loss pays nothing. */
class TokenSettleTest {
	@Test
	void winPaysWinningsOnly() {
		TokenSettle.Settlement s = TokenSettle.settle(50, 50, 100, false); // even-money win on a 50 token
		assertEquals(50, s.credit());
		assertFalse(s.returnToken());
		assertEquals(75, TokenSettle.settle(50, 50, 125, false).credit(), "blackjack 3:2");
	}

	@Test
	void pushReturnsTheToken() {
		TokenSettle.Settlement s = TokenSettle.settle(50, 50, 50, false);
		assertEquals(0, s.credit());
		assertTrue(s.returnToken());
	}

	@Test
	void lossPaysNothing() {
		TokenSettle.Settlement s = TokenSettle.settle(50, 50, 0, false);
		assertEquals(0, s.credit());
		assertFalse(s.returnToken());
		assertEquals(0, TokenSettle.settle(50, 50, 25, false).credit(), "a partial return below the value pays nothing");
	}

	@Test
	void chipsAddedOnTopAreTheirsAgain() {
		// blackjack: 50 token + 50 chips double; a doubled win returns 200 -> 150 chips (50 own back + 100 winnings)
		assertEquals(150, TokenSettle.settle(100, 50, 200, false).credit());
		// doubled push: token back plus the 50 chips
		TokenSettle.Settlement push = TokenSettle.settle(100, 50, 100, false);
		assertTrue(push.returnToken());
		assertEquals(50, push.credit());
	}

	@Test
	void slotsCreditTheFullWin() {
		TokenSettle.Settlement s = TokenSettle.settle(40, 40, 40, true);
		assertEquals(40, s.credit());
		assertFalse(s.returnToken(), "slots have no push rule");
		assertEquals(0, TokenSettle.settle(40, 40, 0, true).credit());
	}

	@Test
	void oneBetOutcomes() {
		assertEquals(48, TokenSettle.ofOutcome(Stakes.Outcome.WIN, 48).credit());
		assertTrue(TokenSettle.ofOutcome(Stakes.Outcome.PUSH, 0).returnToken());
		assertEquals(0, TokenSettle.ofOutcome(Stakes.Outcome.LOSS, 99).credit());
	}
}

package dev.nezo.burmaldaholic.games.poker.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import org.junit.jupiter.api.Test;

class PokerDealerCuesTest {
	private static PokerPub pub(int kind, long start, int... flags) {
		return new PokerPub(4, kind, start, new int[0], 1, 0, new int[0], 100, flags, new long[flags.length], new int[flags.length * 2]);
	}

	@Test
	void dealStreetsAndPot() {
		int d = PokerPub.SEATED | PokerPub.DEALT;
		assertNull(PokerDealerCues.cue(PokerPub.EMPTY, 10, 0));
		PokerPub deal = pub(1, 100, d, d, d);
		var first = PokerDealerCues.cue(deal, 101, 0);
		assertEquals(DealerGesture.DEAL, first.gesture());
		assertEquals(-1f, first.towardSeat(), 1e-6, "first card to seat 0 (dealer's left)");
		assertEquals(1f, PokerDealerCues.cue(deal, 100 + 2 * PokerDealerCues.DEAL_STEP + 1, 0).towardSeat(), 1e-6, "third card to seat 2");
		assertNull(PokerDealerCues.cue(deal, 100 + 6 * PokerDealerCues.DEAL_STEP + 10, 0), "six cards pitched, then idle");
		assertEquals(DealerGesture.DEAL, PokerDealerCues.cue(pub(2, 300, d, d), 302, 0).gesture(), "flop / turn / river");
		var pot = PokerDealerCues.cue(pub(3, 500, d, d | PokerPub.WINNER, d), 503, 0);
		assertEquals(DealerGesture.POT_PUSH, pot.gesture());
		assertEquals(0f, pot.towardSeat(), 1e-6, "pot pushed to the middle seat");
		assertEquals(DealerGesture.SHUFFLE, PokerDealerCues.cue(pub(3, 500, d, d), 500 + PokerDealerCues.SHUFFLE_DELAY + 5, 0).gesture());
		assertTrue(PokerDealerCues.cue(pub(3, 500, d), 500 + PokerDealerCues.SHUFFLE_DELAY + 40, 0) == null);
	}
}

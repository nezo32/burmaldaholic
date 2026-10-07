package dev.nezo.burmaldaholic.games.blackjack.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

class BlackjackPubTest {
	static BlackjackPub sample() {
		var h1 = new BlackjackPub.HandPub(50, BlackjackPub.SPLIT, BlackjackPub.outcomeCode("win"), 100, new int[] {12, 25}, new int[] {0, 30});
		var h2 = new BlackjackPub.HandPub(100, BlackjackPub.SPLIT | BlackjackPub.DOUBLED | BlackjackPub.DONE, BlackjackPub.outcomeCode("bust"), 0,
			new int[] {12, 4, 9}, new int[] {6, 36, 48});
		var s0 = new BlackjackPub.SeatPub(0, BlackjackPub.SETTLED, 50, -50, List.of(h1, h2));
		var s3 = new BlackjackPub.SeatPub(3, BlackjackPub.BOT, 25, 0, List.of(new BlackjackPub.HandPub(25, 0, 0, 0, new int[] {51}, new int[] {12})));
		return new BlackjackPub(7, 123_456_789_012L, BlackjackPub.RESULT, 10, 5_000_000_000L, -1, -1, 123_456_789_100L, new int[] {0, -1},
			new int[] {18, 24}, List.of(s0, s3));
	}

	@Test
	void roundTrips() {
		BlackjackPub p = sample();
		BlackjackPub d = BlackjackPub.decode(p.encode());
		assertEquals(p, d);
		assertEquals(5_000_000_000L, d.max());
		assertEquals("bust", d.seat(0).hands().get(1).outcomeId());
		assertEquals(-1, d.dealer()[1]);
		assertEquals(BlackjackPub.EMPTY, BlackjackPub.decode(BlackjackPub.EMPTY.encode()));
	}

	@Test
	void malformedGivesEmpty() {
		int[] e = sample().encode();
		assertSame(BlackjackPub.EMPTY, BlackjackPub.decode(new int[0]));
		assertSame(BlackjackPub.EMPTY, BlackjackPub.decode(java.util.Arrays.copyOf(e, e.length - 1)));
		assertSame(BlackjackPub.EMPTY, BlackjackPub.decode(java.util.Arrays.copyOf(e, e.length + 1)));
		int[] v = e.clone();
		v[0] = 99;
		assertSame(BlackjackPub.EMPTY, BlackjackPub.decode(v));
	}

	@Test
	void outcomeCodesMatchTheRoundEnum() {
		var values = dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound.Outcome.values();
		assertEquals(values.length + 1, BlackjackPub.OUTCOMES.length);
		for (int i = 0; i < values.length; i++) assertEquals(values[i].id(), BlackjackPub.OUTCOMES[i + 1]);
	}
}

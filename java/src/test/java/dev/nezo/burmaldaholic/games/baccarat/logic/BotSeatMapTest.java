package dev.nezo.burmaldaholic.games.baccarat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Bots around core's human seats (0-based seat indices). */
class BotSeatMapTest {
	@Test
	void fillsTheLowestFreeSeatAndMovesForHumans() {
		BotSeatMap m = new BotSeatMap();
		assertTrue(m.add("bot:a", List.of(0), 3));
		assertTrue(m.add("bot:b", List.of(0), 3));
		assertFalse(m.add("bot:c", List.of(0), 3));
		assertEquals(1, m.seat("bot:a").getAsInt());
		assertEquals(2, m.seat("bot:b").getAsInt());
		m.remove("bot:a");
		// core seats the next human at index 1 (it does not know bots)… index 2 stays the bot's
		assertTrue(m.resolve(List.of(0, 1), 3));
		assertEquals(2, m.seat("bot:b").getAsInt());
		BotSeatMap n = new BotSeatMap();
		n.add("bot:x", List.of(), 3);
		assertEquals(0, n.seat("bot:x").getAsInt());
		assertTrue(n.resolve(List.of(0), 3), "a human took the bot's seat: the bot moves");
		assertEquals(1, n.seat("bot:x").getAsInt());
		assertFalse(n.resolve(List.of(0, 1, 2), 3), "no seat left");
		assertFalse(n.has("bot:x"));
	}
}

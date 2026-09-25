package dev.nezo.burmaldaholic.core.bots.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Which bot shows the thinking dots when several wait on their decision moment (global.md §4.12). */
class ThinkingBotTest {
	@Test
	void earliestFutureMomentWins() {
		Map<String, Long> due = new LinkedHashMap<>();
		due.put("bot:a", 150L);
		due.put("bot:b", 120L);
		due.put("bot:c", 90L); // already acted
		assertEquals("bot:b", ThinkingBot.next(due, 100));
		assertEquals("bot:a", ThinkingBot.next(due, 120)); // due == now: acting this tick, not thinking
	}

	@Test
	void tiesKeepSeatOrder() {
		Map<String, Long> due = new LinkedHashMap<>();
		due.put("bot:x", 200L);
		due.put("bot:y", 200L);
		assertEquals("bot:x", ThinkingBot.next(due, 0));
	}

	@Test
	void noneWhenNothingIsPending() {
		Map<String, Long> due = new LinkedHashMap<>();
		assertNull(ThinkingBot.next(due, 0));
		due.put("bot:a", -1L);
		due.put("bot:b", 10L);
		assertNull(ThinkingBot.next(due, 10));
	}
}

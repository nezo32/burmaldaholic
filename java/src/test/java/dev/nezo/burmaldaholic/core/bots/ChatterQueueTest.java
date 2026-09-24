package dev.nezo.burmaldaholic.core.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotLines;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.ChatterQueue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Chatter limits (BOTS.md §7.4, §12.6). */
class ChatterQueueTest {
	private static final ChatterQueue.Config DEFAULT = new ChatterQueue.Config(true, 0.35, 600, 200, 3);
	private static final List<String> CHATTY = List.of("win_big", "bust", "bad_beat", "human_wins", "all_in", "join", "leave");

	@Test
	void thirtyMinuteSessionRespectsEveryLimit() {
		BotRng rng = BotRng.seeded(7);
		ChatterQueue q = new ChatterQueue();
		List<ChatterQueue.Line> said = new ArrayList<>();
		String[] bots = {"bot:a", "bot:b", "bot:c", "bot:d", "bot:e"};
		for (long t = 0; t < 36_000; t += 5) {
			// a bot-heavy table: an event every 5 ticks
			q.offer(bots[(int) (t / 5 % bots.length)], BotDifficulty.NORMAL, CHATTY.get((int) (t / 5 % CHATTY.size())), "Alex", t, DEFAULT, rng);
			said.addAll(q.due(t));
		}
		assertTrue(said.size() > 20, "the table still talks: " + said.size());
		for (int i = 0; i < said.size(); i++) {
			long start = said.get(i).dueTick();
			long inMinute = said.stream().filter(l -> l.dueTick() >= start && l.dueTick() < start + 1200).count();
			assertTrue(inMinute <= 3, "≤ 3 lines per table-minute");
			if (i > 0) {
				assertTrue(start - said.get(i - 1).dueTick() >= 200, "table cooldown");
			}
		}
		Map<String, Long> last = new HashMap<>();
		for (ChatterQueue.Line l : said) {
			Long prev = last.put(l.botKey(), l.dueTick());
			assertTrue(prev == null || l.dueTick() - prev >= 600, "≤ 1 line per bot per 30 s");
			assertTrue(l.variant() >= 1 && l.variant() <= BotLines.variants(l.event()));
		}
	}

	@Test
	void explanatoryLinesQueueForTheTableCooldown() {
		BotRng rng = BotRng.seeded(1);
		ChatterQueue q = new ChatterQueue();
		ChatterQueue.Config always = new ChatterQueue.Config(true, 1.0, 600, 200, 3);
		assertNotNull(q.offer("bot:a", BotDifficulty.NORMAL, "bust", "", 1000, always, rng));
		assertNull(q.offer("bot:b", BotDifficulty.NORMAL, "bust", "", 1100, always, rng), "ordinary lines are dropped in the cooldown");
		ChatterQueue.Line y = q.offer("bot:b", BotDifficulty.NORMAL, "yield", "Alex", 1170, always, rng);
		assertNotNull(y, "yield always fires");
		assertEquals(1200, y.dueTick(), "…after the table cooldown (≤ 40 t)");
		assertNull(q.offer("bot:c", BotDifficulty.NORMAL, "sulk", "Alex", 1210, always, rng), "more than 40 t to wait: dropped");
		assertEquals(1, q.due(1000).size());
		assertTrue(q.due(1199).isEmpty());
		assertEquals("dialog.burmaldaholic.bots.yield." + y.variant(), q.due(1200).getFirst().key());
	}

	@Test
	void switchesAndHardBotsTalkLess() {
		BotRng rng = BotRng.seeded(3);
		ChatterQueue off = new ChatterQueue();
		assertNull(off.offer("bot:a", BotDifficulty.NORMAL, "yield", "", 0, new ChatterQueue.Config(false, 1, 0, 0, 3), rng), "muted table / server");
		assertNull(off.offer("bot:a", BotDifficulty.NORMAL, "no_such_event", "", 0, new ChatterQueue.Config(true, 1, 0, 0, 3), rng));
		int normal = 0;
		int hard = 0;
		ChatterQueue.Config free = new ChatterQueue.Config(true, 0.4, 0, 0, 60);
		for (int i = 0; i < 20_000; i++) {
			ChatterQueue a = new ChatterQueue();
			normal += a.offer("bot:n", BotDifficulty.NORMAL, "bust", "", 0, free, rng) != null ? 1 : 0;
			ChatterQueue b = new ChatterQueue();
			hard += b.offer("bot:h", BotDifficulty.HARD, "bust", "", 0, free, rng) != null ? 1 : 0;
		}
		assertEquals(0.4, normal / 20_000.0, 0.02);
		assertEquals(0.2, hard / 20_000.0, 0.02, "HARD bots: half the chance");
	}
}

package dev.nezo.burmaldaholic.core.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.ChatterQueue;
import dev.nezo.burmaldaholic.core.bots.logic.HeatNotices;
import dev.nezo.burmaldaholic.core.bots.logic.HeatStage;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Review wave 3: the heat lines have one source, told once per table, player, stage and day. */
class HeatNoticesTest {
	private static final UUID A = UUID.randomUUID();
	private static final UUID B = UUID.randomUUID();

	private static Map<UUID, HeatStage> stages(Object... kv) {
		Map<UUID, HeatStage> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((UUID) kv[i], (HeatStage) kv[i + 1]);
		}
		return m;
	}

	@Test
	void eachStageIsToldOncePerTableAndDay() {
		HeatNotices n = new HeatNotices();
		List<HeatNotices.Notice> first = n.due("t1", "poker", true, stages(A, HeatStage.HARD_ONLY), 5);
		assertEquals(1, first.size());
		assertEquals("msg.burmaldaholic.bots.word_got_around", first.getFirst().message());
		assertEquals("word_got_around", first.getFirst().quip());
		// every later safe point of the day (also a new bot session: stand up, sit down) stays quiet
		for (int i = 0; i < 5; i++) {
			assertTrue(n.due("t1", "poker", true, stages(A, HeatStage.HARD_ONLY), 5).isEmpty());
		}
		// the next stage is news
		List<HeatNotices.Notice> sulk = n.due("t1", "poker", true, stages(A, HeatStage.SULKING), 5);
		assertEquals(List.of("msg.burmaldaholic.bots.sulking"), sulk.stream().map(HeatNotices.Notice::message).toList());
		assertEquals("sulk", sulk.getFirst().quip());
		assertTrue(n.due("t1", "poker", true, stages(A, HeatStage.SULKING), 5).isEmpty());
		assertTrue(n.due("t1", "poker", true, stages(A, HeatStage.HARD_ONLY), 5).isEmpty(), "never back down to an older line");
		// another table hears it once too; a new day starts over
		assertEquals(1, n.due("t2", "poker", true, stages(A, HeatStage.SULKING), 5).size());
		assertEquals(1, n.due("t1", "poker", true, stages(A, HeatStage.SULKING), 6).size());
	}

	@Test
	void onlyHouseMoneyTablesAndWordGotAroundOnlyAtPoker() {
		HeatNotices n = new HeatNotices();
		assertTrue(n.due("t", "poker", false, stages(A, HeatStage.SULKING), 1).isEmpty(), "owner-funded / atmosphere bots: no heat");
		assertTrue(n.due("c", "chemmy", true, stages(A, HeatStage.HARD_ONLY), 1).isEmpty(), "chemin de fer is unaffected by the first stage");
		assertEquals(1, n.due("c", "chemmy", true, stages(A, HeatStage.SULKING), 1).size(), "...but the bots sulk there too");
		assertTrue(n.due("t", "poker", true, stages(A, HeatStage.NONE, B, null), 1).isEmpty());
	}

	@Test
	void resetForgetsOnlyThatPlayer() {
		HeatNotices n = new HeatNotices();
		n.due("t", "poker", true, stages(A, HeatStage.SULKING, B, HeatStage.SULKING), 1);
		n.reset(A);
		List<HeatNotices.Notice> again = n.due("t", "poker", true, stages(A, HeatStage.SULKING, B, HeatStage.SULKING), 1);
		assertEquals(List.of(A), again.stream().map(HeatNotices.Notice::player).toList());
	}

	/** The quip goes through the table's chatter queue: with the table's Chatter toggle off it is dropped. */
	@Test
	void heatQuipsObeyTheChatterToggle() {
		ChatterQueue q = new ChatterQueue();
		BotRng rng = BotRng.seeded(1);
		for (String quip : List.of("sulk", "word_got_around")) {
			assertEquals(null, q.offer("bot", BotDifficulty.NORMAL, quip, "Steve", 100, new ChatterQueue.Config(false, 1, 0, 0, 99), rng),
				quip + " with Chatter off");
		}
		assertTrue(q.offer("bot", BotDifficulty.NORMAL, "sulk", "Steve", 100, new ChatterQueue.Config(true, 1, 0, 0, 99), rng) != null,
			"on: an explanatory line always fires");
	}
}

package dev.nezo.burmaldaholic.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.bots.logic.AdvancementRules;
import dev.nezo.burmaldaholic.bots.logic.ChatterGate;
import dev.nezo.burmaldaholic.bots.logic.CommandArgs;
import dev.nezo.burmaldaholic.bots.logic.HeatStage;
import dev.nezo.burmaldaholic.bots.logic.InviteBook;
import dev.nezo.burmaldaholic.bots.logic.Quips;
import dev.nezo.burmaldaholic.bots.logic.SeatPoints;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotEconomyMath;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Heat (BOTS.md §5.4 / §12.5), chatter limits (§7.4 / §12.6), quips (§11.4), invites, avatars, commands, advancements (§10). */
class BotsLogicTest {
	private static final UUID A = new UUID(0, 1);
	private static final UUID B = new UUID(0, 2);
	private static final UUID C = new UUID(0, 3);

	@Test
	void heatVectors() {
		// §12.5: Gold player (tier max 1 000) → threshold 5 000; +5 010 → Word got around; +10 000 → sulking
		long cap = BotEconomyMath.dailyCap(1000, 500, 5);
		assertEquals(5000, cap);
		assertEquals(HeatStage.NONE, HeatStage.of(4999, cap, 2.0));
		assertEquals(HeatStage.WORD_GOT_AROUND, HeatStage.of(5010, cap, 2.0));
		assertEquals(HeatStage.WORD_GOT_AROUND, HeatStage.of(9999, cap, 2.0));
		assertEquals(HeatStage.SULKING, HeatStage.of(10000, cap, 2.0));
		assertEquals(HeatStage.NONE, HeatStage.of(1_000_000, 0, 2.0)); // heat disabled
		assertEquals(HeatStage.NONE, HeatStage.of(-800, cap, 2.0)); // losses are never limited
		assertEquals(24000, HeatStage.ticksToNextDay(48000));
		assertEquals(1, HeatStage.ticksToNextDay(23999));
		assertTrue(AdvancementRules.wordGotAround(HeatStage.SULKING));
		assertFalse(AdvancementRules.wordGotAround(HeatStage.NONE));
	}

	@Test
	void heatAnnouncedOncePerStagePerDay() {
		HeatStage.Watch w = new HeatStage.Watch();
		assertTrue(w.crossed(A, 3, HeatStage.WORD_GOT_AROUND));
		assertFalse(w.crossed(A, 3, HeatStage.WORD_GOT_AROUND));
		assertTrue(w.crossed(A, 3, HeatStage.SULKING));
		assertFalse(w.crossed(A, 3, HeatStage.WORD_GOT_AROUND));
		assertTrue(w.crossed(A, 4, HeatStage.WORD_GOT_AROUND)); // next MCD
		assertFalse(w.crossed(B, 4, HeatStage.NONE));
		w.reset(A);
		assertTrue(w.crossed(A, 4, HeatStage.WORD_GOT_AROUND));
	}

	private static final ChatterGate.Config CHATTER = new ChatterGate.Config(1.0, 600, 200, 3);

	@Test
	void chatterLimits() {
		// §12.6: a 30-minute bot-heavy session → ≤ 3 lines per table-minute and ≤ 1 per bot per 30 s
		ChatterGate gate = new ChatterGate();
		BotRng rng = BotRng.seeded(7);
		Map<String, Long> lastByBot = new HashMap<>();
		Map<Long, Integer> perMinute = new HashMap<>();
		for (long t = 0; t < 36_000; t += 5) {
			String bot = "bot:b" + (t / 5 % 5);
			long wait = gate.admit(bot, BotDifficulty.NORMAL, "win_big", t, CHATTER, rng);
			if (wait >= 0) {
				long at = t + wait;
				Long last = lastByBot.put(bot, at);
				assertTrue(last == null || at - last >= 600, "per-bot cooldown");
				perMinute.merge(at / 1200, 1, Integer::sum);
				assertTrue(gate.inWindow(t) <= 3);
			}
		}
		perMinute.values().forEach(n -> assertTrue(n <= 3, "per-minute cap"));
		assertTrue(perMinute.values().stream().mapToInt(Integer::intValue).sum() > 0);
	}

	@Test
	void explanatoryLinesAlwaysFireButQueue() {
		ChatterGate gate = new ChatterGate();
		BotRng rng = BotRng.seeded(1);
		ChatterGate.Config never = new ChatterGate.Config(0.0, 600, 200, 0);
		assertEquals(-1, gate.admit("bot:x", BotDifficulty.NORMAL, "win_big", 0, never, rng)); // chance 0, cap 0
		assertEquals(0, gate.admit("bot:x", BotDifficulty.NORMAL, "yield", 0, never, rng));
		long wait = gate.admit("bot:y", BotDifficulty.NORMAL, "sulk", 170, never, rng);
		assertEquals(30, wait); // queued for the table cooldown (≤ 40 t)
		assertEquals(-1, gate.admit("bot:z", BotDifficulty.NORMAL, "duel_decline", 180, never, rng)); // would wait 220 t > 40 → dropped
	}

	@Test
	void hardBotsTalkHalfAsOften() {
		int normal = 0;
		int hard = 0;
		BotRng rng = BotRng.seeded(99);
		ChatterGate.Config c = new ChatterGate.Config(0.5, 0, 0, 1000);
		ChatterGate g1 = new ChatterGate();
		ChatterGate g2 = new ChatterGate();
		for (int i = 0; i < 20_000; i++) {
			normal += g1.admit("bot:n", BotDifficulty.NORMAL, "win_big", i * 2000L, c, rng) >= 0 ? 1 : 0;
			hard += g2.admit("bot:h", BotDifficulty.HARD, "win_big", i * 2000L, c, rng) >= 0 ? 1 : 0;
		}
		assertEquals(0.5, normal / 20_000.0, 0.02);
		assertEquals(0.25, hard / 20_000.0, 0.02);
	}

	@Test
	void mutedPlayersHearNothing() {
		assertTrue(ChatterGate.hears(true, true, false, false, 49, 8));
		assertFalse(ChatterGate.hears(true, true, false, false, 81, 8));
		assertTrue(ChatterGate.hears(true, true, false, true, 10_000, 8)); // seated: always
		assertFalse(ChatterGate.hears(true, true, true, true, 0, 8)); // muted
		assertFalse(ChatterGate.hears(true, false, false, true, 0, 8)); // table toggle
		assertFalse(ChatterGate.hears(false, true, false, true, 0, 8)); // server switch
	}

	@Test
	void quipsExistInBothLanguages() throws IOException {
		int total = Quips.VARIANTS.values().stream().mapToInt(Integer::intValue).sum();
		assertEquals(70, total);
		Path root = Path.of(System.getProperty("burmaldaholic.projectDir", "."));
		for (String lang : new String[] {"en_us", "ru_ru"}) {
			JsonObject json = JsonParser.parseString(Files.readString(root.resolve("src/main/lang/bots/" + lang + ".json"))).getAsJsonObject();
			Quips.VARIANTS.forEach((event, n) -> {
				for (int i = 1; i <= n; i++) {
					assertTrue(json.has(Quips.key(event, i)), lang + " " + Quips.key(event, i));
				}
				assertFalse(json.has(Quips.key(event, n + 1)), lang + " extra variant " + event);
			});
		}
		BotRng rng = BotRng.seeded(5);
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			seen.add(Quips.pick("duel_decline", rng));
		}
		assertEquals(Set.of(Quips.key("duel_decline", 1), Quips.key("duel_decline", 2)), seen);
		assertNull(Quips.pick("nope", rng));
		assertEquals(Quips.Emote.HAPPY, Quips.emote("win_big"));
		assertEquals(Quips.Emote.ANGRY, Quips.emote("bust"));
		assertEquals(Quips.Emote.NOTE, Quips.emote("join"));
		assertEquals(Quips.Emote.SMOKE, Quips.emote("leave"));
	}

	@Test
	void russianButtonLabelsFit() throws IOException {
		// §12.6: RU button labels ≤ 24 characters
		Path root = Path.of(System.getProperty("burmaldaholic.projectDir", "."));
		JsonObject ru = JsonParser.parseString(Files.readString(root.resolve("src/main/lang/bots/ru_ru.json"))).getAsJsonObject();
		for (String key : new String[] {"gui.burmaldaholic.bots.settings.open", "gui.burmaldaholic.bots.private.open",
			"gui.burmaldaholic.bots.private.invite", "gui.burmaldaholic.bots.private.uninvite", "gui.burmaldaholic.bots.pvp.play_vs_bots",
			"gui.burmaldaholic.bots.menu.mute", "gui.burmaldaholic.bots.menu.unmute", "gui.burmaldaholic.bots.menu.tab",
			"gui.burmaldaholic.bots.private.invite_submit", "gui.burmaldaholic.bots.private.uninvite_submit", "gui.burmaldaholic.bots.settings.save_short"}) {
			assertNotNull(ru.get(key), key);
			assertTrue(ru.get(key).getAsString().length() <= 24, key);
		}
	}

	@Test
	void invitesRememberTheInviter() {
		InviteBook book = new InviteBook();
		book.record("t", A, B);
		book.record("t", A, A); // self: ignored
		assertEquals(A, book.inviterOf("t", B));
		assertEquals(Set.of(B), book.invitedBy("t", A));
		assertTrue(book.playsWithInvitee("t", A, Set.of(A, B)));
		assertFalse(book.playsWithInvitee("t", A, Set.of(A, C)));
		assertFalse(book.playsWithInvitee("t", B, Set.of(A, B))); // the invitee did not invite anyone
		assertFalse(book.playsWithInvitee("other", A, Set.of(A, B)));
		book.remove("t", B);
		assertNull(book.inviterOf("t", B));
		assertTrue(AdvancementRules.membersOnly(true, true));
		assertFalse(AdvancementRules.membersOnly(false, true));
	}

	@Test
	void noRobots() {
		assertTrue(AdvancementRules.noRobots(SeatPolicy.HUMANS_ONLY, 4, 0));
		assertFalse(AdvancementRules.noRobots(SeatPolicy.HUMANS_ONLY, 3, 0));
		assertFalse(AdvancementRules.noRobots(SeatPolicy.MIXED, 5, 0));
		assertFalse(AdvancementRules.noRobots(SeatPolicy.HUMANS_ONLY, 4, 1)); // bots still leaving
	}

	@Test
	void seatPointsOnThePlayersSide() {
		int[][] facings = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
		for (int[] f : facings) {
			for (int seats = 1; seats <= 8; seats++) {
				Set<String> distinct = new HashSet<>();
				for (int i = 0; i < seats; i++) {
					double[] o = SeatPoints.offset(i, seats, f[0], f[1]);
					assertEquals(SeatPoints.RADIUS, Math.hypot(o[0], o[1]), 1e-9);
					assertTrue(o[0] * f[0] + o[1] * f[1] > 0, "on the facing side");
					distinct.add(Math.round(o[0] * 1000) + "," + Math.round(o[1] * 1000));
				}
				assertEquals(seats, distinct.size());
			}
		}
	}

	@Test
	void tableBotsCommand() {
		assertEquals(SeatPolicy.HUMANS_ONLY, CommandArgs.policy("humans"));
		assertEquals(SeatPolicy.MIXED, CommandArgs.policy("MIXED"));
		assertEquals(SeatPolicy.BOTS_ONLY, CommandArgs.policy("bots"));
		assertNull(CommandArgs.policy("robots"));
		assertEquals(BotDifficulty.HARD, CommandArgs.difficulty("hard"));
		assertNull(CommandArgs.difficulty("insane"));
		BotSettings none = new BotSettings(SeatPolicy.HUMANS_ONLY, 0, BotDifficulty.NORMAL, true, false, BotSpeed.FAST);
		BotSettings s = CommandArgs.apply(none, SeatPolicy.MIXED, null, null);
		assertEquals(new BotSettings(SeatPolicy.MIXED, 1, BotDifficulty.NORMAL, true, false, BotSpeed.FAST), s);
		assertEquals(3, CommandArgs.apply(s, SeatPolicy.BOTS_ONLY, 3, BotDifficulty.EASY).count());
		assertEquals(BotDifficulty.EASY, CommandArgs.apply(s, SeatPolicy.BOTS_ONLY, 3, BotDifficulty.EASY).difficulty());
		assertEquals(0, CommandArgs.apply(none, SeatPolicy.HUMANS_ONLY, null, null).count());
	}
}

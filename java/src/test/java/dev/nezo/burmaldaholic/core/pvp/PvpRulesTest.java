package dev.nezo.burmaldaholic.core.pvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.pvp.logic.CoinChain;
import dev.nezo.burmaldaholic.core.pvp.logic.DecisionView;
import dev.nezo.burmaldaholic.core.pvp.logic.HeadToHead;
import dev.nezo.burmaldaholic.core.pvp.logic.LobbyRules;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpBotRules;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpPlayerRecord;
import dev.nezo.burmaldaholic.core.pvp.logic.Rivalry;
import dev.nezo.burmaldaholic.core.pvp.logic.Settlement;
import dev.nezo.burmaldaholic.core.pvp.logic.Settlement.Seat;
import dev.nezo.burmaldaholic.core.pvp.logic.Taunts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PVP.md §16.1 C8–C13, §16.2 K2–K5, §16.8 B4/B5 (pure engine rules). */
class PvpRulesTest {
	private static final UUID A = new UUID(0, 1);
	private static final UUID B = new UUID(0, 2);
	private static final UUID C = new UUID(0, 3);

	// ---- Coin Flip Duel chain -----------------------------------------------------------------------

	/** K2: S = 100, the loser keeps losing → stakes 100, 100, 200, 400, 800; no 6th offer with maxDoubles 4. */
	@Test
	void chainStakesK2() {
		List<Long> stakes = new ArrayList<>();
		long stake = 100;
		CoinChain ch = null;
		for (int link = 1; ; link++) {
			stakes.add(stake);
			ch = CoinChain.after(ch, link, 0, stake, 4);
			if (ch.over()) {
				break;
			}
			stake = ch.nextStake();
		}
		assertEquals(List.of(100L, 100L, 200L, 400L, 800L), stakes);
		assertEquals(1600, ch.deficit());
	}

	/** K3 (B, B, B, A: A −24, B −24, rake 48) and K4 (B, then A wins the DoN: A −6, B −6). */
	@Test
	void workedChainsK3K4() {
		long[] k3 = playChain(new int[] {1, 1, 1, 0});
		assertEquals(-24, k3[0]);
		assertEquals(-24, k3[1]);
		assertEquals(48, k3[2]);
		long[] k4 = playChain(new int[] {1, 0});
		assertEquals(-6, k4[0]);
		assertEquals(-6, k4[1]);
	}

	/** Plays flips won by {@code winners[k]} (A = 0, B = 1), S = 100, 300 bp; returns {A net, B net, rake}. */
	private static long[] playChain(int[] winners) {
		long a = 0;
		long b = 0;
		long rake = 0;
		long stake = 100;
		CoinChain ch = null;
		for (int k = 0; k < winners.length; k++) {
			Settlement.Result r = Settlement.settle(List.of(Seat.human(stake), Seat.human(stake)), new int[] {winners[k]}, new int[] {0, 1}, 300,
				false);
			a += r.payouts()[0] - stake;
			b += r.payouts()[1] - stake;
			rake += r.rake();
			ch = CoinChain.after(ch, k + 1, 1 - winners[k], stake, 4);
			if (ch.over()) {
				assertEquals(winners.length - 1, k, "the chain ends only after the last flip");
				break;
			}
			stake = ch.nextStake();
		}
		return new long[] {a, b, rake};
	}

	/** K5: offer disabled when D exceeds a tier max or a balance. */
	@Test
	void offerBlockK5() {
		assertNull(CoinChain.offerBlock(200, new long[] {500, 500}, new long[] {1000, 200}));
		assertEquals("gui.burmaldaholic.pvp.coin.don_limit", CoinChain.offerBlock(600, new long[] {500, 5000}, new long[] {9999, 9999}));
		assertEquals("gui.burmaldaholic.pvp.coin.don_unaffordable", CoinChain.offerBlock(200, new long[] {500, 500}, new long[] {1000, 199}));
		assertTrue(CoinChain.after(null, 1, 0, 100, 0).over(), "maxDoubles 0: no doubling at all");
	}

	/**
	 * B4: Coin Flip Duel chains against Easy and Hard bots (decisions from the fallback rules): the human's mean
	 * net per chip staked equals minus the rake share whatever the bot does (Lemma 3).
	 */
	@Test
	void chainEvIndependentOfDifficultyB4() {
		for (BotDifficulty level : List.of(BotDifficulty.EASY, BotDifficulty.NORMAL, BotDifficulty.HARD)) {
			SplittableRandom fair = new SplittableRandom(42);
			BotRng bot = BotRng.seeded(7);
			long staked = 0;
			long net = 0;
			long rakeShare = 0;
			for (int chain = 0; chain < 300_000; chain++) {
				long stake = 100;
				CoinChain ch = null;
				for (int link = 1; ; link++) {
					int winner = fair.nextInt(2); // 0 = human, 1 = bot
					Settlement.Result r = Settlement.settle(List.of(Seat.human(stake), Seat.bankBot(stake)), new int[] {winner}, new int[] {0, 1},
						300, false);
					staked += stake;
					net += r.payouts()[0] - stake;
					rakeShare += r.rake();
					ch = CoinChain.after(ch, link, 1 - winner, stake, 4);
					if (ch.over()) {
						break;
					}
					// The chain loser offers, the winner rides — the bot's side per its level, the human always continues.
					int botSeat = 1;
					boolean botIsLoser = ch.loser() == botSeat;
					long choice = PvpBotRules.fallback(new DecisionView(botIsLoser ? "coin.don_offer" : "coin.let_it_ride", botSeat, link,
						ch.deficit(), 10, 0, stake, 0, 300), level, bot);
					if (choice == 0) {
						break;
					}
					stake = ch.nextStake();
				}
			}
			double perChip = net / (double) staked;
			double expected = -(rakeShare / 2.0) / staked;
			assertEquals(expected, perChip, 0.004, "level " + level);
		}
	}

	// ---- rivalry, streaks, grudge ---------------------------------------------------------------------

	/** C9: 3-player match A wins → A +1 W vs B and C; B, C +1 L vs A; B–C unchanged; split A = B → no A–B change. */
	@Test
	void rivalryC9() {
		Map<UUID, PvpPlayerRecord> recs = records(A, B, C);
		List<Rivalry.Player> ps = List.of(new Rivalry.Player(A, "A", 100), new Rivalry.Player(B, "B", 100), new Rivalry.Player(C, "C", 100));
		Rivalry.apply(ps, new long[] {291, 0, 0}, new int[] {0}, recs, new int[] {3, 5, 10});
		assertEquals(new HeadToHead(1, 0, 100, 1), recs.get(A).vs(B));
		assertEquals(new HeadToHead(1, 0, 100, 1), recs.get(A).vs(C));
		assertEquals(new HeadToHead(0, 1, -100, -1), recs.get(B).vs(A));
		assertEquals(HeadToHead.EMPTY, recs.get(B).vs(C));
		assertEquals(1, recs.get(A).wins);
		assertEquals(1, recs.get(C).losses);
		assertEquals(191, recs.get(A).net);
		// split A = B, C loses
		Rivalry.apply(ps, new long[] {146, 145, 0}, new int[] {0, 1}, recs, new int[] {3, 5, 10});
		assertEquals(1, recs.get(A).vs(B).wins(), "no A–B change on a split");
		assertEquals(0, recs.get(B).vs(A).wins());
		assertEquals(2, recs.get(A).vs(C).wins());
		assertEquals(1, recs.get(B).vs(C).wins());
		assertEquals(-2, recs.get(C).vs(A).run());
	}

	/** C10: streak call-outs exactly at 3, 5, 10; a loss at 4 → "broken" naming the breaker. */
	@Test
	void streakC10() {
		Map<UUID, PvpPlayerRecord> recs = records(A, B);
		List<Rivalry.Player> ps = List.of(new Rivalry.Player(A, "A", 10), new Rivalry.Player(B, "B", 10));
		List<Integer> tiersAt = new ArrayList<>();
		for (int i = 1; i <= 10; i++) {
			for (Rivalry.Callout c : Rivalry.apply(ps, new long[] {19, 0}, new int[] {0}, recs, new int[] {3, 5, 10})) {
				assertEquals(A, c.player());
				tiersAt.add(c.streak() * 10 + c.tier());
			}
		}
		assertEquals(List.of(30, 51, 102), tiersAt);
		recs = records(A, B);
		for (int i = 0; i < 4; i++) {
			Rivalry.apply(ps, new long[] {19, 0}, new int[] {0}, recs, new int[] {3, 5, 10});
		}
		List<Rivalry.Callout> broken = Rivalry.apply(ps, new long[] {0, 19}, new int[] {1}, recs, new int[] {3, 5, 10});
		assertEquals(1, broken.size());
		assertEquals(-1, broken.get(0).tier());
		assertEquals(4, broken.get(0).streak());
		assertEquals(1, broken.get(0).breaker());
		assertEquals(0, recs.get(A).streak);
	}

	/** Bots are never rivals: no rows, no streak change for a bot-only match, "vs bots" totals instead. */
	@Test
	void botsNeverRivals() {
		Map<UUID, PvpPlayerRecord> recs = records(A);
		recs.get(A).streak = 4;
		List<Rivalry.Player> ps = List.of(new Rivalry.Player(A, "A", 100), new Rivalry.Player(null, "bot", 100));
		assertTrue(Rivalry.apply(ps, new long[] {0, 194}, new int[] {1}, recs, new int[] {3, 5, 10}).isEmpty());
		assertEquals(4, recs.get(A).streak, "a bot-only match neither extends nor breaks the streak");
		assertTrue(recs.get(A).rivals().isEmpty());
		assertEquals(1, recs.get(A).botRounds);
		assertEquals(-100, recs.get(A).botNet);
		assertEquals(0, recs.get(A).losses);
	}

	/** C11: B lost the last 3 to A → next A–B 2-player match is a grudge match (B the underdog). */
	@Test
	void grudgeC11() {
		HeadToHead aVsB = HeadToHead.EMPTY.win(10).win(10).win(10);
		HeadToHead bVsA = HeadToHead.EMPTY.loss(10).loss(10).loss(10);
		assertEquals(1, Rivalry.grudgeUnderdog(aVsB, bVsA, 3));
		assertEquals(-1, Rivalry.grudgeUnderdog(HeadToHead.EMPTY.win(1), HeadToHead.EMPTY.loss(1), 3));
	}

	@Test
	void recordJsonRoundTripAndLru() {
		PvpPlayerRecord r = new PvpPlayerRecord();
		r.wins = 12;
		r.losses = 9;
		r.net = 340;
		r.streak = 2;
		r.acceptInvites = false;
		r.botRounds = 40;
		r.botNet = -120;
		for (int i = 0; i < 60; i++) {
			r.putRival(new UUID(1, i), "P" + i, new HeadToHead(i, 1, i * 10L, -1));
		}
		r.notes.add(new PvpPlayerRecord.Note("offline", "slots", -100));
		PvpPlayerRecord back = PvpPlayerRecord.fromJson(r.toJson());
		assertEquals(r.toJson(), back.toJson());
		assertEquals(PvpPlayerRecord.MAX_RIVALS, back.rivals().size());
		assertEquals(new UUID(1, 59), back.rivals().keySet().iterator().next(), "most recent first");
		assertFalse(back.rivals().containsKey(new UUID(1, 0)), "oldest dropped");
		assertFalse(back.acceptInvites);
		assertEquals(0, PvpPlayerRecord.fromJson("{}").wins);
		assertTrue(PvpPlayerRecord.fromJson("not json").acceptInvites);
		PvpPlayerRecord nem = new PvpPlayerRecord();
		nem.putRival(B, "B", new HeadToHead(1, 5, -420, -3));
		nem.putRival(C, "C", new HeadToHead(0, 1, -900, -1));
		assertEquals(B, nem.nemesis());
	}

	// ---- taunts, lobbies, bot timing ------------------------------------------------------------------

	/** C13: 6th taunt refused; 2 taunts within 100 t → the 2nd refused. */
	@Test
	void tauntsC13() {
		assertNull(Taunts.check(true, 4, 5, 0, 500, 100));
		assertEquals("gui.burmaldaholic.pvp.taunt.limit", Taunts.check(true, 5, 5, 0, 500, 100));
		assertEquals("gui.burmaldaholic.error.cooldown", Taunts.check(true, 1, 5, 450, 500, 100));
		assertNull(Taunts.check(true, 1, 5, Long.MIN_VALUE, 500, 100));
		assertEquals("gui.burmaldaholic.error.disabled", Taunts.check(false, 0, 5, Long.MIN_VALUE, 0, 100));
		assertEquals(8, Taunts.LINES.size());
		assertTrue(Taunts.friendly(0));
		assertFalse(Taunts.friendly(3));
	}

	/** C8: the host leaves with 2 others → the earliest (human) joiner hosts; host alone at the timer → cancel. */
	@Test
	void lobbyHostAndStartC8() {
		List<LobbyRules.Seat> seats = List.of(new LobbyRules.Seat("h", false), new LobbyRules.Seat("bot:x", true), new LobbyRules.Seat("j1", false),
			new LobbyRules.Seat("j2", false));
		assertEquals("j1", LobbyRules.nextHost(seats, "h"));
		assertNull(LobbyRules.nextHost(List.of(new LobbyRules.Seat("h", false), new LobbyRules.Seat("bot:x", true)), "h"));
		assertEquals(LobbyRules.Decision.CANCEL, LobbyRules.startRule(1, 6, false, true, 0));
		assertEquals(LobbyRules.Decision.START, LobbyRules.startRule(1, 6, false, true, 3));
		assertEquals(LobbyRules.Decision.START, LobbyRules.startRule(6, 6, false, false, 0));
		assertEquals(LobbyRules.Decision.WAIT, LobbyRules.startRule(2, 6, false, false, 0));
		assertEquals(LobbyRules.Decision.START, LobbyRules.startRule(2, 6, true, false, 0));
	}

	/** B5: MIXED, 6 seats, 2 humans at Start → 4 bots (cap permitting); the delayed fill keeps a seat free. */
	@Test
	void botFillB5() {
		assertEquals(4, LobbyRules.botsToSeat(SeatPolicy.MIXED, 7, 6, 2, 0, 7, true, true));
		assertEquals(3, LobbyRules.botsToSeat(SeatPolicy.MIXED, 7, 6, 2, 0, 3, true, true), "bots.pvp.maxPerMatch");
		assertEquals(3, LobbyRules.botsToSeat(SeatPolicy.MIXED, 7, 6, 2, 0, 7, true, false), "keep one seat free");
		assertEquals(0, LobbyRules.botsToSeat(SeatPolicy.HUMANS_ONLY, 7, 6, 2, 0, 7, true, true));
		assertEquals(1, LobbyRules.botsToSeat(SeatPolicy.BOTS_ONLY, 0, 2, 1, 0, 3, false, true), "a duel vs a bot");
		assertEquals(3, LobbyRules.botsToSeat(SeatPolicy.MIXED, 5, 6, 2, 2, 7, false, true), "count = total bots wanted");
	}

	/** B9: bots press within their window and never past half the step. */
	@Test
	void botPacingB9() {
		BotRng rng = BotRng.seeded(1);
		for (int i = 0; i < 1000; i++) {
			int e = PvpBotRules.pressTicks(rng, BotDifficulty.EASY, BotSpeed.NORMAL, 100);
			int h = PvpBotRules.pressTicks(rng, BotDifficulty.HARD, BotSpeed.NORMAL, 100);
			assertTrue(e >= 30 && e <= 40 && h >= 10 && h <= 15);
			assertTrue(PvpBotRules.pressTicks(rng, BotDifficulty.EASY, BotSpeed.NORMAL, 40) <= 20);
			assertEquals(0, PvpBotRules.pressTicks(rng, BotDifficulty.NORMAL, BotSpeed.INSTANT, 100));
			int a = PvpBotRules.acceptTicks(rng, BotSpeed.NORMAL);
			assertTrue(a >= 20 && a <= 60);
		}
	}

	/** BOTS.md §4.8 fallback decisions: Wheel Party stakes stay in [minStake, cap]. */
	@Test
	void wheelFallback() {
		BotRng rng = BotRng.seeded(3);
		DecisionView v = new DecisionView("wheel.stake", 2, 0, 0, 10, 500, 0, 120, 400);
		assertEquals(10, PvpBotRules.fallback(v, BotDifficulty.EASY, rng));
		assertEquals(120, PvpBotRules.fallback(v, BotDifficulty.NORMAL, rng));
		assertEquals(500, PvpBotRules.fallback(v, BotDifficulty.HARD, rng));
		DecisionView top = new DecisionView("wheel.top_up", 2, 0, 0, 10, 500, 500, 120, 40);
		assertEquals(0, PvpBotRules.fallback(top, BotDifficulty.EASY, rng), "no room under the cap");
	}

	private static Map<UUID, PvpPlayerRecord> records(UUID... ids) {
		Map<UUID, PvpPlayerRecord> m = new HashMap<>();
		for (UUID id : ids) {
			m.put(id, new PvpPlayerRecord());
		}
		return m;
	}
}

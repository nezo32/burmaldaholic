package dev.nezo.burmaldaholic.games.extras.pvp.coin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.pvp.logic.DecisionView;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMath;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** PVP.md §16.2 K1–K7 and §16.8 B1/B4 for Coin Flip Duel (same vectors as Bedrock). */
class CoinDuelModeTest {
	private static final CoinDuelMode MODE = new CoinDuelMode();
	private static final int RAKE_BP = 300;

	private static PvpRng fair(long seed) {
		return PvpRng.of(new SplittableRandom(seed));
	}

	/** Payouts of one flip between participants 0 and 1 at stake s (the engine's settlement, PvpMath). */
	private static long[] settle(long s, int winner) {
		long pot = 2 * s;
		long w = pot - PvpMath.rake(pot, RAKE_BP);
		return PvpMath.split(w, new int[] {winner}, new int[] {0, 1}, 2);
	}

	@Test
	void k1HeadsIsFair() {
		PvpRng rng = fair(0xC0105L);
		int n = 1_000_000;
		int heads = 0;
		CoinDuelMode.Params p = new CoinDuelMode.Params(100, true);
		for (int i = 0; i < n; i++) {
			if (MODE.draw(rng, 2, p).heads()) {
				heads++;
			}
		}
		assertEquals(0.5, heads / (double) n, 0.0015);
	}

	@Test
	void scoreFollowsTheSides() {
		CoinDuelMode.Params heads0 = new CoinDuelMode.Params(100, true);
		CoinDuelMode.Params tails0 = new CoinDuelMode.Params(100, false);
		CoinDuelMode.Tape headsUp = new CoinDuelMode.Tape(new int[] {1, 0}, true);
		Outcome o = MODE.score(headsUp, new long[] {100, 100}, heads0);
		assertArrayEquals(new int[] {0}, o.winners());
		assertArrayEquals(new int[] {0, 1}, o.rankOrder());
		assertArrayEquals(new long[] {1, 0}, o.points());
		assertArrayEquals(new int[] {1, 0}, o.seatOrder());
		assertEquals("landed", o.events().get(0).kind());
		assertEquals(1L, o.events().get(0).data().get("heads"));
		assertArrayEquals(new int[] {1}, MODE.score(headsUp, new long[] {100, 100}, tails0).winners());
		// No ties: exactly one winner whatever the tape
		for (boolean h : new boolean[] {true, false}) {
			assertEquals(1, MODE.score(new CoinDuelMode.Tape(new int[] {0, 1}, h), new long[] {5, 5}, heads0).winners().length);
		}
		assertThrows(IllegalArgumentException.class, () -> MODE.draw(fair(1), 3, heads0));
	}

	@Test
	void winnerTakesTwoStakesMinusRake() {
		// §3.5 example: 100 v 100 → pot 200, rake 6, winner +94
		long[] pay = settle(100, 1);
		assertEquals(0, pay[0]);
		assertEquals(194, pay[1]);
	}

	@Test
	void k2ChainStakesAndLimit() {
		CoinChain.State st = CoinChain.first(100, "A");
		List<Long> stakes = new ArrayList<>(List.of(100L));
		for (int i = 0; i < 4; i++) {
			assertNull(CoinChain.offerBlock(st, 4, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE), "offer after link " + st.link());
			stakes.add(st.nextStake());
			st = CoinChain.next(st, false);
		}
		assertEquals(List.of(100L, 100L, 200L, 400L, 800L), stakes);
		assertEquals(5, st.link());
		assertEquals(1600, st.deficit()); // D = 16S: chain over
		assertEquals(CoinChain.DON_LIMIT, CoinChain.offerBlock(st, 4, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
		for (int link = 1; link <= 5; link++) {
			assertEquals(stakes.get(link - 1), CoinChain.stakeOfLink(100, link));
		}
		// maxDoubles 0 = Double or nothing off
		assertEquals(CoinChain.DON_LIMIT, CoinChain.offerBlock(CoinChain.first(100, "A"), 0, 1000, 1000, 1000, 1000));
	}

	@Test
	void k3WorkedChain() {
		// §4.4: S = 100; flips B, B, B, A. A = participant 0 of every link (the chain loser calls), B = 1.
		long a = 0;
		long b = 0;
		long rake = 0;
		CoinChain.State st = null;
		int[] winners = {1, 1, 1, 0};
		for (int k = 0; k < winners.length; k++) {
			long s = st == null ? 100 : st.nextStake();
			long[] pay = settle(s, winners[k]);
			a += pay[0] - s;
			b += pay[1] - s;
			rake += 2 * s - pay[0] - pay[1];
			if (st == null) {
				st = CoinChain.first(s, winners[k] == 0 ? "B" : "A");
			} else {
				boolean loserWon = st.loser().equals(winners[k] == 0 ? "A" : "B");
				st = CoinChain.next(st, loserWon);
			}
		}
		assertEquals(-24, a);
		assertEquals(-24, b);
		assertEquals(48, rake);
		assertTrue(st.allSquare());
		assertEquals(CoinChain.DON_LIMIT, CoinChain.offerBlock(st, 4, 10_000, 10_000, 10_000, 10_000));
		CoinChain.State ended = st;
		assertThrows(IllegalStateException.class, () -> CoinChain.next(ended, true));
	}

	@Test
	void k4TwoFlipsAllSquare() {
		CoinChain.State st = CoinChain.first(100, "A"); // B won flip 1
		long[] f1 = settle(100, 1);
		long[] f2 = settle(st.nextStake(), 0); // A wins the double or nothing
		st = CoinChain.next(st, true);
		assertTrue(st.allSquare());
		assertEquals(-6, f1[0] + f2[0] - 200);
		assertEquals(-6, f1[1] + f2[1] - 200);
	}

	@Test
	void k5OfferNeedsTierMaxAndBalance() {
		CoinChain.State st = CoinChain.next(CoinChain.first(100, "A"), false); // D = 200
		assertNull(CoinChain.offerBlock(st, 4, 200, 200, 200, 200));
		assertEquals(CoinChain.DON_LIMIT, CoinChain.offerBlock(st, 4, 199, 1000, 1000, 1000));
		assertEquals(CoinChain.DON_LIMIT, CoinChain.offerBlock(st, 4, 1000, 199, 1000, 1000));
		assertEquals(CoinChain.DON_UNAFFORDABLE, CoinChain.offerBlock(st, 4, 1000, 1000, 199, 1000));
		assertEquals(CoinChain.DON_UNAFFORDABLE, CoinChain.offerBlock(st, 4, 1000, 1000, 1000, 199));
	}

	@Test
	void k6TimeoutDefaultsAreTheSafeOption() {
		// The engine answers a silent player with option 0: the loser walks away, the winner takes the money.
		assertEquals(0, CoinChain.WALK_AWAY);
		assertEquals(0, CoinChain.TAKE);
		assertFalse(CoinChain.continues(CoinChain.WALK_AWAY));
		assertTrue(CoinChain.continues(CoinChain.DON_HEADS));
		assertTrue(CoinChain.continues(CoinChain.DON_TAILS));
		assertTrue(CoinChain.calledHeads(CoinChain.DON_HEADS));
		assertFalse(CoinChain.calledHeads(CoinChain.DON_TAILS));
	}

	@Test
	void k7TapeIsSelfContained() {
		// Disconnect during the countdown: the flip settles from the persisted tape alone.
		CoinDuelMode.Params p = new CoinDuelMode.Params(250, false);
		CoinDuelMode.Tape t = MODE.draw(fair(77), 2, p);
		CoinDuelMode.Tape back = MODE.decodeTape(MODE.encodeTape(t));
		assertEquals(t.heads(), back.heads());
		assertArrayEquals(t.seatOrder(), back.seatOrder());
		assertArrayEquals(MODE.score(t, new long[] {250, 250}, p).winners(), MODE.score(back, new long[] {250, 250}, p).winners());
		assertEquals(p, MODE.decodeParams(MODE.encodeParams(p)));
		assertTrue(MODE.encodeTape(t).toString().length() < 100);
	}

	@Test
	void validateAndTimeline() {
		assertEquals("gui.burmaldaholic.pvp.error.stake_min", CoinDuelMode.validate(new CoinDuelMode.Params(9, true), 10));
		assertNull(CoinDuelMode.validate(new CoinDuelMode.Params(10, true), 10));
		CoinDuelMode.Params p = new CoinDuelMode.Params(100, true);
		CoinDuelMode.Tape t = new CoinDuelMode.Tape(new int[] {0, 1}, false);
		List<Step> steps = CoinDuelMode.timeline(t, MODE.score(t, new long[] {100, 100}, p), p, 60);
		assertEquals(List.of("countdown", "spin", "land"), steps.stream().map(Step::kind).toList());
		assertEquals(60, steps.get(0).ticks());
		assertTrue(steps.get(0).data().get("heads0").getAsBoolean());
		// the face is only in the land step (tape confidentiality)
		assertFalse(steps.get(0).data().has("heads"));
		assertFalse(steps.get(1).data().has("heads"));
		assertFalse(steps.get(2).data().get("heads").getAsBoolean());
		assertEquals(1, steps.get(2).data().get("winner").getAsInt());
	}

	private static DecisionView view(String decision) {
		return new DecisionView(decision, 1, 1, 100, 10, 0, 100, 0, 300);
	}

	@Test
	void botStylesBots48() {
		BotRng rng = BotRng.seeded(42);
		int n = 20_000;
		int[] don = new int[3];
		int[] ride = new int[3];
		int tails = 0;
		BotDifficulty[] levels = {BotDifficulty.EASY, BotDifficulty.NORMAL, BotDifficulty.HARD};
		for (int i = 0; i < n; i++) {
			for (int l = 0; l < 3; l++) {
				long d = MODE.botDecide(view(CoinChain.DON_OFFER), levels[l], rng);
				assertTrue(d == 0 || d == 1 || d == 2);
				if (d != 0) {
					don[l]++;
				}
				if (d == CoinChain.DON_TAILS) {
					tails++;
				}
				if (MODE.botDecide(view(CoinChain.LET_IT_RIDE), levels[l], rng) == CoinChain.RIDE) {
					ride[l]++;
				}
			}
		}
		assertEquals(n, don[0]);
		assertEquals(0.5, don[1] / (double) n, 0.02);
		assertEquals(0, don[2]);
		assertEquals(n, ride[0]);
		assertEquals(0.5, ride[1] / (double) n, 0.02);
		assertEquals(0, ride[2]);
		assertEquals(0.5, tails / (double) (don[0] + don[1]), 0.02);
		int sideTails = 0;
		for (int i = 0; i < n; i++) {
			sideTails += (int) MODE.botDecide(view(CoinChain.SIDE), BotDifficulty.HARD, rng);
		}
		assertEquals(0.5, sideTails / (double) n, 0.02);
		// MIXED never reaches a profile; if it does, it plays Steady
		assertNotEquals(-1, MODE.botDecide(view(CoinChain.DON_OFFER), BotDifficulty.MIXED, rng));
		assertThrows(IllegalArgumentException.class, () -> MODE.botDecide(view("coin.nope"), BotDifficulty.EASY, rng));
	}

	/** Result of one simulated chain for the human (participant key "H") against a bot ("B"). */
	private record ChainRun(long net, long staked, List<Boolean> faces) {}

	/**
	 * Plays one chain: flip 1 at stake s (human = challenger on Heads), then Double or nothing while both agree.
	 * The human decides with its own rng (50 %); the bot with {@link CoinDuelMode#botDecide}. The FAIR rng only draws tapes.
	 */
	private static ChainRun chain(PvpRng fairRng, BotRng botRng, SplittableRandom human, BotDifficulty level, long s, int maxDoubles) {
		long net = 0;
		long staked = 0;
		List<Boolean> faces = new ArrayList<>();
		// participant 0 = the side caller (human on the first link, the chain loser afterwards)
		String p0 = "H";
		boolean p0Heads = true;
		CoinChain.State st = null;
		while (true) {
			long stake = st == null ? s : st.nextStake();
			CoinDuelMode.Params params = new CoinDuelMode.Params(stake, p0Heads);
			CoinDuelMode.Tape tape = MODE.draw(fairRng, 2, params);
			faces.add(tape.heads());
			int w = MODE.score(tape, new long[] {stake, stake}, params).winners()[0];
			String winner = w == 0 ? p0 : (p0.equals("H") ? "B" : "H");
			long[] pay = settle(stake, 0);
			staked += stake;
			net += (winner.equals("H") ? pay[0] : 0) - stake;
			st = st == null ? CoinChain.first(stake, winner.equals("H") ? "B" : "H") : CoinChain.next(st, winner.equals(st.loser()));
			if (st.allSquare() || CoinChain.offerBlock(st, maxDoubles, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE) != null) {
				return new ChainRun(net, staked, faces);
			}
			String loser = st.loser();
			long call = loser.equals("H") ? human.nextInt(3) : MODE.botDecide(view(CoinChain.DON_OFFER), level, botRng);
			if (!CoinChain.continues(call)) {
				return new ChainRun(net, staked, faces);
			}
			long ride = loser.equals("H") ? MODE.botDecide(view(CoinChain.LET_IT_RIDE), level, botRng) : human.nextInt(2);
			if (ride != CoinChain.RIDE) {
				return new ChainRun(net, staked, faces);
			}
			p0 = loser;
			p0Heads = CoinChain.calledHeads(call);
		}
	}

	@Test
	void b1DifficultyNeverTouchesTheTapes() {
		// Same fair seed: the sequence of drawn faces is identical whatever the bots decide (their rng is separate).
		for (int round = 0; round < 3; round++) {
			List<Boolean> easy = new ArrayList<>();
			List<Boolean> hard = new ArrayList<>();
			PvpRng fe = fair(round);
			PvpRng fh = fair(round);
			CoinDuelMode.Params p = new CoinDuelMode.Params(100, true);
			for (int i = 0; i < 1000; i++) {
				MODE.botDecide(view(CoinChain.DON_OFFER), BotDifficulty.EASY, BotRng.seeded(i));
				easy.add(MODE.draw(fe, 2, p).heads());
				MODE.botDecide(view(CoinChain.DON_OFFER), BotDifficulty.HARD, BotRng.seeded(i * 31L));
				hard.add(MODE.draw(fh, 2, p).heads());
			}
			assertEquals(easy, hard);
		}
	}

	@Test
	void b4ChainEvIsMinusRakeShareForEveryStyle() {
		// 10⁶+ chains per style (+ more to keep the 3σ band inside the ±0.1 % tolerance): mean net per chip staked = −3 %.
		for (BotDifficulty level : new BotDifficulty[] {BotDifficulty.EASY, BotDifficulty.NORMAL, BotDifficulty.HARD}) {
			PvpRng fairRng = fair(0xB4L + level.ordinal());
			BotRng botRng = BotRng.seeded(0xB07L ^ level.ordinal());
			SplittableRandom human = new SplittableRandom(99 + level.ordinal());
			long net = 0;
			long staked = 0;
			for (int i = 0; i < 4_000_000; i++) {
				ChainRun r = chain(fairRng, botRng, human, level, 100, 4);
				net += r.net();
				staked += r.staked();
			}
			assertEquals(-0.03, net / (double) staked, 0.001, level.name());
		}
	}
}

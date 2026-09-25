package dev.nezo.burmaldaholic.games.uth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.games.uth.logic.Decision;
import dev.nezo.burmaldaholic.games.uth.logic.Paytables;
import dev.nezo.burmaldaholic.games.uth.logic.ReferenceStrategy;
import dev.nezo.burmaldaholic.games.uth.logic.SeatDecider;
import dev.nezo.burmaldaholic.games.uth.logic.UthBotPolicy;
import dev.nezo.burmaldaholic.games.uth.logic.UthBotPolicy.Option;
import dev.nezo.burmaldaholic.games.uth.logic.UthBotPolicy.RiverEstimate;
import dev.nezo.burmaldaholic.games.uth.logic.UthBotPolicy.RiverWork;
import dev.nezo.burmaldaholic.games.uth.logic.UthBotPolicy.View;
import dev.nezo.burmaldaholic.games.uth.logic.UthBotRules;
import dev.nezo.burmaldaholic.games.uth.logic.UthBotRules.Outcome;
import dev.nezo.burmaldaholic.games.uth.logic.UthBotRules.SeatResultFacts;
import dev.nezo.burmaldaholic.games.uth.logic.UthBotSim;
import dev.nezo.burmaldaholic.games.uth.logic.UthCards;
import dev.nezo.burmaldaholic.games.uth.logic.UthRound;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * UTH bots (BOTS.md §4.5, §4.6, §12.3): EASY rules, NORMAL / HARD = strategy R with the river enumeration as a
 * BotWork job and its deadline fallback, legalize, virtual bets, player-banked rules, virtual seats, quips,
 * RNG independence (the same deck with and without bots) and the published cost per level. Test vectors are
 * frozen reference values.
 */
class UthBotsTest {
	private static final Paytables P = Paytables.DEFAULT;

	private static int[] c(String s) {
		return s.isEmpty() ? new int[0] : UthCards.parseAll(s);
	}

	private static BotProfile bot(BotDifficulty level) {
		return bot(level, Personality.TAG);
	}

	private static BotProfile bot(BotDifficulty level, Personality p) {
		return new BotProfile("babcdefg", "creeper42", level, p);
	}

	private static View view(UthRound.Street street, String hole, String board, boolean allow3x) {
		List<Decision> ds = switch (street) {
			case PREFLOP -> allow3x ? List.of(Decision.CHECK, Decision.BET_3X, Decision.BET_4X) : List.of(Decision.CHECK, Decision.BET_4X);
			case FLOP -> List.of(Decision.CHECK, Decision.BET_2X);
			default -> List.of(Decision.FOLD, Decision.BET_1X);
		};
		List<Option> opts = new ArrayList<>();
		for (Decision d : ds) {
			opts.add(new Option(d, 10, true));
		}
		return new View(street, c(hole), c(board), 10, 0, opts, P);
	}

	private static View view(UthRound.Street street, String hole, String board) {
		return view(street, hole, board, true);
	}

	/** An rng that always returns {@code v} (and counts its draws). */
	private static final class Fixed implements BotRng {
		final double v;
		int calls;

		Fixed(double v) {
			this.v = v;
		}

		@Override
		public int nextInt(int bound) {
			calls++;
			return (int) Math.min(bound - 1, Math.floor(v * bound));
		}

		@Override
		public double nextDouble() {
			calls++;
			return v;
		}
	}

	private static final UthBotPolicy POL = UthBotPolicy.INSTANCE;

	// ---- EASY ----------------------------------------------------------------------------------------

	@Test
	void easyPreflopAnyPairOrAceScaredThreeX() {
		assertTrue(UthBotPolicy.easyPreflopBet4(c("2c 2d")));
		assertTrue(UthBotPolicy.easyPreflopBet4(c("Ah 3d")));
		assertFalse(UthBotPolicy.easyPreflopBet4(c("Kh Qh")));
		BotProfile b = bot(BotDifficulty.EASY);
		assertEquals(Decision.BET_3X, POL.act(b, view(UthRound.Street.PREFLOP, "Kh Qh", ""), null, new Fixed(0.1)));
		assertEquals(Decision.CHECK, POL.act(b, view(UthRound.Street.PREFLOP, "Kh Qh", ""), null, new Fixed(0.2)));
		// without ×3 on the table the scared bet is legalized to a check
		assertEquals(Decision.CHECK, POL.act(b, view(UthRound.Street.PREFLOP, "Kh Qh", "", false), null, new Fixed(0.1)));
		BotRng rng = BotRng.seeded(5);
		int n = 0;
		int total = 20_000;
		for (int i = 0; i < total; i++) {
			if (POL.decide(b, view(UthRound.Street.PREFLOP, "9c 4d", ""), null, rng) == Decision.BET_3X) {
				n++;
			}
		}
		assertEquals(UthBotPolicy.EASY_SCARED_3X, (double) n / total, 0.01);
	}

	@Test
	void easyFlopAnyPairIncludingBoardPairsOrFlushDraw() {
		assertTrue(UthBotPolicy.easyFlopBet2(c("4d 3h"), c("9s 9h Kc")), "board-only pair: the visible mistake");
		assertFalse(ReferenceStrategy.flopBet(c("4d 3h"), c("9s 9h Kc")), "R would check");
		assertTrue(UthBotPolicy.easyFlopBet2(c("4h 3h"), c("9h 7h Kc")), "any flush draw");
		assertTrue(UthBotPolicy.fourToFlush(c("4h 3c"), c("9h 7h Kh")));
		assertFalse(UthBotPolicy.fourToFlush(c("4c 3c"), c("9h 7h Kh")), "four hearts need a heart in the hole");
		assertFalse(UthBotPolicy.easyFlopBet2(c("4d 3c"), c("9h 7h Ks")));
	}

	@Test
	void easyRiverPairOrThirtyPercentCall() {
		assertTrue(UthBotPolicy.easyRiverBet1(c("4d 3c"), c("9h 9s Ks 7c 2d")));
		BotProfile b = bot(BotDifficulty.EASY);
		assertEquals(Decision.BET_1X, POL.act(b, view(UthRound.Street.RIVER, "4d 3c", "9h Ts Ks 7c 2d"), null, new Fixed(0.29)));
		assertEquals(Decision.FOLD, POL.act(b, view(UthRound.Street.RIVER, "4d 3c", "9h Ts Ks 7c 2d"), null, new Fixed(0.31)));
		BotRng rng = BotRng.seeded(6);
		int n = 0;
		int total = 20_000;
		for (int i = 0; i < total; i++) {
			if (POL.decide(b, view(UthRound.Street.RIVER, "4d 3c", "9h Ts Ks 7c 2d"), null, rng) == Decision.BET_1X) {
				n++;
			}
		}
		assertEquals(UthBotPolicy.EASY_RIVER_CALL, (double) n / total, 0.01);
	}

	@Test
	void easyNeedsNoHeavyWork() {
		assertNull(POL.work(bot(BotDifficulty.EASY), view(UthRound.Street.RIVER, "4d 3c", "9h Ts Ks 7c 2d"), BotRng.seeded(1)));
	}

	// ---- NORMAL / HARD = strategy R ------------------------------------------------------------------

	@Test
	void normalAndHardPlayStrategyROnEveryStreet() {
		SplittableRandom rnd = new SplittableRandom(77);
		SeatDecider ref = new ReferenceStrategy(P);
		SeatDecider.Context ctx = new SeatDecider.Context(true, true, Long.MAX_VALUE / 4);
		for (BotDifficulty level : List.of(BotDifficulty.NORMAL, BotDifficulty.HARD)) {
			SeatDecider pol = UthBotPolicy.decider(bot(level), BotRng.seeded(1234), P);
			for (int k = 0; k < 150; k++) {
				UthRound r = UthRound.deal(List.of(new UthRound.Entry(0, new UUID(0, 1), "", 1, 0, true)), UthCards.shuffledDeck(rnd::nextInt));
				UthRound.Seat s = r.seats().getFirst();
				while (!r.settled()) {
					Decision mine = pol.decide(r, s, ctx);
					assertEquals(ref.decide(r, s, ctx), mine, level + " " + r.street() + " deal " + k);
					// check the next street too: keep the seat in the round
					r.decide(s, r.street() == UthRound.Street.RIVER ? mine : Decision.CHECK, true);
					r.advance(P);
				}
			}
		}
	}

	@Test
	void normalAndHardNeverBetThreeX() {
		BotProfile b = bot(BotDifficulty.HARD);
		for (int i = 0; i < 52; i += 3) {
			for (int j = i + 1; j < 52; j += 5) {
				View v = new View(UthRound.Street.PREFLOP, new int[] {i, j}, new int[0], 10, 0,
					view(UthRound.Street.PREFLOP, "Ac Kd", "").options(), P);
				Decision d = POL.act(b, v, null, BotRng.seeded(i * 52L + j));
				assertTrue(d == Decision.BET_4X || d == Decision.CHECK);
				assertEquals(ReferenceStrategy.preflopBet(new int[] {i, j}), d == Decision.BET_4X);
			}
		}
	}

	@Test
	void normalAndHardDrawNothingFromTheirRng() {
		Fixed counting = new Fixed(0.5);
		POL.decide(bot(BotDifficulty.NORMAL), view(UthRound.Street.PREFLOP, "Kh 7d", ""), null, counting);
		POL.decide(bot(BotDifficulty.NORMAL), view(UthRound.Street.FLOP, "Kh 7d", "2c 9s Jd"), null, counting);
		POL.decide(bot(BotDifficulty.HARD), view(UthRound.Street.RIVER, "Kh 7d", "2c 9s Jd 4h 4s"), null, counting);
		assertEquals(0, counting.calls);
	}

	// ---- river enumeration as BotWork ------------------------------------------------------------------

	@Test
	void riverWorkVisitsAll990DealerHandsAndEqualsTheExactEv() {
		int[] hole = c("Kh 7d");
		int[] board = c("2c 9s Jd 4h 4s");
		RiverWork w = new RiverWork(hole, board, P);
		assertEquals(990, w.total());
		int steps = 0;
		while (!w.done()) {
			assertTrue(w.step(25) <= 25);
			steps++;
		}
		assertEquals((990 + 24) / 25, steps);
		assertEquals(990, w.progress());
		assertEquals(ReferenceStrategy.riverBetEv(hole, board, P), w.result().ev(), 1e-12);
		// random spots, flush boards included
		SplittableRandom rnd = new SplittableRandom(11);
		for (int t = 0; t < 200; t++) {
			int[] d = UthCards.shuffledDeck(rnd::nextInt);
			int[] h = {d[0], d[1]};
			int[] b = {d[2], d[3], d[4], d[5], d[6]};
			RiverWork x = new RiverWork(h, b, P);
			while (!x.done()) {
				x.step(2000);
			}
			assertEquals(ReferenceStrategy.riverBetEv(h, b, P), x.result().ev(), 1e-9);
			assertEquals(ReferenceStrategy.riverBet(h, b, P), UthBotPolicy.riverChoice(h, b, x.result()));
		}
	}

	@Test
	void aPartialRiverResultIsASpreadSample() {
		int[] hole = c("Kh 7d");
		int[] board = c("2c 9s Jd 4h 4s");
		RiverWork w = new RiverWork(hole, board, P);
		w.step(330);
		RiverEstimate part = w.result();
		assertEquals(330, part.samples());
		assertTrue(Math.abs(part.ev() - ReferenceStrategy.riverBetEv(hole, board, P)) < 0.15, "a third of the dealer hands, evenly spread");
	}

	@Test
	void workOnlyForNormalAndHardRivers() {
		assertNull(POL.work(bot(BotDifficulty.NORMAL), view(UthRound.Street.FLOP, "Kh 7d", "2c 9s Jd"), BotRng.seeded(1)));
		assertInstanceOf(RiverWork.class, POL.work(bot(BotDifficulty.HARD), view(UthRound.Street.RIVER, "Kh 7d", "2c 9s Jd 4h 4s"), BotRng.seeded(1)));
		assertInstanceOf(RiverWork.class, POL.work(bot(BotDifficulty.NORMAL), view(UthRound.Street.RIVER, "Kh 7d", "2c 9s Jd 4h 4s"), BotRng.seeded(1)));
	}

	@Test
	void deadlineFallsBackBelowFiftySamples() {
		assertTrue(UthBotPolicy.riverFallbackBet1(c("9d 3c"), c("9h Ts Ks 7c 2d")), "hole pair plays");
		assertFalse(UthBotPolicy.riverFallbackBet1(c("4d 3c"), c("9h 9s Ks 7c 2d")), "board pair only");
		int[] b = c("9h Ts Ks 7c 2d");
		assertFalse(UthBotPolicy.riverChoice(c("4d 3c"), b, new RiverEstimate(5, UthBotPolicy.RIVER_MIN_SAMPLES - 1, 990)));
		assertTrue(UthBotPolicy.riverChoice(c("4d 3c"), b, new RiverEstimate(5, UthBotPolicy.RIVER_MIN_SAMPLES, 990)));
		assertFalse(UthBotPolicy.riverChoice(c("4d 3c"), b, new RiverEstimate(-2.5, 990, 990)));
		assertFalse(UthBotPolicy.riverChoice(c("4d 3c"), b, null));
		assertFalse(UthBotPolicy.riverChoice(c("4d 3c"), b, "junk"));
		// the policy with a timed-out (null) job uses the fallback
		assertEquals(Decision.BET_1X, POL.act(bot(BotDifficulty.NORMAL), view(UthRound.Street.RIVER, "9d 3c", "9h Ts Ks 7c 2d"), null, BotRng.seeded(1)));
		assertEquals(Decision.FOLD, POL.act(bot(BotDifficulty.NORMAL), view(UthRound.Street.RIVER, "4d 3c", "9h 9s Ks 7c 2d"), null, BotRng.seeded(1)));
	}

	// ---- legalize --------------------------------------------------------------------------------------

	@Test
	void legalizeNeverReturnsAnUnofferedOrUnaffordableOption() {
		SplittableRandom rnd = new SplittableRandom(2026);
		Decision[] all = Decision.values();
		for (int i = 0; i < 100_000; i++) {
			UthRound.Street street = UthRound.Street.values()[rnd.nextInt(3)];
			List<Decision> ds = switch (street) {
				case PREFLOP -> List.of(Decision.CHECK, Decision.BET_3X, Decision.BET_4X);
				case FLOP -> List.of(Decision.CHECK, Decision.BET_2X);
				default -> List.of(Decision.FOLD, Decision.BET_1X);
			};
			List<Option> opts = new ArrayList<>();
			for (Decision d : ds) {
				opts.add(new Option(d, 1, !d.isBet() || rnd.nextBoolean()));
			}
			View v = new View(street, new int[] {0, 1}, new int[0], 1, 0, opts, P);
			Decision a = POL.legalize(v, all[rnd.nextInt(all.length)]);
			assertTrue(v.offers(a), street + " " + a);
		}
		assertEquals(Decision.CHECK, POL.legalize(view(UthRound.Street.FLOP, "2c 3d", "4h 5h 6h"), null));
	}

	@Test
	void viewShowsOnlyOwnHoleAndVisibleBoard() {
		int[] deck = UthCards.shuffledDeck(new SplittableRandom(9)::nextInt);
		UthRound r = UthRound.deal(List.of(new UthRound.Entry(0, new UUID(0, 1), "", 1, 0, true)), deck);
		UthRound.Seat s = r.seats().getFirst();
		r.decide(s, Decision.CHECK, true);
		r.advance(P);
		View v = View.of(r, s, 100, true, P);
		assertEquals(3, v.board().length);
		for (int d : r.dealerCards()) {
			for (int x : v.hole()) {
				assertTrue(x != d);
			}
			for (int x : v.board()) {
				assertTrue(x != d);
			}
		}
		assertEquals(45 * 44 / 2, new RiverWork(s.hole, r.board(), P).total(), "river pool = 52 − own hole − board");
	}

	// ---- virtual bets and pacing ------------------------------------------------------------------------

	@Test
	void virtualAnteByPersonalityTripsOnlyEasy() {
		BotRng rng = BotRng.seeded(3);
		for (Personality p : Personality.values()) {
			int[] u = UthBotRules.personalityUnits(p);
			for (int i = 0; i < 200; i++) {
				UthBotRules.VirtualBet v = UthBotRules.virtualBet(bot(BotDifficulty.NORMAL, p), rng, 5, 1000, true);
				assertTrue(v.ante() >= 5 + u[0] * 5L && v.ante() <= 5 + u[1] * 5L, p + " " + v.ante());
				assertEquals(0, v.trips());
			}
		}
		assertEquals(12, UthBotRules.virtualBet(bot(BotDifficulty.NORMAL, Personality.MANIAC), rng, 5, 12, true).ante());
		int trips = 0;
		for (int i = 0; i < 10_000; i++) {
			UthBotRules.VirtualBet v = UthBotRules.virtualBet(bot(BotDifficulty.EASY, Personality.ROCK), rng, 1, 100, true);
			if (v.trips() > 0) {
				trips++;
				assertEquals(v.ante(), v.trips(), "Trips = 1 × Ante");
			}
		}
		assertEquals(0.5, trips / 10_000.0, 0.02);
		assertEquals(0, UthBotRules.virtualBet(bot(BotDifficulty.EASY), new Fixed(0), 1, 100, false).trips());
	}

	@Test
	void betsLandSixtyToOneSixtyTicksIntoBetting() {
		BotRng rng = BotRng.seeded(4);
		for (int i = 0; i < 1000; i++) {
			int d = UthBotRules.virtualBetDelay(rng, BotSpeed.NORMAL, 0.5);
			assertTrue(d >= 60 && d <= 160);
		}
		assertTrue(UthBotRules.virtualBetDelay(rng, BotSpeed.FAST, 0.5) <= 80);
		assertEquals(0, UthBotRules.virtualBetDelay(rng, BotSpeed.INSTANT, 0.5));
	}

	// ---- player-banked tables -----------------------------------------------------------------------------

	@Test
	void botsWatchUnderAHumanBanker() {
		assertFalse(UthBotRules.botsDealtIn(true));
		assertTrue(UthBotRules.botsDealtIn(false));
	}

	@Test
	void standInOnlyForHouseRoundsWithBotsAllowed() {
		UthBotRules.StandInFacts base = new UthBotRules.StandInFacts(true, true, false, SeatPolicy.MIXED, true);
		assertTrue(UthBotRules.standInShown(base));
		assertFalse(UthBotRules.standInShown(new UthBotRules.StandInFacts(true, true, true, SeatPolicy.MIXED, true)));
		assertFalse(UthBotRules.standInShown(new UthBotRules.StandInFacts(true, true, false, SeatPolicy.MIXED, false)), "no bot banker without house rounds");
		assertFalse(UthBotRules.standInShown(new UthBotRules.StandInFacts(true, true, false, SeatPolicy.HUMANS_ONLY, true)));
		assertFalse(UthBotRules.standInShown(new UthBotRules.StandInFacts(false, true, false, SeatPolicy.MIXED, true)));
		assertFalse(UthBotRules.standInShown(new UthBotRules.StandInFacts(true, false, false, SeatPolicy.MIXED, true)));
		assertTrue(UthBotRules.standInOfferDue(10, 10));
		assertTrue(UthBotRules.standInOfferDue(20, 10));
		assertFalse(UthBotRules.standInOfferDue(9, 10));
		assertFalse(UthBotRules.standInOfferDue(10, 0));
	}

	// ---- virtual seats ---------------------------------------------------------------------------------------

	@Test
	void botsFillFromTheFarEndMoveAndDrop() {
		UthBotRules.BotSlots s = new UthBotRules.BotSlots();
		assertEquals(5, s.place("bot:a", 6, Set.of(0)));
		assertEquals(4, s.place("bot:b", 6, Set.of(0)));
		// a human sits in seat 6 (slot 5): the bot moves to slot 3
		assertEquals(List.of(), s.settle(6, Set.of(0, 5)));
		assertEquals(3, s.slotOf("bot:a"));
		assertEquals(4, s.slotOf("bot:b"));
		// table full of humans: the bots no longer fit
		List<String> gone = new ArrayList<>(s.settle(6, Set.of(0, 1, 2, 3, 4, 5)));
		gone.sort(null);
		assertEquals(List.of("bot:a", "bot:b"), gone);
		assertTrue(s.keys().isEmpty());
		assertNull(s.place("bot:c", 2, Set.of(0, 1)));
	}

	@Test
	void occupantsHumansAndBotsAtTheirSlots() {
		UthBotRules.BotSlots s = new UthBotRules.BotSlots();
		s.place("bot:a", 4, Set.of(0));
		assertEquals(java.util.Arrays.asList("H", null, null, "B"), s.occupants(4, Map.of(0, "H"), Map.of("bot:a", "B")));
	}

	// ---- chatter ---------------------------------------------------------------------------------------------

	@Test
	void roundQuipBlindWinOrBigHumanWin() {
		assertEquals(new UthBotRules.Quip("win_big", "bot:a"), UthBotRules.roundQuip(List.of(new SeatResultFacts("bot:a", true, Outcome.WIN, 5, true))));
		assertEquals(new UthBotRules.Quip("human_wins", "p1"), UthBotRules.roundQuip(List.of(new SeatResultFacts("p1", false, Outcome.WIN, 12, true))));
		assertNull(UthBotRules.roundQuip(List.of(new SeatResultFacts("p1", false, Outcome.WIN, 3, false))));
		assertEquals("p2", UthBotRules.roundQuip(List.of(new SeatResultFacts("p1", false, Outcome.WIN, 12, false),
			new SeatResultFacts("p2", false, Outcome.WIN, 20, false))).key(), "the biggest human win");
	}

	// ---- RNG independence (BOTS.md §12.1 / §12.3) ---------------------------------------------------------

	@Test
	void botSeatsAreDealtAfterTheBoard() {
		int[] deck = UthCards.shuffledDeck(new SplittableRandom(3)::nextInt);
		UUID h = new UUID(0, 1);
		UthRound r = UthRound.deal(List.of(new UthRound.Entry(3, new UUID(9, 1), "", 1, 0, true), new UthRound.Entry(0, h, "", 1, 0),
			new UthRound.Entry(5, new UUID(9, 2), "", 1, 0, true)), deck);
		assertArrayEquals(new int[] {deck[0], deck[2]}, r.seat(h).hole);
		assertArrayEquals(new int[] {deck[1], deck[3]}, r.dealerCards());
		assertArrayEquals(java.util.Arrays.copyOfRange(deck, 4, 9), r.board());
		assertArrayEquals(new int[] {deck[9], deck[10]}, r.seat(new UUID(9, 1)).hole);
		assertArrayEquals(new int[] {deck[11], deck[12]}, r.seat(new UUID(9, 2)).hole);
	}

	/** Plays {@code rounds} rounds from one game seed; returns every human's hole / dealer / board / net per round. */
	private static List<String> play(long seed, BotDifficulty level, boolean withBots) {
		SplittableRandom game = new SplittableRandom(seed); // the fair game rng (OddsService in production)
		BotRng botRng = BotRng.seeded(seed ^ 0xB07L); // the bot rng: never the game rng
		SeatDecider ref = new ReferenceStrategy(P);
		SeatDecider.Context humanCtx = new SeatDecider.Context(true, true, 1_000_000);
		SeatDecider.Context botCtx = new SeatDecider.Context(true, true, Long.MAX_VALUE / 4);
		UUID h1 = new UUID(0, 1);
		UUID h2 = new UUID(0, 3);
		List<String> out = new ArrayList<>();
		for (int k = 0; k < 1000; k++) {
			List<UthRound.Entry> entries = new ArrayList<>(List.of(new UthRound.Entry(0, h1, "", 5, 1), new UthRound.Entry(2, h2, "", 2, 0)));
			if (withBots) {
				entries.add(new UthRound.Entry(1, new UUID(7, 1), "", 10, 10, true));
				entries.add(new UthRound.Entry(5, new UUID(7, 2), "", 7, 0, true));
			}
			int[] deck = UthCards.shuffledDeck(game::nextInt);
			UthRound r = UthRound.deal(entries, deck);
			SeatDecider bots = UthBotPolicy.decider(bot(level), botRng, P);
			while (!r.settled()) {
				for (UthRound.Seat s : r.pendingSeats()) {
					r.decide(s, s.bot ? bots.decide(r, s, botCtx) : ref.decide(r, s, humanCtx), true);
				}
				r.advance(P);
			}
			StringBuilder b = new StringBuilder(java.util.Arrays.toString(r.dealerCards())).append(java.util.Arrays.toString(r.board()));
			for (UthRound.Seat s : r.seats()) {
				if (!s.bot) { // bots are never settled: only the humans' results count
					b.append('|').append(java.util.Arrays.toString(s.hole)).append(s.playMultiple).append(':').append(s.result.net());
				}
			}
			out.add(b.toString());
		}
		return out;
	}

	@Test
	void theSameDeckWithAndWithoutBotsGivesTheSameHumanRounds() {
		List<String> none = play(42, BotDifficulty.NORMAL, false);
		for (BotDifficulty level : List.of(BotDifficulty.EASY, BotDifficulty.NORMAL, BotDifficulty.HARD)) {
			assertEquals(none, play(42, level, true), level.name());
		}
	}

	// ---- cost per level (BOTS.md §4.5, §12.3) ------------------------------------------------------------

	private static final UthBotSim.Rules RULES = new UthBotSim.Rules(true, true, P);

	@Test
	void normalAndHardCostExactlyWhatStrategyRCosts() {
		UthBotSim.Stats r = UthBotSim.measure(new SplittableRandom(11)::nextInt, 40, null, RULES);
		UthBotSim.Stats n = UthBotSim.measure(new SplittableRandom(11)::nextInt, 40, BotDifficulty.NORMAL, RULES);
		UthBotSim.Stats h = UthBotSim.measure(new SplittableRandom(11)::nextInt, 40, BotDifficulty.HARD, RULES);
		assertEquals(40L * 1081, r.hands());
		assertEquals(r.edge(), n.edge(), 1e-12);
		assertEquals(r.edge(), h.edge(), 1e-12);
	}

	/** The board sampler's exact inner sums match a brute-force deal count on one board. */
	@Test
	void boardSamplerMatchesTheExactRiverCounts() {
		int[] board = c("2c 9s Jd 4h 4s");
		int[] n = new int[1];
		UthBotSim.forEachHand(board, P, h -> {
			if (n[0]++ % 97 == 0) {
				assertEquals(ReferenceStrategy.riverBetEv(h.hole(), board, P), h.ev(1), 1e-12);
			}
		});
		assertEquals(1081, n[0]);
	}

	/**
	 * EASY costs clearly more than R; the published figures are inside the measurement.
	 * {@code UTH_BOT_BOARDS=200000} reproduces the published EASY figure (SE ≈ 0.23 %); CI default 1 500.
	 */
	@Test
	void publishedCostPerLevel() {
		String env = System.getenv("UTH_BOT_BOARDS");
		int boards = env != null && env.matches("\\d+") ? Integer.parseInt(env) : 1500;
		UthBotSim.Stats easy = UthBotSim.measure(new SplittableRandom(20260925)::nextInt, boards, BotDifficulty.EASY, RULES);
		UthBotSim.Stats normal = UthBotSim.measure(new SplittableRandom(20260925)::nextInt, boards, BotDifficulty.NORMAL, RULES);
		System.out.printf("UTH bot cost: EASY %.3f %% ± %.3f, NORMAL %.3f %% ± %.3f (boards %d)%n", easy.edge() * 100, easy.se() * 100,
			normal.edge() * 100, normal.se() * 100, boards);
		assertTrue(easy.edge() > normal.edge() + 0.01);
		// same boards for both levels: the paired difference has a far smaller error than either figure
		double[] diff = new double[boards];
		for (int i = 0; i < boards; i++) {
			diff[i] = normal.boardMeans()[i] - easy.boardMeans()[i];
		}
		UthBotSim.Stats paired = new UthBotSim.Stats(1, 0, 0, diff);
		double extra = easy.edge() - normal.edge();
		double published = UthBotPolicy.edge(BotDifficulty.EASY) - UthBotPolicy.edge(BotDifficulty.NORMAL);
		System.out.printf("UTH bot cost: EASY − NORMAL %.3f %% ± %.3f (published %.2f %%)%n", extra * 100, paired.se() * 100, published * 100);
		assertTrue(Math.abs(extra - published) < Math.max(4 * paired.se(), 0.004), "EASY − NORMAL ≈ 17.1 %: " + extra);
		assertTrue(Math.abs(easy.edge() - UthBotPolicy.edge(BotDifficulty.EASY)) < Math.max(4 * easy.se(), 0.004), "EASY ≈ 19.4 %: " + easy.edge());
		assertTrue(Math.abs(normal.edge() - UthBotPolicy.edge(BotDifficulty.NORMAL)) < Math.max(4 * normal.se(), 0.0015), "NORMAL ≈ 2.27 %: " + normal.edge());
		// HARD's published figure is within the §12.3 tolerance of strategy R
		assertTrue(Math.abs(UthBotPolicy.edge(BotDifficulty.HARD) - UthBotPolicy.edge(BotDifficulty.NORMAL)) < 0.002);
		assertEquals("2.27 %", UthBotPolicy.edgePercent(BotDifficulty.NORMAL));
		assertEquals("2.19 %", UthBotPolicy.edgePercent(BotDifficulty.HARD));
		assertEquals("19.4 %", UthBotPolicy.edgePercent(BotDifficulty.EASY));
	}
}

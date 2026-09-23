package dev.nezo.burmaldaholic.games.poker.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.poker.logic.Hand.Action;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Hand state machine — same scenarios as the Bedrock engine tests. */
class HandTest {
	private static final Pots.RakeConfig RAKE = new Pots.RakeConfig(0.05, 3, true);
	private static final Pots.RakeConfig NO_RAKE = new Pots.RakeConfig(0, 0, true);
	private static final Action F = Action.fold(), X = Action.check(), C = Action.call(), A = Action.allIn();

	private static Action r(long to) {
		return Action.raiseTo(to);
	}

	/** Deck so that player i gets holes[i] and the board comes out as given (burn cards in between). */
	static int[] rig(int n, int button, String[] holes, String board) {
		int[][] h = Arrays.stream(holes).map(Cards::parseAll).toArray(int[][]::new);
		int[] b = Cards.parseAll(board);
		Set<Integer> used = new LinkedHashSet<>();
		for (int[] x : h) {
			for (int c : x) {
				used.add(c);
			}
		}
		for (int c : b) {
			used.add(c);
		}
		List<Integer> rest = new ArrayList<>();
		for (int c = 0; c < 52; c++) {
			if (!used.contains(c)) {
				rest.add(c);
			}
		}
		List<Integer> deck = new ArrayList<>();
		for (int round = 0; round < 2; round++) {
			for (int k = 1; k <= n; k++) {
				deck.add(h[(button + k) % n][round]);
			}
		}
		deck.addAll(List.of(rest.remove(0), b[0], b[1], b[2], rest.remove(0), b[3], rest.remove(0), b[4]));
		deck.addAll(rest);
		return deck.stream().mapToInt(Integer::intValue).toArray();
	}

	private record Opts(int button, long bb, String[] holes, String board, Pots.RakeConfig rake, boolean[] humans) {
		Opts() {
			this(0, 10, null, "2c 7d 9h Js Kd", NO_RAKE, null);
		}

		Opts button(int b) {
			return new Opts(b, bb, holes, board, rake, humans);
		}

		Opts cards(String board, String... holes) {
			return new Opts(button, bb, holes, board, rake, humans);
		}

		Opts rake(Pots.RakeConfig r) {
			return new Opts(button, bb, holes, board, r, humans);
		}

		Opts humans(boolean... h) {
			return new Opts(button, bb, holes, board, rake, h);
		}
	}

	private static Hand hand(Opts o, long... stacks) {
		List<Hand.Seed> seeds = new ArrayList<>();
		for (int i = 0; i < stacks.length; i++) {
			seeds.add(new Hand.Seed("p" + i, o.humans() == null || o.humans()[i], stacks[i]));
		}
		int[] deck = o.holes() == null ? null : rig(stacks.length, o.button(), o.holes(), o.board());
		return new Hand(seeds, PokerRng.of(new SplittableRandom(1)), new Hand.Options(o.bb() / 2, o.bb(), o.button(), o.rake(), deck));
	}

	private static Hand hand(long... stacks) {
		return hand(new Opts(), stacks);
	}

	private static void act(Hand h, Action... actions) {
		for (Action a : actions) {
			h.apply(a);
		}
	}

	private static long[] stacks(Hand h) {
		return h.players().stream().mapToLong(Hand.Player::stack).toArray();
	}

	private static long chipsIn(Hand h) {
		return Arrays.stream(stacks(h)).sum() + (h.result() == null ? 0 : h.result().rake());
	}

	private static List<Integer> board(String s) {
		return Arrays.stream(Cards.parseAll(s)).boxed().toList();
	}

	// ---- blinds and order -------------------------------------------------------------------------

	@Test
	void threeHandedBlindsAndFirstToAct() {
		Hand s = hand(1000, 1000, 1000);
		assertEquals(1, s.sbIndex());
		assertEquals(2, s.bbIndex());
		assertArrayEquals(new long[] {0, 5, 10}, s.players().stream().mapToLong(Hand.Player::bet).toArray());
		assertEquals(0, s.toAct());
		assertTrue(s.players().stream().allMatch(p -> p.hole().length == 2));
	}

	@Test
	void sixHandedActionStartsUnderTheGun() {
		Hand s = hand(new Opts().button(3), 500, 500, 500, 500, 500, 500);
		assertEquals(4, s.sbIndex());
		assertEquals(5, s.bbIndex());
		assertEquals(0, s.toAct());
	}

	@Test
	void headsUpButtonPostsSmallBlindAndActsFirstPreflop() {
		Hand s = hand(new Opts().button(1), 1000, 1000);
		assertEquals(1, s.sbIndex());
		assertEquals(0, s.bbIndex());
		assertEquals(1, s.toAct());
		act(s, C, X);
		assertEquals(Hand.Street.FLOP, s.street());
		assertEquals(0, s.toAct(), "BB acts first after the flop");
	}

	@Test
	void bigBlindHasTheOption() {
		Hand s = hand(1000, 1000, 1000);
		act(s, C, C);
		assertEquals(Hand.Street.PREFLOP, s.street());
		assertEquals(2, s.toAct());
		assertTrue(s.legal().canCheck());
		assertTrue(s.legal().canRaise());
		act(s, X);
		assertEquals(Hand.Street.FLOP, s.street());
		assertEquals(3, s.board().size());
		assertEquals(1, s.toAct());
	}

	@Test
	void shortBlindPostsAllIn() {
		Hand s = hand(1000, 3, 1000);
		assertEquals(3, s.player(1).bet());
		assertTrue(s.player(1).allIn());
		assertEquals(new Hand.Blind(1, false, 3, true), s.events().get(0));
	}

	@Test
	void burnsOneCardBeforeEachStreet() {
		Hand s = hand(new Opts().cards("2c 7d 9h Js 3d", "As Ad", "Ks Kd", "Qs Qd"), 1000, 1000, 1000);
		assertArrayEquals(Cards.parseAll("As Ad"), s.player(0).hole());
		act(s, C, C, X);
		assertEquals(board("2c 7d 9h"), s.board());
		act(s, X, X, X);
		assertEquals(board("2c 7d 9h Js"), s.board());
		act(s, X, X, X);
		assertEquals(board("2c 7d 9h Js 3d"), s.board());
	}

	// ---- no-limit betting -------------------------------------------------------------------------

	@Test
	void minBetIsBigBlindAndMinRaiseIsLastFullIncrement() {
		Hand s = hand(1000, 1000, 1000);
		assertEquals(20, s.legal().minRaiseTo());
		assertThrows(IllegalStateException.class, () -> s.apply(r(15)));
		act(s, r(30));
		assertEquals(50, s.legal().minRaiseTo());
		act(s, r(80));
		assertEquals(130, s.legal().minRaiseTo());
		act(s, F, C);
		assertEquals(Hand.Street.FLOP, s.street());
		assertEquals(10, s.legal().minRaiseTo());
		assertTrue(s.legal().isBet());
	}

	@Test
	void shortAllInDoesNotReopenBetting() {
		Hand s = hand(1000, 1000, 1000, 1000);
		act(s, C, C, C, X);
		assertEquals(Hand.Street.FLOP, s.street());
		s.player(3).stack = 150;
		act(s, r(100), C, A); // p3 all-in 150: +50 < 100, not a full raise
		assertEquals(150, s.currentBet());
		assertEquals(0, s.toAct());
		assertTrue(s.legal().canRaise(), "p0 has not acted yet");
		act(s, C);
		assertEquals(1, s.toAct());
		assertFalse(s.legal().canRaise(), "p1 bet and faces only a short all-in");
		assertEquals(50, s.legal().toCall());
		assertThrows(IllegalStateException.class, () -> s.apply(r(300)));
		assertEquals(C, s.coerce(r(300)));
		act(s, C);
		assertFalse(s.legal().canRaise());
		act(s, C);
		assertEquals(Hand.Street.TURN, s.street());
	}

	@Test
	void fullRaiseAfterShortAllInReopens() {
		Hand s = hand(1000, 1000, 1000, 1000);
		act(s, C, C, C, X);
		s.player(3).stack = 150;
		act(s, r(100), C, A, r(400));
		assertEquals(1, s.toAct());
		assertTrue(s.legal().canRaise());
	}

	@Test
	void uncalledBetReturnsWhenEveryoneFolds() {
		Hand s = hand(1000, 1000, 1000);
		act(s, r(300), F, F);
		assertTrue(s.complete());
		assertTrue(s.result().uncontested());
		assertEquals(new Pots.Uncalled(0, 290), s.result().uncalled());
		assertArrayEquals(new long[] {1015, 995, 990}, stacks(s));
		assertEquals(List.of(), s.result().shown());
	}

	@Test
	void walkToTheBigBlind() {
		Hand s = hand(1000, 1000, 1000);
		act(s, F, F);
		assertArrayEquals(new long[] {1000, 995, 1005}, stacks(s));
	}

	@Test
	void coerceProducesLegalActions() {
		Hand s = hand(1000, 1000, 1000);
		assertEquals(F, s.coerce(X));
		assertEquals(r(20), s.coerce(r(5)));
		assertEquals(r(1000), s.coerce(r(99999)));
		act(s, C, C);
		assertEquals(X, s.coerce(F));
	}

	// ---- showdown and pots -------------------------------------------------------------------------

	@Test
	void bestHandWinsLosersMuckAggressorShowsFirst() {
		Hand s = hand(new Opts().cards("3c 8d 9h Js 4d", "As Ad", "Ks Kd", "7c 2h"), 1000, 1000, 1000);
		act(s, r(30), C, F);
		act(s, X, r(50), C);
		act(s, X, X, X, X);
		assertTrue(s.complete());
		Hand.Result res = s.result();
		assertEquals(1, res.pots().size());
		assertEquals(List.of(0), res.pots().get(0).winners());
		assertEquals(170, res.pots().get(0).amount());
		assertEquals(List.of(0), res.shown());
		assertArrayEquals(new long[] {1090, 920, 990}, stacks(s));
		assertEquals("pair", HandEvaluator.handName(res.values()[0]));
	}

	@Test
	void sidePots() {
		Hand s = hand(new Opts().button(2).cards("2c 7d 9h Js 3d", "As Ad", "Ks Kd", "Qs Qd"), 100, 1000, 1000);
		act(s, r(300), A, C);
		assertEquals(Hand.Street.FLOP, s.street());
		act(s, X, X, X, X, X, X);
		Hand.Result res = s.result();
		assertEquals(List.of(300L, 400L), res.pots().stream().map(Hand.PotResult::amount).toList());
		assertEquals(List.of(List.of(0), List.of(1)), res.pots().stream().map(Hand.PotResult::winners).toList());
		assertArrayEquals(new long[] {300, 1100, 700}, stacks(s));
		assertEquals(2100, chipsIn(s));
	}

	@Test
	void displayPotsOnlySplitAtAllInLevels() {
		Hand blinds = hand(1000, 1000, 1000);
		assertEquals(List.of(15L), blinds.displayPots(), "blinds are one pot, not side pots");
		Hand s = hand(new Opts().button(2), 100, 1000, 1000);
		act(s, r(300), A);
		assertEquals(List.of(210L, 200L), s.displayPots(), "main pot capped at the all-in, the rest is a side pot");
		act(s, C);
		assertEquals(List.of(300L, 400L), s.displayPots());
		assertEquals(700, s.potTotal());
	}

	@Test
	void splitPotOddChipOrder() {
		Hand s = hand(new Opts().cards("Ts Js Qd Kc Ah", "7c 2d", "3s 4d", "3h 4c"), 1000, 1000, 1000);
		act(s, C, C, X);
		act(s, X, X, X, X, X, X, X, X, X);
		Hand.PotResult pot = s.result().pots().get(0);
		assertEquals(List.of(1, 2, 0), pot.winners());
		assertArrayEquals(new long[] {10, 10, 10}, pot.shares());
	}

	@Test
	void oddChipWithTwoWinners() {
		Hand s = hand(new Opts().cards("5s 9h Tc Jd 4c", "As Ks", "Ad Kd", "2c 3h"), 1000, 1000, 7);
		act(s, C, C);
		act(s, X, X, X, X, X, X);
		Hand.Result res = s.result();
		assertEquals(21, res.pots().get(0).amount());
		assertEquals(List.of(1, 0), res.pots().get(0).winners());
		assertArrayEquals(new long[] {11, 10}, res.pots().get(0).shares());
		assertEquals(6, res.pots().get(1).amount());
		assertEquals(2007, chipsIn(s));
	}

	@Test
	void allInPreflopRunsOutTheBoardAndShowsEveryHand() {
		Hand s = hand(new Opts().cards("2c 7d 9h Js 3d", "As Ad", "Kh Kd"), 500, 500);
		act(s, A, C);
		assertTrue(s.complete());
		assertEquals(5, s.board().size());
		assertEquals(Set.of(0, 1), Set.copyOf(s.result().shown()));
		assertArrayEquals(new long[] {1000, 0}, stacks(s));
	}

	@Test
	void rakeRules() {
		Hand raked = hand(new Opts().rake(RAKE).cards("2c 7d 9h Js 3d", "As Ad", "Kh Kd"), 1000, 1000);
		act(raked, A, C);
		assertEquals(30, raked.result().rake(), "min(floor(2000 × 5 %), 3 × 10)");
		assertEquals(1970, raked.player(0).stack());

		Hand vsBot = hand(new Opts().rake(RAKE).humans(true, false).cards("2c 7d 9h Js 3d", "As Ad", "Kh Kd"), 1000, 1000);
		act(vsBot, A, C);
		assertEquals(0, vsBot.result().rake(), "a human facing a bot is never raked");

		Hand noFlop = hand(new Opts().rake(RAKE), 1000, 1000, 1000);
		act(noFlop, r(100), F, r(300));
		assertFalse(noFlop.complete());
		act(noFlop, F);
		assertFalse(noFlop.sawFlop());
		assertEquals(0, noFlop.result().rake());
	}

	@Test
	void eventsDescribeTheHand() {
		Hand s = hand(1000, 1000, 1000);
		act(s, r(30), C, F);
		List<Hand.Event> ev = s.events();
		assertEquals(new Hand.Acted(0, Hand.ActionType.RAISE, 30, false), ev.get(2));
		assertEquals(new Hand.Acted(1, Hand.ActionType.CALL, 25, false), ev.get(3));
		assertEquals(new Hand.Acted(2, Hand.ActionType.FOLD, 0, false), ev.get(4));
		assertTrue(ev.get(5) instanceof Hand.Dealt d && d.street() == Hand.Street.FLOP && d.cards().length == 3);
		act(s, r(40));
		assertEquals(new Hand.Acted(1, Hand.ActionType.BET, 40, false), s.events().get(6));
	}

	// ---- random play invariants ------------------------------------------------------------------

	@Test
	void chipsAreConservedOverRandomHands() {
		SplittableRandom random = new SplittableRandom(42);
		PokerRng rng = PokerRng.of(random);
		for (int n = 0; n < 20_000; n++) {
			int count = 2 + random.nextInt(8);
			long[] st = new long[count];
			List<Hand.Seed> seeds = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				st[i] = 1 + random.nextInt(400);
				seeds.add(new Hand.Seed("p" + i, random.nextDouble() < 0.6, st[i]));
			}
			Hand s = new Hand(seeds, rng, new Hand.Options(1, 2, random.nextInt(count), RAKE, null));
			int guard = 0;
			while (!s.complete()) {
				assertTrue(++guard < 500, "hand did not finish");
				Hand.Legal l = s.legal();
				double x = random.nextDouble();
				Action a = x < 0.15 ? F : x < 0.55 ? (l.canCheck() ? X : C) : x < 0.85 ? r(l.minRaiseTo() + random.nextInt(50)) : A;
				s.apply(s.coerce(a));
			}
			assertEquals(Arrays.stream(st).sum(), chipsIn(s));
			assertTrue(Arrays.stream(stacks(s)).allMatch(v -> v >= 0));
			long potSum = s.result().pots().stream().mapToLong(Hand.PotResult::amount).sum();
			long paid = s.result().pots().stream().mapToLong(p -> Arrays.stream(p.shares()).sum() + p.rake()).sum();
			assertEquals(potSum, paid);
			for (Hand.PotResult p : s.result().pots()) {
				assertTrue(p.rake() <= p.amount() / 20, "rake ≤ 5 %");
			}
			long netSum = Arrays.stream(s.result().net()).sum();
			assertEquals(-s.result().rake(), netSum, "net results sum to minus the rake");
		}
	}
}

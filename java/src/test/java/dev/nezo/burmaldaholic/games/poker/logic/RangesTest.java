package dev.nezo.burmaldaholic.games.poker.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.poker.logic.Hand.Action;
import dev.nezo.burmaldaholic.games.poker.logic.Ranges.HandStat;
import dev.nezo.burmaldaholic.games.poker.logic.Ranges.OpponentType;
import dev.nezo.burmaldaholic.games.poker.logic.Ranges.Spec;
import dev.nezo.burmaldaholic.games.poker.logic.Ranges.Tag;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

/** Public ranges and the opponent model (BOTS.md §4.3) — same vectors as Bedrock {@code ranges.test.ts}. */
class RangesTest {
	private static Hand deal(int n) {
		List<Hand.Seed> seeds = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			seeds.add(new Hand.Seed("p" + i, true, 1000));
		}
		return new Hand(seeds, PokerRng.of(new SplittableRandom(1)), new Hand.Options(5, 10, 0, Pots.RakeConfig.NONE, null));
	}

	private static void play(Hand h, Action... actions) {
		for (Action a : actions) {
			h.apply(a);
		}
	}

	@Test
	void rankedCombos() {
		int[] all = Ranges.rankedCombos();
		assertEquals(1326, all.length);
		Set<Integer> distinct = new HashSet<>();
		for (int c : all) {
			distinct.add(c);
		}
		assertEquals(1326, distinct.size());
		assertTrue(Ranges.handRankShare(Cards.parseAll("As Ah")) < 0.005);
		assertTrue(Ranges.handRankShare(Cards.parseAll("7s 2h")) > 0.9);
		assertTrue(Ranges.handRankShare(Cards.parseAll("Ks Qs")) < Ranges.handRankShare(Cards.parseAll("Ks Qh")));
	}

	@Test
	void preflopTags() {
		Hand s = deal(6); // button 0, SB 1, BB 2, UTG 3
		play(s, Action.call()); // p3 limps
		assertEquals(Tag.ANY, Ranges.publicTags(s)[3]);
		play(s, Action.raiseTo(30)); // p4 opens
		assertEquals(Tag.SMALL, Ranges.publicTags(s)[4]);
		play(s, Action.call()); // p5 calls
		assertEquals(Tag.CALL, Ranges.publicTags(s)[5]);
		play(s, Action.raiseTo(100)); // p0 3-bets
		assertEquals(Tag.MID, Ranges.publicTags(s)[0]);
		play(s, Action.fold(), Action.fold(), Action.fold()); // p1 p2 p3
		play(s, Action.raiseTo(300)); // p4 4-bets
		assertEquals(Tag.STRONG, Ranges.publicTags(s)[4]);
		assertEquals(3, Ranges.streetRaises(s));
		play(s, Action.call()); // p5 calls the 4-bet
		assertEquals(Tag.CALL, Ranges.publicTags(s)[5]);
		play(s, Action.allIn()); // p0 jams
		assertEquals(Tag.STRONG, Ranges.publicTags(s)[0]);
	}

	@Test
	void postflopTags() {
		Hand s = deal(3); // the button acts first preflop 3-handed
		play(s, Action.call(), Action.call(), Action.check()); // pot 30, flop
		assertEquals(Hand.Street.FLOP, s.street());
		play(s, Action.raiseTo(15)); // p1 bets ½ pot
		assertEquals(Tag.SMALL, Ranges.publicTags(s)[1]);
		play(s, Action.raiseTo(60)); // p2 raises
		assertEquals(Tag.STRONG, Ranges.publicTags(s)[2]);
		Hand t = deal(3);
		play(t, Action.call(), Action.call(), Action.check());
		play(t, Action.raiseTo(25)); // 25 into 30
		assertEquals(Tag.MID, Ranges.publicTags(t)[1]);
		Hand u = deal(3);
		play(u, Action.call(), Action.call(), Action.check());
		play(u, Action.raiseTo(45)); // overbet
		assertEquals(Tag.STRONG, Ranges.publicTags(u)[1]);
		assertEquals(Tag.SMALL, Tag.CALL.tighter(Tag.SMALL));
		assertEquals(Tag.STRONG, Tag.STRONG.tighter(Tag.ANY));
	}

	@Test
	void hardRangesExcludeLimpersAndScaleWithVpip() {
		assertEquals(new Spec(0, 1), Ranges.rangeOf(Tag.ANY, false));
		assertEquals(new Spec(0.08, 1), Ranges.rangeOf(Tag.ANY, true));
		assertEquals(new Spec(0, 0.45), Ranges.rangeOf(Tag.SMALL, false));
		assertEquals(0.5, Ranges.rangeOf(Tag.MID, true, 0.5, false).hi(), 1e-9, "twice as wide as the table");
		assertEquals(1, Ranges.rangeOf(Tag.SMALL, true, 0.9, false).hi(), 1e-9, "clamped");
		assertEquals(0.45, Ranges.rangeOf(Tag.SMALL, false, 0.9, false).hi(), 1e-9, "NORMAL never scales");
		assertEquals(0.12 * 0.4, Ranges.rangeOf(Tag.STRONG, true, 0.1, false).hi(), 1e-9);
		assertEquals(0.6, Ranges.rangeOf(Tag.CALL, true, 0.9, false).hi(), 1e-9, "a call stays 60 %");
		assertEquals(new Spec(0, 1), Ranges.rangeOf(Tag.ANY, true, 0.5, true), "a passive human limps big hands too");
	}

	private static List<HandStat> hands(int n, IntFunction<HandStat> f) {
		List<HandStat> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(f.apply(i));
		}
		return out;
	}

	@Test
	void opponentModel() {
		assertNull(Ranges.statsOf(hands(9, i -> new HandStat(true, false, 0, 1))), "needs 10 hands");
		List<HandStat> h = hands(50, i -> new HandStat(false, false, 0, 0));
		h.addAll(hands(50, i -> new HandStat(true, false, 0, 2)));
		Ranges.HumanStats st = Ranges.statsOf(h);
		assertEquals(50, st.hands(), "keeps the last 50");
		assertEquals(1, st.vpip(), 1e-9);
		assertEquals(OpponentType.STATION, Ranges.opponentType(Ranges.statsOf(hands(20, i -> new HandStat(i % 2 == 0, false, 0, 2)))));
		assertEquals(OpponentType.NIT, Ranges.opponentType(Ranges.statsOf(hands(20, i -> new HandStat(i == 0, false, 1, 1)))));
		assertEquals(OpponentType.MANIAC, Ranges.opponentType(Ranges.statsOf(hands(20, i -> new HandStat(i % 4 == 0, false, 4, 1)))));
		assertEquals(OpponentType.REGULAR, Ranges.opponentType(Ranges.statsOf(hands(20, i -> new HandStat(i % 4 == 0, false, 1, 1)))));
		assertEquals(OpponentType.REGULAR, Ranges.opponentType(null));
		List<HandStat> window = new ArrayList<>();
		for (int i = 0; i < 70; i++) {
			Ranges.record(window, new HandStat(true, false, 0, 0));
		}
		assertEquals(Ranges.MODEL_WINDOW, window.size());
	}

	@Test
	void handStatCountsPostflopAggression() {
		Hand s = deal(3);
		play(s, Action.call(), Action.call(), Action.check());
		play(s, Action.raiseTo(15), Action.raiseTo(60), Action.fold(), Action.call());
		HandStat p1 = Ranges.handStat(s, 1);
		assertEquals(new HandStat(true, false, 1, 1), p1);
		assertEquals(new HandStat(false, false, 1, 0), Ranges.handStat(s, 2), "the BB checked its option: no VPIP");
	}
}

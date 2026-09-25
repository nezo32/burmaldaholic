package dev.nezo.burmaldaholic.games.poker.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Clock;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Hold'em beat schedules (animation/cards.md §2.2, C1): storyboard times, publication order, honesty. */
class PokerBeatsTest {
	private static final PokerBeats.Pacing P = PokerBeats.Pacing.DEFAULT;

	@Test
	void dealFollowsTheStoryboard() {
		Timeline t = PokerBeats.deal(6, 1, P, 7);
		assertEquals(Clock.SHARED, t.beats().get(0).clock());
		// button 0–400, blinds at 400, hole cards from 600 every 150 ms clockwise from the small blind
		Beat first = PokerBeats.find(t, PokerBeats.DEAL, 1);
		assertNotNull(first);
		assertEquals(600, first.at());
		assertEquals(PokerBeats.DEAL_MS, first.dur());
		assertEquals(12, PokerBeats.started(t, PokerBeats.DEAL, 1e9));
		// "A 6-seat deal takes 1.8 s": the last card lands at 600 + 11 × 150 + 200
		int lastLand = 0;
		for (Beat b : t.beats()) if (b.kind().equals(PokerBeats.DEAL)) lastLand = Math.max(lastLand, b.end());
		assertEquals(600 + 11 * 150 + 200, lastLand);
		// every seat's pair lifts after ITS second card
		for (int p = 0; p < 6; p++) {
			Beat lift = PokerBeats.find(t, PokerBeats.LIFT, p);
			assertNotNull(lift);
			int second = -1;
			for (Beat b : t.beats()) if (b.kind().equals(PokerBeats.DEAL) && b.lane() == p && b.arg(0) == 1) second = b.end();
			assertEquals(second, lift.at());
		}
	}

	@Test
	void streetsPublishTheirCardsAtTheSlide() {
		Timeline flop = PokerBeats.street(1, P, 0);
		assertEquals(0, PokerBeats.boardPublished(flop, 599));
		assertEquals(1, PokerBeats.boardPublished(flop, 600));
		assertEquals(3, PokerBeats.boardPublished(flop, 900));
		Timeline turn = PokerBeats.street(2, P, 0);
		assertEquals(4, PokerBeats.boardPublished(turn, 600)); // slot 3 → 4 cards on the felt
		Timeline river = PokerBeats.street(3, P, 0);
		assertEquals(5, PokerBeats.boardPublished(river, 10_000));
		// flips come after the slides, left to right
		Beat f0 = PokerBeats.find(flop, PokerBeats.FLIP, 0);
		Beat f2 = PokerBeats.find(flop, PokerBeats.FLIP, 2);
		assertTrue(f0.at() >= PokerBeats.find(flop, PokerBeats.SLIDE, 2).end());
		assertEquals(f0.at() + 2 * PokerBeats.FLIP_STAGGER_MS, f2.at());
	}

	@Test
	void slowRunoutPausesTheSameBeforeEveryStreet() {
		Timeline t = PokerBeats.finish(new PokerBeats.Finish(true, 1, true, 2, 1), P, 0);
		List<Integer> sweat = new ArrayList<>();
		List<Integer> flipDur = new ArrayList<>();
		for (Beat b : t.beats()) {
			if (b.kind().equals(PokerBeats.SWEAT)) sweat.add(b.dur());
			if (b.kind().equals(PokerBeats.FLIP)) flipDur.add(b.dur());
		}
		assertEquals(List.of(1200, 1200, 1200), sweat);
		assertTrue(flipDur.stream().allMatch(d -> d == PokerBeats.RUNOUT_FLIP_MS));
		assertEquals(5, flipDur.size());
		// exposure before the first run-out card, the showdown after the river flip, the award last (the gate)
		Beat expose = PokerBeats.find(t, PokerBeats.EXPOSE, -1);
		Beat river = PokerBeats.find(t, PokerBeats.FLIP, 4);
		Beat show0 = PokerBeats.find(t, PokerBeats.SHOW, 0);
		Beat award = PokerBeats.find(t, PokerBeats.AWARD, 0);
		assertTrue(expose.at() < PokerBeats.find(t, PokerBeats.SLIDE, 0).at());
		assertTrue(show0.at() >= river.end());
		assertEquals(award.end(), t.sharedEndMs());
	}

	@Test
	void sidePotsAwardInOrderAndTheGateIsTheLastAward() {
		Timeline t = PokerBeats.finish(new PokerBeats.Finish(false, 0, false, 3, 3), P, 0);
		Beat a0 = PokerBeats.find(t, PokerBeats.AWARD, 0);
		Beat a1 = PokerBeats.find(t, PokerBeats.AWARD, 1);
		Beat a2 = PokerBeats.find(t, PokerBeats.AWARD, 2);
		assertEquals(PokerBeats.POT_SLIDE_MS + PokerBeats.POT_GAP_MS, a1.at() - a0.at());
		assertEquals(a2.end(), t.sharedEndMs());
		assertTrue(a0.at() > PokerBeats.find(t, PokerBeats.BEST, -1).at());
		// uncontested: no show, the pot slides at once
		Timeline u = PokerBeats.finish(new PokerBeats.Finish(false, 0, false, 0, 1), P, 0);
		assertEquals(0, PokerBeats.find(u, PokerBeats.AWARD, 0).at());
		assertEquals(0, PokerBeats.started(u, PokerBeats.SHOW, 1e9));
	}

	@Test
	void publicationsAreMonotonicAndEndAtTheGate() {
		for (Timeline t : List.of(PokerBeats.deal(9, 4, P, 1), PokerBeats.street(1, P, 2),
			PokerBeats.finish(new PokerBeats.Finish(true, 2, true, 3, 2), P, 3))) {
			double now = -1;
			int guard = 0;
			int next;
			while ((next = PokerBeats.nextPublication(t, now)) >= 0 && guard++ < 100) {
				assertTrue(next > now);
				now = next;
			}
			assertEquals(t.sharedEndMs(), (int) now);
		}
	}

	/**
	 * Honesty (§0.7.3): the schedule is built from counts only — two hands with different hidden cards but the same
	 * public facts get byte-identical timelines (the builder takes no card at all, so any permutation of the unrevealed
	 * cards maps to the same inputs). Checked here over random public facts: same facts ⇒ same JSON.
	 */
	@Test
	void timingDependsOnPublicCountsOnly() {
		Random rnd = new Random(5);
		for (int i = 0; i < 200; i++) {
			PokerBeats.Finish f = new PokerBeats.Finish(rnd.nextBoolean(), rnd.nextInt(4), rnd.nextBoolean(), rnd.nextInt(7), 1 + rnd.nextInt(4));
			assertEquals(PokerBeats.finish(f, P, 11).toCanonicalJson(),
				PokerBeats.finish(new PokerBeats.Finish(f.gather(), f.runoutFrom(), f.expose(), f.shows(), f.pots()), P, 11).toCanonicalJson());
		}
		// the run-out street length is the same for the turn and the river
		Timeline t = PokerBeats.finish(new PokerBeats.Finish(false, 2, false, 2, 1), P, 0);
		int turn = PokerBeats.find(t, PokerBeats.FLIP, 3).end() - PokerBeats.find(t, PokerBeats.SWEAT, 2).at();
		int river = PokerBeats.find(t, PokerBeats.FLIP, 4).end() - PokerBeats.find(t, PokerBeats.SWEAT, 3).at();
		assertEquals(turn, river);
	}

	@Test
	void bestFiveFindsTheCardsOfTheValue() {
		int[] hole = Cards.parseAll("Ah Kh");
		int[] board = Cards.parseAll("Qh Jh Th 2c 3d");
		int mask = BestFive.mask(hole, board);
		assertEquals(0b0011111, mask); // royal: both hole cards + the first three board cards
		int[] seven = Cards.parseAll("2c 2d 9s 9h Kc 5d 7s");
		int[] idx = BestFive.indices(seven);
		int[] five = new int[5];
		for (int i = 0; i < 5; i++) five[i] = seven[idx[i]];
		assertEquals(HandEvaluator.evaluate(seven), HandEvaluator.evaluate(five));
		assertArrayEquals(new int[] {0, 1, 2, 3, 4}, idx);
	}

	@Test
	void publishedArgsCarryThePacing() {
		PokerBeats.Pacing custom = new PokerBeats.Pacing(5, 10, 20, 14, 16, 40);
		int[] args = PokerBeats.withPacing(new int[] {1, 2, 1, 3, 2}, custom);
		assertEquals(5 + PokerBeats.Pacing.SIZE, args.length);
		assertEquals(custom, PokerBeats.pacingOf(args, 5));
		Timeline viaArgs = PokerBeats.segment("finish", args, 9);
		Timeline direct = PokerBeats.finish(new PokerBeats.Finish(true, 2, true, 3, 2), custom, 9);
		assertEquals(direct.beats(), viaArgs.beats());
		// args without the tail (older states): the defaults
		assertEquals(PokerBeats.Pacing.DEFAULT, PokerBeats.pacingOf(new int[] {3}, 1));
		assertEquals(PokerBeats.street(3, P, 4).beats(), PokerBeats.segment("street", new int[] {3}, 4).beats());
	}
}

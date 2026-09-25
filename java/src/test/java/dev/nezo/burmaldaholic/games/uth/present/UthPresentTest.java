package dev.nezo.burmaldaholic.games.uth.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.games.poker.present.LabelPlacer;
import dev.nezo.burmaldaholic.games.poker.present.LabelPlacer.Rect;
import dev.nezo.burmaldaholic.games.poker.present.PokerPub;
import dev.nezo.burmaldaholic.games.uth.logic.UthBeats;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UTH beats (animation/cards.md §3.2), layout (visual §6.5) and both public tags (BER). */
class UthPresentTest {
	@Test
	void dealOrderAndBoardSweep() {
		Timeline t = UthBeats.deal(3, 0);
		List<String> order = new ArrayList<>();
		for (Beat b : t.beats()) {
			if (b.kind().equals(UthBeats.DEAL)) order.add("s" + b.lane());
			if (b.kind().equals(UthBeats.DEALER_DEAL)) order.add("d");
		}
		assertEquals(List.of("s0", "s1", "s2", "d", "s0", "s1", "s2", "d"), order);
		int lastHole = 0;
		int firstBoard = Integer.MAX_VALUE;
		for (Beat b : t.beats()) {
			if (b.kind().equals(UthBeats.DEAL) || b.kind().equals(UthBeats.DEALER_DEAL)) lastHole = Math.max(lastHole, b.end());
			if (b.kind().equals(UthBeats.BOARD)) firstBoard = Math.min(firstBoard, b.at());
		}
		assertEquals(lastHole + 300, firstBoard);
		assertTrue(UthBeats.dealTicks(6) > UthBeats.dealTicks(1));
	}

	@Test
	void showdownQualifyPauseIsFixedAndSettlesCircleByCircle() {
		Timeline t = UthBeats.showdown(4, 0);
		Beat q = null;
		int settles = 0;
		for (Beat b : t.beats()) {
			if (b.kind().equals(UthBeats.QUALIFY)) q = b;
			if (b.kind().equals(UthBeats.SETTLE)) settles++;
		}
		assertEquals(600, q.at());
		assertEquals(16, settles);
		// the same schedule whatever the cards (input = seat count only), the gate after the last seat's Trips
		assertEquals(UthBeats.showdown(4, 0).sharedEndMs(), t.sharedEndMs());
		assertEquals(1500 + 3 * UthBeats.SEAT_STAGGER_MS + 3 * UthBeats.CIRCLE_STEP_MS + UthBeats.SETTLE_MS, t.sharedEndMs());
	}

	@Test
	void layoutMatchesTheMockupAndNothingOverlaps() {
		assertEquals(new Rect(10 + 204 - 12, 18 + 146 - 12, 24, 24), UthLayout.circle(0));
		assertEquals(34, UthLayout.circlePitch());
		for (int others = 0; others <= 5; others++) {
			List<Rect> cards = UthLayout.cardRects(others);
			for (int i = 0; i < cards.size(); i++) {
				for (int j = i + 1; j < cards.size(); j++) {
					assertFalse(cards.get(i).intersects(cards.get(j)) && !samePair(cards.get(i), cards.get(j)),
						others + " others: " + cards.get(i) + " × " + cards.get(j));
				}
			}
			// plates never cover another seat's cards or the viewer's circles
			UthLayout.Seat[] seats = UthLayout.others(others);
			for (UthLayout.Seat s : seats) {
				Rect plate = new Rect(s.plateX(), s.plateY(), 60, UthLayout.PLATE_H);
				for (UthLayout.Seat o : seats) if (o != s) assertFalse(plate.intersects(o.cards()), "plate over " + o);
				for (int c = 0; c < 4; c++) assertFalse(plate.intersects(UthLayout.circle(c)));
			}
			if (UthLayout.crowded(others)) {
				for (UthLayout.Seat s : seats) assertFalse(s.cards().intersects(UthLayout.deck(others)));
			} else {
				for (UthLayout.Seat s : seats) assertFalse(s.cards().intersects(UthLayout.paytable()));
			}
			// hand names never cover a card
			List<LabelPlacer.Request> reqs = new ArrayList<>();
			reqs.add(UthLayout.viewerTag(96));
			for (UthLayout.Seat s : seats) reqs.add(UthLayout.seatTag(s, 60, 90));
			for (Rect l : LabelPlacer.place(cards, List.of(), UthLayout.labelBounds(), reqs)) {
				for (Rect c : cards) assertFalse(l.intersects(c), l + " covers " + c);
			}
		}
	}

	private static boolean samePair(Rect a, Rect b) {
		return a.y() == b.y() && Math.abs(a.x() - b.x()) == 12 && a.w() == UthLayout.M_W;
	}

	@Test
	void pubRoundTripsAndRejectsGarbage() {
		UthPub p = new UthPub(3, UthPub.SHOWDOWN, 123_456_789_012L, -5, new int[] {1, 2, 3, 4, 5}, new int[] {6, 7}, new int[] {0, 2},
			new int[] {UthPub.flags(true, false, true, 4), UthPub.flags(false, true, false, 0)}, new long[] {3_000_000_000L, 20}, new int[] {8, 9, -1, -1});
		assertEquals(p, UthPub.decode(p.encode()));
		assertEquals(4, p.play(0));
		assertTrue(p.has(0, UthPub.BOT) && p.has(0, UthPub.WON) && p.has(1, UthPub.FOLDED));
		assertEquals(UthPub.EMPTY, UthPub.decode(new int[] {99}));
		assertEquals(UthPub.EMPTY, UthPub.decode(new int[0]));
		PokerPub q = new PokerPub(2, 3, 77L, new int[] {1, 1, 0, 2, 1}, 9, 4, new int[] {10, 11, 12}, 500, new int[] {PokerPub.SEATED | PokerPub.DEALT, 0},
			new long[] {20, 0}, new int[] {30, 31, -1, -1});
		assertEquals(q, PokerPub.decode(q.encode()));
		assertNotEquals(q, PokerPub.EMPTY);
		assertEquals(PokerPub.EMPTY, PokerPub.decode(new int[] {1, 2}));
		assertTrue(q.encode().length * 4 < 1024, "public tag ≤ 1 KB");
	}
}

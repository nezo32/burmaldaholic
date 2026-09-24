package dev.nezo.burmaldaholic.games.poker.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.poker.present.HoldemLayout.Slot;
import dev.nezo.burmaldaholic.games.poker.present.LabelPlacer.Rect;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Hold'em layout (visual/cards.md §6.4): anchors, seat rotation, button path, and labels that never cover cards. */
class HoldemLayoutTest {
	@Test
	void mockupAnchors() {
		// table-local anchors of the spec + the table origin (10, 18)
		assertEquals(new Rect(116, 74, 37, 49), HoldemLayout.board(0));
		assertEquals(new Rect(276, 74, 37, 49), HoldemLayout.board(4));
		Slot viewer = HoldemLayout.slotOf(3, 3, 6);
		assertEquals(0, viewer.id());
		assertEquals(174, viewer.cardsX());
		assertEquals(136, viewer.cardsY());
		assertTrue(viewer.large());
		// clockwise: the next seat sits bottom-left, then left, top, right, bottom-right
		int[] expected = {0, 1, 2, 4, 6, 7};
		for (int k = 0; k < 6; k++) assertEquals(expected[k], HoldemLayout.slotOf((3 + k) % 6, 3, 6).id());
	}

	@Test
	void seatsNeverShareASlotAndCardsNeverOverlap() {
		for (int size = 2; size <= 9; size++) {
			Set<Integer> ids = new HashSet<>();
			for (int seat = 0; seat < size; seat++) ids.add(HoldemLayout.slotOf(seat, 0, size).id());
			assertEquals(size, ids.size(), "size " + size);
			List<Rect> cards = HoldemLayout.cardRects(size);
			for (int i = 0; i < cards.size(); i++) {
				assertTrue(cards.get(i).inside(HoldemLayout.canvas()), "card on the canvas: " + cards.get(i));
				for (int j = i + 1; j < cards.size(); j++) {
					boolean sameSeat = i >= 5 && j == i + 1 && (i - 5) % 2 == 0;
					if (!sameSeat) assertFalse(cards.get(i).intersects(cards.get(j)), "size " + size + ": " + cards.get(i) + " × " + cards.get(j));
				}
			}
		}
	}

	@Test
	void buttonTravelsAlongTheEllipse() {
		Slot a = HoldemLayout.slot(0);
		Slot b = HoldemLayout.slot(4);
		int[] mid = HoldemLayout.button(a, b, 0.5);
		// never straight across the felt: the midpoint sits on the ellipse, far from the centre
		double nx = (mid[0] - HoldemLayout.CENTER_X) / (double) HoldemLayout.BUTTON_RX;
		double ny = (mid[1] - HoldemLayout.CENTER_Y) / (double) HoldemLayout.BUTTON_RY;
		assertEquals(1.0, Math.hypot(nx, ny), 0.05);
		int[] end = HoldemLayout.button(a, b, 1);
		int[] endDirect = HoldemLayout.button(b, b, 0);
		assertEquals(endDirect[0], end[0], 1);
		assertEquals(endDirect[1], end[1], 1);
	}

	/**
	 * The mockup's flaw, fixed: every seat shows a hand name at the showdown, with RU-wide labels (up to 96 px); none
	 * may cover a card, a board card or another label, at every table size.
	 */
	@Test
	void showdownLabelsNeverCoverCards() {
		for (int size = 2; size <= 9; size++) {
			for (int width : new int[] {44, 72, 96}) {
				List<Rect> cards = HoldemLayout.cardRects(size);
				List<Rect> plates = new ArrayList<>();
				List<LabelPlacer.Request> reqs = new ArrayList<>();
				for (int seat = 0; seat < size; seat++) {
					Slot s = HoldemLayout.slotOf(seat, 0, size);
					int pw = HoldemLayout.plateWidth(40);
					plates.add(HoldemLayout.plate(s, pw));
					reqs.add(HoldemLayout.tagRequest(s, pw, width));
				}
				List<Rect> placed = LabelPlacer.place(cards, plates, HoldemLayout.labelBounds(), reqs);
				for (int i = 0; i < placed.size(); i++) {
					Rect l = placed.get(i);
					for (Rect c : cards) assertFalse(l.intersects(c), "size " + size + " w " + width + ": label " + l + " covers " + c);
					for (int j = i + 1; j < placed.size(); j++) assertFalse(l.intersects(placed.get(j)), "labels overlap");
					assertTrue(l.inside(HoldemLayout.labelBounds()));
				}
			}
		}
	}

	@Test
	void placerPrefersTheFirstFreeCandidate() {
		List<Rect> hard = List.of(new Rect(10, 10, 20, 20));
		Rect bounds = new Rect(0, 0, 200, 200);
		List<Rect> r = LabelPlacer.place(hard, List.of(), bounds, List.of(new LabelPlacer.Request(10, 5, 15, 15, 50, 50)));
		assertEquals(new Rect(50, 50, 10, 5), r.get(0));
		// no free candidate: the ring search moves it off the card
		r = LabelPlacer.place(hard, List.of(), bounds, List.of(new LabelPlacer.Request(10, 5, 15, 15)));
		assertFalse(r.get(0).intersects(hard.get(0)));
	}
}

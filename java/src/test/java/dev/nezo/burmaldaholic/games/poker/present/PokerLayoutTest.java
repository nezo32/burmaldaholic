package dev.nezo.burmaldaholic.games.poker.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.LayoutAudit;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Hold'em v2 layout (REDESIGN.md §7.3) at every GUI size of docs/architecture/ui-v2.md §11.1 × 2–9 seats: the
 * board, the pot, every plate, stack pill, hole-card fan, bet and button spot inside the felt and disjoint.
 */
class PokerLayoutTest {
	static List<int[]> sizes() {
		List<int[]> out = new ArrayList<>(List.of(new int[] {320, 240}, new int[] {427, 240}, new int[] {455, 256}, new int[] {480, 270},
			new int[] {512, 288}, new int[] {569, 320}, new int[] {600, 338}, new int[] {640, 360}, new int[] {683, 384}, new int[] {854, 480},
			new int[] {630, 270}, new int[] {960, 539}));
		for (int h = 240; h < 540; h += 13) {
			for (int w = Math.max(320, h * 4 / 3); w <= Math.min(1000, h * 21 / 9 + 1); w += 37) out.add(new int[] {w, h});
		}
		return out;
	}

	@Test
	void everySeatCountAtEverySizeIsCollisionFree() {
		List<String> problems = new ArrayList<>();
		for (int[] s : sizes()) {
			TableGrid g = TableGrid.of(s[0], s[1]);
			for (int n = 2; n <= 9; n++) {
				PokerLayout.Layout l = PokerLayout.of(g, n);
				LayoutAudit a = new LayoutAudit(g.play);
				for (int i = 0; i < 5; i++) a.add("board" + i, l.board()[i]);
				a.add("pot", l.pot());
				for (PokerLayout.Seat seat : l.seats()) {
					String p = "s" + seat.rel() + ".";
					a.add(p + "plate", seat.plate()).add(p + "pill", seat.pill()).add(p + "cards", seat.cards()).add(p + "bet", seat.bet())
						.add(p + "button", seat.button());
					if (seat.rel() == 0) a.add(p + "hand", seat.hand());
				}
				for (String pr : a.problems()) problems.add(s[0] + "x" + s[1] + " n=" + n + ": " + pr);
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems, first: " + problems.subList(0, Math.min(12, problems.size())));
	}

	@Test
	void the480MockupGeometry() {
		TableGrid g = TableGrid.of(480, 270);
		PokerLayout.Layout l = PokerLayout.of(g, 6);
		assertEquals(Tier.T2, g.tier);
		Box hero = l.seat(0).cards();
		assertEquals(g.play.bottom() - 4, hero.bottom(), "hero cards end 4 px above the Dock");
		assertEquals(240, hero.cx(), 1);
		assertTrue(l.seat(0).plate().right() <= hero.x(), "the hero plate is left of the cards");
		assertTrue(l.board()[0].bottom() < l.pot().y() + 1 && l.pot().bottom() <= hero.y(), "pot between board and hero");
		// the top seat (rel 3 of 6) sits at the top centre
		assertTrue(Math.abs(l.seat(3).plate().cx() + l.seat(3).pill().w() / 2 - 240) < 20, "top plate centred: " + l.seat(3).plate());
		assertTrue(l.seat(1).plate().cx() < 120 && l.seat(5).plate().cx() > 360, "left / right seats");
	}

	@Test
	void relativeSeatsRotateTheViewerToTheBottom() {
		assertEquals(0, PokerLayout.rel(3, 3, 6));
		assertEquals(1, PokerLayout.rel(4, 3, 6));
		assertEquals(5, PokerLayout.rel(2, 3, 6));
		assertEquals(2, PokerLayout.rel(2, -1, 6));
	}
}

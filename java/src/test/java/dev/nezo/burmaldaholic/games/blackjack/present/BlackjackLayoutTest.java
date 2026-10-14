package dev.nezo.burmaldaholic.games.blackjack.present;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.LayoutAudit;
import dev.nezo.burmaldaholic.core.ui.v2.SeatColumn;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The blackjack PlayArea at every real size: nothing outside the felt, no content box over another (decor yields). */
class BlackjackLayoutTest {
	static List<int[]> sizes() {
		List<int[]> out = new ArrayList<>(List.of(new int[] {320, 240}, new int[] {427, 240}, new int[] {455, 256}, new int[] {480, 270},
			new int[] {512, 288}, new int[] {569, 320}, new int[] {600, 338}, new int[] {640, 360}, new int[] {683, 384}, new int[] {854, 480},
			new int[] {630, 270}, new int[] {960, 539}));
		for (int h = 240; h < 540; h += 23) {
			for (int w = Math.max(320, h * 4 / 3); w <= Math.min(1000, h * 21 / 9 + 1); w += 53) out.add(new int[] {w, h});
		}
		return out;
	}

	@Test
	void noOverlapsAtAnySize() {
		int[][] hands = {{2}, {5}, {3, 3}, {2, 4}, {}};
		for (int[] size : sizes()) {
			TableGrid g = TableGrid.of(size[0], size[1]);
			for (int hero : new int[] {-1, 0, 2, 4}) {
				for (int[] h : hands) {
					for (int occupied = 1; occupied <= 5; occupied++) {
						BlackjackLayout.SeatIn[] seats = new BlackjackLayout.SeatIn[5];
						for (int s = 0; s < 5; s++) {
							if (s == hero || s < occupied) seats[s] = new BlackjackLayout.SeatIn(h, new int[] {60, 40}, 24);
						}
						for (int dealerCards : new int[] {2, 6}) {
							BlackjackLayout l = BlackjackLayout.of(g, seats, hero, dealerCards, 26, 80, new int[] {130, 140});
							audit(g, l, size, hero, h, occupied);
						}
					}
				}
			}
		}
	}

	private static void audit(TableGrid g, BlackjackLayout l, int[] size, int hero, int[] h, int occupied) {
		LayoutAudit a = new LayoutAudit(g.play);
		a.add("dealer", l.dealer()).add("dtotal", l.dealerTotal()).add("note", l.dealerNote()).add("shoe", l.shoe()).add("discard", l.discard())
			.add("banner", l.banner());
		for (int i = 0; i < l.prints().length; i++) a.add("print" + i, l.prints()[i]);
		for (int i = 0; i < l.columns().length; i++) {
			SeatColumn c = l.columns()[i];
			if (c == null) continue;
			a.add("s" + i + ".cards", c.cards()).add("s" + i + ".out", c.outcome()).add("s" + i + ".stack", c.stack()).add("s" + i + ".value", c.value())
				.add("s" + i + ".plate", c.plate());
		}
		a.allow("s", "s"); // across columns checked below with the right message
		a.allow("banner", "print"); // the banner covers decor only
		List<String> p = new ArrayList<>(a.problems());
		for (int i = 0; i < l.columns().length; i++) {
			for (int j = i + 1; j < l.columns().length; j++) {
				SeatColumn x = l.columns()[i];
				SeatColumn y = l.columns()[j];
				if (x == null || y == null) continue;
				for (Box bx : x.boxes()) for (Box by : y.boxes()) if (bx.intersects(by)) p.add("seat " + i + " " + bx + " x seat " + j + " " + by);
			}
		}
		String where = size[0] + "x" + size[1] + " hero " + hero + " hands " + java.util.Arrays.toString(h) + " occ " + occupied;
		assertTrue(p.isEmpty(), () -> where + ": " + p);
		if (hero >= 0) assertNotNull(l.columns()[l.hero()], where);
	}
}

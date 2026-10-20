package dev.nezo.burmaldaholic.games.blackjack.present;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.LayoutAudit;
import dev.nezo.burmaldaholic.core.ui.v2.SeatColumn;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
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
			.add("banner", l.banner()).add("e5bar", l.shoeBar()).add("e5count", l.discardCount()); // not "s…": allow("s", "s") would skip them
		for (int i = 0; i < l.prints().length; i++) a.add("print" + i, l.prints()[i]);
		for (int i = 0; i < l.columns().length; i++) {
			SeatColumn c = l.columns()[i];
			if (c == null) continue;
			a.add("s" + i + ".cards", c.cards()).add("s" + i + ".out", c.outcome()).add("s" + i + ".stack", c.stack()).add("s" + i + ".value", c.value())
				.add("s" + i + ".plate", c.plate());
		}
		a.allow("s", "s"); // across columns checked below with the right message
		a.allow("banner", "print"); // the banner covers decor only
		a.allow("e5bar", "shoe"); // drawn on the shoe's face
		a.allow("e5count", "discard"); // drawn on the tray
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
		// E5: the shoe-depth bar and the discard count exist at every tier, inside the felt, right under their boxes
		if (l.shoeBar().isEmpty() || !l.shoeBar().inside(g.felt())) p.add("shoe bar " + l.shoeBar() + " missing / off the felt");
		if (l.discardCount().isEmpty() || !l.discardCount().inside(g.felt())) p.add("discard count " + l.discardCount() + " missing / off the felt");
		if (l.shoeBar().w() < 20 || l.shoeBar().h() < 3) p.add("shoe bar too small " + l.shoeBar());
		if (l.discardCount().w() < BlackjackLayout.COUNT_W) p.add("discard count too narrow " + l.discardCount());
		if (!l.shoeBar().inside(l.shoe())) p.add("shoe bar " + l.shoeBar() + " off the shoe " + l.shoe());
		if (!l.discardCount().inside(l.discard())) p.add("discard count " + l.discardCount() + " off the tray " + l.discard());
		for (Box e5 : new Box[] {l.shoeBar(), l.discardCount()}) {
			if (e5.intersects(l.resultBanner())) p.add(e5 + " under the result banner");
			for (Box sp : l.spots()) if (e5.intersects(sp)) p.add(e5 + " over a betting box " + sp);
		}
		assertTrue(p.isEmpty(), () -> where + ": " + p);
		if (hero >= 0) assertNotNull(l.columns()[l.hero()], where);
	}

	/**
	 * DES blackjack P1 (A-0022): split hands side by side, each with its own total pill, stack and value pill, all disjoint
	 * from each other and from the other seats; the result banner region (over the hero's outcome slot) touches no other box.
	 */
	@Test
	void splitHandsAreDisjointAndResultBannerIsFree() {
		int[][] splits = {{2, 2}, {3, 3}, {2, 5}, {4, 4}, {2, 2, 2}};
		for (int[] size : sizes()) {
			TableGrid g = TableGrid.of(size[0], size[1]);
			Tier t = g.tier;
			for (int[] split : splits) {
				for (int occupied = 1; occupied <= 5; occupied++) {
					BlackjackLayout.SeatIn[] seats = new BlackjackLayout.SeatIn[5];
					for (int s = 0; s < 5; s++) if (s == 2 || s < occupied) seats[s] = new BlackjackLayout.SeatIn(s == 2 ? split : new int[] {3}, new int[] {40, 24}, 24);
					BlackjackLayout l = BlackjackLayout.of(g, seats, 2, 3, 26, 80, new int[] {130, 140});
					String where = size[0] + "x" + size[1] + " split " + java.util.Arrays.toString(split) + " occ " + occupied;
					for (int slot = 0; slot < l.columns().length; slot++) {
						SeatColumn c = l.columns()[slot];
						if (c == null || slot != l.hero()) continue;
						int[] tw = new int[split.length];
						int[] vw = new int[split.length];
						java.util.Arrays.fill(tw, 16);
						java.util.Arrays.fill(vw, 18);
						BlackjackLayout.HandBox[] hb = BlackjackLayout.hands(c, t, true, split, tw, vw);
						List<String> p = new ArrayList<>();
						for (int a = 0; a < hb.length; a++) {
							for (Box b : hb[a].boxes()) if (!b.isEmpty() && !b.inside(g.felt())) p.add("hand " + a + " " + b + " off the felt");
							if (hb[a].fan().intersects(hb[a].total())) p.add("hand " + a + " total over its cards");
							if (hb[a].fan().intersects(hb[a].value())) p.add("hand " + a + " value over its cards");
							for (int b = a + 1; b < hb.length; b++) {
								for (Box x : hb[a].boxes()) for (Box y : hb[b].boxes()) if (x.intersects(y)) p.add("hand " + a + " " + x + " x hand " + b + " " + y);
								if (hb[b].fan().x() - hb[a].fan().right() < BlackjackLayout.SPLIT_GAP) p.add("hands closer than the gap");
							}
							if (hb[a].step() < 3) p.add("hand " + a + " step " + hb[a].step());
						}
						assertTrue(p.isEmpty(), () -> where + ": " + p);
					}
					Box rb = l.resultBanner();
					if (!rb.isEmpty()) {
						for (int i = 0; i < l.columns().length; i++) {
							SeatColumn c = l.columns()[i];
							if (c == null) continue;
							for (Box b : c.boxes()) {
								if (i == l.hero() && b == c.outcome()) continue;
								assertTrue(!b.intersects(rb), where + ": result banner " + rb + " over seat " + i + " " + b);
							}
						}
						assertTrue(!rb.intersects(l.dealer()), where + ": result banner over the dealer");
					}
				}
			}
			if (size[0] == 480 && size[1] == 270) {
				BlackjackLayout.SeatIn[] seats = new BlackjackLayout.SeatIn[5];
				seats[2] = new BlackjackLayout.SeatIn(new int[] {3}, new int[] {40, 24}, 24);
				BlackjackLayout l = BlackjackLayout.of(g, seats, 2, 3, 26, 80, new int[] {130, 140});
				assertTrue(l.resultBanner().h() >= 20 + 2, "480x270: the compact banner (200x20) fits the dealer-hero gap: " + l.resultBanner());
			}
		}
	}
}

package dev.nezo.burmaldaholic.games.uth.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.LayoutAudit;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The UTH v2 layout (REDESIGN.md §7.4) at every size × 0–5 other seats: inside the felt, disjoint, spots readable. */
class UthTableLayoutTest {
	static List<int[]> sizes() {
		List<int[]> out = new ArrayList<>(List.of(new int[] {320, 240}, new int[] {427, 240}, new int[] {455, 256}, new int[] {480, 270},
			new int[] {512, 288}, new int[] {569, 320}, new int[] {600, 338}, new int[] {640, 360}, new int[] {683, 384}, new int[] {854, 480},
			new int[] {630, 270}, new int[] {960, 539}));
		for (int h = 240; h < 540; h += 13) {
			for (int w = Math.max(320, h * 4 / 3); w <= Math.min(1000, h * 21 / 9 + 1); w += 37) out.add(new int[] {w, h});
		}
		return out;
	}

	static UthTableLayout.Layout of(TableGrid g, int others) {
		// widths of the longest EN / RU texts at 6 px a char: "Dealer · Two pair", "Дилер: две пары"; "Straight flush"; "Трипс"
		return UthTableLayout.of(g, others, 17 * 6 + 6, 14 * 6 + 8, 5 * 6 + 2, 9 * 6 + 6);
	}

	@Test
	void everySizeAndSeatCountIsCollisionFree() {
		List<String> problems = new ArrayList<>();
		for (int[] s : sizes()) {
			TableGrid g = TableGrid.of(s[0], s[1]);
			for (int n = 0; n <= 5; n++) {
				UthTableLayout.Layout l = of(g, n);
				LayoutAudit a = new LayoutAudit(g.felt());
				a.add("dealer", l.dealer()).add("dealerPill", l.dealerPill()).add("deck", l.deck()).add("hero", l.hero()).add("heroPlate", l.heroPlate())
					.add("heroHand", l.heroHand());
				for (int i = 0; i < 5; i++) a.add("board" + i, l.board()[i]);
				for (int i = 0; i < 4; i++) a.add("spot" + i, l.spots()[i]).add("label" + i, l.spotLabels()[i]).add("value" + i, l.spotValues()[i]);
				for (int i = 0; i < n; i++) {
					UthTableLayout.Other o = l.others()[i];
					a.add("o" + i + ".plate", o.plate()).add("o" + i + ".pill", o.pill()).add("o" + i + ".cards", o.cards());
				}
				a.allow("o", "o"); // a seat's own pill sits under its plate: checked below per seat
				for (String p : a.problems()) {
					if (!crossSeat(p)) continue;
					problems.add(s[0] + "x" + s[1] + " n=" + n + ": " + p);
				}
				for (int i = 0; i < n; i++) {
					for (int j = 0; j < n; j++) {
						if (i == j) continue;
						for (Box x : boxes(l.others()[i])) for (Box y : boxes(l.others()[j])) {
							if (x.intersects(y)) problems.add(s[0] + "x" + s[1] + " n=" + n + ": o" + i + " " + x + " overlaps o" + j + " " + y);
						}
					}
					UthTableLayout.Other o = l.others()[i];
					if (o.plate().intersects(o.cards()) || o.pill().intersects(o.cards()) || o.pill().intersects(o.plate())) problems.add("own o" + i);
				}
			}
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems, first: " + problems.subList(0, Math.min(12, problems.size())));
	}

	private static boolean crossSeat(String p) {
		return true;
	}

	private static Box[] boxes(UthTableLayout.Other o) {
		return new Box[] {o.plate(), o.pill(), o.cards()};
	}

	@Test
	void the480Geometry() {
		TableGrid g = TableGrid.of(480, 270);
		UthTableLayout.Layout l = of(g, 5);
		assertEquals(g.play.bottom() - 4, l.hero().bottom());
		assertTrue(l.spots()[0].x() > l.hero().right(), "spots right of the hero cards");
		assertTrue(l.spotLabels()[1].w() >= 30, "spot labels readable: " + l.spotLabels()[1]);
		assertTrue(l.banner().h() >= 20, "a banner band between board and hero: " + l.banner());
		assertTrue(l.others()[0].plate().y() > l.others()[2].plate().y(), "left column bottom → top");
		assertTrue(l.others()[3].plate().y() < l.others()[4].plate().y(), "right column top → bottom");
	}
}

package dev.nezo.burmaldaholic.games.roulette.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.LayoutAudit;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Racetrack;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The roulette v2 PlayArea at every GUI size (docs/architecture/ui-v2.md §11.1): regions inside the felt and disjoint,
 * every spot of the layout reachable by clicking its own HitMap box, stacks never on a printed number (§2.2), call
 * bets labelled in EN and RU.
 */
class RouletteLayoutTest {
	static List<int[]> sizes() {
		List<int[]> out = new ArrayList<>(List.of(new int[] {320, 240}, new int[] {427, 240}, new int[] {455, 256}, new int[] {480, 270},
			new int[] {512, 288}, new int[] {569, 320}, new int[] {600, 338}, new int[] {640, 360}, new int[] {683, 384}, new int[] {854, 480},
			new int[] {630, 270}, new int[] {960, 539}));
		for (int h = 240; h < 540; h += 13) {
			for (int w = Math.max(320, h * 4 / 3); w <= Math.min(1000, h * 21 / 9 + 1); w += 37) out.add(new int[] {w, h});
		}
		return out;
	}

	private static final String[][] CALL_LABELS = {{"Tier", "Orphelins", "Voisins", "Zero"}, {"Тьер", "Орфелен", "Вуазен", "Зеро"}};

	@Test
	void mockupSizesMatchTheDesign() {
		RouletteLayout l = RouletteLayout.of(480, 270);
		assertEquals(Tier.T2, l.tier);
		assertEquals(104, l.wheelD);
		assertEquals(23, l.cells.cw());
		assertEquals(41, l.cells.ch());
		assertEquals(8, l.pills.size());
		RouletteLayout t3 = RouletteLayout.of(640, 360);
		assertEquals(144, t3.wheelD);
		assertEquals(30, t3.cells.cw());
		assertEquals(54, t3.cells.ch());
	}

	@Test
	void regionsStayOnTheFeltAndNeverOverlap() {
		for (int[] s : sizes()) {
			RouletteLayout l = RouletteLayout.of(s[0], s[1]);
			String at = s[0] + "x" + s[1];
			LayoutAudit a = new LayoutAudit(l.grid.play);
			a.add("wheel", l.wheel).add("layout", l.layout).add("history", l.historyLabel);
			for (int i = 0; i < l.pills.size(); i++) a.add("pill." + i, l.pills.get(i));
			for (int i = 0; i < l.calls.size(); i++) a.add("call." + i, l.calls.get(i));
			assertEquals(List.of(), a.problems(), at);
			assertTrue(l.pills.size() >= 6, at + " pills " + l.pills.size());
			assertTrue(l.wheel.right() <= l.layout.x() - 4, at + " wheel touches the layout");
			assertTrue(l.cells.ch() >= l.chip + 2 * RouletteLayout.GLYPH_H, at + " cell too short " + l.cells.ch());
		}
	}

	@Test
	void everySpotIsReachableByItsAnchorAndItsHitBox() {
		for (int[] s : sizes()) {
			RouletteLayout l = RouletteLayout.of(s[0], s[1]);
			HitMap map = new HitMap();
			for (Spot sp : RouletteLayout.allSpots()) {
				map.add(RouletteLayout.spotId(sp), l.target(sp), true);
			}
			Set<String> ids = new HashSet<>();
			for (Spot sp : RouletteLayout.allSpots()) {
				String at = s[0] + "x" + s[1] + " " + sp.key();
				int[] p = l.anchor(sp);
				assertEquals(Optional.of(sp), l.hit(p[0], p[1]), at + " anchor");
				Box t = l.target(sp);
				assertTrue(t.inside(l.layout), at + " target " + t + " outside " + l.layout);
				assertEquals(Optional.of(sp), l.hit(t.cx(), t.cy()), at + " target centre");
				// the HitMap (topmost wins) routes the centre of every target back to the same spot
				assertEquals(RouletteLayout.spotId(sp), map.at(t.cx(), t.cy()).orElseThrow().id(), at + " hitmap");
				assertTrue(ids.add(RouletteLayout.spotId(sp)), at + " duplicate id");
			}
			assertEquals(157, ids.size());
		}
	}

	@Test
	void everyPixelOfTheLayoutIsAValidSpotOrNothing() {
		for (int[] s : List.of(new int[] {320, 240}, new int[] {480, 270}, new int[] {640, 360}, new int[] {854, 480})) {
			RouletteLayout l = RouletteLayout.of(s[0], s[1]);
			Set<String> seen = new HashSet<>();
			for (int y = l.layout.y(); y < l.layout.bottom(); y++) {
				for (int x = l.layout.x(); x < l.layout.right(); x++) {
					l.hit(x, y).ifPresent(sp -> {
						assertTrue(sp.isValid(), sp.key());
						seen.add(sp.key());
					});
				}
			}
			assertEquals(157, seen.size(), s[0] + "x" + s[1]);
			assertFalse(l.hit(l.layout.x() - 1, l.layout.y()).isPresent());
		}
	}

	@Test
	void stacksNeverCoverAPrintedNumber() {
		for (int[] s : sizes()) {
			RouletteLayout l = RouletteLayout.of(s[0], s[1]);
			List<Box> glyphs = l.glyphs();
			for (Spot sp : RouletteLayout.allSpots()) {
				Box st = l.stack(sp, RouletteLayout.MAX_DISCS);
				for (int n = 0; n <= 36; n++) {
					assertFalse(st.intersects(glyphs.get(n)), s[0] + "x" + s[1] + " " + sp.key() + " stack " + st + " covers " + n + " " + glyphs.get(n));
				}
				assertTrue(st.inside(l.grid.play), s[0] + "x" + s[1] + " " + sp.key());
			}
		}
	}

	@Test
	void valueTagsAvoidNumbersAndOtherStacks() {
		RouletteLayout l = RouletteLayout.of(480, 270);
		Spot split = Spot.of(BetType.SPLIT, 8, 11);
		Box other = l.stack(Spot.of(BetType.STRAIGHT, 11), 1);
		Box tag = l.tag(split, 2, 17, 11, List.of(other)).orElseThrow();
		for (Box g : l.glyphs()) assertFalse(tag.intersects(g), "tag on " + g);
		assertFalse(tag.intersects(other));
		assertFalse(tag.intersects(l.stack(split, 2)));
		int found = 0;
		for (Spot sp : RouletteLayout.allSpots()) {
			if (l.tag(sp, 1, 23, 11, List.of()).isPresent()) found++;
		}
		assertTrue(found > 140, "tags placed " + found);
	}

	@Test
	void callBetsAreClickableAndLabelled() {
		for (int[] s : sizes()) {
			RouletteLayout l = RouletteLayout.of(s[0], s[1]);
			for (Racetrack.Section sec : Racetrack.Section.values()) {
				Box b = l.calls.get(sec.ordinal());
				assertEquals(Optional.of(sec), l.callAt(b.cx(), b.cy()));
				if (s[0] >= 400) {
					for (String[] lang : CALL_LABELS) {
						int w = lang[sec.ordinal()].length() * 6;
						assertTrue(w + 4 <= b.w(), s[0] + "x" + s[1] + " " + lang[sec.ordinal()] + " in " + b.w());
					}
				}
			}
		}
	}
}

package dev.nezo.burmaldaholic.games.roulette.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.FreeSlot;
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
		// the outside labels are obstacles too (T-C2 V2): a few tags near them give way to the status readout
		assertTrue(found > 120, "tags placed " + found);
	}

	/** T-C2 V2: no chip stack (up to the full height) and no value tag ever covers a printed outside label, at every size. */
	@Test
	void stacksAndTagsNeverCoverOutsideLabels() {
		for (int[] s : sizes()) {
			RouletteLayout l = RouletteLayout.of(s[0], s[1]);
			List<Box> labels = l.labelAreas();
			if (s[0] < 400) continue; // below the smallest real size (427×240) the rows are too short for both
			for (Spot sp : RouletteLayout.allSpots()) {
				Box st = l.stack(sp, RouletteLayout.MAX_DISCS);
				// street / six-line chips sit ON the grid's bottom line by convention; only outside stacks are checked here
				if (sp.type().inside()) continue;
				for (Box la : labels) assertFalse(st.intersects(la), s[0] + "x" + s[1] + " " + sp.key() + " stack over label " + la);
				l.tag(sp, 2, 23, 11, List.of()).ifPresent(t -> {
					for (Box la : labels) assertFalse(t.intersects(la), s[0] + "x" + s[1] + " " + sp.key() + " tag over label");
				});
			}
		}
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

	/** Banner sizes the kit uses by tier (ResultBanner: T1 160×18, T2 232×28, T3 300×36) and K2a's compact 200×20. */
	private static int[][] bannerSizes(Tier t) {
		return switch (t) {
			case T1 -> new int[][] {{160, 18}, {200, 20}};
			case T2 -> new int[][] {{232, 28}, {200, 20}};
			case T3 -> new int[][] {{300, 36}, {200, 20}};
		};
	}

	/**
	 * R-CDE M4: wherever the ResultBanner lands it never covers the winning cell — for each of the 37 numbers at every
	 * size — nor the wheel and the history; at the standard sizes it is placed (not skipped) under the numbers.
	 */
	@Test
	void bannerNeverCoversTheWinningCell() {
		int placed = 0;
		for (int[] s : sizes()) {
			RouletteLayout l = RouletteLayout.of(s[0], s[1]);
			for (int[] b : bannerSizes(l.tier)) {
				Optional<Box> at = FreeSlot.first(l.bannerRegions(), b[0], b[1], l.bannerObstacles());
				if (at.isEmpty()) continue;
				placed++;
				Box box = at.get();
				String where = s[0] + "x" + s[1] + " banner " + box;
				assertTrue(box.inside(l.grid.play), where + " outside the felt");
				for (int n = 0; n <= 36; n++) {
					assertFalse(box.intersects(l.cell(n)), where + " covers the winning cell " + n + " " + l.cell(n));
					assertFalse(box.intersects(l.glyph(n)), where + " covers the number " + n);
				}
				assertFalse(box.intersects(l.wheel), where + " covers the wheel");
				for (Box p : l.pills) assertFalse(box.intersects(p), where + " covers a history pill");
			}
		}
		assertTrue(placed > sizes().size(), "banners placed " + placed);
		for (int[] s : List.of(new int[] {480, 270}, new int[] {640, 360}, new int[] {427, 240})) {
			RouletteLayout l = RouletteLayout.of(s[0], s[1]);
			int[] b = bannerSizes(l.tier)[0];
			Box box = FreeSlot.first(l.bannerRegions(), b[0], b[1], l.bannerObstacles()).orElse(null);
			if (box == null) {
				b = bannerSizes(l.tier)[1];
				box = FreeSlot.first(l.bannerRegions(), b[0], b[1], l.bannerObstacles()).orElse(null);
			}
			assertTrue(box != null, s[0] + "x" + s[1] + " no place for the banner");
			assertTrue(box.y() >= l.numbers.bottom(), s[0] + "x" + s[1] + " banner " + box + " not under the numbers " + l.numbers);
		}
	}

	/** T-C2 V5: the framed / ROOMY felts (owner 2560×1440 GUI 2, 1920×1080 GUI 2, 4K) are filled, not just their top 45 %. */
	@Test
	void roomyAndFramedFeltsAreFilled() {
		int[][] cases = {{2560, 1440, 2}, {1920, 1080, 2}, {3840, 2160, 4}, {3840, 2160, 3}};
		int roomy = 0;
		for (int[] c : cases) {
			dev.nezo.burmaldaholic.core.ui.v2.Viewport vp = dev.nezo.burmaldaholic.core.ui.v2.Viewport.of(c[0], c[1], c[2]);
			RouletteLayout l = RouletteLayout.of(new dev.nezo.burmaldaholic.core.ui.v2.TableGrid(vp));
			String at = c[0] + "x" + c[1] + " gui " + c[2] + " frame " + vp.frameW() + "x" + vp.frameH();
			if (!l.grid.roomy()) continue;
			roomy++;
			Box play = l.grid.play;
			int top = l.layout.y();
			int bottom = l.calls.get(0).bottom();
			assertTrue(bottom - top >= 0.6 * play.h(), at + ": layout block " + (bottom - top) + " of felt " + play.h());
			int above = top - play.y();
			int below = play.bottom() - bottom;
			assertTrue(Math.abs(above - below) <= 24, at + ": not centred, " + above + " above / " + below + " below");
			assertTrue(l.wheelD >= 144, at + ": wheel " + l.wheelD);
			assertTrue(l.layout.w() >= 0.6 * play.w(), at + ": layout width " + l.layout.w() + " of " + play.w());
		}
		assertTrue(roomy >= 2, "ROOMY cases checked " + roomy);
	}

	/** T-C2 V4: the cell drawn over a celebration covers the whole winning cell and its number, inside the felt. */
	@Test
	void theWinningCellOverTheCelebrationCoversCellAndNumber() {
		for (int[] s : sizes()) {
			RouletteLayout l = RouletteLayout.of(s[0], s[1]);
			for (int n = 0; n <= 36; n++) {
				Box p = l.winCellPunch(n);
				assertTrue(l.cell(n).inside(p) && l.glyph(n).inside(p), s[0] + "x" + s[1] + " " + n);
				assertTrue(p.inside(l.grid.play), s[0] + "x" + s[1] + " " + n + " punch outside the felt");
			}
		}
	}

	/** T-C2 V6: the banner keeps a 2 px gap to every call bet (Orphelins / Voisins stay readable). */
	@Test
	void theBannerNeverTouchesTheCallBets() {
		for (int[] s : sizes()) {
			RouletteLayout l = RouletteLayout.of(s[0], s[1]);
			for (int[] b : bannerSizes(l.tier)) {
				Optional<Box> at = FreeSlot.first(l.bannerRegions(), b[0], b[1], l.bannerObstacles());
				if (at.isEmpty()) continue;
				for (Box c : l.calls) {
					Box grown = new Box(c.x(), c.y() - 2, c.w(), c.h() + 4);
					assertFalse(at.get().intersects(grown), s[0] + "x" + s[1] + " banner " + at.get() + " touches call " + c);
				}
			}
		}
	}
}

package dev.nezo.burmaldaholic.games.craps.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.LayoutAudit;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import dev.nezo.burmaldaholic.games.craps.present.CrapsLayout.Layer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The craps v2 felt at every GUI size of ui-v2.md §11.1: every bet area inside the PlayArea, the areas disjoint (the
 * odds lane is part of the Pass line), every chip stack inside its area and off every printed number, the puck on its
 * box and off the number, the dice and the total pill inside the COME row, the banner region free, labels fit (EN / RU
 * widths through the 6 px/char stub).
 */
class CrapsLayoutTest {
	static final java.util.function.ToIntFunction<String> FONT = s -> s.codePointCount(0, s.length()) * 6;

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
	void mockupGeometryAt480x270() {
		CrapsLayout l = CrapsLayout.of(TableGrid.of(480, 270));
		assertEquals(Tier.T2, l.tier);
		assertEquals(new Box(10, 30, 460, 184), l.felt);
		assertTrue(Math.abs(l.dontCome.w() - 78) <= 1, "Don't Come box 0.17 w: " + l.dontCome);
		assertEquals(18, l.die1.w());
		assertEquals(2, l.pointScale);
		assertEquals(3, CrapsLayout.of(TableGrid.of(640, 360)).pointScale);
		// rows in the mockup's order
		assertTrue(l.points[0].bottom() < l.come.y() && l.come.bottom() < l.field.y() && l.field.bottom() < l.dontPass.y()
			&& l.dontPass.bottom() < l.pass.y());
	}

	@Test
	void everySizeIsCleanAndCollisionFree() {
		for (int[] s : sizes()) {
			TableGrid g = TableGrid.of(s[0], s[1]);
			CrapsLayout l = CrapsLayout.of(g);
			String at = s[0] + "x" + s[1] + " ";
			assertTrue(l.felt.inside(g.play), at + "felt " + l.felt + " in " + g.play);
			// areas
			LayoutAudit areas = new LayoutAudit(l.felt);
			for (Map.Entry<String, Box> e : l.spots().entrySet()) {
				if (!e.getKey().equals(CrapsLayout.PASS_ODDS)) areas.add(e.getKey(), e.getValue());
			}
			assertEquals(List.of(), areas.problems(), at);
			assertTrue(l.passOdds.inside(l.pass), at + "odds lane in the pass line");
			for (Box p : l.points) assertTrue(p.w() >= 30 && p.h() >= 30, at + "point box " + p);
			// glyphs inside their boxes
			for (int i = 0; i < CrapsLayout.POINTS.length; i++) {
				assertTrue(l.pointGlyphs[i].inside(l.points[i]), at + "glyph " + l.pointGlyphs[i] + " in " + l.points[i]);
				Box puck = l.puckBox(CrapsLayout.POINTS[i]);
				assertTrue(puck.inside(l.points[i]) && !puck.intersects(l.pointGlyphs[i]), at + "puck " + puck + " on " + l.points[i]);
			}
			assertTrue(l.puckBox(0).inside(l.dontCome), at + "puck off in Don't Come");
			for (Box f : l.fieldGlyphs) assertTrue(f.inside(l.field), at + "field glyph " + f);
			// dice + total + caption inside COME, not on the COME word
			LayoutAudit come = new LayoutAudit(l.come);
			come.add("word", l.comeWord).add("die1", l.die1).add("die2", l.die2).add("total", l.total).add("caption", l.pointCaption)
				.add("mine", l.stack("come", 0, Layer.MINE)).add("others", l.stack("come", 0, Layer.OTHERS));
			assertEquals(List.of(), come.problems(), at);
			assertTrue(l.restRegion.inside(l.come), at + "rest region " + l.restRegion);
			assertTrue(l.throwX < l.felt.right() && l.throwY < l.felt.bottom(), at + "throw start on the felt");
			// stacks
			for (String kind : new String[] {"pass", "dont_pass", "field", "dont_come"}) {
				Box area = area(l, kind);
				LayoutAudit a = new LayoutAudit(area);
				a.add("mine", l.stack(kind, 0, Layer.MINE)).add("others", l.stack(kind, 0, Layer.OTHERS));
				if (!kind.equals("field")) a.add("odds", l.stack(kind, 0, Layer.ODDS));
				for (Box glyph : l.glyphs()) {
					if (glyph.inside(area)) a.add("glyph" + glyph, glyph);
				}
				a.allow("glyph", "glyph");
				assertEquals(List.of(), a.problems(), at + kind);
			}
			for (int p : CrapsLayout.POINTS) {
				for (String kind : new String[] {"come", "dont_come"}) {
					LayoutAudit a = new LayoutAudit(l.point(p));
					a.add("mine", l.stack(kind, p, Layer.MINE)).add("odds", l.stack(kind, p, Layer.ODDS)).add("others", l.stack(kind, p, Layer.OTHERS))
						.add("glyph", l.pointGlyph(p)).add("puck", l.puckBox(p));
					assertEquals(List.of(), a.problems(), at + kind + " on " + p);
				}
			}
			// banner region: free of numbers, stacks and dice
			Box banner = l.bannerRegion();
			assertTrue(banner.w() >= 60 && banner.inside(l.come), at + "banner " + banner);
			assertTrue(!banner.intersects(l.die1) && !banner.intersects(l.stack("come", 0, Layer.MINE)), at + "banner free");
			// labels (EN / RU) fit their print boxes
			assertTrue(FONT.applyAsInt("FIELD") * l.wordScale <= l.fieldWord.w() || FONT.applyAsInt("ФИЛД") <= l.fieldWord.w(), at + "FIELD");
			assertTrue(FONT.applyAsInt("point") <= l.pointCaption.w() + 2 && FONT.applyAsInt("поинт") <= l.pointCaption.w() + 2, at + "point caption");
			assertTrue(l.maxDiscs() >= 2, at + "stacks show at least two discs");
		}
	}

	private static Box area(CrapsLayout l, String kind) {
		return switch (kind) {
			case "pass" -> l.pass;
			case "dont_pass" -> l.dontPass;
			case "field" -> l.field;
			case "come" -> l.come;
			default -> l.dontCome;
		};
	}

	@Test
	void spotIdsRoundTrip() {
		assertEquals("point", CrapsLayout.kindOf(CrapsLayout.pointId(8)));
		assertEquals(8, CrapsLayout.pointOf(CrapsLayout.pointId(8)));
		assertEquals("pass_odds", CrapsLayout.kindOf(CrapsLayout.PASS_ODDS));
		assertEquals("dont_pass", CrapsLayout.kindOf(CrapsLayout.DONT_PASS));
		assertEquals(0, CrapsLayout.pointOf(CrapsLayout.FIELD));
	}
}

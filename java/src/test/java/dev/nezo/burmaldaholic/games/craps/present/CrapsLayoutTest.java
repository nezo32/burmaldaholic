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
		assertEquals(new Box(10, 30, 460, 6), l.backWall);
		assertTrue(Math.abs(l.dontCome.w() - 78) <= 1, "Don't Come box 0.17 w: " + l.dontCome);
		assertEquals(18, l.die);
		assertEquals(30, CrapsLayout.of(TableGrid.of(960, 540)).die, "ROOMY 30-px dice");
		assertEquals(10, CrapsLayout.of(TableGrid.of(960, 540)).backWall.h());
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
			Box diceArea = l.diceZone.inset(-l.die / 2);
			come.add("word", l.comeWord).add("dice", diceArea).add("total", l.total).add("caption", l.pointCaption)
				.add("mine", l.stack("come", 0, Layer.MINE)).add("others", l.stack("come", 0, Layer.OTHERS));
			assertEquals(List.of(), come.problems(), at);
			assertTrue(diceArea.inside(l.come), at + "dice zone " + diceArea + " in " + l.come);
			assertTrue(l.diceZone.w() >= 2 * l.die, at + "two dice fit side by side " + l.diceZone);
			// the wall strip on top, every row under it
			assertTrue(l.backWall.inside(l.felt) && l.backWall.y() == l.felt.y() && l.backWall.bottom() < l.points[0].y(), at + "wall " + l.backWall);
			// dice home: on the Pass line, two dice wide, clear of the odds word and every stack
			for (Box home : new Box[] {l.diceHomeL, l.diceHomeR}) {
				assertTrue(home.inside(l.pass), at + "home " + home);
				assertTrue(!home.intersects(l.oddsWord) && !home.intersects(l.passWord), at + "home off the words " + home);
				for (Layer ly : Layer.values()) assertTrue(!home.intersects(l.stack("pass", 0, ly)), at + "home off the pass stacks " + ly);
			}
			// the arena: rest zone inside diceZone ± 1 px, wall plane on the wall's bottom, home on dice home
			var map = l.arenaMap();
			var rz = CrapsThrow.restZone();
			Box zg = l.diceZone.inset(-1);
			assertTrue(map.x(rz.x0()) >= zg.x() && map.x(rz.x1()) <= zg.right() && map.y(rz.y0()) >= zg.y() && map.y(rz.y1()) <= zg.bottom(),
				at + "arena rest zone x " + map.x(rz.x0()) + ".." + map.x(rz.x1()) + " y " + map.y(rz.y0()) + ".." + map.y(rz.y1()) + " in " + l.diceZone);
			assertEquals(l.backWall.bottom(), map.y(0), 1e-9, at + "wall plane");
			for (int side : new int[] {CrapsThrow.LEFT, CrapsThrow.RIGHT}) {
				double[] h = CrapsThrow.home(side);
				assertTrue(l.diceHome(side).inset(-l.die / 2).contains(map.x(h[0]), map.y(h[1])), at + "home " + side + " at " + map.x(h[0]) + "," + map.y(h[1]));
			}
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
			assertTrue(!banner.intersects(l.diceZone.inset(-l.die / 2)) && !banner.intersects(l.stack("come", 0, Layer.MINE)), at + "banner free");
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

	/** Dumps the 480 × 270 and 640 × 360 boxes for the design preview ({@code build/craps_layout_<w>.txt}). */
	@Test
	void dumpForPreview() throws Exception {
		for (int[] wh : new int[][] {{480, 270}, {640, 360}, {427, 240}}) {
			CrapsLayout l = CrapsLayout.of(TableGrid.of(wh[0], wh[1]));
			StringBuilder sb = new StringBuilder();
			java.util.function.BiConsumer<String, Box> put = (n, b) -> sb.append(n).append(' ').append(b.x()).append(' ').append(b.y()).append(' ')
				.append(b.w()).append(' ').append(b.h()).append('\n');
			put.accept("felt", l.felt);
			l.spots().forEach(put);
			for (int i = 0; i < 6; i++) put.accept("glyph." + CrapsLayout.POINTS[i], l.pointGlyphs[i]);
			for (int i = 0; i < 7; i++) put.accept("fglyph." + l.fieldNumbers[i], l.fieldGlyphs[i]);
			put.accept("comeWord", l.comeWord);
			put.accept("fieldWord", l.fieldWord);
			put.accept("dpWord", l.dontPassWord);
			put.accept("passWord", l.passWord);
			put.accept("oddsWord", l.oddsWord);
			put.accept("wall", l.backWall);
			put.accept("diceZone", l.diceZone);
			put.accept("homeR", l.diceHomeR);
			put.accept("total", l.total);
			put.accept("caption", l.pointCaption);
			put.accept("puck8", l.puckBox(8));
			put.accept("puck0", l.puckBox(0));
			put.accept("stack.pass", l.stack("pass", 0, Layer.MINE));
			put.accept("stack.pass_odds", l.stack("pass", 0, Layer.ODDS));
			put.accept("stack.field", l.stack("field", 0, Layer.MINE));
			put.accept("stack.come5", l.stack("come", 5, Layer.MINE));
			put.accept("stack.others_field", l.stack("field", 0, Layer.OTHERS));
			put.accept("banner", l.bannerRegion());
			String root = System.getProperty("burmaldaholic.projectDir", ".");
			java.nio.file.Files.writeString(java.nio.file.Path.of(root, "build", "craps_layout_" + wh[0] + ".txt"), sb.toString());
		}
	}

	/** T-D2 V1: your value pills never sit on a printed number / word, the dice zone or the total, at every size. */
	@Test
	void valuePillsStayOffThePrint() {
		for (int[] s : sizes()) {
			for (int strip : new int[] {0, 11}) {
				CrapsLayout l = CrapsLayout.of(TableGrid.of(s[0], s[1]), strip);
				List<Box> placed = new ArrayList<>();
				List<Box> stacks = new ArrayList<>(List.of(l.stack("pass", 0, Layer.MINE), l.stack("pass", 0, Layer.ODDS), l.stack("field", 0, Layer.MINE),
					l.stack("dont_pass", 0, Layer.MINE), l.stack("come", 0, Layer.MINE)));
				stacks.add(l.stack("come", 6, Layer.MINE));
				stacks.add(l.stack("come", 6, Layer.ODDS));
				for (Box st : stacks) {
					Box tag = l.valueTag(st, 4, 6 * 3 + 6, placed);
					for (Box gl : l.glyphs()) assertTrue(!tag.intersects(gl), s[0] + "x" + s[1] + " strip " + strip + " pill " + tag + " on glyph " + gl);
					assertTrue(!tag.intersects(l.total) && !tag.intersects(l.diceZone.inset(-l.die / 2)), s[0] + "x" + s[1] + " pill " + tag + " on the dice / total");
					assertTrue(tag.inside(l.felt), s[0] + "x" + s[1] + " pill " + tag + " off the felt");
				}
			}
		}
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

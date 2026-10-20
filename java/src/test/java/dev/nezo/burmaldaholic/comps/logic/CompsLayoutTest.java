package dev.nezo.burmaldaholic.comps.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Comps tab fits and never overlaps, from a narrow body up to the full 480×270 design (UI at every scale). */
class CompsLayoutTest {
	@Test
	void fitsWithoutOverlapAtEverySize() {
		int n = CompsRules.REWARDS.size();
		for (int w = 320; w <= 480; w += 16) {
			for (int h = 180; h <= 240; h += 10) {
				Box body = new Box(0, 20, w, h);
				CompsLayout.Layout l = CompsLayout.compute(body, n);
				String at = w + "x" + h;
				List<Box> targets = new ArrayList<>(l.redeem());
				targets.add(l.claim());
				for (Box b : targets) {
					assertTrue(b.inside(body), at + " target inside the body: " + b);
					assertTrue(b.w() >= 40 && b.h() >= 8, at + " target big enough: " + b);
				}
				for (int i = 0; i < targets.size(); i++) {
					for (int j = i + 1; j < targets.size(); j++) {
						assertFalse(targets.get(i).intersects(targets.get(j)), at + " targets overlap: " + targets.get(i) + " / " + targets.get(j));
					}
				}
				for (int i = 0; i < n; i++) {
					assertTrue(l.rows().get(i).inside(body), at + " row " + i);
					assertFalse(l.names().get(i).intersects(l.costs().get(i)), at + " name vs cost " + i);
					assertFalse(l.costs().get(i).intersects(l.redeem().get(i)), at + " cost vs redeem " + i);
					assertTrue(l.names().get(i).w() >= 60, at + " name column readable " + i);
				}
				for (Box line : l.lines()) {
					assertFalse(line.intersects(l.card()), at + " readout vs card");
					assertFalse(line.intersects(l.header()) || l.rows().stream().anyMatch(line::intersects), at + " left vs right column");
				}
				assertTrue(l.claim().inside(l.card()), at + " claim inside the card");
				Box result = new Box(body.x() + 8, body.bottom() - 12, body.w() - 16, 10); // CounterLayout.shell result line
				assertFalse(l.card().intersects(result) || l.rows().stream().anyMatch(result::intersects), at + " clear of the result line");
				assertFalse(l.claim().intersects(l.cardText()), at + " claim vs card text");
			}
		}
	}

	@Test
	void fullDesignUsesFullRows() {
		CompsLayout.Layout l = CompsLayout.compute(new Box(0, 20, 480, 236), CompsRules.REWARDS.size());
		assertEquals(CompsLayout.MAX_ROW - 2, l.rows().getFirst().h());
	}
}

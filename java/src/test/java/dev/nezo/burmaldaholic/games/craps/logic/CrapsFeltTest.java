package dev.nezo.burmaldaholic.games.craps.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.roulette.logic.RouletteSync;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The craps felt geometry = the generated art (visual/tables.md §4.1), the puck and the sync payloads. */
class CrapsFeltTest {
	@Test
	void rectsMatchTheArt() {
		CrapsFelt b = CrapsFelt.BIG;
		assertEquals(162, b.height());
		assertEquals(new CrapsFelt.Rect(0, 9, 64, 71), b.dontCome());
		assertEquals(new CrapsFelt.Rect(66, 9, 52, 36), b.place(4));
		assertEquals(new CrapsFelt.Rect(66 + 53 * 3, 9, 52, 36), b.place(8));
		assertEquals(new CrapsFelt.Rect(66, 48, 320, 32), b.come());
		assertEquals(new CrapsFelt.Rect(0, 83, 386, 30), b.field());
		assertEquals(new CrapsFelt.Rect(0, 83, 85, 30), b.fieldLabel());
		assertEquals(new CrapsFelt.Rect(0, 116, 386, 16), b.dontPass());
		assertEquals(new CrapsFelt.Rect(0, 135, 386, 18), b.pass());
		assertEquals(new CrapsFelt.Rect(0, 154, 386, 8), b.odds());
		assertEquals(117, CrapsFelt.COMPACT.height());
	}

	@Test
	void areasHitAndChipsSitInside() {
		for (CrapsFelt f : List.of(CrapsFelt.BIG, CrapsFelt.COMPACT)) {
			for (CrapsFelt.Area a : f.areas()) {
				assertEquals(a.kind(), f.hit(a.rect().cx(), a.rect().cy()).orElseThrow().kind());
			}
			for (String kind : List.of("pass", "dont_pass", "come", "dont_come", "field")) {
				int[] s = f.chipSpot(kind, 0, 0);
				assertTrue(f.hit(s[0], s[1] - 2).isPresent(), kind);
			}
			for (int p : CrapsFelt.POINTS) {
				int[] s = f.chipSpot("come", p, 0);
				assertTrue(f.place(p).contains(s[0], s[1] - 2), "come bet on " + p);
				int[] puck = f.puck(p);
				assertTrue(puck[0] >= 0 && puck[0] + 18 <= f.w());
			}
			double[] r = f.restRegion();
			assertTrue(f.come().contains(r[0], r[1]) && f.come().contains(r[2] + 28, r[3]), "dice rest in the Come area");
		}
	}

	@Test
	void puckEndsOnThePoint() {
		CrapsBeats.Puck out = new CrapsBeats.Puck();
		int[] off = CrapsFelt.BIG.puck(0);
		int[] on8 = CrapsFelt.BIG.puck(8);
		CrapsBeats.puck(off, 0, on8, 8, CrapsBeats.PUCK_TOTAL, out);
		assertEquals(on8[0], out.x, 0);
		assertEquals(on8[1], out.y, 0);
		assertEquals(0, out.lift, 0);
		assertEquals(CrapsBeats.FRAME_ON, out.frame);
		CrapsBeats.puck(on8, 8, off, 0, CrapsBeats.PUCK_TOTAL + 1, out);
		assertEquals(CrapsBeats.FRAME_OFF, out.frame);
		CrapsBeats.puck(off, 0, on8, 8, CrapsBeats.PUCK_RISE + CrapsBeats.PUCK_FLIP / 2.0, out);
		assertEquals(2, out.frame, "the edge frame at the thin moment");
		for (double t = 0; t < CrapsBeats.PUCK_TOTAL; t += 5) {
			CrapsBeats.puck(off, 0, on8, 8, t, out);
			assertTrue(out.lift >= -1e-9);
		}
	}

	@Test
	void syncRoundTrips() {
		CrapsSync c = new CrapsSync(12, 123_456_789_012L, 3, 4, -99, 0, 7, 2, 5, 1);
		assertEquals(c, CrapsSync.decode(c.encode()));
		RouletteSync r = new RouletteSync(2, 1L << 40, (1L << 40) + 20, 100, 17, 42, 5, 2, 9);
		assertEquals(r, RouletteSync.decode(r.encode()));
		assertEquals(RouletteSync.RESULT, RouletteSync.phaseOf("result"));
	}
}

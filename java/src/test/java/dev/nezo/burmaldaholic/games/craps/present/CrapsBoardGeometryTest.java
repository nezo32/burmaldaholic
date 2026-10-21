package dev.nezo.burmaldaholic.games.craps.present;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import org.junit.jupiter.api.Test;

/** The in-world print: inside the 2.0 × 1.4 felt, rows in order, every stack spot on its own area for 8 seats. */
class CrapsBoardGeometryTest {
	@Test
	void printFitsTheFeltAndStacksStayOnTheirAreas() {
		assertTrue(CrapsBoardGeometry.x(0) >= 0.5f - 1.0f && CrapsBoardGeometry.x(CrapsBoardGeometry.PRINT_W) <= 0.5f + 1.0f);
		assertTrue(CrapsBoardGeometry.z(0) >= 0.5f - 0.7f && CrapsBoardGeometry.z(CrapsBoardGeometry.PRINT_H) <= 0.5f + 0.7f);
		assertTrue(CrapsBoardGeometry.POINTS[0].bottom() < CrapsBoardGeometry.COME.y() && CrapsBoardGeometry.COME.bottom() < CrapsBoardGeometry.FIELD.y()
			&& CrapsBoardGeometry.FIELD.bottom() < CrapsBoardGeometry.DONT_PASS.y() && CrapsBoardGeometry.DONT_PASS.bottom() < CrapsBoardGeometry.PASS.y());
		for (int seat = 0; seat < 8; seat++) {
			for (boolean odds : new boolean[] {false, true}) {
				check("pass", 0, odds, seat, CrapsBoardGeometry.PASS);
				check("dont_pass", 0, odds, seat, CrapsBoardGeometry.DONT_PASS);
				check("field", 0, false, seat, CrapsBoardGeometry.FIELD);
				check("come", 0, false, seat, CrapsBoardGeometry.COME);
				check("dont_come", 0, odds, seat, CrapsBoardGeometry.DONT_COME);
				for (int p : CrapsLayout.POINTS) {
					check("come", p, odds, seat, CrapsBoardGeometry.point(p));
					check("dont_come", p, odds, seat, CrapsBoardGeometry.point(p));
				}
			}
		}
		for (int p : CrapsLayout.POINTS) {
			double[] c = CrapsBoardGeometry.puck(p);
			assertTrue(CrapsBoardGeometry.point(p).contains(c[0], c[1]), "puck on " + p);
		}
		double[] off = CrapsBoardGeometry.puck(0);
		assertTrue(CrapsBoardGeometry.DONT_COME.contains(off[0], off[1]));
	}

	private static void check(String kind, int point, boolean odds, int seat, Box area) {
		double[] c = CrapsBoardGeometry.stack(kind, point, odds, seat);
		assertTrue(area.contains(c[0], c[1]), kind + "@" + point + (odds ? " odds" : "") + " seat " + seat + " at " + c[0] + "," + c[1] + " not in " + area);
	}
}

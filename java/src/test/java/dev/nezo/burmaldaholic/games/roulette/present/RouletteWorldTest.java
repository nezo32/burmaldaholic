package dev.nezo.burmaldaholic.games.roulette.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import org.junit.jupiter.api.Test;

class RouletteWorldTest {
	private static final float HW = RouletteWorld.FELT_W / 2, HD = RouletteWorld.FELT_D / 2;

	@Test
	void wheelAndLayoutFitTheFeltSideBySide() {
		float wl = RouletteWorld.WHEEL_X - RouletteWorld.WHEEL_R;
		float wr = RouletteWorld.WHEEL_X + RouletteWorld.WHEEL_R;
		assertTrue(wl >= -HW + 0.02f && wr <= RouletteWorld.LAYOUT_X - 0.05f, "wheel " + wl + ".." + wr);
		assertTrue(Math.abs(RouletteWorld.WHEEL_Z) + RouletteWorld.WHEEL_R <= HD - 0.05f);
		assertTrue(RouletteWorld.LAYOUT_X + RouletteWorld.LAYOUT_W <= HW - 0.005f, "layout right " + (RouletteWorld.LAYOUT_X + RouletteWorld.LAYOUT_W));
		assertTrue(RouletteWorld.LAYOUT_Z >= -HD && RouletteWorld.LAYOUT_Z + RouletteWorld.LAYOUT_D <= HD);
		assertEquals(140, RouletteWorld.PRINT.width());
		assertEquals(52, RouletteWorld.PRINT.height());
	}

	@Test
	void everyStackIsOnThePrintedLayoutAndPlatesAreOutsideTheFelt() {
		for (Spot s : RouletteLayout.allSpots()) {
			float[] p = RouletteWorld.stack(s);
			assertTrue(p[0] >= RouletteWorld.LAYOUT_X && p[0] <= RouletteWorld.LAYOUT_X + RouletteWorld.LAYOUT_W, s.key());
			assertTrue(p[1] >= RouletteWorld.LAYOUT_Z && p[1] <= RouletteWorld.LAYOUT_Z + RouletteWorld.LAYOUT_D, s.key());
		}
		for (int n = 1; n <= 7; n++) {
			float last = -9;
			for (int i = 0; i < n; i++) {
				float[] p = RouletteWorld.plate(i, n);
				assertTrue(p[1] > HD, "plate inside the felt");
				assertTrue(p[0] > last && Math.abs(p[0]) < HW, n + "/" + i);
				last = p[0];
			}
		}
		assertEquals(0, RouletteWorld.discs(0));
		assertEquals(1, RouletteWorld.discs(1));
		assertEquals(RouletteWorld.MAX_DISCS, RouletteWorld.discs(1_000_000));
	}
}

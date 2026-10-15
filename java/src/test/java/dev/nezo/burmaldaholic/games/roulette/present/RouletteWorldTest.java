package dev.nezo.burmaldaholic.games.roulette.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.roulette.logic.RouletteSpin;
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

	/** VQA-INWORLD RO 1: ONE wheel, centred in the furniture's bowl at the floor, inside the opening. */
	@Test
	void theWheelSitsInTheFurnituresBowl() {
		var f = dev.nezo.burmaldaholic.core.table.parts.FeltFrame.of(dev.nezo.burmaldaholic.core.table.parts.TableGame.ROULETTE,
			dev.nezo.burmaldaholic.core.table.parts.TablePart.MAIN);
		assertTrue(f.hasWheel());
		float rw = RouletteWorld.rw(f.wheelDiameter());
		double opening = f.wheelDiameter() / 2 * RouletteWorld.BOWL_OPENING;
		assertTrue(rw <= opening && rw > opening * 0.95, "track wall just inside the bowl opening: " + rw + " / " + opening);
		assertEquals(13.5 / 16, RouletteWorld.BOWL_FLOOR_Y, 1e-6);
		assertTrue(RouletteWorld.surfaceY(RouletteSpin.R_POCKET, rw) > 0, "the rotor stands above the floor (no z-fight with the static art)");
		assertTrue(RouletteWorld.surfaceY(1, rw) + RouletteWorld.BOWL_FLOOR_Y < f.top() + 0.08, "the track stays inside the housing");
		for (double r = 0; r <= 1; r += 0.01) assertTrue(RouletteWorld.surfaceY(r, rw) >= RouletteWorld.surfaceY(r - 0.01, rw) - 1e-6, "rises outward");
		assertEquals(RouletteWorld.Lod.A, RouletteWorld.Lod.of(5));
		assertEquals(RouletteWorld.Lod.B, RouletteWorld.Lod.of(12));
		assertEquals(RouletteWorld.Lod.C, RouletteWorld.Lod.of(24));
	}
}

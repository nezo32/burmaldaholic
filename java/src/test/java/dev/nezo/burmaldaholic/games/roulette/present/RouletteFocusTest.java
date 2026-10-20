package dev.nezo.burmaldaholic.games.roulette.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.motion.FxGate;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.ViewportPolicy;
import dev.nezo.burmaldaholic.games.roulette.logic.RotorModel;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteSpin;
import org.junit.jupiter.api.Test;

/** ROULETTE-ANIM-SPEC §9.1-9 (focus sizes) and §9.1-10 (reduced motion). */
class RouletteFocusTest {
	private static RouletteFocus focus(int fbW, int fbH, int gui) {
		return RouletteFocus.of(RouletteLayout.of(new TableGrid(ViewportPolicy.of(fbW, fbH, gui))));
	}

	@Test
	void focusWheelIsTheLargestNativeSizeThatFitsAtEverySize() {
		int[][] rows = {
			// fbW, fbH, gui, expected art, expected multiple (0 = any)
			{3840, 2160, 9, 144, 1}, {1920, 1080, 4, 144, 1}, {1366, 768, 3, 144, 1}, {1920, 1080, 3, 208, 1}, {2560, 1440, 4, 208, 1},
			{1920, 1080, 2, 208, 2}, {3840, 2160, 4, 208, 2}, {1708, 960, 2, 0, 0}, {3840, 2160, 5, 0, 0}, {3840, 2160, 6, 0, 0}, {3840, 2160, 8, 0, 0},
			{1280, 720, 3, 144, 1}, {2560, 1440, 2, 0, 0}};
		for (int[] r : rows) {
			TableGrid g = new TableGrid(ViewportPolicy.of(r[0], r[1], r[2]));
			RouletteLayout l = RouletteLayout.of(g);
			RouletteFocus f = RouletteFocus.of(l);
			String at = r[0] + "x" + r[1] + " gui " + r[2] + " → " + g.screen.w() + "x" + g.screen.h() + " k" + g.viewport.k() + ": " + f;
			assertTrue(f.enabled() || l.wheelD >= 208, "focus grows the wheel (unless the ROOMY column already holds the 208): " + at);
			if (r[3] > 0) {
				assertEquals(r[3], f.art(), at);
				assertEquals(r[4], f.mult(), at);
			}
			Box play = g.play;
			Box b = f.box();
			assertTrue(b.x() >= play.x() && b.right() <= play.right() + 1 && b.y() >= play.y() - 1 && b.bottom() <= play.bottom() + 1, "fits the PlayArea: " + at);
			assertTrue(f.size() <= Math.min(play.h() - 16, 0.55 * play.w()) + 1e-9, at);
			int k = g.viewport.k();
			assertEquals(0, Math.abs(f.cx() * k - Math.round(f.cx() * k)), 1e-9, "origin on the physical grid: " + at);
			assertEquals(0, Math.abs(f.cy() * k - Math.round(f.cy() * k)), 1e-9, at);
			// the slide starts at the column wheel and ends at the focus spot
			assertEquals(f.colCx(), f.x(0), 1.0 / k + 1e-9);
			assertEquals(f.cx(), f.x(1), 1e-9);
			assertEquals(f.size(), f.diameter(1), 1e-9);
		}
	}

	@Test
	void roomyLayoutGrowsTheCellsAndKeepsTheBannerOffTheNumbers() {
		RouletteLayout normal = RouletteLayout.of(new TableGrid(ViewportPolicy.of(1920, 1080, 3)));
		RouletteLayout roomy = RouletteLayout.of(new TableGrid(ViewportPolicy.of(2560, 1440, 2)));
		assertTrue(roomy.grid.roomy());
		assertTrue(roomy.cells.cw() > 40, "ROOMY cells grow past the old 40 px cap: " + roomy.cells.cw());
		assertTrue(roomy.layout.w() > normal.layout.w());
		assertTrue(roomy.wheelD >= 144);
	}

	@Test
	void reducedMotionDrawsNoDecorationAndHidesTheBallUntilRest() {
		RotorModel r = RotorModel.launch(10, 36, 1);
		FxGate.Counter c = new FxGate.Counter();
		RouletteFx.Frame f = new RouletteFx.Frame();
		RouletteSpin.Sample s = new RouletteSpin.Sample();
		for (int seed = 0; seed < 20; seed++) {
			RouletteSpin sp = RouletteSpin.build(seed % 37, seed, 5, 100, 120, 140, r);
			for (double t = 0; t < sp.restMs() + 3000; t += 11) {
				sp.sample(t, s);
				RouletteFx.plan(sp, s, t, 5, true, true, c, f);
				assertEquals(t >= sp.restMs(), f.ball, "invisible before rest, then fades in: " + t);
				assertTrue(!f.streak && !f.loupe && !f.focus && !f.flash);
				assertEquals(t >= sp.restMs() ? -RouletteSpin.pocketAngle(sp.result) : -RouletteSpin.pocketAngle(5), f.rotor, 1e-9, "static wheel");
				RouletteFx.spark("sparks", true, true, c);
				RouletteFx.motion("shimmer", true, c);
				RouletteFx.diamondFlash(true, true, c);
			}
		}
		assertEquals(0, c.total(), "no streak, spark, loupe, shake or flash calls pass with reduced motion");
		// and with motion on they do pass
		RouletteSpin sp = RouletteSpin.build(3, 1, 5, 100, 120, 140, r);
		for (double t = 0; t < sp.restMs() + 500; t += 11) {
			sp.sample(t, s);
			RouletteFx.plan(sp, s, t, 5, false, true, c, f);
		}
		assertTrue(c.count("streak") > 0 && c.count("loupe") > 0 && c.count("focus") > 0);
	}
}

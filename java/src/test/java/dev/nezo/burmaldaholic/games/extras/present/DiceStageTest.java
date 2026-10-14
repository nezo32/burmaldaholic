package dev.nezo.burmaldaholic.games.extras.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import org.junit.jupiter.api.Test;

/**
 * DI §10.1-14 / PHASE2B §2.2.4: the Dice Duel arena gets a crisp art scale at every window of the resolution matrix
 * (integer physical px per art px whenever that keeps ≥ 80 % of the size), stays inside the PlayArea under the PvP
 * strip, and its origin sits on a physical pixel.
 */
class DiceStageTest {
	/** {fbW, fbH, guiScale} incl. the owner's 4K GUI Auto and 1366×768 GUI 3. */
	private static final int[][] WINDOWS = {
		{1280, 720, 3}, {1280, 720, 2}, {1920, 1080, 4}, {1920, 1080, 3}, {1920, 1080, 2}, {2560, 1440, 6}, {2560, 1440, 4}, {2560, 1440, 3},
		{2560, 1440, 2}, {3840, 2160, 9}, {3840, 2160, 8}, {3840, 2160, 6}, {3840, 2160, 5}, {3840, 2160, 4}, {3840, 2160, 3}, {3840, 2160, 2},
		{1366, 768, 3}, {1366, 768, 2}, {854, 480, 2}, {1280, 1024, 3}, {2560, 1080, 3}, {3440, 1440, 4}};

	@Test
	void crispInsideThePlayAreaUnderTheStripAtEveryWindow() {
		for (int[] w : WINDOWS) {
			Viewport vp = Viewport.of(w[0], w[1], w[2]);
			TableGrid g = new TableGrid(vp);
			DiceStage s = DiceStage.of(g);
			String at = w[0] + "x" + w[1] + " gui" + w[2] + " -> " + vp.frameW() + "x" + vp.frameH() + " k" + vp.k() + ": r " + s.art().r() + " n " + s.art().n();
			// crisp: an integer n whenever floor(r) keeps 80 % of the size
			double fl = Math.floor(s.art().r());
			if (fl >= 1 && fl >= 0.8 * s.art().r()) {
				assertTrue(s.art().integral(), at);
				assertEquals(fl, s.art().n(), 1e-12, at);
			}
			// origin on a physical pixel
			assertEquals(Math.rint(s.ox() * vp.k()), s.ox() * vp.k(), 1e-9, at);
			assertEquals(Math.rint(s.oy() * vp.k()), s.oy() * vp.k(), 1e-9, at);
			// inside the PlayArea, clear of the strip
			Box play = g.play;
			Box a = s.arena();
			assertTrue(a.x() >= play.x() && a.right() <= play.right(), at + " x " + a);
			assertTrue(a.y() >= play.y() && a.bottom() <= play.bottom(), at + " y " + a + " in " + play);
			assertTrue(s.strip().bottom() <= a.y(), at + " strip " + s.strip() + " arena " + a);
			assertTrue(s.strip().x() >= play.x() && s.strip().right() <= play.right(), at + " strip x");
			// plates fit over the top rail, inside the arena
			Box plate = s.plate(101, 120, s.roomy() ? 26 : 18);
			assertTrue(plate.y() >= a.y() && plate.bottom() <= a.bottom(), at + " plate " + plate);
		}
	}

	@Test
	void ownerFourKGuiAutoAndBigGuisScaleUp() {
		// 3840×2160 GUI Auto (427×240 k9): every art px is a whole 8 or 9 physical px, never 8.87
		DiceStage owner = DiceStage.of(new TableGrid(Viewport.of(3840, 2160, 9)));
		assertTrue(owner.art().integral(), "owner 4K Auto is crisp");
		assertTrue(owner.n() >= 8, "n " + owner.n());
		// 1920×1080 GUI 2 (960×540 k2): the arena grows well past its native 360 px (DI §4.1: ≈ 900 wide)
		DiceStage big = DiceStage.of(new TableGrid(Viewport.of(1920, 1080, 2)));
		assertTrue(big.art().integral() && big.art().w() >= 700, "960x540 arena " + big.art().w());
		// 1920×1080 GUI 4 (480×270 k4): native size at n 4
		DiceStage std = DiceStage.of(new TableGrid(Viewport.of(1920, 1080, 4)));
		assertEquals(4, std.n(), "480x270 n");
	}
}

package dev.nezo.burmaldaholic.games.blackjack.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** E5: the shoe-depth bar and tray geometry (deterministic, no screenshots). */
class ShoeViewTest {
	@Test
	void barShrinksAndMeetsTheCutMarkerWhenTheCutCardIsOut() {
		for (int w : new int[] {24, 36, 48}) {
			int prev = Integer.MAX_VALUE;
			for (int dealt = 0; dealt <= 312; dealt++) {
				ShoeView v = new ShoeView(312, dealt, 234, Math.max(0, dealt - 5));
				int fw = v.fillW(w);
				assertTrue(fw <= prev && fw >= 0 && fw <= w, "monotone fill");
				prev = fw;
				int cx = v.cutX(w);
				assertTrue(cx >= 0 && cx < w, "marker inside the bar");
				if (v.cutOut()) assertTrue(fw <= cx, "cut out: fill at or left of the marker (w " + w + ", dealt " + dealt + ")");
				assertEquals(dealt >= 234, v.cutOut());
			}
			assertEquals(w, new ShoeView(312, 0, 234, 0).fillW(w));
			assertEquals(Math.round(w * 78 / 312.0), new ShoeView(312, 0, 234, 0).cutX(w));
		}
	}

	@Test
	void trayAndUnknownShoe() {
		assertEquals(0, ShoeView.NONE.fillW(30));
		assertFalse(ShoeView.NONE.known());
		assertEquals(0, new ShoeView(312, 10, 234, 0).trayH(16));
		assertEquals(1, new ShoeView(312, 10, 234, 1).trayH(16));
		assertEquals(16, new ShoeView(312, 312, 234, 312).trayH(16));
		ShoeView clamped = new ShoeView(312, 400, 500, 999);
		assertEquals(312, clamped.dealt());
		assertEquals(0, clamped.left());
		assertEquals(312, clamped.discard());
	}
}

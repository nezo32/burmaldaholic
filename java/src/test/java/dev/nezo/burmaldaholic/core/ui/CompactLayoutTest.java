package dev.nezo.burmaldaholic.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The compact layout of the extras / PvP game screens ({@link UiLayout#fitScale}: the full 400 × 240 panel at a lower
 * whole GUI scale) and the PvP hub page grid ({@link HubLayout}).
 */
class CompactLayoutTest {
	@Test
	void fitScaleKeepsTheGuiScaleWhenThePanelFits() {
		assertEquals(2, UiLayout.fitScale(854, 480, 2, 408, 240)); // 427 × 240
		assertEquals(3, UiLayout.fitScale(1280, 800, 3, 408, 240)); // 427 × 267
		assertEquals(2, UiLayout.fitScale(1920, 1080, 2, 408, 240));
	}

	@Test
	void fitScaleStepsDownToAWholeScale() {
		assertEquals(3, UiLayout.fitScale(1280, 800, 4, 408, 240)); // GUI 320 × 200 → drawn at 3
		assertEquals(2, UiLayout.fitScale(854, 480, 3, 408, 240)); // GUI 285 × 160 → drawn at 2
		assertEquals(2, UiLayout.fitScale(854, 480, 4, 408, 240));
		assertEquals(1, UiLayout.fitScale(640, 360, 2, 408, 240)); // a small window
		assertEquals(1, UiLayout.fitScale(300, 200, 3, 408, 240)); // nothing fits: 1 (the S layout takes over)
		for (int gs = 1; gs <= 6; gs++) {
			for (int w = 320; w <= 2560; w += 97) {
				for (int h = 240; h <= 1440; h += 61) {
					int k = UiLayout.fitScale(w, h, gs, 408, 240);
					assertTrue(k >= 1 && k <= gs);
					if (k > 1) {
						assertTrue(UiLayout.fitGui(w, k) >= 408 && UiLayout.fitGui(h, k) >= 240, w + "×" + h + " @" + gs);
					}
					if (k < gs) { // the next scale up really does not fit
						assertFalse(UiLayout.fitGui(w, k + 1) >= 408 && UiLayout.fitGui(h, k + 1) >= 240);
					}
				}
			}
		}
	}

	@Test
	void fitGuiIsTheCeiling() {
		assertEquals(427, UiLayout.fitGui(854, 2));
		assertEquals(285, UiLayout.fitGui(854, 3));
		assertEquals(854, UiLayout.fitGui(854, 0));
	}

	@Test
	void hubCardsNeverOverlapAndStayInside() {
		for (int width : new int[] {352, 304, 272, 240, 180}) {
			for (boolean nemesis : new boolean[] {false, true}) {
				int[][] r = new int[HubLayout.CARDS][];
				for (int i = 0; i < HubLayout.CARDS; i++) {
					r[i] = HubLayout.card(i, width, nemesis);
					assertTrue(r[i][0] >= 0 && r[i][0] + r[i][2] <= width, "card " + i + " at width " + width);
					assertTrue(r[i][1] >= HubLayout.gridTop(nemesis) && r[i][1] + r[i][3] <= HubLayout.gridBottom(nemesis));
				}
				for (int i = 0; i < r.length; i++) {
					for (int j = i + 1; j < r.length; j++) {
						boolean overlap = r[i][0] < r[j][0] + r[j][2] && r[j][0] < r[i][0] + r[i][2] && r[i][1] < r[j][1] + r[j][3] && r[j][1] < r[i][1]
							+ r[i][3];
						assertFalse(overlap, i + "/" + j + " at " + width);
					}
				}
			}
		}
		assertTrue(HubLayout.named(HubLayout.cardW(352)));
		assertTrue(HubLayout.gridTop(true) > HubLayout.gridTop(false));
	}

	@Test
	void lobbyTextNeverRunsUnderTheJoinControl() {
		for (int width : new int[] {352, 272, 200}) {
			for (int join : new int[] {1, 40, 90, 150, 400}) {
				int[] l = HubLayout.lobby(width, join);
				assertTrue(l[1] + l[2] <= l[3] - 4, width + "/" + join);
				assertTrue(l[3] + l[4] <= width, "join inside");
				assertTrue(l[4] <= width / 2);
			}
		}
	}
}

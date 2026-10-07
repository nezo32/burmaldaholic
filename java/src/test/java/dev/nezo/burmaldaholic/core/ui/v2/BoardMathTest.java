package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.board.HoloScale;
import dev.nezo.burmaldaholic.core.ui.v2.board.PlateLayout;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The in-world board math (docs/architecture/ui-v2.md §7): legibility from 3 to 8 blocks, plate collision avoidance. */
class BoardMathTest {
	@Test
	void boardTextIsReadableFromThreeToEightBlocks() {
		for (int screenH : new int[] {1080, 1440}) {
			for (double fov : new double[] {70, 90}) {
				for (double d = 3; d <= 8; d += 0.5) {
					float board = HoloScale.textPx(HoloScale.BOARD_BASE, d, fov, screenH);
					float plate = HoloScale.textPx(HoloScale.PLATE_BASE, d, fov, screenH);
					assertTrue(HoloScale.glyphPx(board, d, fov, screenH) >= HoloScale.MIN_GLYPH_PX - 1e-6, d + " board @" + screenH + " fov " + fov);
					assertTrue(HoloScale.glyphPx(plate, d, fov, screenH) >= HoloScale.MIN_GLYPH_PX - 1e-6, d + " plate");
				}
			}
		}
		// up close the base size wins (no giant text at the table)
		assertEquals(HoloScale.BOARD_BASE, HoloScale.textPx(HoloScale.BOARD_BASE, 2, 70, 1080));
		// a 20-char line stays within ~1.7 blocks at 8 blocks on 1080p / FOV 70
		assertTrue(20 * 6 * HoloScale.textPx(HoloScale.BOARD_BASE, 8, 70, 1080) < 2.1f);
		assertEquals(HoloScale.Lod.FULL, HoloScale.lod(8));
		assertEquals(HoloScale.Lod.BOARD, HoloScale.lod(12));
		assertEquals(HoloScale.Lod.NO_TEXT, HoloScale.lod(20));
		assertEquals(HoloScale.Lod.MODEL, HoloScale.lod(40));
	}

	@Test
	void platesSitAboveTheFeltAndNeverOverlap() {
		Random r = new Random(7);
		for (int run = 0; run < 2000; run++) {
			int n = 1 + r.nextInt(8);
			float[] au = new float[n], av = new float[n], w = new float[n], h = new float[n], ou = new float[n], ov = new float[n];
			boolean[] leader = new boolean[n];
			int[] order = new int[n];
			for (int i = 0; i < n; i++) {
				au[i] = -1.2f + 2.4f * r.nextFloat(); // side-by-side bots squeeze into ±1.2 blocks
				av[i] = -0.1f + 0.3f * r.nextFloat();
				w[i] = 0.4f + 0.5f * r.nextFloat();
				h[i] = 0.2f;
				order[i] = i;
			}
			float top = 0.12f;
			PlateLayout.place(au, av, w, h, order, top, 0.02f, ou, ov, leader);
			for (int i = 0; i < n; i++) {
				assertTrue(ov[i] >= top + 0.02f - 1e-5, "above the felt content");
				for (int j = i + 1; j < n; j++) {
					boolean apart = Math.abs(ou[i] - ou[j]) >= (w[i] + w[j]) / 2 - 1e-4 || ov[i] >= ov[j] + h[j] - 1e-4 || ov[j] >= ov[i] + h[i] - 1e-4;
					assertTrue(apart, "run " + run + " plates " + i + "/" + j);
				}
			}
		}
	}

	@Test
	void aLonePlateStaysOnItsSeat() {
		float[] ou = new float[1], ov = new float[1];
		boolean[] leader = new boolean[1];
		PlateLayout.place(new float[] {0.3f}, new float[] {0.2f}, new float[] {0.5f}, new float[] {0.2f}, new int[] {0}, 0.1f, 0.02f, ou, ov, leader);
		assertEquals(0.3f, ou[0]);
		assertEquals(0.2f, ov[0]);
		assertFalse(leader[0]);
	}
}

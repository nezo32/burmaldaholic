package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.board.HoloScale;
import dev.nezo.burmaldaholic.core.ui.v2.board.PlateLayout;
import java.util.ArrayList;
import java.util.List;
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

	/** Design audit W P1: the muted ink keeps WCAG AA (4.5:1) on the 90 % panel over any background. */
	@Test
	void mutedInkKeepsContrastOnThePanel() {
		for (int bg : new int[] {0xFFFFFFFF, 0xFF000000, 0xFF87CEEB, 0xFF1E6A3E, 0xFF8A1C22, 0xFFB07A44}) {
			int panel = HoloScale.over(HoloScale.PANEL, bg);
			double c = HoloScale.contrast(HoloScale.MUTED, panel);
			assertTrue(c >= HoloScale.MIN_CONTRAST, String.format("muted on panel over %08X: %.2f", bg, c));
		}
		assertEquals(21, HoloScale.contrast(0xFFFFFFFF, 0xFF000000), 0.01);
		assertEquals(1, HoloScale.contrast(0xFF808080, 0xFF808080), 1e-9);
	}

	/** R-KW m14: the text size follows the FOV actually rendered. */
	@Test
	void effectiveFovPrefersTheCamera() {
		assertEquals(80.5, HoloScale.effectiveFov(80.5, 70), 1e-9);
		assertEquals(70, HoloScale.effectiveFov(0, 70), 1e-9);
		assertEquals(70, HoloScale.effectiveFov(Double.NaN, 70), 1e-9);
		// a wider FOV needs a bigger text px for the same cap height
		assertTrue(HoloScale.readablePx(HoloScale.PLATE_READ, 8, 90, 1080) > HoloScale.readablePx(HoloScale.PLATE_READ, 8, 70, 1080));
	}

	/**
	 * R-KW M7 / design audit W P0: across tables the shown plates never overlap, the best-ranked table keeps all of its
	 * plates, and a hidden plate needs the margin before it returns (2000 random scenes).
	 */
	@Test
	void globalResolveKeepsPlatesOfDifferentTablesApart() {
		Random r = new Random(11);
		for (int run = 0; run < 2000; run++) {
			int tables = 1 + r.nextInt(6);
			List<PlateLayout.TablePlates> list = new ArrayList<>();
			for (int t = 0; t < tables; t++) {
				PlateLayout.TablePlates tp = new PlateLayout.TablePlates();
				tp.key = t;
				tp.rank = r.nextDouble() * 10;
				float tx = -0.6f + 1.2f * r.nextFloat(), ty = -0.3f + 0.6f * r.nextFloat();
				int n = r.nextInt(6);
				// a table's own plates come disjoint from PlateLayout: a row of them
				float x = tx;
				for (int i = 0; i < n; i++) {
					float w = 0.05f + 0.1f * r.nextFloat(), h = 0.03f + 0.03f * r.nextFloat();
					tp.add(i, x, ty, x + w, ty + h, r.nextBoolean());
					x += w + 0.005f;
				}
				if (r.nextBoolean()) tp.addFixed(tx, ty + 0.1f, tx + 0.2f, ty + 0.18f);
				list.add(tp);
			}
			PlateLayout.TablePlates best = list.stream().min((a, b) -> a.rank != b.rank ? Double.compare(a.rank, b.rank) : Long.compare(a.key, b.key)).orElseThrow();
			PlateLayout.resolveGlobal(list, 0.01f);
			for (int i = 0; i < best.count; i++) assertTrue(best.shown[i], "run " + run + ": the best table keeps plate " + i);
			List<float[]> shown = new ArrayList<>();
			for (PlateLayout.TablePlates tp : list) {
				for (int i = 0; i < tp.count; i++) if (tp.shown[i]) shown.add(java.util.Arrays.copyOfRange(tp.rects, i * 4, i * 4 + 4));
			}
			for (int a = 0; a < shown.size(); a++) {
				for (int b = a + 1; b < shown.size(); b++) {
					float[] p = shown.get(a), q = shown.get(b);
					boolean apart = p[2] <= q[0] + 1e-6 || q[2] <= p[0] + 1e-6 || p[3] <= q[1] + 1e-6 || q[3] <= p[1] + 1e-6;
					assertTrue(apart, "run " + run + ": shown plates " + a + "/" + b + " overlap");
				}
			}
		}
		// hysteresis: a hidden plate 0.005 clear of a better table's plate stays hidden; one shown stays shown
		for (boolean was : new boolean[] {false, true}) {
			PlateLayout.TablePlates a = new PlateLayout.TablePlates(), b = new PlateLayout.TablePlates();
			a.key = 1;
			a.rank = 1;
			a.add(0, 0, 0, 0.1f, 0.05f, true);
			b.key = 2;
			b.rank = 2;
			b.add(0, 0.105f, 0, 0.2f, 0.05f, was);
			PlateLayout.resolveGlobal(new ArrayList<>(List.of(a, b)), 0.01f);
			assertEquals(was, b.shown[0], "hysteresis keeps the previous answer near the edge");
		}
	}
}

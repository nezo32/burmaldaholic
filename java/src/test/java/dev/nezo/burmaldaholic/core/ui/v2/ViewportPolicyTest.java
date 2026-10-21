package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * The resolution policy (docs/architecture/ui-v2.md §3.7, PHASE2B §2.4): every cell of the matrix maps to (w, h, k,
 * frame, room, tier); the player's scale is kept for 240 to 720 px high; and the kit's Dock lays out without overlap in
 * every unique frame, EN and RU.
 */
class ViewportPolicyTest {
	static final ToIntFunction<String> FONT = TableLayoutV2Test.FONT;

	/** window w, h, GUI scale; layout w, h, k; frame w, h; room; tier. */
	record Cell(int fw, int fh, int gs, int w, int h, int k, int frameW, int frameH, Room room, Tier tier) {}

	static final List<Cell> MATRIX = List.of(
		// 1280 x 720: Auto (3) = 427 x 240 k3 T1; GUI 2 = 640 x 360 k2 T3
		new Cell(1280, 720, 3, 427, 240, 3, 427, 240, Room.NORMAL, Tier.T1),
		new Cell(1280, 720, 2, 640, 360, 2, 640, 360, Room.NORMAL, Tier.T3),
		// 1920 x 1080: Auto (4) = 480 x 270 k4 T2; GUI 2 = 960 x 540 ROOMY; GUI 3 = 640 x 360
		new Cell(1920, 1080, 4, 480, 270, 4, 480, 270, Room.NORMAL, Tier.T2),
		new Cell(1920, 1080, 2, 960, 540, 2, 960, 540, Room.ROOMY, Tier.T3),
		new Cell(1920, 1080, 3, 640, 360, 3, 640, 360, Room.NORMAL, Tier.T3),
		// 2560 x 1440: Auto (6) = 427 x 240 k6; GUI 2 = 1280 x 720 framed 1067 x 600 ROOMY; GUI 3 = 854 x 480 ROOMY; GUI 4
		new Cell(2560, 1440, 6, 427, 240, 6, 427, 240, Room.NORMAL, Tier.T1),
		new Cell(2560, 1440, 2, 1280, 720, 2, 1067, 600, Room.ROOMY, Tier.T3),
		new Cell(2560, 1440, 3, 854, 480, 3, 854, 480, Room.ROOMY, Tier.T3),
		new Cell(2560, 1440, 4, 640, 360, 4, 640, 360, Room.NORMAL, Tier.T3),
		// 3840 x 2160: Auto (9, the owner's) = 427 x 240 k9; GUI 2 = 960 x 540 k4; GUI 3 = 1280 x 720 k3 framed; GUI 4; GUI 6
		new Cell(3840, 2160, 9, 427, 240, 9, 427, 240, Room.NORMAL, Tier.T1),
		new Cell(3840, 2160, 2, 960, 540, 4, 960, 540, Room.ROOMY, Tier.T3),
		new Cell(3840, 2160, 3, 1280, 720, 3, 1067, 600, Room.ROOMY, Tier.T3),
		new Cell(3840, 2160, 4, 960, 540, 4, 960, 540, Room.ROOMY, Tier.T3),
		new Cell(3840, 2160, 6, 640, 360, 6, 640, 360, Room.NORMAL, Tier.T3),
		// odd windows
		new Cell(1366, 768, 2, 683, 384, 2, 683, 384, Room.NORMAL, Tier.T3),
		new Cell(1366, 768, 3, 456, 256, 3, 456, 256, Room.NORMAL, Tier.T2),
		new Cell(1280, 1024, 4, 427, 342, 3, 427, 342, Room.NORMAL, Tier.T2), // 5:4 at GUI Auto: steps down to 400+ wide (T-K2 D5)
		new Cell(1280, 1024, 3, 427, 342, 3, 427, 342, Room.NORMAL, Tier.T2),
		new Cell(854, 480, 2, 427, 240, 2, 427, 240, Room.NORMAL, Tier.T1),
		new Cell(2560, 1080, 4, 640, 270, 4, 480, 270, Room.NORMAL, Tier.T2), // ultrawide: 16:9 frame
		new Cell(3440, 1440, 3, 1147, 480, 3, 854, 480, Room.ROOMY, Tier.T3),
		new Cell(3440, 1440, 2, 1720, 720, 2, 1067, 600, Room.ROOMY, Tier.T3));

	@Test
	void everyMatrixCell() {
		List<String> bad = new ArrayList<>();
		for (Cell c : MATRIX) {
			Viewport v = ViewportPolicy.of(c.fw(), c.fh(), c.gs());
			TableGrid g = new TableGrid(v);
			String got = v.w() + "x" + v.h() + " k" + v.k() + " frame " + v.frameW() + "x" + v.frameH() + " " + v.room() + " " + g.tier;
			String want = c.w() + "x" + c.h() + " k" + c.k() + " frame " + c.frameW() + "x" + c.frameH() + " " + c.room() + " " + c.tier();
			if (!got.equals(want)) bad.add(c.fw() + "x" + c.fh() + "@" + c.gs() + ": got " + got + ", want " + want);
		}
		assertEquals(List.of(), bad);
	}

	@Test
	void playerScaleKeptBetween240And720() {
		for (int gs = 1; gs <= 9; gs++) {
			for (int fw = 960; fw <= 7680; fw += 97) {
				for (int fh = 720; fh <= 4320; fh += 89) {
					int gh = (fh + gs - 1) / gs;
					int gw = (fw + gs - 1) / gs;
					Viewport v = ViewportPolicy.of(fw, fh, gs);
					String where = fw + "x" + fh + "@" + gs + " -> " + v;
					if (gh >= 240 && gh <= 720 && gw >= ViewportPolicy.MIN_W) assertEquals(gs, v.k(), where);
					if (gh > 720 && v.w() >= 2 * ViewportPolicy.MIN_W) { // (a portrait window may keep a taller GUI)
						assertTrue(v.h() <= 720 && v.k() % gs == 0, where);
						int prev = v.k() - gs; // the next smaller whole step leaves the GUI above 720
						assertTrue(prev == gs || (fh + prev - 1) / prev > 720, "the smallest step up: " + where);
					}
				}
			}
		}
	}

	@Test
	void offsetsAreWholeGuiPixelsAndCentred() {
		for (Cell c : MATRIX) {
			Viewport v = ViewportPolicy.of(c.fw(), c.fh(), c.gs());
			assertEquals(Math.round(v.guiOx()), v.guiOx(), 1e-6, "integer GUI offset " + c);
			assertEquals(Math.round(v.guiOy()), v.guiOy(), 1e-6, "integer GUI offset " + c);
			assertTrue(Math.abs((v.w() - v.frameW() - v.ox()) - v.ox()) <= 1, "centred +-1 " + c);
			assertTrue(Math.abs((v.h() - v.frameH() - v.oy()) - v.oy()) <= 1, "centred +-1 " + c);
			assertEquals((10 + v.ox()) * v.k(), v.windowX(10), 1e-9, "a frame point maps to its window pixel " + c);
		}
	}

	/** The Dock (betting and actions, EN and RU) fits every unique frame of the matrix. */
	@Test
	void dockFitsEveryFrame() {
		String[][] labels = {{"Bet", "Deal", "Hit", "Stand", "Double"}, {"Ставка", "Раздать", "Ещё", "Хватит", "Удвоить"}};
		for (Cell c : MATRIX) {
			TableGrid g = new TableGrid(ViewportPolicy.of(c.fw(), c.fh(), c.gs()));
			for (String[] l : labels) {
				int readout = Math.max(FONT.applyAsInt(l[0]), 2 * FONT.applyAsInt("12 500"));
				int readoutC = Math.max(FONT.applyAsInt(l[0]), FONT.applyAsInt("12 500"));
				int primary = FONT.applyAsInt(l[1]) + FONT.applyAsInt("Space") + 22;
				String where = c + " " + l[1];
				DockLayout.Dock b = DockLayout.layout(g, DockLayout.Spec.betting(5, 4, readout, readoutC, 0, primary, primary - 30).withStatus(120), 2);
				assertEquals(0, b.overflow(), where);
				LayoutAudit a = new LayoutAudit(g.dock);
				for (int i = 0; i < b.chips().size(); i++) a.add("chip" + i, b.chips().get(i));
				for (int i = 0; i < b.tools().size(); i++) a.add("tool" + i, b.tools().get(i));
				a.add("readout", b.readout()).add("status", b.status()).add("primary", b.primary());
				assertEquals(List.of(), a.problems(), where);
				int[] full = new int[3];
				int[] compact = new int[3];
				for (int i = 0; i < 3; i++) {
					compact[i] = FONT.applyAsInt(l[2 + i]) + 16;
					full[i] = compact[i] + 14;
				}
				DockLayout.Dock d = DockLayout.layout(g, DockLayout.Spec.actions(full, compact, primary, primary - 30).withStatus(120));
				assertEquals(0, d.overflow(), where);
				LayoutAudit au = new LayoutAudit(g.dock).add("status", d.status()).add("primary", d.primary());
				for (int i = 0; i < 3; i++) au.add("action" + i, d.actions().get(i));
				assertEquals(List.of(), au.problems(), where);
				assertTrue(g.screen.w() == g.viewport.frameW() && g.screen.h() == g.viewport.frameH(), "the grid lays out in the frame " + where);
			}
		}
	}
}

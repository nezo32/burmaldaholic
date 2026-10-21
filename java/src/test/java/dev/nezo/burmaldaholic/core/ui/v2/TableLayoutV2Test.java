package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * Pure layout tests of the table UI v2 kit (docs/architecture/ui-v2.md §9.1): the real-player GUI sizes (1080p and
 * 1440p at GUI 3–4, 4K, small windows) and every size in between; EN and RU label widths; no box outside the screen,
 * no two boxes overlapping, the primary always present.
 */
class TableLayoutV2Test {
	/** Vanilla font stub: 6 px per char (5 px glyph + 1 px advance; Cyrillic is the same width class). */
	static final ToIntFunction<String> FONT = s -> s.codePointCount(0, s.length()) * 6;

	/** Layout sizes: the named real setups plus a sweep of everything between 320 × 240 and 1000 × 539. */
	static List<int[]> sizes() {
		List<int[]> out = new ArrayList<>(List.of(new int[] {320, 240}, new int[] {427, 240}, new int[] {455, 256}, new int[] {480, 270},
			new int[] {512, 288}, new int[] {569, 320}, new int[] {600, 338}, new int[] {640, 360}, new int[] {683, 384}, new int[] {854, 480},
			new int[] {630, 270}, new int[] {960, 539}));
		for (int h = 240; h < 540; h += 13) {
			for (int w = Math.max(320, h * 4 / 3); w <= Math.min(1000, h * 21 / 9 + 1); w += 37) out.add(new int[] {w, h});
		}
		return out;
	}

	@Test
	void viewportKeepsThePlayersScaleAndStepsUpOrDownByWholeFactors() {
		// phase 2b (§3.7): the player's scale is kept from 240 to 720 px high; above, the smallest whole step up
		assertViewport(Viewport.of(1920, 1080, 4), 480, 270, 4);
		assertViewport(Viewport.of(1920, 1080, 3), 640, 360, 3);
		assertViewport(Viewport.of(1920, 1080, 2), 960, 540, 2); // ROOMY, no longer 480 × 270 drawn at 2×
		assertViewport(Viewport.of(1920, 1080, 1), 960, 540, 2);
		assertViewport(Viewport.of(2560, 1440, 4), 640, 360, 4);
		assertViewport(Viewport.of(2560, 1440, 3), 854, 480, 3);
		assertViewport(Viewport.of(2560, 1440, 2), 1280, 720, 2);
		assertViewport(Viewport.of(3840, 2160, 4), 960, 540, 4);
		assertViewport(Viewport.of(1280, 720, 4), 427, 240, 3); // 320 × 180 steps down
		assertViewport(Viewport.of(1366, 768, 2), 683, 384, 2);
		assertEquals(1f, Viewport.of(1920, 1080, 2).factor());
		assertEquals(0.75f, Viewport.of(1280, 720, 4).factor());
		for (int gs = 1; gs <= 9; gs++) {
			for (int fw = 640; fw <= 3840; fw += 131) {
				for (int fh = 480; fh <= 2160; fh += 97) {
					Viewport v = Viewport.of(fw, fh, gs);
					assertTrue(v.k() >= 1, "k");
					assertTrue(v.h() <= ViewportPolicy.MAX_H || v.w() < 2 * Viewport.MIN_W, fw + "x" + fh + "@" + gs + " -> " + v);
					assertTrue(v.frameH() <= Frame.CAP_H && v.frameW() * 9 <= v.frameH() * 16 + 9, fw + "x" + fh + "@" + gs + " frame " + v);
					int gh = (fh + gs - 1) / gs;
					int gw = (fw + gs - 1) / gs;
					if (gh >= 240 && gh <= 720 && gw >= ViewportPolicy.MIN_W) assertEquals(gs, v.k(), fw + "x" + fh + "@" + gs + ": the player's scale is kept");
					if (fw >= Viewport.MIN_W && fh >= Viewport.MIN_H) {
						assertTrue(v.w() >= Viewport.MIN_W && v.h() >= Viewport.MIN_H, fw + "x" + fh + "@" + gs + " -> " + v);
					}
				}
			}
		}
	}

	private static void assertViewport(Viewport v, int w, int h, int k) {
		assertEquals(w, v.w(), "w " + v);
		assertEquals(h, v.h(), "h " + v);
		assertEquals(k, v.k(), "k " + v);
	}

	@Test
	void tiersMatchTheSpec() {
		assertEquals(Tier.T1, TableGrid.of(427, 240).tier);
		assertEquals(Tier.T1, TableGrid.of(354, 266).tier); // a 4:3 window keeps the tier its width can hold
		assertEquals(Tier.T2, TableGrid.of(480, 270).tier);
		assertEquals(Tier.T3, TableGrid.of(640, 360).tier);
		TableGrid g = TableGrid.of(480, 270);
		assertEquals(new Box(0, 0, 480, 22), g.topBar);
		assertEquals(new Box(0, 22, 480, 204), g.play);
		assertEquals(new Box(0, 226, 480, 44), g.dock);
		TableGrid g3 = TableGrid.of(640, 360);
		assertEquals(new Box(0, 304, 640, 56), g3.dock);
	}

	@Test
	void bettingDockFitsEveryScreenInEnglishAndRussian() {
		String[][] labels = {{"Bet", "Deal", "Leave"}, {"Ставка", "Раздать", "Уйти"}};
		for (int[] wh : sizes()) {
			for (String[] l : labels) {
				TableGrid g = TableGrid.of(wh[0], wh[1]);
				int readout = Math.max(FONT.applyAsInt(l[0]), 2 * FONT.applyAsInt("12 500"));
				int readoutCompact = Math.max(FONT.applyAsInt(l[0]), FONT.applyAsInt("12 500"));
				int primary = FONT.applyAsInt(l[1]) + FONT.applyAsInt("Space") + 4 + 16;
				int primaryCompact = FONT.applyAsInt(l[1]) + 16;
				int secondary = FONT.applyAsInt(l[2]) + 16;
				for (int sec : new int[] {0, secondary}) {
					DockLayout.Dock d = DockLayout.layout(g, DockLayout.Spec.betting(5, 4, readout, readoutCompact, sec, primary, primaryCompact), 4);
					String where = wh[0] + "x" + wh[1] + " " + l[1] + " sec=" + sec;
					assertEquals(0, d.overflow(), where);
					assertTrue(!d.compactReadout() || wh[0] < 400, where + ": the 2× bet value stays from 400 px up");
					LayoutAudit a = new LayoutAudit(g.dock);
					for (int i = 0; i < d.chips().size(); i++) a.add("chip" + i, d.chips().get(i));
					for (int i = 0; i < d.tools().size(); i++) a.add("tool" + i, d.tools().get(i));
					a.add("readout", d.readout()).add("status", d.status()).add("secondary", d.secondary()).add("primary", d.primary());
					assertEquals(List.of(), a.problems(), where);
					assertTrue(d.tools().size() >= 2, where + ": Undo and Clear always stay");
					assertTrue(d.chips().size() >= 3, where);
					assertTrue(d.status().w() >= DockLayout.minStatus(g.tier), where);
					assertTrue(d.firstChip() <= 4 && 4 < d.firstChip() + d.chips().size(), where + ": the selected chip stays visible");
					assertTrue(d.primary().right() == g.dock.w() - g.m(), where + ": the primary sits at the right edge");
				}
			}
		}
		// 480 × 270 shows everything
		DockLayout.Dock d = DockLayout.layout(TableGrid.of(480, 270), DockLayout.Spec.betting(5, 4, 60, 30, 0, 70, 50));
		assertEquals(5, d.chips().size());
		assertEquals(4, d.tools().size());
		assertEquals(new Box(382, 233, 92, 30), d.primary());
	}

	@Test
	void actionDockFitsFiveRussianActionsDownTo427() {
		String[][] actions = {{"Hit", "Stand", "Double", "Split", "Surrender"}, {"Ещё", "Хватит", "Удвоить", "Сплит", "Сдаться"}};
		for (int[] wh : sizes()) {
			if (wh[0] < 427) continue; // 320-wide windows: T1 compact buttons are checked below
			for (String[] l : actions) {
				TableGrid g = TableGrid.of(wh[0], wh[1]);
				int n = 4; // the primary carries the 5th
				int[] full = new int[n];
				int[] compact = new int[n];
				for (int i = 0; i < n; i++) {
					compact[i] = FONT.applyAsInt(l[i]) + 10;
					full[i] = compact[i] + 12;
				}
				int primary = FONT.applyAsInt(l[4]) + 12 + 16;
				DockLayout.Dock d = DockLayout.layout(g, DockLayout.Spec.actions(full, compact, primary, primary - 12));
				String where = wh[0] + "x" + wh[1] + " " + l[0];
				assertEquals(0, d.overflow(), where);
				LayoutAudit a = new LayoutAudit(g.dock).add("status", d.status()).add("primary", d.primary());
				for (int i = 0; i < n; i++) a.add("action" + i, d.actions().get(i));
				assertEquals(List.of(), a.problems(), where);
			}
		}
	}

	@Test
	void crescentSeatsNeverOverlapAtAnySizeWithFiveCardHands() {
		String[][] stamps = {{"BLACKJACK", "+750"}, {"БЛЭКДЖЕК", "+750"}, {"BUST", "-500"}, {"SURRENDER", "-25"}, {"СДАЧА", "-25"}};
		int heroAt2x = 0, cases = 0;
		for (int[] wh : sizes()) {
			TableGrid g = TableGrid.of(wh[0], wh[1]);
			for (int seats = 1; seats <= 7; seats++) {
				int n = SeatColumn.crescent(g, seats).length;
				int hero = n / 2;
				for (String[] st : stamps) {
					for (int cards = 2; cards <= 5; cards++) {
						int[] cardsBySlot = new int[n];
						int[][] levels = new int[n][];
						int[] value = new int[n];
						for (int i = 0; i < n; i++) {
							cardsBySlot[i] = seats % 2 == 0 && i == n - 1 ? -1 : cards; // even tables leave one slot empty
							// the stamp carries the word; the net goes into the seat's value pill ("50" -> "+75")
							levels[i] = i == hero ? new int[] {2 * FONT.applyAsInt(st[0]) + 8, FONT.applyAsInt(st[0]) + 6} : new int[] {FONT.applyAsInt(st[0]) + 6};
							value[i] = FONT.applyAsInt("+" + st[1]) + 4;
						}
						int[] chosen = new int[n];
						SeatColumn[] cols = SeatColumn.table(g, seats, cardsBySlot, hero, levels, value, chosen);
						LayoutAudit a = new LayoutAudit(g.felt());
						for (int i = 0; i < n; i++) {
							if (cols[i] == null) continue;
							a.add(i + ".cards", cols[i].cards()).add(i + ".outcome", cols[i].outcome()).add(i + ".stack", cols[i].stack())
								.add(i + ".value", cols[i].value()).add(i + ".plate", cols[i].plate());
						}
						assertEquals(List.of(), a.problems(), wh[0] + "x" + wh[1] + " seats=" + seats + " cards=" + cards + " " + st[0]);
						cases++;
						if (chosen[hero] == 0) heroAt2x++;
					}
				}
			}
		}
		// the hero's 2x stamp is the norm, not the exception
		assertTrue(heroAt2x * 10 >= cases * 7, heroAt2x + " of " + cases);
		// and at the real setups (480 x 270, 640 x 360, five seats, two cards) every seat shows its full stamp
		for (int[] wh : new int[][] {{480, 270}, {640, 360}}) {
			TableGrid g = TableGrid.of(wh[0], wh[1]);
			int[] chosen = new int[5];
			int[][] levels = new int[5][];
			for (int i = 0; i < 5; i++) levels[i] = i == 2 ? new int[] {2 * 54 + 8, 60} : new int[] {60};
			SeatColumn.table(g, 5, new int[] {2, 2, 2, 2, 2}, 2, levels, new int[] {22, 22, 22, 22, 22}, chosen);
			assertTrue(java.util.Arrays.equals(new int[] {0, 0, 0, 0, 0}, chosen), wh[0] + ": " + java.util.Arrays.toString(chosen));
		}
	}

	@Test
	void hitMapPicksTheTopmostTargetAndKeepsDisabledOnes() {
		HitMap m = new HitMap().add("felt.spot", new Box(0, 0, 100, 100), true).add("primary", new Box(50, 50, 20, 10), false);
		assertEquals("primary", m.at(55, 55).orElseThrow().id());
		assertTrue(!m.at(55, 55).orElseThrow().enabled());
		assertEquals("felt.spot", m.at(10, 10).orElseThrow().id());
		assertTrue(m.at(100, 100).isEmpty(), "half-open");
	}

	@Test
	void aDisabledGateNeedsAReason() {
		assertThrows(IllegalArgumentException.class, () -> new Gate("deal", "gui.x.deal", null, false, null, null));
		Gate g = Gate.off("deal", "gui.x.deal", "gui.burmaldaholic.core.v2.reason.place_bet");
		assertTrue(!g.enabled());
		assertEquals(null, Gate.on("deal", "gui.x.deal").reasonKey());
	}

	@Test
	void textFitEllipsisesByCodePoints() {
		assertEquals("Steve", TextFit.fit("Steve", 30, FONT));
		assertEquals("Lady…", TextFit.fit("Lady Luckless", 30, FONT));
		assertEquals("", TextFit.fit("Lady", 3, FONT));
	}
}

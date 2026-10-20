package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * The Dock's stepper slot (review R-AB M3) and the status width (review R-KW B2, DES 3): when the status cannot get the
 * width its lines want, the actions go compact first, then line 2 moves to the hint strip above the Dock. Every size of
 * docs/architecture/ui-v2.md §11.1, EN and RU.
 */
class DockLayoutTest {
	static final ToIntFunction<String> FONT = TableLayoutV2Test.FONT;

	/** Status lines of the real games (EN / RU) — the long ones that truncated in the phase-1 shots. */
	static final String[][] STATUS = {{"Chips add to your bet", "Place a bet first"}, {"Фишки → ставка", "Кликните поле"},
		{"Insure for 25 or play on", "Shuffling the shoe"}, {"Страховка за 25 или играть дальше", "Ставки: 12 с"}};

	static int statusW(TableGrid g, String l1, String l2) {
		return Math.min(g.dock.w() / 2, Math.max(FONT.applyAsInt(l1), FONT.applyAsInt(l2)));
	}

	@Test
	void stepperSlotSitsBetweenStatusAndActions() {
		for (int[] wh : TableLayoutV2Test.sizes()) {
			if (wh[0] < 427) continue;
			TableGrid g = TableGrid.of(wh[0], wh[1]);
			// poker: Fold / Call 40 as actions, Raise to N is the primary
			for (String[] l : new String[][] {{"Fold", "Call 40", "Raise to 120"}, {"Пас", "Колл 40", "Рейз до 120"}}) {
				int[] full = new int[2];
				int[] compact = new int[2];
				for (int i = 0; i < 2; i++) {
					compact[i] = FONT.applyAsInt(l[i]) + 16;
					full[i] = compact[i] + 14;
				}
				int stepper = 2 * g.tier.buttonH + 4 + FONT.applyAsInt("888 888") + 8;
				int primary = FONT.applyAsInt(l[2]) + 16;
				DockLayout.Dock d = DockLayout.layout(g, DockLayout.Spec.actions(full, compact, stepper, primary + 14, primary));
				String where = wh[0] + "x" + wh[1] + " " + l[0];
				assertEquals(0, d.overflow(), where);
				assertEquals(stepper, d.stepper().w(), where);
				LayoutAudit a = new LayoutAudit(g.dock).add("status", d.status()).add("stepper", d.stepper()).add("primary", d.primary());
				for (int i = 0; i < 2; i++) a.add("action" + i, d.actions().get(i));
				assertEquals(List.of(), a.problems(), where);
				assertTrue(d.status().right() <= d.stepper().x() && d.stepper().right() <= d.actions().get(0).x(), where);
			}
		}
		DockLayout.Dock none = DockLayout.layout(TableGrid.of(480, 270), DockLayout.Spec.actions(new int[] {60}, new int[] {40}, 70, 50));
		assertTrue(none.stepper().isEmpty());
	}

	@Test
	void aWiderStatusKeepsTheKeyPlatesAndUsesTheHintStrip() {
		// T-K2 R3 P2-a: the key plates stay whenever they fit beside the minimum status column; text that does not fit the
		// column goes into the hint strip (StatusPlan), never at the cost of the plates
		TableGrid g = TableGrid.of(480, 270);
		int[] full = {90, 90, 90};
		int[] compact = {50, 50, 50};
		DockLayout.Spec s = DockLayout.Spec.actions(full, compact, 70, 50);
		DockLayout.Dock plain = DockLayout.layout(g, s);
		assertFalse(plain.compactActions());
		assertTrue(plain.hintStrip().isEmpty());
		DockLayout.Dock wants = DockLayout.layout(g, s.withStatus(plain.status().w() + 20));
		assertFalse(wants.compactActions(), "the plates stay");
		assertEquals(plain.actions(), wants.actions(), "the action row does not move for the status");
		assertFalse(wants.hintStrip().isEmpty(), "the status that does not fit goes to the strip");
		assertEquals(g.dock.y(), wants.hintStrip().bottom());
		assertTrue(wants.hintStrip().inside(g.play));
		assertEquals(DockLayout.HINT_STRIP_H, wants.hintStrip().h());
		DockLayout.Dock fits = DockLayout.layout(g, s.withStatus(plain.status().w()));
		assertTrue(fits.hintStrip().isEmpty(), "a status that fits its column opens no strip");
		// the plates go only when they do not fit beside the minimum column
		DockLayout.Dock tight = DockLayout.layout(g, DockLayout.Spec.actions(new int[] {120, 120, 120}, compact, 70, 50));
		assertTrue(tight.compactActions());
	}

	@Test
	void everySizeEnAndRuFitsTheStatusOrStrips() {
		for (int[] wh : TableLayoutV2Test.sizes()) {
			TableGrid g = TableGrid.of(wh[0], wh[1]);
			for (String[] st : STATUS) {
				int want = statusW(g, st[0], st[1]);
				// betting
				int readout = Math.max(FONT.applyAsInt("Ставка"), 2 * FONT.applyAsInt("12 500"));
				int readoutC = Math.max(FONT.applyAsInt("Ставка"), FONT.applyAsInt("12 500"));
				DockLayout.Dock b = DockLayout.layout(g, DockLayout.Spec.betting(5, 4, readout, readoutC, 0, 90, 60).withStatus(want), 2);
				String where = wh[0] + "x" + wh[1] + " " + st[0];
				assertEquals(0, b.overflow(), where);
				check(g, b, want, where + " betting");
				// actions
				if (wh[0] < 427) continue;
				int[] full = new int[4];
				int[] compact = new int[4];
				String[] l = {"Ещё", "Хватит", "Удвоить", "Разделить"};
				for (int i = 0; i < 4; i++) {
					compact[i] = FONT.applyAsInt(l[i]) + 16;
					full[i] = compact[i] + 14;
				}
				DockLayout.Dock a = DockLayout.layout(g, DockLayout.Spec.actions(full, compact, FONT.applyAsInt("Сдаться") + 30, FONT.applyAsInt("Сдаться") + 16)
					.withStatus(want));
				assertEquals(0, a.overflow(), where);
				check(g, a, want, where + " actions");
				LayoutAudit au = new LayoutAudit(g.dock).add("status", a.status()).add("primary", a.primary());
				for (int i = 0; i < 4; i++) au.add("action" + i, a.actions().get(i));
				assertEquals(List.of(), au.problems(), where);
			}
		}
	}

	private static void check(TableGrid g, DockLayout.Dock d, int want, String where) {
		if (d.status().w() >= want) {
			assertTrue(d.hintStrip().isEmpty(), where + ": the status fits, no strip");
		} else {
			assertFalse(d.hintStrip().isEmpty(), where + ": the status is short, line 2 goes to the strip");
			assertTrue(d.hintStrip().inside(g.play), where);
			assertTrue(d.hintStrip().w() >= Math.min(want, g.dock.w() - 2 * g.m()), where);
		}
		assertTrue(d.status().w() >= DockLayout.minStatus(g.tier), where);
	}
}

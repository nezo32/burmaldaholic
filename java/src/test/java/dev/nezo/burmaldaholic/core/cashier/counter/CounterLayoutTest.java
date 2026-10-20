package dev.nezo.burmaldaholic.core.cashier.counter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The counter screen's pure layout at every GUI size of the test matrix (ui-v2.md §11.1: 320 × 240 up to 4K steps):
 * every box inside the panel, no two interactive boxes overlap, the narrow store switches to 4 columns + a strip.
 */
class CounterLayoutTest {
	private static final int[][] SIZES = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}, {683, 384}, {360, 270}};

	@Test
	void viewportsOfRealWindowsAreCovered() {
		// 1080p GUI 2, 4K GUI auto (4) and 1366 x 768 GUI 2: the shell sees these virtual GUIs
		for (int[] w : new int[][] {{1920, 1080, 2}, {3840, 2160, 4}, {1366, 768, 2}, {1280, 720, 3}}) {
			Viewport vp = Viewport.of(w[0], w[1], w[2]);
			check(vp.w(), vp.h());
		}
	}

	@Test
	void everySize() {
		for (int[] s : SIZES) check(s[0], s[1]);
	}

	private static void check(int w, int h) {
		String at = w + "x" + h;
		CounterLayout.Shell sh = CounterLayout.shell(w, h, new int[] {60, 50, 56});
		Box gui = new Box(0, 0, w, h);
		assertTrue(sh.panel().inside(gui), at);
		List<Box> shellBoxes = new ArrayList<>(sh.tabs());
		shellBoxes.add(sh.close());
		shellBoxes.add(sh.readout());
		noOverlap(shellBoxes, at + " top bar");
		for (Box b : shellBoxes) assertTrue(b.isEmpty() || b.inside(sh.top()), at + " in top bar " + b);
		assertTrue(sh.body().inside(sh.panel()), at);
		assertFalse(sh.body().intersects(sh.top()), at);
		// exchange with 4 rows (most rows of any dimension by default)
		CounterLayout.Exchange ex = CounterLayout.exchange(sh.body(), 4);
		assertTrue(ex.money().inside(sh.body()) && ex.rows().inside(sh.body()), at + " exchange in body");
		assertFalse(ex.money().intersects(ex.rows()), at);
		assertEquals(4, CounterLayout.visibleRows(ex), at + " all 4 rows visible");
		assertFalse(ex.rows().intersects(sh.result()), at + " rows clear of the result line");
		if (w >= CounterLayout.DESIGN_W) {
			assertFalse(ex.tray().isEmpty(), at + " wide layouts show the counting tray");
			assertTrue(ex.tray().inside(sh.body()) && !ex.tray().intersects(ex.money()) && !ex.tray().intersects(ex.rows())
				&& !ex.tray().intersects(sh.result()), at + " tray placement " + ex.tray());
		}
		CounterLayout.Money m = CounterLayout.money(ex.money(), 8); // 8 denominations once HR adds 1000/5000/25000
		List<Box> money = new ArrayList<>(m.chips());
		money.addAll(List.of(m.depositAll(), m.depositHeld(), m.max(), m.clear(), m.withdraw(), m.amount(), m.info()));
		noOverlap(money, at + " money");
		for (Box b : money) assertTrue(b.inside(ex.money()), at + " money box " + b);
		for (Box c : m.chips()) assertTrue(c.w() >= 18, at + " chip button wide enough for '+25k' " + c);
		for (int i = 0; i < 4; i++) {
			Box row = new Box(ex.rows().x(), ex.rows().y() + i * CounterLayout.ROW_H, ex.rows().w(), CounterLayout.ROW_H);
			CounterLayout.RowBoxes rb = CounterLayout.row(row);
			noOverlap(rb.all(), at + " row");
			for (Box b : rb.all()) assertTrue(b.inside(row), at + " row box " + b);
			assertTrue(rb.have().w() >= 30, at + " have text room " + rb.have());
		}
		// store
		CounterLayout.Store st = CounterLayout.store(sh.body(), 8);
		assertEquals(w < CounterLayout.DESIGN_W ? 4 : 6, st.cols(), at);
		assertEquals(w < CounterLayout.DESIGN_W, st.strip(), at);
		assertEquals(8, st.categories().size(), at + " every category visible");
		assertTrue(st.rows() >= 3, at + " grid rows " + st.rows());
		List<Box> store = new ArrayList<>(st.categories());
		store.add(st.search());
		for (int i = 0; i < st.cols() * st.rows(); i++) store.add(st.tile(i));
		store.add(st.detail());
		noOverlap(store, at + " store");
		for (Box b : store) assertTrue(b.inside(sh.body()), at + " store box " + b);
		for (int i = 0; i < st.cols() * st.rows(); i++) assertTrue(st.tile(i).inside(st.grid()), at);
		assertFalse(st.detail().intersects(sh.result()), at + " detail clear of the result line");
		CounterLayout.Detail d = CounterLayout.detail(st);
		List<Box> det = new ArrayList<>(d.qty());
		det.addAll(List.of(d.icon(), d.name(), d.line2(), d.total(), d.buy()));
		if (!d.line3().isEmpty()) det.add(d.line3());
		noOverlap(det, at + " detail");
		for (Box b : det) assertTrue(b.inside(st.detail()), at + " detail box " + b);
		assertTrue(d.name().w() >= 60, at + " name room " + d.name());
	}

	private static void noOverlap(List<Box> boxes, String what) {
		for (int i = 0; i < boxes.size(); i++) {
			for (int j = i + 1; j < boxes.size(); j++) {
				Box a = boxes.get(i), b = boxes.get(j);
				if (a.isEmpty() || b.isEmpty()) continue;
				assertFalse(a.intersects(b), what + ": " + a + " overlaps " + b);
			}
		}
	}
}

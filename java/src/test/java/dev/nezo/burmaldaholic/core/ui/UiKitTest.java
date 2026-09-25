package dev.nezo.burmaldaholic.core.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure maths of the casino UI kit (lane J-L2): layouts, motion, ticker, floaters, tabs, chip columns, loan look. */
class UiKitTest {
	@Test
	void layoutSizesFollowExtrasSpec() {
		assertEquals(UiLayout.Size.L, UiLayout.size(427, 240));
		assertEquals(UiLayout.Size.L, UiLayout.size(416, 240));
		assertEquals(UiLayout.Size.M, UiLayout.size(415, 240));
		assertEquals(UiLayout.Size.M, UiLayout.size(320, 240));
		assertEquals(UiLayout.Size.S, UiLayout.size(319, 240));
		assertEquals(UiLayout.Size.S, UiLayout.size(480, 239));
	}

	@Test
	void panelIsCentredAndClamped() {
		assertEquals(new UiLayout.Rect(13, 0, 400, 240), UiLayout.panel(427, 240, 400, 240));
		assertEquals(new UiLayout.Rect(40, 60, 400, 240), UiLayout.panel(480, 360, 400, 240));
		UiLayout.Rect m = UiLayout.panel(360, 250, 400, 240);
		assertEquals(320, m.w());
		assertEquals(20, m.x());
		UiLayout.Rect s = UiLayout.panel(300, 200, 400, 240);
		assertTrue(s.w() >= UiLayout.MIN_W && s.h() >= UiLayout.MIN_H);
		assertTrue(s.x() >= 0 && s.y() >= 0);
	}

	@Test
	void entranceEndsExactlyAtRest() {
		assertEquals(0.97f, UiLayout.entranceScale(0, false), 1e-6);
		assertEquals(1f, UiLayout.entranceScale(UiLayout.ENTRANCE_MS, false), 1e-6);
		assertEquals(1f, UiLayout.entranceScale(0, true), 1e-6);
		assertEquals(1f, UiLayout.entranceAlpha(0, true), 1e-6);
		assertEquals(0f, UiLayout.entranceAlpha(0, false), 1e-6);
		float prev = 0;
		for (int t = 0; t <= 150; t += 10) {
			float s = UiLayout.entranceScale(t, false);
			assertTrue(s >= prev);
			prev = s;
		}
	}

	@Test
	void buttonMotionIsWholePixelsAndSettles() {
		assertEquals(0, UiLayout.hoverLift(-1, false));
		assertEquals(0, UiLayout.hoverLift(0, false));
		assertEquals(1, UiLayout.hoverLift(80, false));
		assertEquals(1, UiLayout.pressDepth(0));
		assertEquals(0, UiLayout.pressDepth(60));
		assertEquals(0, UiLayout.pressDepth(-1));
		for (int t = 0; t < 300; t += 5) assertTrue(Math.abs(UiLayout.shake(t, false)) <= 2);
		assertEquals(0, UiLayout.shake(240, false));
		assertEquals(0, UiLayout.shake(40, true));
		assertEquals(4, UiLayout.errorSlide(0, false));
		assertEquals(0, UiLayout.errorSlide(150, false));
		assertEquals(0, UiLayout.errorSlide(0, true));
	}

	@Test
	void barsAndWidths() {
		assertEquals(0, UiLayout.fillPixels(100, 0));
		assertEquals(1, UiLayout.fillPixels(100, 0.001));
		assertEquals(100, UiLayout.fillPixels(100, 1));
		assertEquals(70, UiLayout.fillPixels(100, 0.7));
		assertEquals(0.5, UiLayout.barFill(0.5, 10_000, false), 1e-9);
		assertEquals(0.0, UiLayout.barFill(0.5, 0, false), 1e-9);
		assertEquals(0.5, UiLayout.barFill(0.5, 0, true), 1e-9);
		assertEquals(60, UiLayout.buttonWidth(60, 20, 0));
		assertEquals(20 + 8 + 16 + 3, UiLayout.buttonWidth(10, 20, 16));
		assertEquals(110, UiLayout.bannerWidth(40, 300));
		assertEquals(232, UiLayout.bannerWidth(200, 300));
		assertEquals(220, UiLayout.bannerWidth(200, 220));
	}

	@Test
	void tickerEndsOnTargetAndIsMonotonic() {
		BalanceTicker t = new BalanceTicker();
		t.retarget(1000, 0, false);
		assertEquals(1000, t.value(0)); // first sync snaps
		t.retarget(13_500, 100, false);
		int d = BalanceTicker.durationMs(12_500);
		assertEquals(Math.min(1100, Math.round(250 + 150 * Math.log10(12_500))), d);
		long prev = 1000;
		for (long ms = 100; ms <= 100 + d; ms += 16) {
			long v = t.value(ms);
			assertTrue(v >= prev && v <= 13_500);
			prev = v;
		}
		assertEquals(13_500, t.value(100 + d));
		assertEquals(0f, t.tint(100 + d), 1e-6);
		assertEquals(1f, t.tint(101), 1e-6);
		// a loss is capped at 600 ms and counts down
		assertEquals(600, BalanceTicker.durationMs(-1_000_000));
		assertEquals(250, BalanceTicker.durationMs(-1));
		t.retarget(500, 5000, false);
		assertEquals(-1, t.direction(5001));
		assertEquals(500, t.value(5600));
		// retarget mid-tween restarts from the shown value (no jump back)
		t.retarget(10_000, 10_000, false);
		long mid = t.value(10_100);
		t.retarget(20_000, 10_100, false);
		assertEquals(mid, t.value(10_100));
		// reduce motion snaps
		t.retarget(7, 20_000, true);
		assertEquals(7, t.value(20_000));
	}

	@Test
	void floatersMergeStackAndExpire() {
		DeltaFloaters f = new DeltaFloaters();
		f.add(100, 0);
		f.add(20, 300); // merged
		assertEquals(1, f.size(300));
		assertEquals(120, f.amount(0));
		f.add(-50, 1000); // other sign: new label
		assertEquals(2, f.size(1000));
		assertEquals(-50, f.amount(0));
		assertEquals(-DeltaFloaters.STACK, f.yOffset(1, 1000, true));
		assertTrue(f.yOffset(0, 1000 + DeltaFloaters.LIFE_MS - 1, false) <= -DeltaFloaters.RISE + 1);
		f.add(5, 1500);
		f.add(5, 2000);
		f.add(5, 2500);
		assertTrue(f.size(2500) <= DeltaFloaters.MAX);
		assertEquals(1f, f.alpha(0, 2500), 1e-6);
		assertEquals(0, f.size(2500 + DeltaFloaters.LIFE_MS));
		f.add(0, 0);
		assertEquals(0, f.size(4000));
	}

	@Test
	void tabsFitNineTabsWithTheLongestRussianName() {
		// «Достижения» ≈ 60 px (extras.md §8.2): 9 tabs + the selected label fit the 368 px page
		LedgerLayout.Tabs t = LedgerLayout.tabs(9, 4, 60, LedgerLayout.TABS_X, 368);
		assertTrue(t.labelShown());
		int end = t.x()[8] + t.w()[8];
		assertTrue(end <= LedgerLayout.TABS_X + 368, "tabs end at " + end);
		assertEquals(LedgerLayout.TAB_LABEL_EXTRA + 60, t.w()[4]);
		for (int i = 1; i < 9; i++) assertEquals(t.x()[i - 1] + t.w()[i - 1] + LedgerLayout.TAB_GAP, t.x()[i]);
		// too many tabs: icon-only, narrowed, never below 18
		LedgerLayout.Tabs many = LedgerLayout.tabs(16, 0, 60, 0, 368);
		assertFalse(many.labelShown());
		assertTrue(many.w()[0] >= 18);
		assertEquals(368, LedgerLayout.pageW(400));
		assertEquals(177, LedgerLayout.pageH(240));
	}

	@Test
	void chipColumns() {
		assertArrayEquals(new long[] {3, 4, 0, 2, 0}, ChipColumns.greedy(1910));
		assertArrayEquals(new long[] {0, 0, 0, 0, 0}, ChipColumns.greedy(-5));
		assertEquals(12, ChipColumns.visible(40));
		assertArrayEquals(new int[] {12, 3, 0, 1}, ChipColumns.scaled(new long[] {40, 10, 0, 1}, 12));
		assertArrayEquals(new int[] {5, 2}, ChipColumns.scaled(new long[] {5, 2}, 12));
	}

	@Test
	void loanLook() {
		assertEquals(LoanLook.Mood.DEFAULT, LoanLook.mood("default"));
		assertEquals(LoanLook.Mood.NONE, LoanLook.mood("weird"));
		assertEquals(LoanLook.Mood.ACTIVE, LoanLook.mood(10, false));
		assertEquals(LoanLook.Mood.NONE, LoanLook.mood(0, true));
		assertEquals("gui.burmaldaholic.loan.shark.cooldown", LoanLook.Mood.COOLDOWN.key());
		assertEquals(0.0, LoanLook.debtFill(0, 100), 1e-9);
		assertEquals(1.0, LoanLook.debtFill(5250, 2500), 1e-9);
		assertEquals(0.5, LoanLook.debtFill(75, 100), 1e-9);
		assertEquals(0, LoanLook.overdueDays(0));
		assertEquals(1, LoanLook.overdueDays(5));
		assertEquals(2, LoanLook.overdueDays(48_000));
	}
}

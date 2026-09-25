package dev.nezo.burmaldaholic.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Early tester pass over the J-L2 UI kit maths: the panel at GUI scales 1–4 on common and odd window sizes, the header
 * (banner never under the balance plaque), whole-pixel motion that settles, reduce motion.
 */
class UiKitReviewTest {
	private static final int[][] WINDOWS = {{854, 480}, {1280, 800}, {1281, 721}, {1366, 767}, {1920, 1080}, {2560, 1440}, {3840, 2160}, {1024, 768},
		{800, 600}, {1279, 719}, {1600, 901}, {1283, 961}};

	/** Vanilla's scaled size: framebuffer / scale rounded up. */
	private static int gui(int px, int scale) {
		return (px + scale - 1) / scale;
	}

	@Test
	void panelFitsEveryGuiScaleAndOddWindow() {
		for (int[] win : WINDOWS) {
			for (int scale = 1; scale <= 4; scale++) {
				int w = gui(win[0], scale);
				int h = gui(win[1], scale);
				UiLayout.Size size = UiLayout.size(w, h);
				UiLayout.Rect r = UiLayout.panel(w, h, UiLayout.FULL_W, UiLayout.FULL_H);
				String at = win[0] + "x" + win[1] + " @" + scale + " -> gui " + w + "x" + h + " " + size + " " + r;
				assertTrue(r.x() >= 0 && r.y() >= 0, at);
				if (size != UiLayout.Size.S) {
					assertTrue(r.right() <= w && r.bottom() <= h, "inside the screen: " + at);
				}
				// centred to the pixel: left and right margins differ by at most 1 (odd sizes)
				if (r.w() <= w) assertTrue(Math.abs((w - r.right()) - r.x()) <= 1, "centred x: " + at);
				if (r.h() <= h) assertTrue(Math.abs((h - r.bottom()) - r.y()) <= 1, "centred y: " + at);
				if (size == UiLayout.Size.L) assertEquals(new UiLayout.Rect(r.x(), r.y(), 400, 240), r, at);
				if (size == UiLayout.Size.M) assertEquals(320, r.w(), at);
				// the 12 px frame border fits twice with room left for the content
				assertTrue(r.w() >= 2 * 12 + 1 && r.h() >= 2 * 12 + 1, at);
			}
		}
	}

	@Test
	void autoGuiScaleNeverLandsInTheForcedLayout() {
		// vanilla Auto picks the largest scale that keeps >= 320 x 240
		for (int[] win : WINDOWS) {
			int scale = 1;
			while (win[0] / (scale + 1) >= 320 && win[1] / (scale + 1) >= 240) scale++;
			assertTrue(UiLayout.size(gui(win[0], scale), gui(win[1], scale)) != UiLayout.Size.S, win[0] + "x" + win[1] + " auto " + scale);
		}
	}

	@Test
	void bannerStaysClearOfTheBalancePlaque() {
		for (int panelW : new int[] {300, 316, 320, 400, 427}) {
			int maxW = UiLayout.bannerMaxW(panelW, true);
			for (int text = 0; text <= 400; text += 7) {
				int w = UiLayout.bannerWidth(text, maxW);
				int left = panelW / 2 - w / 2;
				int right = left + w;
				int plaqueLeft = panelW - UiLayout.PLAQUE_RIGHT - UiLayout.PLAQUE_W;
				assertTrue(right <= plaqueLeft - 3, "panel " + panelW + " text " + text + ": banner ends " + right + ", plaque at " + plaqueLeft);
				assertTrue(left >= 0);
			}
			int free = UiLayout.bannerMaxW(panelW, false);
			assertTrue(free <= 220 && panelW / 2 - free / 2 >= 12, "inside the frame border without a plaque: " + panelW);
		}
		assertEquals(180, UiLayout.bannerMaxW(400, true));
	}

	@Test
	void motionIsWholePixelsSettlesAndHonoursReduceMotion() {
		for (long ms = -5; ms < 1000; ms += 3) {
			int lift = UiLayout.hoverLift(ms, false);
			assertTrue(lift == 0 || lift == 1);
			assertTrue(Math.abs(UiLayout.shake(ms, false)) <= 2);
			assertEquals(0, UiLayout.shake(ms, true), "reduce motion drops the shake");
			assertEquals(0, UiLayout.errorSlide(ms, true));
			assertEquals(1f, UiLayout.entranceScale(ms, true));
			assertEquals(1f, UiLayout.entranceAlpha(ms, true));
			assertEquals(1f, UiLayout.tabFadeIn(ms, true));
			assertEquals(0, UiLayout.tabSlide(ms, 1, true));
			assertEquals(0.4, UiLayout.barFill(0.4, ms, true), 0.0);
		}
		assertEquals(1, UiLayout.hoverLift(0, true), "reduce motion keeps the lift (no ease)");
		// every motion ends exactly at rest
		assertEquals(1f, UiLayout.entranceScale(UiLayout.ENTRANCE_MS, false));
		assertEquals(1f, UiLayout.entranceAlpha(UiLayout.ENTRANCE_MS, false));
		assertEquals(1, UiLayout.hoverLift(UiLayout.HOVER_MS, false));
		assertEquals(0, UiLayout.pressDepth(UiLayout.PRESS_MS));
		assertEquals(1, UiLayout.pressDepth(0));
		assertEquals(0, UiLayout.shake(UiLayout.SHAKE_MS, false));
		assertEquals(0, UiLayout.errorSlide(UiLayout.ERROR_SLIDE_MS, false));
		assertEquals(1f, UiLayout.tabFadeIn(1000, false));
		assertEquals(0, UiLayout.tabSlide(120, -1, false));
		assertEquals(0.4, UiLayout.barFill(0.4, 600, false), 0.0);
	}

	@Test
	void widthsFitTheLongestRussianLabels() {
		// RU budget 1.45 × EN (global.md §2.2): a 60-px EN label grows to 87 px; the button grows, never clips
		assertEquals(95, UiLayout.buttonWidth(60, 87, 0));
		assertEquals(95 + 19, UiLayout.buttonWidth(60, 87, 16));
		assertEquals(60, UiLayout.buttonWidth(60, 10, 0));
		assertEquals(110, UiLayout.bannerWidth(0, 220));
		assertEquals(220, UiLayout.bannerWidth(1000, 220), "capped: the title is cut with an ellipsis");
		assertEquals(1, UiLayout.fillPixels(100, 1e-9));
		assertEquals(100, UiLayout.fillPixels(100, 1));
		assertEquals(0, UiLayout.fillPixels(0, 0.5));
	}

	@Test
	void tickerEndsOnTheServerBalanceUnderReduceMotionAndRetargets() {
		BalanceTicker t = new BalanceTicker();
		t.retarget(1000, 0, false);
		assertEquals(1000, t.value(0), "first sync snaps");
		t.retarget(5000, 100, false);
		t.retarget(200, 300, false); // mid-tween loss: restarts from what is shown
		long prev = Long.MAX_VALUE;
		for (long ms = 300; ms < 2000; ms += 10) {
			long v = t.value(ms);
			assertTrue(v <= prev, "a loss counts down monotonically");
			prev = v;
		}
		assertEquals(200, t.value(2000));
		t.retarget(999, 2000, true);
		assertEquals(999, t.value(2000), "reduce motion snaps");
	}

	@Test
	void narrationIsThrottledNewestWins() {
		NarrationThrottle<String> n = new NarrationThrottle<>();
		assertEquals("a", n.offer("a", 1000));
		assertEquals(null, n.offer("b", 1100));
		assertEquals(null, n.offer("c", 1200), "coalesced");
		assertEquals(null, n.poll(1599));
		assertEquals("c", n.poll(1600), "the newest pending one, once 600 ms passed");
		assertEquals(null, n.poll(5000));
		assertEquals(null, n.offer("d", 2000));
		assertEquals("e", n.offer("e", 2200));
		assertTrue(!n.hasPending(), "speaking drops the older pending line");
		assertEquals("x", new NarrationThrottle<String>().offer("x", Long.MIN_VALUE / 2), "the first line is never held");
	}
}

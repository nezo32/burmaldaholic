package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The frame cap (docs/architecture/ui-v2.md §3.7): at most 600 high, at most 16:9, centred, whole px. */
class FrameTest {
	@Test
	void capsHeightAndAspect() {
		assertEquals(new Frame(106, 60, 1067, 600), Frame.of(1280, 720));
		assertEquals(new Frame(0, 0, 960, 540), Frame.of(960, 540));
		assertEquals(new Frame(80, 0, 480, 270), Frame.of(640, 270)); // ultrawide
		assertEquals(new Frame(0, 0, 427, 240), Frame.of(427, 240));
		assertEquals(new Frame(0, 0, 320, 256), Frame.of(320, 256)); // 5:4: narrower than 16:9 is kept
		assertFalse(Frame.of(960, 540).framed(960, 540));
		assertTrue(Frame.of(1280, 720).framed(1280, 720));
	}

	@Test
	void everyFrameIsCentredAndInside() {
		for (int w = 320; w <= 2000; w += 7) {
			for (int h = 240; h <= 800; h += 5) {
				Frame f = Frame.of(w, h);
				assertTrue(f.x() >= 0 && f.y() >= 0 && f.x() + f.w() <= w && f.y() + f.h() <= h, w + "x" + h + " " + f);
				assertTrue(Math.abs((w - f.w() - f.x()) - f.x()) <= 1 && Math.abs((h - f.h() - f.y()) - f.y()) <= 1, "centred " + f);
				assertTrue(f.h() <= Frame.CAP_H && f.w() * 9 <= f.h() * 16 + 8, "cap " + f);
				if (h <= Frame.CAP_H && w * 9 <= h * 16) assertEquals(new Frame(0, 0, w, h), f, "no frame needed " + w + "x" + h);
			}
		}
	}
}

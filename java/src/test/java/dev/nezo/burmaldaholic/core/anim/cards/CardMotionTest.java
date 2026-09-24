package dev.nezo.burmaldaholic.core.anim.cards;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** C0: the card motion primitives end on their targets, the squeeze is monotonic and card-independent, layout maths. */
class CardMotionTest {
	private final CardMotion.Pose p = new CardMotion.Pose();

	@Test
	void dealEndsExactlyOnTheSlotFaceDown() {
		for (int seed : new int[] {0, 1, -7, 123456789}) {
			CardMotion.deal(p, 358, 30, 166, 14, 1, 0, seed, false);
			assertEquals(166, p.x, 1e-9);
			assertEquals(14, p.y, 1e-9);
			assertEquals(0, p.rot, 1e-9);
			assertEquals(1, p.scale, 1e-9);
			CardMotion.deal(p, 358, 30, 144, 36, 1, 90, seed, false);
			assertEquals(90, p.rot, 1e-9, "sideways card ends at 90°");
			CardMotion.deal(p, 358, 30, 166, 14, 0.5, 0, seed, false);
			assertTrue(!p.face, "a dealt card flies face down");
		}
		CardMotion.deal(p, 358, 30, 166, 14, 0, 0, 5, false);
		assertEquals(358, p.x, 1e-9);
		assertEquals(0.82, p.scale, 1e-9);
		// the arc rises above the chord
		CardMotion.deal(p, 0, 100, 200, 100, 0.5, 0, 5, false);
		assertTrue(p.y < 100);
		CardMotion.deal(p, 0, 100, 200, 100, 0.3, 0, 5, true);
		assertEquals(200, p.x, 1e-9, "reduced motion: at the slot");
		assertEquals(0.3, p.alpha, 1e-9);
	}

	@Test
	void flipSwapsAtTheHalfAndEndsFlat() {
		CardMotion.flip(p, 0.25);
		assertTrue(!p.face && p.scaleX < 1);
		CardMotion.flip(p, 0.5);
		assertTrue(p.face);
		assertEquals(0, p.scaleX, 1e-9);
		CardMotion.flip(p, 1);
		assertTrue(p.face);
		assertEquals(1, p.scaleX, 1e-9);
		assertEquals(0, p.y, 1e-9);
		assertEquals(0, p.highlight, 1e-9);
	}

	@Test
	void squeezeIsMonotonicAndSnaps() {
		double prev = -1;
		for (int i = 0; i <= 1000; i++) {
			double f = CardMotion.squeeze(i / 1000.0);
			assertTrue(f >= prev - 1e-12, "monotonic at " + i);
			assertTrue(f >= 0 && f <= 1);
			prev = f;
		}
		assertEquals(0, CardMotion.squeeze(0), 1e-12);
		assertEquals(0.18, CardMotion.squeeze(0.3), 1e-12, "the breath");
		assertEquals(1, CardMotion.squeeze(0.88), 1e-12);
		assertEquals(1, CardMotion.squeeze(1), 1e-12);
		assertEquals(1, CardMotion.squeezePop(1), 1e-9);
	}

	@Test
	void gatherAndStampsAndTagsEnd() {
		CardMotion.gather(p, 100, 100, 20, 20, 1, true, false);
		assertEquals(20, p.x, 1e-9);
		assertEquals(0, p.alpha, 1e-9);
		CardMotion.stamp(p, 1, -6, false);
		assertEquals(1, p.scale, 1e-9);
		assertEquals(1, p.alpha, 1e-9);
		assertEquals(-6, p.rot, 1e-9);
		CardMotion.stamp(p, 1, -6, true);
		assertEquals(0, p.rot, 1e-9, "reduced motion: no rotation");
		assertEquals(0, CardMotion.tagAlpha(CardMotion.tagTotalMs() + 1), 1e-9);
		assertEquals(1, CardMotion.tagAlpha(CardMotion.TAG_IN_MS + 10), 1e-9);
		assertEquals(0, CardMotion.bustShakePx(1));
		assertEquals(0, CardMotion.gatherDelay(0));
		assertEquals(CardMotion.GATHER_MS + 2 * CardMotion.GATHER_STAGGER_MS, CardMotion.gatherTotal(3));
		CardMotion.chipFlight(p, 0, 0, 50, 60, 1, false);
		assertEquals(50, p.x, 1e-9);
		assertEquals(60, p.y, 1e-9);
		assertEquals(1, p.scale, 1e-9);
	}

	@Test
	void glowPulseRangeAndSettings() {
		for (long ms = 0; ms < 2000; ms += 37) {
			double a = CardMotion.glowAlpha(ms, false, true);
			assertTrue(a >= 0.35 - 1e-9 && a <= 0.8 + 1e-9);
		}
		assertEquals(0.6, CardMotion.glowAlpha(123, true, true), 1e-12);
		assertEquals(0.5, CardMotion.glowAlpha(123, false, false), 1e-12);
	}

	@Test
	void jitterIsBoundedAndSeeded() {
		for (int s = -1000; s < 1000; s += 7) {
			int seed = CardMotion.seed(42, 7, s);
			assertTrue(Math.abs(CardMotion.jitterDeg(seed)) <= 2.0 + 1e-9);
			assertTrue(Math.abs(CardMotion.jitterPx(seed, true)) <= 1);
			assertTrue(CardMotion.dealPitch(seed) >= 0.95f && CardMotion.dealPitch(seed) <= 1.05f);
			assertEquals(seed, CardMotion.seed(42, 7, s));
		}
	}

	@Test
	void layoutAnchors() {
		assertEquals(1, CardLayout.scale(427, 240));
		assertEquals(0, CardLayout.scale(284, 160));
		assertEquals(2, CardLayout.scale(960, 540));
		assertEquals(280, CardLayout.c(408));
		assertEquals(140, CardLayout.c(204));
		// one hand centred on 204, two at 136 / 214, 3–4: the active L hand at 170
		assertEquals(204 - (37 + 14) / 2, CardLayout.Blackjack.handX(0, 1, 0, 2, false));
		assertEquals(136, CardLayout.Blackjack.handX(0, 2, 1, 2, false));
		assertEquals(214, CardLayout.Blackjack.handX(1, 2, 1, 2, false));
		assertEquals(170, CardLayout.Blackjack.handX(2, 4, 2, 2, false));
		assertEquals(1, CardLayout.Blackjack.handSize(0, 3, 1, false));
		assertEquals(0, CardLayout.Blackjack.handSize(1, 3, 1, false));
		assertEquals(166, CardLayout.Blackjack.dealerX(0, false));
		assertEquals(188, CardLayout.Blackjack.dealerX(1, false));
		assertEquals(16, CardLayout.Blackjack.dealerY(1, false));
		assertArrayEquals(new int[] {-6, 82}, CardLayout.Blackjack.plate(0));
		assertArrayEquals(new int[] {340, 82}, CardLayout.Blackjack.plate(3));
		assertEquals(64, CardLayout.Baccarat.cardX(0, 0));
		assertEquals(104, CardLayout.Baccarat.cardX(0, 1));
		assertEquals(144, CardLayout.Baccarat.cardX(0, 2));
		assertEquals(300, CardLayout.Baccarat.cardX(1, 2));
	}

	@Test
	void chipDiscsAreGreedyLargestFirst() {
		assertArrayEquals(new int[] {100, 100, 25, 5, 5}, CardLayout.discs(235, 5));
		assertArrayEquals(new int[] {25, 25}, CardLayout.discs(50, 5));
		assertArrayEquals(new int[] {500, 500, 500, 500, 500}, CardLayout.discs(10_000, 5));
		assertArrayEquals(new int[0], CardLayout.discs(0, 5));
		assertArrayEquals(new int[] {1}, CardLayout.discs(1, 5));
	}

	@Test
	void dealerGestureIds() {
		for (DealerGesture gz : DealerGesture.values()) assertEquals(gz, DealerGesture.byId(gz.id()));
		assertEquals(DealerGesture.NONE, DealerGesture.byId(99));
		assertEquals(6, DealerGesture.DEAL.ticks());
	}
}

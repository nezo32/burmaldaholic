package dev.nezo.burmaldaholic.core.anim.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Early tester pass over the J-L4 card kit (pure parts): every primitive lands EXACTLY (not within an epsilon) on its
 * target at t = 1, past it (any speed factor, a skip) and at NaN; reduced motion follows cards.md §0.6; the §0.3
 * repetition counts; the layout anchors stay inside the canvas; gestures round-trip through the synced byte.
 */
class CardKitReviewTest {
	private final CardMotion.Pose p = new CardMotion.Pose();
	/** t = 1, speed factors 1.5 / 2 / 3 past the end, a skip (huge), and a 0 / 0 progress. */
	private static final double[] ENDS = {1, 1.0000001, 1.5, 2, 3, 1e9, Double.POSITIVE_INFINITY, Double.NaN};
	/** Fractional anchors: a + (b − a)·1 misses b by an ulp for some of these. */
	private static final double[][] TRIPS = {{358, 30, 166, 14}, {0.1, 0.7, 0.3, 0.2}, {350.35, 30.1, 144.7, 36.3}, {-3.3, 1e-3, 1 / 3.0, 2 / 3.0}};

	@Test
	void everyTravelEndsExactlyOnItsTargetAtAnySpeedOrSkip() {
		for (double[] tr : TRIPS) {
			for (double t : ENDS) {
				for (boolean reduced : new boolean[] {false, true}) {
					String at = " at t=" + t + " reduced=" + reduced + " " + java.util.Arrays.toString(tr);
					CardMotion.deal(p, tr[0], tr[1], tr[2], tr[3], t, 90, 12345, reduced);
					assertEquals(tr[2], p.x, 0.0, "deal x" + at);
					assertEquals(tr[3], p.y, 0.0, "deal y" + at);
					assertEquals(90, p.rot, 0.0, "deal rot" + at);
					assertEquals(1, p.scale, 0.0, "deal scale" + at);
					assertEquals(1, p.alpha, 0.0, "deal alpha" + at);
					assertTrue(!p.face, "a deal ends face down (the flip reveals)" + at);

					CardMotion.chipFlight(p, tr[0], tr[1], tr[2], tr[3], t, reduced);
					assertEquals(tr[2], p.x, 0.0, "chip x" + at);
					assertEquals(tr[3], p.y, 0.0, "chip y" + at);
					assertEquals(1, p.scale, 0.0, "chip scale" + at);
					assertEquals(1, p.alpha, 0.0, "chip alpha" + at);

					CardMotion.gather(p, tr[0], tr[1], tr[2], tr[3], t, true, reduced);
					assertEquals(0, p.alpha, 0.0, "gather gone" + at);
					if (!reduced) {
						assertEquals(tr[2], p.x, 0.0, "gather x" + at);
						assertEquals(tr[3], p.y, 0.0, "gather y" + at);
					}
					CardMotion.muck(p, tr[0], tr[1], tr[2], tr[3], t, reduced);
					assertEquals(0, p.alpha, 0.0, "muck gone" + at);
					if (!reduced) assertEquals(tr[2], p.x, 0.0, "muck x" + at);
					CardMotion.sweep(p, tr[0], tr[1], tr[2], tr[3], t, reduced);
					assertEquals(0, p.alpha, 0.0, "sweep gone" + at);
					if (!reduced) assertEquals(tr[3], p.y, 0.0, "sweep y" + at);

					CardMotion.stamp(p, t, -6, reduced);
					assertEquals(1, p.scale, 0.0, "stamp scale" + at);
					assertEquals(1, p.alpha, 0.0, "stamp alpha" + at);
					assertEquals(reduced ? 0 : -6, p.rot, 0.0, "stamp rot" + at);
				}
				CardMotion.flip(p, t);
				assertEquals(1, p.scaleX, 0.0);
				assertEquals(0, p.y, 0.0);
				assertEquals(0, p.highlight, 0.0);
				assertTrue(p.face);
				assertEquals(1, CardMotion.reducedFlipFace(t), 0.0);
				assertEquals(1, CardMotion.squeeze(t), 0.0);
				assertEquals(1, CardMotion.squeezePop(t), 0.0, "squeeze pop at " + t);
				assertEquals(0, CardMotion.squeezeLift(t), 0.0);
				assertEquals(1, CardMotion.slide(t), 0.0);
				assertEquals(0, CardMotion.settleDy(t), 0.0);
				assertEquals(0, CardMotion.badgeOldDy(t) + 6, 0.0);
				assertEquals(0, CardMotion.badgeNewDy(t), 0.0);
				assertEquals(1, CardMotion.desat(t), 0.0);
				assertEquals(0, CardMotion.bustShakePx(t));
				assertEquals(0, CardMotion.knockDy(t));
				assertEquals(0, CardMotion.wigglePx(t));
				assertEquals(1, CardMotion.pass(t), 0.0);
				assertEquals(0, CardMotion.beadDropDy(t), 1e-12, "bead drop at " + t);
				assertEquals(300, CardMotion.shimmerX(t, 300, 8), 0.0);
			}
		}
	}

	@Test
	void progressIsClampedAndSkipsToTheEnd() {
		assertEquals(1, CardMotion.progress(1000, 0, 240), 0.0);
		assertEquals(0, CardMotion.progress(-5, 0, 240), 0.0);
		assertEquals(1, CardMotion.progress(0, 0, 0), 0.0, "zero-length tween is settled at once");
		assertEquals(0, CardMotion.progress(-1, 0, 0), 0.0);
		// a speed factor is applied by scaling elapsed time: still exactly 1 at and after the end
		for (double speed : new double[] {0.5, 1, 1.5, 2, 3}) {
			double end = 240 / speed;
			assertEquals(1, CardMotion.progress(end * speed, 0, 240), 0.0);
			assertEquals(1, CardMotion.progress((end + 17) * speed, 0, 240), 0.0);
		}
		assertTrue(CardMotion.playTween(479, 240));
		assertTrue(!CardMotion.playTween(480, 240), "catch-up: settled after 2 × duration");
	}

	@Test
	void reducedMotionFollowsTheAccessibilityTable() {
		// K1: 120 ms fade-in at the slot (t in deal units: 120 / 240 = 0.5)
		CardMotion.deal(p, 358, 30, 166, 14, 60.0 / CardMotion.DEAL_MS, 0, 1, true);
		assertEquals(166, p.x, 0.0, "no travel");
		assertEquals(0.5, p.alpha, 1e-12);
		CardMotion.deal(p, 358, 30, 166, 14, CardMotion.REDUCED_FADE_MS / (double) CardMotion.DEAL_MS, 0, 1, true);
		assertEquals(1, p.alpha, 0.0, "faded in after 120 ms");
		// K5 / K6: 150 ms fade in place
		CardMotion.gather(p, 50, 60, 0, 0, CardMotion.REDUCED_GATHER_MS / (double) CardMotion.GATHER_MS, true, true);
		assertEquals(0, p.alpha, 0.0);
		assertEquals(50, p.x, 0.0, "in place");
		CardMotion.muck(p, 50, 60, 0, 0, CardMotion.REDUCED_GATHER_MS / (double) CardMotion.MUCK_MS, true);
		assertEquals(0, p.alpha, 0.0);
		// chips: 120 ms fade
		CardMotion.chipFlight(p, 0, 0, 40, 40, CardMotion.REDUCED_FADE_MS / (double) CardMotion.CHIP_FLIGHT_MS, true);
		assertEquals(1, p.alpha, 0.0);
		assertEquals(40, p.x, 0.0, "at the destination");
		CardMotion.sweep(p, 10, 10, 40, 40, CardMotion.REDUCED_FADE_MS / (double) CardMotion.SWEEP_MS, true);
		assertEquals(0, p.alpha, 0.0);
		// K7: 120 ms fade, no rotation, no scale
		CardMotion.stamp(p, 60.0 / CardMotion.STAMP_MS, -6, true);
		assertEquals(0.5, p.alpha, 1e-12);
		assertEquals(0, p.rot, 0.0);
		assertEquals(1, p.scale, 0.0);
		// K9: static 0.6 reduced, 0.5 flashes off
		for (long ms = 0; ms < 3000; ms += 111) {
			assertEquals(0.6, CardMotion.glowAlpha(ms, true, true), 0.0);
			assertEquals(0.5, CardMotion.glowAlpha(ms, false, false), 0.0);
		}
	}

	@Test
	void repetitionCountsOfSection03() {
		// K13 knock: the plate jumps down and back TWICE
		assertEquals(2, downSteps(t -> CardMotion.knockDy(t) != 0), "knock raps");
		// bust shake: 3 times ±2 px
		int max = 0;
		for (int i = 0; i <= 1000; i++) max = Math.max(max, Math.abs(CardMotion.bustShakePx(i / 1000.0)));
		assertEquals(2, max);
		// push wiggle: ±1 px twice
		assertEquals(2, downSteps(t -> CardMotion.wigglePx(t) > 0), "wiggles");
		// K9 never flashes more than 3 times per second (1 Hz)
		int peaks = 0;
		double prev = CardMotion.glowAlpha(0, false, true);
		boolean rising = true;
		for (long ms = 1; ms <= 1000; ms++) {
			double a = CardMotion.glowAlpha(ms, false, true);
			if (rising && a < prev) peaks++;
			rising = a >= prev;
			prev = a;
		}
		assertTrue(peaks <= 3, "glow peaks per second: " + peaks);
	}

	private static int downSteps(java.util.function.DoublePredicate on) {
		int n = 0;
		boolean was = false;
		for (int i = 0; i <= 1000; i++) {
			boolean now = on.test(i / 1000.0);
			if (now && !was) n++;
			was = now;
		}
		return n;
	}

	@Test
	void flipSwapsExactlyOnceAtTheHalf() {
		boolean seenFace = false;
		for (int i = 0; i <= 280; i++) {
			CardMotion.flip(p, i / 280.0);
			if (seenFace) assertTrue(p.face, "never back to the back after the swap");
			if (p.face) {
				seenFace = true;
				assertTrue(i >= 140, "the face never shows before scaleX = 0");
			}
			assertTrue(p.scaleX <= 1.062, "outBack s 1.3 peaks at ≈ 1.06 (the spec rounds to 1.05)");
		}
	}

	@Test
	void squeezeIsTheSameForEveryCardAndMonotonic() {
		// pure function of u only (§0.7.3 / §0.7.7): no card argument exists; check monotonic + snap position
		double prev = 0;
		for (int i = 0; i <= 10_000; i++) {
			double f = CardMotion.squeeze(i / 10_000.0);
			assertTrue(f + 1e-15 >= prev);
			prev = f;
		}
		assertEquals(0.72, CardMotion.squeeze(0.8799999), 1e-5);
	}

	@Test
	void gatherTimingAndDeal() {
		assertEquals(0, CardMotion.gatherTotal(0));
		assertEquals(CardMotion.GATHER_MS, CardMotion.gatherTotal(1));
		assertEquals(0, CardMotion.gatherDelay(-3));
		assertEquals(CardMotion.DEAL_MS + CardMotion.FLIP_MS, CardMotion.READABLE_MS);
		assertEquals(520, CardMotion.READABLE_MS, "cards.md §0.6: readable 520 ms after the beat");
		// jitter is deterministic from public values and differs across slots
		int a = CardMotion.seed(7, 3, 1);
		int b = CardMotion.seed(7, 3, 2);
		assertTrue(a != b);
		assertEquals(a, CardMotion.seed(7, 3, 1));
		for (int s = 0; s < 500; s++) {
			int px = CardMotion.jitterPx(CardMotion.seed(1, 2, s), false);
			assertTrue(px >= -1 && px <= 1);
		}
	}

	@Test
	void anchorsStayOnTheCanvas() {
		for (int n = 1; n <= 4; n++) {
			for (int h = 0; h < n; h++) {
				for (boolean compact : new boolean[] {false, true}) {
					int active = Math.min(n - 1, 1);
					int size = CardLayout.Blackjack.handSize(h, n, active, compact);
					int x = CardLayout.Blackjack.handX(h, n, active, 3, compact);
					int w = CardLayout.handWidth(3, CardLayout.cardW(size), compact ? CardLayout.Blackjack.C_STEP : CardLayout.Blackjack.ME_STEP);
					int tableW = compact ? CardLayout.TABLE_CW : CardLayout.TABLE_W;
					assertTrue(x >= 0 && x + w <= tableW, "hand " + h + "/" + n + " compact=" + compact + " at " + x + " w " + w);
				}
			}
		}
		for (int i = 0; i < 6; i++) {
			assertTrue(CardLayout.Blackjack.dealerX(i, false) + CardLayout.L_W <= CardLayout.Blackjack.SHOE_X, "dealer card " + i + " clears the shoe");
		}
		assertEquals(CardLayout.CANVAS_W, 427);
		assertEquals(1, CardLayout.scale(427, 240));
		assertEquals(1, CardLayout.scale(853, 479), "odd sizes round down");
		assertEquals(0, CardLayout.scale(426, 240));
		assertEquals(4, CardLayout.scale(1708, 960));
	}

	@Test
	void discsNeverExceedTheCapAndStayLargestFirst() {
		for (long amount : new long[] {1, 4, 5, 6, 99, 100, 101, 499, 500, 777, 12_345, Long.MAX_VALUE}) {
			int[] d = CardLayout.discs(amount, 5);
			assertTrue(d.length >= 1 && d.length <= 5);
			for (int i = 1; i < d.length; i++) assertTrue(d[i] <= d[i - 1]);
		}
		assertEquals(0, CardLayout.discs(-5, 5).length);
		assertEquals(1, CardLayout.discs(3, 0).length, "max 0 still shows one disc");
	}

	@Test
	void gesturesRoundTripThroughTheSyncedByte() {
		for (DealerGesture gz : DealerGesture.values()) {
			byte b = gz.id();
			assertEquals(gz, DealerGesture.byId(b));
			assertEquals(1, gz.progress(gz.ms + 1), 0.0);
			assertEquals(1, gz.progress(Double.MAX_VALUE), 0.0);
			assertTrue(gz.ticks() * 50 >= gz.ms);
		}
		assertEquals(DealerGesture.NONE, DealerGesture.byId(-1));
		assertEquals(1, DealerGesture.NONE.progress(0), 0.0, "idle is always settled");
	}
}

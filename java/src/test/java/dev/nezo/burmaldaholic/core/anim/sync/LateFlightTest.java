package dev.nezo.burmaldaholic.core.anim.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * SY-SPEC §3 Cards row: a card waits in the shoe until first seen, then flies; a late card still flies, compressed to
 * ≥ 120 ms; an on-time card is the shared flight; a card that had landed before this viewer arrived is settled. Under
 * the NetLag profile (300 ms ± 150, bunched every second) at 60 fps no frame moves the card more than 1.15 × the
 * fastest legit flight (the 120 ms one) allows, and every card ends in its slot.
 */
class LateFlightTest {
	private static final double DEAL = 260;
	private static final double FRAME = 1000 / 60.0;

	@Test
	void onTimeIsTheSharedFlight() {
		for (double t = -50; t < 400; t += 7) {
			assertEquals(Math.max(0, Math.min(1, (t) / DEAL)), LateFlight.progress(1000 + t, 1000, 990, DEAL), 1e-9, "t " + t);
			assertEquals(LateFlight.progress(1000 + t, 1000, 1000, DEAL), LateFlight.progress(1000 + t, 1000, 900, DEAL), 1e-9);
		}
	}

	@Test
	void aLateCardStillFliesAtLeast120Ms() {
		for (double late : new double[] {1, 100, 200, 259, 260, 500, 1500, 5000}) {
			double seen = 1000 + late;
			assertEquals(0, LateFlight.progress(seen, 1000, seen, DEAL), 1e-9, "starts in the shoe at the first sight, late " + late);
			double dur = Math.max(LateFlight.MIN_MS, DEAL - late);
			assertTrue(LateFlight.progress(seen + dur - 1, 1000, seen, DEAL) < 1, "still flying just before the end, late " + late);
			assertEquals(1, LateFlight.progress(seen + dur, 1000, seen, DEAL), 1e-9, "lands, late " + late);
			assertTrue(dur >= 120);
		}
	}

	@Test
	void aLandedCardIsSettled() {
		assertEquals(1, LateFlight.progress(5000, 1000, Double.NEGATIVE_INFINITY, DEAL), 0);
	}

	/** NetLag profile: per-frame motion within 1.15 × the fastest legit (120 ms, outCubic) flight; ends at 1. */
	@Test
	void underNetLagNoFrameJumps() {
		java.util.Random rnd = new java.util.Random(7);
		// outCubic's top speed is 3 / duration (at u = 0)
		double bound = 1.15 * 3 / LateFlight.MIN_MS * FRAME;
		for (int run = 0; run < 400; run++) {
			double deal = 2000 + rnd.nextInt(3000);
			double arrival = Math.ceil((deal - 200 + 300 + (rnd.nextDouble() * 2 - 1) * 150) / 1000) * 1000; // sent 4 ticks early
			double seen = Double.NaN;
			double prev = Double.NaN;
			double max = 0;
			for (double w = 0; w < deal + 3000; w += FRAME) {
				if (w < arrival) continue;
				if (Double.isNaN(seen)) seen = w;
				double u = LateFlight.progress(w, deal, seen, DEAL);
				double e = 1 - Math.pow(1 - u, 3);
				if (!Double.isNaN(prev)) max = Math.max(max, Math.abs(e - prev));
				prev = e;
			}
			assertTrue(max <= bound, "run " + run + ": a frame moved " + max + " (bound " + bound + ")");
			assertEquals(1, prev, 1e-9, "run " + run + ": in its slot");
		}
	}
}

package dev.nezo.burmaldaholic.games.uth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.uth.logic.PayHand;
import dev.nezo.burmaldaholic.games.uth.logic.Paytables;
import dev.nezo.burmaldaholic.games.uth.logic.TripsMath;
import dev.nezo.burmaldaholic.games.uth.logic.UthCards;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * GAME_DESIGN §21.3 / §21.8: the Trips edge is exact. The class counts are verified by enumerating all
 * C(52,7) = 133 784 560 seven-card hands with the shared evaluator; the edge is then integer arithmetic.
 */
class UthTripsEdgeTest {
	@Test
	void defaultPaytableEdgeIsExactly2547324Over133784560() {
		assertEquals(BigInteger.valueOf(-2_547_324), TripsMath.totalReturnUnits(Paytables.DEFAULT));
		assertEquals(0.019040, TripsMath.houseEdge(Paytables.DEFAULT), 5e-7);
		assertEquals(UthMath.TRIPS_EDGE, TripsMath.houseEdge(Paytables.DEFAULT), 1e-15);
	}

	@Test
	void variantPaytableEdgeIs0Point90Percent() {
		Paytables variant = new Paytables(Map.of(), Map.of("royal", 50, "straightFlush", 40, "quads", 30, "fullHouse", 9, "flush", 7,
			"straight", 4, "trips", 3));
		assertEquals(BigInteger.valueOf(-1_206_516), TripsMath.totalReturnUnits(variant));
		assertEquals(0.009018, TripsMath.houseEdge(variant), 5e-7);
	}

	@Test
	void hitRateIs15Point27Percent() {
		long hits = 0;
		for (PayHand h : PayHand.values()) {
			if (h != PayHand.NONE) {
				hits += TripsMath.count(h);
			}
		}
		assertEquals(0.1527, hits / (double) TripsMath.TOTAL, 5e-5);
		assertEquals(TripsMath.TOTAL, hits + TripsMath.count(PayHand.NONE));
	}

	/** Full enumeration of all 133 784 560 hands (parallel over the first card; a few seconds). */
	@Test
	void classCountsMatchFullEnumeration() {
		AtomicLongArray counts = new AtomicLongArray(PayHand.values().length);
		IntStream.range(0, 46).parallel().forEach(a -> {
			long[] local = new long[PayHand.values().length];
			int[] h = new int[7];
			h[0] = a;
			for (int b = a + 1; b < 47; b++) {
				h[1] = b;
				for (int c = b + 1; c < 48; c++) {
					h[2] = c;
					for (int d = c + 1; d < 49; d++) {
						h[3] = d;
						for (int e = d + 1; e < 50; e++) {
							h[4] = e;
							for (int f = e + 1; f < 51; f++) {
								h[5] = f;
								for (int g = f + 1; g < 52; g++) {
									h[6] = g;
									local[PayHand.of(UthCards.evaluate(h, 7)).ordinal()]++;
								}
							}
						}
					}
				}
			}
			for (int i = 0; i < local.length; i++) {
				counts.addAndGet(i, local[i]);
			}
		});
		long total = 0;
		for (PayHand h : PayHand.values()) {
			assertEquals(TripsMath.count(h), counts.get(h.ordinal()), h.name());
			total += counts.get(h.ordinal());
		}
		assertEquals(TripsMath.TOTAL, total);
		assertTrue(TripsMath.houseEdge(Paytables.DEFAULT) > 0.019);
	}
}

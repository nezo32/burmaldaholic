package dev.nezo.burmaldaholic.games.extras.logic.anim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.extras.logic.Wheel;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The in-world spectator animations (lane J-L7 finish): the Wheel of Fortune and Plinko machine syncs (the SAME curves as
 * the screens, round-trips of the wire form, nothing shown ahead of the landing in the far LOD) and the Lucky Coin toss
 * keyframes (the face only from the landing tick, slerp-safe steps, ends on time).
 */
class InWorldSyncTest {
	private static final String B = String.join("", Wheel.DEFAULT_SEGMENTS);

	// ---- wheel ---------------------------------------------------------------------------------------------------------

	@Test
	void wheelSyncRoundTrips() {
		WheelSync s = new WheelSync(17, 123_456_789_012L, 26, 3, 16, 80, B);
		WheelSync back = WheelSync.decode(s.encode());
		assertEquals(s, back);
		WheelSync custom = new WheelSync(1, 5, 2, -1, -1, 80, "BCHMDTEX");
		assertEquals(custom, WheelSync.decode(custom.encode()));
		assertThrows(IllegalArgumentException.class, () -> WheelSync.decode(new int[] {99, 1, 0, 0}));
		assertThrows(IllegalArgumentException.class, () -> new WheelSync(1, 0, 54, -1, -1, 80, B));
	}

	@Test
	void wheelLandsOnTheDrawnSegmentLikeTheScreen() {
		Random r = new Random(7);
		int n = B.length();
		for (int index = 0; index < n; index++) {
			int prev = r.nextInt(n);
			WheelSync s = new WheelSync(1000 + index, 0, index, prev, 999 + index, 80, B);
			double end = s.angle(s.totalMs(), false);
			assertEquals(index, WheelAnim.segmentAt(end, n), "segment " + index);
			// the same rest angle as the screen: restAngle(index, n, seed(seq))
			assertEquals(WheelAnim.restAngle(index, n, WheelSync.seed(s.seq())), end - 360 * Math.floor((end - s.rest()) / 360 + 0.5), 1e-6);
			assertEquals(index, WheelAnim.segmentAt(s.angle(1e9, true), n));
			// the start is the previous spin's rest
			assertEquals(prev, WheelAnim.segmentAt(s.angle(0, false), n));
		}
	}

	@Test
	void farWheelNeverShowsTheOutcomeBeforeTheStop() {
		int n = B.length();
		WheelSync s = new WheelSync(5, 0, 26, 0, 4, 80, B);
		for (double ms = -500; ms < s.totalMs(); ms += 50) {
			assertEquals(0, WheelAnim.segmentAt(s.shownAngle(ms, false, false), n), "at " + ms);
		}
		assertEquals(26, WheelAnim.segmentAt(s.shownAngle(s.totalMs(), false, false), n));
		// reduce motion stops early on the screen, but the far view still waits for the full spin
		assertEquals(0, WheelAnim.segmentAt(s.shownAngle(WheelAnim.REDUCED_MS + 1, false, true), n));
	}

	// ---- plinko --------------------------------------------------------------------------------------------------------

	@Test
	void plinkoSyncRoundTripsAndLands() {
		for (int path = 0; path < 1 << PlinkoAnim.ROWS; path += 37) {
			PlinkoSync s = new PlinkoSync(path, 1L << 40, path, 4, path % 5, path % 2 == 0);
			assertEquals(s, PlinkoSync.decode(s.encode()));
			PlinkoAnim.Ball end = s.ball(s.landMs() + PlinkoAnim.LAND_MS, false);
			assertEquals(PlinkoAnim.binX(Integer.bitCount(path)), end.x(), 1e-9);
			assertEquals(Integer.bitCount(path), s.bin());
			assertNotNull(s.ball(PlinkoAnim.reducedMs(), true));
			assertNull(s.ball(PlinkoAnim.reducedMs() - 1, true));
		}
		assertThrows(IllegalArgumentException.class, () -> PlinkoSync.decode(new int[] {1, 2, 3}));
	}

	@Test
	void plinkoLampOnlyAfterTheLanding() {
		PlinkoSync s = new PlinkoSync(1, 0, 0b101010101010, 4, 2, false);
		for (double ms = -200; ms < s.landMs(); ms += 10) assertEquals(0, s.lampLit(ms, true), "at " + ms);
		assertEquals(1, s.lampLit(s.landMs(), true));
		assertEquals(2, s.lampLit(s.landMs() + 300, true)); // the blink (2 Hz)
		assertEquals(1, s.lampLit(s.landMs() + 300, false)); // flashes off: steady
		assertEquals(1, s.lampLit(s.landMs() + 1500, true));
		assertEquals(0, s.lampLit(s.landMs() + PlinkoSync.LAMP_MS, true));
	}

	@Test
	void plinkoFaceMapping() {
		// the 13 lamps are one texel apart, centred inside the face (texels 2 … 14)
		for (int bin = 0; bin <= 12; bin++) assertEquals(2 + bin, PlinkoSync.lampU(bin), 1e-9);
		for (int path = 0; path < 1 << PlinkoAnim.ROWS; path += 101) {
			for (double ms = 0; ms < PlinkoAnim.totalMs(200); ms += 25) {
				PlinkoAnim.Ball b = PlinkoAnim.sample(path, 200, ms);
				double u = PlinkoSync.faceU(b.x());
				double v = PlinkoSync.faceV(b.y());
				assertTrue(u >= 1 && u <= 15, "u " + u);
				assertTrue(v >= 0 && v <= 15, "v " + v);
			}
		}
	}

	// ---- coin toss -----------------------------------------------------------------------------------------------------

	@Test
	void coinTossHidesTheFaceUntilTheLanding() {
		int keys = 0;
		float lastRoll = 0;
		for (int t = 0; t <= CoinTossKeys.REMOVE_TICK; t++) {
			CoinTossKeys.Key k = CoinTossKeys.at(t);
			if (t < CoinTossKeys.LAND_TICK) {
				assertTrue(CoinTossKeys.faceHidden(t));
				if (k != null) {
					assertFalse(k.face(), "face at " + t);
					assertTrue(k.rollDeg() - lastRoll <= 120.0001f, "slerp step at " + t);
					lastRoll = k.rollDeg();
					keys++;
				}
			} else {
				assertFalse(CoinTossKeys.faceHidden(t));
			}
		}
		assertEquals(CoinTossKeys.FLIGHT_KEYS, keys);
		assertEquals(720f, CoinTossKeys.flight(CoinTossKeys.FLIGHT_KEYS).rollDeg(), 1e-6);
		assertEquals(0f, CoinTossKeys.flight(CoinTossKeys.FLIGHT_KEYS).y(), 1e-6); // caught at hand height
		assertEquals(CoinTossKeys.ARC, CoinTossKeys.flight(3).y(), 1e-6); // the apex
		CoinTossKeys.Key land = CoinTossKeys.at(CoinTossKeys.LAND_TICK);
		assertNotNull(land);
		assertTrue(land.face());
		assertEquals(0f, CoinTossKeys.at(CoinTossKeys.SHRINK_TICK).scale());
		assertTrue(CoinTossKeys.SHRINK_TICK + CoinTossKeys.SHRINK_TICKS < CoinTossKeys.REMOVE_TICK);
		assertArrayEquals(new float[] {0.4f, 0.5f}, new float[] {CoinTossKeys.SCALE, CoinTossKeys.POP_SCALE});
	}
}

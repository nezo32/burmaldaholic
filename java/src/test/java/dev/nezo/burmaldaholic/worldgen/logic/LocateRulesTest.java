package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LocateRulesTest {
	@Test
	void eightPointDirectionsUseMinecraftAxes() {
		assertEquals("n", LocateRules.direction(0, -100));
		assertEquals("ne", LocateRules.direction(70, -70));
		assertEquals("e", LocateRules.direction(100, 0));
		assertEquals("se", LocateRules.direction(70, 70));
		assertEquals("s", LocateRules.direction(0, 100));
		assertEquals("sw", LocateRules.direction(-70, 70));
		assertEquals("w", LocateRules.direction(-100, 0));
		assertEquals("nw", LocateRules.direction(-70, -70));
		assertEquals("n", LocateRules.direction(0, 0), "standing on it");
		// Sector borders at 22.5°: just inside north / just inside north-east.
		assertEquals("n", LocateRules.direction(Math.tan(Math.toRadians(22)) * 100, -100));
		assertEquals("ne", LocateRules.direction(Math.tan(Math.toRadians(23)) * 100, -100));
		assertEquals("n", LocateRules.direction(-Math.tan(Math.toRadians(22)) * 100, -100), "north-north-west is north");
	}

	@Test
	void cooldownCountsDownInWholeSecondsOfMonotonicTime() {
		long s = 1_000_000_000L;
		assertEquals(0, LocateRules.cooldownLeftSeconds(null, 5 * s, 30), "never searched");
		assertEquals(30, LocateRules.cooldownLeftSeconds(5 * s, 5 * s, 30));
		assertEquals(1, LocateRules.cooldownLeftSeconds(5 * s, 35 * s - 1, 30), "part of a second rounds up");
		assertEquals(0, LocateRules.cooldownLeftSeconds(5 * s, 35 * s, 30));
		assertEquals(0, LocateRules.cooldownLeftSeconds(5 * s, 5 * s, 0), "cooldown disabled");
		// nanoTime may be negative and may wrap: only differences count.
		assertEquals(11, LocateRules.cooldownLeftSeconds(Long.MAX_VALUE - 5 * s, Long.MIN_VALUE + 14 * s, 30), "19 s + 1 ns elapsed");
		assertEquals(30, LocateRules.cooldownLeftSeconds(-7 * s, -7 * s, 30));
	}

	@Test
	void gridSearchesAreSpacedServerWide() {
		long s = 1_000_000_000L;
		assertTrue(LocateRules.gridSearchAllowed(null, 0), "first search");
		assertFalse(LocateRules.gridSearchAllowed(10 * s, 10 * s + s - 1));
		assertTrue(LocateRules.gridSearchAllowed(10 * s, 11 * s));
		assertTrue(LocateRules.gridSearchAllowed(Long.MAX_VALUE, Long.MIN_VALUE + s), "across a nanoTime wrap");
	}

	@Test
	void nearestPicksTheClosestHorizontally() {
		var a = new LocateRules.Candidate(CasinoKind.VILLAGE_CASINO, 100, 70, 0, false);
		var b = new LocateRules.Candidate(CasinoKind.VILLAGE_CASINO, -40, 200, 30, true); // high, but closer horizontally
		var c = new LocateRules.Candidate(CasinoKind.PIGLIN_PARLOR, 0, 40, -90, false);
		assertEquals(b, LocateRules.nearest(List.of(a, b, c), 0, 0).orElseThrow());
		assertEquals(a, LocateRules.nearest(List.of(a, c), 60, 0).orElseThrow());
		assertTrue(LocateRules.nearest(List.of(), 0, 0).isEmpty());
	}

	@Test
	void ringsCoverEverySquareCellOnce() {
		Set<String> seen = new HashSet<>();
		for (int r = 0; r <= 4; r++) {
			List<int[]> cells = LocateRules.ringCells(r);
			assertEquals(r == 0 ? 1 : 8 * r, cells.size(), "ring " + r);
			for (int[] c : cells) {
				assertEquals(r, Math.max(Math.abs(c[0]), Math.abs(c[1])), "cell on ring " + r);
				assertTrue(seen.add(c[0] + "," + c[1]), "duplicate cell " + c[0] + "," + c[1]);
			}
		}
		assertEquals(81, seen.size());
	}

	@Test
	void radiusAndRingBoundsFollowTheCaps() {
		assertEquals(64, LocateRules.capChunks(CasinoKind.VILLAGE_CASINO));
		assertEquals(48, LocateRules.capChunks(CasinoKind.PIGLIN_PARLOR));
		assertEquals(96, LocateRules.capChunks(CasinoKind.HIGH_ROLLER));
		assertEquals(3, LocateRules.maxRing(64, 34), "villages: 2 cells + the offset ring");
		assertEquals(0, LocateRules.maxRing(0, 34));
		assertTrue(LocateRules.ringMayBeat(0, 34, 0));
		assertTrue(LocateRules.ringMayBeat(1, 34, 0), "neighbour cells may hold a start right next to the player");
		assertTrue(LocateRules.ringMayBeat(2, 34, 40));
		assertFalse(LocateRules.ringMayBeat(3, 34, 50), "ring 3 starts at least 68 chunks out (60 with reach)");
		assertTrue(LocateRules.withinRadius(70, 64));
		assertFalse(LocateRules.withinRadius(73, 64));
	}

	/**
	 * Parity with generation: the locator's gate must be the exact call VillageCasinos / CasinoStructures make.
	 * Golden values computed independently (SplitMix64 in Python) pin {@link Gates#unit} itself.
	 */
	@Test
	void gateMatchesGenerationOnFixedVectors() {
		assertEquals(0.8497044023506652, Gates.unit(0, 0, 0, Gates.SALT_VILLAGE));
		assertEquals(0.916436151703411, Gates.unit(0, -160, 320, Gates.SALT_VILLAGE));
		assertEquals(0.9862743702450337, Gates.unit(0, 1600, -48, Gates.SALT_VILLAGE));
		assertEquals(0.7040485854820763, Gates.unit(123456789, 12, -7, Gates.SALT_PARLOR));
		assertEquals(0.30615310876469304, Gates.unit(123456789, -30, 41, Gates.SALT_PARLOR));
		assertEquals(0.7284613210502584, Gates.unit(-4242, 5, 5, Gates.SALT_LOUNGE));
		assertEquals(0.8433487195647729, Gates.unit(-4242, -100, 77, Gates.SALT_LOUNGE));

		// Village: seed 0, the start piece at the min corner of the start chunk (chunk -10, 20 -> block -160, 320).
		assertTrue(LocateRules.gate(CasinoKind.VILLAGE_CASINO, 0.92, 999L, -10, 20), "0.9164 < 0.92, world seed ignored");
		assertFalse(LocateRules.gate(CasinoKind.VILLAGE_CASINO, 0.91, 999L, -10, 20));
		// Parlor / lounge: level seed + start chunk.
		assertTrue(LocateRules.gate(CasinoKind.PIGLIN_PARLOR, 0.31, 123456789, -30, 41));
		assertFalse(LocateRules.gate(CasinoKind.PIGLIN_PARLOR, 0.30, 123456789, -30, 41));
		assertTrue(LocateRules.gate(CasinoKind.HIGH_ROLLER, 0.73, -4242, 5, 5));
		assertFalse(LocateRules.gate(CasinoKind.HIGH_ROLLER, 0.72, -4242, 5, 5));
		// Bounds: chance 0 never, 1 always.
		assertFalse(LocateRules.gate(CasinoKind.HIGH_ROLLER, 0, -4242, 5, 5));
		assertTrue(LocateRules.gate(CasinoKind.VILLAGE_CASINO, 1, 0, 1, 1));
		for (int i = -50; i < 50; i++) {
			int cx = i * 7 - 3;
			int cz = i * -13 + 1;
			assertEquals(Gates.pass(0.5, 0L, cx * 16, cz * 16, Gates.SALT_VILLAGE), LocateRules.gate(CasinoKind.VILLAGE_CASINO, 0.5, 77L, cx, cz));
			assertEquals(Gates.pass(0.6, 77L, cx, cz, Gates.SALT_PARLOR), LocateRules.gate(CasinoKind.PIGLIN_PARLOR, 0.6, 77L, cx, cz));
			assertEquals(Gates.pass(0.5, 77L, cx, cz, Gates.SALT_LOUNGE), LocateRules.gate(CasinoKind.HIGH_ROLLER, 0.5, 77L, cx, cz));
		}
	}
}

package dev.nezo.burmaldaholic.chaos.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Lane J-L3: chaos / Golden Hour presentation curves (global.md §4.5, §4.6, §2.8 flash rules). */
class ChaosFxMathTest {
	@Test
	void toneFollowsTheEventKind() {
		assertEquals(ChaosFxMath.Tone.GOOD, ChaosFxMath.Tone.of(ChaosEvent.DIAMOND_RAIN.kind()));
		assertEquals(ChaosFxMath.Tone.BAD, ChaosFxMath.Tone.of(ChaosEvent.CURSE.kind()));
		assertEquals(ChaosFxMath.Tone.NEUTRAL, ChaosFxMath.Tone.of(ChaosEvent.RANDOM_TELEPORT.kind()));
		assertEquals("chaos_good", ChaosFxMath.Tone.GOOD.sound);
	}

	@Test
	void cardFlipsInHoldsAndFlipsOut() {
		assertEquals(0, ChaosFxMath.cardScaleX(0, false), 1e-9);
		assertEquals(1, ChaosFxMath.cardScaleX(ChaosFxMath.CARD_IN_MS, false), 1e-9);
		assertEquals(1, ChaosFxMath.cardScaleX(1500, false), 1e-9);
		assertEquals(0, ChaosFxMath.cardScaleX(ChaosFxMath.CARD_TOTAL_MS, false), 1e-9);
		assertEquals(1, ChaosFxMath.cardScaleX(10, true), 1e-9, "reduced motion: no flip");
		for (int ms = 0; ms < ChaosFxMath.CARD_TOTAL_MS; ms += 10) {
			double a = ChaosFxMath.cardAlpha(ms);
			assertTrue(a >= 0 && a <= 1);
		}
	}

	@Test
	void shakeOnlyWithMotionAndEndsAtRest() {
		double max = 0;
		for (int ms = 0; ms < ChaosFxMath.SHAKE_MS; ms++) max = Math.max(max, Math.abs(ChaosFxMath.shakePx(ms, false)));
		assertTrue(max <= ChaosFxMath.SHAKE_PX && max > 1);
		assertEquals(0, ChaosFxMath.shakePx(100, true), 1e-9);
		assertEquals(0, ChaosFxMath.shakePx(ChaosFxMath.SHAKE_MS, false), 1e-9);
	}

	@Test
	void curseVignetteRespectsTheFlashRule() {
		for (int ms = 0; ms < 600; ms++) {
			double a = ChaosFxMath.curseVignette(ms, true);
			assertTrue(a >= 0 && a <= 0.30 + 1e-9, "≤ 30 %");
		}
		assertEquals(0.10, ChaosFxMath.curseVignette(200, false), 1e-9, "flashes off: static 10 %");
		assertEquals(0, ChaosFxMath.curseVignette(ChaosFxMath.CURSE_VIGNETTE_MS, false), 1e-9);
	}

	@Test
	void teleportVeilWrapsTheTeleportTickAndHonoursFlashes() {
		int at = ChaosFxMath.TELEPORT_LEAD_TICKS * 50;
		assertEquals(0.85, ChaosFxMath.teleportVeil(at, true), 1e-9, "fully veiled when the server teleports");
		assertEquals(0, ChaosFxMath.teleportVeil(0, true), 1e-9);
		assertEquals(0, ChaosFxMath.teleportVeil(at + ChaosFxMath.TELEPORT_FADE_MS, true), 1e-9);
		for (int ms = 0; ms < 800; ms += 5) assertEquals(0, ChaosFxMath.teleportVeil(ms, false), 1e-9);
		// no change faster than 250 ms: ramps last ≥ 250 ms
		assertTrue(at >= 250 && ChaosFxMath.TELEPORT_FADE_MS >= 250);
	}

	@Test
	void spawnPatterns() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 16; i++) {
			double[] p = ChaosFxMath.ring(i, 16, 2, 0);
			assertEquals(2, Math.hypot(p[0], p[1]), 1e-9);
			seen.add(Math.round(p[0] * 100) + "," + Math.round(p[1] * 100));
		}
		assertEquals(16, seen.size());
		double prev = Double.MAX_VALUE;
		for (int ms = 0; ms <= ChaosFxMath.COLUMN_MS; ms += 10) {
			double h = ChaosFxMath.columnHeight(ms);
			assertTrue(h <= prev + 1e-12, "the column only falls");
			prev = h;
		}
		assertEquals(ChaosFxMath.COLUMN_HEIGHT, ChaosFxMath.columnHeight(0), 1e-9);
		assertEquals(0, ChaosFxMath.columnHeight(ChaosFxMath.COLUMN_MS), 1e-9, "lands on the real drop point");
		double[] top = ChaosFxMath.spiral(0, 6, 0);
		double[] end = ChaosFxMath.spiral(0, 6, 1);
		assertTrue(top[1] > end[1] && Math.hypot(end[0], end[2]) < Math.hypot(top[0], top[2]), "the curse spiral descends and closes in");
		assertEquals(0.5, ChaosFxMath.teleportRingRadius(0), 1e-9);
		assertEquals(2, ChaosFxMath.teleportRingRadius(300), 1e-9);
		for (int i = 0; i < ChaosFxMath.GH_MOTES; i++) {
			int t = ChaosFxMath.moteSpawnMs(i);
			assertTrue(t >= ChaosFxMath.GH_MOTES_FROM_MS && t < ChaosFxMath.GH_MOTES_TO_MS);
		}
		assertEquals(18, ChaosFxMath.RUNE_LEAD_TICKS, "runes 900 ms before the mob");
	}

	@Test
	void goldenHourVignetteEnvelope() {
		assertEquals(0, ChaosFxMath.goldenVignette(0, true), 1e-9);
		assertEquals(0.30, ChaosFxMath.goldenVignette(ChaosFxMath.GH_RISE_MS, true), 1e-6);
		assertEquals(0.12, ChaosFxMath.goldenVignette(ChaosFxMath.GH_SETTLE_MS, true), 1e-6);
		assertEquals(0.12, ChaosFxMath.goldenVignette(3_600_000, true), 1e-6, "steady for the whole hour");
		assertEquals(0, ChaosFxMath.goldenVignette(1000, false), 1e-9, "flashes off: none");
		for (int ms = 0; ms < 4000; ms += 5) assertTrue(ChaosFxMath.goldenVignette(ms, true) <= 0.30 + 1e-9);
		assertEquals(0.12, ChaosFxMath.goldenVignetteEnd(0, 0.12), 1e-9);
		assertEquals(0, ChaosFxMath.goldenVignetteEnd(ChaosFxMath.GH_END_MS, 0.12), 1e-9);
	}

	@Test
	void countdownNeverEndsEarly() {
		assertEquals(0, ChaosFxMath.remainingTicks(0, 5));
		assertEquals(95, ChaosFxMath.remainingTicks(100, 5));
		assertEquals(1, ChaosFxMath.remainingTicks(100, 500), "held at the last tick until the server ends it");
		assertEquals("1:00", ChaosFxMath.clock(1200));
		assertEquals("0:01", ChaosFxMath.clock(1));
		assertEquals("12:05", ChaosFxMath.clock(14500));
		assertEquals(1.25, ChaosFxMath.countdownScale(200, 0, false), 1e-9);
		assertEquals(1, ChaosFxMath.countdownScale(200, 300, false), 1e-9);
		assertEquals(1, ChaosFxMath.countdownScale(400, 0, false), 1e-9, "only the last ten seconds punch");
		assertEquals(1, ChaosFxMath.countdownScale(100, 0, true), 1e-9);
	}

	@Test
	void goldenHourScoreIsADeterministicLoop() {
		assertEquals(0.5f, GoldenHourScore.pitch(0), 1e-6);
		assertEquals(1f, GoldenHourScore.pitch(12), 1e-6);
		assertEquals(2f, GoldenHourScore.pitch(24), 1e-6);
		int bass = 0;
		for (int s = 0; s < GoldenHourScore.LOOP_STEPS; s++) {
			List<GoldenHourScore.Note> a = GoldenHourScore.notes(s);
			assertEquals(a, GoldenHourScore.notes(s + GoldenHourScore.LOOP_STEPS), "loops");
			assertTrue(a.size() <= 3, "≤ 3 voices per step (sound budget)");
			for (GoldenHourScore.Note n : a) {
				assertTrue(n.semitone() >= 0 && n.semitone() <= 24);
				assertTrue(n.volume() > 0 && n.volume() <= 0.5f);
				if (n.voice().equals("bass")) bass++;
			}
		}
		assertEquals(GoldenHourScore.BARS * 2, bass, "bass on beats 1 and 3 of every bar");
		assertTrue(GoldenHourScore.onStep(0) && !GoldenHourScore.onStep(1) && GoldenHourScore.onStep(GoldenHourScore.STEP_TICKS));
	}
}

package dev.nezo.burmaldaholic.core.anim.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Ballistic, HeightShadow, Streak, StageMap, Oscillation and the FxGate (reduced motion / flashes). */
class MotionKitMiscTest {
	@Test
	void ballisticClosedForms() {
		// PL §2.2: Δt = (−vy0 + √(vy0² + 2gΔy))/g, then the fall lands exactly
		double t = Ballistic.fallTime(-60, 12, 900);
		assertEquals(12, Ballistic.pos(0, -60, 900, t), 1e-12);
		assertTrue(Double.isNaN(Ballistic.fallTime(-10, -50, 900)), "cannot climb 50 px at 10 px/s");
		double v0 = Ballistic.v0For(20, 0.2, -900);
		assertEquals(20, Ballistic.pos(0, v0, -900, 0.2), 1e-12);
		assertEquals(4, Ballistic.apexOfHop(Ballistic.hopTime(4, 1300), 1300), 1e-12);
		double vz = Ballistic.vzForApex(1.5, 70);
		assertEquals(1.5, Ballistic.pos(0, vz, -70, Ballistic.apexTime(vz, 70)), 1e-12);
		assertEquals(Math.sqrt(2 * 0.9 / 70) + Math.sqrt(3 / 70.0), Ballistic.flightTime(0.6, 1.5, 70), 1e-12);
		assertEquals(5, Ballistic.vxFor(10, 2), 0);
	}

	@Test
	void ballisticValidatesGravity() {
		for (double g : new double[] {0, -9, Double.NaN, Double.POSITIVE_INFINITY}) {
			assertThrows(IllegalArgumentException.class, () -> Ballistic.fallTime(0, 1, g));
			assertThrows(IllegalArgumentException.class, () -> Ballistic.hopTime(1, g));
			assertThrows(IllegalArgumentException.class, () -> Ballistic.apexOfHop(1, g));
			assertThrows(IllegalArgumentException.class, () -> Ballistic.apexTime(1, g));
			assertThrows(IllegalArgumentException.class, () -> Ballistic.vzForApex(1, g));
			assertThrows(IllegalArgumentException.class, () -> Ballistic.flightTime(0, 1, g));
		}
		// later root: thrown up at 60 px/s (y down), crossing 1 px ABOVE the start is the descending crossing
		double t = Ballistic.fallTime(-60, -1, 900);
		assertTrue(t > 60 / 900.0, "after the apex");
		assertEquals(-1, Ballistic.pos(0, -60, 900, t), 1e-12);
	}

	@Test
	void heightShadowLooks() {
		HeightShadow.Out o = new HeightShadow.Out();
		HeightShadow.apply(HeightShadow.ROULETTE, 0, 1, o);
		assertEquals(0, o.dy, 0);
		assertEquals(0.45, o.shadowAlpha, 1e-12);
		HeightShadow.apply(HeightShadow.ROULETTE, 4, 0.5, o); // RO §3.5: (−0.3z, −0.7z)·k, α 0.45·(1 − z/8)
		assertEquals(-0.6, o.dx, 1e-12);
		assertEquals(-1.4, o.dy, 1e-12);
		assertEquals(0.225, o.shadowAlpha, 1e-12);
		assertEquals(0.5, o.shadowDx, 1e-12);
		HeightShadow.apply(HeightShadow.CRAPS, 10, 1, o);
		assertEquals(0.2, o.shadowAlpha, 1e-12, "craps alpha floor 0.2");
		assertEquals(2, o.shadowSize, "smallest of 3 shadow sizes");
		double prevA = 1;
		for (double z = 0; z < 12; z += 0.25) {
			HeightShadow.apply(HeightShadow.DICE, z, 1, o);
			assertTrue(o.shadowAlpha <= prevA && o.shadowAlpha >= 0);
			prevA = o.shadowAlpha;
		}
	}

	@Test
	void streakSpacingAndCutoff() {
		Streak st = new Streak();
		double[] out = new double[16];
		// 756°/s on a 52 px radius ≈ 686 px/s → a 16.7 ms frame covers ≈ 11 px → 8 samples at ≤ 1.5 px … capped at 8
		Streak.Sampler fast = (ms, xy) -> {
			double a = Math.toRadians(756 * ms / 1000);
			xy[0] = 52 * Math.cos(a);
			xy[1] = 52 * Math.sin(a);
		};
		int n = st.samples(fast, 1000, 16.7, 1.5, out);
		assertEquals(8, n);
		double[] head = new double[2];
		fast.at(1000, head);
		double prevX = head[0];
		double prevY = head[1];
		for (int i = 0; i < n; i++) {
			double d = Math.hypot(out[2 * i] - prevX, out[2 * i + 1] - prevY);
			assertTrue(d <= 1.6, "gap " + d);
			prevX = out[2 * i];
			prevY = out[2 * i + 1];
		}
		Streak.Sampler slow = (ms, xy) -> {
			xy[0] = ms * 0.05; // 50 px/s
			xy[1] = 0;
		};
		assertEquals(0, st.samples(slow, 1000, 16.7, 1.5, out), "slow: no streak");
		assertEquals(0.45, Streak.alpha(0, 8, 0.45, 0.08), 1e-12);
		assertEquals(0.08, Streak.alpha(7, 8, 0.45, 0.08), 1e-12);
	}

	@Test
	void stageMapMirrorsLanes() {
		StageMap left = StageMap.of(10, 20, 0.5, 0.5, 0.9);
		StageMap right = left.mirrored(360);
		assertEquals(10 + 0.5 * 40, left.x(40), 1e-12);
		assertEquals(10 + 0.5 * 320, right.x(40), 1e-12, "x' = 360 − x");
		assertEquals(40, right.u(right.x(40)), 1e-12);
		assertEquals(20 + 0.5 * 8 - 0.9 * 2, left.yLifted(8, 2), 1e-12);
		assertEquals(8, left.v(left.y(8)), 1e-12);
		assertFalse(left.isMirrored());
	}

	@Test
	void oscillationsDecayAndStop() {
		assertEquals(0, Oscillation.dampedSin(2, 450, 22, -1), 0);
		assertEquals(1, Oscillation.dampedCos(1, 40, 90, 0), 1e-12);
		double p22 = Oscillation.periodOfHz(22);
		assertEquals(1000 / 22.0, p22, 1e-12);
		assertEquals(0, Oscillation.shake(2, 60, p22, 60 * 4), 0, "finished shake is exactly 0");
		assertTrue(Math.abs(Oscillation.shake(2, 450, p22, 5)) > 0);
		assertEquals(2 * Math.exp(-5 / 450.0) * Math.sin(2 * Math.PI * 22 * 5 / 1000.0), Oscillation.shake(2, 450, p22, 5), 1e-12,
			"period ms ≡ 22 Hz");
		assertEquals(0, Oscillation.shake(2, 0, p22, 5), 0, "a shake without decay never runs (would never end)");
		assertEquals(0, Oscillation.shake(2, -10, p22, 5), 0);
		assertTrue(Oscillation.envelope(-100, 100) > 2.7, "negative tau grows");
	}

	@Test
	void fxGateHonoursReducedMotionAndFlashes() {
		FxGate.Counter rec = new FxGate.Counter();
		for (FxGate.Kind k : FxGate.Kind.values()) {
			assertFalse(FxGate.pass(k, "x", true, true, rec), "reduced motion blocks " + k);
		}
		assertEquals(0, rec.total(), "recorder sees no effect under reduced motion");
		assertTrue(FxGate.pass(FxGate.Kind.SHAKE, "plinko_bin_top", false, false, rec));
		assertFalse(FxGate.pass(FxGate.Kind.FLASH, "board_flash", false, false, rec), "flashes off");
		assertTrue(FxGate.pass(FxGate.Kind.FLASH, "board_flash", false, true, rec));
		assertEquals(1, rec.count(FxGate.Kind.SHAKE));
		assertEquals(1, rec.count("board_flash"));
		rec.clear();
		assertEquals(0, rec.total());
	}
}

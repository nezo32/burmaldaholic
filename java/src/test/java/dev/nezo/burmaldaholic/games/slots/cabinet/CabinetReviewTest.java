package dev.nezo.burmaldaholic.games.slots.cabinet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetFrames.Motion;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync.Spin;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync.Wheel;
import org.junit.jupiter.api.Test;

/**
 * Adversarial review tests of lane J-L10 (in-world cabinet) for the bugs found in the Bedrock twin: no free spin's
 * stops before it lands (early-spoiler planner), no landing read beyond the strip, late viewers of the Dragon Wheel
 * see the landed segments (not segment 0).
 */
class CabinetReviewTest {
	private static final Motion[] MOTIONS = Motion.values();

	@Test
	void noSpinShowsItsStopsBeforeItsReelsLand() {
		for (CabinetSync s : CabinetFramesTest.samples()) {
			CabinetFrame f = new CabinetFrame();
			for (int i = 0; i < s.spins().size(); i++) {
				Spin sp = s.spins().get(i);
				int[] prev = i == 0 ? s.prevStops() : s.spins().get(i - 1).stops();
				// before the spin starts, the frame still belongs to an earlier spin (or the rest window)
				for (Motion m : MOTIONS) {
					CabinetFrames.sample(s, sp.startMs() - 1, m, f);
					assertTrue(f.spin < i, "spin " + i + " sampled before its start in " + m);
				}
				for (int r = 0; r < CabinetSync.REELS; r++) {
					if ((sp.heldMask() & 1 << r) != 0) continue;
					double stop = sp.stopMs()[r];
					double k = 100.0 / s.speedPct() * (i > 0 ? 0.8 : 1.0);
					// SETTLED (late joiner, far LOD) and REDUCED (before its cross-fade): the previous rest window, blurred
					for (Motion m : new Motion[] {Motion.SETTLED, Motion.REDUCED}) {
						double until = m == Motion.REDUCED ? stop - CabinetFrames.CROSSFADE_MS * k : stop;
						for (double t = 0; t < until; t += 17) {
							CabinetFrames.sample(s, sp.startMs() + t, m, f);
							for (int c = 0; c < f.count[r]; c++) {
								float y = f.y[r][c];
								if (y < 0 || y >= 3) continue;
								assertEquals(s.symbolAt(r, prev[r] + Math.round(y)), f.sym[r][c],
									"spin " + i + " reel " + r + " at " + t + " in " + m + " shows a non-rest cell");
							}
						}
					}
					// FULL: until the reference switch, the reel scrolls from the previous stop (never towards the result)
					double sw = ReelMotion.switchAt(stop, s.speedPct(), (sp.anticipationMask() & 1 << r) != 0);
					assertTrue(sw < stop, "switch while blurred, before the landing ends");
				}
			}
		}
	}

	@Test
	void landingNeverDrawsCellsOutsideTheWindowOrUnknownSymbols() {
		for (CabinetSync s : CabinetFramesTest.samples()) {
			CabinetFrame f = new CabinetFrame();
			for (Motion m : MOTIONS) {
				for (double t = -100; t <= s.endMs() + 100 && t < 200_000; t += 3) {
					CabinetFrames.sample(s, t, m, f);
					for (int r = 0; r < CabinetSync.REELS; r++) {
						assertTrue(f.count[r] <= CabinetFrame.SLOTS);
						for (int c = 0; c < f.count[r]; c++) {
							float y = f.y[r][c];
							assertFalse(Float.isNaN(y) || Float.isNaN(f.alpha[r][c]) || Float.isNaN(f.scale[r][c]));
							assertTrue(y > -3.5f && y < 4.25f, "cell row " + y + " far outside the window (tumble refills start at most 3 rows above)");
							assertTrue(f.sym[r][c] >= 0 && f.sym[r][c] < 11, "symbol index " + f.sym[r][c]);
						}
					}
				}
			}
		}
	}

	@Test
	void lateViewersOfTheDragonWheelSeeTheLandedSegments() {
		for (int seed = 0; seed < 60; seed++) {
			CabinetSync s = CabinetFramesTest.end(seed, seed % 2 == 0 ? 100 : 200);
			Wheel w = s.wheel();
			CabinetFrame f = new CabinetFrame();
			for (Motion m : MOTIONS) {
				for (int i = 0; i < w.segments().length; i++) {
					double landed = w.spinMs()[i] + w.spinDurMs()[i];
					for (double t = landed; t < w.endMs(); t += 41) {
						CabinetFrames.sample(s, t, m, f);
						assertTrue(f.wheelRise > 0, "wheel visible");
						assertTrue(f.ringCount > i, "ring " + i + " visible after it landed");
						assertEquals(w.segments()[i], CabinetFrames.wedgeAt(f.ringAngle[i], w.ringSizes()[i]),
							"ring " + i + " at " + t + " in " + m);
					}
				}
			}
			// a viewer arriving between the spins (SETTLED catch-up) never sees a ring parked on the landed wedge early
			for (int i = 0; i < w.segments().length; i++) {
				CabinetFrames.sample(s, w.spinMs()[i] - 1, Motion.SETTLED, f);
				double start = CabinetFrames.ringFinalAngle(s, i) - (CabinetFrames.WHEEL_TURNS * 360 + CabinetFrames.ringExtra(s, i));
				assertEquals((float) start, f.ringAngle[i], 1e-3, "idle before the spin");
			}
		}
	}
}

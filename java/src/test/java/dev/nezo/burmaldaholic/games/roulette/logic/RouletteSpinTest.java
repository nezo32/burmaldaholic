package dev.nezo.burmaldaholic.games.roulette.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.nezo.burmaldaholic.core.anim.motion.BounceChain;
import dev.nezo.burmaldaholic.core.anim.motion.EventCursor;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ROULETTE-ANIM-SPEC §9.1 1–8: the deterministic spin ends in the drawn pocket for every result × seed × spin length,
 * leaks nothing before the SPIN start, moves physically, rattles like a ball, keeps the angle under decoration, keeps the
 * rotor continuous over a long session and fires its events exactly once at any frame rate. The full sweep of item 1
 * (2048 seeds × 8 rotor states) runs with {@code -Droulette.fullSweep=true}; the default covers every result, spin length
 * and direction with fewer seeds.
 */
class RouletteSpinTest {
	private static final int[] SPIN_TICKS = {40, 60, 100, 140, 200, 300};
	private static final boolean FULL = Boolean.getBoolean("roulette.fullSweep");
	private static final double RW_PX = 92;

	private static double mod360(double a) {
		double x = a % 360;
		return x < 0 ? x + 360 : x;
	}

	private static double angDiff(double a, double b) {
		double d = mod360(a - b);
		return d > 180 ? d - 360 : d;
	}

	/** Rotor states at launch: angle, signed speed (the previous spin's direction or idle). */
	private static RotorModel rotor(int i, int dir) {
		double[] angles = {0, 37.25, 123.5, 359.99, 211, 90.01, 300.5, 17};
		double[] speeds = {36, -36, 150, -150, 120, -60, 0, 80};
		return RotorModel.launch(angles[i % 8], speeds[i % 8], dir);
	}

	private static RouletteSpin spin(int result, int seed, int ticks, RotorModel r) {
		long launch = 1_000_000L + seed % 977;
		return RouletteSpin.build(result, seed, (seed & 3) == 0 ? -1 : Math.floorMod(seed, 37), launch, launch + RouletteSpin.NMB_TICKS, ticks, r);
	}

	// ---- 1. ends in the drawn pocket ------------------------------------------------------------------------------------

	@Test
	void everySpinEndsInTheDrawnPocket() {
		int seeds = FULL ? 2048 : 48;
		int rotors = FULL ? 8 : 2;
		RouletteSpin.Sample s = new RouletteSpin.Sample();
		int built = 0;
		for (int ticks : SPIN_TICKS) {
			for (int dir = -1; dir <= 1; dir += 2) {
				for (int ri = 0; ri < rotors; ri++) {
					RotorModel r = rotor(ri + (dir > 0 ? 3 : 0), dir);
					for (int seed = 0; seed < seeds; seed++) {
						int sd = seed * 7919 - 31 + ticks;
						for (int result = 0; result < Wheel.POCKETS; result++) {
							RouletteSpin sp = spin(result, sd, ticks, r);
							built++;
							String at = "result " + result + " seed " + sd + " ticks " + ticks + " dir " + dir + " rotor " + ri;
							if (sp.usedFallback()) fail("solver fallback: " + at);
							assertTrue(sp.restMs() <= sp.spinEndMs() - 300 + 1e-9, "rest before end − 300: " + at);
							sp.sample(sp.restMs(), s);
							assertEquals(0, angDiff(s.rel, RouletteSpin.pocketAngle(result)), 1e-9, at);
							assertEquals(result, sp.pocketUnder(sp.restMs()), at);
							for (double later : new double[] {1, 1000, 15_000, 60_000}) {
								sp.sample(sp.restMs() + later, s);
								assertEquals(0, angDiff(s.rel, RouletteSpin.pocketAngle(result)), 1e-9, "riding: " + at);
								assertEquals(RouletteSpin.R_POCKET, s.r, 1e-12);
							}
						}
					}
				}
			}
		}
		assertTrue(built > 1000);
	}

	// ---- 2. no result leak before the SPIN start -----------------------------------------------------------------------

	@Test
	void nothingBeforeTheSpinStartDependsOnTheResult() {
		RouletteSpin.Sample a = new RouletteSpin.Sample();
		RouletteSpin.Sample b = new RouletteSpin.Sample();
		for (int ticks : new int[] {40, 140, 300}) {
			for (int seed = 0; seed < 16; seed++) {
				RotorModel r = rotor(seed, seed % 2 == 0 ? 1 : -1);
				RouletteSpin pre = spin(-1, seed * 31 + 5, ticks, r);
				assertFalse(pre.solved());
				for (int result = 0; result < Wheel.POCKETS; result++) {
					RouletteSpin sp = spin(result, seed * 31 + 5, ticks, r);
					assertTrue(sp.huntMs() >= sp.spinStartMs(), "hunting never starts before the draw");
					for (double t = 0; t < sp.spinStartMs(); t += 7.3) {
						pre.sample(t, a);
						sp.sample(t, b);
						String at = "t " + t + " result " + result;
						assertEquals(Double.doubleToLongBits(a.abs), Double.doubleToLongBits(b.abs), at);
						assertEquals(Double.doubleToLongBits(a.r), Double.doubleToLongBits(b.r), at);
						assertEquals(Double.doubleToLongBits(a.z), Double.doubleToLongBits(b.z), at);
						assertEquals(Double.doubleToLongBits(a.rotor), Double.doubleToLongBits(b.rotor), at);
						assertEquals(a.visible, b.visible, at);
						assertEquals(a.frame, b.frame, at);
						assertEquals(a.phase, b.phase, at);
					}
					for (MotionEvent e : sp.events()) {
						if (e.tMs() < sp.spinStartMs()) {
							boolean found = false;
							for (MotionEvent q : pre.events()) found |= q.equals(e);
							assertTrue(found, "event before the draw is result-free: " + e);
						}
					}
				}
			}
		}
	}

	// ---- 3. physical ------------------------------------------------------------------------------------------------------

	@Test
	void theBallMovesLikeABall() {
		RouletteSpin.Sample s = new RouletteSpin.Sample();
		RouletteSpin.Sample q = new RouletteSpin.Sample();
		for (int ticks : SPIN_TICKS) {
			for (int seed = 0; seed < 24; seed++) {
				int dir = seed % 2 == 0 ? 1 : -1;
				RouletteSpin sp = spin(seed % 37, seed * 13 + ticks, ticks, rotor(seed, dir));
				double tL = sp.launchMs();
				double prevOmega = Double.MAX_VALUE;
				for (double t = tL + 150; t < sp.dropMs(); t += 5) {
					sp.sample(t, s);
					assertTrue(s.omega <= prevOmega + 1e-6, "orbit speed never rises: " + t);
					assertTrue(s.omega >= 250, "no stall on the track: " + s.omega + " at " + t + " ticks " + ticks);
					prevOmega = s.omega;
				}
				// the ball runs against the rotor until contact (−dir in absolute terms)
				for (double t = tL + 1; t < sp.contactMs() - 1; t += 3) {
					sp.sample(t, s);
					sp.sample(t + 1, q);
					assertTrue((q.abs - s.abs) * dir < 0, "ball runs at −dir at " + t);
				}
				// ω continuous at the SPIN start (≤ 2 %)
				sp.sample(sp.spinStartMs() - 0.01, s);
				sp.sample(sp.spinStartMs() + 0.01, q);
				assertEquals(s.omega, q.omega, s.omega * 0.02);
				// speed at the drop in [0.75, 0.95] rev/s
				sp.sample(sp.dropMs(), s);
				assertTrue(s.omega >= 270 && s.omega <= 342, "drop speed " + s.omega);
				// C0 in screen space: < 0.6 px on the 92 px track for 0.5 ms steps (except at contacts)
				double[] contacts = contactTimes(sp);
				for (double t = 0; t < sp.restMs() + 400; t += 0.5) {
					if (near(contacts, t, 0.6) || Math.abs(t - sp.launchMs()) < 0.6 || t < sp.launchMs() + 1) continue;
					sp.sample(t, s);
					sp.sample(t + 0.5, q);
					if (!s.visible || !q.visible) continue;
					double dx = q.r * Math.sin(Math.toRadians(q.abs)) - s.r * Math.sin(Math.toRadians(s.abs));
					double dy = q.r * Math.cos(Math.toRadians(q.abs)) - s.r * Math.cos(Math.toRadians(s.abs));
					assertTrue(Math.hypot(dx, dy) * RW_PX < 0.75, "jump " + Math.hypot(dx, dy) * RW_PX + " px at " + t + " phase " + s.phase);
					assertTrue(s.z >= 0);
				}
				// radius inside the bowl bands per phase
				for (double t = sp.launchMs(); t < sp.restMs() + 100; t += 2) {
					sp.sample(t, s);
					switch (s.phase) {
						case RouletteSpin.PH_LAUNCH, RouletteSpin.PH_ORBIT, RouletteSpin.PH_HUNT -> assertTrue(s.r >= 0.89 && s.r <= RouletteSpin.R_WALL + 0.0025,
							"track r " + s.r);
						case RouletteSpin.PH_SPIRAL -> assertTrue(s.r >= RouletteSpin.R_LIP - 1e-9 && s.r <= RouletteSpin.R_WALL, "stator r " + s.r);
						case RouletteSpin.PH_RATTLE, RouletteSpin.PH_SETTLE, RouletteSpin.PH_REST -> assertTrue(s.r >= RouletteSpin.R_POCKET - 1e-9
							&& s.r <= RouletteSpin.R_LIP + 0.03, "rotor r " + s.r);
						default -> { }
					}
				}
			}
		}
	}

	private static double[] contactTimes(RouletteSpin sp) {
		List<Double> out = new ArrayList<>();
		for (MotionEvent e : sp.events()) {
			int k = e.kind();
			if (k == RouletteSpin.EV_DROP || k == RouletteSpin.EV_DEFLECTOR || k == RouletteSpin.EV_CONTACT || k == RouletteSpin.EV_FRET || k == RouletteSpin.EV_FLOOR
				|| k == RouletteSpin.EV_KICKBACK || k == RouletteSpin.EV_REST) {
				out.add(e.tMs());
			}
		}
		out.add(sp.rattleEndMs());
		out.add(sp.launchMs() + 0.6 * 150.0);
		return out.stream().mapToDouble(Double::doubleValue).toArray();
	}

	private static boolean near(double[] ts, double t, double tol) {
		for (double x : ts) if (t > x - tol - 0.5 && t < x + tol) return true;
		return false;
	}

	// ---- 4. rattle ---------------------------------------------------------------------------------------------------------

	@Test
	void theRattleBouncesAcrossTheFrets() {
		RouletteSpin.Sample s = new RouletteSpin.Sample();
		for (int ticks : SPIN_TICKS) {
			for (int seed = 0; seed < 64; seed++) {
				RouletteSpin sp = spin(seed % 37, seed * 101 + ticks, ticks, rotor(seed, seed % 3 == 0 ? 1 : -1));
				BounceChain ch = sp.rattle();
				int n = ch.hops();
				assertTrue(n >= 4 && n <= 7, "hops " + n);
				int kicks = 0;
				for (int j = 0; j < n; j++) {
					if (ch.isKick(j)) kicks++;
					if (j > 0 && !ch.isKick(j) && !ch.isKick(j - 1)) {
						assertTrue(ch.airMs(j) < ch.airMs(j - 1), "airtimes decrease");
						assertTrue(ch.apex(j) < ch.apex(j - 1), "heights decrease");
					}
					if (j > 0 && !ch.isKick(j) && !ch.isKick(j - 1)) assertTrue(ch.landClass(j) != ch.landClass(j - 1), "fret / floor alternate");
				}
				assertTrue(kicks <= 1);
				assertTrue(sp.scatter() >= 5 && sp.scatter() <= 16, "scatter " + sp.scatter());
				// never inside a non-result pocket for more than 120 ms
				int cur = -2;
				double since = 0;
				for (double t = sp.contactMs(); t <= sp.restMs(); t += 1) {
					int p = sp.pocketUnder(t);
					if (p != cur) {
						cur = p;
						since = t;
					}
					if (p >= 0 && p != sp.result) assertTrue(t - since <= 120, "lingers in " + p + " at " + t);
				}
				sp.sample(sp.restMs(), s);
				assertEquals(RouletteSpin.PH_REST, s.phase);
			}
		}
	}

	// ---- 5. decoration invariance --------------------------------------------------------------------------------------------

	@Test
	void theDeflectorNeverMovesTheContactAngle() {
		RouletteSpin.Sample s = new RouletteSpin.Sample();
		int hits = 0;
		for (int seed = 0; seed < 400; seed++) {
			RouletteSpin sp = spin(seed % 37, seed * 7 + 3, 140, rotor(seed, 1));
			// the angle at contact equals the undecorated spiral's (the dip is windowed to 0 by t_C)
			sp.sample(sp.contactMs() - 1e-7, s);
			double atC = s.abs;
			sp.sample(sp.contactMs(), s);
			assertEquals(0, angDiff(atC, s.abs), 1e-4, "angle continuous at contact");
			assertEquals(0, angDiff(s.rel, sp.contactPocket() * RouletteSpin.STEP), 1e-6, "contact on the solved pocket coordinate");
			if (sp.deflectorHit()) {
				hits++;
				sp.sample(sp.deflectorMs(), s);
				double diamond = RouletteSpin.DIAMOND0 + sp.diamond() * RouletteSpin.DIAMOND_PITCH;
				double bx = s.r * Math.sin(Math.toRadians(s.abs)), by = s.r * Math.cos(Math.toRadians(s.abs));
				double dx = RouletteSpin.R_DIAMOND * Math.sin(Math.toRadians(diamond)), dy = RouletteSpin.R_DIAMOND * Math.cos(Math.toRadians(diamond));
				assertTrue(Math.hypot(bx - dx, by - dy) * RW_PX <= 1.5, "ball at the diamond when it hits");
			}
		}
		assertTrue(hits > 120 && hits < 330, "deflector about 55 %: " + hits);
	}

	// ---- 6. rotor continuity over a 50-round session -------------------------------------------------------------------------

	@Test
	void theRotorNeverSnapsOverAFiftyRoundSession() {
		RouletteDealer dealer = new RouletteDealer(0x5EED);
		RouletteSync prev = null;
		long now = 10_000;
		int result = 17;
		java.util.Random timers = new java.util.Random(4);
		for (int round = 0; round < 50; round++) {
			now += 40 + timers.nextInt(600); // BETTING
			double before = dealer.rotorAngleAt(now);
			RouletteDealer.Launch l = dealer.launch(now, 1234 + round);
			RotorModel m = l.rotor();
			assertEquals(0, angDiff(before, m.angle(0)), 0.01, "no snap at launch (round " + round + ")");
			assertEquals(l.dir(), (dealer.seq() & 1) == 0 ? 1 : -1, "the dealer alternates");
			if (prev != null) {
				// the server's rotor0 equals the previous model evaluated at the new launch tick
				RotorModel pm = prev.rotorModel();
				assertEquals(0, angDiff(pm.angle((now - prev.launchTick()) * 50.0), m.angle(0)), 0.01);
				assertEquals(pm.omega((now - prev.launchTick()) * 50.0), m.omega0(), 0.01);
			}
			long spinStart = now + 20;
			int ticks = new int[] {40, 100, 140, 300}[round % 4];
			RouletteSpin sp = RouletteSpin.build(result, l.seed(), result, now, spinStart, ticks, m);
			assertTrue(sp.restMs() <= sp.spinEndMs() - 300);
			// spin → result → betting: the model keeps going (sampled on the same model)
			double atEnd = sp.rotor(sp.spinEndMs());
			double atRes = sp.rotor(sp.spinEndMs() + 3000 + 0.5);
			assertTrue(Math.abs(atRes - atEnd) < 3.5 * 160, "turning, no jump");
			RouletteSync sync = new RouletteSync(RouletteSync.RESULT, spinStart + ticks, spinStart, ticks, result, l.seed(), 3, 0, dealer.seq(), now,
				RotorModel.centiAngle(m.angle0()), RotorModel.centiOmega(m.omega0()), l.dir());
			RouletteSync back = RouletteSync.decode(sync.encode());
			assertEquals(sync, back);
			prev = back;
			now = spinStart + ticks + 60;
			result = (result * 7 + 3) % 37;
		}
	}

	// ---- 7. determinism and frame-rate independence -----------------------------------------------------------------------

	@Test
	void eventsFireOnceInOrderAtAnyFrameRate() {
		RouletteSpin sp = spin(26, 4242, 140, rotor(1, 1));
		RouletteSpin again = spin(26, 4242, 140, rotor(1, 1));
		RouletteSpin.Sample a = new RouletteSpin.Sample();
		RouletteSpin.Sample b = new RouletteSpin.Sample();
		for (double t = 0; t < sp.restMs() + 500; t += 3.7) {
			sp.sample(t, a);
			again.sample(t, b);
			assertEquals(Double.doubleToLongBits(a.abs), Double.doubleToLongBits(b.abs));
			assertEquals(Double.doubleToLongBits(a.z), Double.doubleToLongBits(b.z));
		}
		MotionEvent[] ev = sp.events();
		assertTrue(MotionEvent.sorted(ev));
		for (double step : new double[] {4.2, 8.3, 16.7, 33, 500}) {
			EventCursor cur = new EventCursor(ev);
			List<MotionEvent> fired = new ArrayList<>();
			int[] silent = {0};
			for (double t = 0; t < sp.restMs() + 1000; t += step) {
				cur.advance(t, (e, quiet) -> {
					fired.add(e);
					if (quiet) silent[0]++;
				});
			}
			assertEquals(ev.length, fired.size(), "every event once at " + step);
			for (int i = 1; i < fired.size(); i++) assertTrue(fired.get(i).tMs() >= fired.get(i - 1).tMs());
			if (step < 100) assertEquals(0, silent[0], "no hitch at " + step);
			else assertTrue(silent[0] > 0, "a 500 ms hitch fires late events silently");
		}
	}

	// ---- 8. sync v2 round trip and v1 decode --------------------------------------------------------------------------------

	@Test
	void syncV2RoundTripsAndV1StillDecodes() {
		RouletteSync s = new RouletteSync(RouletteSync.SPIN, 5_000_000_123L, 5_000_000_123L, 140, 26, -77, 3, 1, 9, 5_000_000_103L, 35999, -15000, -1);
		assertEquals(17, s.encode().length);
		assertEquals(s, RouletteSync.decode(s.encode()));
		int[] v1 = {1, RouletteSync.SPIN, 1, 5, 1, 5, 100, 12, 42, 7, 0, 3};
		RouletteSync d = RouletteSync.decode(v1);
		assertEquals(((long) 1 << 32) | 5, d.spinStart());
		assertEquals(d.spinStart() - RouletteSpin.NMB_TICKS, d.launchTick());
		assertEquals(-1, d.dir());
		assertEquals(12, d.result());
		RotorModel m = d.rotorModel();
		assertEquals(RotorModel.OMEGA_IDLE, Math.abs(m.omega(0)), 1e-9);
	}
}

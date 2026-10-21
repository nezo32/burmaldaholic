package dev.nezo.burmaldaholic.games.slots.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimelineSeed;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDefaults;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotRng;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import dev.nezo.burmaldaholic.games.slots.v2.present.ReelHandoff;
import dev.nezo.burmaldaholic.games.slots.v2.present.ReelMotion;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotFrames;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * S1 (owner playtest, SY-SPEC §2–§5, slots row): the spinning player's pre-roll is the wait loop; when the result
 * arrives 0 / 300 / 800 / 1 500 ms late every reel decelerates from its current pose onto the server's stop. Asserts per
 * frame (60 fps): never backwards beyond the landing bounce, never faster than 3.5 × the cruise speed (no jump), and
 * the final window / stops equal the server's. Spectator clocks stay monotonic under ±1 s time jumps and bunched
 * packets; the in-world cabinet time too.
 */
class SlotSyncTest {
	private static final double FRAME = 1000 / 60.0;
	private static final int[] REST = {3, 7, 11, 2, 9};

	/** Test source: a settable wall clock and shared clock. */
	private static final class FakeTime implements SpinClock.TimeSource {
		double wall;
		double shared;

		@Override
		public double sharedMs(TimelineSeed seed, float partialTick) {
			return shared;
		}

		@Override
		public double levelMs(float partialTick) {
			return shared;
		}

		@Override
		public double wallMs() {
			return wall;
		}
	}

	@AfterEach
	void restore() {
		SpinClock.timeSource(null);
	}

	private static SpinTape tape(Machine m, int seed) {
		MachineDef d = SlotDefaults.def(m);
		return SlotDraw.draw(new SlotDraw.Request(d, m == Machine.END ? 100 : 20, false, true, null), SlotRng.seeded(seed));
	}

	/** Pre-roll probe of reel r exactly as {@code ReelView.preRoll} builds it. */
	private static ReelMotion probe(MachineDef d, int r) {
		return new ReelMotion(120, Integer.MAX_VALUE / 2, 350, false, REST[r], REST[r], d.stripLength(r));
	}

	/** Signed travel between two top indices on a strip of {@code len}, folded into [−len/2, len/2). */
	private static double travel(double prevTop, double top, int len) {
		double d = prevTop - top;
		return d - len * Math.floor(d / len + 0.5);
	}

	private static void runActor(Machine m, int seed, double lateMs) {
		FakeTime time = new FakeTime();
		SpinClock.timeSource(time);
		MachineDef d = SlotDefaults.def(m);
		SpinTape tp = tape(m, seed);
		Timeline tl = SlotTimeline.build(tp, d, TimingProfile.SHARED, TimingProfile.SHARED, 1);
		SlotScript script = new SlotScript(tl, d, REST, null);
		SlotFrames.Sampler f = new SlotFrames.Sampler(script);
		double[] prev = new double[5];
		for (int r = 0; r < 5; r++) prev[r] = REST[r];
		double maxStep = 3.5 * ReelMotion.V * FRAME + 1e-9;
		SpinClock clock = null;
		double maxSeen = 0;
		for (double wall = 0; wall < lateMs + tl.endMs() + 4000; wall += FRAME) {
			time.wall = wall;
			double[] top = new double[5];
			if (wall < lateMs) {
				for (int r = 0; r < 5; r++) top[r] = probe(d, r).top(wall - 30.0 * r);
			} else {
				if (clock == null) {
					// arrival: capture the wait-loop pose, plan the handoff, anchor the actor at arrival
					double[] top0 = new double[5];
					double[] v0 = new double[5];
					for (int r = 0; r < 5; r++) {
						top0[r] = probe(d, r).top(wall - 30.0 * r);
						v0[r] = Math.max(0, probe(d, r).cruiseVel(wall - 30.0 * r));
					}
					ReelHandoff.Plan plan = ReelHandoff.plan(script, top0, v0, wall);
					f.handoff(plan);
					clock = SpinClock.actor(tl, wall, plan.tH());
					assertTrue(plan.tH() <= wall + 1e-9, "never ahead of the pre-roll");
				}
				double t = clock.t(wall);
				f.sample(t);
				if (f.phase > 0) continue; // later phases (free spins) are ordinary timeline motion
				for (int r = 0; r < 5; r++) top[r] = f.moving[r] ? f.top[r] : prev[r] - travel(prev[r], restTop(f, r, script), d.stripLength(r));
			}
			for (int r = 0; r < 5; r++) {
				double step = travel(prev[r], top[r], d.stripLength(r));
				maxSeen = Math.max(maxSeen, step);
				assertTrue(step >= -0.2, m + " late " + lateMs + " reel " + r + " moved back " + step + " at " + wall);
				assertTrue(step <= maxStep, m + " late " + lateMs + " reel " + r + " jumped " + step + " cells at " + wall);
				prev[r] = top[r];
			}
		}
		assertTrue(clock != null && clock.done(), "the spin finished");
		f.sample(clock.t(time.wall));
		assertArrayEquals(SlotTimeline.terminalWindow(tp, d), f.cells, m + " final window = the server's");
		SlotScript.Phase p0 = script.phase(0);
		for (int r = 0; r < 5; r++) assertEquals(tp.stops()[r], p0.reels[r].stop(), "server stop");
		assertTrue(maxSeen > ReelMotion.V * FRAME * 0.5, "the reels actually spun");
	}

	/** Top of a reel at rest in the sampled frame (its landed stop; first phase only). */
	private static double restTop(SlotFrames.Sampler f, int r, SlotScript script) {
		SlotScript.Phase p = script.phase(Math.max(0, f.phase));
		ReelMotion m = p.reels[r];
		return m == null ? p.restStops[r] : m.stop();
	}

	@Test
	void lateResultsDecelerateFromThePreRollWithoutAJump() {
		for (Machine m : Machine.values()) {
			for (double late : new double[] {0, 300, 800, 1500}) {
				for (int seed = 1; seed <= 6; seed++) runActor(m, seed, late);
			}
		}
	}

	@Test
	void handoffSpeedStaysInsideTheBand() {
		for (Machine m : Machine.values()) {
			MachineDef d = SlotDefaults.def(m);
			for (int seed = 1; seed <= 40; seed++) {
				SpinTape tp = tape(m, seed);
				Timeline tl = SlotTimeline.build(tp, d, TimingProfile.SHARED, TimingProfile.SHARED, 1);
				SlotScript script = new SlotScript(tl, d, REST, null);
				for (double arrival : new double[] {130, 300, 800, 1500, 4000}) {
					double[] top0 = new double[5];
					double[] v0 = new double[5];
					for (int r = 0; r < 5; r++) {
						top0[r] = probe(d, r).top(arrival - 30.0 * r);
						v0[r] = probe(d, r).cruiseVel(arrival - 30.0 * r);
					}
					ReelHandoff.Plan plan = ReelHandoff.plan(script, top0, v0, arrival);
					SlotScript.Phase p = script.phase(0);
					for (int r = 0; r < 5; r++) {
						ReelHandoff h = plan.reels()[r];
						ReelMotion mo = p.reels[r];
						double from = plan.tH() - p.spinUpAt;
						assertEquals(top0[r], h.top(from), 1e-9, "C0 at the arrival");
						assertEquals(Math.max(0, v0[r]), h.speed(from), 1e-9, "C1 at the arrival (the spin-up back-kick is clamped to 0)");
						double end = h.top(mo.landAt());
						double gap = end - mo.top(mo.landAt());
						assertEquals(0, gap - d.stripLength(r) * Math.rint(gap / d.stripLength(r)), 1e-6, "lands on the server's stop line");
						assertEquals(mo.landStartSpeed(), h.speed(mo.landAt()), 1e-9, "C1 into the landing");
						for (double t = from; t <= mo.landAt(); t += 5) {
							double v = h.speed(t);
							assertTrue(v >= 0 && v <= 3.5 * ReelMotion.V, m + " speed " + v / ReelMotion.V + " V");
						}
					}
				}
			}
		}
	}

	@Test
	void actorClockIgnoresSharedTimeJumpsAndIsNeverLate() {
		FakeTime time = new FakeTime();
		SpinClock.timeSource(time);
		MachineDef d = SlotDefaults.def(Machine.NETHER);
		Timeline tl = SlotTimeline.build(tape(Machine.NETHER, 3), d, TimingProfile.SHARED, TimingProfile.SHARED, 1);
		time.wall = 5000;
		time.shared = tl.sharedEndMs() + 5000; // the result arrived "after the end" on the shared clock
		SpinClock c = SpinClock.actor(tl, 5000, -400);
		double last = c.t(5000);
		assertEquals(-400, last, 1e-9);
		assertFalse(c.late(), "the player's own spin is never late");
		for (int i = 1; i < 300; i++) {
			time.wall = 5000 + i * FRAME;
			time.shared += i % 50 == 0 ? -1000 : i % 77 == 0 ? 1000 : FRAME; // ±1 s jumps
			double t = c.t(time.wall);
			assertEquals(-400 + i * FRAME, t, 1e-6, "local time since arrival");
			assertTrue(t >= last);
			last = t;
		}
	}

	@Test
	void spectatorClockIsMonotonicUnderJumpsAndBunching() {
		FakeTime time = new FakeTime();
		SpinClock.timeSource(time);
		MachineDef d = SlotDefaults.def(Machine.OVERWORLD);
		Timeline tl = SlotTimeline.build(tape(Machine.OVERWORLD, 5), d, TimingProfile.SHARED, TimingProfile.SHARED, 1);
		TimelineSeed seed = new TimelineSeed("slots.overworld", 1, 100, 1, 100);
		time.shared = -200; // server lead: the result arrives before beat 0
		SpinClock c = SpinClock.server(tl, seed, () -> 0f);
		double last = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < 400; i++) {
			time.wall = i * FRAME;
			if (i == 60) time.shared -= 1000; // time packet: back 1 s
			else if (i == 120) time.shared += 1000; // forward 1 s
			else if (i % 30 < 20) time.shared += 0; // bunched: shared stalls …
			else time.shared += FRAME * 3; // … then catches up
			double t = c.t(time.wall);
			assertTrue(t >= last, "spectator t never steps back");
			last = t;
		}
		assertFalse(c.late());
		// a spectator who arrives after the shared part settles
		time.shared = tl.sharedEndMs() + 10;
		SpinClock lateJoin = SpinClock.server(tl, seed, () -> 0f);
		lateJoin.t(0);
		assertTrue(lateJoin.late());
	}

	@Test
	void cabinetTimeIsMonotonicPerSpin() {
		double t = SlotCabinetRenderer.cabinetT(10_000, 100, Double.NaN);
		assertEquals(5_000, t, 1e-9);
		assertEquals(5_000, SlotCabinetRenderer.cabinetT(9_000, 100, t), 1e-9, "a time packet back does not replay the reels");
		assertEquals(5_500, SlotCabinetRenderer.cabinetT(10_500, 100, t), 1e-9);
	}
}

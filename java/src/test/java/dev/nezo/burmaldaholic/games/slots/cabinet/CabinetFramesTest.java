package dev.nezo.burmaldaholic.games.slots.cabinet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetFrames.Motion;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Fidelity of the in-world cabinet (slots.md §2.1 F2/F4/F10, docs/architecture/animation.md §3.4): the cabinet
 * always ends on the server's window, reels land exactly on their stops, overshoot stays ≤ 0.18 cell, and the
 * sync survives the update-tag codec.
 */
class CabinetFramesTest {
	private static final Motion[] MOTIONS = Motion.values();

	/** Deterministic test strips (11 symbols, lengths 32…36 so reels differ). */
	static int[][] strips(int seed) {
		SeedMix.FxRng rng = new SeedMix.FxRng(seed);
		int[][] s = new int[5][];
		for (int r = 0; r < 5; r++) {
			s[r] = new int[32 + r];
			for (int i = 0; i < s[r].length; i++) s[r][i] = rng.nextInt(11);
		}
		return s;
	}

	static int[] stops(SeedMix.FxRng rng, int[][] strips) {
		int[] st = new int[5];
		for (int r = 0; r < 5; r++) st[r] = rng.nextInt(strips[r].length);
		return st;
	}

	static int[] randomWindow(SeedMix.FxRng rng) {
		int[] w = new int[15];
		for (int i = 0; i < 15; i++) w[i] = rng.nextInt(11);
		return w;
	}

	/** A Nether spin with a 2-step tumble chain and a win on the final window, then a Hoard. */
	static CabinetSync nether(int seed) {
		SeedMix.FxRng rng = new SeedMix.FxRng(seed);
		int[][] strips = strips(seed);
		CabinetSync.Builder b = CabinetSync.builder(Machine.NETHER, 7, 1000, seed, 100, strips, stops(rng, strips));
		b.spin(stops(rng, strips), new int[] {600, 750, 900, 1900, 2900}, 0b11000)
			.tumble(0b000_000_001_011_011, 2, randomWindow(rng))
			.tumble(0b000_100_100_100_000, 3, randomWindow(rng))
			.wins(0b100_100_100, 0)
			.done();
		b.hoard(0b1000_0000_0101, new int[] {5, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, -2, 0, 0, 0}, new int[] {0b1000_0000_0101, 0b1100_0000_0101, 0b1100_0000_0101});
		return b.result(WinTier.BIG, 12345, 2, false).build();
	}

	/** An End base spin + 3 free spins with sticky wilds, then the Dragon Wheel. */
	static CabinetSync end(int seed, int speed) {
		SeedMix.FxRng rng = new SeedMix.FxRng(seed);
		int[][] strips = strips(seed);
		CabinetSync.Builder b = CabinetSync.builder(Machine.END, 3, 50_000, seed, speed, strips, stops(rng, strips));
		b.spin(stops(rng, strips)).done().freeSpinsIntro();
		b.spin(stops(rng, strips)).stickyAfter(0b00100).wins(0b000_000_111_000_000).done();
		b.spin(stops(rng, strips)).held(0b00100).stickyAfter(0b01100).done();
		b.spin(stops(rng, strips)).held(0b01100).stickyAfter(0b01110).wins(0b1).done();
		b.wheel(new int[] {19, 3, 11});
		return b.result(WinTier.EPIC, 99_999, 4, true).build();
	}

	/** An Overworld spin with a Treasure Hunt in progress (two picks). */
	static CabinetSync overworld(int seed) {
		SeedMix.FxRng rng = new SeedMix.FxRng(seed);
		int[][] strips = strips(seed);
		CabinetSync.Builder b = CabinetSync.builder(Machine.OVERWORLD, 1, 20, seed, 200, strips, stops(rng, strips));
		b.spin(stops(rng, strips)).done();
		b.hunt(b.cursor(), new int[] {4, 11}, new int[] {5, -1}, false);
		return b.result(WinTier.WIN, 50, 0, false).build();
	}

	static List<CabinetSync> samples() {
		return List.of(nether(1), nether(99), end(5, 100), end(6, 200), overworld(8));
	}

	@Test
	void codecRoundTrips() {
		for (CabinetSync s : samples()) {
			int[] enc = s.encode();
			CabinetSync back = CabinetSync.decode(enc);
			assertEquals(s, back);
			assertArrayEquals(enc, back.encode());
			assertTrue(enc.length < 1200, "sync too big: " + enc.length);
		}
		int[] enc = nether(1).encode();
		assertThrows(IllegalArgumentException.class, () -> CabinetSync.decode(java.util.Arrays.copyOf(enc, enc.length - 1)));
		int[] bad = enc.clone();
		bad[0] = 99;
		assertThrows(IllegalArgumentException.class, () -> CabinetSync.decode(bad));
	}

	@Test
	void terminalShowsTheServerWindowInEveryMotionMode() {
		for (CabinetSync s : samples()) {
			if (s.hunt() != null) continue; // the hunt board stays until the next sync
			CabinetFrame term = CabinetFrames.INSTANCE.terminal(s);
			assertArrayEquals(s.finalWindow(), term.landedWindow(), "terminal window");
			assertTrue(term.settled);
			assertEquals(Marquee.Pattern.IDLE, term.marquee);
			for (Motion m : MOTIONS) {
				CabinetFrame f = new CabinetFrame();
				CabinetFrames.sample(s, s.endMs(), m, f);
				assertEquals(term, f, "frame at endMs == terminal (" + m + ")");
				CabinetFrames.sample(s, s.endMs() + 123_456, m, f);
				assertEquals(term, f, "frame after endMs == terminal (" + m + ")");
			}
		}
	}

	@Test
	void everyReelLandsOnItsStopAtItsStopTime() {
		for (CabinetSync s : samples()) {
			for (int i = 0; i < s.spins().size(); i++) {
				CabinetSync.Spin sp = s.spins().get(i);
				for (int r = 0; r < 5; r++) {
					if ((sp.heldMask() & 1 << r) != 0) continue;
					for (Motion m : MOTIONS) {
						CabinetFrame f = new CabinetFrame();
						CabinetFrames.sample(s, sp.startMs() + sp.stopMs()[r], m, f);
						int[] w = f.landedWindow();
						for (int y = 0; y < 3; y++) {
							assertEquals(sp.window()[r * 3 + y], w[r * 3 + y], "spin " + i + " reel " + r + " row " + y + " " + m);
						}
					}
				}
			}
		}
	}

	@Test
	void restWindowBeforeTheSpinAndContinuityOfTheSpinUp() {
		CabinetSync s = nether(3);
		CabinetFrame f = new CabinetFrame();
		CabinetFrames.sample(s, -500, Motion.FULL, f);
		assertArrayEquals(s.restWindow(), f.landedWindow());
		assertEquals(-1, f.spin);
		CabinetFrames.sample(s, 0, Motion.FULL, f);
		assertArrayEquals(s.restWindow(), f.landedWindow(), "the reels start where they rested");
	}

	@Test
	void landingNeverOvershootsMoreThanF4AndSwitchIsWholeCells() {
		for (int pct : new int[] {50, 100, 150, 200}) {
			for (int stop = (ReelMotion.SPIN_UP_MS + ReelMotion.ANTICIPATE_LAND_MS) * 100 / pct + 1; stop <= 4000; stop += 37) {
				for (boolean ant : new boolean[] {false, true}) {
					int target = ReelMotion.target(stop, pct, ant);
					double max = Double.NEGATIVE_INFINITY;
					for (double t = 0; t <= stop; t += 0.5) max = Math.max(max, ReelMotion.pos(t, stop, pct, ant) - target);
					assertTrue(max <= 0.18 + 1e-9, "overshoot " + max + " stop " + stop + " pct " + pct);
					assertEquals(target, ReelMotion.pos(stop, stop, pct, ant), 1e-12);
					double sw = ReelMotion.switchAt(stop, pct, ant);
					double before = ReelMotion.offset(sw - 1e-7, 5, 17, stop, pct, ant);
					double after = ReelMotion.offset(sw, 5, 17, stop, pct, ant);
					double jump = after - before;
					assertEquals(Math.round(jump), jump, 1e-4, "switch must move by whole cells");
					assertEquals(17, ReelMotion.offset(stop, 5, 17, stop, pct, ant), 1e-12);
					assertEquals(5, ReelMotion.offset(0, 5, 17, stop, pct, ant), 1e-12);
				}
			}
		}
	}

	@Test
	void reelsBlurAtFullSpeedAndScrollDownward() {
		CabinetSync s = nether(4);
		CabinetFrame f = new CabinetFrame();
		CabinetFrames.sample(s, 400, Motion.FULL, f);
		assertTrue(f.blur[4][0], "reel 5 at full speed draws blur frames");
		assertEquals(Marquee.Pattern.SPIN, f.marquee);
		// o decreases: the top cell's row grows over time between two nearby samples (reel moves down)
		double a = ReelMotion.offset(300, 0, 10, 2900, 100, true);
		double b = ReelMotion.offset(320, 0, 10, 2900, 100, true);
		assertTrue(b < a);
	}

	@Test
	void anticipationGlowsOnlyOnAnticipatedReelsAfterThePreviousStop() {
		CabinetSync s = nether(5); // reels 4 and 5 anticipate (mask 0b11000), stops 1900 / 2900
		CabinetFrame f = new CabinetFrame();
		CabinetFrames.sample(s, 800, Motion.FULL, f);
		assertEquals(0f, f.anticipate[3]);
		CabinetFrames.sample(s, 1200, Motion.FULL, f);
		assertTrue(f.anticipate[3] > 0);
		assertEquals(0f, f.anticipate[2]);
		CabinetFrames.sample(s, 1200, Motion.REDUCED, f);
		assertEquals(1f, f.anticipate[3], "static outline with reduce motion");
	}

	@Test
	void tumbleExplodesFallsAndLandsOnTheStepWindow() {
		CabinetSync s = nether(6);
		CabinetSync.Spin sp = s.spins().getFirst();
		CabinetSync.Tumble t0 = sp.tumbles().getFirst();
		CabinetFrame f = new CabinetFrame();
		// overview before the explode: win frames on the exploding cells
		CabinetFrames.sample(s, t0.atMs() - 100, Motion.FULL, f);
		assertEquals(t0.explodeMask(), f.winMask);
		// explode: burning cells
		CabinetFrames.sample(s, t0.atMs() + 100, Motion.FULL, f);
		boolean burning = false;
		for (int r = 0; r < 5; r++) for (int i = 0; i < f.count[r]; i++) burning |= f.burn[r][i] > 0;
		assertTrue(burning);
		assertEquals(2, f.multValue);
		assertTrue(f.multAlpha > 0);
		// mid-fall: every cell inside the drawable band, reel 0 had 2 exploded rows
		CabinetFrames.sample(s, t0.atMs() + CabinetFrames.EXPLODE_MS + 120, Motion.FULL, f);
		for (int r = 0; r < 5; r++) for (int i = 0; i < f.count[r]; i++) assertTrue(f.y[r][i] > -1 && f.y[r][i] < 3);
		// after the fall: the step window
		CabinetFrames.sample(s, t0.atMs() + 1000, Motion.FULL, f);
		int[] w = f.landedWindow();
		for (int c = 0; c < 15; c++) assertEquals(t0.cell(c), w[c], "cell " + c);
	}

	@Test
	void fallRowIsMonotonicUntilTheBounceAndEndsExactly() {
		double prev = -2;
		for (double t = 0; t < 400; t += 5) {
			float y = CabinetFrames.fallRow(-2, 1, t, 1);
			assertTrue(y >= prev - 4f / 44f - 1e-6);
			assertTrue(y <= 1f + 1e-6);
			prev = y;
		}
		assertEquals(1f, CabinetFrames.fallRow(-2, 1, 10_000, 1));
	}

	@Test
	void stickyEggsCrackExpandAndChain() {
		CabinetSync s = end(7, 100);
		CabinetSync.Spin fs1 = s.spins().get(1);
		CabinetFrame f = new CabinetFrame();
		CabinetFrames.sample(s, fs1.startMs() + fs1.stopMs()[2] + 50, Motion.FULL, f);
		assertEquals(1f / 3f, f.eggGrow[2], 1e-6);
		CabinetFrames.sample(s, fs1.startMs() + fs1.stopMs()[2] + CabinetFrames.STICK_END_MS + 10, Motion.FULL, f);
		assertEquals(1f, f.eggGrow[2]);
		assertEquals(1f, f.chain[2]);
		CabinetSync.Spin fs2 = s.spins().get(2);
		CabinetFrames.sample(s, fs2.startMs() + 300, Motion.FULL, f);
		assertEquals(1f, f.eggGrow[2], "held reel keeps its egg while the others spin");
		assertEquals(0f, f.eggGrow[3]);
		CabinetFrame term = CabinetFrames.INSTANCE.terminal(s);
		assertEquals(1f, term.eggGrow[1]);
		assertEquals(1f, term.eggGrow[2]);
		assertEquals(1f, term.eggGrow[3]);
		assertEquals(0f, term.eggGrow[0]);
	}

	@Test
	void wheelLandsInsideTheDrawnWedgeAwayFromBoundaries() {
		for (int seed = 0; seed < 200; seed++) {
			CabinetSync s = end(seed, seed % 2 == 0 ? 100 : 200);
			CabinetSync.Wheel w = s.wheel();
			for (int i = 0; i < w.segments().length; i++) {
				double fin = CabinetFrames.ringFinalAngle(s, i);
				assertEquals(w.segments()[i], CabinetFrames.wedgeAt(fin, w.ringSizes()[i]));
				double wedge = 360.0 / w.ringSizes()[i];
				double pos = ((-fin) % 360 + 360) % 360 / wedge;
				double frac = pos - Math.floor(pos);
				assertTrue(frac >= 0.2 - 1e-9 && frac <= 0.8 + 1e-9, "near a boundary: " + frac);
				assertEquals(fin, CabinetFrames.ringAngle(s, i, w.spinMs()[i] + w.spinDurMs()[i], Motion.FULL), 1e-9);
			}
			CabinetFrame f = new CabinetFrame();
			CabinetFrames.sample(s, w.spinMs()[2] + 10, Motion.FULL, f);
			assertEquals(3, f.ringCount);
			assertEquals(2, f.activeRing);
			assertEquals(1f, f.ringBright[2]);
		}
	}

	@Test
	void hoardFillsCoinsAndResetsPips() {
		CabinetSync s = nether(9);
		CabinetSync.Hoard h = s.hoard();
		CabinetFrame f = new CabinetFrame();
		CabinetFrames.sample(s, h.startMs() + 100, Motion.FULL, f);
		assertTrue(f.hoard);
		assertEquals(h.initialMask(), f.coinMask);
		assertEquals(3, f.pips);
		CabinetFrames.sample(s, h.stepMs()[0] + 100, Motion.FULL, f);
		assertTrue(f.cellSpin[3] >= 0, "empty cells spin during a respin");
		CabinetFrames.sample(s, h.stepMs()[1] + 890, Motion.FULL, f);
		assertEquals(h.stepMasks()[1], f.coinMask);
		assertEquals(2, f.pips, "step 1 no coin (3 → 2), step 2 new coin resets to 3 only after its end");
		CabinetFrames.sample(s, h.stepMs()[2] + 890, Motion.FULL, f);
		assertEquals(3, f.pips);
		CabinetFrames.sample(s, h.endMs() + 1, Motion.FULL, f);
		assertFalse(f.hoard);
	}

	@Test
	void tierTextAndMarqueeCelebrateAtTheGateOnly() {
		CabinetSync s = end(11, 100);
		CabinetFrame f = new CabinetFrame();
		CabinetFrames.sample(s, s.gateMs() - 1, Motion.FULL, f);
		assertEquals(0f, f.tierAlpha);
		CabinetFrames.sample(s, s.gateMs() + 1000, Motion.FULL, f);
		assertTrue(f.tierAlpha > 0);
		assertTrue(f.tierRise > 0 && f.tierRise <= 0.3f);
		assertEquals(Marquee.Pattern.JACKPOT, f.marquee);
		assertEquals(4, f.jackpotTier);
		CabinetFrames.sample(s, s.gateMs() + 1000, Motion.REDUCED, f);
		assertEquals(0f, f.tierRise, "no rise with reduce motion");
		CabinetSync small = overworld(2);
		CabinetFrames.sample(small, small.gateMs() + 100, Motion.FULL, f);
		assertEquals(0f, f.tierAlpha, "WIN tier: no floating word (Big+ only)");
	}

	@Test
	void huntBoardShowsOnlyOpenedChests() {
		CabinetSync s = overworld(3);
		CabinetFrame f = new CabinetFrame();
		CabinetFrames.sample(s, s.hunt().startMs() + 10, Motion.FULL, f);
		assertTrue(f.hunt);
		assertEquals(5, f.chest[4]);
		assertEquals(-1, f.chest[11]);
		assertEquals(CabinetFrame.CHEST_CLOSED, f.chest[0]);
		assertEquals(Marquee.Pattern.FEATURE, f.marquee);
	}

	@Test
	void fxPlanAndBeatsFollowTheSync() {
		CabinetSync s = nether(12);
		List<CabinetFxPlan.Event> ev = CabinetFxPlan.events(s);
		assertEquals(CabinetFxPlan.Kind.SPIN, ev.getFirst().kind());
		assertEquals(2, ev.stream().filter(e -> e.kind() == CabinetFxPlan.Kind.TUMBLE).count());
		assertTrue(ev.stream().anyMatch(e -> e.kind() == CabinetFxPlan.Kind.TIER && e.arg() == WinTier.BIG.ordinal()));
		assertTrue(ev.stream().anyMatch(e -> e.kind() == CabinetFxPlan.Kind.JACKPOT && e.arg() == 2));
		for (int i = 1; i < ev.size(); i++) assertTrue(ev.get(i).atMs() >= ev.get(i - 1).atMs());
		CabinetSync e = end(12, 100);
		assertEquals(3, CabinetFxPlan.events(e).stream().filter(x -> x.kind() == CabinetFxPlan.Kind.STICKY).count());
		assertTrue(CabinetFxPlan.events(e).stream().anyMatch(x -> x.kind() == CabinetFxPlan.Kind.TIER && x.arg() == CabinetFxPlan.MAX_WIN_ARG));
		for (CabinetSync x : samples()) {
			Timeline tl = CabinetBeats.timeline(x);
			assertTrue(tl.sharedEndMs() >= x.gateMs());
			assertTrue(tl.beats().stream().anyMatch(b -> b.kind().equals(SlotTimeline.END) && b.at() == x.gateMs()));
			long lands = tl.beats().stream().filter(b -> b.kind().equals(SlotTimeline.REEL_LAND)).count();
			long expected = x.spins().stream().mapToLong(sp -> 5 - Integer.bitCount(sp.heldMask())).sum();
			assertEquals(expected, lands);
		}
	}

	@Test
	void marqueeHonoursFlashesOff() {
		for (Marquee.Pattern p : new Marquee.Pattern[] {Marquee.Pattern.WIN, Marquee.Pattern.BIG, Marquee.Pattern.JACKPOT}) {
			int c0 = Marquee.color(p, Machine.END, 3, 0, false);
			for (int ms = 0; ms < 3000; ms += 17) assertEquals(c0, Marquee.color(p, Machine.END, 3, ms, false), p + " must not blink");
		}
		for (Marquee.Pattern p : Marquee.Pattern.values()) {
			for (int ms = 0; ms < 3000; ms += 13) {
				int fr = Marquee.frame(p, ms, true);
				assertTrue(fr >= 0 && fr < Marquee.FRAMES);
				if (p != Marquee.Pattern.IDLE && p != Marquee.Pattern.SPIN && p != Marquee.Pattern.FEATURE) {
					assertEquals(4, Marquee.frame(p, ms, false), p + " holds all-on without flashes");
				}
			}
		}
		assertTrue(Marquee.frame(Marquee.Pattern.WIN, 0, true) != Marquee.frame(Marquee.Pattern.WIN, 300, true));
		assertTrue(Marquee.color(Marquee.Pattern.WIN, Machine.NETHER, 0, 0, true) != Marquee.color(Marquee.Pattern.WIN, Machine.NETHER, 0, 300, true));
	}

	@Test
	void cabinetLogicIsPure() throws IOException {
		Path dir = Path.of(System.getProperty("burmaldaholic.projectDir", "."),
			"src/main/java/dev/nezo/burmaldaholic/games/slots/cabinet");
		try (Stream<Path> files = Files.list(dir)) {
			for (Path p : files.toList()) {
				String src = Files.readString(p);
				assertFalse(src.contains("import net.minecraft") || src.contains("import com.mojang"), p + " must stay pure");
			}
		}
	}
}

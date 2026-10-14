package dev.nezo.burmaldaholic.games.craps.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.dice.CubeGroup;
import dev.nezo.burmaldaholic.core.anim.dice.DiceEvent;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsBeats;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CM (CS §9.1 / §9.2) on the shipped craps tray: every throw rests on the server's faces inside the rest zone, every
 * die hits the back wall, the rest time sits in the CS §5 window and paces the server's reveal, the solve is
 * deterministic and result-independent before the rest, and the resting dice never overlap in ANY viewer's mapping
 * (each GUI size and the in-world print).
 */
class CrapsThrowTest {
	static final int SEEDS = System.getenv("DICE_SEEDS") != null ? Integer.parseInt(System.getenv("DICE_SEEDS")) : 48;
	static final int[][] SIZES = {{427, 240}, {455, 256}, {480, 270}, {640, 360}, {854, 480}, {960, 540}};

	static final int[] PUCKS = {0, 4, 5, 6, 8, 9, 10};

	static int seed(int i) {
		return 0xC4A95000 ^ (i * 0x9E3779B1);
	}

	@Test
	void restsOnTheFacesAfterHittingTheWall() {
		List<String> err = new ArrayList<>();
		DieSample s = new DieSample();
		List<CrapsLayout> layouts = new ArrayList<>();
		for (int[] wh : SIZES) layouts.add(CrapsLayout.of(TableGrid.of(wh[0], wh[1])));
		for (int side = 0; side < 2; side++) {
			for (int si = 0; si < SEEDS; si++) {
				int seed = seed(si);
				for (int d1 = 1; d1 <= 6; d1++)
					for (int d2 = 1; d2 <= 6; d2++) {
						DiceRoll r = CrapsThrow.solve(side, seed, d1, d2, PUCKS[si % PUCKS.length]);
						String at = "side " + side + " seed " + si + " " + d1 + d2 + ": ";
						int rest = r.restMs();
						if (rest < CrapsThrow.TARGET_MIN - 1 || rest > CrapsThrow.TARGET_MAX + 1) err.add(at + "rest " + rest);
						if (rest != CrapsThrow.targetMs(seed)) err.add(at + "target " + rest + " vs " + CrapsThrow.targetMs(seed));
						if (CrapsThrow.maxSpeed(r) > CrapsThrow.MAX_SPEED + 15) err.add(at + "too fast " + CrapsThrow.maxSpeed(r));
						int[] walls = new int[2];
						for (MotionEvent e : r.events()) {
							if (e.kind() == DiceEvent.WALL && e.obj() >= 0 && e.obj() < 2 && e.y() < 2) walls[e.obj()]++;
						}
						double[][] restAt = new double[2][];
						for (int d = 0; d < 2; d++) {
							if (walls[d] < 1) err.add(at + "die " + d + " never hit the back wall");
							if (r.wallMs(d) < 120 || r.wallMs(d) > 1100) err.add(at + "wall at " + r.wallMs(d));
							r.sample(d, rest + 50, s);
							int want = d == 0 ? d1 : d2;
							if (CubeGroup.faceUp(s.q) != want) err.add(at + "die " + d + " shows " + CubeGroup.faceUp(s.q));
							if (!CrapsThrow.restZone().contains(s.x, s.y)) err.add(at + "rest " + s.x + "," + s.y + " outside the zone");
							restAt[d] = new double[] {s.x, s.y};
						}
						// no overlap at rest in every viewer mapping (Chebyshev ≥ one die)
						for (CrapsLayout l : layouts) {
							var m = l.arenaMap();
							double dx = Math.abs(m.x(restAt[0][0]) - m.x(restAt[1][0]));
							double dy = Math.abs(m.y(restAt[0][1]) - m.y(restAt[1][1]));
							if (Math.max(dx, dy) < l.die - 1) err.add(at + "dice overlap at " + l.felt + " d " + dx + "," + dy);
						}
						double wdx = Math.abs(CrapsBoardGeometry.px(restAt[0][0]) - CrapsBoardGeometry.px(restAt[1][0])) * CrapsBoardGeometry.SCALE;
						double wdy = Math.abs(CrapsBoardGeometry.py(restAt[0][1]) - CrapsBoardGeometry.py(restAt[1][1])) * CrapsBoardGeometry.SCALE;
						if (Math.max(wdx, wdy) < CrapsBoardGeometry.DIE) err.add(at + "world dice overlap " + wdx + "," + wdy);
					}
			}
		}
		assertTrue(err.isEmpty(), err.size() + " failures\n" + String.join("\n", err.subList(0, Math.min(30, err.size()))));
	}

	@Test
	void deterministicAndPacesTheServer() {
		DiceRoll a = CrapsThrow.solve(CrapsThrow.RIGHT, 12345, 3, 4, 8);
		DiceRoll b = CrapsThrow.solve(CrapsThrow.RIGHT, 12345, 3, 4, 8);
		assertEquals(a.restMs(), b.restMs());
		DieSample x = new DieSample();
		DieSample y = new DieSample();
		for (double t = 0; t < a.restMs() + 100; t += 7.3) {
			for (int d = 0; d < 2; d++) {
				a.sample(d, t, x);
				b.sample(d, t, y);
				assertEquals(x.x, y.x, 0);
				assertEquals(x.zc, y.zc, 0);
			}
		}
		// the path does not depend on the faces (no result leak in the motion)
		DiceRoll c = CrapsThrow.solve(CrapsThrow.RIGHT, 12345, 6, 6, 8);
		assertEquals(a.restMs(), c.restMs());
		for (double t = 0; t < a.restMs(); t += 50) {
			a.sample(0, t, x);
			c.sample(0, t, y);
			assertEquals(x.x, y.x, 0);
			assertEquals(x.y, y.y, 0);
		}
		CrapsBeats beats = CrapsBeats.of(a.restMs());
		assertEquals((int) Math.ceil((a.restMs() + 330) / 50.0), CrapsBeats.revealTicks(a.restMs()));
		assertEquals(beats.CHIPS, a.restMs() + 330);
		assertTrue(CrapsBeats.revealTicks(a.restMs()) * 50 >= beats.CHIPS, "chat at the chips, never before the dice rest");
	}

	@Test
	void beatsStrictlyIncreaseAtEverySpeed() {
		for (int rest = CrapsThrow.TARGET_MIN; rest <= CrapsThrow.TARGET_MAX; rest += 40) {
			for (double speed : new double[] {0.5, 1, 1.5}) {
				CrapsBeats b = CrapsBeats.of(rest, speed);
				int[] seq = {b.rest, b.BADGE, b.BANNER, b.PUCK, b.CHIPS, b.TRAVEL, b.COLLECT, b.STICK, b.IDLE};
				for (int i = 1; i < seq.length; i++) assertTrue(seq[i] > seq[i - 1], b + " at " + i);
				assertTrue(b.IDLE <= rest + CrapsBeats.IDLE_CAP, "SLOW cap " + b);
				assertTrue(b.stickGone() <= b.IDLE + 1, "the stick is gone by IDLE " + b);
				// shared time never scales: the throw and the puck are the same at every speed
				assertEquals(CrapsBeats.of(rest).PUCK, b.PUCK);
				assertEquals(CrapsBeats.of(rest).CHIPS, b.CHIPS);
			}
		}
		assertEquals(1.0, CrapsBeats.of(1700, 1).f, 0);
		assertEquals(1 / 1.5, CrapsBeats.of(1700, 1.5).f, 1e-12);
	}

	@Test
	void homeAndReleaseLieOnDiceHome() {
		for (int[] wh : SIZES) {
			CrapsLayout l = CrapsLayout.of(TableGrid.of(wh[0], wh[1]));
			for (int side : new int[] {CrapsThrow.LEFT, CrapsThrow.RIGHT}) {
				Box home = l.diceHome(side).inset(-l.die / 2 - 1);
				for (int d = 0; d < 2; d++) {
					double[] p = CrapsThrow.release(side, d);
					var m = l.arenaMap();
					assertTrue(home.contains(m.x(p[0]), m.y(p[1])), wh[0] + " side " + side + " die " + d + " at " + m.x(p[0]) + "," + m.y(p[1]) + " home " + home);
				}
			}
		}
		assertEquals(CrapsThrow.LEFT, CrapsThrow.sideOfSeat(0, 4));
		assertEquals(CrapsThrow.LEFT, CrapsThrow.sideOfSeat(1, 4));
		assertEquals(CrapsThrow.RIGHT, CrapsThrow.sideOfSeat(2, 4));
		assertEquals(CrapsThrow.RIGHT, CrapsThrow.sideOfSeat(-1, 4));
	}
}

package dev.nezo.burmaldaholic.core.anim.dice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.craps.logic.CrapsFelt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Faithfulness of the shared dice throw (tables.md §0.6: 36 pairs × 64 seeds × 8 paths). */
class DiceThrowPathTest {
	/** Four craps throws (big / compact felt, two shooter sides) and four duel lanes. */
	static List<DiceThrowPath.Params> paths() {
		List<DiceThrowPath.Params> out = new ArrayList<>();
		for (CrapsFelt f : List.of(CrapsFelt.BIG, CrapsFelt.COMPACT)) {
			double[] r = f.restRegion();
			double[] s = f.throwStart();
			out.add(DiceThrowPath.Params.craps(s[0], s[1], f.wall(), f.dcW() + 2, f.w(), r[0], r[1], r[2], r[3]));
			out.add(DiceThrowPath.Params.craps(20, s[1], f.wall(), f.dcW() + 2, f.w(), r[0], r[1], r[2], r[3]));
		}
		out.add(DiceThrowPath.Params.duel(28, 84, 78, 84, 84, 88, 36));
		out.add(DiceThrowPath.Params.duel(332, 84, 240, 84, 246, 88, 36));
		out.add(DiceThrowPath.Params.duel(20, 60, 30, 40, 40, 44, 18));
		out.add(DiceThrowPath.Params.duel(250, 60, 150, 40, 160, 44, 18));
		return out;
	}

	@Test
	void everyThrowLandsOnTheRolledFaces() {
		DiceThrowPath.Sample s = new DiceThrowPath.Sample();
		for (DiceThrowPath.Params p : paths()) {
			for (int d1 = 1; d1 <= 6; d1++) {
				for (int d2 = 1; d2 <= 6; d2++) {
					for (int seed = 0; seed < 64; seed++) {
						DiceThrowPath path = DiceThrowPath.of(seed * 131 + 7, d1, d2, p);
						int T = path.durationMs();
						for (int die = 0; die < 2; die++) {
							path.sample(die, T, s);
							assertTrue(s.resting, "rests at T");
							assertEquals(-1, s.frame, "real face at T");
							assertEquals(die == 0 ? d1 : d2, s.face);
							assertEquals(0, s.z, 0);
							assertEquals(0, Math.floorMod(Math.round(s.theta), 90), "square at rest");
							assertEquals(path.restX(die), s.x, 0);
							path.sample(die, T * 3.0, s);
							assertEquals(path.restX(die), s.x, 0);
						}
						double gap = Math.hypot(path.restX(1) - path.restX(0), path.restY(1) - path.restY(0));
						assertTrue(gap >= p.restGap() - 1e-9, "dice never overlap: " + gap);
					}
				}
			}
		}
	}

	@Test
	void noFalseReadoutsBeforeTheLastTumble() {
		DiceThrowPath.Sample s = new DiceThrowPath.Sample();
		for (DiceThrowPath.Params p : paths()) {
			for (int seed = 0; seed < 32; seed++) {
				DiceThrowPath path = DiceThrowPath.of(seed, 3, 5, p);
				int T = path.durationMs();
				Set<Integer> frames = new HashSet<>();
				for (int die = 0; die < 2; die++) {
					for (double t = 0; t < T * DiceThrowPath.FACE_AT - 45; t += 5) {
						path.sample(die, t, s);
						assertTrue(s.frame >= 0 && s.frame < DiceThrowPath.TUMBLE_FRAMES, "blank tumble frame before 82 %: t=" + t);
						assertTrue(s.z >= 0, "above the felt");
						frames.add(s.frame);
					}
					for (double t = T * DiceThrowPath.FACE_AT + 45; t <= T; t += 5) {
						path.sample(die, t, s);
						assertEquals(-1, s.frame, "the real face from 82 % on");
						assertEquals(die == 0 ? 3 : 5, s.face);
					}
				}
				assertTrue(frames.size() > 3, "the dice tumble");
			}
		}
	}

	@Test
	void motionIsContinuous() {
		DiceThrowPath.Sample s = new DiceThrowPath.Sample();
		for (DiceThrowPath.Params p : paths()) {
			for (int seed = 0; seed < 16; seed++) {
				DiceThrowPath path = DiceThrowPath.of(seed, 6, 1, p);
				for (int die = 0; die < 2; die++) {
					path.sample(die, 0, s);
					double px = s.x;
					double py = s.y;
					double pz = s.z;
					for (double t = 1; t <= path.durationMs() + 5; t += 1) {
						path.sample(die, t, s);
						assertTrue(Math.hypot(s.x - px, s.y - py) < 6, "jump at t=" + t);
						assertTrue(Math.abs(s.z - pz) < 2, "z jump at t=" + t);
						px = s.x;
						py = s.y;
						pz = s.z;
					}
				}
			}
		}
	}

	@Test
	void cuesAndDeterminism() {
		DiceThrowPath.Params p = paths().get(0);
		DiceThrowPath a = DiceThrowPath.of(77, 2, 4, p);
		DiceThrowPath b = DiceThrowPath.of(77, 2, 4, p);
		DiceThrowPath.Sample sa = new DiceThrowPath.Sample();
		DiceThrowPath.Sample sb = new DiceThrowPath.Sample();
		for (double t = 0; t <= 1400; t += 17) {
			a.sample(1, t, sa);
			b.sample(1, t, sb);
			assertEquals(sa.x, sb.x, 0);
			assertEquals(sa.frame, sb.frame);
		}
		assertEquals("dice_throw", a.cues().get(0).sound());
		assertEquals("dice_wall", a.cues().get(1).sound());
		assertEquals(450, a.cues().get(1).atMs());
	}

	@Test
	void faceTable() {
		for (int top = 1; top <= 6; top++) {
			int[] f = DiceFaces.layout(top);
			assertEquals(top, f[DiceFaces.TOP]);
			assertEquals(7, f[DiceFaces.TOP] + f[DiceFaces.BOTTOM]);
			assertEquals(7, f[DiceFaces.NORTH] + f[DiceFaces.SOUTH]);
			assertEquals(7, f[DiceFaces.EAST] + f[DiceFaces.WEST]);
			int[] sorted = f.clone();
			java.util.Arrays.sort(sorted);
			assertArrayEquals(new int[] {1, 2, 3, 4, 5, 6}, sorted);
		}
	}

	@Test
	void chipStacks() {
		int[] out = new int[ChipStacks.MAX_DISCS];
		assertEquals(0, ChipStacks.discs(0, out));
		assertEquals(2, ChipStacks.discs(30, out));
		assertArrayEquals(new int[] {25, 5}, new int[] {out[0], out[1]});
		assertEquals(5, ChipStacks.discs(1_000_000, out));
		assertEquals(500, out[0]);
		assertEquals(4, ChipStacks.discs(4, out));
		assertEquals(25, ChipStacks.denomOf(99));
		assertEquals(1, ChipStacks.denomOf(0));
		assertTrue(ChipStacks.payoutDiscs(1_000_000) <= 8);
		assertEquals(0, ChipStacks.payoutDiscs(0));
	}

	@Test
	void duelTimelineMatchesTheServerSchedule() {
		for (int i = 0; i < 3; i++) {
			assertTrue(DuelTimeline.revealTick(i) * 50 >= DuelTimeline.reveal(i), "chat never before the reveal");
			assertTrue(DuelTimeline.revealTick(i) * 50 - DuelTimeline.reveal(i) < 50);
			assertTrue(DuelTimeline.throwAt(i, 1) + DiceThrowPath.DUEL_MS <= DuelTimeline.reveal(i), "their dice rest before the compare");
		}
		assertEquals(DuelTimeline.END, DuelTimeline.endMs(1));
		assertEquals(2 * DuelTimeline.ROUND_PERIOD + DuelTimeline.END, DuelTimeline.endMs(3));
		assertEquals(2, DuelTimeline.roundAt(99999, 3));
		assertEquals(0, DuelTimeline.cupFrame(5000, false));
	}
}

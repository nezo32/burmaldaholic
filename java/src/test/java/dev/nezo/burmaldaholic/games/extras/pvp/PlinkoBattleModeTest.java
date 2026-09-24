package dev.nezo.burmaldaholic.games.extras.pvp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.config.sections.ExtrasConfig;
import dev.nezo.burmaldaholic.core.config.sections.PvpConfig;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpEvent;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMath;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import dev.nezo.burmaldaholic.games.extras.logic.Plinko;
import dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattle;
import dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattleMode;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Plinko Battle, PVP.md §16.5 P1–P5 (+ codecs, timeline, bots B1/B2). Same vectors as Bedrock. */
class PlinkoBattleModeTest {
	private static final PvpConfig PVP = new PvpConfig();
	private static final ExtrasConfig.Plinko ROWS = new ExtrasConfig.Plinko();
	private static final PlinkoBattleMode MODE = new PlinkoBattleMode(() -> PVP, () -> ROWS);

	private static PvpRng rng(long seed) {
		SplittableRandom r = new SplittableRandom(seed);
		return new PvpRng() {
			@Override
			public int nextInt(int bound) {
				return r.nextInt(bound);
			}

			@Override
			public long nextLong(long bound) {
				return r.nextLong(bound);
			}

			@Override
			public boolean nextBoolean() {
				return r.nextBoolean();
			}
		};
	}

	@Test
	void p1PointsRows() {
		assertArrayEquals(new long[] {100, 30, 16, 14, 10, 10, 5, 10, 10, 14, 16, 30, 100}, MODE.pointsRow(Plinko.Risk.LOW));
		assertArrayEquals(new long[] {330, 110, 40, 20, 10, 6, 3, 6, 10, 20, 40, 110, 330}, MODE.pointsRow(Plinko.Risk.MEDIUM));
		assertArrayEquals(new long[] {1700, 240, 81, 20, 6, 2, 2, 2, 6, 20, 81, 240, 1700}, MODE.pointsRow(Plinko.Risk.HIGH));
		// rows follow the live config
		ExtrasConfig.Plinko custom = new ExtrasConfig.Plinko();
		custom.low = new double[] {0.25, 3, 1.6, 1.4, 1.0, 1.0, 0.54, 1.0, 1.0, 1.4, 1.6, 3, 10};
		PlinkoBattleMode m = new PlinkoBattleMode(() -> PVP, () -> custom);
		assertEquals(3, m.pointsRow(Plinko.Risk.LOW)[0]); // 2.5 rounds half up
		assertEquals(5, m.pointsRow(Plinko.Risk.LOW)[6]);
	}

	@Test
	void p2ExactMeanPointsPerBall() {
		assertEquals(9.65625, PlinkoBattle.meanPoints(MODE.pointsRow(Plinko.Risk.LOW)), 0);
		assertEquals(9.6572265625, PlinkoBattle.meanPoints(MODE.pointsRow(Plinko.Risk.MEDIUM)), 0);
		assertEquals(9.669921875, PlinkoBattle.meanPoints(MODE.pointsRow(Plinko.Risk.HIGH)), 0);
		// brute force over the 4096 paths agrees
		long[] high = MODE.pointsRow(Plinko.Risk.HIGH);
		long sum = 0;
		for (int mask = 0; mask < 4096; mask++) {
			sum += high[PlinkoBattle.bin(mask)];
		}
		assertEquals(9.669921875, sum / 4096.0, 0);
	}

	@Test
	void p3MaskToBin() {
		assertEquals(0, PlinkoBattle.bin(0b000000000000));
		assertEquals(12, PlinkoBattle.bin(0b111111111111));
		assertEquals(6, PlinkoBattle.bin(0b101010101010));
		assertEquals(Plinko.encode(Plinko.decode(0b101010101010)), 0b101010101010); // solo wire encoding
		assertEquals(Plinko.fromPath(Plinko.decode(0b110000000111), new double[13]).bin(), PlinkoBattle.bin(0b110000000111));
	}

	/** A mask with exactly {@code bin} rights. */
	private static int m(int bin) {
		return (1 << bin) - 1;
	}

	@Test
	void p4Underdog() {
		assertArrayEquals(new boolean[] {true, true, false}, PlinkoBattle.underdogs(new long[] {12, 12, 30}));
		assertArrayEquals(new boolean[] {false, false, false}, PlinkoBattle.underdogs(new long[] {20, 20, 20}));
		long[] low = MODE.pointsRow(Plinko.Risk.LOW);
		// ball 1: A bin 6 (5), B bin 5 (10), C bin 5 (10); final: everyone bin 4 (10) → A ×2 = 20
		int[][] paths = {{m(6), m(4)}, {m(5), m(4)}, {m(5), m(4)}};
		PlinkoBattle.Scored s = PlinkoBattle.score(paths, new int[] {0, 1, 2}, low, true);
		assertArrayEquals(new long[] {25, 20, 20}, s.totals());
		assertArrayEquals(new boolean[] {true, false, false}, s.underdog());
		assertArrayEquals(new int[] {0}, s.winners());
		assertTrue(s.events().stream().anyMatch(e -> e.kind().equals("underdog") && e.seat() == 0 && e.round() == 1));
		// boost off → a three-way tie on points and best ball → split
		PlinkoBattle.Scored off = PlinkoBattle.score(paths, new int[] {2, 0, 1}, low, false);
		assertArrayEquals(new long[] {15, 20, 20}, off.totals());
		assertArrayEquals(new int[] {1, 2}, off.winners()); // winners by participant index (Bedrock parity)
		assertArrayEquals(new int[] {2, 1, 0}, off.rankOrder()); // exact tie ordered by seat order [2, 0, 1]
		// one ball: nobody is boosted
		assertFalse(PlinkoBattle.score(new int[][] {{m(6)}, {m(0)}}, new int[] {0, 1}, low, true).underdog()[0]);
	}

	@Test
	void p4TieBreakBestBallThenSplit() {
		long[] low = MODE.pointsRow(Plinko.Risk.LOW);
		// A: 30 + 10 = 40 (best 30), B: 16 + 14 + ... → make B 14 + 16 + 10 = 40 (best 16)
		int[][] paths = {{m(1), m(4), m(6), m(6)}, {m(3), m(2), m(4), m(8)}};
		// A: 30 + 10 + 5 + 5 = 50, B: 14 + 16 + 10 + 10 = 50; before final A 45, B 40 → B is boosted: final 20 → 60
		PlinkoBattle.Scored boosted = PlinkoBattle.score(paths, new int[] {0, 1}, low, true);
		assertArrayEquals(new long[] {50, 60}, boosted.totals());
		PlinkoBattle.Scored s = PlinkoBattle.score(paths, new int[] {1, 0}, low, false);
		assertArrayEquals(new long[] {50, 50}, s.totals());
		assertArrayEquals(new long[] {30, 16}, s.best());
		assertArrayEquals(new int[] {0}, s.winners());
		// equal best too → split; odd chip to the first winner in seat order
		int[][] same = {{m(1), m(4)}, {m(11), m(8)}};
		PlinkoBattle.Scored split = PlinkoBattle.score(same, new int[] {1, 0}, low, false);
		assertArrayEquals(new int[] {0, 1}, split.winners());
		assertArrayEquals(new long[] {48, 49}, PvpMath.split(97, split.winners(), new int[] {1, 0}, 2)); // odd chip by seat order
	}

	@Test
	void edgeEventsAndFinalBall() {
		long[] high = MODE.pointsRow(Plinko.Risk.HIGH);
		int[][] paths = {{m(0), m(12)}, {m(6), m(6)}};
		PlinkoBattle.Scored s = PlinkoBattle.score(paths, new int[] {0, 1}, high, true);
		List<PvpEvent> edges = s.events().stream().filter(e -> e.kind().equals("edge")).toList();
		assertEquals(2, edges.size());
		assertEquals(1700L, edges.get(0).data().get("points"));
		// before final: A 1700, B 2 → B is the underdog: 2 × 2
		assertEquals(3400, s.totals()[0]);
		assertEquals(6, s.totals()[1]);
		assertEquals(2, s.events().stream().filter(e -> e.kind().equals("final_ball")).count());
	}

	@Test
	void validateAndDefaults() {
		assertEquals(new PlinkoBattleMode.Params("low", 3), MODE.defaults());
		assertNull(MODE.validate(new PlinkoBattleMode.Params("high", 5)));
		assertNotNull(MODE.validate(new PlinkoBattleMode.Params("high", 4)));
		assertNotNull(MODE.validate(new PlinkoBattleMode.Params("extreme", 3)));
		assertEquals(6, MODE.maxPlayers());
		assertTrue(MODE.enabled());
		PvpConfig off = new PvpConfig();
		off.plinko.enabled = false;
		assertFalse(new PlinkoBattleMode(() -> off, () -> ROWS).enabled());
	}

	@Test
	void codecsRoundTripAndSize() {
		PlinkoBattleMode.Params p = new PlinkoBattleMode.Params("high", 5);
		assertEquals(p, MODE.decodeParams(MODE.encodeParams(p)));
		PlinkoBattleMode.Params ten = new PlinkoBattleMode.Params("medium", 10);
		PlinkoBattleMode.Tape t = MODE.draw(rng(7), 6, ten);
		String json = MODE.encodeTape(t).toString();
		assertTrue(json.length() <= 700, json.length() + " chars");
		assertEquals(t, MODE.decodeTape(com.google.gson.JsonParser.parseString(json)));
		Outcome a = MODE.score(t, new long[] {100, 100, 100, 100, 100, 100}, ten);
		Outcome b = MODE.score(MODE.decodeTape(MODE.encodeTape(t)), new long[] {100, 100, 100, 100, 100, 100}, ten);
		assertArrayEquals(a.points(), b.points());
		assertArrayEquals(a.winners(), b.winners());
	}

	@Test
	void tapeFreezesConfig() {
		PvpConfig pvp = new PvpConfig();
		ExtrasConfig.Plinko rows = new ExtrasConfig.Plinko();
		PlinkoBattleMode m = new PlinkoBattleMode(() -> pvp, () -> rows);
		PlinkoBattleMode.Params p = new PlinkoBattleMode.Params("low", 3);
		PlinkoBattleMode.Tape t = m.draw(rng(3), 3, p);
		long[] before = m.score(t, new long[3], p).points();
		rows.low = new double[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
		pvp.plinko.underdogBoost = false;
		assertArrayEquals(before, m.score(t, new long[3], p).points());
	}

	@Test
	void timelineShape() {
		PlinkoBattleMode.Params p = new PlinkoBattleMode.Params("low", 3);
		PlinkoBattleMode.Tape t = new PlinkoBattleMode.Tape(new int[] {0, 1}, new int[][] {{m(6), m(4), m(12)}, {m(5), m(5), m(0)}},
			MODE.pointsRow(Plinko.Risk.LOW), true);
		List<Step> steps = MODE.timeline(t, MODE.score(t, new long[] {10, 10}, p), p);
		List<String> kinds = steps.stream().map(Step::kind).toList();
		assertEquals(List.of("ball_wait", "drop", "ball_score", "ball_wait", "drop", "ball_score", "underdog", "ball_wait", "drop"), kinds);
		assertTrue(steps.get(0).waitForAll());
		assertEquals(80, steps.get(0).ticks());
		assertEquals(48, steps.get(1).ticks());
		assertEquals(40, steps.get(2).ticks());
		assertEquals(20, steps.get(6).ticks());
		JsonObject finalDrop = steps.get(8).data();
		assertTrue(finalDrop.get("final").getAsBoolean());
		assertEquals(11, finalDrop.get("rows").getAsInt());
		assertFalse(finalDrop.has("bins")); // the landing stays hidden until the Final Reveal
		assertEquals(m(11), finalDrop.getAsJsonArray("masks").get(0).getAsInt());
		assertEquals(0, finalDrop.getAsJsonArray("masks").get(1).getAsInt());
		JsonObject score2 = steps.get(5).data();
		assertEquals(15, score2.getAsJsonArray("totals").get(0).getAsLong()); // 5 + 10
		assertEquals(20, score2.getAsJsonArray("totals").get(1).getAsLong()); // 10 + 10
		assertEquals(1, score2.getAsJsonArray("standings").get(0).getAsInt());
	}

	/** P5 / B2: every seat wins 1/N of the pot (split wins counted fractionally), High risk, 3 balls. */
	@Test
	void p5Fairness() {
		for (int n : new int[] {2, 6}) {
			PlinkoBattleMode.Params p = new PlinkoBattleMode.Params("high", 3);
			PvpRng r = rng(1000 + n);
			double[] share = new double[n];
			int matches = 1_000_000;
			long[] stakes = new long[n];
			for (int k = 0; k < matches; k++) {
				Outcome o = MODE.score(MODE.draw(r, n, p), stakes, p);
				for (int w : o.winners()) {
					share[w] += 1.0 / o.winners().length;
				}
			}
			for (int i = 0; i < n; i++) {
				assertEquals(1.0 / n, share[i] / matches, 0.003, "seat " + i + " of " + n);
			}
		}
	}

	/** B1: the tape and the score depend only on the fair rng (there is no bot input at all). */
	@Test
	void b1SameSeedSameTape() {
		PlinkoBattleMode.Params p = new PlinkoBattleMode.Params("medium", 5);
		PlinkoBattleMode.Tape a = MODE.draw(rng(99), 4, p);
		PlinkoBattleMode.Tape b = MODE.draw(rng(99), 4, p);
		assertEquals(a, b);
		assertArrayEquals(MODE.score(a, new long[4], p).points(), MODE.score(b, new long[4], p).points());
	}
}

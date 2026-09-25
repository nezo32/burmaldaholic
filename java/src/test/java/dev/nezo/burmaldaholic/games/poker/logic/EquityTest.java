package dev.nezo.burmaldaholic.games.poker.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Range-aware Monte-Carlo equity (BOTS.md §4.3, §12.1.2) — same vectors as Bedrock {@code bots.test.ts}. */
class EquityTest {
	private static double eq(String hole, String board, int opponents, int samples, long seed) {
		return Equity.equity(Equity.Input.vsRandom(Cards.parseAll(hole), Cards.parseAll(board), opponents, samples), BotRng.seeded(seed));
	}

	@Test
	void knownMatchups() {
		double aa = eq("As Ah", "", 1, 6000, 7);
		assertTrue(aa > 0.82 && aa < 0.88, "AA vs a random hand ≈ 85 %: " + aa);
		double bad = eq("7s 2h", "", 1, 6000, 8);
		assertTrue(bad > 0.31 && bad < 0.39, "72o ≈ 35 %: " + bad);
		assertTrue(eq("Qs Jh", "", 4, 3000, 9) < eq("Qs Jh", "", 1, 3000, 9) - 0.2, "equity drops with more opponents");
		assertEquals(1, eq("As Ks", "Qs Js Ts 2c 3d", 3, 300, 1), 1e-12, "the nuts on the river");
		assertEquals(0.5, eq("2c 3d", "As Ks Qs Js Ts", 1, 200, 2), 1e-9, "a board royal always splits");
		assertEquals(1, eq("2s 3d", "", 0, 100, 1), 1e-12, "no opponents");
	}

	@Test
	void aRaisersRangeIsMuchToughterThanRandomHands() {
		int[] hole = Cards.parseAll("Ks 9h");
		double any = Equity.equity(new Equity.Input(hole, new int[0], List.of(Ranges.Spec.FULL), null, 5000), BotRng.seeded(3));
		double tight = Equity.equity(new Equity.Input(hole, new int[0], List.of(Ranges.rangeOf(Ranges.Tag.STRONG, false)), null, 5000),
			BotRng.seeded(3));
		assertTrue(tight < any - 0.12, "the research flaw (equity vs random hands): " + tight + " vs " + any);
	}

	/** BOTS.md §12.1.2: the Monte-Carlo card pool = 52 − own hole cards − visible board (50 / 47 / 46 / 45). */
	@Test
	void neverSamplesACardTheBotCanSee() {
		int[] sizes = {50, 47, 46, 45};
		String[] boards = {"", "Kd 7c 2s", "Kd 7c 2s 9h", "Kd 7c 2s 9h 3c"};
		for (int b = 0; b < boards.length; b++) {
			int[] hole = Cards.parseAll("As Ah");
			int[] board = Cards.parseAll(boards[b]);
			Set<Integer> seen = new HashSet<>();
			for (int c : hole) {
				seen.add(c);
			}
			for (int c : board) {
				seen.add(c);
			}
			Set<Integer> drawn = new HashSet<>();
			List<Integer> sample = new ArrayList<>();
			int perSample = 2 * 4 + (5 - board.length);
			Equity.Work w = new Equity.Work(new Equity.Input(hole, board,
				List.of(Ranges.Spec.FULL, Ranges.rangeOf(Ranges.Tag.STRONG, true), Ranges.Spec.FULL), Ranges.Spec.FULL, 2000, c -> {
					sample.add(c);
					if (sample.size() == perSample) {
						assertEquals(perSample, new HashSet<>(sample).size(), "no card twice in a sample");
						sample.clear();
					}
					drawn.add(c);
				}), BotRng.seeded(5));
			w.step(2000);
			assertEquals(2000, w.result().samples());
			for (int c : drawn) {
				assertFalse(seen.contains(c), "drew a visible card");
			}
			assertEquals(sizes[b], drawn.size(), "pool size for a board of " + board.length);
		}
	}

	@Test
	void resumableWithTheDefencePercentile() {
		Equity.Work w = new Equity.Work(new Equity.Input(Cards.parseAll("Ks Kd"), Cards.parseAll("Kc 7h 2d"), List.of(Ranges.Spec.FULL),
			Ranges.Spec.FULL, 200), BotRng.seeded(4));
		assertEquals(0, w.result().samples());
		assertEquals(30, w.step(30));
		assertEquals(30, w.progress());
		assertFalse(w.done());
		w.step(1000);
		assertTrue(w.done());
		assertTrue(w.result().equity() > 0.9);
		assertTrue(w.result().pct() > 0.9, "top set beats nearly its whole range");
	}
}

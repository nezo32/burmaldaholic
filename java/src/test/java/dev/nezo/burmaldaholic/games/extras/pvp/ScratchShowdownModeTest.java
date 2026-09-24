package dev.nezo.burmaldaholic.games.extras.pvp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.config.sections.PvpConfig;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpEvent;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import dev.nezo.burmaldaholic.games.extras.pvp.scratch.ScratchShowdownMode;
import dev.nezo.burmaldaholic.games.extras.pvp.scratch.ShowdownCard;
import dev.nezo.burmaldaholic.games.extras.pvp.scratch.ShowdownCard.Sym;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Scratch Showdown, PVP.md §16.6 R1–R7 (+ codecs, timeline, bots B1/B2). Same vectors as Bedrock. */
class ScratchShowdownModeTest {
	private static final PvpConfig PVP = new PvpConfig();
	private static final ScratchShowdownMode MODE = new ScratchShowdownMode(() -> PVP);
	private static final int[] V = ShowdownCard.DEFAULT_VALUES;

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

	private static int[] card(Sym... s) {
		int[] out = new int[s.length];
		for (int i = 0; i < s.length; i++) {
			out[i] = s[i].ordinal();
		}
		return out;
	}

	@Test
	void configMaps() {
		assertArrayEquals(ShowdownCard.DEFAULT_WEIGHTS, ShowdownCard.weights(PVP.scratch.weights));
		assertArrayEquals(ShowdownCard.DEFAULT_VALUES, ShowdownCard.values(PVP.scratch.values));
		assertArrayEquals(ShowdownCard.DEFAULT_WEIGHTS, ShowdownCard.weights(java.util.Map.of("coal", 0)));
		assertEquals(100, java.util.Arrays.stream(ShowdownCard.DEFAULT_WEIGHTS).sum());
	}

	@Test
	void r1WorkedCard() {
		ShowdownCard.Evaluation e = ShowdownCard.evaluate(card(Sym.COAL, Sym.COAL, Sym.COAL, Sym.DIAMOND, Sym.CREEPER, Sym.IRON, Sym.FOOT,
			Sym.EMERALD, Sym.GOLD), V);
		assertEquals(32, e.score());
		assertTrue(e.burned(3)); // the Diamond
		assertEquals(3, e.creeperBurns()[4]);
		assertEquals(1, e.feet());
		assertEquals(5, e.best());
		assertArrayEquals(new long[] {1, 2, 6, 16, 6, 8, 16, 26, 32}, e.running());
	}

	@Test
	void r2CreeperFizzles() {
		ShowdownCard.Evaluation e = ShowdownCard.evaluate(card(Sym.STAR, Sym.CREEPER, Sym.CREEPER, Sym.COAL, Sym.COAL, Sym.COAL, Sym.COAL,
			Sym.COAL, Sym.COAL), V);
		assertTrue(e.burned(0));
		assertEquals(0, e.creeperBurns()[1]);
		assertEquals(-1, e.creeperBurns()[2]); // fizzles
		assertEquals(12, e.score());
		// a Creeper as the first cell fizzles too
		assertEquals(-1, ShowdownCard.evaluate(card(Sym.CREEPER, Sym.COAL, Sym.COAL, Sym.COAL, Sym.COAL, Sym.COAL, Sym.COAL, Sym.COAL,
			Sym.COAL), V).creeperBurns()[0]);
	}

	@Test
	void creeperTieBurnsTheEarliest() {
		int[] values = {1, 1, 3, 5, 10, 25}; // Coal and Iron both worth 1
		ShowdownCard.Evaluation e = ShowdownCard.evaluate(card(Sym.IRON, Sym.COAL, Sym.CREEPER, Sym.COAL, Sym.COAL, Sym.FOOT, Sym.FOOT,
			Sym.FOOT, Sym.IRON), values);
		assertEquals(0, e.creeperBurns()[2]);
		assertEquals((3 * 1 * 2 + 1) * 4, e.score());
	}

	@Test
	void r3ThirdFootIgnored() {
		ShowdownCard.Evaluation e = ShowdownCard.evaluate(card(Sym.FOOT, Sym.DIAMOND, Sym.FOOT, Sym.DIAMOND, Sym.DIAMOND, Sym.FOOT,
			Sym.DIAMOND, Sym.DIAMOND, Sym.DIAMOND), V);
		assertEquals(480, e.score());
		assertEquals(3, e.feet());
		assertEquals(4, e.multiplier());
	}

	@Test
	void r4Max() {
		ShowdownCard.Evaluation e = ShowdownCard.evaluate(card(Sym.STAR, Sym.STAR, Sym.STAR, Sym.FOOT, Sym.STAR, Sym.STAR, Sym.STAR,
			Sym.FOOT, Sym.STAR), V);
		assertEquals(1400, e.score());
	}

	@Test
	void r5ExactDistribution() {
		ShowdownCard.Distribution d = ShowdownCard.exact(ShowdownCard.DEFAULT_WEIGHTS, V);
		assertEquals(38.8164, d.mean(), 0.0001);
		assertEquals(3.2458e-5, d.scores().get(0L), 0.0001e-5);
		assertEquals(0.028158, d.feet()[2], 0.000001);
		assertEquals(0.019972, d.tieProbability(), 0.000001);
		assertEquals(1400L, d.scores().lastKey());
		double total = d.scores().values().stream().mapToDouble(Double::doubleValue).sum();
		assertEquals(1.0, total, 1e-12);
		double mean = d.mean();
		double var = 0;
		for (var e : d.scores().entrySet()) {
			var += (e.getKey() - mean) * (e.getKey() - mean) * e.getValue();
		}
		assertEquals(21.85, Math.sqrt(var), 0.005);
	}

	@Test
	void r6MonteCarloMatchesExact() {
		PvpRng r = rng(42);
		int n = 10_000_000;
		double sum = 0;
		for (int k = 0; k < n; k++) {
			sum += ShowdownCard.evaluate(ShowdownCard.drawCard(r, ShowdownCard.DEFAULT_WEIGHTS), V).score();
		}
		double exact = ShowdownCard.exact(ShowdownCard.DEFAULT_WEIGHTS, V).mean();
		assertEquals(exact, sum / n, exact * 0.003);
	}

	/** R7 / B2: every seat wins 1/N (split wins counted fractionally). */
	@Test
	void r7Fairness() {
		ScratchShowdownMode.Params p = MODE.defaults();
		for (int n : new int[] {2, 6}) {
			PvpRng r = rng(7 + n);
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

	@Test
	void tieBreakBestCellThenSplit() {
		ScratchShowdownMode.Params p = MODE.defaults();
		int[] a = card(Sym.DIAMOND, Sym.IRON, Sym.IRON, Sym.CREEPER, Sym.CREEPER, Sym.CREEPER, Sym.FOOT, Sym.GOLD, Sym.GOLD);
		// A: Diamond, Iron, Iron, then 3 creepers burn Diamond, Iron, Iron → 0; foot; Gold Gold → 6 × 2 = 12
		int[] b = card(Sym.EMERALD, Sym.COAL, Sym.CREEPER, Sym.COAL, Sym.FOOT, Sym.IRON, Sym.IRON, Sym.CREEPER, Sym.COAL);
		// B: Emerald, Coal, creeper burns Emerald, Coal, foot, Iron, Iron, creeper burns the first Iron, Coal → coal trio 6 + iron 2 = 8 × 2 = 16
		ShowdownCard.Evaluation ea = ShowdownCard.evaluate(a, V);
		ShowdownCard.Evaluation eb = ShowdownCard.evaluate(b, V);
		assertEquals(12, ea.score());
		assertEquals(16, eb.score());
		int[] c = card(Sym.GOLD, Sym.GOLD, Sym.FOOT, Sym.CREEPER, Sym.COAL, Sym.COAL, Sym.COAL, Sym.CREEPER, Sym.COAL);
		// C: Gold, Gold, foot, creeper burns Gold (earliest), Coal ×3, creeper burns Gold, Coal → coal 4 trio 8 × 2 = 16, best 1
		assertEquals(16, ShowdownCard.evaluate(c, V).score());
		assertEquals(1, ShowdownCard.evaluate(c, V).best());
		assertEquals(2, eb.best());
		Outcome o = MODE.score(new ScratchShowdownMode.Tape(new int[] {2, 1, 0}, new int[][] {a, b, c}, V), new long[3], p);
		assertArrayEquals(new int[] {1}, o.winners()); // 16 vs 16: best cell 2 beats 1
		assertArrayEquals(new int[] {1, 2, 0}, o.rankOrder());
		Outcome split = MODE.score(new ScratchShowdownMode.Tape(new int[] {1, 0}, new int[][] {b, b}, V), new long[2], p);
		assertArrayEquals(new int[] {1, 0}, split.winners());
	}

	@Test
	void outcomeEvents() {
		int[] a = card(Sym.COAL, Sym.COAL, Sym.COAL, Sym.DIAMOND, Sym.CREEPER, Sym.IRON, Sym.FOOT, Sym.EMERALD, Sym.FOOT);
		int[] b = card(Sym.CREEPER, Sym.COAL, Sym.COAL, Sym.COAL, Sym.COAL, Sym.COAL, Sym.COAL, Sym.COAL, Sym.COAL);
		Outcome o = MODE.score(new ScratchShowdownMode.Tape(new int[] {0, 1}, new int[][] {a, b}, V), new long[2], MODE.defaults());
		List<PvpEvent> ev = o.events();
		PvpEvent creeper = ev.stream().filter(e -> e.kind().equals("creeper")).findFirst().orElseThrow();
		assertEquals(0, creeper.seat());
		assertEquals(4, creeper.round());
		assertEquals(3L, creeper.data().get("cell"));
		assertEquals((long) Sym.DIAMOND.ordinal(), creeper.data().get("symbol"));
		assertTrue(ev.stream().anyMatch(e -> e.kind().equals("fizzle") && e.seat() == 1 && e.round() == 0));
		assertTrue(ev.stream().anyMatch(e -> e.kind().equals("foot") && e.seat() == 0 && e.data().get("mult") == 4L));
		assertTrue(ev.stream().anyMatch(e -> e.kind().equals("lucky_feet") && e.seat() == 0));
		assertFalse(ev.stream().anyMatch(e -> e.kind().equals("lucky_feet") && e.seat() == 1));
		assertEquals(2, ev.stream().filter(e -> e.kind().equals("final_cell")).count());
		// A: coal trio 6 + iron 2 + emerald 5 = 13 × 4 = 52; B: 8 coal → 16
		assertArrayEquals(new long[] {52, 16}, o.points());
	}

	@Test
	void timelineShape() {
		int[] a = card(Sym.COAL, Sym.COAL, Sym.COAL, Sym.DIAMOND, Sym.CREEPER, Sym.IRON, Sym.FOOT, Sym.EMERALD, Sym.GOLD);
		int[] b = card(Sym.IRON, Sym.IRON, Sym.IRON, Sym.IRON, Sym.IRON, Sym.IRON, Sym.IRON, Sym.IRON, Sym.STAR);
		ScratchShowdownMode.Tape t = new ScratchShowdownMode.Tape(new int[] {0, 1}, new int[][] {a, b}, V);
		List<Step> steps = MODE.timeline(t, MODE.score(t, new long[2], MODE.defaults()), MODE.defaults());
		// 8 × (wait + cell) + events at cells 5 and 7 + final wait
		assertEquals(8 * 2 + 2 + 1, steps.size());
		assertEquals("cell_wait", steps.get(steps.size() - 1).kind());
		assertTrue(steps.get(steps.size() - 1).waitForAll());
		assertEquals(40, steps.get(0).ticks());
		for (Step s : steps) {
			// the ninth cell (the Star) never appears before the Final Reveal
			if (s.kind().equals("cell")) {
				assertTrue(s.round() < 8);
			}
		}
		Step cell5 = steps.stream().filter(s -> s.kind().equals("cell") && s.round() == 4).findFirst().orElseThrow();
		JsonObject d = cell5.data();
		assertEquals(Sym.CREEPER.ordinal(), d.getAsJsonArray("symbols").get(0).getAsInt());
		assertEquals(6, d.getAsJsonArray("scores").get(0).getAsLong());
		assertEquals(10 * 2, d.getAsJsonArray("scores").get(1).getAsLong()); // 5 irons: trio → 20
		assertEquals(3, d.getAsJsonArray("burns").get(0).getAsJsonObject().get("cell").getAsInt());
		Step events = steps.stream().filter(s -> s.kind().equals("cell_events") && s.round() == 6).findFirst().orElseThrow();
		assertEquals(2, events.data().getAsJsonArray("feet").get(0).getAsJsonObject().get("mult").getAsInt());
	}

	@Test
	void codecs() {
		ScratchShowdownMode.Tape t = MODE.draw(rng(5), 6, MODE.defaults());
		String json = MODE.encodeTape(t).toString();
		assertTrue(json.length() < 200, json);
		assertEquals(t, MODE.decodeTape(com.google.gson.JsonParser.parseString(json)));
		assertEquals(MODE.defaults(), MODE.decodeParams(MODE.encodeParams(MODE.defaults())));
	}

	@Test
	void b1SameSeedSameTape() {
		assertEquals(MODE.draw(rng(11), 5, MODE.defaults()), MODE.draw(rng(11), 5, MODE.defaults()));
	}
}

package dev.nezo.burmaldaholic.games.pvp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.config.sections.ExtrasConfig;
import dev.nezo.burmaldaholic.core.config.sections.PvpConfig;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpEvent;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpRng;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelMode;
import dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattleMode;
import dev.nezo.burmaldaholic.games.extras.pvp.scratch.ScratchShowdownMode;
import dev.nezo.burmaldaholic.games.extras.pvp.wheel.WheelPartyMode;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.pvp.SlotShowdownMode;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden-vector parity of the PvP modes: the vectors in {@code golden-vectors.json} are frozen reference outputs
 * of the pure mode logic (review wave 2, drawn with mulberry32). Java draws every tape from the SAME fair stream
 * (the adapter below reproduces that float rng: {@code nextInt(b) = ⌊next()·b⌋},
 * {@code nextBoolean = next() < ½}) and must produce the same tape and the same outcome: points, winners, ranking
 * (creeper ties → the earliest revealed, Plinko best ball with the Underdog doubling, split order), seat order
 * and the events. This pins the draw order (seat order first, then the mode's cells / paths / spins).
 * Vectors are frozen data, not code: never regenerate them from the code under test.
 */
class GoldenVectorParityTest {
	private static final JsonObject V = load();

	private static JsonObject load() {
		try (InputStream in = GoldenVectorParityTest.class.getResourceAsStream("/dev/nezo/burmaldaholic/pvp/golden-vectors.json")) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/** The reference {@code seededRng} (mulberry32) with its float semantics. */
	static PvpRng mulberry(long seed) {
		return new PvpRng() {
			private int s = (int) seed;

			double next() {
				s += 0x6d2b79f5;
				int r = s;
				r = (r ^ (r >>> 15)) * (r | 1);
				r ^= r + (r ^ (r >>> 7)) * (r | 61);
				return Integer.toUnsignedLong(r ^ (r >>> 14)) / 4294967296.0;
			}

			@Override
			public int nextInt(int bound) {
				return (int) Math.floor(next() * bound);
			}

			@Override
			public long nextLong(long bound) {
				return (long) Math.floor(next() * bound);
			}

			@Override
			public boolean nextBoolean() {
				return next() < 0.5;
			}
		};
	}

	private static int[] ints(JsonElement e) {
		JsonArray a = e.getAsJsonArray();
		int[] out = new int[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsInt();
		}
		return out;
	}

	private static long[] longs(JsonElement e) {
		JsonArray a = e.getAsJsonArray();
		long[] out = new long[a.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = a.get(i).getAsLong();
		}
		return out;
	}

	/** Java-only presentation events (the Java screens animate the final ball / cell from them). */
	private static final java.util.Set<String> JAVA_PRESENTATION = java.util.Set.of("final_ball", "final_cell");

	private static List<String> events(Outcome o) {
		List<String> out = new ArrayList<>();
		for (PvpEvent e : o.events()) {
			if (!JAVA_PRESENTATION.contains(e.kind())) {
				out.add(e.kind() + ":" + e.seat() + ":" + e.round());
			}
		}
		return out;
	}

	private static List<String> strings(JsonElement e) {
		List<String> out = new ArrayList<>();
		e.getAsJsonArray().forEach(x -> out.add(x.getAsString()));
		return out;
	}

	private static void sameOutcome(String what, JsonObject expected, Outcome o, boolean withEvents) {
		assertArrayEquals(longs(expected.get("points")), o.points(), what + " points");
		assertArrayEquals(ints(expected.get("winners")), o.winners(), what + " winners");
		assertArrayEquals(ints(expected.get("rankOrder")), o.rankOrder(), what + " ranking");
		assertArrayEquals(ints(expected.get("seatOrder")), o.seatOrder(), what + " seat order");
		if (withEvents) {
			assertEquals(strings(expected.get("events")), events(o), what + " events");
		}
	}

	private static long[] stakes(int n, long each) {
		long[] s = new long[n];
		java.util.Arrays.fill(s, each);
		return s;
	}

	@Test
	void plinkoSameSeedSameTapeSameOutcome() {
		PvpConfig pvp = new PvpConfig();
		PlinkoBattleMode mode = new PlinkoBattleMode(() -> pvp, ExtrasConfig.Plinko::new);
		int n = 0;
		for (JsonElement e : V.getAsJsonArray("plinko")) {
			JsonObject v = e.getAsJsonObject();
			String what = "plinko seed " + v.get("seed") + " " + v.get("risk").getAsString();
			int players = v.get("players").getAsInt();
			PlinkoBattleMode.Params params = mode.decodeParams(mode.encodeParams(new PlinkoBattleMode.Params(v.get("risk").getAsString(),
				v.get("balls").getAsInt())));
			PlinkoBattleMode.Tape tape = mode.draw(mulberry(v.get("seed").getAsLong()), players, params);
			JsonObject t = v.getAsJsonObject("tape");
			assertArrayEquals(ints(t.get("seatOrder")), tape.seatOrder(), what + " seat order");
			JsonArray paths = t.getAsJsonArray("paths");
			for (int i = 0; i < players; i++) {
				assertArrayEquals(ints(paths.get(i)), tape.paths()[i], what + " paths of " + i);
			}
			sameOutcome(what, v.getAsJsonObject("outcome"), mode.score(tape, stakes(players, 10), params), true);
			n++;
		}
		assertTrue(n >= 20);
	}

	@Test
	void scratchSameSeedSameTapeSameOutcome() {
		PvpConfig pvp = new PvpConfig();
		ScratchShowdownMode mode = new ScratchShowdownMode(() -> pvp);
		for (JsonElement e : V.getAsJsonArray("scratch")) {
			JsonObject v = e.getAsJsonObject();
			String what = "scratch seed " + v.get("seed");
			int players = v.get("players").getAsInt();
			ScratchShowdownMode.Params params = mode.decodeParams(mode.encodeParams(mode.defaults()));
			ScratchShowdownMode.Tape tape = mode.draw(mulberry(v.get("seed").getAsLong()), players, params);
			JsonObject t = v.getAsJsonObject("tape");
			assertArrayEquals(ints(t.get("seatOrder")), tape.seatOrder(), what + " seat order");
			JsonArray cells = t.getAsJsonArray("cells");
			for (int i = 0; i < players; i++) {
				assertArrayEquals(ints(cells.get(i)), tape.cells()[i], what + " card of " + i);
			}
			sameOutcome(what, v.getAsJsonObject("outcome"), mode.score(tape, stakes(players, 10), params), true);
		}
	}

	@Test
	void slotShowdownSameSeedSameTapeSameOutcome() {
		SlotShowdownMode mode = SlotShowdownMode.withDefaults();
		for (JsonElement e : V.getAsJsonArray("slots")) {
			JsonObject v = e.getAsJsonObject();
			String what = "slots seed " + v.get("seed") + " " + v.get("tier").getAsString();
			int players = v.get("players").getAsInt();
			SlotShowdownMode.Params params = mode.defaults(Tier.byId(v.get("tier").getAsString()));
			params = new SlotShowdownMode.Params(params.tier(), v.get("spins").getAsInt(), params.rules());
			SlotShowdownMode.Tape tape = mode.draw(mulberry(v.get("seed").getAsLong()), players, params);
			JsonObject t = v.getAsJsonObject("tape");
			assertArrayEquals(ints(t.get("seatOrder")), tape.seatOrder(), what + " seat order");
			assertArrayEquals(ints(t.get("hot")), tape.hot(), what + " HOT symbols");
			JsonArray grids = t.getAsJsonArray("grids");
			for (int p = 0; p < players; p++) {
				JsonArray mine = grids.get(p).getAsJsonArray();
				for (int r = 0; r < mine.size(); r++) {
					assertArrayEquals(ints(mine.get(r)), tape.grids()[p][r], what + " grid " + p + "/" + r);
				}
			}
			sameOutcome(what, v.getAsJsonObject("outcome"), mode.score(tape, stakes(players, 10), params), true);
		}
	}

	@Test
	void coinSameSeedSameTapeSameWinner() {
		CoinDuelMode mode = new CoinDuelMode();
		for (JsonElement e : V.getAsJsonArray("coin")) {
			JsonObject v = e.getAsJsonObject();
			String what = "coin seed " + v.get("seed");
			CoinDuelMode.Params params = new CoinDuelMode.Params(100, v.get("challengerHeads").getAsBoolean());
			CoinDuelMode.Tape tape = mode.draw(mulberry(v.get("seed").getAsLong()), 2, params);
			JsonObject t = v.getAsJsonObject("tape");
			assertArrayEquals(ints(t.get("seatOrder")), tape.seatOrder(), what + " seat order");
			assertEquals(t.get("heads").getAsBoolean(), tape.heads(), what + " side");
			Outcome o = mode.score(tape, new long[] {100, 100}, params);
			JsonObject x = v.getAsJsonObject("outcome");
			assertArrayEquals(ints(x.get("winners")), o.winners(), what + " winner");
			assertArrayEquals(ints(x.get("seatOrder")), o.seatOrder(), what + " seat order");
		}
	}

	@Test
	void wheelSameSeedSameSpinSameWinner() {
		for (JsonElement e : V.getAsJsonArray("wheel")) {
			JsonObject v = e.getAsJsonObject();
			String what = "wheel seed " + v.get("seed");
			long[] st = longs(v.get("stakes"));
			WheelPartyMode.Tape tape = new WheelPartyMode().draw(mulberry(v.get("seed").getAsLong()), st.length, new WheelPartyMode.Params(1000));
			JsonObject t = v.getAsJsonObject("tape");
			assertArrayEquals(ints(t.get("seatOrder")), tape.seatOrder(), what + " seat order");
			// the reference r ∈ [0,1) is a 32-bit fraction; Java draws the same point on a 2⁵³ grid
			assertEquals((long) (t.get("r").getAsDouble() * (1L << 53)), tape.r(), what + " spin point");
			sameOutcome(what, v.getAsJsonObject("outcome"), WheelPartyMode.score(tape, st, 1000), true);
		}
	}

	@Test
	void scratchCreeperTargetsAndTies() {
		// equal values (gold = emerald = 5): the higher symbol, then the earliest cell (PVP.md §8.1)
		for (JsonElement e : V.getAsJsonArray("scratchCards")) {
			JsonObject v = e.getAsJsonObject();
			int[] cells = ints(v.get("cells"));
			var ev = dev.nezo.burmaldaholic.games.extras.pvp.scratch.ShowdownCard.evaluate(cells, ints(v.get("values")));
			String what = "card " + java.util.Arrays.toString(cells);
			assertEquals(v.get("score").getAsLong(), ev.score(), what + " score");
			assertEquals(v.get("best").getAsLong(), ev.best(), what + " best cell");
			List<String> burns = new ArrayList<>();
			for (int c = 0; c < cells.length; c++) {
				if (cells[c] == dev.nezo.burmaldaholic.games.extras.pvp.scratch.ShowdownCard.Sym.CREEPER.ordinal()) {
					int t = ev.creeperBurns()[c];
					burns.add((t < 0 ? "fizzle@" : "creeper@") + c + "->" + t);
				}
			}
			List<String> expected = strings(v.get("events")).stream().filter(x -> x.startsWith("creeper") || x.startsWith("fizzle")).toList();
			assertEquals(expected, burns, what + " creeper targets");
		}
	}

	@Test
	void plinkoBestBallCountsTheUnderdogDoubling() {
		for (JsonElement e : V.getAsJsonArray("plinkoBattles")) {
			JsonObject v = e.getAsJsonObject();
			JsonArray p = v.getAsJsonArray("paths");
			int[][] paths = new int[p.size()][];
			for (int i = 0; i < paths.length; i++) {
				paths[i] = ints(p.get(i));
			}
			var s = dev.nezo.burmaldaholic.games.extras.pvp.plinko.PlinkoBattle.score(paths, ints(v.get("seatOrder")), longs(v.get("row")),
				v.get("boost").getAsBoolean());
			JsonObject r = v.getAsJsonObject("result");
			String what = "battle " + p;
			assertArrayEquals(longs(r.get("totals")), s.totals(), what + " totals");
			assertArrayEquals(longs(r.get("best")), s.best(), what + " best ball (boost included)");
			assertArrayEquals(ints(r.get("winners")), s.winners(), what + " winners");
			assertArrayEquals(ints(r.get("rankOrder")), s.rankOrder(), what + " ranking");
		}
	}
}

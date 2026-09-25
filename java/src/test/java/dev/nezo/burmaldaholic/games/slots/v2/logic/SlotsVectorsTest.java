package dev.nezo.burmaldaholic.games.slots.v2.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Golden vectors of the slots v2 engine and timeline (docs/architecture/animation.md §3.4, §7.3):
 * {@code src/test/resources/fx/vectors/slots_engine.json} (windows and way wins, tumble chains incl. a 7- and an
 * 8-tumble chain, seeded draws → tape strings, codec round trips) and {@code slots_timeline.json} (20 tapes per machine →
 * canonical beat lists). The inputs are frozen (first drawn by the former TypeScript engine); the engine outputs are
 * recomputed here from the inputs, the timelines follow the Java {@code SlotBeats} layout (regenerate with
 * {@code FX_DUMP_VECTORS=1}).
 */
class SlotsVectorsTest {
	private static JsonObject load(String name) throws IOException {
		Path f = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/test/resources/fx/vectors", name);
		return JsonParser.parseString(Files.readString(f)).getAsJsonObject();
	}

	private static int[] ints(JsonElement e) {
		JsonArray a = e.getAsJsonArray();
		int[] out = new int[a.size()];
		for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsInt();
		return out;
	}

	private static long[] longs(JsonElement e) {
		JsonArray a = e.getAsJsonArray();
		long[] out = new long[a.size()];
		for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsLong();
		return out;
	}

	private static MachineDef def(String id) {
		return SlotDefaults.def(Machine.byId(id));
	}

	@Test
	void windowsAndWayWins() throws IOException {
		JsonArray ws = load("slots_engine.json").getAsJsonArray("windows");
		assertTrue(ws.size() >= 30);
		for (JsonElement el : ws) {
			JsonObject w = el.getAsJsonObject();
			MachineDef d = def(w.get("machine").getAsString());
			int[] stops = ints(w.get("stops"));
			Window win = Window.fromStops(d, stops);
			assertArrayEquals(ints(w.get("window")), win.cells());
			Ways.Result r = Ways.evaluate(d, win, 0);
			JsonArray wins = w.getAsJsonArray("wins");
			assertEquals(wins.size(), r.wins().size(), w.toString());
			for (int i = 0; i < wins.size(); i++) {
				Ways.WayWin x = r.wins().get(i);
				assertArrayEquals(longs(wins.get(i)), new long[] {x.symbol(), x.k(), x.ways(), x.payFifths(), x.cellMask()}, w.toString());
			}
			assertEquals(w.get("pay").getAsLong(), r.payFifths());
			assertEquals(w.get("scatters").getAsInt(), r.scatters());
			assertEquals(w.get("bonusCount").getAsInt(), r.bonusCount());
			assertEquals(w.get("coins").getAsInt(), r.coins());
		}
	}

	@Test
	void tumbleChains() throws IOException {
		JsonArray ts = load("slots_engine.json").getAsJsonArray("tumbles");
		MachineDef d = def("nether");
		boolean seven = false;
		boolean eight = false;
		for (JsonElement el : ts) {
			JsonObject t = el.getAsJsonObject();
			Tumble.Chain c = Tumble.run(d, ints(t.get("stops")), d.ladder());
			JsonArray windows = t.getAsJsonArray("windows");
			assertEquals(windows.size(), c.steps().size(), t.get("stops").toString());
			for (int i = 0; i < windows.size(); i++) {
				Tumble.Step s = c.steps().get(i);
				assertArrayEquals(ints(windows.get(i)), s.window().cells());
				assertEquals(t.getAsJsonArray("pays").get(i).getAsLong(), s.payFifths());
				assertEquals(t.getAsJsonArray("multipliers").get(i).getAsInt(), s.multiplier());
				assertArrayEquals(ints(t.getAsJsonArray("tops").get(i)), s.tops());
			}
			assertEquals(t.get("total").getAsLong(), c.payFifths());
			assertEquals(windows.size() - 1, c.tumbles());
			seven |= c.tumbles() == 7;
			eight |= c.tumbles() == 8;
		}
		assertTrue(seven && eight, "a 7- and an 8-tumble chain (SLOTS.md §15.3)");
	}

	@Test
	void seededDrawsAndCodec() throws IOException {
		JsonArray ds = load("slots_engine.json").getAsJsonArray("draws");
		int featured = 0;
		for (JsonElement el : ds) {
			JsonObject v = el.getAsJsonObject();
			MachineDef d = def(v.get("machine").getAsString());
			long[] pools = longs(v.get("pools"));
			boolean owned = v.get("owned").getAsBoolean();
			SpinTape t = SlotDraw.draw(new SlotDraw.Request(d, v.get("bet").getAsLong(), v.get("buy").getAsBoolean(), owned,
				owned || pools.length == 0 ? null : pools), SlotRng.seeded(v.get("seed").getAsInt()));
			String tape = v.get("tape").getAsString();
			assertEquals(tape, TapeCodec.encode(t), v.toString());
			assertEquals(tape, TapeCodec.encode(TapeCodec.decode(tape)));
			assertEquals(t, TapeCodec.decode(tape));
			if (t.featureTriggered()) featured++;
		}
		assertTrue(featured >= 36);
	}

	/**
	 * 20 tapes per machine → canonical timeline JSON (the {@code SlotBeats} layout). {@code FX_DUMP_VECTORS=1} rewrites the
	 * expected timelines from the Java builder (inputs unchanged), like the other fx vectors.
	 */
	@Test
	void timelines() throws IOException {
		Path file = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/test/resources/fx/vectors", "slots_timeline.json");
		String text = Files.readString(file);
		JsonArray vs = JsonParser.parseString(text).getAsJsonObject().getAsJsonArray("vectors");
		boolean dump = "1".equals(System.getenv("FX_DUMP_VECTORS"));
		com.google.gson.Gson gson = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
		int[] perMachine = new int[3];
		for (JsonElement el : vs) {
			JsonObject v = el.getAsJsonObject();
			SpinTape t = TapeCodec.decode(v.get("tape").getAsString());
			perMachine[t.machine().ordinal()]++;
			boolean rm = v.get("reduceMotion").getAsBoolean();
			String actual = SlotTimeline.build(t, SlotDefaults.def(t.machine()), TimingProfile.SHARED.withSpeed(v.get("sharedPct").getAsInt()),
				new TimingProfile(v.get("localPct").getAsInt(), rm, !rm), v.get("seed").getAsInt()).toCanonicalJson();
			String expected = v.get("timeline").getAsString();
			if (dump) {
				text = text.replace(gson.toJson(expected), gson.toJson(actual));
				continue;
			}
			assertEquals(expected, actual, v.get("tape").getAsString());
			if (!t.jackpots().isEmpty() && t.totalFifths() > 0) {
				assertTrue(actual.indexOf(SlotTimeline.ROLLUP) < actual.indexOf(SlotTimeline.JACKPOT), "roll-up before the jackpots " + v.get("tape"));
			}
		}
		if (dump) Files.writeString(file, text);
		for (int n : perMachine) assertTrue(n >= 20, "20+ tapes per machine");
	}
}

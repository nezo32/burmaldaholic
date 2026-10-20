package dev.nezo.burmaldaholic.core.anim.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden vectors of the dice solver (PHASE2B §4 DCa): {@code src/test/resources/fx/vectors/dice_roll.json} holds 16
 * cases (tray, seed, faces, target) with the expected rest times, events and 12 samples per die. The solver uses only
 * IEEE arithmetic, {@code Math.sqrt} and {@code StrictMath}, so the server, every client and a future port reproduce
 * it. Regenerate only after an intended change: {@code FX_DUMP_VECTORS=1 ./gradlew test --tests '*DiceRollVectorsTest'}.
 */
class DiceRollVectorsTest {
	private static final double[] T = {-50, 0, 40, 90, 160, 250, 380, 520, 700, 900, 1150, 4000};

	@Test
	void vectorsMatch() throws IOException {
		Path file = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/test/resources/fx/vectors/dice_roll.json");
		JsonObject actual = compute();
		if (System.getenv("FX_DUMP_VECTORS") != null || !Files.isRegularFile(file)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
			Files.createDirectories(file.getParent());
			Files.writeString(file, gson.toJson(actual) + "\n", StandardCharsets.UTF_8);
			return;
		}
		JsonObject expected = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertClose("$", expected, actual);
	}

	static JsonObject compute() {
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		JsonArray cases = new JsonArray();
		List<DiceFixtures.Case> trays = DiceFixtures.all();
		int k = 0;
		for (int i = 0; i < 16; i++) {
			DiceFixtures.Case c = trays.get(i % trays.size());
			int seed = 0x1234567 * (i + 1) ^ 0x5A5A5A5A;
			int[] faces = {1 + (i * 5) % 6, 1 + (i * 7 + 3) % 6};
			int target = i % 5 == 4 ? DiceSolver.NATURAL : c.targetMs();
			DiceRoll r = DiceSolver.solve(c.tray(), c.spec(), seed, faces, target);
			JsonObject j = new JsonObject();
			j.addProperty("tray", c.name());
			j.addProperty("seed", seed);
			JsonArray f = new JsonArray();
			for (int x : faces) f.add(x);
			j.add("faces", f);
			j.addProperty("target", target);
			j.addProperty("attempt", r.attempt());
			JsonArray rest = new JsonArray();
			for (int d = 0; d < r.dice(); d++) rest.add(r.restTime(d));
			j.add("rest", rest);
			JsonArray ev = new JsonArray();
			for (MotionEvent e : r.events()) {
				JsonArray a = new JsonArray();
				a.add(e.tMs());
				a.add(DiceEvent.name(e.kind()));
				a.add(e.obj());
				a.add(e.aux());
				a.add(e.x());
				a.add(e.y());
				ev.add(a);
			}
			j.add("events", ev);
			JsonArray samples = new JsonArray();
			DieSample s = new DieSample();
			for (int d = 0; d < r.dice(); d++) {
				for (double t : T) {
					r.sample(d, t, s);
					JsonArray a = new JsonArray();
					a.add(d);
					a.add(t);
					a.add(DieSample.phaseName(s.phase));
					a.add(s.x);
					a.add(s.y);
					a.add(s.zLow);
					a.add(s.zc);
					for (double q : s.q) a.add(q);
					samples.add(a);
				}
			}
			j.add("samples", samples);
			cases.add(j);
			k++;
		}
		o.add("cases", cases);
		o.addProperty("count", k);
		return o;
	}

	private static void assertClose(String path, JsonElement e, JsonElement a) {
		if (e.isJsonObject()) {
			assertTrue(a.isJsonObject(), path);
			assertEquals(e.getAsJsonObject().keySet(), a.getAsJsonObject().keySet(), path);
			for (String k : e.getAsJsonObject().keySet()) assertClose(path + "." + k, e.getAsJsonObject().get(k), a.getAsJsonObject().get(k));
		} else if (e.isJsonArray()) {
			assertEquals(e.getAsJsonArray().size(), a.getAsJsonArray().size(), path);
			for (int i = 0; i < e.getAsJsonArray().size(); i++) assertClose(path + "[" + i + "]", e.getAsJsonArray().get(i), a.getAsJsonArray().get(i));
		} else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			assertEquals(e.getAsDouble(), a.getAsDouble(), 1e-9, path);
		} else {
			assertEquals(e, a, path);
		}
	}
}

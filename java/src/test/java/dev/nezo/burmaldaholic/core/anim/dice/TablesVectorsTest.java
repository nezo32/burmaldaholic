package dev.nezo.burmaldaholic.core.anim.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsFelt;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteBallPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Golden vectors of the table paths (docs/architecture/animation.md §3.4, A3): {@code src/test/resources/fx/vectors/
 * tables.json} holds inputs and the expected ball / dice samples; this test recomputes them. Regenerate only after an
 * intended change: {@code FX_DUMP_VECTORS=1 ./gradlew test --tests '*TablesVectorsTest'}.
 */
class TablesVectorsTest {
	private static final double[] U = {0, 0.1, 0.3, 0.59, 0.62, 0.64, 0.7, 0.8, 0.87, 0.9, 1};

	@Test
	void vectorsMatch() throws IOException {
		Path file = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/test/resources/fx/vectors/tables.json");
		boolean dump = System.getenv("FX_DUMP_VECTORS") != null || !Files.isRegularFile(file);
		JsonObject actual = compute();
		if (dump) {
			Files.createDirectories(file.getParent());
			Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(actual) + "\n", StandardCharsets.UTF_8);
			return;
		}
		JsonObject expected = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		compare("$", expected, actual);
	}

	private static JsonObject compute() {
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		JsonArray ball = new JsonArray();
		for (int result = 0; result < 37; result += 4) {
			for (int seed : new int[] {0, 1, -7, 123456}) {
				for (int spin : new int[] {2000, 5000}) {
					RouletteBallPath p = RouletteBallPath.of(result, seed, spin);
					JsonObject c = new JsonObject();
					c.addProperty("result", result);
					c.addProperty("seed", seed);
					c.addProperty("spinMs", spin);
					c.addProperty("h1", p.h1);
					c.addProperty("h2", p.h2);
					c.addProperty("laps", p.laps);
					JsonArray s = new JsonArray();
					for (double u : U) {
						JsonArray row = new JsonArray();
						row.add(round(p.head(u * spin)));
						row.add(round(p.rel(u * spin)));
						row.add(round(p.radius(u * spin)));
						s.add(row);
					}
					c.add("samples", s);
					ball.add(c);
				}
			}
		}
		o.add("rouletteBall", ball);
		JsonArray dice = new JsonArray();
		DiceThrowPath.Sample smp = new DiceThrowPath.Sample();
		CrapsFelt f = CrapsFelt.BIG;
		double[] r = f.restRegion();
		double[] st = f.throwStart();
		DiceThrowPath.Params[] params = {DiceThrowPath.Params.craps(st[0], st[1], f.wall(), f.dcW() + 2, f.w(), r[0], r[1], r[2], r[3]),
			DiceThrowPath.Params.duel(28, 84, 78, 84, 84, 88, 36)};
		for (int mode = 0; mode < 2; mode++) {
			for (int pair = 0; pair < 36; pair += 5) {
				int d1 = pair / 6 + 1;
				int d2 = pair % 6 + 1;
				DiceThrowPath path = DiceThrowPath.of(pair * 31 + mode, d1, d2, params[mode]);
				JsonObject c = new JsonObject();
				c.addProperty("mode", mode == 0 ? "craps" : "duel");
				c.addProperty("d1", d1);
				c.addProperty("d2", d2);
				JsonArray s = new JsonArray();
				for (int k = 0; k <= 10; k++) {
					double t = path.durationMs() * k / 10.0;
					for (int die = 0; die < 2; die++) {
						path.sample(die, t, smp);
						JsonArray row = new JsonArray();
						row.add(round(smp.x));
						row.add(round(smp.y));
						row.add(round(smp.z));
						row.add(smp.frame);
						s.add(row);
					}
				}
				c.add("samples", s);
				dice.add(c);
			}
		}
		o.add("diceThrow", dice);
		return o;
	}

	private static double round(double v) {
		return Math.round(v * 1e6) / 1e6;
	}

	private static void compare(String path, JsonElement e, JsonElement a) {
		if (e.isJsonObject()) {
			for (String k : e.getAsJsonObject().keySet()) {
				compare(path + "." + k, e.getAsJsonObject().get(k), a.getAsJsonObject().get(k));
			}
		} else if (e.isJsonArray()) {
			assertEquals(e.getAsJsonArray().size(), a.getAsJsonArray().size(), path);
			for (int i = 0; i < e.getAsJsonArray().size(); i++) {
				compare(path + "[" + i + "]", e.getAsJsonArray().get(i), a.getAsJsonArray().get(i));
			}
		} else if (e.getAsJsonPrimitive().isNumber()) {
			assertEquals(e.getAsDouble(), a.getAsDouble(), 1e-6, path);
		} else {
			assertEquals(e, a, path);
		}
	}
}

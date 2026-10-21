package dev.nezo.burmaldaholic.core.anim.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden vectors of the motion kit: {@code src/test/resources/fx/vectors/core_motion.json} (next to
 * {@code core_anim.json}) holds the outputs of fixed inputs, so the server, every client and a Bedrock port stay in
 * sync across refactors. Regenerate only after an intended change:
 * {@code FX_DUMP_VECTORS=1 ./gradlew test --tests '*CoreMotionVectorsTest'}. A missing file FAILS the test (it is
 * never silently regenerated).
 */
class CoreMotionVectorsTest {
	private static final double TOL = 1e-9;

	@Test
	void vectorsMatch() throws IOException {
		Path file = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/test/resources/fx/vectors/core_motion.json");
		JsonObject actual = compute();
		if (System.getenv("FX_DUMP_VECTORS") == null && !Files.isRegularFile(file)) {
			fail("golden vectors missing: " + file + " (regenerate deliberately with FX_DUMP_VECTORS=1)");
		}
		if (System.getenv("FX_DUMP_VECTORS") != null) {
			Files.createDirectories(file.getParent());
			Files.writeString(file, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(actual) + "\n", StandardCharsets.UTF_8);
			return;
		}
		JsonObject expected = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertClose("$", expected, actual);
	}

	static JsonObject compute() {
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		// MotionRng
		JsonArray rng = new JsonArray();
		for (Object[] c : new Object[][] {{0, "PEG", 0}, {123456789, "DSIM", 3}, {-7, "RATTLE", 11}}) {
			MotionRng r = MotionRng.of((Integer) c[0], (String) c[1], (Integer) c[2]);
			JsonObject j = new JsonObject();
			j.addProperty("seed", (Integer) c[0]);
			j.addProperty("stream", (String) c[1]);
			j.addProperty("idx", (Integer) c[2]);
			j.addProperty("mixed", r.seed());
			JsonArray u = new JsonArray();
			for (int i = 0; i < 6; i++) u.add(r.nextU32());
			j.add("u32", u);
			rows(o, rng, j);
		}
		o.add("motionRng", rng);
		// Track
		Track tr = TrackTest.everyKind();
		JsonArray track = new JsonArray();
		double[] s = new double[3];
		double[] v = new double[3];
		for (double t = 0; t <= tr.end() + 50; t += 37.5) {
			tr.sample(t, s);
			tr.velocity(t, v);
			JsonObject j = new JsonObject();
			j.addProperty("t", t);
			j.add("p", arr(s));
			j.add("v", arr(v));
			track.add(j);
		}
		o.add("track", track);
		// Ballistic
		JsonObject ball = new JsonObject();
		ball.addProperty("fallTime_-60_12_900", Ballistic.fallTime(-60, 12, 900));
		ball.addProperty("fallTime_0_7.5_900", Ballistic.fallTime(0, 7.5, 900));
		ball.addProperty("flightTime_0.6_1.5_70", Ballistic.flightTime(0.6, 1.5, 70));
		ball.addProperty("v0For_20_0.2_900", Ballistic.v0For(20, 0.2, 900));
		o.add("ballistic", ball);
		// DecaySolver
		JsonArray decay = new JsonArray();
		for (double[] c : new double[][] {{756, 306, 2.55, 900}, {600, 306, 4.5, 1900}, {900, 250, 6, 3000}, {500, 300, 1, 2000}}) {
			DecaySolver.Solution sol = DecaySolver.solve(c[0], c[1], c[2], c[3], 1, 0.2, 6);
			JsonObject j = new JsonObject();
			j.add("in", arr(c));
			j.addProperty("k", sol.k());
			j.addProperty("ok", sol.ok());
			j.addProperty("theta", sol.theta());
			j.addProperty("residual", sol.residual());
			j.add("angleAt", arr(new double[] {sol.angle(c[2] * 0.25), sol.angle(c[2] * 0.5), sol.angle(c[2] * 0.75)}));
			decay.add(j);
		}
		o.add("decaySolver", decay);
		// BounceChain
		JsonArray chains = new JsonArray();
		for (int seed = 0; seed < 6; seed++) {
			BounceChain c = BounceChain.plan(3.4, 45, 14, BounceChain.Lattice.pockets(), BounceChain.Params.roulette(), MotionRng.of(seed, "RATTLE", 0));
			chains.add(chain("roulette", seed, c));
		}
		chains.add(chain("plinkoBin", 7,
			BounceChain.plan(131.2, 30, 136, BounceChain.Lattice.NONE, BounceChain.Params.plinkoBin(), MotionRng.of(7, "BIN", 0))));
		o.add("bounceChain", chains);
		// ArtScale + PixelSnap
		JsonArray art = new JsonArray();
		for (int[] c : new int[][] {{272, 204, 299, 186, 9}, {272, 204, 318, 190, 3}, {360, 140, 936, 394, 4}, {360, 140, 439, 134, 3}}) {
			ArtScale.Result r = ArtScale.pick(c[0], c[1], c[2], c[3], c[4]);
			JsonObject j = new JsonObject();
			j.add("in", arr(new double[] {c[0], c[1], c[2], c[3], c[4]}));
			j.addProperty("n", r.n());
			j.addProperty("integral", r.integral());
			j.addProperty("scale", r.scale());
			j.addProperty("ox", r.originX(10, c[2]));
			art.add(j);
		}
		ArtScale.Result wheel = ArtScale.pickNative(new int[] {104, 144, 208}, 442, 2);
		JsonObject w = new JsonObject();
		w.addProperty("size", wheel.artW());
		w.addProperty("mult", wheel.scale());
		art.add(w);
		o.add("artScale", art);
		JsonArray snap = new JsonArray();
		for (int k = 1; k <= 12; k++) snap.add(PixelSnap.snap(123.456789, k));
		o.add("pixelSnap", snap);
		// HeightShadow + Oscillation
		JsonArray hs = new JsonArray();
		HeightShadow.Out out = new HeightShadow.Out();
		for (HeightShadow.Style st : new HeightShadow.Style[] {HeightShadow.ROULETTE, HeightShadow.DICE, HeightShadow.CRAPS}) {
			for (double z : new double[] {0, 1.5, 4}) {
				HeightShadow.apply(st, z, 0.5, out);
				hs.add(arr(new double[] {out.dx, out.dy, out.shadowDx, out.shadowDy, out.shadowAlpha, out.shadowScale, out.shadowSize}));
			}
		}
		o.add("heightShadow", hs);
		o.add("oscillation", arr(new double[] {Oscillation.dampedSin(2, 120, Oscillation.periodOfHz(22), 17), Oscillation.dampedCos(1, 40, 90, 33),
			Oscillation.shake(2, 450, Oscillation.periodOfHz(22), 123)}));
		return o;
	}

	private static void rows(JsonObject o, JsonArray a, JsonObject j) {
		a.add(j);
	}

	private static JsonObject chain(String what, int seed, BounceChain c) {
		JsonObject j = new JsonObject();
		j.addProperty("params", what);
		j.addProperty("seed", seed);
		j.addProperty("hops", c.hops());
		j.addProperty("kick", c.kickIndex());
		j.addProperty("g", c.g());
		JsonArray land = new JsonArray();
		for (int i = 0; i < c.hops(); i++) land.add(arr(new double[] {c.landMs(i), c.landAt(i), c.takeoffMs(i), c.takeoffAt(i), c.apex(i), c.landClass(i)}));
		j.add("hops_", land);
		return j;
	}

	private static JsonArray arr(double[] d) {
		JsonArray a = new JsonArray();
		for (double x : d) a.add(x);
		return a;
	}

	private static void assertClose(String path, JsonElement e, JsonElement a) {
		if (e.isJsonObject()) {
			assertTrue(a.isJsonObject(), path);
			for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
				JsonElement av = a.getAsJsonObject().get(en.getKey());
				if (av == null) fail(path + "." + en.getKey() + " missing");
				assertClose(path + "." + en.getKey(), en.getValue(), av);
			}
			assertEquals(e.getAsJsonObject().size(), a.getAsJsonObject().size(), path + " keys");
		} else if (e.isJsonArray()) {
			JsonArray ea = e.getAsJsonArray();
			JsonArray aa = a.getAsJsonArray();
			assertEquals(ea.size(), aa.size(), path + " size");
			for (int i = 0; i < ea.size(); i++) assertClose(path + "[" + i + "]", ea.get(i), aa.get(i));
		} else if (e.getAsJsonPrimitive().isNumber()) {
			double x = e.getAsDouble();
			double y = a.getAsDouble();
			assertEquals(x, y, TOL * Math.max(1, Math.abs(x)), path);
		} else {
			assertEquals(e, a, path);
		}
	}
}

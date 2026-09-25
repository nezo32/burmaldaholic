package dev.nezo.burmaldaholic.core.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.sound.SoundCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden vectors of the presentation core (docs/architecture/animation.md §3.4).
 *
 * <p>The file {@code src/test/resources/fx/vectors/core_anim.json} holds INPUTS and expected OUTPUTS. This test
 * recomputes every output from the inputs with the Java implementation. Regenerate only after an intended
 * change: {@code FX_DUMP_VECTORS=1 ./gradlew test --tests '*CoreAnimVectorsTest'}.
 */
class CoreAnimVectorsTest {
	private static final double[] EASE_T = {0, 0.05, 0.1, 0.25, 0.3333, 0.5, 0.6, 0.75, 0.9, 0.99, 1};

	@Test
	void vectorsMatch() throws IOException {
		Path file = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/test/resources/fx/vectors/core_anim.json");
		JsonObject expected = Files.isRegularFile(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : null;
		JsonObject actual = compute(expected == null || System.getenv("FX_DUMP_VECTORS") != null ? defaultInputs() : expected);
		if (System.getenv("FX_DUMP_VECTORS") != null || expected == null) {
			Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
			Files.createDirectories(file.getParent());
			Files.writeString(file, gson.toJson(actual) + "\n", StandardCharsets.UTF_8);
			return;
		}
		assertClose("$", expected, actual);
	}

	/** Inputs (outputs are filled by {@link #compute}). */
	private static JsonObject defaultInputs() {
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		JsonArray t = new JsonArray();
		for (double d : EASE_T) t.add(d);
		o.add("easeT", t);
		JsonArray mix = new JsonArray();
		for (int[] parts : new int[][] {{}, {0}, {1}, {1, 2, 3}, {-1, 42}, {Integer.MIN_VALUE, Integer.MAX_VALUE, 7}}) {
			JsonObject c = new JsonObject();
			JsonArray p = new JsonArray();
			for (int x : parts) p.add(x);
			c.add("parts", p);
			mix.add(c);
		}
		o.add("seedMix", mix);
		JsonArray hash = new JsonArray();
		for (String s : new String[] {"", "slots.nether", "roulette", "Привет"}) {
			JsonObject c = new JsonObject();
			c.addProperty("s", s);
			hash.add(c);
		}
		o.add("hash", hash);
		JsonObject rng = new JsonObject();
		rng.addProperty("seed", 123456789);
		o.add("fxRng", rng);
		JsonArray tiers = new JsonArray();
		Object[][] cases = {
			{0L, 100L, "DEFAULT", false, null}, {50L, 100L, "DEFAULT", false, null}, {100L, 100L, "DEFAULT", false, null},
			{150L, 100L, "DEFAULT", false, null}, {150L, 100L, "DEFAULT", false, "NICE"}, {1000L, 10L, "DEFAULT", false, null},
			{1100L, 100L, "DEFAULT", false, null}, {2600L, 100L, "DEFAULT", false, null}, {5100L, 100L, "DEFAULT", false, null},
			{6000L, 1000L, "DEFAULT", false, null}, {10L, 10L, "DEFAULT", true, null}, {5L, 5L, "SLOTS", false, null},
			{24L, 5L, "SLOTS", false, null}, {25L, 5L, "SLOTS", false, null}, {75L, 5L, "SLOTS", false, null},
			{200L, 5L, "SLOTS", false, null}, {500L, 5L, "SLOTS", false, null}, {3L, 5L, "SLOTS", false, null}};
		for (Object[] c : cases) {
			JsonObject j = new JsonObject();
			j.addProperty("ret", (Long) c[0]);
			j.addProperty("stake", (Long) c[1]);
			j.addProperty("table", (String) c[2]);
			j.addProperty("jackpot", (Boolean) c[3]);
			if (c[4] != null) j.addProperty("floor", (String) c[4]);
			tiers.add(j);
		}
		o.add("winTier", tiers);
		JsonArray roll = new JsonArray();
		for (long[] c : new long[][] {{0, 10, 600, 8000}, {10, 10, 600, 8000}, {50, 10, 600, 8000}, {1000, 10, 600, 8000},
			{100000, 1, 600, 8000}, {250, 10, 400, 1200}, {3, 5, 600, 8000}}) {
			JsonObject j = new JsonObject();
			j.addProperty("ret", c[0]);
			j.addProperty("stake", c[1]);
			j.addProperty("min", c[2]);
			j.addProperty("max", c[3]);
			roll.add(j);
		}
		o.add("rollUp", roll);
		JsonArray scale = new JsonArray();
		for (int[] c : new int[][] {{50, 1000}, {100, 1000}, {150, 1000}, {200, 1350}, {150, 7}}) {
			JsonObject j = new JsonObject();
			j.addProperty("pct", c[0]);
			j.addProperty("ms", c[1]);
			scale.add(j);
		}
		o.add("profileScale", scale);
		return o;
	}

	private static JsonObject compute(JsonObject in) {
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		JsonArray ts = in.getAsJsonArray("easeT");
		o.add("easeT", ts);
		JsonObject ease = new JsonObject();
		for (Ease e : Ease.values()) {
			JsonArray a = new JsonArray();
			for (JsonElement t : ts) a.add(e.apply(t.getAsDouble()));
			ease.add(e.id(), a);
		}
		JsonArray back12 = new JsonArray();
		JsonArray decay = new JsonArray();
		for (JsonElement t : ts) {
			back12.add(Ease.outBack(1.2, t.getAsDouble()));
			decay.add(Ease.expDecay(3.2, t.getAsDouble()));
		}
		ease.add("outBack(1.2)", back12);
		ease.add("expDecay(3.2)", decay);
		o.add("ease", ease);

		JsonArray mix = new JsonArray();
		for (JsonElement c : in.getAsJsonArray("seedMix")) {
			JsonArray p = c.getAsJsonObject().getAsJsonArray("parts");
			int[] parts = new int[p.size()];
			for (int i = 0; i < parts.length; i++) parts[i] = p.get(i).getAsInt();
			JsonObject j = new JsonObject();
			j.add("parts", p);
			j.addProperty("out", SeedMix.mix(parts));
			mix.add(j);
		}
		o.add("seedMix", mix);
		JsonArray hash = new JsonArray();
		for (JsonElement c : in.getAsJsonArray("hash")) {
			String s = c.getAsJsonObject().get("s").getAsString();
			JsonObject j = new JsonObject();
			j.addProperty("s", s);
			j.addProperty("out", SeedMix.hash(s));
			hash.add(j);
		}
		o.add("hash", hash);

		int seed = in.getAsJsonObject("fxRng").get("seed").getAsInt();
		JsonObject rng = new JsonObject();
		rng.addProperty("seed", seed);
		SeedMix.FxRng r = new SeedMix.FxRng(seed);
		JsonArray u32 = new JsonArray();
		for (int i = 0; i < 8; i++) u32.add(r.nextU32());
		JsonArray ints = new JsonArray();
		for (int i = 0; i < 8; i++) ints.add(r.nextInt(37));
		rng.add("u32", u32);
		rng.add("int37", ints);
		o.add("fxRng", rng);

		Map<String, WinTierTable> tables = Map.of("DEFAULT", WinTierTable.DEFAULT, "SLOTS", WinTierTable.SLOTS);
		JsonArray tiers = new JsonArray();
		for (JsonElement c : in.getAsJsonArray("winTier")) {
			JsonObject j = c.getAsJsonObject().deepCopy();
			WinTier floor = j.has("floor") ? WinTier.valueOf(j.get("floor").getAsString()) : null;
			WinTier tier = WinTier.of(j.get("ret").getAsLong(), j.get("stake").getAsLong(), tables.get(j.get("table").getAsString()),
				j.get("jackpot").getAsBoolean(), floor);
			j.addProperty("out", tier.name());
			tiers.add(j);
		}
		o.add("winTier", tiers);

		JsonArray roll = new JsonArray();
		for (JsonElement c : in.getAsJsonArray("rollUp")) {
			JsonObject j = c.getAsJsonObject().deepCopy();
			long ret = j.get("ret").getAsLong();
			j.addProperty("dur", RollUp.durationMs(ret, j.get("stake").getAsLong(), j.get("min").getAsInt(), j.get("max").getAsInt()));
			JsonArray vals = new JsonArray();
			for (double t : new double[] {0, 0.1, 0.5, 0.9, 0.999, 1}) vals.add(RollUp.valueAt(ret, t));
			j.add("values", vals);
			roll.add(j);
		}
		o.add("rollUp", roll);

		JsonArray scale = new JsonArray();
		for (JsonElement c : in.getAsJsonArray("profileScale")) {
			JsonObject j = c.getAsJsonObject().deepCopy();
			j.addProperty("out", new TimingProfile(j.get("pct").getAsInt(), false, true).scale(j.get("ms").getAsInt()));
			scale.add(j);
		}
		o.add("profileScale", scale);

		o.addProperty("timeline", sampleTimeline().toCanonicalJson());
		JsonArray sounds = new JsonArray();
		for (SoundCatalog s : SoundCatalog.all()) sounds.add(s.id() + "@" + s.owner());
		o.add("sounds", sounds);
		return o;
	}

	/** A small timeline exercising every builder call; Bedrock builds the same one. */
	static Timeline sampleTimeline() {
		TimingProfile local = new TimingProfile(150, false, true);
		TimelineBuilder b = Timeline.builder("demo", SeedMix.mix(7, 42));
		b.then(120, "demo.spin_up", -1);
		for (int r = 0; r < 3; r++) b.add(600 + 150 * r - 350, 350, "demo.reel_land", r, r * 11, 3);
		b.cue("demo.stop_sound", 2, 5);
		b.group(1).clock(Clock.LOCAL).then(local.scale(900), "demo.win_show", -1, 1, 2, 3).pause(local.scale(150))
			.then(local.scale(RollUp.durationMs(40, 5, 600, 8000)), "demo.rollup", -1, 40);
		return b.build();
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
			assertEquals(e.getAsDouble(), a.getAsDouble(), 1e-12, path);
		} else {
			assertEquals(e, a, path);
		}
	}

	@Test
	void timelineQueries() {
		Timeline t = sampleTimeline();
		assertEquals(900, t.sharedEndMs());
		assertEquals(t.beats().get(0).kind(), "demo.spin_up");
		assertEquals(1, t.groupAt(t.sharedEndMs() + 10));
		assertEquals(Timeline.ceilTicks(901), 19);
	}

	@Test
	void winTierTablesMatchTheSpecs() {
		// global.md §2.4: BIG needs 10× AND net ≥ 100
		assertEquals(WinTier.WIN, WinTier.of(100, 10, WinTierTable.DEFAULT));
		assertEquals(WinTier.BIG, WinTier.of(1100, 100, WinTierTable.DEFAULT));
		// SLOTS.md §10.1: 1× is a WIN, 5× NICE, 100× EPIC
		assertEquals(WinTier.WIN, WinTier.of(5, 5, WinTierTable.SLOTS));
		assertEquals(WinTier.NICE, WinTier.of(25, 5, WinTierTable.SLOTS));
		assertEquals(WinTier.EPIC, WinTier.of(500, 5, WinTierTable.SLOTS));
		long[][] up = WinTierTable.SLOTS.upgradePoints(5, WinTier.NICE, WinTier.EPIC);
		assertEquals(3, up.length);
		assertEquals(75, up[0][1]);
	}

	@Test
	void textFit() {
		assertEquals(2, TextFit.bannerScale(100, 3, 250));
		assertEquals(1, TextFit.bannerScale(300, 3, 250));
		assertEquals(40, TextFit.buttonWidth(40, 20));
		int[] rows = TextFit.flowRows(new int[] {60, 60, 60}, 130, 4);
		assertEquals(1, rows[2]);
	}

	@Test
	void soundIdsAreUnique() {
		assertEquals(SoundCatalog.all().size(), SoundCatalog.all().stream().map(SoundCatalog::id).distinct().count());
	}
}

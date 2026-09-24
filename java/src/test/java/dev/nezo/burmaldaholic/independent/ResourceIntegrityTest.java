package dev.nezo.burmaldaholic.independent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Dangling-reference scan of the mod's resources and sources (no Minecraft bootstrap):
 * <ul>
 *   <li>every JSON parses;</li>
 *   <li>blockstates → models → parents/textures, item definitions → models (26.x {@code items/}), blocks have an
 *       item definition, a loot table and a lang name;</li>
 *   <li>recipes / loot tables / advancement icons only reference items that have an item definition;</li>
 *   <li>advancement parents exist, their title/description keys exist in both languages;</li>
 *   <li>sound fragments: subtitle keys exist, {@code burmaldaholic:} sound files exist;</li>
 *   <li>sources: every {@code *.burmaldaholic.*} key literal exists in both languages, and every
 *       {@code Component.translatable("literal", args…)} passes as many arguments as the EN string has placeholders.</li>
 * </ul>
 * Runtime registry coverage (every registered block / item / entity has a name and model) is checked by the
 * GameTest {@code ResourceRegistryGameTests}.
 */
class ResourceIntegrityTest {
	private static Path root;
	private static Path assets;
	private static Path data;
	private static final Map<String, Map<String, String>> LANG = new HashMap<>();
	private static final Set<String> ITEMS = new HashSet<>();

	@BeforeAll
	static void load() throws IOException {
		root = Path.of(System.getProperty("burmaldaholic.projectDir", "."));
		assets = root.resolve("src/main/resources/assets/burmaldaholic");
		data = root.resolve("src/main/resources/data/burmaldaholic");
		for (String lang : List.of("en_us", "ru_ru")) {
			Map<String, String> all = new HashMap<>();
			try (Stream<Path> files = Files.walk(root.resolve("src/main/lang"))) {
				for (Path f : files.filter(p -> p.getFileName().toString().equals(lang + ".json")).toList()) {
					JsonParser.parseString(Files.readString(f)).getAsJsonObject().entrySet()
						.forEach(e -> all.put(e.getKey(), e.getValue().getAsString()));
				}
			}
			LANG.put(lang, all);
		}
		try (Stream<Path> files = Files.list(assets.resolve("items"))) {
			files.forEach(p -> ITEMS.add(p.getFileName().toString().replace(".json", "")));
		}
	}

	private static List<Path> json(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> s = Files.walk(dir)) {
			return s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
		}
	}

	private static JsonElement parse(Path p, List<String> errors) {
		try {
			return JsonParser.parseString(Files.readString(p));
		} catch (Exception e) {
			errors.add("parse error " + root.relativize(p) + ": " + e.getMessage());
			return new JsonObject();
		}
	}

	/** Visits every string value stored under {@code key} (any depth). */
	private static void strings(JsonElement e, String key, Consumer<String> out) {
		if (e.isJsonObject()) {
			for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
				if (x.getKey().equals(key) && x.getValue().isJsonPrimitive()) {
					out.accept(x.getValue().getAsString());
				}
				strings(x.getValue(), key, out);
			}
		} else if (e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				strings(x, key, out);
			}
		}
	}

	/** {@code burmaldaholic:x/y} → {@code x/y}; null for other namespaces (vanilla assets are assumed present). */
	private static String own(String id) {
		if (id.startsWith("burmaldaholic:")) {
			return id.substring("burmaldaholic:".length());
		}
		return null;
	}

	private static boolean hasLang(String key) {
		return LANG.values().stream().allMatch(m -> m.containsKey(key));
	}

	private static void report(List<String> errors) {
		assertTrue(errors.isEmpty(), () -> errors.size() + " problem(s):\n  " + String.join("\n  ", errors));
	}

	@Test
	void allJsonParses() throws IOException {
		List<String> errors = new ArrayList<>();
		for (Path p : json(root.resolve("src/main/resources"))) {
			parse(p, errors);
		}
		for (Path p : json(root.resolve("src/main/lang"))) {
			parse(p, errors);
		}
		for (Path p : json(root.resolve("src/main/sounds"))) {
			parse(p, errors);
		}
		report(errors);
	}

	@Test
	void modelsTexturesAndItemDefinitions() throws IOException {
		List<String> errors = new ArrayList<>();
		for (Path p : json(assets.resolve("blockstates"))) {
			String name = p.getFileName().toString().replace(".json", "");
			strings(parse(p, errors), "model", m -> checkModel(m, "blockstate " + name, errors));
			if (!ITEMS.contains(name)) {
				errors.add("block " + name + " has no items/" + name + ".json");
			}
			if (!Files.exists(data.resolve("loot_table/blocks/" + name + ".json"))) {
				errors.add("block " + name + " has no loot table (breaking it drops nothing)");
			}
			if (!hasLang("block.burmaldaholic." + name)) {
				errors.add("block " + name + " has no name in both languages");
			}
		}
		for (Path p : json(assets.resolve("items"))) {
			String name = p.getFileName().toString().replace(".json", "");
			strings(parse(p, errors), "model", m -> checkModel(m, "item " + name, errors));
		}
		for (Path p : json(assets.resolve("models"))) {
			JsonObject m = parse(p, errors).getAsJsonObject();
			String where = root.relativize(p).toString();
			if (m.has("parent")) {
				checkModel(m.get("parent").getAsString(), where, errors);
			}
			if (m.has("textures")) {
				for (Map.Entry<String, JsonElement> t : m.getAsJsonObject("textures").entrySet()) {
					String tex = t.getValue().getAsString();
					String o = own(tex);
					if (!tex.startsWith("#") && o != null && !Files.exists(assets.resolve("textures/" + o + ".png"))) {
						errors.add(where + ": missing texture " + tex);
					}
				}
			}
		}
		report(errors);
	}

	private static void checkModel(String model, String where, List<String> errors) {
		String o = own(model);
		if (o != null && !Files.exists(assets.resolve("models/" + o + ".json"))) {
			errors.add(where + ": missing model " + model);
		}
	}

	/** Item ids referenced from data files: {@code "item"} / {@code "id"} / {@code "name"} / ingredient strings. */
	@Test
	void dataReferencesKnownItems() throws IOException {
		List<String> errors = new ArrayList<>();
		List<Path> files = new ArrayList<>(json(data.resolve("recipe")));
		files.addAll(json(data.resolve("loot_table")));
		for (Path p : files) {
			checkIds(parse(p, errors), root.relativize(p).toString(), errors);
		}
		report(errors);
	}

	private static void checkIds(JsonElement e, String where, List<String> errors) {
		if (e.isJsonObject()) {
			for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
				if (x.getKey().equals("random_sequence")) {
					continue; // a loot sequence id, not an item
				}
				checkIds(x.getValue(), where, errors);
			}
		} else if (e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				checkIds(x, where, errors);
			}
		} else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
			String o = own(e.getAsString());
			if (o != null && !ITEMS.contains(o)) {
				errors.add(where + ": unknown item " + e.getAsString());
			}
		}
	}

	@Test
	void advancementsResolve() throws IOException {
		List<String> errors = new ArrayList<>();
		Path dir = data.resolve("advancement");
		Set<String> ids = new HashSet<>();
		for (Path p : json(dir)) {
			ids.add(dir.relativize(p).toString().replace('\\', '/').replace(".json", ""));
		}
		for (Path p : json(dir)) {
			String id = dir.relativize(p).toString().replace(".json", "");
			JsonObject a = parse(p, errors).getAsJsonObject();
			if (a.has("parent")) {
				String o = own(a.get("parent").getAsString());
				if (o != null && !ids.contains(o)) {
					errors.add(id + ": missing parent " + a.get("parent").getAsString());
				}
			}
			if (!a.has("criteria") || a.getAsJsonObject("criteria").isEmpty()) {
				errors.add(id + ": no criteria");
			}
			if (a.has("display")) {
				JsonObject d = a.getAsJsonObject("display");
				if (d.has("icon")) {
					String o = own(d.getAsJsonObject("icon").get("id").getAsString());
					if (o != null && !ITEMS.contains(o)) {
						errors.add(id + ": unknown icon item " + o);
					}
				}
				for (String f : List.of("title", "description")) {
					JsonElement t = d.get(f);
					if (t == null || !t.isJsonObject() || !t.getAsJsonObject().has("translate")) {
						errors.add(id + ": " + f + " is not translatable");
					} else if (!hasLang(t.getAsJsonObject().get("translate").getAsString())) {
						errors.add(id + ": missing lang " + t.getAsJsonObject().get("translate").getAsString());
					}
				}
				if (d.has("background")) {
					String o = own(d.get("background").getAsString());
					if (o != null && !Files.exists(assets.resolve("textures/" + o + ".png"))) {
						errors.add(id + ": missing background " + o);
					}
				}
			}
		}
		report(errors);
	}

	@Test
	void soundsResolve() throws IOException {
		List<String> errors = new ArrayList<>();
		for (Path p : json(root.resolve("src/main/sounds"))) {
			JsonObject o = parse(p, errors).getAsJsonObject();
			for (Map.Entry<String, JsonElement> ev : o.entrySet()) {
				JsonObject def = ev.getValue().getAsJsonObject();
				if (def.has("subtitle") && !hasLang(def.get("subtitle").getAsString())) {
					errors.add("sound " + ev.getKey() + ": missing subtitle " + def.get("subtitle").getAsString());
				}
				JsonArray sounds = def.has("sounds") ? def.getAsJsonArray("sounds") : new JsonArray();
				for (JsonElement s : sounds) {
					boolean event = s.isJsonObject() && s.getAsJsonObject().has("type") && s.getAsJsonObject().get("type").getAsString().equals("event");
					String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
					String own = own(name);
					if (!event && own != null && !Files.exists(assets.resolve("sounds/" + own + ".ogg"))) {
						errors.add("sound " + ev.getKey() + ": missing file " + name);
					}
				}
			}
		}
		report(errors);
	}

	// ---- sources ------------------------------------------------------------------------------

	private static final Pattern KEY_LITERAL = Pattern.compile(
		"\"((?:gui|msg|hud|tooltip|unit|item|block|entity|advancement|chat|title|death|subtitles|key|config|gamerule|itemGroup|"
			+ "container|commands|error|screen|toast)\\.burmaldaholic\\.[a-z0-9_.]*)\"");
	private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?s");

	private static List<Path> sources() throws IOException {
		List<Path> out = new ArrayList<>();
		for (String set : List.of("src/main/java", "src/client/java")) {
			try (Stream<Path> s = Files.walk(root.resolve(set))) {
				s.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
			}
		}
		return out;
	}

	private static boolean keyOrFamilyExists(String key) {
		Map<String, String> en = LANG.get("en_us");
		if (key.endsWith(".")) {
			return en.keySet().stream().anyMatch(k -> k.startsWith(key));
		}
		return hasLang(key) || hasLang(key + ".p1") || en.keySet().stream().anyMatch(k -> k.startsWith(key + "."));
	}

	@Test
	void sourceKeyLiteralsExistInBothLanguages() throws IOException {
		List<String> errors = new ArrayList<>();
		for (Path f : sources()) {
			String src = Files.readString(f);
			Matcher m = KEY_LITERAL.matcher(src);
			while (m.find()) {
				if (!keyOrFamilyExists(m.group(1))) {
					errors.add(root.relativize(f) + ":" + line(src, m.start()) + " dangling key " + m.group(1));
				}
			}
		}
		report(errors);
	}

	@Test
	void translatableArgumentCountsMatchPlaceholders() throws IOException {
		List<String> errors = new ArrayList<>();
		Map<String, String> en = LANG.get("en_us");
		for (Path f : sources()) {
			String src = Files.readString(f);
			int from = 0;
			while (true) {
				int i = src.indexOf("Component.translatable(", from);
				if (i < 0) {
					break;
				}
				int open = i + "Component.translatable".length();
				from = open + 1;
				List<String> args = args(src, open);
				if (args.isEmpty() || !args.getFirst().matches("\"[^\"]+\"")) {
					continue;
				}
				String key = args.getFirst().substring(1, args.getFirst().length() - 1);
				String value = en.get(key);
				if (value == null) {
					continue; // vanilla key (e.g. gui.advancements), or reported by the literal test
				}
				int needed = placeholders(value);
				if (needed != args.size() - 1) {
					errors.add(root.relativize(f) + ":" + line(src, i) + " " + key + " needs " + needed + " argument(s), got " + (args.size() - 1));
				}
			}
		}
		report(errors);
	}

	private static int placeholders(String s) {
		int max = 0, plain = 0;
		Matcher m = PLACEHOLDER.matcher(s.replace("%%", ""));
		while (m.find()) {
			if (m.group(1) != null) {
				max = Math.max(max, Integer.parseInt(m.group(1)));
			} else {
				plain++;
			}
		}
		return Math.max(max, plain);
	}

	/** Top-level arguments of the call whose '(' is at {@code open}. */
	private static List<String> args(String src, int open) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		int depth = 0;
		char quote = 0;
		for (int j = open; j < src.length(); j++) {
			char c = src.charAt(j);
			if (quote != 0) {
				cur.append(c);
				if (c == '\\') {
					cur.append(src.charAt(++j));
				} else if (c == quote) {
					quote = 0;
				}
				continue;
			}
			switch (c) {
				case '"', '\'' -> {
					quote = c;
					cur.append(c);
				}
				case '(', '[', '{' -> {
					if (depth++ > 0) {
						cur.append(c);
					}
				}
				case ')', ']', '}' -> {
					if (--depth == 0) {
						if (!cur.toString().isBlank()) {
							out.add(cur.toString().trim());
						}
						return out;
					}
					cur.append(c);
				}
				case ',' -> {
					if (depth == 1) {
						out.add(cur.toString().trim());
						cur.setLength(0);
					} else {
						cur.append(c);
					}
				}
				default -> cur.append(c);
			}
		}
		return out;
	}

	private static int line(String src, int pos) {
		int n = 1;
		for (int i = 0; i < pos; i++) {
			if (src.charAt(i) == '\n') {
				n++;
			}
		}
		return n;
	}
}

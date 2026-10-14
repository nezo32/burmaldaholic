package dev.nezo.burmaldaholic.worldgen.logic;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Data-pack files of the v0.2 casinos (PURE; architecture doc casino-buildings.md §4.1 / §5): template
 * pools, the standalone structures (one per style, type {@code burmaldaholic:casino}), the structure set
 * {@code burmaldaholic:casinos}, the per-style biome tags and the {@code #burmaldaholic:casino} structure
 * tag. Written at build time by {@link PackGen}; never committed (the v0.1 files stay frozen in
 * {@code src/main/resources/resourcepacks/casinos}).
 *
 * <p>Piece ids: {@code casino/<style>/<piece>} for the standalone Overworld casino (pools by piece name, see
 * {@link #poolName}); any other v0.2 id ({@code casino/annex/<village style>}, {@code casino/parlor},
 * {@code casino/lounge}) gets a single-element pool at its own path, like v0.1.
 *
 * <p>Only JSON fields that exist in both 26.2 and 26.3 are written (§1). Output is deterministic.
 */
public final class PackFiles {
	public static final String NS = "burmaldaholic";
	/** Structure type of the standalone casino (§6.1). */
	public static final String STRUCTURE_TYPE = NS + ":casino";
	public static final String SET = NS + ":casinos";
	public static final String TAG = NS + ":casino";
	/**
	 * 448-block cells. Measured (WorldgenSpawnSurvey, 4 seeds): with one fixed spot only ~19 % of cells hold a casino
	 * (oceans, rivers, mountains, uncovered biomes, steep or wet footprints) and the contract's 40/12 gave one per
	 * ~1500 blocks; trying 5 spots per cell (CasinoStructure) raises that to ~29 %, so 28/10 gives the design target
	 * of one per ~830 blocks.
	 */
	public static final int SPACING = 28;
	public static final int SEPARATION = 10;
	public static final int SALT = 1592770897;
	/** Never closer than 3 chunks to a village placement: the village annex covers villages. */
	public static final int VILLAGE_EXCLUSION_CHUNKS = 3;
	public static final int SIZE = 3;
	public static final int START_HEIGHT = -2;
	public static final int MAX_DISTANCE = 48;
	public static final int MAX_HEIGHT_RANGE = 12;
	/** Pools every standalone casino must contain (D5: complete or absent). */
	public static final List<String> REQUIRED_POOLS = List.of("entrance", "floor", "bar", "rear");
	/** Pool path segment of annex pieces; never a style. */
	public static final String ANNEX = "annex";

	/** Overworld styles in their canonical order, with the biomes each one spawns in (§4.1 table). */
	public static final Map<String, List<String>> STYLE_BIOMES;

	static {
		Map<String, List<String>> m = new LinkedHashMap<>();
		// §4.1 table + the open land biomes the survey found uncovered (sunflower plains, old-growth birch / pine,
		// sparse jungle, eroded badlands); dense-canopy, swamp and mountain biomes stay out on purpose.
		m.put("tudor", List.of("#minecraft:has_structure/village_plains", "minecraft:meadow", "minecraft:forest", "minecraft:birch_forest",
			"minecraft:flower_forest", "minecraft:sunflower_plains", "minecraft:old_growth_birch_forest"));
		m.put("oasis", List.of("#minecraft:has_structure/village_desert", "minecraft:badlands", "minecraft:wooded_badlands",
			"minecraft:eroded_badlands"));
		m.put("safari", List.of("#minecraft:has_structure/village_savanna", "minecraft:savanna_plateau", "minecraft:sparse_jungle"));
		m.put("alpine", List.of("#minecraft:has_structure/village_taiga", "minecraft:cherry_grove", "minecraft:old_growth_spruce_taiga",
			"minecraft:old_growth_pine_taiga"));
		m.put("ice_palace", List.of("#minecraft:has_structure/village_snowy", "minecraft:snowy_taiga", "minecraft:ice_spikes"));
		STYLE_BIOMES = java.util.Collections.unmodifiableMap(m);
	}

	private PackFiles() {}

	// ---- piece → pool model ---------------------------------------------------------------------------

	/** Last path segment of a layout id ({@code casino/tudor/floor_a} → {@code floor_a}). */
	public static String pieceName(String layoutId) {
		return layoutId.substring(layoutId.lastIndexOf('/') + 1);
	}

	/** Style of a standalone-casino piece id {@code casino/<style>/<piece>}; empty for every other id. */
	public static Optional<String> styleOf(String layoutId) {
		String[] p = layoutId.split("/");
		return p.length == 3 && p[0].equals("casino") && !p[1].equals(ANNEX) ? Optional.of(p[1]) : Optional.empty();
	}

	/** Pool a standalone piece belongs to: {@code floor_a/floor_b → floor}, {@code office/vip → rear}, else its name. */
	public static String poolName(String pieceName) {
		return switch (pieceName) {
			case "floor_a", "floor_b" -> "floor";
			case "office", "vip" -> "rear";
			default -> pieceName;
		};
	}

	/** Pool weight of a standalone piece (§4.1: office 2, everything else 1). */
	public static int weight(String pieceName) {
		return pieceName.equals("office") ? 2 : 1;
	}

	/** Pool path ({@code worldgen/casino/<style>/<pool>}) of a style's pool. */
	public static String stylePoolPath(String style, String pool) {
		return "worldgen/casino/" + style + "/" + pool;
	}

	/** Resource id of a style's pool, as written in jigsaw blocks. */
	public static String stylePoolId(String style, String pool) {
		return NS + ":" + stylePoolPath(style, pool);
	}

	public static String structureId(String style) {
		return NS + ":casino_" + style;
	}

	public static String biomeTagId(String style) {
		return NS + ":has_structure/casino_" + style;
	}

	public record Element(Layout layout, int weight) {}

	/** A template pool: {@code path} without namespace (= {@code worldgen/…}), elements in a fixed order. */
	public record Pool(String path, List<Element> elements) {
		public Pool {
			elements = List.copyOf(elements);
		}

		public String id() {
			return NS + ":" + path;
		}
	}

	/** Every pool of a set of v0.2 layouts, sorted by path (deterministic). */
	public static List<Pool> pools(Collection<Layout> layouts) {
		Map<String, List<Element>> byPath = new TreeMap<>();
		for (Layout l : layouts) {
			Optional<String> style = styleOf(l.id());
			String name = pieceName(l.id());
			String path = style.isPresent() ? stylePoolPath(style.get(), poolName(name)) : l.templatePath();
			byPath.computeIfAbsent(path, k -> new ArrayList<>()).add(new Element(l, style.isPresent() ? weight(name) : 1));
		}
		List<Pool> out = new ArrayList<>();
		byPath.forEach((path, elements) -> {
			elements.sort(java.util.Comparator.comparing(e -> e.layout().id()));
			out.add(new Pool(path, elements));
		});
		return out;
	}

	/** Styles whose required pools all exist, in canonical order (unknown styles after, alphabetically). */
	public static List<String> completeStyles(Collection<Layout> layouts) {
		Map<String, java.util.Set<String>> poolsOf = new TreeMap<>();
		for (Layout l : layouts) {
			styleOf(l.id()).ifPresent(s -> poolsOf.computeIfAbsent(s, k -> new java.util.TreeSet<>()).add(poolName(pieceName(l.id()))));
		}
		List<String> out = new ArrayList<>();
		for (String s : STYLE_BIOMES.keySet()) {
			if (poolsOf.containsKey(s) && poolsOf.get(s).containsAll(REQUIRED_POOLS)) {
				out.add(s);
			}
		}
		poolsOf.forEach((s, pools) -> {
			if (!STYLE_BIOMES.containsKey(s) && pools.containsAll(REQUIRED_POOLS)) {
				out.add(s);
			}
		});
		return out;
	}

	// ---- JSON -----------------------------------------------------------------------------------------

	/** Template pool JSON (same element format as v0.1 / vanilla: single_pool_element, no processors, rigid). */
	public static String poolJson(Pool pool) {
		StringBuilder sb = new StringBuilder("{\n  \"fallback\": \"minecraft:empty\",\n  \"elements\": [\n");
		for (int i = 0; i < pool.elements().size(); i++) {
			Element e = pool.elements().get(i);
			sb.append("    {\n")
				.append("      \"element\": {\n")
				.append("        \"element_type\": \"minecraft:single_pool_element\",\n")
				.append("        \"location\": ").append(q(NS + ":" + e.layout().templatePath())).append(",\n")
				.append("        \"processors\": \"minecraft:empty\",\n")
				.append("        \"projection\": \"rigid\"\n")
				.append("      },\n")
				.append("      \"weight\": ").append(e.weight()).append('\n')
				.append(i + 1 < pool.elements().size() ? "    },\n" : "    }\n");
		}
		return sb.append("  ]\n}\n").toString();
	}

	/** The standalone casino structure of a style (§4.1). */
	public static String structureJson(String style) {
		StringBuilder required = new StringBuilder();
		for (int i = 0; i < REQUIRED_POOLS.size(); i++) {
			required.append(i == 0 ? "" : ", ").append(q(REQUIRED_POOLS.get(i)));
		}
		return "{\n"
			+ "  \"type\": " + q(STRUCTURE_TYPE) + ",\n"
			+ "  \"biomes\": " + q("#" + biomeTagId(style)) + ",\n"
			+ "  \"step\": \"surface_structures\",\n"
			+ "  \"spawn_overrides\": {},\n"
			+ "  \"terrain_adaptation\": \"beard_thin\",\n"
			+ "  \"start_pool\": " + q(stylePoolId(style, "entrance")) + ",\n"
			+ "  \"size\": " + SIZE + ",\n"
			+ "  \"start_height\": { \"absolute\": " + START_HEIGHT + " },\n"
			+ "  \"max_distance_from_center\": " + MAX_DISTANCE + ",\n"
			+ "  \"use_expansion_hack\": false,\n"
			// field of JigsawStructure on 26.2 and 26.3: casino blocks placed where water was are not waterlogged
			+ "  \"liquid_settings\": \"ignore_waterlogging\",\n"
			+ "  \"max_height_range\": " + MAX_HEIGHT_RANGE + ",\n"
			+ "  \"required_pieces\": [" + required + "]\n"
			+ "}\n";
	}

	/** {@code burmaldaholic:casinos}: every complete style, equal weights, village-like spread. */
	public static String structureSetJson(List<String> styles) {
		StringBuilder sb = new StringBuilder("{\n  \"structures\": [\n");
		for (int i = 0; i < styles.size(); i++) {
			sb.append("    { \"structure\": ").append(q(structureId(styles.get(i)))).append(", \"weight\": 1 }")
				.append(i + 1 < styles.size() ? ",\n" : "\n");
		}
		return sb.append("  ],\n")
			.append("  \"placement\": {\n")
			.append("    \"type\": \"minecraft:random_spread\",\n")
			.append("    \"spacing\": ").append(SPACING).append(",\n")
			.append("    \"separation\": ").append(SEPARATION).append(",\n")
			.append("    \"salt\": ").append(SALT).append(",\n")
			.append("    \"exclusion_zone\": { \"other_set\": \"minecraft:villages\", \"chunk_count\": ").append(VILLAGE_EXCLUSION_CHUNKS).append(" }\n")
			.append("  }\n}\n").toString();
	}

	/** Biome tag of a style; every entry optional so a biome / tag renamed in a later 26.x never breaks loading. */
	public static String biomeTagJson(String style) {
		List<String> biomes = STYLE_BIOMES.get(style);
		if (biomes == null) {
			throw new IllegalArgumentException("no biome list for casino style '" + style + "' (add it to PackFiles.STYLE_BIOMES)");
		}
		return tagJson(biomes);
	}

	/** {@code #burmaldaholic:casino} for {@code /locate structure #burmaldaholic:casino} (may be empty). */
	public static String structureTagJson(List<String> styles) {
		List<String> ids = new ArrayList<>();
		styles.forEach(s -> ids.add(structureId(s)));
		return tagJson(ids);
	}

	private static String tagJson(List<String> values) {
		StringBuilder sb = new StringBuilder("{\n  \"replace\": false,\n  \"values\": [");
		for (int i = 0; i < values.size(); i++) {
			sb.append(i == 0 ? "\n" : ",\n").append("    { \"id\": ").append(q(values.get(i))).append(", \"required\": false }");
		}
		return sb.append(values.isEmpty() ? "]\n}\n" : "\n  ]\n}\n").toString();
	}

	// ---- file set -------------------------------------------------------------------------------------

	public static String poolFile(Pool pool) {
		return "data/" + NS + "/worldgen/template_pool/" + pool.path() + ".json";
	}

	public static String structureFile(String style) {
		return "data/" + NS + "/worldgen/structure/casino_" + style + ".json";
	}

	public static String structureSetFile() {
		return "data/" + NS + "/worldgen/structure_set/casinos.json";
	}

	public static String biomeTagFile(String style) {
		return "data/" + NS + "/tags/worldgen/biome/has_structure/casino_" + style + ".json";
	}

	public static String structureTagFile() {
		return "data/" + NS + "/tags/worldgen/structure/casino.json";
	}

	/**
	 * Every pack file (pack-relative path → bytes) for the given v0.2 layouts: templates, pools and — for each
	 * complete style — its structure + biome tag; the set only when at least one style is complete (an empty
	 * pack is valid and generates nothing); the structure tag always.
	 */
	public static Map<String, byte[]> files(Collection<Layout> layouts) {
		Map<String, byte[]> out = new LinkedHashMap<>();
		List<Layout> sorted = new ArrayList<>(layouts);
		sorted.sort(java.util.Comparator.comparing(Layout::id));
		for (Layout l : sorted) {
			out.put(TemplateExporter.templateFile(l), Nbt.writeGzip(TemplateExporter.template(l)));
		}
		for (Pool p : pools(sorted)) {
			out.put(poolFile(p), utf8(poolJson(p)));
		}
		List<String> styles = completeStyles(sorted);
		for (String s : styles) {
			out.put(structureFile(s), utf8(structureJson(s)));
			out.put(biomeTagFile(s), utf8(biomeTagJson(s)));
		}
		if (!styles.isEmpty()) {
			out.put(structureSetFile(), utf8(structureSetJson(styles)));
		}
		out.put(structureTagFile(), utf8(structureTagJson(styles)));
		return out;
	}

	private static byte[] utf8(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	/** JSON string literal. */
	static String q(String s) {
		StringBuilder sb = new StringBuilder("\"");
		for (char c : s.toCharArray()) {
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		return sb.append('"').toString();
	}
}

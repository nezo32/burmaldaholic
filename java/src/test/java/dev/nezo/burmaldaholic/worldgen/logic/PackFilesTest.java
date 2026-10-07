package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@link PackFiles} / {@link PackGen} (architecture doc §5, §9.1): JSON shape, references resolve, no path
 * collides with the frozen v0.1 tree, deterministic output. Runs on the real {@link CasinoPack} (whatever the
 * lanes have delivered) and on the platform placeholders (a complete 5-style pack).
 */
class PackFilesTest {
	private static final Path LEGACY = Path.of("src/main/resources").resolve(TemplateExporter.PACK_ROOT);
	private static final Pattern LOCATION = Pattern.compile("\"location\": \"burmaldaholic:([^\"]+)\"");
	private static final Pattern START_POOL = Pattern.compile("\"start_pool\": \"burmaldaholic:([^\"]+)\"");
	private static final Pattern BIOMES = Pattern.compile("\"biomes\": \"#burmaldaholic:([^\"]+)\"");
	private static final Pattern STRUCTURE = Pattern.compile("\"structure\": \"burmaldaholic:([^\"]+)\"");

	static List<Layout> placeholderPack() {
		List<Layout> out = new ArrayList<>();
		for (String s : PackFiles.STYLE_BIOMES.keySet()) {
			PlaceholderPieces.style(s).forEach(p -> out.add(p.layout()));
		}
		return out;
	}

	private static String text(Map<String, byte[]> files, String path) {
		byte[] b = files.get(path);
		assertTrue(b != null, "missing " + path);
		return new String(b, StandardCharsets.UTF_8);
	}

	/** Every reference inside the pack resolves to a file of the pack (or a vanilla id). */
	private static void assertConsistent(Map<String, byte[]> files) {
		String ns = "data/burmaldaholic/";
		for (Map.Entry<String, byte[]> f : files.entrySet()) {
			String path = f.getKey();
			String json = path.endsWith(".json") ? new String(f.getValue(), StandardCharsets.UTF_8) : "";
			if (path.contains("/template_pool/")) {
				Matcher m = LOCATION.matcher(json);
				int n = 0;
				while (m.find()) {
					n++;
					assertTrue(files.containsKey(ns + "structure/" + m.group(1) + ".nbt"), path + " → template " + m.group(1));
				}
				assertTrue(n > 0, path + " has no element");
			}
			if (path.startsWith(ns + "worldgen/structure/")) {
				Matcher sp = START_POOL.matcher(json);
				assertTrue(sp.find(), path);
				assertTrue(files.containsKey(ns + "worldgen/template_pool/" + sp.group(1) + ".json"), path + " → start pool");
				Matcher bi = BIOMES.matcher(json);
				assertTrue(bi.find(), path);
				assertTrue(files.containsKey(ns + "tags/worldgen/biome/" + bi.group(1) + ".json"), path + " → biome tag");
			}
			if (path.endsWith("structure_set/casinos.json")) {
				Matcher m = STRUCTURE.matcher(json);
				while (m.find()) {
					assertTrue(files.containsKey(ns + "worldgen/structure/" + m.group(1) + ".json"), "set → " + m.group(1));
				}
			}
		}
		// every jigsaw's pool exists
		for (PackFiles.Pool pool : PackFiles.pools(layoutsOf(files))) {
			assertTrue(files.containsKey(PackFiles.poolFile(pool)), pool.id());
		}
	}

	private static List<Layout> layoutsOf(Map<String, byte[]> files) {
		List<Layout> out = new ArrayList<>();
		for (Layout l : placeholderPack()) {
			if (files.containsKey(TemplateExporter.templateFile(l))) {
				out.add(l);
			}
		}
		return out;
	}

	@Test
	void placeholderPackIsCompleteAndConsistent() {
		List<Layout> pack = placeholderPack();
		Map<String, byte[]> files = PackFiles.files(pack);
		assertConsistent(files);
		assertEquals(List.copyOf(PackFiles.STYLE_BIOMES.keySet()), PackFiles.completeStyles(pack));
		for (Layout l : pack) {
			for (Layout.Jigsaw j : l.jigsaws()) {
				if (!j.pool().equals("minecraft:empty")) {
					String path = "data/burmaldaholic/worldgen/template_pool/" + j.pool().substring("burmaldaholic:".length()) + ".json";
					assertTrue(files.containsKey(path), l.id() + " jigsaw pool " + j.pool());
				}
			}
		}
		assertTrue(files.containsKey(PackFiles.structureSetFile()));
		String set = text(files, PackFiles.structureSetFile());
		assertTrue(set.contains("\"spacing\": 22") && set.contains("\"separation\": 8") && set.contains("\"salt\": 1592770897")
			&& set.contains("\"other_set\": \"minecraft:villages\", \"chunk_count\": 3"), set);
		String tag = text(files, PackFiles.structureTagFile());
		for (String s : PackFiles.STYLE_BIOMES.keySet()) {
			assertTrue(tag.contains("\"burmaldaholic:casino_" + s + "\""), tag);
		}
	}

	@Test
	void goldenJson() {
		assertEquals("""
			{
			  "type": "burmaldaholic:casino",
			  "biomes": "#burmaldaholic:has_structure/casino_tudor",
			  "step": "surface_structures",
			  "spawn_overrides": {},
			  "terrain_adaptation": "beard_thin",
			  "start_pool": "burmaldaholic:worldgen/casino/tudor/entrance",
			  "size": 3,
			  "start_height": { "absolute": -3 },
			  "project_start_to_heightmap": "WORLD_SURFACE_WG",
			  "max_distance_from_center": 48,
			  "use_expansion_hack": false,
			  "max_height_range": 12,
			  "required_pieces": ["entrance", "floor", "bar"]
			}
			""", PackFiles.structureJson("tudor"));
		assertEquals("""
			{
			  "structures": [
			    { "structure": "burmaldaholic:casino_tudor", "weight": 1 },
			    { "structure": "burmaldaholic:casino_oasis", "weight": 1 }
			  ],
			  "placement": {
			    "type": "minecraft:random_spread",
			    "spacing": 22,
			    "separation": 8,
			    "salt": 1592770897,
			    "exclusion_zone": { "other_set": "minecraft:villages", "chunk_count": 3 }
			  }
			}
			""", PackFiles.structureSetJson(List.of("tudor", "oasis")));
		assertEquals("""
			{
			  "replace": false,
			  "values": [
			    { "id": "#minecraft:has_structure/village_desert", "required": false },
			    { "id": "minecraft:badlands", "required": false },
			    { "id": "minecraft:wooded_badlands", "required": false },
			    { "id": "minecraft:eroded_badlands", "required": false }
			  ]
			}
			""", PackFiles.biomeTagJson("oasis"));
		assertEquals("{\n  \"replace\": false,\n  \"values\": []\n}\n", PackFiles.structureTagJson(List.of()));
	}

	@Test
	void poolsGroupPiecesWithContractWeights() {
		List<PackFiles.Pool> pools = PackFiles.pools(PlaceholderPieces.style("tudor").stream().map(Piece::layout).toList());
		Map<String, String> got = new java.util.TreeMap<>();
		for (PackFiles.Pool p : pools) {
			StringBuilder sb = new StringBuilder();
			p.elements().forEach(e -> sb.append(PackFiles.pieceName(e.layout().id())).append('=').append(e.weight()).append(' '));
			got.put(p.path(), sb.toString().trim());
		}
		assertEquals(Map.of(
			"worldgen/casino/tudor/entrance", "entrance=1",
			"worldgen/casino/tudor/floor", "floor_a=1 floor_b=1",
			"worldgen/casino/tudor/bar", "bar=1",
			"worldgen/casino/tudor/rear", "office=2 vip=1"), got);
		assertTrue(PackFiles.styleOf("casino/annex/plains").isEmpty());
		assertTrue(PackFiles.styleOf("casino/parlor").isEmpty());
		assertTrue(PackFiles.styleOf("village_casino_plains").isEmpty());
		assertEquals("ice_palace", PackFiles.styleOf("casino/ice_palace/vip").orElseThrow());
	}

	/** An incomplete style (no bar) gets pools and templates but no structure; an empty pack writes no set. */
	@Test
	void incompleteStylesAndTheEmptyPackGenerateNothing() {
		List<Layout> noBar = PlaceholderPieces.style("tudor").stream().map(Piece::layout).filter(l -> !l.id().endsWith("/bar")).toList();
		Map<String, byte[]> files = PackFiles.files(noBar);
		assertFalse(files.containsKey(PackFiles.structureFile("tudor")));
		assertFalse(files.containsKey(PackFiles.structureSetFile()));
		Map<String, byte[]> empty = PackFiles.files(List.of());
		assertEquals(Set.of(PackFiles.structureTagFile()), empty.keySet());
	}

	@Test
	void realPackIsConsistentAndNeverTouchesTheLegacyTree() throws Exception {
		Map<String, byte[]> gen = PackGen.files();
		Set<String> legacy = new TreeSet<>();
		try (var walk = Files.walk(LEGACY)) {
			walk.filter(Files::isRegularFile).forEach(p -> legacy.add(TemplateExporter.PACK_ROOT + "/" + LEGACY.relativize(p).toString().replace('\\', '/')));
		}
		for (String path : gen.keySet()) {
			assertFalse(legacy.contains(path), path + " collides with a frozen v0.1 file");
		}
		for (Layout l : CasinoPack.layouts()) {
			assertTrue(l.id().startsWith("casino/"), l.id());
			assertFalse(Layouts.legacy().containsKey(l.id()), l.id());
		}
		assertTrue(gen.containsKey(PackGen.PREVIEWS + "/manifest.json"));
	}

	@Test
	void outputIsDeterministic() {
		Map<String, byte[]> a = PackFiles.files(placeholderPack());
		Map<String, byte[]> b = PackFiles.files(placeholderPack());
		assertEquals(List.copyOf(a.keySet()), List.copyOf(b.keySet()));
		a.forEach((k, v) -> assertArrayEquals(v, b.get(k), k));
		Map<String, byte[]> g1 = PackGen.files();
		Map<String, byte[]> g2 = PackGen.files();
		assertEquals(List.copyOf(g1.keySet()), List.copyOf(g2.keySet()));
		g1.forEach((k, v) -> assertArrayEquals(v, g2.get(k), k));
		String manifest = PackGen.manifest(PlaceholderPieces.style("tudor"));
		assertTrue(manifest.contains("\"name\": \"floor_a+office\"") && manifest.contains("\"rotation\": 1"), manifest);
	}
}

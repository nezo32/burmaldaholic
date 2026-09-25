package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns {@link Layouts} into the files of the built-in data pack {@code burmaldaholic:casinos}
 * (PURE): vanilla structure templates ({@code .nbt}) and one template pool per building.
 *
 * <p>Regenerate the committed files with
 * {@code BURMALDAHOLIC_EXPORT_STRUCTURES=1 ./gradlew test --tests '*TemplateExportTest'}; without the
 * variable the same test fails if a committed file differs from what the layouts produce.
 */
public final class TemplateExporter {
	/**
	 * Data version the templates are written with: Minecraft 26.2's world version (4903). Newer game
	 * versions upgrade templates on load through their data fixers; never write a newer version.
	 */
	public static final int DATA_VERSION = 4903;

	/** Folder of the built-in pack inside the mod jar (Fabric: {@code resourcepacks/<pack id path>}). */
	public static final String PACK_ROOT = "resourcepacks/casinos";

	private TemplateExporter() {}

	/** Pack-relative path of the template ({@code data/burmaldaholic/structure/worldgen/<id>.nbt}). */
	public static String templateFile(Layout l) {
		return "data/burmaldaholic/structure/" + l.templatePath() + ".nbt";
	}

	/** Pack-relative path of the single-element pool ({@code burmaldaholic:worldgen/<id>}). */
	public static String poolFile(Layout l) {
		return "data/burmaldaholic/worldgen/template_pool/" + l.templatePath() + ".json";
	}

	/** The structure template NBT. */
	public static Map<String, Object> template(Layout layout) {
		Vec size = layout.size();
		Grid grid = layout.grid();
		Map<String, BlockSpec> overrides = new LinkedHashMap<>();
		Map<String, Map<String, Object>> blockNbt = new LinkedHashMap<>();
		for (Layout.PlacedMarker m : layout.markers()) {
			String key = key(m.pos());
			overrides.put(key, BlockSpec.mc("structure_block", "mode", "data"));
			Map<String, Object> nbt = Nbt.compound();
			nbt.put("id", "minecraft:structure_block");
			nbt.put("mode", "DATA");
			nbt.put("metadata", m.marker().metadata());
			nbt.put("name", "");
			blockNbt.put(key, nbt);
		}
		for (Layout.Jigsaw j : layout.jigsaws()) {
			String key = key(j.pos());
			overrides.put(key, BlockSpec.mc("jigsaw", "orientation", j.orientation()));
			Map<String, Object> nbt = Nbt.compound();
			nbt.put("id", "minecraft:jigsaw");
			nbt.put("final_state", j.finalState());
			nbt.put("joint", j.joint());
			nbt.put("name", j.name());
			nbt.put("placement_priority", 0);
			nbt.put("pool", j.pool());
			nbt.put("selection_priority", 0);
			nbt.put("target", j.target());
			blockNbt.put(key, nbt);
		}

		List<BlockSpec> palette = new ArrayList<>();
		Map<BlockSpec, Integer> paletteIndex = new LinkedHashMap<>();
		List<Map<String, Object>> blocks = new ArrayList<>();
		for (int y = 0; y < size.y(); y++) {
			for (int z = 0; z < size.z(); z++) {
				for (int x = 0; x < size.x(); x++) {
					String key = key(new Vec(x, y, z));
					BlockSpec b = overrides.getOrDefault(key, grid.get(x, y, z));
					if (b == null) {
						continue; // structure void
					}
					Integer state = paletteIndex.get(b);
					if (state == null) {
						state = palette.size();
						palette.add(b);
						paletteIndex.put(b, state);
					}
					Map<String, Object> block = Nbt.compound();
					block.put("pos", Nbt.TagList.ints(x, y, z));
					block.put("state", state);
					Map<String, Object> nbt = blockNbt.get(key);
					if (nbt != null) {
						block.put("nbt", nbt);
					}
					blocks.add(block);
				}
			}
		}
		List<Map<String, Object>> paletteTags = new ArrayList<>();
		for (BlockSpec b : palette) {
			Map<String, Object> tag = Nbt.compound();
			tag.put("Name", b.name());
			if (!b.properties().isEmpty()) {
				Map<String, Object> props = Nbt.compound();
				b.properties().forEach(props::put);
				tag.put("Properties", props);
			}
			paletteTags.add(tag);
		}

		Map<String, Object> root = Nbt.compound();
		root.put("DataVersion", DATA_VERSION);
		root.put("size", Nbt.TagList.ints(size.x(), size.y(), size.z()));
		root.put("palette", Nbt.TagList.compounds(paletteTags));
		root.put("blocks", Nbt.TagList.compounds(blocks));
		root.put("entities", Nbt.TagList.compounds(List.of()));
		return root;
	}

	/**
	 * Single-element template pool JSON. Same element format as vanilla's own pools (identical in 26.2
	 * and 26.3): {@code single_pool_element} places the template's air too (clears terrain inside),
	 * no processors, rigid projection.
	 */
	public static String poolJson(Layout l) {
		return "{\n"
			+ "  \"fallback\": \"minecraft:empty\",\n"
			+ "  \"elements\": [\n"
			+ "    {\n"
			+ "      \"element\": {\n"
			+ "        \"element_type\": \"minecraft:single_pool_element\",\n"
			+ "        \"location\": \"burmaldaholic:" + l.templatePath() + "\",\n"
			+ "        \"processors\": \"minecraft:empty\",\n"
			+ "        \"projection\": \"rigid\"\n"
			+ "      },\n"
			+ "      \"weight\": 1\n"
			+ "    }\n"
			+ "  ]\n"
			+ "}\n";
	}

	/** Every generated file: pack-relative path → bytes. */
	public static Map<String, byte[]> files() {
		Map<String, byte[]> out = new LinkedHashMap<>();
		for (Layout l : Layouts.all().values()) {
			out.put(templateFile(l), Nbt.writeGzip(template(l)));
			out.put(poolFile(l), poolJson(l).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		return out;
	}

	private static String key(Vec v) {
		return v.x() + "," + v.y() + "," + v.z();
	}
}

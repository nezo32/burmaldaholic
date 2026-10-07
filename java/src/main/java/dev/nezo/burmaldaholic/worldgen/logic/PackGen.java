package dev.nezo.burmaldaholic.worldgen.logic;

import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Build-time generator of the v0.2 casino data pack (PURE, no Minecraft on the classpath; architecture doc
 * casino-buildings.md §3.7). Gradle task {@code generateCasinoPack} runs
 * {@code PackGen <java/build/generated/casino-pack>} and the directory is a resources source dir, so the jar and
 * every run task see the pack merged with the frozen v0.1 files.
 *
 * <pre>
 *   &lt;out&gt;/resourcepacks/casinos/data/burmaldaholic/…   templates, pools, structures, set, tags (→ jar)
 *   &lt;out&gt;/previews/manifest.json                        pieces + assemblies for render_structure.py (not in the jar)
 * </pre>
 * Output is byte-identical across runs; the output directory is cleaned first so removed pieces never linger.
 */
public final class PackGen {
	public static final String PREVIEWS = "previews";

	private PackGen() {}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			throw new IllegalArgumentException("usage: PackGen <output dir>");
		}
		Path out = Path.of(args[0]);
		Map<String, byte[]> files = files();
		clean(out.resolve("resourcepacks"));
		clean(out.resolve(PREVIEWS));
		for (Map.Entry<String, byte[]> f : files.entrySet()) {
			Path p = out.resolve(f.getKey());
			Files.createDirectories(p.getParent());
			Files.write(p, f.getValue());
		}
		System.out.println("[PackGen] " + CasinoPack.pieces().size() + " pieces, styles " + PackFiles.completeStyles(CasinoPack.layouts())
			+ (CasinoPack.placeholders() ? " (PLACEHOLDERS — not for release)" : "") + " → " + out);
	}

	/** Every output file, path relative to the output directory → bytes. */
	public static Map<String, byte[]> files() {
		Map<String, byte[]> out = new LinkedHashMap<>();
		List<Layout> layouts = CasinoPack.layouts();
		PackFiles.files(layouts).forEach((path, bytes) -> out.put(TemplateExporter.PACK_ROOT + "/" + path, bytes));
		out.put(PREVIEWS + "/manifest.json", manifest(CasinoPack.pieces()).getBytes(StandardCharsets.UTF_8));
		return out;
	}

	/**
	 * Preview manifest (§8.1): every piece (template, size, games) and every assembly with each piece's template
	 * origin and clockwise quarter turns (Minecraft's rotation around the template origin, see {@link Geometry}).
	 */
	public static String manifest(List<Piece> pieces) {
		StringBuilder sb = new StringBuilder("{\n");
		sb.append("  \"packRoot\": ").append(PackFiles.q(TemplateExporter.PACK_ROOT)).append(",\n");
		sb.append("  \"rotation\": \"clockwise quarter turns around the template origin: 1 = (x, z) -> (-z, x)\",\n");
		sb.append("  \"placeholders\": ").append(CasinoPack.placeholders()).append(",\n");
		sb.append("  \"pieces\": [");
		for (int i = 0; i < pieces.size(); i++) {
			Piece p = pieces.get(i);
			Layout l = p.layout();
			sb.append(i == 0 ? "\n" : ",\n").append("    {\"id\": ").append(PackFiles.q(l.id()))
				.append(", \"template\": ").append(PackFiles.q(PackFiles.NS + ":" + l.templatePath()))
				.append(", \"file\": ").append(PackFiles.q(TemplateExporter.PACK_ROOT + "/" + TemplateExporter.templateFile(l)))
				.append(", \"kind\": ").append(PackFiles.q(l.kind().id()))
				.append(", \"size\": ").append(vec(l.size()))
				.append(", \"games\": [");
			for (int g = 0; g < p.games().size(); g++) {
				Piece.GameSpot s = p.games().get(g);
				sb.append(g == 0 ? "" : ", ").append("{\"game\": ").append(PackFiles.q(s.game().name().toLowerCase(java.util.Locale.ROOT)))
					.append(", \"block\": ").append(PackFiles.q(s.game().blockId()))
					.append(", \"pos\": ").append(vec(s.pos()))
					.append(", \"playerSide\": ").append(PackFiles.q(s.playerSide().id())).append('}');
			}
			sb.append("]}");
		}
		sb.append(pieces.isEmpty() ? "],\n" : "\n  ],\n");
		List<Assembly.Result> assemblies = Assembly.all(pieces.stream().map(Piece::layout).toList());
		sb.append("  \"assemblies\": [");
		for (int i = 0; i < assemblies.size(); i++) {
			Assembly.Result r = assemblies.get(i);
			sb.append(i == 0 ? "\n" : ",\n").append("    {\"style\": ").append(PackFiles.q(r.style()))
				.append(", \"name\": ").append(PackFiles.q(r.name()))
				.append(", \"structure\": ").append(PackFiles.q(PackFiles.structureId(r.style())))
				.append(", \"problems\": ").append(r.problems().size())
				.append(", \"pieces\": [");
			for (int k = 0; k < r.pieces().size(); k++) {
				Assembly.Placed p = r.pieces().get(k);
				Geometry.Box b = p.box();
				sb.append(k == 0 ? "\n" : ",\n").append("      {\"id\": ").append(PackFiles.q(p.layout().id()))
					.append(", \"template\": ").append(PackFiles.q(PackFiles.NS + ":" + p.layout().templatePath()))
					.append(", \"origin\": ").append(vec(p.origin()))
					.append(", \"rotation\": ").append(p.turns())
					.append(", \"box\": [").append(b.minX()).append(", ").append(b.minY()).append(", ").append(b.minZ()).append(", ")
					.append(b.maxX()).append(", ").append(b.maxY()).append(", ").append(b.maxZ()).append("]}");
			}
			sb.append("\n    ]}");
		}
		sb.append(assemblies.isEmpty() ? "]\n" : "\n  ]\n");
		return sb.append("}\n").toString();
	}

	private static String vec(Vec v) {
		return "[" + v.x() + ", " + v.y() + ", " + v.z() + "]";
	}

	private static void clean(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}
}

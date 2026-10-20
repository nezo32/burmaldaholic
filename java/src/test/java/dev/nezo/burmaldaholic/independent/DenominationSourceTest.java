package dev.nezo.burmaldaholic.independent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.chips.ChipMath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Lane HR guard (owner playtest E4): the chip values exist in ONE place, {@link ChipMath#DENOMINATIONS} (Java) and
 * {@code CHIP_DENOMS} in {@code tools/assets/lib/palette.mjs} (the art generator). Before, ~12 copies of
 * {@code {1, 5, 25, 100, 500}} lived in screens and renderers, so the UI offered 1000 / 5000 chips no item backed.
 *
 * <p>A source scan, like {@link RawInputGuardTest}: any three consecutive chip values written as a list ({@code 5, 25, 100}
 * or {@code 500, 100, 25}, with {@code _} / {@code L} allowed) outside the two sources fails. Verified non-chip lists
 * (e.g. slot jackpot ladders) go into {@link #ALLOWED} with the reason.
 */
class DenominationSourceTest {
	private static final List<String> JAVA_SOURCES = List.of("src/main/java", "src/client/java", "src/gametest/java");
	private static final String SOURCE = "src/main/java/dev/nezo/burmaldaholic/core/chips/ChipMath.java";
	private static final String JS_SOURCE = "tools/assets/lib/palette.mjs";

	/** {@code <path relative to the repo> | <trimmed line>} → why it is not a chip list. */
	private static final Map<String, String> ALLOWED = Map.of(
		"java/src/main/java/dev/nezo/burmaldaholic/core/config/sections/SlotsV2Config.java | jackpot = new Jackpot(100, new int[] {10, 25, 100, 500}, new double[] {0.004, 0.003, 0.002, 0.001}, new int[] {10, 25, 100, 250});",
		"slot jackpot seed multipliers",
		"java/src/main/java/dev/nezo/burmaldaholic/games/slots/v2/logic/SlotDefaults.java | 100, new int[] {10, 25, 100, 500}, new int[] {4000, 3000, 2000, 1000}, new int[] {10, 25, 100, 250});",
		"slot jackpot seed multipliers");

	static Pattern triples() {
		List<String> alts = new ArrayList<>();
		int[] d = ChipMath.DENOMINATIONS;
		for (int i = 0; i + 2 < d.length; i++) {
			alts.add(num(d[i + 2]) + SEP + num(d[i + 1]) + SEP + num(d[i])); // ascending
			alts.add(num(d[i]) + SEP + num(d[i + 1]) + SEP + num(d[i + 2])); // descending
		}
		return Pattern.compile("(?<![\\w.])(?:" + String.join("|", alts) + ")(?![\\w.])");
	}

	private static final String SEP = "\\s*,\\s*";

	/** A number as written in source: {@code 25000}, {@code 25_000}, {@code 25000L}. */
	private static String num(int v) {
		String s = Integer.toString(v);
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			if (i > 0 && (s.length() - i) % 3 == 0) b.append("_?");
			b.append(s.charAt(i));
		}
		return b + "[lL]?";
	}

	@Test
	void chipValuesAreListedOnlyInChipMathAndThePalette() throws IOException {
		Path java = Path.of(System.getProperty("burmaldaholic.projectDir", ".")).toAbsolutePath().normalize();
		Path repo = java.getParent();
		Pattern p = triples();
		List<String> hits = new ArrayList<>();
		Set<String> used = new HashSet<>();
		List<Path> files = new ArrayList<>();
		for (String set : JAVA_SOURCES) collect(java.resolve(set), ".java", files);
		collect(repo.resolve("tools/assets"), ".mjs", files);
		assertTrue(files.size() > 100, "scanned too few files (" + files.size() + "): wrong project dir?");
		for (Path f : files) {
			String rel = repo.relativize(f).toString().replace('\\', '/');
			if (rel.equals("java/" + SOURCE) || rel.equals(JS_SOURCE) || rel.endsWith(".test.mjs")) continue;
			List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				String code = line.replaceAll("//.*$", "");
				if (code.trim().startsWith("*")) continue; // javadoc prose
				if (!p.matcher(code).find()) continue;
				String key = rel + " | " + line.trim();
				if (ALLOWED.containsKey(key)) used.add(key);
				else hits.add(rel + ":" + (i + 1) + "  " + line.trim());
			}
		}
		assertTrue(hits.isEmpty(), hits.size() + " hard-coded chip denomination list(s); read ChipMath.DENOMINATIONS / ascending() (Java) or"
			+ " CHIP_DENOMS (tools/assets/lib/palette.mjs) instead, or allowlist a verified non-chip list:\n  " + String.join("\n  ", hits));
		Set<String> stale = new HashSet<>(ALLOWED.keySet());
		stale.removeAll(used);
		assertTrue(stale.isEmpty(), "stale allowlist entries:\n  " + String.join("\n  ", stale));
	}

	@Test
	void theScanCatchesTheOldLists() {
		Pattern p = triples();
		for (String bad : List.of("int[] DENOMS = {1, 5, 25, 100, 500};", "{500, 100, 25, 5, 1}", "[1, 5, 25, 100, 500, 1000, 5000]",
			"{1_000, 5_000, 25_000}", "long[] d = {500L, 100L, 25L};")) {
			assertTrue(p.matcher(bad).find(), bad);
		}
		for (String ok : List.of("{10, 25, 100, 250}", "new int[] {4000, 3000, 2000, 1000}", "x = 1.5, 25, 100", "{2, 5, 25}")) {
			assertTrue(!p.matcher(ok).find(), ok);
		}
	}

	private static void collect(Path dir, String ext, List<Path> out) throws IOException {
		if (!Files.isDirectory(dir)) return;
		try (Stream<Path> s = Files.walk(dir)) {
			s.filter(f -> f.toString().endsWith(ext) && !f.toString().contains("node_modules")).sorted().forEach(out::add);
		}
	}
}

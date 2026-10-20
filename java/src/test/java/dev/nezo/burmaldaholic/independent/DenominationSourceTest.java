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
import java.util.regex.Matcher;
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
			for (Hit h : scan(lines)) {
				String key = rel + " | " + lines.get(h.line() - 1).trim();
				if (ALLOWED.containsKey(key)) used.add(key);
				else hits.add(rel + ":" + h.line() + "  [" + h.what() + "]  " + lines.get(h.line() - 1).trim());
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
		for (String bad : List.of("int[] DENOMS = {1, 5, 25, 100, 500};", "{500, 100, 25, 5, 1}", "[1, 5, 25, 100, 500, 1000, 5000]",
			"{1_000, 5_000, 25_000}", "long[] d = {500L, 100L, 25L};", "List.of(1L, 5L, 25L)", "{100, 1, 25}", "{1000, 5000}",
			"{0, 500, 100, 25, 5, 1}", "int[] d = {\n  500,\n  100,\n  25\n};",
			"return amount >= 500 ? 500 : amount >= 100 ? 100 : 1;", "a >= 5000\n ? 5000\n : a >= 1000 ? 1000 : 1;")) {
			assertTrue(!scan(List.of(bad.split("\n"))).isEmpty(), "not caught: " + bad);
		}
		for (String ok : List.of("{10, 25, 100, 250}", "new int[] {4000, 3000, 2000, 1000}", "x = 1.5, 25, 100", "{2, 5, 25}", "{1, 5}", "t >= 500 ? 500 : t", "// {1, 5, 25, 100}", "{25, 100, 7}")) {
			assertTrue(scan(List.of(ok.split("\n"))).isEmpty(), "false positive: " + ok);
		}
	}

	record Hit(int line, String what) {}

	private static final Pattern LIST = Pattern.compile("[\\[{(]([-\\d_lL,\\s]+)[\\]})]");
	private static final Pattern TERNARY = Pattern.compile(">=?\\s*(\\d[\\d_]*)[lL]?\\s*\\?\\s*(\\d[\\d_]*)[lL]?\\b");

	/**
	 * The hits of one file (comments stripped, lines joined so a list split over lines is one list): any written list of
	 * three or more distinct chip values in any order (a {@code 0} may join them), two high chips ({@code {1000, 5000}}),
	 * the ordered triple inside a longer list ({@link #triples()}), and a ternary ladder over two or more chip values
	 * ({@code a >= 500 ? 500 : a >= 100 ? 100 : 1}, what BetControl had).
	 */
	static List<Hit> scan(List<String> lines) {
		StringBuilder text = new StringBuilder();
		int[] lineOf;
		boolean block = false;
		List<Integer> starts = new ArrayList<>();
		for (String raw : lines) {
			starts.add(text.length());
			StringBuilder code = new StringBuilder();
			int i = 0;
			while (i < raw.length()) {
				if (block) {
					int end = raw.indexOf("*/", i);
					if (end < 0) break;
					block = false;
					i = end + 2;
					continue;
				}
				if (raw.startsWith("//", i)) break;
				if (raw.startsWith("/*", i)) {
					block = true;
					i += 2;
					continue;
				}
				code.append(raw.charAt(i++));
			}
			String c = code.toString();
			if (c.trim().startsWith("*")) c = ""; // javadoc prose
			text.append(c).append('\n');
		}
		lineOf = starts.stream().mapToInt(Integer::intValue).toArray();
		String all = text.toString();
		Set<Long> denoms = new HashSet<>();
		for (int d : ChipMath.DENOMINATIONS) denoms.add((long) d);
		List<Hit> out = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		java.util.function.BiConsumer<Integer, String> add = (off, what) -> {
			int ln = line(lineOf, off);
			if (seen.add(ln)) out.add(new Hit(ln, what));
		};
		Matcher m = LIST.matcher(all);
		while (m.find()) {
			String[] parts = m.group(1).split(",");
			Set<Long> chips = new HashSet<>();
			boolean onlyChips = parts.length >= 2;
			for (String part : parts) {
				String n = part.trim().replace("_", "").replaceAll("[lL]$", "");
				if (n.isEmpty()) continue;
				long v;
				try {
					v = Long.parseLong(n);
				} catch (NumberFormatException e) {
					onlyChips = false;
					break;
				}
				if (denoms.contains(v)) chips.add(v);
				else if (v != 0) onlyChips = false;
			}
			if (!onlyChips) continue;
			boolean highPair = chips.size() == 2 && chips.stream().allMatch(v -> v >= 1000);
			if (chips.size() >= 3 || highPair) add.accept(m.start(), "list of chip values");
		}
		Matcher t = triples().matcher(all);
		while (t.find()) add.accept(t.start(), "chip values in order");
		for (String stmt : splitKeep(all)) {
			Matcher q = TERNARY.matcher(stmt);
			Set<Long> ladder = new HashSet<>();
			int first = -1;
			while (q.find()) {
				long x = Long.parseLong(q.group(1).replace("_", ""));
				if (x == Long.parseLong(q.group(2).replace("_", "")) && denoms.contains(x)) {
					ladder.add(x);
					if (first < 0) first = q.start();
				}
			}
			if (ladder.size() >= 2) add.accept(stmtStart(all, stmt) + first, "ternary ladder of chip values");
		}
		out.sort(java.util.Comparator.comparingInt(Hit::line));
		return out;
	}

	private static List<String> splitKeep(String all) {
		return List.of(all.split("(?<=;)"));
	}

	private static int stmtStart(String all, String stmt) {
		return Math.max(0, all.indexOf(stmt));
	}

	private static int line(int[] starts, int offset) {
		int ln = 1;
		for (int i = 0; i < starts.length; i++) if (starts[i] <= offset) ln = i + 1;
		return ln;
	}

	private static void collect(Path dir, String ext, List<Path> out) throws IOException {
		if (!Files.isDirectory(dir)) return;
		try (Stream<Path> s = Files.walk(dir)) {
			s.filter(f -> f.toString().endsWith(ext) && !f.toString().contains("node_modules")).sorted().forEach(out::add);
		}
	}
}

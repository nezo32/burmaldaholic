package dev.nezo.burmaldaholic.independent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * v0.1.3 regression guard: no raw mouse-button / key-code numbers outside {@code client/input/Inputs.java}.
 *
 * <p>Minecraft 26.3 (SDL3) renumbered every mouse button and key code; the release jar is compiled against 26.2 and
 * javac inlines {@code static final} constants, so {@code button == 0}, {@code InputConstants.KEY_B},
 * {@code GLFW.GLFW_KEY_SPACE}, {@code event.key() == 32} etc. all carry 26.2 numbers and silently break on 26.3. Use
 * {@code Inputs.MOUSE_LEFT / isLeft(..) / KEY_*} or vanilla helpers ({@code KeyEvent.isEscape()/isSelection()}).
 *
 * <p>The scan is a pragmatic regex over {@code src/client}, {@code src/main} and {@code src/gametest}. A verified false
 * positive goes into {@code config/input-literal-allowlist.txt} as {@code <path relative to java/> | <trimmed line>}.
 */
class RawInputGuardTest {
	private static final String INPUTS = "src/client/java/dev/nezo/burmaldaholic/client/input/Inputs.java";
	private static final List<String> SOURCES = List.of("src/client/java", "src/main/java", "src/gametest/java");

	/** Compared value: a digit 0..9, or 32 and above (10..31 are left out: they are never button / key codes). */
	private static final String CODE = "(?:[0-9]|3[2-9]|[4-9][0-9]|[1-9][0-9]{2,})\\b";
	/**
	 * An input-ish operand: {@code button}, {@code button()}, {@code key}, {@code keyCode}, {@code key()}, {@code input()},
	 * {@code scancode}.
	 */
	private static final String OPERAND = "(?:\\bbutton\\b(?:\\(\\))?|\\bkey\\b(?!\\s*\\()|\\bkeyCode\\b|\\bscan[cC]ode\\b(?:\\(\\))?|\\.key\\(\\)|\\.input\\(\\))";
	/** A comparison: {@code == != < <= > >=} (not {@code ->}, {@code <<}, {@code >>}). */
	private static final String CMP = "(?:[!=]=|(?<![<-])<(?!<)=?|(?<![->])>(?!>)=?)";
	/** {@code int b = event.button();}: a local alias of a raw code; its comparisons are checked like the operand's. */
	static final Pattern ALIAS = Pattern.compile("\\bint\\s+(\\w+)\\s*=\\s*\\w+\\.(?:key|button|input)\\(\\)");

	private static final List<Rule> RULES = List.of(
		new Rule("GLFW key / button constant", Pattern.compile("\\bGLFW\\.GLFW_(?:KEY|MOUSE_BUTTON)_")),
		new Rule("InputConstants key / button constant (inlined at compile time)", Pattern.compile("\\bInputConstants\\.(?:KEY|MOUSE_BUTTON)_")),
		new Rule("button / key compared with a number", Pattern.compile(OPERAND + "\\s*" + CMP + "\\s*-?" + CODE)),
		new Rule("number compared with a button / key", Pattern.compile("(?<![\\w.])-?" + CODE + "\\s*" + CMP + "\\s*" + OPERAND)),
		new Rule("switch on a button / key code", Pattern.compile("\\bswitch\\s*\\(\\s*(?:[\\w.]+\\.)?(?:button\\b(?:\\(\\))?|keyCode\\b|key\\(\\)|input\\(\\))\\s*\\)")),
		new Rule("home-made key / button constant", Pattern.compile("\\bstatic\\s+final\\s+int\\s+(?:GLFW_)?(?:KEY|MOUSE_BUTTON)_\\w*\\s*=\\s*-?\\d")),
		new Rule("GameTest input with a numeric button / key", Pattern.compile("\\b(?:press|hold|release)(?:Mouse|Key)\\s*\\(\\s*-?\\d")));

	private record Rule(String what, Pattern pattern) {}

	@Test
	void noRawInputNumbersOutsideInputs() throws IOException {
		Path root = Path.of(System.getProperty("burmaldaholic.projectDir", "."));
		Set<String> allowed = allowlist(root.resolve("config/input-literal-allowlist.txt"));
		Set<String> used = new HashSet<>();
		List<String> hits = new ArrayList<>();
		for (String set : SOURCES) {
			Path dir = root.resolve(set);
			if (!Files.isDirectory(dir)) {
				continue;
			}
			List<Path> files;
			try (Stream<Path> s = Files.walk(dir)) {
				files = s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
			}
			for (Path f : files) {
				String rel = root.relativize(f).toString().replace('\\', '/');
				if (rel.equals(INPUTS)) {
					continue;
				}
				List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
				for (Hit h : scan(lines)) {
					String key = rel + " | " + h.line().trim();
					if (allowed.contains(key)) {
						used.add(key);
					} else {
						hits.add(rel + ":" + h.number() + "  [" + h.what() + "]  " + h.line().trim());
					}
				}
			}
		}
		assertTrue(hits.isEmpty(), hits.size() + " raw mouse-button / key-code use(s); use dev.nezo.burmaldaholic.client.input.Inputs"
			+ " (or KeyEvent.isEscape()/isSelection()...), or allowlist a VERIFIED false positive in config/input-literal-allowlist.txt:\n  "
			+ String.join("\n  ", hits));
		Set<String> stale = new HashSet<>(allowed);
		stale.removeAll(used);
		assertTrue(stale.isEmpty(), "stale entries in config/input-literal-allowlist.txt (no longer match anything):\n  "
			+ String.join("\n  ", stale));
	}

	record Hit(int number, String what, String line) {}

	/** The rule hits of one file's lines (comments and string literals ignored; aliases of raw codes followed). */
	static List<Hit> scan(List<String> lines) {
		List<Hit> out = new ArrayList<>();
		List<String> codes = new ArrayList<>();
		boolean inBlock = false;
		Set<String> aliases = new HashSet<>();
		for (String raw : lines) {
			StringBuilder code = new StringBuilder();
			inBlock = stripComments(raw, inBlock, code);
			codes.add(code.toString());
			Matcher m = ALIAS.matcher(code);
			while (m.find()) aliases.add(m.group(1));
		}
		List<Rule> rules = new ArrayList<>(RULES);
		if (!aliases.isEmpty()) {
			String alias = "\\b(?:" + String.join("|", aliases) + ")\\b";
			rules.add(new Rule("alias of a raw button / key compared with a number", Pattern.compile(alias + "\\s*" + CMP + "\\s*-?" + CODE)));
			rules.add(new Rule("number compared with an alias of a raw button / key", Pattern.compile("(?<![\\w.])-?" + CODE + "\\s*" + CMP + "\\s*" + alias)));
			rules.add(new Rule("switch on an alias of a raw button / key", Pattern.compile("\\bswitch\\s*\\(\\s*" + alias + "\\s*\\)")));
		}
		for (int i = 0; i < codes.size(); i++) {
			for (Rule r : rules) {
				if (r.pattern().matcher(codes.get(i)).find()) {
					out.add(new Hit(i + 1, r.what(), lines.get(i)));
					break;
				}
			}
		}
		return out;
	}

	@Test
	void rulesCatchTheKnownShapes() {
		for (String bad : List.of("if (key == 32) {", "if (button < 2) {", "if (event.key() >= 48 && x) {", "return 0 != button;",
			"if (keyCode > 64) {", "if (GLFW.GLFW_KEY_SPACE == k) {")) {
			assertTrue(!scan(List.of(bad)).isEmpty(), "missed: " + bad);
		}
		assertTrue(!scan(List.of("int b = event.button();", "if (b == 1) {")).isEmpty(), "alias compare");
		assertTrue(!scan(List.of("int k = e.key();", "switch (k) {")).isEmpty(), "alias switch");
		for (String ok : List.of("Map<String, Integer> key = new HashMap<>();", "list.forEach(button -> 0);", "if (key == null) {",
			"x = key(3);", "int b = event.button();", "if (Inputs.isLeft(button)) {", "long v = a << 3;", "// key == 32")) {
			assertTrue(scan(List.of(ok)).isEmpty(), "false positive: " + ok + " " + scan(List.of(ok)));
		}
	}

	/** Appends the code part of {@code line} (string literals blanked, comments dropped); returns whether a block comment is still open. */
	private static boolean stripComments(String line, boolean inBlock, StringBuilder out) {
		int i = 0;
		while (i < line.length()) {
			if (inBlock) {
				int end = line.indexOf("*/", i);
				if (end < 0) {
					return true;
				}
				i = end + 2;
				inBlock = false;
				continue;
			}
			char c = line.charAt(i);
			if (c == '"' || c == '\'') {
				int j = i + 1;
				while (j < line.length() && line.charAt(j) != c) {
					j += line.charAt(j) == '\\' ? 2 : 1;
				}
				out.append(c).append(c);
				i = j + 1;
			} else if (line.startsWith("//", i)) {
				return false;
			} else if (line.startsWith("/*", i)) {
				inBlock = true;
				i += 2;
			} else {
				out.append(c);
				i++;
			}
		}
		return inBlock;
	}

	private static Set<String> allowlist(Path file) throws IOException {
		Set<String> out = new HashSet<>();
		if (!Files.exists(file)) {
			return out;
		}
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			String t = line.strip();
			if (t.isEmpty() || t.startsWith("#")) {
				continue;
			}
			Matcher m = Pattern.compile("^(\\S+)\\s*\\|\\s*(.+)$").matcher(t);
			if (!m.matches()) {
				throw new IllegalStateException("bad allowlist line (want '<path> | <trimmed source line>'): " + line);
			}
			out.add(m.group(1) + " | " + m.group(2));
		}
		return out;
	}
}

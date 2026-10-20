package dev.nezo.burmaldaholic.core.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/**
 * Owner playtest B2: the big-win chat line rendered as tofu because the glyph font sat on the ROOT and the
 * translated text inherited it. Walks the component tree (siblings and translatable arguments) with the
 * effective, inherited font and asserts that only glyph nodes (Private Use Area code points) use our glyph font.
 */
class GlyphFontScopeTest {
	/** True for U+E000–U+F8FF only text: our bitmap glyphs. */
	static boolean isGlyphText(String s) {
		if (s.isEmpty()) return false;
		return s.codePoints().allMatch(cp -> cp >= 0xE000 && cp <= 0xF8FF);
	}

	/**
	 * Violations of "only glyph nodes carry {@code ours}": text nodes in our font that are not glyphs, glyph
	 * nodes not in our font, and translatable nodes (words) in our font.
	 */
	static List<String> violations(Component root, FontDescription ours) {
		List<String> out = new ArrayList<>();
		walk(root, Style.EMPTY, ours, "root", out);
		return out;
	}

	private static void walk(Component c, Style inherited, FontDescription ours, String path, List<String> out) {
		// the renderer's rule: a node's unset style fields come from its parent
		Style style = c.getStyle().applyTo(inherited);
		boolean inOurs = ours.equals(style.getFont());
		if (c.getContents() instanceof PlainTextContents plain) {
			String text = plain.text();
			if (isGlyphText(text) && !inOurs) out.add(path + ": glyph '" + text + "' not in " + ours);
			if (!text.isEmpty() && !isGlyphText(text) && inOurs) out.add(path + ": text '" + text + "' inherits " + ours);
		} else if (c.getContents() instanceof TranslatableContents tr) {
			if (inOurs) out.add(path + ": translatable " + tr.getKey() + " inherits " + ours);
			Object[] args = tr.getArgs();
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Component a) walk(a, style, ours, path + "/" + tr.getKey() + "[" + i + "]", out);
			}
		}
		List<Component> sibs = c.getSiblings();
		for (int i = 0; i < sibs.size(); i++) walk(sibs.get(i), style, ours, path + "/" + i, out);
	}

	@Test
	void bigWinLineKeepsTheGlyphFontOnTheGlyphOnly() {
		Component line = BigWinBroadcast.line(Component.literal("Steve"), 5000, "roulette");
		List<String> v = violations(line, BigWinBroadcast.GLYPHS);
		assertTrue(v.isEmpty(), String.join("\n", v));
		// the chip glyph is still there, in our font
		long glyphs = line.getSiblings().stream()
			.filter(s -> s.getContents() instanceof PlainTextContents p && isGlyphText(p.text()))
			.filter(s -> BigWinBroadcast.GLYPHS.equals(s.getStyle().getFont())).count();
		assertEquals(1, glyphs, "exactly one chip glyph in the glyph font");
	}

	@Test
	void walkerCatchesTheOldParentFontPattern() {
		// the pre-fix shape: glyph as the root with the words appended -> must be flagged
		Component bad = Component.literal(BigWinBroadcast.CHIP_GLYPH).withStyle(s -> s.withFont(BigWinBroadcast.GLYPHS))
			.append(Component.literal(" "))
			.append(Component.translatable("msg.burmaldaholic.core.big_win", Component.literal("Steve"), Component.literal("5"), Component.literal("x")));
		assertTrue(violations(bad, BigWinBroadcast.GLYPHS).size() >= 2);
	}

	/** {@code x.withStyle(s -> s.withFont(F)).append(text)}: the font set on a parent that gets text appended. */
	private static final Pattern FONT_ON_PARENT = Pattern.compile("withFont\\([^()]*\\)\\)\\s*\\.append\\(");

	@Test
	void noSourceSetsAFontOnAParentWithAppendedText() throws IOException {
		Path root = Path.of(System.getProperty("burmaldaholic.projectDir", "."));
		List<String> hits = new ArrayList<>();
		for (String set : List.of("src/main/java", "src/client/java")) {
			List<Path> files;
			try (Stream<Path> st = Files.walk(root.resolve(set))) {
				files = st.filter(p -> p.toString().endsWith(".java")).sorted().toList();
			}
			for (Path f : files) {
				List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
				for (int i = 0; i < lines.size(); i++) {
					if (FONT_ON_PARENT.matcher(lines.get(i)).find()) hits.add(root.relativize(f) + ":" + (i + 1) + ": " + lines.get(i).trim());
				}
			}
		}
		assertTrue(hits.isEmpty(), "font on a parent component (children inherit it -> tofu); make the glyph a sibling:\n" + String.join("\n", hits));
		assertTrue(FONT_ON_PARENT.matcher("Texts.raw(G).withStyle(s -> s.withFont(GLYPHS)).append(Texts.raw(\" \"))").find(), "pattern self-check");
	}
}

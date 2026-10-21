package dev.nezo.burmaldaholic.games.slots.v2.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.DockLayout;
import dev.nezo.burmaldaholic.core.ui.v2.FlowCheck;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.ViewportPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * T-SLT D3: the slot Dock fits the frame at 427 × 240, 480 × 270, 640 × 360 and 960 × 540, EN and RU. The widths are
 * measured as the chrome measures them ({@code V2Button.width}, {@code Stepper.measure}, the status's wanted width) with a
 * vanilla-like advance table, the Dock laid out by {@link DockLayout}, and folded into More by the same
 * {@link SlotDock#collapse} the screen runs. Every control (stepper, actions or More, primary, status) is inside the
 * frame and the Dock row and overlaps no other; the More menu's entries are inside the frame, above the Dock, and
 * overlap neither each other nor a Dock control. Worst-case labels: a 5 000 bet, the End buy price, the Join Showdown
 * entry, Stop Auto.
 */
class SlotDockFitTest {
	/** {window w, h, GUI scale} → the four layout sizes (as {@code PlayFieldUnityTest.CELLS}). */
	static final int[][] CELLS = {{1280, 720, 3}, {960, 540, 2}, {1280, 720, 2}, {1920, 1080, 2}};
	static final int[][] FRAMES = {{427, 240}, {480, 270}, {640, 360}, {960, 540}};

	/** A vanilla-like advance width (as {@code StatusFitTest.FONT}): 6 px, space 4, narrow glyphs 2–5. */
	static final ToIntFunction<String> FONT = s -> {
		int w = 0;
		for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
			int c = s.codePointAt(i);
			w += c == ' ' ? 4 : "i!.,:;|'".indexOf(c) >= 0 ? 2 : "l`".indexOf(c) >= 0 ? 3 : "tI[]".indexOf(c) >= 0 ? 4 : "fk<>\"()*{}".indexOf(c) >= 0 ? 5 : 6;
		}
		return w;
	};
	/** V2Button paddings and the Stepper gap (client constants; mirrored here). */
	static final int PAD = 8, HINT_GAP = 5, HINT_PAD = 3, STEPPER_GAP = 2;

	static int button(String label, String hint) {
		int w = FONT.applyAsInt(label) + 2 * PAD;
		if (hint != null) w += HINT_GAP + FONT.applyAsInt(hint) + 2 * HINT_PAD;
		return w;
	}

	private static Map<String, String> lang(String code) throws IOException {
		Path dir = langDir();
		Map<String, String> out = new HashMap<>();
		for (String module : new String[] {"core", "slots", "pvp"}) {
			JsonObject o = JsonParser.parseString(Files.readString(dir.resolve(module + "/" + code + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
			for (var e : o.entrySet()) out.put(e.getKey(), e.getValue().getAsString());
		}
		return out;
	}

	private static Path langDir() {
		String root = System.getProperty("burmaldaholic.projectDir", ".");
		for (Path p : new Path[] {Path.of(root, "src/main/lang"), Path.of("src/main/lang"), Path.of("java/src/main/lang")}) {
			if (Files.isDirectory(p)) return p;
		}
		throw new IllegalStateException("src/main/lang not found");
	}

	private static String tr(Map<String, String> l, Gate g) {
		String s = l.get(g.labelKey());
		if (s == null) throw new AssertionError("missing " + g.labelKey());
		Object[] args = g.labelArgs() == null ? new Object[0] : g.labelArgs();
		for (int i = 0; i < args.length; i++) {
			Object a = args[i];
			String v = a instanceof Number n ? group(n.longValue()) : String.valueOf(a);
			s = s.replace("%" + (i + 1) + "$s", v);
		}
		return s.replace("%s", args.length > 0 ? String.valueOf(args[0]) : "");
	}

	/** "536 000": grouped like {@code Texts.number}, plus a chip glyph's width of slack. */
	private static String group(long n) {
		String s = Long.toString(n);
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
			b.append(s.charAt(i));
		}
		return b + "WW";
	}

	private static SlotView view(boolean auto, Gate showdown) {
		return new SlotView(true, false, new String[0], auto, auto, 5_000, 4, 9, 10_000_000, 5, true, 536_000, true, true, true, showdown);
	}

	@Test
	void everyDockControlFitsAtEverySizeInEnglishAndRussian() throws IOException {
		Gate join = Gate.on(SlotFlow.SHOWDOWN, "gui.burmaldaholic.pvp.slots.join", 5_000L, 3, 8);
		Gate title = Gate.on(SlotFlow.SHOWDOWN, "gui.burmaldaholic.pvp.slots.title");
		List<String> problems = new ArrayList<>();
		Map<String, Boolean> compactAt = new LinkedHashMap<>();
		for (String code : new String[] {"en_us", "ru_ru"}) {
			Map<String, String> l = lang(code);
			String space = code.equals("ru_ru") ? "Пробел" : "Space";
			for (int c = 0; c < CELLS.length; c++) {
				TableGrid g = new TableGrid(ViewportPolicy.of(CELLS[c][0], CELLS[c][1], CELLS[c][2]));
				String at = code + " " + g.screen.w() + "x" + g.screen.h();
				assertEquals(FRAMES[c][0], g.screen.w(), at);
				assertEquals(FRAMES[c][1], g.screen.h(), at);
				for (boolean auto : new boolean[] {false, true}) {
					for (Gate sd : new Gate[] {title, join}) {
						String tag = at + (auto ? " autoplay" : " idle") + " " + sd.labelKey().substring(sd.labelKey().lastIndexOf('.') + 1);
						boolean compact = fit(g, l, space, view(auto, sd), tag, problems);
						compactAt.merge(at, compact, Boolean::logicalOr);
					}
				}
			}
		}
		assertEquals(List.of(), problems);
		// the full ActionRow where it fits: 960 x 540 in both languages; the narrow frames fold into More
		assertFalse(compactAt.get("en_us 960x540"), "960x540 EN keeps the full ActionRow");
		assertFalse(compactAt.get("ru_ru 960x540"), "960x540 RU keeps the full ActionRow");
		assertTrue(compactAt.get("en_us 427x240") && compactAt.get("ru_ru 427x240"), "427x240 folds into More (D3)");
	}

	/** Lays the Dock out as the screen does; returns whether it folded into More. */
	private static boolean fit(TableGrid g, Map<String, String> l, String space, SlotView v, String tag, List<String> problems) {
		Gate primary = SlotFlow.INSTANCE.primary(v);
		int pw = button(tr(l, primary), space);
		int pc = button(tr(l, primary), null);
		int stepperW = 2 * g.tier.buttonH + 2 * STEPPER_GAP + Math.max(FONT.applyAsInt(group(v.bet())), FONT.applyAsInt("888 888")) + 8;
		int statusW = Math.min(g.dock.w() * 2 / 5, FONT.applyAsInt(tr(l, Gate.on("", "gui.burmaldaholic.slots.bet_coin", v.coin(), 1_000L))));
		DockLayout.Dock d = layout(g, l, SlotFlow.INSTANCE.actions(v), stepperW, pw, pc, statusW);
		boolean compact = SlotDock.collapse(g, d);
		List<Gate> acts = SlotFlow.INSTANCE.actions(v);
		if (compact) {
			acts = SlotFlow.COMPACT.actions(v);
			assertEquals(List.of(), FlowCheck.problems(SlotFlow.COMPACT, v), tag);
			assertEquals(1, acts.size(), tag + ": More only");
			d = layout(g, l, acts, stepperW, pw, pc, statusW);
			if (SlotDock.collapse(g, d)) problems.add(tag + ": the compact Dock still overflows by " + d.overflow());
		}
		Map<String, Box> boxes = new LinkedHashMap<>();
		boxes.put("stepper", d.stepper());
		for (int i = 0; i < acts.size(); i++) boxes.put("action." + acts.get(i).action(), i < d.actions().size() ? d.actions().get(i) : Box.EMPTY);
		boxes.put("primary", d.primary());
		boxes.put("status", d.status());
		check(g, boxes, g.dock, tag, problems);
		if (compact) {
			List<Gate> sec = SlotFlow.secondary(v);
			int[] w = new int[sec.size()];
			for (int i = 0; i < w.length; i++) w[i] = button(tr(l, sec.get(i)), null);
			List<Box> menu = SlotDock.menu(g, d.actions().get(0), w);
			for (int i = 0; i < menu.size(); i++) {
				Box m = menu.get(i);
				String id = "more." + sec.get(i).action();
				if (!m.inside(g.screen)) problems.add(tag + ": " + id + " " + m + " outside the frame");
				if (m.bottom() > g.dock.y()) problems.add(tag + ": " + id + " " + m + " reaches into the Dock " + g.dock);
				if (m.y() < g.play.y()) problems.add(tag + ": " + id + " " + m + " above the PlayArea " + g.play);
				if (m.w() < w[i]) problems.add(tag + ": " + id + " label does not fit (" + m.w() + " < " + w[i] + ")");
				for (int j = i + 1; j < menu.size(); j++) if (m.intersects(menu.get(j))) problems.add(tag + ": " + id + " overlaps more." + sec.get(j).action());
				for (var e : boxes.entrySet()) if (!e.getKey().equals("status") && m.intersects(e.getValue())) problems.add(tag + ": " + id + " overlaps " + e.getKey());
			}
		}
		return compact;
	}

	private static DockLayout.Dock layout(TableGrid g, Map<String, String> l, List<Gate> acts, int stepperW, int pw, int pc, int statusW) {
		int[] aw = new int[acts.size()];
		int[] ac = new int[acts.size()];
		for (int i = 0; i < aw.length; i++) {
			String a = acts.get(i).action();
			String key = a.equals(SlotFlow.AUTO) ? "A" : a.equals(SlotFlow.TURBO) ? "T" : null;
			aw[i] = button(tr(l, acts.get(i)), key);
			ac[i] = button(tr(l, acts.get(i)), null);
		}
		return DockLayout.layout(g, DockLayout.Spec.actions(aw, ac, stepperW, pw, pc).withStatus(statusW));
	}

	private static void check(TableGrid g, Map<String, Box> boxes, Box dock, String tag, List<String> problems) {
		List<String> ids = new ArrayList<>(boxes.keySet());
		for (int i = 0; i < ids.size(); i++) {
			Box b = boxes.get(ids.get(i));
			if (b.isEmpty()) {
				problems.add(tag + ": " + ids.get(i) + " missing");
				continue;
			}
			if (!b.inside(g.screen)) problems.add(tag + ": " + ids.get(i) + " " + b + " outside the frame " + g.screen);
			if (!b.inside(dock)) problems.add(tag + ": " + ids.get(i) + " " + b + " outside the Dock " + dock);
			for (int j = i + 1; j < ids.size(); j++) {
				if (b.intersects(boxes.get(ids.get(j)))) problems.add(tag + ": " + ids.get(i) + " " + b + " overlaps " + ids.get(j) + " " + boxes.get(ids.get(j)));
			}
		}
	}
}

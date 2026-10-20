package dev.nezo.burmaldaholic.games.poker.present;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.DockLayout;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * T-K2 D4 (release blocker): every control of the poker Dock lies inside the screen and no two overlap, for each Dock
 * the table shows (stake pick, buy-in, a turn, seated between hands), EN and RU, at 427 × 240, 480 × 270, 640 × 360 and
 * 960 × 540. The widths are the kit's ({@code V2Button.width}: label + 2 × 8, key plate + 5 + 2 × 3; the stepper
 * 2 × button height + 2 × 2 + its value) with the vanilla-like font of {@code StatusFitTest}; the stake pick follows
 * {@code PokerScreen.refresh}: full labels ("Micro (1/2)"), the short ones ("Micro") when the Dock overflows.
 */
class PokerDockFitTest {
	/** The vanilla-like advance width of {@code StatusFitTest} (6 px, space 4, narrow glyphs 2–5). */
	static final ToIntFunction<String> FONT = s -> {
		int w = 0;
		for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
			int c = s.codePointAt(i);
			w += c == ' ' ? 4 : "i!.,:;|'".indexOf(c) >= 0 ? 2 : "l`".indexOf(c) >= 0 ? 3 : "tI[]".indexOf(c) >= 0 ? 4 : "fk<>\"()*{}".indexOf(c) >= 0 ? 5 : 6;
		}
		return w;
	};

	private static final int[][] SIZES = {{427, 240}, {480, 270}, {640, 360}, {960, 540}};
	private static final String P = "gui.burmaldaholic.poker.";

	private record Button(String label, String hint) {
		int width() {
			int w = FONT.applyAsInt(label) + 16;
			return hint == null ? w : w + 5 + FONT.applyAsInt(hint) + 6;
		}

		int compactWidth() {
			return FONT.applyAsInt(label) + 16;
		}
	}

	private record DockCase(String name, List<Button> actions, String stepperValue, Button primary, String status) {}

	private static JsonObject lang(String module, String code) throws IOException {
		Path f = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/main/lang", module, code + ".json");
		return JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static String tr(JsonObject poker, JsonObject core, String key, Object... args) {
		JsonObject src = poker.has(key) ? poker : core;
		assertTrue(src.has(key), "missing string " + key);
		String s = src.get(key).getAsString();
		for (int i = 0; i < args.length; i++) s = s.replace("%" + (i + 1) + "$s", String.valueOf(args[i]));
		return s;
	}

	private static List<DockCase> cases(String code, boolean shortStakes) throws IOException {
		JsonObject p = lang("poker", code);
		JsonObject c = lang("core", code);
		String space = code.equals("ru_ru") ? "Пробел" : "Space";
		List<DockCase> out = new ArrayList<>();
		long[][] blinds = {{1, 2}, {5, 10}, {25, 50}, {100, 200}};
		String[] ids = {"micro", "low", "mid", "high"};
		List<Button> stakes = new ArrayList<>();
		for (int i = 0; i < ids.length; i++) {
			stakes.add(new Button(shortStakes ? tr(p, c, P + "v2.stakes_short." + ids[i]) : tr(p, c, P + "stakes." + ids[i], blinds[i][0], blinds[i][1]), null));
		}
		out.add(new DockCase("stakes", stakes, null, new Button(tr(p, c, P + "v2.primary.buy_in"), space), tr(p, c, P + "v2.reason.pick_stakes")));
		out.add(new DockCase("buy_in", List.of(new Button(tr(p, c, "gui.burmaldaholic.common.back"), null)), "20 000",
			new Button(tr(p, c, P + "v2.primary.buy_in_amount", "20 000"), space), ""));
		out.add(new DockCase("turn", List.of(new Button(tr(p, c, P + "fold"), "F"), new Button(tr(p, c, P + "call", "5 000"), "C")), "19 800",
			new Button(tr(p, c, P + "raise_to", "19 800"), "R"), tr(p, c, P + "v2.status.to_call", "5 000")));
		out.add(new DockCase("seated", List.of(new Button(tr(p, c, P + "sit_out"), null), new Button(tr(p, c, P + "top_up"), null),
			new Button(tr(p, c, P + "stand_up"), null)), null, new Button(tr(p, c, P + "v2.primary.wait"), space), ""));
		return out;
	}

	/** The Dock the kit lays out for {@code dc} ({@code V2Chrome.relayout}, ACTIONS mode). */
	private static DockLayout.Dock layout(TableGrid g, DockCase dc) {
		int[] w = dc.actions().stream().mapToInt(Button::width).toArray();
		int[] cw = dc.actions().stream().mapToInt(Button::compactWidth).toArray();
		int stepperW = dc.stepperValue() == null ? 0 : 2 * g.tier.buttonH + 2 * 2 + Math.max(FONT.applyAsInt(dc.stepperValue()), FONT.applyAsInt("888 888")) + 8;
		DockLayout.Spec spec = DockLayout.Spec.actions(w, cw, stepperW, dc.primary().width(), dc.primary().compactWidth())
			.withStatus(Math.min(g.dock.w() * 2 / 5, FONT.applyAsInt(dc.status()) + 2));
		return DockLayout.layout(g, spec, 0);
	}

	@Test
	void everyPokerDockControlIsOnScreenAndAlone() throws IOException {
		List<String> bad = new ArrayList<>();
		for (String code : new String[] {"en_us", "ru_ru"}) {
			List<DockCase> full = cases(code, false);
			List<DockCase> shortOnes = cases(code, true);
			for (int[] wh : SIZES) {
				TableGrid g = TableGrid.of(wh[0], wh[1]);
				for (int k = 0; k < full.size(); k++) {
					DockCase dc = full.get(k);
					DockLayout.Dock d = layout(g, dc);
					if (d.overflow() > 0 && dc.name().equals("stakes")) { // PokerScreen.refresh: the short stake labels
						dc = shortOnes.get(k);
						d = layout(g, dc);
					}
					String where = code + " " + wh[0] + "x" + wh[1] + " " + dc.name() + ": ";
					if (d.overflow() > 0) bad.add(where + "overflow " + d.overflow() + " px");
					List<Box> controls = new ArrayList<>(d.actions());
					controls.add(d.primary());
					if (!d.stepper().isEmpty()) controls.add(d.stepper());
					List<Box> all = new ArrayList<>(controls);
					if (!d.status().isEmpty()) all.add(d.status());
					for (Box b : all) {
						if (b.x() < 0 || b.y() < 0 || b.right() > g.screen.w() || b.bottom() > g.screen.h()) bad.add(where + b + " is off the " + g.screen);
					}
					for (int i = 0; i < controls.size(); i++) {
						for (int j = i + 1; j < controls.size(); j++) {
							if (intersects(controls.get(i), controls.get(j))) bad.add(where + controls.get(i) + " overlaps " + controls.get(j));
						}
						if (!d.status().isEmpty() && intersects(controls.get(i), d.status())) bad.add(where + controls.get(i) + " overlaps the status " + d.status());
					}
				}
			}
		}
		assertTrue(bad.isEmpty(), "poker Dock misfits:\n" + String.join("\n", bad));
	}

	@Test
	void theFullStakeLabelsAreKeptWhereTheyFit() throws IOException {
		// 960 × 540 EN has room for "Micro (1/2)" × 4: the short labels are only the narrow-Dock fallback
		DockCase stakes = cases("en_us", false).get(0);
		assertTrue(layout(TableGrid.of(960, 540), stakes).overflow() == 0, "960 × 540 EN keeps the blinds on the stake buttons");
	}

	private static boolean intersects(Box a, Box b) {
		return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
	}
}

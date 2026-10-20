package dev.nezo.burmaldaholic.client.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.ui.v2.DockLayout;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/**
 * Money bug T-B2 M1: the "buttons moved, click again" flash was wider than the decision status, so the next relayout put
 * the action row into its compact form and the player's second click, at the old Check, pressed Bet ×3 (debit 30).
 * Transient status text (flash, server error, watchdog) must never change a control rect: for every game, every stable
 * status that game shows (its {@code .v2.status.*} lines, alone and with the kit's line-2 hints), and every transient text
 * of every game and the kit, at the 4 play-field sizes (427×240, 480×270, 640×360, 960×540), in the betting and the
 * actions Docks, EN and RU: the Dock laid out while the transient shows is identical to the one without it. The transient
 * text itself stays readable (its words are drawn in the status box or the overlay band above the Dock).
 */
class TransientStatusLayoutTest {
	/** A vanilla-like advance width (as StatusFitTest). */
	static final ToIntFunction<String> FONT = s -> {
		int w = 0;
		for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
			int c = s.codePointAt(i);
			w += c == ' ' ? 4 : "i!.,:;|'".indexOf(c) >= 0 ? 2 : "l`".indexOf(c) >= 0 ? 3 : "tI[]".indexOf(c) >= 0 ? 4 : "fk<>\"()*{}".indexOf(c) >= 0 ? 5 : 6;
		}
		return w;
	};

	private static final int[][] SIZES = {{427, 240}, {480, 270}, {640, 360}, {960, 540}};
	private static final List<String> GAMES = List.of("baccarat", "blackjack", "craps", "extras", "poker", "roulette", "uth");
	private static final Pattern ARG = Pattern.compile("%(\\d\\$)?s");

	private static Map<String, String> lang(String module, String code) throws IOException {
		Path f = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/main/lang", module, code + ".json");
		JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
		Map<String, String> out = new java.util.TreeMap<>();
		for (var e : o.entrySet()) out.put(e.getKey(), ARG.matcher(e.getValue().getAsString()).replaceAll("99"));
		return out;
	}

	private static List<String> matching(Map<String, String> l, String part) {
		List<String> out = new ArrayList<>();
		for (var e : l.entrySet()) if (e.getKey().contains(part)) out.add(e.getValue());
		return out;
	}

	/** The kit's Docks at a size in a language (labels of the given language), as V2Chrome lays them out. */
	private static List<DockLayout.Dock> docks(TableGrid g, String[] l, int statusW) {
		int readout = Math.max(FONT.applyAsInt(l[0]), 2 * FONT.applyAsInt("12 500"));
		int readoutC = Math.max(FONT.applyAsInt(l[0]), FONT.applyAsInt("12 500"));
		int primary = FONT.applyAsInt(l[1]) + 16 + 5 + FONT.applyAsInt("Пробел") + 6;
		int primaryC = FONT.applyAsInt(l[1]) + 16;
		int[] full = new int[3];
		int[] compact = new int[3];
		for (int i = 0; i < 3; i++) {
			compact[i] = FONT.applyAsInt(l[2 + i]) + 16;
			full[i] = compact[i] + 5 + 6 + 6;
		}
		return List.of(DockLayout.layout(g, DockLayout.Spec.betting(5, 4, readout, readoutC, 0, primary, primaryC).withStatus(statusW), 2),
			DockLayout.layout(g, DockLayout.Spec.actions(full, compact, primary, primaryC).withStatus(statusW)));
	}

	@Test
	void transientTextNeverMovesAControlInAnyGame() throws IOException {
		List<String> moved = new ArrayList<>();
		java.util.Set<String> unreadable = new java.util.TreeSet<>();
		int checked = 0;
		for (String code : List.of("en_us", "ru_ru")) {
			String[] labels = code.equals("en_us") ? new String[] {"Bet", "Sit down", "Check", "Bet ×3", "Fold"}
				: new String[] {"Ставка", "Сесть", "Чек", "Ставка ×3", "Пас"};
			Map<String, String> core = lang("core", code);
			List<String> hints = new ArrayList<>();
			hints.add(null);
			hints.addAll(matching(core, ".v2.status."));
			// every transient text: every reason (a flash) and error string of the kit and the games, the "moved" flash,
			// the watchdog's line
			List<String> transients = new ArrayList<>(matching(core, ".v2.reason."));
			for (String gm : GAMES) {
				Map<String, String> l = lang(gm, code);
				transients.addAll(matching(l, ".v2.reason."));
				transients.addAll(matching(l, ".error."));
			}
			for (String game : GAMES) {
				List<String> lines = new ArrayList<>(matching(lang(game, code), ".v2.status."));
				lines.add(null);
				for (String l1 : lines) {
					for (String hint : hints) {
						for (int kind = 0; kind < 3; kind++) {
							for (String t : transients) {
								StatusLine s = new StatusLine();
								s.set(l1 == null ? null : Component.literal(l1), null);
								s.hint(hint == null ? null : Component.literal(hint));
								int stable = s.measure(FONT);
								Consumer<Component> show = kind == 0 ? s::flash : kind == 1 ? s::error : s::watchdog;
								show.accept(Component.literal(t));
								int during = s.measure(FONT);
								checked++;
								if (during != stable) {
									moved.add(code + " " + game + " '" + l1 + "' + '" + hint + "' kind " + kind + " '" + t + "': status width " + stable + " -> " + during);
									continue;
								}
								if (kind > 0) continue; // the same measure; one kind lays the Docks out
								for (int[] wh : SIZES) {
									TableGrid g = TableGrid.of(wh[0], wh[1]);
									List<DockLayout.Dock> before = docks(g, labels, stable);
									List<DockLayout.Dock> after = docks(g, labels, during);
									if (!before.equals(after)) moved.add(code + " " + game + " " + wh[0] + "x" + wh[1] + " kind " + kind + " '" + t + "'");
									// the transient's words stay readable in the laid-out box (or the overlay band above the Dock)
									DockLayout.Dock d = before.get(1);
									int m = g.tier.margin;
									s.place(d.status(), d.hintStrip(), new dev.nezo.burmaldaholic.core.ui.v2.Box(m, g.dock.y() - DockLayout.HINT_STRIP_H,
										g.dock.w() - 2 * m, DockLayout.HINT_STRIP_H));
									String lost = s.lost(FONT);
									if (!lost.isEmpty() && unreadable.size() < 40) {
										unreadable.add(code + " " + wh[0] + "x" + wh[1] + " '" + t + "' + '" + hint + "': " + lost);
									}
								}
							}
						}
					}
				}
			}
		}
		assertTrue(checked > 10_000, "checked " + checked);
		assertEquals(List.of(), moved, "transient status text moved a control");
		assertEquals(java.util.Set.of(), unreadable, "transient status text lost words");
	}

	@Test
	void theMovedFlashAtTheUthDecisionKeepsCheckWhereItWas() {
		// the T-B2 M1 repro, pure: the UTH pre-flop decision Dock at 480 × 270 EN, then the refusal flash
		StatusLine s = new StatusLine();
		s.set(Component.literal("Your decision"), null);
		s.hint(Component.literal("Check or bet ×3 / ×4"));
		TableGrid g = TableGrid.of(480, 270);
		String[] l = {"Bet", "Bet ×4", "Check", "Bet ×3", "Fold"};
		DockLayout.Dock before = docks(g, l, s.measure(FONT)).get(1);
		s.flash(Component.literal("The buttons moved — click again"));
		DockLayout.Dock after = docks(g, l, s.measure(FONT)).get(1);
		assertEquals(before.actions(), after.actions(), "Check / Bet ×3 / Fold keep their rects");
		assertEquals(before.primary(), after.primary());
		assertEquals(before.compactActions(), after.compactActions());
		assertEquals("The buttons moved — click again", s.line1().getString(), "the flash still shows");
	}
}

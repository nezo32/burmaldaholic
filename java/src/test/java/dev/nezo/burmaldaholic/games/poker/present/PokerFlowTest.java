package dev.nezo.burmaldaholic.games.poker.present;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The Hold'em v2 gates over every state (docs/architecture/ui-v2.md §5.3): never stuck (an enabled gate or a reason
 * that names what the table waits for), no disabled gate without a reason, every label / reason key in EN and RU.
 */
public class PokerFlowTest {
	static final PokerView.Level MICRO = new PokerView.Level("micro", 1, 2, 40, 200, 0, true);
	static final PokerView.Level LOW = new PokerView.Level("low", 5, 10, 200, 1000, 0, true);
	static final PokerView.Level BROKE = new PokerView.Level("mid", 25, 50, 1000, 300, 0, true);
	static final PokerView.Level VIP = new PokerView.Level("high", 100, 200, 4000, 20000, 3, false);

	static List<PokerView> states() {
		List<PokerView> out = new ArrayList<>();
		List<PokerView.Level> levels = List.of(MICRO, LOW, BROKE, VIP);
		List<PokerView.Turn> turns = new ArrayList<>();
		turns.add(null);
		for (boolean canCheck : new boolean[] {true, false}) {
			for (boolean canRaise : new boolean[] {true, false}) {
				for (long stack : new long[] {5, 500}) {
					for (long raiseTo : new long[] {20, 505}) {
						turns.add(new PokerView.Turn(7, canCheck ? 0 : 10, canCheck, canRaise, 20, 505, canCheck, stack, 0, canCheck ? 0 : 10, 60, 10, raiseTo));
					}
				}
			}
		}
		for (boolean enabled : new boolean[] {true, false}) {
			for (PokerView.Seat seat : PokerView.Seat.values()) {
				for (PokerView.Level level : new PokerView.Level[] {null, MICRO, BROKE, VIP}) {
					for (boolean fixed : new boolean[] {true, false}) {
						for (PokerView.Turn turn : seat == PokerView.Seat.SEATED ? turns : List.<PokerView.Turn>of()) {
							for (int f = 0; f < 32; f++) {
								out.add(new PokerView(enabled, seat, fixed, levels, level, 100, 500, (f & 1) != 0, (f & 2) != 0, (f & 4) != 0, 120,
									(f & 8) != 0, 60, turn, (f & 16) != 0 ? 1 : 4, turn != null, false, turn == null ? "Alex" : "", 12, -1));
							}
						}
						if (seat != PokerView.Seat.SEATED) {
							out.add(new PokerView(enabled, seat, fixed, levels, level, 100, 500, false, false, false, 0, false, 0, null, 3, true, false, "Alex", 10,
								-1));
						}
					}
				}
			}
		}
		return out;
	}

	@Test
	void neverStuckAndEveryDisabledGateHasAReason() {
		for (PokerView v : states()) {
			Gate p = PokerFlow.primary(v);
			List<Gate> all = new ArrayList<>(PokerFlow.actions(v));
			all.add(p);
			boolean anyOn = all.stream().anyMatch(Gate::enabled) || PokerFlow.stepper(v).isPresent();
			assertTrue(anyOn || p.reasonKey() != null, "stuck: " + v);
			for (Gate g : all) assertTrue(g.enabled() || g.reasonKey() != null, "no reason: " + g + " in " + v);
			assertTrue(PokerFlow.actions(v).size() <= 4, "≤ 4 actions + primary: " + v);
			if (v.enabled() && v.seat() == PokerView.Seat.SEATED && v.turn() != null) {
				assertTrue(p.enabled(), "your turn: the primary acts");
				if (!v.turn().canCheck()) assertTrue(PokerFlow.actions(v).stream().anyMatch(g -> g.action().equals("fold") && g.enabled()), "fold offered");
			}
			if (v.enabled() && v.seat() == PokerView.Seat.NONE && v.level() == MICRO) {
				assertTrue(p.enabled() && p.action().equals("buy_in"), "an affordable level can be bought into");
			}
		}
	}

	@Test
	void theMockupTurnIsFoldCallRaise() {
		PokerView.Turn t = new PokerView.Turn(3, 10, false, true, 40, 188, false, 188, 10, 20, 120, 2, 40);
		PokerView v = new PokerView(true, PokerView.Seat.SEATED, true, List.of(MICRO), MICRO, 0, 12150, false, false, false, 0, false, 0, t, 5, true,
			false, "", 24, -1);
		assertEquals(List.of("fold", "call"), PokerFlow.actions(v).stream().map(Gate::action).toList());
		Gate p = PokerFlow.primary(v);
		assertEquals("raise", p.action());
		assertEquals("gui.burmaldaholic.poker.raise_to", p.labelKey());
		assertArrayEquals(new Object[] {40L}, p.labelArgs());
		PokerFlow.Stepper s = PokerFlow.stepper(v).orElseThrow();
		assertEquals(42, s.next(1, 1));
		assertEquals(40, s.next(-1, 1));
		PokerView.Turn max = new PokerView.Turn(3, 10, false, true, 40, 188, false, 188, 10, 20, 120, 2, 188);
		assertEquals("all_in", PokerFlow.primary(new PokerView(true, PokerView.Seat.SEATED, true, List.of(), MICRO, 0, 0, false, false, false, 0, false,
			0, max, 5, true, false, "", 24, -1)).action());
	}

	@Test
	void stepperSnapsAndClamps() {
		PokerFlow.Stepper s = new PokerFlow.Stepper("raise", 41, 40, 188, 10);
		assertEquals(50, s.next(1, 1));
		assertEquals(40, s.next(-1, 1));
		assertEquals(188, new PokerFlow.Stepper("raise", 180, 40, 188, 10).next(1, 1));
		assertEquals(180, new PokerFlow.Stepper("raise", 188, 40, 188, 10).next(-1, 1));
		assertEquals(90, s.next(1, 5));
		assertFalse(new PokerFlow.Stepper("raise", 40, 40, 188, 10).canMinus());
	}

	@Test
	void raisePresetsAreLegalAscendingAndCycle() {
		PokerView.Turn t = new PokerView.Turn(3, 10, false, true, 40, 188, false, 188, 10, 20, 120, 2, 40);
		long[] p = PokerFlow.raisePresets(t);
		assertEquals(40, p[0]);
		assertEquals(188, p[p.length - 1]);
		for (int i = 1; i < p.length; i++) assertTrue(p[i] > p[i - 1]);
		assertEquals(40, PokerFlow.nextPreset(t, 188));
	}

	@Test
	void everyKeyExistsInEnglishAndRussian() throws IOException {
		Set<String> keys = new HashSet<>();
		for (PokerView v : states()) {
			List<Gate> all = new ArrayList<>(PokerFlow.actions(v));
			all.add(PokerFlow.primary(v));
			for (Gate g : all) {
				keys.add(g.labelKey());
				if (g.reasonKey() != null) keys.add(g.reasonKey());
			}
		}
		for (String extra : List.of("status.to_call", "status.auto_fold", "status.auto_check", "status.stepper", "status.buy_in", "status.watching",
			"status.top_up", "status.seated", "pot", "board.pot_street", "board.acting", "board.winner", "board.waiting", "board.next_hand")) {
			keys.add(PokerFlow.V2 + extra);
		}
		for (String lang : new String[] {"en_us", "ru_ru"}) {
			Set<String> have = langKeys(lang);
			for (String k : keys) assertTrue(have.contains(k), lang + " misses " + k);
		}
	}

	public static Set<String> langKeys(String lang) throws IOException {
		Path root = Path.of("src/main/lang");
		Set<String> have = new HashSet<>();
		try (Stream<Path> dirs = Files.list(root)) {
			for (Path d : dirs.toList()) {
				Path f = d.resolve(lang + ".json");
				if (!Files.exists(f)) continue;
				JsonObject j = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
				have.addAll(j.keySet());
			}
		}
		return have;
	}
}

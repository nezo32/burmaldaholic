package dev.nezo.burmaldaholic.games.extras.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.FlowCheck;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The v2 extras flows and layouts (docs/architecture/ui-v2.md §5.3, §11.1; lane E): "impossible to get stuck" over every
 * combination of busy × balance × bet × limits × pawn stakes for Plinko / Wheel / Coin / Dice and every ticket state of
 * Scratch; the local bet's clamping; the scenes inside the PlayArea at every size; every key in EN and RU.
 */
class ExtrasFlowTest {
	private static final ExtrasFlow[] FLOWS = {ExtrasFlow.PLINKO, ExtrasFlow.WHEEL, ExtrasFlow.COIN, ExtrasFlow.DICE};

	@Test
	void singleSpotFlowsAreNeverStuck() {
		List<String> problems = new ArrayList<>();
		long[] balances = {0, 3, 10, 50, 5000, 2_000_000};
		long[] bets = {0, 5, 10, 60, 1000};
		long[][] limits = {{1, 0}, {10, 500}, {1, 50}};
		for (ExtrasFlow f : FLOWS) {
			for (boolean busy : new boolean[] {false, true}) {
				for (long bal : balances) {
					for (long[] lim : limits) {
						for (long amount : bets) {
							for (String block : new String[] {null, ExtrasKeys.REASON_PICK_SIDE}) {
								LocalBet bet = new LocalBet(amount);
								ExtrasView v = ExtrasView.chips(bet.view(lim[0], lim[1], bal, busy), busy, block, amount * 2);
								for (String p : FlowCheck.problems(f, v)) problems.add(f.action() + " busy=" + busy + " bal=" + bal + " bet=" + amount + ": " + p);
							}
						}
					}
					// pawn stakes: levels / hearts (−/+), a held item (none)
					for (long[] st : new long[][] {{1, 30}, {3, 3}, {1, 0}, {0, 0}}) {
						ExtrasView v = ExtrasView.pawn(busy, null, st[0], st[1]);
						for (String p : FlowCheck.problems(f, v)) problems.add(f.action() + " pawn " + st[0] + "/" + st[1] + ": " + p);
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void thePrimarySaysWhy() {
		LocalBet bet = new LocalBet(0);
		ExtrasView none = ExtrasView.chips(bet.view(1, 100, 500, false), false, null, 0);
		assertEquals(V2Keys.REASON_PLACE_BET, ExtrasFlow.PLINKO.primary(none).reasonKey());
		assertEquals(ExtrasKeys.HINT_CHIPS, ExtrasFlow.PLINKO.hintKey(none).orElseThrow());
		bet.add(25, 100, 500);
		ExtrasView ok = ExtrasView.chips(bet.view(1, 100, 500, false), false, null, 825);
		Gate drop = ExtrasFlow.PLINKO.primary(ok);
		assertTrue(drop.enabled());
		assertEquals("drop", drop.action());
		assertEquals(ExtrasKeys.HINT_WIN_UP_TO, ExtrasFlow.PLINKO.hintKey(ok).orElseThrow());
		assertEquals(ExtrasKeys.REASON_DROPPING, ExtrasFlow.PLINKO.primary(ExtrasView.chips(bet.view(1, 100, 500, true), true, null, 0)).reasonKey());
		assertEquals(V2Keys.REASON_SPINNING, ExtrasFlow.WHEEL.primary(ExtrasView.chips(bet.view(1, 100, 500, true), true, null, 0)).reasonKey());
		assertEquals(ExtrasKeys.REASON_PICK_SIDE, ExtrasFlow.COIN.primary(ExtrasView.chips(bet.view(1, 100, 500, false), false,
			ExtrasKeys.REASON_PICK_SIDE, 0)).reasonKey());
		assertEquals(ExtrasKeys.REASON_NO_ITEM, ExtrasFlow.WHEEL.primary(ExtrasView.pawn(false, null, 0, 0)).reasonKey());
		assertEquals(2, ExtrasFlow.DICE.actions(ExtrasView.pawn(false, null, 2, 5)).size());
		assertTrue(ExtrasFlow.DICE.actions(ok).isEmpty(), "the BetBar shows when chips are staked");
	}

	@Test
	void scratchTicketIsNeverStuck() {
		List<String> problems = new ArrayList<>();
		for (boolean playable : new boolean[] {false, true}) {
			for (boolean revealing : new boolean[] {false, true}) {
				for (int fresh : new int[] {0, 2}) {
					for (boolean canBuy : new boolean[] {false, true}) {
						for (long bal : new long[] {0, 5, 1000}) {
							for (boolean gold : new boolean[] {false, true}) {
								var v = new ScratchFlow.View(playable, revealing, fresh, 10, canBuy, bal, gold);
								for (String p : FlowCheck.problems(ScratchFlow.INSTANCE, v)) problems.add(v + ": " + p);
							}
						}
					}
				}
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
		var fresh = new ScratchFlow.View(true, false, 1, 10, true, 100, false);
		assertEquals(ScratchFlow.ALL, ScratchFlow.INSTANCE.primary(fresh).action());
		assertEquals(ScratchFlow.NEW, ScratchFlow.INSTANCE.primary(new ScratchFlow.View(false, false, 1, 10, true, 100, false)).action());
		assertEquals(ScratchFlow.BUY, ScratchFlow.INSTANCE.primary(new ScratchFlow.View(false, false, 0, 10, true, 100, false)).action());
		assertFalse(ScratchFlow.INSTANCE.actions(fresh).get(0).enabled(), "the ticket on the counter");
		assertTrue(ScratchFlow.INSTANCE.actions(fresh).get(1).enabled());
	}

	@Test
	void localBetClampsAndRemembers() {
		LocalBet b = new LocalBet();
		assertTrue(b.add(25, 100, 1000));
		assertTrue(b.add(100, 100, 1000));
		assertEquals(100, b.amount(), "clamped to the maximum");
		assertFalse(b.add(5, 100, 1000), "nothing above the maximum");
		assertTrue(b.undo());
		assertEquals(25, b.amount());
		b.played();
		assertTrue(b.clear());
		assertEquals(0, b.amount());
		assertTrue(b.rebet(100, 1000));
		assertEquals(25, b.amount());
		assertTrue(b.dbl(0, 40));
		assertEquals(40, b.amount(), "×2 clamped to the balance");
		b.clamp(0, 10);
		assertEquals(10, b.amount());
		var v = b.view(1, 0, 10, false);
		assertTrue(v.canClear());
		assertTrue(BetGates.tool(BetGates.CLEAR, v).enabled());
		assertTrue(v.seated() && v.bettingOpen());
		assertFalse(b.view(1, 0, 10, true).bettingOpen());
	}

	/** Every size of ui-v2.md §11.1: the object and the options column stay inside the PlayArea and never overlap. */
	@Test
	void scenesFitThePlayArea() {
		int[][] sizes = {{320, 240}, {427, 240}, {455, 256}, {480, 270}, {512, 288}, {569, 320}, {600, 338}, {640, 360}, {683, 384}, {854, 480},
			{630, 270}, {960, 539}};
		List<String> problems = new ArrayList<>();
		for (int[] s : sizes) {
			TableGrid g = TableGrid.of(s[0], s[1]);
			for (var e : List.of(java.util.Map.entry("plinko", ExtrasLayout.plinko(g)), java.util.Map.entry("wheel", ExtrasLayout.wheel(g)),
				java.util.Map.entry("coin", ExtrasLayout.coin(g)), java.util.Map.entry("scratch", ExtrasLayout.scratch(g)),
				java.util.Map.entry("dice", ExtrasLayout.dice(g)))) {
				ExtrasLayout.Scene sc = e.getValue();
				String at = e.getKey() + " @" + s[0] + "x" + s[1];
				if (!sc.object().inside(g.play)) problems.add(at + ": object " + sc.object() + " outside " + g.play);
				if (!sc.side().isEmpty() && !sc.side().inside(g.play)) problems.add(at + ": side " + sc.side() + " outside " + g.play);
				if (sc.side().intersects(sc.object())) problems.add(at + ": side overlaps the object");
				if (sc.scale() <= 0.5f) problems.add(at + ": scale " + sc.scale());
				if (s[1] >= 270 && s[0] >= 480 && !e.getKey().equals("dice") && sc.scale() < 1f) problems.add(at + ": art scaled at 1080p@4");
				Box lastRow = sc.row(14, 3);
				if (e.getKey().equals("plinko") && lastRow.bottom() > g.play.bottom()) problems.add(at + ": risk rows below the PlayArea");
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void everyKeyHasEnglishAndRussian() throws Exception {
		Path root = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/main/lang");
		List<String> missing = new ArrayList<>();
		for (String lang : new String[] {"en_us", "ru_ru"}) {
			StringBuilder all = new StringBuilder();
			for (String module : new String[] {"extras", "pvp"}) all.append(Files.readString(root.resolve(module).resolve(lang + ".json"), StandardCharsets.UTF_8));
			for (String k : ExtrasKeys.ALL) if (!all.toString().contains("\"" + k + "\"")) missing.add(lang + ": " + k);
			for (String k : new String[] {"gui.burmaldaholic.pvp.v2.pill", "gui.burmaldaholic.pvp.v2.invite.from", "gui.burmaldaholic.extras.v2.board.plinko_result"}) {
				if (!all.toString().contains("\"" + k + "\"")) missing.add(lang + ": " + k);
			}
		}
		assertTrue(missing.isEmpty(), String.join("\n", missing));
	}
}

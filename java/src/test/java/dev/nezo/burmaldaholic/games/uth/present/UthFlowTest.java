package dev.nezo.burmaldaholic.games.uth.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.games.poker.present.PokerFlowTest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * UTH v2 gates over every state (docs/architecture/ui-v2.md §5.3) and the bet draft: the bet can always be changed
 * while bets are open (the v0.1.1 "UTH bet can't be changed" report), a chip is live whenever the viewer is seated in
 * the betting phase, no disabled gate without a reason, every key in EN and RU.
 */
class UthFlowTest {
	static List<UthView> states() {
		List<UthView> out = new ArrayList<>();
		List<List<String>> legals = List.of(List.of(), List.of("bet_4x", "bet_3x", "check"), List.of("bet_4x", "check"), List.of("bet_2x", "check"),
			List.of("bet_1x", "fold"));
		UthBetDraft[] drafts = {UthBetDraft.empty(), UthBetDraft.empty().add(UthBetDraft.Spot.ANTE, 10),
			UthBetDraft.empty().add(UthBetDraft.Spot.TRIPS, 5), UthBetDraft.empty().add(UthBetDraft.Spot.ANTE, 5000),
			UthBetDraft.empty().add(UthBetDraft.Spot.ANTE, 1).add(UthBetDraft.Spot.TRIPS, 1)};
		for (boolean seated : new boolean[] {true, false}) {
			for (boolean banker : new boolean[] {false, true}) {
				for (UthView.Phase phase : UthView.Phase.values()) {
					for (long[] confirmed : new long[][] {null, {10, 0}}) {
						for (UthBetDraft d : drafts) {
							for (long balance : new long[] {0, 5, 40, 100_000}) {
								for (List<String> legal : phase == UthView.Phase.PLAYING ? legals : List.<List<String>>of(List.of())) {
									for (int f = 0; f < 8; f++) {
										UthView.Bank bank = (f & 1) == 0 ? null : new UthView.Bank((f & 2) != 0, (f & 4) != 0, (f & 2) != 0, 500, 300);
										out.add(new UthView(seated, banker, phase, confirmed, d, 5, 3000, (f & 4) == 0, 5, balance, (f & 2) != 0 ? 10 : 0, 0, legal,
											10, (f & 4) == 0, (f & 1) != 0 ? "Alex" : "", 9, bank));
									}
								}
							}
						}
					}
				}
			}
		}
		return out;
	}

	static List<Gate> gates(UthView v) {
		List<Gate> all = new ArrayList<>(UthFlow.actions(v));
		all.add(UthFlow.primary(v));
		all.add(UthFlow.chips(v));
		all.add(UthFlow.spot("ante", v));
		all.add(UthFlow.spot("trips", v));
		for (String t : UthFlow.TOOLS) all.add(UthFlow.tool(t, v));
		return all;
	}

	@Test
	void neverStuckAndTheBetIsAlwaysChangeableWhileBetsAreOpen() {
		for (UthView v : states()) {
			List<Gate> all = gates(v);
			for (Gate g : all) assertTrue(g.enabled() || g.reasonKey() != null, "no reason: " + g.labelKey() + " in " + v);
			assertTrue(all.stream().anyMatch(Gate::enabled) || UthFlow.primary(v).reasonKey() != null, "stuck: " + v);
			if (v.seated() && !v.banker() && v.betting()) {
				assertTrue(UthFlow.chips(v).enabled(), "chips live while betting");
				assertTrue(UthFlow.spot("ante", v).enabled(), "ante spot live while betting");
				if (v.confirmed() != null && (v.bank() == null || !v.bank().form())) {
					assertEquals("~change", UthFlow.primary(v).action(), "a confirmed bet shows Change bet");
				}
				if (!v.draft().isEmpty()) assertTrue(UthFlow.tool("clear", v).enabled(), "a draft can be cleared");
			}
			if (v.deciding() && !v.banker() && v.seated()) {
				assertTrue(UthFlow.actions(v).size() + 1 <= 3, "≤ 2 decisions + primary");
				Gate p = UthFlow.primary(v);
				assertTrue(p.action().startsWith("bet_") || p.action().equals("check") || p.action().equals("fold"));
			}
			if (!v.seated() && !v.banker()) assertEquals("sit", UthFlow.primary(v).action());
		}
	}

	@Test
	void decisionRowsMatchTheDesign() {
		UthView pre = view(UthView.Phase.PLAYING, List.of("bet_4x", "bet_3x", "check"), UthBetDraft.empty(), 1000);
		assertEquals("bet_4x", UthFlow.primary(pre).action());
		assertEquals(List.of("check", "bet_3x"), UthFlow.actions(pre).stream().map(Gate::action).toList());
		assertEquals(40L, UthFlow.primary(pre).labelArgs()[0]);
		UthView river = view(UthView.Phase.PLAYING, List.of("bet_1x", "fold"), UthBetDraft.empty(), 1000);
		assertEquals("bet_1x", UthFlow.primary(river).action());
		assertEquals(List.of("fold"), UthFlow.actions(river).stream().map(Gate::action).toList());
		UthView poor = view(UthView.Phase.PLAYING, List.of("bet_4x", "check"), UthBetDraft.empty(), 30);
		assertFalse(UthFlow.primary(poor).enabled());
		assertEquals("gui.burmaldaholic.uth.unaffordable", UthFlow.primary(poor).reasonKey());
	}

	@Test
	void dealNeedsAValidDraftAndSaysWhy() {
		UthView empty = view(UthView.Phase.BETTING, List.of(), UthBetDraft.empty(), 1000);
		assertEquals("gui.burmaldaholic.uth.v2.reason.place_ante", UthFlow.primary(empty).reasonKey());
		UthBetDraft ten = UthBetDraft.empty().add(UthBetDraft.Spot.ANTE, 10).add(UthBetDraft.Spot.TRIPS, 5);
		Gate deal = UthFlow.primary(view(UthView.Phase.BETTING, List.of(), ten, 1000));
		assertTrue(deal.enabled());
		assertEquals("bet", deal.action());
		assertEquals(25L, deal.labelArgs()[0]);
		Gate keep = UthFlow.primary(view(UthView.Phase.BETTING, List.of(), ten, 30));
		assertEquals("gui.burmaldaholic.uth.error.keep_for_river", keep.reasonKey());
	}

	@Test
	void draftEditsAreReversible() {
		UthBetDraft d = UthBetDraft.empty().add(UthBetDraft.Spot.ANTE, 5).add(UthBetDraft.Spot.ANTE, 25).add(UthBetDraft.Spot.TRIPS, 5);
		assertEquals(30, d.ante());
		assertEquals(5, d.trips());
		assertEquals(UthBetDraft.Spot.TRIPS, d.spot());
		assertEquals(65, d.cost());
		d = d.remove(UthBetDraft.Spot.ANTE, 25);
		assertEquals(5, d.ante());
		d = d.undo();
		assertEquals(30, d.ante());
		d = d.doubled();
		assertEquals(60, d.ante());
		assertEquals(10, d.trips());
		d = d.clear();
		assertTrue(d.isEmpty());
		d = d.undo();
		assertEquals(60, d.ante());
		assertEquals(0, UthBetDraft.empty().remove(UthBetDraft.Spot.TRIPS, 5).trips());
		UthBetDraft many = UthBetDraft.empty();
		for (int i = 0; i < 100; i++) many = many.add(UthBetDraft.Spot.ANTE, 1);
		assertEquals(UthBetDraft.HISTORY, many.history().size());
	}

	@Test
	void everyKeyExistsInEnglishAndRussian() throws IOException {
		Set<String> keys = new HashSet<>();
		for (UthView v : states()) {
			for (Gate g : gates(v)) {
				keys.add(g.labelKey());
				if (g.reasonKey() != null) keys.add(g.reasonKey());
			}
		}
		for (String extra : List.of("status.chips_to", "status.cost", "status.right_click", "status.bet_placed", "status.starts_in", "status.decide_in",
			"status.watching", "status.bank_form", "dealer_no_open", "board.bets_open", "board.street", "board.deciding", "board.result")) {
			keys.add(UthFlow.V2 + extra);
		}
		for (String lang : new String[] {"en_us", "ru_ru"}) {
			Set<String> have = PokerFlowTest.langKeys(lang);
			for (String k : keys) assertTrue(have.contains(k), lang + " misses " + k);
		}
	}

	private static UthView view(UthView.Phase phase, List<String> legal, UthBetDraft d, long balance) {
		return new UthView(true, false, phase, null, d, 5, 3000, true, 5, balance, 0, 0, legal, 10, true, "", 10, null);
	}
}

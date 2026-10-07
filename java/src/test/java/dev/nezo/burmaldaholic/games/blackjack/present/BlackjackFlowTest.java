package dev.nezo.burmaldaholic.games.blackjack.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackView.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Exhaustive gates (docs/architecture/ui-v2.md §5.3): every phase × seated × balance × draft × placed × turn ×
 * offer: (a) something is pressable or the screen says what it waits for; (b) every disabled gate has a reason (the
 * type enforces it); (c) when betting is open for a seated player who can afford the minimum, a chip is enabled.
 */
class BlackjackFlowTest {
	static final long MIN = 10, MAX = 100;

	@Test
	void neverStuck() {
		int cases = 0;
		for (Phase phase : Phase.values()) {
			for (boolean seated : new boolean[] {false, true}) {
				for (boolean canSit : new boolean[] {false, true}) {
					for (long balance : new long[] {0, 5, 50, 1_000}) {
						for (long draft : new long[] {0, 5, 25, 150}) {
							for (long placed : new long[] {0, 25}) {
								for (long last : new long[] {0, 25, 500}) {
									for (boolean myTurn : new boolean[] {false, true}) {
										for (String offer : new String[] {"", "insurance", "even_money"}) {
											for (boolean busy : new boolean[] {false, true}) {
												for (int secs : new int[] {-1, 7}) {
													for (boolean pending : new boolean[] {false, true}) {
														BlackjackView v = new BlackjackView(seated, canSit, phase, busy, MIN, MAX, balance, draft, placed, last,
															draft > 0, phase == Phase.TURNS || phase == Phase.INSURANCE, myTurn && phase == Phase.TURNS && !busy,
															myTurn ? Set.of("hit", "stand") : Set.of(), phase == Phase.INSURANCE ? offer : "", 12, myTurn ? "" : "Alex",
															secs, 1, 1, pending);
														check(v);
														cases++;
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
		assertTrue(cases > 10_000);
	}

	private static void check(BlackjackView v) {
		List<Gate> all = new ArrayList<>();
		all.add(BlackjackFlow.INSTANCE.primary(v));
		all.addAll(BlackjackFlow.INSTANCE.actions(v));
		for (String t : new String[] {"undo", "clear", "rebet", "double"}) all.add(BlackjackFlow.INSTANCE.tool(t, v));
		for (long d : v.chips()) all.add(BlackjackFlow.INSTANCE.chip(d, v));
		boolean anyOn = all.stream().anyMatch(Gate::enabled);
		Gate p = all.get(0);
		boolean waits = !p.enabled() && p.reasonKey() != null;
		assertTrue(anyOn || waits, () -> "stuck: " + v);
		for (Gate g : all) assertTrue(g.enabled() || g.reasonKey() != null && !g.reasonKey().isEmpty(), () -> g + " " + v);
		if (v.seated() && v.bettingOpen() && !v.pending() && v.balance() >= v.min()) {
			assertTrue(v.chips().stream().anyMatch(d -> BlackjackFlow.INSTANCE.chip(d, v).enabled()) || v.draft() >= v.cap(), () -> "no chip: " + v);
		}
		if (v.seated() && v.phase() == Phase.BETTING && v.placed() == 0 && !v.pending() && v.draft() >= v.min() && v.draft() <= v.cap()) {
			assertTrue(p.enabled() && p.action().equals("bet"), () -> "deal not offered: " + v);
		}
		if (v.myTurn() && !v.pending() && v.seated()) assertTrue(anyOn, () -> "my turn, nothing to press: " + v);
		assertEquals(2, BlackjackFlow.status(v).length);
	}

	@Test
	void theDealPathIsOpen() {
		BlackjackView v = new BlackjackView(true, true, Phase.BETTING, false, MIN, MAX, 1_000, 0, 0, 0, false, false, false, Set.of(), "", 0, "", 12, 0, 0, false);
		assertEquals("gui.burmaldaholic.core.v2.reason.place_bet", BlackjackFlow.INSTANCE.primary(v).reasonKey());
		assertTrue(BlackjackFlow.INSTANCE.chip(25, v).enabled());
		assertFalse(BlackjackFlow.INSTANCE.tool("undo", v).enabled());
		BlackjackView d = new BlackjackView(true, true, Phase.BETTING, false, MIN, MAX, 1_000, 25, 0, 0, true, false, false, Set.of(), "", 0, "", 12, 0, 0, false);
		assertTrue(BlackjackFlow.INSTANCE.primary(d).enabled());
		assertEquals("bet", BlackjackFlow.INSTANCE.primary(d).action());
		assertFalse(BlackjackFlow.INSTANCE.chip(100, d).enabled()); // 125 > max
		assertTrue(BlackjackFlow.INSTANCE.tool("double", d).enabled());
		BlackjackView unseated = new BlackjackView(false, true, Phase.BETTING, false, MIN, MAX, 1_000, 0, 0, 0, false, false, false, Set.of(), "", 0, "", -1, 0, 0,
			false);
		assertEquals("sit", BlackjackFlow.INSTANCE.primary(unseated).action());
		assertTrue(BlackjackFlow.INSTANCE.primary(unseated).enabled());
		assertEquals(List.of(1L, 5L, 25L, 100L), unseated.chips());
	}

	@Test
	void chipsFollowTheLimits() {
		BlackjackView hr = new BlackjackView(true, true, Phase.BETTING, false, 100, 10_000, 1_000_000, 0, 0, 0, false, false, false, Set.of(), "", 0, "", -1,
			0, 0, false);
		assertEquals(List.of(25L, 100L, 500L, 1_000L, 5_000L), hr.chips());
	}
}

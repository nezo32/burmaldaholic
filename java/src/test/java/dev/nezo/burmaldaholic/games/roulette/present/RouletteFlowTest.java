package dev.nezo.burmaldaholic.games.roulette.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.anim.dice.ChipStacks;
import dev.nezo.burmaldaholic.core.ui.v2.FlowCheck;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteFlow.Phase;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteFlow.View;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exhaustive roulette bet-flow states (docs/architecture/ui-v2.md §5.3): in every combination there is something to
 * press or a reason to wait, a disabled gate always says why, and whenever betting is open to a player who can afford
 * the minimum a chip is live — the test that would have caught v0.1.1's "roulette won't let me place a bet".
 */
class RouletteFlowTest {
	private static final long MIN = 5, MAX = 500;

	static List<View> all() {
		List<View> out = new ArrayList<>();
		for (boolean enabled : new boolean[] {true, false})
			for (Phase ph : Phase.values())
				for (boolean seated : new boolean[] {true, false})
					for (boolean full : new boolean[] {true, false})
						for (long bal : new long[] {0, MIN - 1, MIN, MAX + 100})
							for (long total : new long[] {0, 25})
								for (boolean rebet : new boolean[] {true, false})
									for (boolean ready : new boolean[] {true, false})
										for (boolean multi : new boolean[] {true, false})
											for (long minTotal : new long[] {0, 100})
												for (int secs : new int[] {-1, 3}) {
													if (!seated && total > 0) continue; // a bet seats you
													out.add(new View(seated, full, enabled, ph, MIN, MAX, minTotal, bal, total, rebet && ph == Phase.BETTING, rebet ? 30 : 0,
														ready && total > 0, multi, ready ? 1 : 0, multi ? 2 : 1, secs));
												}
		return out;
	}

	@Test
	void neverStuckNeverSilent() {
		int n = 0;
		for (View v : all()) {
			n++;
			List<Gate> gates = new ArrayList<>();
			gates.add(RouletteFlow.primary(v));
			gates.addAll(RouletteFlow.tools(v));
			for (int d : ChipStacks.DENOMS) gates.add(RouletteFlow.chip(v, d));
			for (Gate g : gates) {
				assertNotNull(g);
				if (!g.enabled()) assertFalse(g.reasonKey() == null || g.reasonKey().isEmpty(), v + " " + g.labelKey());
			}
			boolean anyOn = gates.stream().anyMatch(Gate::enabled);
			boolean waiting = v.phase() != Phase.BETTING || v.ready() || !v.enabled() || (!v.seated() && v.tableFull())
				|| v.balance() < MIN || (v.minTotal() > v.total() && v.total() > 0);
			assertTrue(anyOn || waiting, "stuck: " + v);
			if (v.enabled() && (v.seated() || v.total() == 0)) { // a disabled game says so (error.disabled) and has nothing to press
				RouletteView rv = RouletteView.of(v, java.util.Map.of());
				assertEquals(List.of(), FlowCheck.problems(RouletteFlow.FLOW, rv), v.toString());
			}
			if (v.bettingOpen() && (v.seated() || !v.tableFull()) && v.balance() >= MIN && v.total() + MIN <= MAX) {
				assertTrue(RouletteFlow.chip(v, MIN).enabled(), "no chip: " + v);
			}
		}
		assertTrue(n > 1000, "" + n);
	}

	private static View betting(boolean seated, long balance, long total) {
		return new View(seated, false, true, Phase.BETTING, MIN, MAX, 0, balance, total, false, 0, false, false, 0, 1, -1);
	}

	@Test
	void primaryWalksThroughTheRound() {
		assertEquals("sit", RouletteFlow.primary(betting(false, 100, 0)).action());
		Gate g = RouletteFlow.primary(betting(true, 100, 0));
		assertFalse(g.enabled());
		assertEquals(RouletteFlow.CORE + "reason.place_bet", g.reasonKey());
		g = RouletteFlow.primary(betting(true, 100, 25));
		assertTrue(g.enabled());
		assertEquals("spin", g.action());
		assertEquals("gui.burmaldaholic.common.spin", g.labelKey());
		View spin = new View(true, false, true, Phase.SPIN, MIN, MAX, 0, 75, 25, false, 0, true, false, 1, 1, 2);
		assertEquals(RouletteFlow.CORE + "reason.spinning", RouletteFlow.primary(spin).reasonKey());
		assertFalse(RouletteFlow.chip(spin, 5).enabled());
		View rebet = new View(true, false, true, Phase.BETTING, MIN, MAX, 0, 75, 0, true, 25, false, false, 0, 0, -1);
		assertEquals("rebet", RouletteFlow.primary(rebet).action());
		assertTrue(RouletteFlow.primary(rebet).enabled());
		assertTrue(RouletteFlow.tool("rebet", rebet).enabled());
	}

	@Test
	void toolsAndChipsRespectLimits() {
		View v = betting(true, 1000, 300);
		assertFalse(RouletteFlow.tool("double", v).enabled());
		assertEquals(RouletteFlow.CORE + "reason.above_max", RouletteFlow.tool("double", v).reasonKey());
		assertFalse(RouletteFlow.tool("undo", new View(true, false, true, Phase.SPIN, MIN, MAX, 0, 1000, 25, false, 0, true, false, 1, 1, 2)).enabled());
		assertFalse(RouletteFlow.chip(v, 500).enabled());
		assertTrue(RouletteFlow.chip(v, 100).enabled());
		assertFalse(RouletteFlow.chip(betting(true, 1000, 0), 1).enabled());
		assertTrue(RouletteFlow.tool("undo", v).enabled());
		assertFalse(RouletteFlow.tool("undo", betting(true, 1000, 0)).enabled());
	}

	/** DES roulette P1: during the result the primary is Repeat N, enabled (gold); once pressed it says it is queued. */
	@Test
	void repeatIsLiveDuringTheResult() {
		View result = new View(true, false, true, Phase.RESULT, MIN, MAX, 0, 500, 0, false, 55, false, false, 0, 0, 2);
		// T-C2 V1: never "click the layout" while betting is closed
		org.junit.jupiter.api.Assertions.assertEquals(java.util.Optional.of("gui.burmaldaholic.roulette.v2.status.result_shown"), RouletteFlow.hintKey(result));
		Gate g = RouletteFlow.primary(result);
		assertEquals("rebet", g.action());
		assertTrue(g.enabled(), "Repeat enabled during the result");
		assertEquals(55L, g.labelArgs()[0]);
		View queued = new View(true, false, true, Phase.RESULT, MIN, MAX, 0, 500, 0, false, 55, false, false, 0, 0, 2, true);
		Gate q = RouletteFlow.primary(queued);
		assertFalse(q.enabled());
		assertEquals(RouletteFlow.K + "reason.rebet_queued", q.reasonKey());
		assertEquals(55L, q.labelArgs()[0]);
		View poor = new View(true, false, true, Phase.RESULT, MIN, MAX, 0, 20, 0, false, 55, false, false, 0, 0, 2);
		assertFalse(RouletteFlow.primary(poor).enabled());
		assertEquals(RouletteFlow.CORE + "reason.bets_open_in", RouletteFlow.primary(poor).reasonKey());
		View none = new View(true, false, true, Phase.RESULT, MIN, MAX, 0, 500, 0, false, 0, false, false, 0, 0, 2);
		assertFalse(RouletteFlow.primary(none).enabled());
	}
}

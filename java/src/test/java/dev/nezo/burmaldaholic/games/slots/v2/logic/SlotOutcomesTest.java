package dev.nezo.burmaldaholic.games.slots.v2.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SLOTS.md §8.4 chaos mapping (one event per spin, by priority; §15 test 15) and §14 advancements. */
class SlotOutcomesTest {
	private static final int[] QUIET = {0, 0, 0, 0, 0};

	private static SpinTape tape(Machine m, int[] stops, SpinTape.Hunt hunt, List<SpinTape.JackpotAward> jps, long total, boolean cap) {
		return new SpinTape(m, 100, false, stops, null, hunt, null, null, jps, total, cap);
	}

	@Test
	void chaosPriorities() {
		MachineDef o = SlotDefaults.def(Machine.OVERWORLD);
		SpinTape.Hunt creeperFirst = new SpinTape.Hunt(new int[] {0, 1, 1}, 0);
		// 1: Major/Grand beats everything else in the same spin
		SpinTape grand = tape(Machine.OVERWORLD, QUIET, creeperFirst, List.of(new SpinTape.JackpotAward(4, 50_000, false)), 0, false);
		assertEquals("jackpot", SlotOutcomes.chaos(o, grand, 100, true).event());
		// 2: Mini / Minor
		SpinTape mini = tape(Machine.OVERWORLD, QUIET, null, List.of(new SpinTape.JackpotAward(1, 1_000, false)), 0, false);
		assertEquals("chip_shower", SlotOutcomes.chaos(o, mini, 100, true).event());
		// 4: the hunt ended on its first chest
		SlotOutcomes.ChaosPick p = SlotOutcomes.chaos(o, tape(Machine.OVERWORLD, QUIET, creeperFirst, List.of(), 0, false), 100, false);
		assertEquals(4, p.priority());
		assertEquals("mob_wave", p.event());
		assertEquals("msg.burmaldaholic.slots.creeper_friends", p.messageKey());
		// 5: big-win rule only with the 30 % roll, net ≥ 50× and ≥ 500 chips
		SpinTape big = tape(Machine.OVERWORLD, QUIET, null, List.of(), 300, false); // 60 × 100 = 6 000 chips
		assertEquals("lucky_buff", SlotOutcomes.chaos(o, big, 100, true).event());
		assertNull(SlotOutcomes.chaos(o, big, 100, false));
		assertNull(SlotOutcomes.chaos(o, tape(Machine.OVERWORLD, QUIET, null, List.of(), 5, false), 100, true));
	}

	@Test
	void netherTumbleChainAndSkulls() {
		MachineDef n = SlotDefaults.def(Machine.NETHER);
		int[] six = {2, 23, 15, 0, 0}; // a 6-tumble chain of the shared vectors
		SpinEval e = SpinEval.of(n, six, false, 0);
		assertEquals(6, e.tumbles());
		SpinTape t = new SpinTape(Machine.NETHER, 20, false, six, null, null, null, null, List.of(), e.payFifths(), false);
		SlotOutcomes.ChaosPick p = SlotOutcomes.chaos(n, t, 20, false);
		if (e.fiveTop()) {
			assertEquals("mob_wave", p.event()); // 5 Wither Skulls outrank the chain (table order)
		} else {
			assertEquals("lucky_buff", p.event());
			assertEquals(6, p.arg());
		}
		assertTrue(SlotOutcomes.advancements(n, t).contains("tumble_six"));
	}

	@Test
	void advancements() {
		MachineDef end = SlotDefaults.def(Machine.END);
		SpinTape capped = new SpinTape(Machine.END, 100, false, QUIET, null, null, null, new SpinTape.Wheel(new int[] {1, 7, 4}),
			List.of(new SpinTape.JackpotAward(4, 100, false)), 25_000, true);
		List<String> a = SlotOutcomes.advancements(end, capped);
		assertTrue(a.containsAll(List.of("max_win", "epic_win", "dragon_core", "jackpot", "mini_jackpot")), a.toString());
		SpinTape bought = new SpinTape(Machine.END, 100, true, new int[0], new SpinTape.FreeSpins(9, List.of(), 0), null, null, null, List.of(), 0,
			false);
		assertTrue(!SlotOutcomes.advancements(end, bought).contains("free_spins"), "bought free spins do not count");
	}
}

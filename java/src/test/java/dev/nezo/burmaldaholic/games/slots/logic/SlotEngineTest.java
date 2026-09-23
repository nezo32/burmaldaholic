package dev.nezo.burmaldaholic.games.slots.logic;

import static dev.nezo.burmaldaholic.games.slots.logic.Symbol.APPLE;
import static dev.nezo.burmaldaholic.games.slots.logic.Symbol.BERRIES;
import static dev.nezo.burmaldaholic.games.slots.logic.Symbol.CLOCK;
import static dev.nezo.burmaldaholic.games.slots.logic.Symbol.CREEPER;
import static dev.nezo.burmaldaholic.games.slots.logic.Symbol.EMERALD;
import static dev.nezo.burmaldaholic.games.slots.logic.Symbol.PEARL;
import static dev.nezo.burmaldaholic.games.slots.logic.Symbol.SEVEN;
import static dev.nezo.burmaldaholic.games.slots.logic.Symbol.STAR;
import static dev.nezo.burmaldaholic.games.slots.logic.Symbol.TNT;
import static dev.nezo.burmaldaholic.games.slots.logic.Symbol.WILD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.slots.logic.SlotEngine.LineKind;
import dev.nezo.burmaldaholic.games.slots.logic.SlotEngine.LineResult;
import dev.nezo.burmaldaholic.games.slots.logic.SlotEngine.SpinEval;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SlotEngineTest {
	private static final SlotTable COPPER = SlotTable.defaults(Tier.COPPER);
	private static final SlotTable GOLD = SlotTable.defaults(Tier.GOLD);
	private static final SlotTable NETH = SlotTable.defaults(Tier.NETHERITE);

	private static Symbol[][] grid(Symbol[] top, Symbol[] mid, Symbol[] bottom) {
		return new Symbol[][] {top, mid, bottom};
	}

	private static Symbol[] row(Symbol a, Symbol b, Symbol c) {
		return new Symbol[] {a, b, c};
	}

	@Test
	void threeOfAKindPaysTheTableMultiplier() {
		assertEquals(new LineResult(LineKind.THREE, SEVEN, 150), SlotEngine.evaluateLine(SEVEN, SEVEN, SEVEN, COPPER));
		assertEquals(7, SlotEngine.evaluateLine(APPLE, APPLE, APPLE, GOLD).multiplier());
	}

	@Test
	void wildSubstitutesAndTheBestCandidateWins() {
		assertEquals(new LineResult(LineKind.THREE, SEVEN, 100), SlotEngine.evaluateLine(WILD, SEVEN, WILD, GOLD));
		assertEquals(APPLE, SlotEngine.evaluateLine(WILD, WILD, APPLE, GOLD).symbol());
		assertEquals(new LineResult(LineKind.THREE, BERRIES, 8), SlotEngine.evaluateLine(BERRIES, WILD, BERRIES, GOLD));
		// wild + wild + berries: berries (8) is the only candidate
		assertEquals(BERRIES, SlotEngine.evaluateLine(WILD, WILD, BERRIES, GOLD).symbol());
	}

	@Test
	void threeWildsUseTheWildPay() {
		assertEquals(new LineResult(LineKind.WILD, WILD, 200), SlotEngine.evaluateLine(WILD, WILD, WILD, GOLD));
		assertEquals(250, SlotEngine.evaluateLine(WILD, WILD, WILD, NETH).multiplier());
	}

	@Test
	void wildNeverSubstitutesForSpecials() {
		assertNull(SlotEngine.evaluateLine(PEARL, WILD, PEARL, GOLD));
		assertNull(SlotEngine.evaluateLine(STAR, STAR, WILD, GOLD));
		assertNull(SlotEngine.evaluateLine(WILD, CREEPER, CREEPER, GOLD));
	}

	@Test
	void specialsPayTheirTableValue() {
		assertEquals(new LineResult(LineKind.SPECIAL, CREEPER, 0), SlotEngine.evaluateLine(CREEPER, CREEPER, CREEPER, COPPER));
		assertEquals(10, SlotEngine.evaluateLine(PEARL, PEARL, PEARL, GOLD).multiplier());
		assertEquals(50, SlotEngine.evaluateLine(CLOCK, CLOCK, CLOCK, NETH).multiplier());
		assertEquals(new LineResult(LineKind.SPECIAL, STAR, 0), SlotEngine.evaluateLine(STAR, STAR, STAR, GOLD));
		assertEquals(new LineResult(LineKind.SPECIAL, TNT, 0), SlotEngine.evaluateLine(TNT, TNT, TNT, NETH));
	}

	@Test
	void ownedMachinesPayAFixedStarMultiplier() {
		SlotTable owned = SlotTable.defaults(Tier.GOLD, true, 1000);
		assertFalse(owned.progressive());
		assertEquals(1000, SlotEngine.evaluateLine(STAR, STAR, STAR, owned).multiplier());
		SpinEval e = SlotEngine.evaluate(grid(row(APPLE, EMERALD, APPLE), row(STAR, STAR, STAR), row(APPLE, EMERALD, APPLE)), owned, 2);
		assertFalse(e.jackpotHit());
		assertEquals(2000, e.basePayout());
		assertEquals(STAR, e.special());
		// copper has no progressive: the owned flag changes nothing
		assertEquals(COPPER.toString(), SlotTable.defaults(Tier.COPPER, true, 1000).toString());
	}

	@Test
	void leadingBerriesWildDoesNotCount() {
		assertEquals(new LineResult(LineKind.BERRY1, BERRIES, 2), SlotEngine.evaluateLine(BERRIES, APPLE, BERRIES, COPPER));
		assertEquals(new LineResult(LineKind.BERRY2, BERRIES, 3), SlotEngine.evaluateLine(BERRIES, BERRIES, APPLE, COPPER));
		assertEquals(LineKind.BERRY1, SlotEngine.evaluateLine(BERRIES, WILD, APPLE, GOLD).kind());
		assertNull(SlotEngine.evaluateLine(WILD, BERRIES, APPLE, GOLD));
		assertNull(SlotEngine.evaluateLine(APPLE, BERRIES, BERRIES, COPPER));
		assertEquals(LineKind.BERRY2, SlotEngine.evaluateLine(BERRIES, BERRIES, CREEPER, COPPER).kind());
	}

	@Test
	void copperPlaysOnlyTheMiddleRow() {
		SpinEval e = SlotEngine.evaluate(grid(row(SEVEN, SEVEN, SEVEN), row(APPLE, APPLE, APPLE), row(SEVEN, SEVEN, SEVEN)), COPPER, 3);
		assertEquals(List.of(new SlotEngine.LineWin(1, LineKind.THREE, APPLE, 10, 30)), e.wins());
		assertEquals(30, e.basePayout());
		assertFalse(e.threeSevens());
	}

	@Test
	void goldPlaysThreeRows() {
		SpinEval e = SlotEngine.evaluate(grid(row(SEVEN, WILD, SEVEN), row(APPLE, APPLE, APPLE), row(BERRIES, EMERALD, APPLE)), GOLD, 5);
		assertEquals(List.of(1, 2, 3), e.wins().stream().map(SlotEngine.LineWin::line).toList());
		assertEquals(7 * 5 + 100 * 5 + 2 * 5, e.basePayout());
		assertTrue(e.threeSevens());
		assertNull(e.special());
	}

	@Test
	void netheritePlaysDiagonalsJackpotOncePriority() {
		SpinEval e = SlotEngine.evaluate(grid(row(STAR, PEARL, STAR), row(CLOCK, STAR, CLOCK), row(STAR, PEARL, STAR)), NETH, 2);
		assertEquals(List.of(4, 5), e.wins().stream().map(SlotEngine.LineWin::line).toList());
		assertTrue(e.jackpotHit());
		assertEquals(0, e.basePayout());
		assertEquals(STAR, e.special());
		SpinEval e2 = SlotEngine.evaluate(grid(row(PEARL, PEARL, PEARL), row(CLOCK, CLOCK, CLOCK), row(TNT, TNT, TNT)), NETH, 2);
		assertEquals(CLOCK, e2.special());
		assertEquals(10 * 2 + 50 * 2, e2.basePayout());
		SpinEval e3 = SlotEngine.evaluate(grid(row(PEARL, PEARL, PEARL), row(APPLE, BERRIES, CLOCK), row(TNT, TNT, TNT)), NETH, 2);
		assertEquals(PEARL, e3.special());
		SpinEval e4 = SlotEngine.evaluate(grid(row(APPLE, PEARL, PEARL), row(APPLE, BERRIES, CLOCK), row(TNT, TNT, TNT)), NETH, 2);
		assertEquals(TNT, e4.special());
	}

	@Test
	void payoutIsFloorOfMultiplierTimesLineBet() {
		SlotTable t = SlotTable.of(Map.of("apple", 1), Map.of("apple", 2.5), new double[] {2, 3}, 1, false);
		Symbol[] a = row(APPLE, APPLE, APPLE);
		assertEquals(7, SlotEngine.evaluate(grid(a, a, a), t, 3).basePayout());
		assertEquals(23, SlotEngine.linePayout(2.3, 10));
	}

	@Test
	void worstCaseCoversTheBestLineOnEveryLine() {
		assertEquals(250L * 10 * 5, SlotEngine.worstCaseReturn(NETH, 10));
		assertEquals(1000L * 10 * 5, SlotEngine.worstCaseReturn(SlotTable.defaults(Tier.NETHERITE, true, 1000), 10));
		assertEquals(150L * 7, SlotEngine.worstCaseReturn(COPPER, 7));
	}

	@Test
	void drawUsesWeightsExactly() {
		SlotTable t = SlotTable.of(Map.of("apple", 3, "seven", 1), Map.of(), null, 1, false);
		int[] next = {0};
		// rolls 0,1,2 -> apple, 3 -> seven
		assertEquals(APPLE, SlotEngine.drawSymbol(t, b -> 2));
		assertEquals(SEVEN, SlotEngine.drawSymbol(t, b -> 3));
		assertEquals(4, t.totalWeight());
		Symbol[][] g = SlotEngine.drawGrid(t, b -> next[0]++ % b);
		assertEquals(SEVEN, g[1][0]);
		assertThrows(IllegalStateException.class, () -> SlotEngine.drawGrid(SlotTable.of(Map.of(), Map.of(), null, 1, false), b -> 0));
	}

	@Test
	void invalidConfigValuesAreIgnored() {
		SlotTable t = SlotTable.of(Map.of("apple", -5, "bogus", 7, "seven", 2), Map.of("seven", Double.NaN, "apple", -1.0), new double[] {-1},
			99, false);
		assertEquals(0, t.weight(APPLE));
		assertEquals(2, t.totalWeight());
		assertEquals(0, t.pay(SEVEN));
		assertEquals(0, t.berryPartial(1));
		assertEquals(Paylines.MAX, t.lines());
	}

	@Test
	void losingDefinition() {
		SpinEval jackpot = SlotEngine.evaluate(grid(row(STAR, STAR, STAR), row(STAR, STAR, STAR), row(STAR, STAR, STAR)), GOLD, 1);
		assertTrue(jackpot.jackpotHit());
		assertFalse(SlotEngine.losing(jackpot, 3));
		SpinEval berry = SlotEngine.evaluate(grid(row(APPLE, EMERALD, APPLE), row(BERRIES, EMERALD, APPLE), row(APPLE, EMERALD, SEVEN)), GOLD, 1);
		assertEquals(2, berry.basePayout());
		assertTrue(SlotEngine.losing(berry, 3));
	}

	@Test
	void symbolAndTierIds() {
		assertEquals(Symbol.GOLDEN_CARROT, Symbol.byId("golden_carrot"));
		assertEquals("gui.burmaldaholic.slots.symbol.carrot", Symbol.GOLDEN_CARROT.translationKey());
		assertEquals("gui.burmaldaholic.slots.symbol.berry", BERRIES.translationKey());
		assertNull(Symbol.byId("nope"));
		assertEquals(BERRIES, Symbol.byOrdinal(-1));
		assertEquals(Tier.NETHERITE, Tier.byId("netherite"));
		assertEquals("slot_machine_gold", Tier.GOLD.blockName());
	}
}

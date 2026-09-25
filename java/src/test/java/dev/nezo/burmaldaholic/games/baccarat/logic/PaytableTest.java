package dev.nezo.burmaldaholic.games.baccarat.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratRules.Side;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** §20.1 payouts / Banker step, §20.4 limits, §20.6 worst case. */
class PaytableTest {
	private static EnumMap<BetKind, Long> slip(Object... kv) {
		EnumMap<BetKind, Long> m = new EnumMap<>(BetKind.class);
		for (int i = 0; i < kv.length; i += 2) {
			m.put((BetKind) kv[i], ((Number) kv[i + 1]).longValue());
		}
		return m;
	}

	@Test
	void bankerStep() {
		assertEquals(20, Paytable.of(0.05, 8, 11).bankerStep());
		assertEquals(25, Paytable.of(0.04, 8, 11).bankerStep());
		assertEquals(1, Paytable.of(0.0, 8, 11).bankerStep());
		assertEquals(100, Paytable.of(0.03, 8, 11).bankerStep(), "3 % needs 100 (3 × 100 / 100 whole)");
		assertTrue(Paytable.of(0.03, 8, 11).exactStep());
		assertEquals(100, Paytable.of(0.0333, 8, 11).bankerStep(), "no exact step ≤ 100 → 100");
		assertFalse(Paytable.of(0.0333, 8, 11).exactStep());
	}

	@Test
	void bankerStepMultiplesPayExactly() {
		Paytable p = Paytable.DEFAULT;
		for (long n = 1; n <= 50; n++) {
			assertEquals(19 * n, p.bankerProfit(20 * n), "B = 20n wins exactly 19n");
			assertEquals(n, p.commission(20 * n));
		}
		assertEquals(4, p.bankerProfit(5), "an off-step amount would be floored (why the step exists)");
	}

	@Test
	void returnsPerBet() {
		Paytable p = Paytable.DEFAULT;
		assertEquals(100, p.returnOf(BetKind.PLAYER, 50, Side.PLAYER, false, false));
		assertEquals(50, p.returnOf(BetKind.PLAYER, 50, Side.TIE, false, false), "push on tie");
		assertEquals(0, p.returnOf(BetKind.PLAYER, 50, Side.BANKER, false, false));
		assertEquals(39, p.returnOf(BetKind.BANKER, 20, Side.BANKER, false, false));
		assertEquals(20, p.returnOf(BetKind.BANKER, 20, Side.TIE, false, false));
		assertEquals(90, p.returnOf(BetKind.TIE, 10, Side.TIE, false, false), "8:1");
		assertEquals(0, p.returnOf(BetKind.TIE, 10, Side.PLAYER, false, false));
		assertEquals(120, p.returnOf(BetKind.PLAYER_PAIR, 10, Side.BANKER, true, false), "11:1 independent of the winner");
		assertEquals(0, p.returnOf(BetKind.BANKER_PAIR, 10, Side.BANKER, true, false));
		assertEquals(120, p.returnOf(BetKind.BANKER_PAIR, 10, Side.PLAYER, false, true));
	}

	@Test
	void worstCaseOverTwelveClasses() {
		Paytable p = Paytable.DEFAULT;
		assertEquals(1, p.worstCase(slip(BetKind.PLAYER, 1)), "smallest worst case at the minimum bet (§20.6)");
		assertEquals(80, p.worstCase(slip(BetKind.TIE, 10)));
		// Player + Banker hedge: worst is Player winning: +100 − 100 = 0
		assertEquals(0, p.worstCase(slip(BetKind.PLAYER, 100, BetKind.BANKER, 100)));
		// Tie 10 + both pairs 10: a tie with both pairs: 80 + 110 + 110
		assertEquals(300, p.worstCase(slip(BetKind.TIE, 10, BetKind.PLAYER_PAIR, 10, BetKind.BANKER_PAIR, 10)));
		// Player 100 + Tie 10: Player wins → 100 − 10 = 90; Tie → 80 (P pushes) → 90
		assertEquals(90, p.worstCase(slip(BetKind.PLAYER, 100, BetKind.TIE, 10)));
	}

	@Test
	void limits() {
		Slips.Limits l = Slips.Limits.of(1, 1000, 0.25, 20, 0, true);
		assertEquals(250, l.sideMax());
		assertEquals(20, l.bankerMin());
		assertEquals(Optional.empty(), l.checkAdd(slip(), BetKind.PLAYER, 1));
		assertEquals(Slips.Code.BANKER_STEP, l.checkAdd(slip(), BetKind.BANKER, 30).orElseThrow().code());
		assertEquals(Optional.empty(), l.checkAdd(slip(), BetKind.BANKER, 40));
		assertEquals(40, l.snapBanker(59));
		assertEquals(Slips.Code.SIDE_MAX, l.checkAdd(slip(BetKind.TIE, 200), BetKind.TIE, 51).orElseThrow().code());
		assertEquals(Optional.empty(), l.checkAdd(slip(BetKind.TIE, 200), BetKind.TIE, 50));
		assertEquals(Slips.Code.TOTAL_MAX, l.checkAdd(slip(BetKind.PLAYER, 900), BetKind.TIE, 101).orElseThrow().code());
		assertEquals(Slips.Code.INVALID_AMOUNT, l.checkAdd(slip(), BetKind.PLAYER, 0).orElseThrow().code());
		Slips.Limits noPairs = Slips.Limits.of(1, 1000, 0.25, 20, 0, false);
		assertEquals(Slips.Code.PAIRS_OFF, noPairs.checkAdd(slip(), BetKind.PLAYER_PAIR, 5).orElseThrow().code());
		Slips.Limits hr = Slips.Limits.of(1, 2000, 0.25, 20, 100, true);
		assertEquals(Slips.Code.MIN_TOTAL, hr.checkSlip(slip(BetKind.PLAYER, 50)).orElseThrow().code());
		assertEquals(Optional.empty(), hr.checkSlip(slip(BetKind.PLAYER, 60, BetKind.BANKER, 40)));
		Slips.Limits min25 = Slips.Limits.of(25, 1000, 0.25, 20, 0, true);
		assertEquals(40, min25.bankerMin(), "Banker ≥ max(k, min) and a multiple of k");
	}

	@Test
	void rebetFitsTheCurrentLimits() {
		Slips.Limits l = Slips.Limits.of(1, 100, 0.25, 20, 0, true);
		Map<BetKind, Long> fit = l.fit(slip(BetKind.PLAYER, 50, BetKind.BANKER, 70, BetKind.TIE, 40));
		assertEquals(50L, fit.get(BetKind.PLAYER));
		assertEquals(40L, fit.get(BetKind.BANKER), "Banker snapped down to the step and the remaining room (50 → 40)");
		assertEquals(10L, fit.get(BetKind.TIE), "Tie gets the room that is left");
		Map<BetKind, Long> fit2 = Slips.Limits.of(1, 1000, 0.25, 20, 0, false).fit(slip(BetKind.PLAYER_PAIR, 10, BetKind.TIE, 400));
		assertFalse(fit2.containsKey(BetKind.PLAYER_PAIR), "pairs off");
		assertEquals(250L, fit2.get(BetKind.TIE), "side max");
	}
}

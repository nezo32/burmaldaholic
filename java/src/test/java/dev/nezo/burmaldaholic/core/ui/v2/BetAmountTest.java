package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.BetAmount.Mode;
import dev.nezo.burmaldaholic.core.wager.BetCap;
import dev.nezo.burmaldaholic.games.uth.logic.UthLimits;
import dev.nezo.burmaldaholic.games.uth.logic.UthLimits.Problem;
import dev.nezo.burmaldaholic.games.uth.present.UthBetDraft;
import dev.nezo.burmaldaholic.games.uth.present.UthFlow;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Lane HR: the bet-entry maths (typed amount, Max, ×2, ½, wheel) and whose cap a clamp reports (owner playtest E4 / B5). */
class BetAmountTest {
	private static BetView.Builder base() {
		return BetView.builder().seated(true).phase(BetView.Phase.BETTING).bettingOpen(true).limits(10, 2000).balance(100_000);
	}

	private static BetCap vip(long max) {
		return BetCap.infer(max, 10, 0, 0, 0, max, 1, max * 4); // Silver → Gold allows 4×
	}

	@Test
	void typingDigitsAndThousands() {
		String t = "";
		for (char c : "25k".toCharArray()) t = BetAmount.type(t, c);
		assertEquals("25000", t);
		assertEquals(25_000, BetAmount.parse(t));
		assertEquals("2500", BetAmount.backspace(t));
		assertEquals("", BetAmount.type("", 'k'), "k needs a number before it");
		assertEquals("7", BetAmount.type("0", '7'), "no leading zero");
		assertEquals("12", BetAmount.type("12", 'x'), "other characters ignored");
		assertEquals("3000000", BetAmount.type("3", 'm'));
		String full = "999999999999";
		assertEquals(full, BetAmount.type(full, '9'), "at most 12 digits");
	}

	@Test
	void maxSaysWhoseCapItIs() {
		BetView v = base().cap(vip(2000)).build();
		BetAmount.Clamp m = BetAmount.max(v, Mode.BET);
		assertEquals(2000, m.value());
		assertEquals(BetCap.KEY_VIP, m.reasonKey(), "VIP max, and the tier that allows more");
		assertEquals(8000L, m.reasonArgs()[3]);
		BetView poor = base().balance(150).cap(vip(2000)).build();
		BetAmount.Clamp p = BetAmount.max(poor, Mode.BET);
		assertEquals(150, p.value());
		assertEquals(BetCap.KEY_BALANCE, p.reasonKey(), "the balance binds");
		BetView owner = base().cap(BetCap.infer(2000, 10, 0, 2000, 0, 5000, 1, 20_000)).build();
		assertEquals(BetCap.KEY_OWNER, BetAmount.max(owner, Mode.BET).reasonKey());
		BetView table = base().cap(BetCap.infer(2000, 10, 2000, 0, 0, 5000, 1, 20_000)).build();
		assertEquals(BetCap.KEY_TABLE, BetAmount.max(table, Mode.BET).reasonKey());
		BetView unknown = base().build();
		assertEquals(V2Keys.REASON_ABOVE_MAX, BetAmount.max(unknown, Mode.BET).reasonKey(), "an old state: the generic sentence");
	}

	@Test
	void clampDoubleHalfWheel() {
		BetView v = base().cap(vip(2000)).build();
		assertNull(BetAmount.clamp(500, v, Mode.BET).reasonKey());
		BetAmount.Clamp hi = BetAmount.clamp(100_000, v, Mode.BET);
		assertEquals(2000, hi.value());
		assertTrue(hi.clamped());
		BetAmount.Clamp lo = BetAmount.clamp(3, v, Mode.BET);
		assertEquals(10, lo.value());
		assertEquals(V2Keys.REASON_BELOW_MIN, lo.reasonKey());
		assertEquals(2000, BetAmount.doubled(1500, v, Mode.BET).value());
		assertEquals(750, BetAmount.halved(1500, v, Mode.BET).value());
		assertEquals(10, BetAmount.halved(15, v, Mode.BET).value(), "½ stops at the minimum");
		assertEquals(1100, BetAmount.wheel(1000, true, v, Mode.BET).value(), "1000 steps by 100");
		assertEquals(900, BetAmount.wheel(1000, false, v, Mode.BET).value());
		assertEquals(25_000, BetAmount.step(100_000));
		assertEquals(1, BetAmount.step(3));
		BetView owned = base().cap(BetCap.infer(2000, 50, 0, 0, 50, 2000, 1, 0)).limits(50, 2000).build();
		assertEquals(BetCap.KEY_OWNER_MIN, BetAmount.clamp(20, owned, Mode.BET).reasonKey(), "the owner's minimum says so");
	}

	@Test
	void unitModeCountsWhatIsOnTheTable() {
		BetView v = base().myTotal(1500).cap(vip(2000)).build();
		assertEquals(500, BetAmount.limit(v, Mode.UNIT));
		assertEquals(2000, BetAmount.limit(v, Mode.BET));
		assertEquals(1, BetAmount.clamp(1, v, Mode.UNIT).value(), "a unit may be below the table minimum");
		assertFalse(BetAmount.clamp(1, v, Mode.UNIT).clamped());
		BetView broke = base().balance(0).build();
		assertEquals(0, BetAmount.clamp(500, broke, Mode.BET).value());
	}

	/**
	 * T-HR D1 + D5: UTH's field is the Ante; the stake shown is Ante + Blind + Trips, but the maximum holds the worst case
	 * 6 × Ante + Trips and the balance must hold 3 × Ante + Trips (the server's {@link UthLimits#check}).
	 */
	@Test
	void anteFieldUsesTheServersUthLimits() {
		BetAmount.Cost uth = UthFlow.entryCost(new UthBetDraft(40, 10, UthBetDraft.Spot.ANTE, List.of()));
		BetView v = base().limits(5, 0).balance(1000).myTotal(uth.of(40)).build();
		assertEquals(90, uth.of(40), "Total stake: 2 × 40 + 10");
		assertEquals(40, BetAmount.initial(v, Mode.BET, 0, uth), "reopens with the Ante, not the cost");
		assertEquals(330, BetAmount.max(v, Mode.BET, uth).value(), "balance: (1000 - 10) / 3 (keep the river's Play bet)");
		assertEquals(BetCap.KEY_BALANCE, BetAmount.max(v, Mode.BET, uth).reasonKey());
		assertEquals(330, BetAmount.clamp(600, v, Mode.BET, uth).value());
		BetView capped = base().limits(5, 300).balance(100_000).cap(vip(300)).build();
		assertEquals(48, BetAmount.max(capped, Mode.BET, uth).value(), "the VIP max holds the worst case: (300 - 10) / 6");
		assertEquals(BetCap.KEY_VIP, BetAmount.max(capped, Mode.BET, uth).reasonKey());
		BetAmount.Cost trips = UthFlow.entryCost(new UthBetDraft(40, 0, UthBetDraft.Spot.TRIPS, List.of()));
		assertEquals(60, BetAmount.max(capped, Mode.BET, trips).value(), "Trips under the VIP max: 300 - 6 × 40");
		BetView poor = base().limits(5, 0).balance(200).build();
		assertEquals(80, BetAmount.max(poor, Mode.BET, trips).value(), "Trips the balance holds: 200 - 3 × 40");
	}

	/**
	 * T-HR D5: for random balances, VIP caps, table maxima and Trips, the entry's Max is exactly what the server accepts:
	 * {@code UthLimits.check(Max) == NONE} and {@code check(Max + 1)} is refused (the same for the Trips spot).
	 */
	@Test
	void uthMaxIsAlwaysAcceptedAndMaxPlusOneRefused() {
		Random rnd = new Random(0x5EED_D5L);
		long[] scales = {100, 10_000, 1_000_000, 100_000_000_000L};
		for (int i = 0; i < 20_000; i++) {
			long vipMax = 1 + (long) (rnd.nextDouble() * scales[rnd.nextInt(scales.length)]);
			long tableMax = 1 + (long) (rnd.nextDouble() * scales[rnd.nextInt(scales.length)]);
			long balance = (long) (rnd.nextDouble() * scales[rnd.nextInt(scales.length)]);
			long minAnte = 1 + rnd.nextInt(50);
			long maxW = Math.min(vipMax, tableMax); // limitsFor: min(table max, tier max)
			BetCap cap = BetCap.infer(maxW, minAnte, tableMax, 0, 0, vipMax, 1, vipMax * 4);
			BetView v = BetView.builder().seated(true).phase(BetView.Phase.BETTING).bettingOpen(true).limits(minAnte, maxW).balance(balance).cap(cap)
				.build();
			long trips = rnd.nextInt(3) == 0 ? 0 : minAnte + rnd.nextInt(500);
			String ctx = "vip " + vipMax + " table " + tableMax + " balance " + balance + " min " + minAnte + " trips " + trips;

			BetAmount.Clamp ante = BetAmount.max(v, Mode.BET, UthFlow.entryCost(new UthBetDraft(1, trips, UthBetDraft.Spot.ANTE, List.of())));
			assertEquals(UthLimits.maxAnte(maxW, trips, balance), ante.value(), ctx);
			if (ante.value() >= minAnte) {
				assertEquals(Problem.NONE, UthLimits.check(ante.value(), trips, minAnte, maxW, true, minAnte, balance), "Max accepted: " + ctx);
			}
			assertNotEquals(Problem.NONE, UthLimits.check(ante.value() + 1, trips, minAnte, maxW, true, minAnte, balance), "Max + 1 refused: " + ctx);
			Problem over = UthLimits.check(ante.value() + 1, trips, minAnte, maxW, true, minAnte, balance);
			if (ante.value() + 1 >= minAnte && over != Problem.INVALID) {
				if (over == Problem.WORST_CASE_MAX) assertNotEquals(BetCap.KEY_BALANCE, ante.reasonKey(), "Max names the max: " + ctx);
				if (over == Problem.INSUFFICIENT_FUNDS || over == Problem.KEEP_FOR_RIVER) assertEquals(BetCap.KEY_BALANCE, ante.reasonKey(), ctx);
			}

			long a = minAnte + rnd.nextInt(1000);
			BetAmount.Clamp t = BetAmount.max(v, Mode.BET, UthFlow.entryCost(new UthBetDraft(a, 0, UthBetDraft.Spot.TRIPS, List.of())));
			assertEquals(UthLimits.maxTrips(maxW, a, balance), t.value(), ctx + " ante " + a);
			if (t.value() >= minAnte) {
				assertEquals(Problem.NONE, UthLimits.check(a, t.value(), minAnte, maxW, true, minAnte, balance), "Trips Max accepted: " + ctx + " ante " + a);
			}
			assertNotEquals(Problem.NONE, UthLimits.check(a, t.value() + 1, minAnte, maxW, true, minAnte, balance),
				"Trips Max + 1 refused: " + ctx + " ante " + a);
		}
	}

	@Test
	void zeroThousandIsNotTyped() {
		assertEquals("0", BetAmount.type("0", 'k'), "T-HR D4: 0k stays 0");
		assertEquals("", BetAmount.type("", 'm'));
	}

	@Test
	void initialValue() {
		BetView v = base().myTotal(300).cap(vip(2000)).build();
		assertEquals(300, BetAmount.initial(v, Mode.BET, 25));
		assertEquals(25, BetAmount.initial(v, Mode.UNIT, 25));
		assertEquals(10, BetAmount.initial(base().build(), Mode.BET, 5), "nothing down: the minimum");
	}
}

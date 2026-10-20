package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.BetAmount.Mode;
import dev.nezo.burmaldaholic.core.wager.BetCap;
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

	@Test
	void initialValue() {
		BetView v = base().myTotal(300).cap(vip(2000)).build();
		assertEquals(300, BetAmount.initial(v, Mode.BET, 25));
		assertEquals(25, BetAmount.initial(v, Mode.UNIT, 25));
		assertEquals(10, BetAmount.initial(base().build(), Mode.BET, 5), "nothing down: the minimum");
	}
}

package dev.nezo.burmaldaholic.core.cashier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nezo.burmaldaholic.core.cashier.ExchangeMath.Slot;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** EC-SPEC §1: fitCount (partial stacks, unstackables), balance room, withdrawable, MAX_UNITS. */
class ExchangeMathTest {
	private static Slot[] slots(int empty, Slot... others) {
		Slot[] s = new Slot[empty + others.length];
		Arrays.fill(s, 0, empty, Slot.emptySlot());
		System.arraycopy(others, 0, s, empty, others.length);
		return s;
	}

	@Test
	void fitCountEmptyAndPartial() {
		assertEquals(36 * 64, ExchangeMath.fitCount(slots(36), 64));
		assertEquals(3 * 64, ExchangeMath.fitCount(slots(3, new Slot(false, false, 64, 64)), 64), "3 free slots = 192");
		assertEquals(64 + 54, ExchangeMath.fitCount(slots(1, new Slot(false, true, 10, 64), new Slot(false, false, 1, 64)), 64),
			"partial stack of the same item adds its room; other items add nothing");
		assertEquals(0, ExchangeMath.fitCount(slots(0, new Slot(false, true, 64, 64)), 64));
		assertEquals(16 + 4, ExchangeMath.fitCount(slots(1, new Slot(false, true, 12, 16)), 16), "ender pearls stack to 16");
	}

	@Test
	void fitCountUnstackables() {
		assertEquals(5, ExchangeMath.fitCount(slots(5, new Slot(false, true, 1, 1)), 1), "unstackables only go into empty slots");
	}

	@Test
	void countIn() {
		assertEquals(64, ExchangeMath.countIn(64, false, 100, 1_000_000, 9));
		assertEquals(100, ExchangeMath.countIn(0, true, 100, 1_000_000, 9), "all = everything you have");
		assertEquals(5, ExchangeMath.countIn(0, true, 100, 45, 9), "room under maxBalance: 45 / 9");
		assertEquals(0, ExchangeMath.countIn(0, true, 100, 8, 9));
		assertEquals(ExchangeMath.MAX_UNITS, ExchangeMath.countIn(0, true, 10_000, Long.MAX_VALUE, 1));
		assertEquals(3, ExchangeMath.countIn(64, false, 3, 1_000, 9), "never more than you have");
	}

	@Test
	void countOut() {
		assertEquals(192, ExchangeMath.countOut(0, true, 1_000_000, 10, 192), "Max with 3 free slots = 192");
		assertEquals(12, ExchangeMath.countOut(0, true, 125, 10, 192), "withdrawable 125 / 10");
		assertEquals(64, ExchangeMath.countOut(64, false, 1_000_000, 10, 1000));
		assertEquals(0, ExchangeMath.countOut(64, false, 1_000_000, 0, 1000), "sell-only row");
		assertEquals(ExchangeMath.MAX_UNITS, ExchangeMath.countOut(0, true, Long.MAX_VALUE, 1, Long.MAX_VALUE));
	}

	@Test
	void countBuy() {
		assertEquals(8, ExchangeMath.countBuy(64, false, 1_000_000, 80, 36 * 64, 1, 8), "golden apple x64 capped at L8");
		assertEquals(10, ExchangeMath.countBuy(0, true, 800, 80, 36 * 64, 1, -1), "Max = balance / price");
		assertEquals(2, ExchangeMath.countBuy(0, true, 1_000_000, 1, 6, 3, -1), "fit counts items, a purchase is 3 items");
		assertEquals(0, ExchangeMath.countBuy(1, false, 1_000_000, 5000, 1, 1, 0), "limit used up");
	}
}

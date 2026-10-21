package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.chips.ChipMath;
import dev.nezo.burmaldaholic.core.ui.v2.BetAmount.Mode;
import dev.nezo.burmaldaholic.core.wager.BetCap;
import dev.nezo.burmaldaholic.games.extras.present.LocalBet;
import org.junit.jupiter.api.Test;

/** Tester T-HR: edge inputs of the bet-entry maths and the chip arithmetic with the high-roller chips. */
class BetAmountEdgeTest {
	private static BetView.Builder base() {
		return BetView.builder().seated(true).phase(BetView.Phase.BETTING).bettingOpen(true).limits(10, 2000).balance(100_000)
			.cap(BetCap.infer(2000, 10, 0, 0, 0, 2000, 1, 8000));
	}

	private static String typed(String keys) {
		String t = "";
		for (char c : keys.toCharArray()) t = BetAmount.type(t, c);
		return t;
	}

	@Test
	void nonDigitsAndSignsAreIgnored() {
		assertEquals("5", typed("-5"), "a minus sign cannot be typed");
		assertEquals("15", typed("1.5"));
		assertEquals("25000", typed("25k"));
		assertEquals("25000", typed("25K"));
		assertEquals("", typed("kkm"));
	}

	@Test
	void veryLongInputStopsAt12Digits() {
		String t = typed("9".repeat(40));
		assertEquals(BetAmount.MAX_DIGITS, t.length());
		assertEquals(999_999_999_999L, BetAmount.parse(t));
		assertEquals("9999999999", typed("9999999999k"), "k that would pass 12 digits is ignored");
		assertEquals("999999999000", typed("999999999k"), "k up to exactly 12 digits");
		assertEquals("9999999", typed("9999999m"), "m that would pass 12 digits is ignored");
	}

	@Test
	void overflowAndHugeValuesClampToTheLimit() {
		BetView v = base().build();
		for (long huge : new long[] {99_999_999_999L, 999_999_999_999L, Long.MAX_VALUE, BetAmount.parse("99999999999999999999")}) {
			BetAmount.Clamp c = BetAmount.clamp(huge, v, Mode.BET);
			assertEquals(2000, c.value(), "clamped to the VIP max: " + huge);
			assertEquals(BetCap.KEY_VIP, c.reasonKey());
			BetAmount.Clamp u = BetAmount.clamp(huge, v, Mode.UNIT);
			assertEquals(2000, u.value());
		}
		assertEquals(Long.MAX_VALUE, BetAmount.parse("99999999999999999999"), "unparsable overflow = huge, then clamped");
		assertEquals(2000, BetAmount.doubled(Long.MAX_VALUE, v, Mode.BET).value(), "x2 saturates");
		assertEquals(2000, BetAmount.wheel(Long.MAX_VALUE, true, v, Mode.BET).value(), "wheel up saturates");
	}

	@Test
	void zeroAndNegativeNeverGoBelowTheMinimum() {
		BetView v = base().build();
		for (long bad : new long[] {0, -1, -25_000, Long.MIN_VALUE}) {
			BetAmount.Clamp c = BetAmount.clamp(bad, v, Mode.BET);
			assertEquals(10, c.value(), "BET: the minimum for " + bad);
			assertEquals(1, BetAmount.clamp(bad, v, Mode.UNIT).value(), "UNIT: one chip for " + bad);
		}
		assertTrue(BetAmount.halved(1, v, Mode.BET).value() >= 10);
		assertTrue(BetAmount.wheel(10, false, v, Mode.BET).value() >= 10, "wheel down never below the minimum");
		assertTrue(BetAmount.wheel(0, false, v, Mode.UNIT).value() >= 1);
	}

	@Test
	void everyResultIsWithinMinTableVipBalance() {
		long[] balances = {0, 5, 10, 150, 2000, 1_000_000};
		long[] maxes = {0, 10, 500, 2000, 25_000};
		long[] values = {-7, 0, 1, 9, 10, 11, 999, 2000, 2001, 25_000, 99_999_999_999L};
		for (long bal : balances) {
			for (long max : maxes) {
				for (long mine : new long[] {0, 500}) {
					BetView v = BetView.builder().seated(true).phase(BetView.Phase.BETTING).bettingOpen(true).limits(10, max).balance(bal)
						.myTotal(mine).build();
					for (Mode m : Mode.values()) {
						long lim = BetAmount.limit(v, m);
						assertTrue(lim <= bal, "limit within the balance");
						if (max > 0) assertTrue(lim <= max, "limit within the maximum");
						if (m == Mode.UNIT && max > 0) assertTrue(lim <= Math.max(0, max - mine), "unit within the room under the max");
						for (long val : values) {
							for (BetAmount.Clamp c : new BetAmount.Clamp[] {BetAmount.clamp(val, v, m), BetAmount.doubled(val, v, m),
								BetAmount.halved(val, v, m), BetAmount.wheel(val, true, v, m), BetAmount.wheel(val, false, v, m), BetAmount.max(v, m)}) {
								assertTrue(c.value() >= 0 && c.value() <= lim, "clamp " + val + " mode " + m + " bal " + bal + " max " + max + " -> "
									+ c.value() + " (limit " + lim + ")");
							}
						}
					}
				}
			}
		}
	}

	@Test
	void brokePlayerGetsZeroWithTheBalanceReason() {
		BetView v = base().balance(0).build();
		BetAmount.Clamp c = BetAmount.clamp(500, v, Mode.BET);
		assertEquals(0, c.value());
		assertEquals(BetCap.KEY_BALANCE, c.reasonKey());
		assertEquals(0, BetAmount.max(v, Mode.BET).value());
		BetView below = base().balance(5).build(); // affords less than the minimum
		BetAmount.Clamp b = BetAmount.clamp(7, below, Mode.BET);
		assertEquals(5, b.value(), "never more than the balance even if it is below the minimum (server refuses TOO_LOW)");
		assertNotNull(b.reasonKey());
	}

	@Test
	void capStateRoundTrip() {
		BetCap c = BetCap.infer(20_000, 25, 0, 0, 0, 20_000, 2, 50_000);
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		c.write(tag);
		assertEquals(c, BetCap.read(tag));
		assertEquals(BetCap.NONE, BetCap.read(new net.minecraft.nbt.CompoundTag()));
		assertEquals(BetCap.Source.OWNER, BetCap.infer(2000, 1, 2000, 2000, 0, 5000, 0, 10_000).source(), "owner wins a tie with the table");
		assertNull(BetAmount.clamp(500, base().build(), Mode.BET).reasonKey());
	}

	@Test
	void localBetSetNeverExceedsCap() {
		LocalBet b = new LocalBet();
		assertFalse(b.set(-5, 2000, 10_000));
		assertEquals(0, b.amount());
		assertTrue(b.set(99_999_999_999L, 2000, 10_000));
		assertEquals(2000, b.amount());
		assertTrue(b.set(99_999_999_999L, 0, 10_000));
		assertEquals(10_000, b.amount(), "no max: the balance");
		assertFalse(b.set(500, 2000, 0));
	}

	@Test
	void chipSplitsWithHighRollerChips() {
		long[] amounts = {0, 1, 24_999, 25_000, 36_500, 100_000, 99_999_999_999L};
		for (long a : amounts) {
			long[] c = ChipMath.split(a);
			long sum = 0;
			for (int i = 0; i < c.length; i++) sum += c[i] * ChipMath.DENOMINATIONS[i];
			assertEquals(a, sum, "split sums back: " + a);
			for (int d : ChipMath.DENOMINATIONS) {
				long[] p = ChipMath.split(a, d);
				long s2 = 0;
				for (int i = 0; i < p.length; i++) s2 += p[i] * ChipMath.DENOMINATIONS[i];
				assertEquals(a, s2, "preferred " + d + " sums back: " + a);
			}
		}
		assertEquals(4, ChipMath.split(100_000)[0], "four 25K");
		assertEquals("25K", ChipMath.label(25_000));
		assertEquals("1K", ChipMath.label(1_000));
		assertEquals("500", ChipMath.label(500));
		assertEquals(25_000, ChipMath.largestAtMost(Long.MAX_VALUE));
		assertEquals(1, ChipMath.largestAtMost(-3));
	}
}

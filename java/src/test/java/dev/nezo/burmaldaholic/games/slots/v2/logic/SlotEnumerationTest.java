package dev.nezo.burmaldaholic.games.slots.v2.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * SLOTS.md §7.5 exact integer vectors by FULL enumeration of every stop combination (§15 test 4 and 5). Runs in the
 * normal unit-test suite (parallel over reel 1; identical reel windows are merged, which keeps the End sticky
 * enumerations cheap).
 */
class SlotEnumerationTest {
	private static long[] head(long[] a, int n) {
		return Arrays.copyOf(a, n);
	}

	private static long sumFrom(long[] a, int from) {
		long s = 0;
		for (int i = from; i < a.length; i++) s += a[i];
		return s;
	}

	@Test
	void overworldBaseGame() {
		SlotEnumerator.Stats s = SlotEnumerator.enumerate(SlotDefaults.def(Machine.OVERWORLD), SlotEnumerator.Mode.BASE, 0);
		assertEquals(102_400_000L, s.n());
		assertEquals(381_579_930L, s.sumPay());
		assertEquals(40_153_130L, s.hitPay());
		assertEquals(40_806_404L, s.hitAny());
		assertArrayEquals(new long[] {58_554_868, 34_909_500, 8_005_320, 882_360, 46_980, 972}, head(s.scat(), 6));
		assertEquals(0, sumFrom(s.scat(), 6));
		assertEquals(777_600L, s.bonus());
		assertEquals(464L, s.maxPay()); // 92.8 × bet
		assertEquals(238_140L, s.fiveTop()); // Diamond
	}

	@Test
	void netherBaseGameWithTumbles() {
		MachineDef def = SlotDefaults.def(Machine.NETHER);
		SlotEnumerator.Stats s = SlotEnumerator.enumerate(def, SlotEnumerator.Mode.BASE, 0);
		assertEquals(33_554_432L, s.n());
		assertEquals(114_458_900L, s.sumPay());
		assertEquals(12_534_784L, s.hitPay());
		assertEquals(12_839_101L, s.hitAny());
		assertArrayEquals(new long[] {18_942_904, 11_569_517, 2_720_221, 305_336, 16_137, 317}, head(s.scat(), 6));
		assertEquals(128_916L, sumFrom(s.coins(), 6));
		assertArrayEquals(new long[] {108_845, 16_483, 3_316, 240, 32, 0}, Arrays.copyOfRange(s.coins(), 6, 12));
		assertEquals(825L, s.maxPay()); // 165 × bet
		assertArrayEquals(new long[] {21_019_648, 8_840_192, 2_651_136, 760_832, 192_512, 70_656, 16_384, 1_024, 2_048, 0}, head(s.tumbles(), 10));
		assertEquals(37_969L, s.fiveTop()); // Wither Skull at any tumble step
	}

	@Test
	void netherFreeSpinLadder() {
		SlotEnumerator.Stats s = SlotEnumerator.enumerate(SlotDefaults.def(Machine.NETHER), SlotEnumerator.Mode.FREE, 0);
		assertEquals(228_917_800L, s.sumPay()); // SLOTS.md §7.3
	}

	@Test
	void endBaseGame() {
		SlotEnumerator.Stats s = SlotEnumerator.enumerate(SlotDefaults.def(Machine.END), SlotEnumerator.Mode.BASE, 0);
		assertEquals(184_528_125L, s.n());
		assertEquals(524_300_931L, s.sumPay());
		assertEquals(66_159_981L, s.hitPay());
		assertEquals(66_603_861L, s.hitAny());
		assertArrayEquals(new long[] {130_691_232, 46_675_440, 6_667_920, 476_280, 17_010, 243}, head(s.scat(), 6));
		assertEquals(656_100L, s.bonus());
		assertEquals(4800L, s.maxPay()); // 960 × bet
		assertEquals(5_488L, s.fiveTop()); // Dragon Head
	}

	@Test
	void endFreeSpinStates() {
		MachineDef def = SlotDefaults.def(Machine.END);
		long[] expected = {1_024_280_910L, 5_722_030_350L, 5_860_237_815L, 34_728_903_900L, 4_465_132_290L, 26_007_038_550L,
			26_578_191_825L, 164_562_181_875L};
		for (int mask = 0; mask < 8; mask++) {
			SlotEnumerator.Stats s = SlotEnumerator.enumerate(def, SlotEnumerator.Mode.FREE, mask);
			assertEquals(184_528_125L, s.n());
			assertEquals(expected[mask], s.sumPay(), "mask " + mask);
			if (mask == 0) {
				long[][] t = {{149_752_557, 275_643}, {10_702_935, 13_365}, {10_709_577, 6_723}, {765_207, 243}, {10_709_577, 6_723},
					{765_207, 243}, {765_369, 81}, {54_675, 0}};
				for (int m = 0; m < 8; m++) assertArrayEquals(t[m], s.post()[m], "transition 0 -> " + m);
			}
		}
	}
}

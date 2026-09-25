package dev.nezo.burmaldaholic.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** LOCALIZATION.md §4 vectors. */
class NumbersTest {
	@Test
	void grouping() {
		assertEquals("0", Numbers.format(0));
		assertEquals("2500", Numbers.format(2500));
		assertEquals("9999", Numbers.format(9999));
		assertEquals("10 000", Numbers.format(10_000));
		assertEquals("12 500", Numbers.format(12_500));
		assertEquals("1 000 000", Numbers.format(1_000_000));
		assertEquals("-12 500", Numbers.format(-12_500));
		assertEquals("-50", Numbers.format(-50));
	}

	@Test
	void durations() {
		assertEquals("02:31", Numbers.minutesSeconds(151 * 20));
		assertEquals("00:01", Numbers.minutesSeconds(1));
		assertEquals("04:12", Numbers.hoursMinutes(252 * 1200));
	}
}

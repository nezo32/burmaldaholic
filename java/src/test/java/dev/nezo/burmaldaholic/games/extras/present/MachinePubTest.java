package dev.nezo.burmaldaholic.games.extras.present;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nezo.burmaldaholic.games.extras.logic.Plinko;
import org.junit.jupiter.api.Test;

/** The in-world machine face (ui-v2.md §7.2 "encode/decode unit-tested"): round trip, bounds, compact multipliers. */
class MachinePubTest {
	@Test
	void plinkoRoundTrip() {
		for (Plinko.Risk r : Plinko.Risk.values()) {
			MachinePub p = MachinePub.plinko("Steve", r.ordinal(), r.defaults());
			MachinePub q = MachinePub.decode(p.name(), p.encode());
			assertEquals(p, q);
			assertArrayEquals(r.defaults(), q.table(), 1e-9);
		}
	}

	@Test
	void badDataIsEmptyAndNamesAreCut() {
		assertEquals(MachinePub.EMPTY, MachinePub.decode("x", new int[] {99, 1}));
		assertEquals(MachinePub.EMPTY, MachinePub.decode("x", null));
		assertEquals(16, MachinePub.wheel("A_very_long_name_of_twenty").name().length());
		assertEquals(0, MachinePub.wheel("Alex").centi().length);
	}

	@Test
	void compactMultipliersFitACap() {
		assertEquals("33", MachinePub.compact(33));
		assertEquals(".6", MachinePub.compact(0.6));
		assertEquals(".25", MachinePub.compact(0.25));
		assertEquals("1.4", MachinePub.compact(1.4));
		assertEquals("0", MachinePub.compact(0));
		for (Plinko.Risk r : Plinko.Risk.values()) {
			for (double m : r.defaults()) assertEquals(true, MachinePub.compact(m).length() <= 3, r + " " + m);
		}
	}
}

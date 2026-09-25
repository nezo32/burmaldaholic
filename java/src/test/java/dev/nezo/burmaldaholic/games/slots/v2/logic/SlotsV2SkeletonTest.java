package dev.nezo.burmaldaholic.games.slots.v2.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nezo.burmaldaholic.core.anim.WinTier;
import org.junit.jupiter.api.Test;

/** Smoke tests of the implemented pure bits of the v2 skeleton. Engine vectors arrive with lane S-J1. */
class SlotsV2SkeletonTest {
	@Test
	void jackpotAwardIsProportionalToTheBet() {
		// SLOTS.md §5.2: floor((seed + increment) × min(1, bet / ref))
		assertEquals(1234, Jackpots.award(1000, 234, 100, 100));
		assertEquals(617, Jackpots.award(1000, 234, 50, 100));
		assertEquals(117, Jackpots.incrementAfter(234, 50, 100));
		assertEquals(0, Jackpots.incrementAfter(234, 500, 100));
	}

	@Test
	void tiersAndPoints() {
		assertEquals(WinTier.RETURN, SlotTiers.of(3, null));
		assertEquals(WinTier.MEGA, SlotTiers.of(200, null));
		assertEquals(10, Showdown.points(5)); // 1 × bet = 10 points
		assertArrayEquals(new int[] {600, 750, 900, 1050, 1200}, Anticipation.baseStopTimes());
	}
}

package dev.nezo.burmaldaholic.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.nezo.burmaldaholic.bots.logic.PlateAnim;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Lane J-L3 (J17): bot nameplate motion (global.md §4.12). */
class PlateAnimTest {
	@Test
	void thinkingDotsCycleEveryTenTicksWhateverTheHand() {
		assertEquals("", PlateAnim.dots(0));
		assertEquals("", PlateAnim.dots(9));
		assertEquals("", PlateAnim.dots(10));
		assertEquals("", PlateAnim.dots(20));
		assertEquals("", PlateAnim.dots(30));
	}

	@Test
	void everyDifficultyHasItsOwnPill() {
		Set<String> pills = new HashSet<>();
		for (BotDifficulty d : BotDifficulty.values()) pills.add(PlateAnim.pill(d));
		assertEquals(BotDifficulty.values().length, pills.size());
	}

	@Test
	void motionKeys() {
		PlateAnim.Key[] join = PlateAnim.join();
		assertEquals(0f, join[0].scale());
		assertEquals(1f, join[join.length - 1].scale());
		assertEquals(PlateAnim.JOIN_TICKS, join[1].duration());
		PlateAnim.Key[] pulse = PlateAnim.pulse();
		assertEquals(PlateAnim.PULSE_SCALE, pulse[0].scale());
		assertEquals(1f, pulse[1].scale(), "ends at rest");
		assertEquals(PlateAnim.PULSE_TICKS, pulse[1].atTick());
		assertEquals(0f, PlateAnim.leave()[0].scale());
		assertNotEquals(0, PlateAnim.leaveDoneTicks());
		assertEquals(0x40140822, PlateAnim.BACKGROUND);
	}
}

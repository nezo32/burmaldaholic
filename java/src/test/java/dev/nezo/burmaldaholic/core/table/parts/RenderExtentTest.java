package dev.nezo.burmaldaholic.core.table.parts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** T-MT D3: the slot render box (y + SLOT_TOP, compact and full) holds every slot renderer element. */
class RenderExtentTest {
	@Test
	void slotBoxContainsTierTextAndBonusWheel() {
		assertTrue(RenderExtent.slotTierTextTop() <= RenderExtent.SLOT_TOP, "tier word top " + RenderExtent.slotTierTextTop());
		assertTrue(RenderExtent.slotBonusWheelTop() <= RenderExtent.SLOT_TOP, "bonus wheel top " + RenderExtent.slotBonusWheelTop());
		// full slot cabinet: 2 cells tall + content above must not undercut the slot box either
		assertTrue(MachineKind.SLOT.height() + RenderExtent.CONTENT_ABOVE <= RenderExtent.SLOT_TOP);
	}
}

package dev.nezo.burmaldaholic.core.table.parts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MachineFootprintTest {
	@Test
	void cellsMainBottomCentreOddWidths() {
		for (MachineKind k : MachineKind.values()) {
			List<LocalCell> cells = k.localCells();
			assertEquals(k.width() * k.height(), cells.size(), k.toString());
			assertEquals(TablePart.MAIN, cells.get(0).part());
			assertEquals(1, k.width() % 2, k + " odd width (mirror swaps LEFT/RIGHT only)");
			Set<TablePart> parts = new HashSet<>();
			for (LocalCell c : cells) {
				assertTrue(parts.add(c.part()), k + " distinct parts");
				assertEquals(0, c.f(), "one row deep");
				assertTrue(c.u() >= 0 && c.u() < k.height());
				assertEquals(c.part(), TablePart.at(c.r(), c.u(), c.f()));
				// mirror keeps the footprint: the mirrored part sits at -r
				assertEquals(-c.r(), c.part().mirrored().r());
				assertEquals(c.u(), c.part().mirrored().u());
			}
			assertEquals(TablePart.SINGLE, k.partValues().get(0), "compact is the default state");
		}
		assertEquals(2, MachineKind.SLOT.localCells().size());
		assertEquals(TablePart.UPPER, MachineKind.SLOT.localCells().get(1).part());
		assertNull(TablePart.at(0, 3, 0));
	}

	@Test
	void blockNames() {
		assertEquals(MachineKind.SLOT, MachineKind.ofBlockName("slot_machine_netherite"));
		assertEquals(MachineKind.WHEEL, MachineKind.ofBlockName("wheel_of_fortune"));
		assertEquals(MachineKind.PLINKO, MachineKind.ofBlockName("plinko_machine"));
		assertEquals(MachineKind.CASHIER, MachineKind.ofBlockName("nether_cashier"));
		assertNull(MachineKind.ofBlockName("blackjack_table"));
		for (String t : List.of("blackjack_table", "craps_table", "poker_table")) assertNull(MachineKind.ofBlockName(t));
		for (String m : List.of("cashier", "plinko_machine", "wheel_of_fortune", "slot_machine_gold")) assertNull(TableGame.ofBlockName(m));
	}

	@Test
	void boardFramesInsideTheFace() {
		for (MachineKind k : MachineKind.values()) {
			BoardFrame b = BoardFrame.of(k, TablePart.MAIN);
			int half = k.width() / 2;
			assertTrue(b.left() >= -half - 1e-9 && b.right() <= half + 1 + 1e-9, k + " inside the width");
			assertTrue(b.bottom() >= 0 && b.top() <= k.height() + 1e-9, k + " inside the height");
			assertTrue(b.faceZ() > 0.5 && b.faceZ() <= 1.0, k + " face near the front");
			assertTrue(!b.compact());
			BoardFrame c = BoardFrame.of(k, TablePart.SINGLE);
			assertTrue(c.compact(), k + " compact frame");
			if (k == MachineKind.PLINKO || k == MachineKind.CASHIER) {
				assertTrue(c.left() >= 0 && c.right() <= 1 && c.bottom() >= 0 && c.top() <= 1, k + " compact face on the block front");
			}
			assertTrue(BoardFrame.scaleCompactToFull(k) >= 1, k + " the full face is at least as big");
		}
		BoardFrame p = BoardFrame.of(MachineKind.PLINKO, TablePart.UPPER);
		assertEquals(272.0 / 204.0, p.w() / p.h(), 1e-9, "Plinko board keeps the 4:3 art");
		assertEquals(2.75, p.w(), 1e-9);
		BoardFrame s = BoardFrame.of(MachineKind.SLOT, TablePart.MAIN);
		assertTrue(s.bottom() < 1.62 && s.top() > 1.62, "slot reels at eye level");
		assertEquals(1.0, BoardFrame.scaleCompactToFull(MachineKind.SLOT), 1e-9, "slot cabinet unchanged");
	}
}

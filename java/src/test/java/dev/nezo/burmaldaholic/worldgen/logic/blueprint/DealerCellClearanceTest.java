package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Markers;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** DL: the dealer cell of every pod is two blocks of air holding a house dealer; a lectern there is an R4 error. */
class DealerCellClearanceTest {
	private static Blueprint pod(boolean compact) {
		Blueprint b = OrnamentTest.bp(9, 6, 9).compactTables(compact);
		b.room(0, 0, 0, 8, 5, 8);
		b.kit(Kits.tablePod(Game.BLACKJACK, null), 4, 1, 4, Facing.SOUTH);
		return b;
	}

	private static List<String> r4(Piece p) {
		List<String> out = new ArrayList<>();
		TableClearance.check(p, out);
		return out.stream().filter(s -> s.contains("R4")).toList();
	}

	@Test
	void podsPutAHouseDealerOnTwoBlocksOfAir() {
		for (boolean compact : new boolean[] {false, true}) {
			Piece p = pod(compact).build();
			assertTrue(r4(p).isEmpty(), r4(p).toString());
			long dealers = p.layout().markers().stream().filter(m -> m.marker() instanceof Markers.Npc n && n.role() == NpcRole.HOUSE_DEALER).count();
			assertEquals(1, dealers, "compact " + compact);
			var g = p.layout().grid();
			for (int x = 0; x < 9; x++) {
				for (int y = 0; y < 6; y++) {
					for (int z = 0; z < 9; z++) {
						BlockSpec s = g.get(x, y, z);
						assertTrue(s == null || !s.is("minecraft:lectern"), "no lectern at " + x + "," + y + "," + z);
					}
				}
			}
		}
	}

	@Test
	void aLecternInTheDealerCellIsReported() {
		Blueprint b = pod(false);
		b.set(4, 1, 2, BlockSpec.mc("lectern", "facing", "south", "has_book", "false", "powered", "false")); // f −2 behind the main block
		assertTrue(r4(b.build()).stream().anyMatch(s -> s.contains("lectern")), r4(b.build()).toString());
	}
}

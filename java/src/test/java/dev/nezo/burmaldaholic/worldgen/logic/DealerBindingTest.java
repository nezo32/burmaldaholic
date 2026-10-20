package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import java.util.List;
import org.junit.jupiter.api.Test;

/** DL-SPEC §1: the binding candidates invert the dealer cell of every table kind and facing, plus the legacy f − 1 cell. */
class DealerBindingTest {
	private static final int[][] FACINGS = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};

	@Test
	void everyGameAndFacingFindsItsMainBlock() {
		for (TableGame g : TableGame.values()) {
			for (int[] f : FACINGS) {
				int[] dealer = TableFootprint.dealerOffset(g, f[0], f[1]); // main -> dealer cell
				List<DealerBinding.Candidate> cs = DealerBinding.candidates(f[0], f[1]);
				assertTrue(cs.contains(new DealerBinding.Candidate(-dealer[0], -dealer[1], g)), g + " facing " + f[0] + "," + f[1]);
				// the main block is in front of the dealer (toward the players) and never on the dealer's own cell
				assertTrue(-dealer[0] * f[0] + -dealer[1] * f[1] == 2, g + ": main two rows in front");
			}
		}
	}

	@Test
	void legacyCompactTableIsOneCellInFront() {
		for (int[] f : FACINGS) {
			assertTrue(DealerBinding.candidates(f[0], f[1]).contains(new DealerBinding.Candidate(f[0], f[1], null)));
		}
	}
}

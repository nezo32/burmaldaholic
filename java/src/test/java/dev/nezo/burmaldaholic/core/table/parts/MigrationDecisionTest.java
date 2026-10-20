package dev.nezo.burmaldaholic.core.table.parts;

import static dev.nezo.burmaldaholic.core.table.parts.MigrationDecision.CellCheck.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nezo.burmaldaholic.core.table.parts.MigrationDecision.Outcome;
import java.util.List;
import org.junit.jupiter.api.Test;

class MigrationDecisionTest {
	@Test
	void matrix() {
		assertEquals(Outcome.GROW, MigrationDecision.of(List.of(FREE, FREE, FREE, FREE, FREE)));
		assertEquals(Outcome.GROW, MigrationDecision.of(List.of(FREE, CARPET, CARPET, FREE, FREE)), "carpet pops (★1)");
		assertEquals(Outcome.GROW, MigrationDecision.of(List.of(FREE, DEALER_MOVABLE, FREE, FREE, FREE)), "dealer moved");
		assertEquals(Outcome.COMPACT, MigrationDecision.of(List.of(FREE, DEALER_STUCK, FREE, FREE, FREE)));
		assertEquals(Outcome.COMPACT, MigrationDecision.of(List.of(FREE, FREE, FLOOR_MISSING, FREE, FREE)));
		assertEquals(Outcome.COMPACT, MigrationDecision.of(List.of(FREE, BLOCKED, FREE, FREE, FREE)));
		assertEquals(Outcome.RETRY, MigrationDecision.of(List.of(FREE, PLAYER, FREE, FREE, FREE)));
		assertEquals(Outcome.RETRY, MigrationDecision.of(List.of(OTHER_LIVING, FREE, FREE, FREE, FREE)));
		assertEquals(Outcome.RETRY, MigrationDecision.of(List.of(FREE, FREE, FREE, FREE, UNLOADED)));
		// permanent obstacles win over temporary ones: never touch blocks
		assertEquals(Outcome.COMPACT, MigrationDecision.of(List.of(PLAYER, BLOCKED, UNLOADED, FREE, FREE)));
		assertEquals(Outcome.COMPACT, MigrationDecision.of(List.of(UNLOADED, FLOOR_MISSING)));
		assertEquals(Outcome.GROW, MigrationDecision.of(List.of()));
	}
}

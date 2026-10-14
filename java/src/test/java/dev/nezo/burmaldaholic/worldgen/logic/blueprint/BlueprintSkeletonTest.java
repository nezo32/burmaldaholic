package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Nbt;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.TemplateExporter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves the DSL skeleton of docs/architecture/casino-buildings.md compiles, composes and passes its own
 * checks. The demo room is NOT a shipped piece (lane CB-2 replaces it with the real pieces + their tests).
 */
class BlueprintSkeletonTest {
	static Piece demoFloor() {
		Blueprint b = Blueprint.of("casino/demo/floor", CasinoKind.VILLAGE_CASINO, 23, 15, 17, Palettes.TUDOR);
		b.foundation(0, 0, 22, 16, 0);
		b.room(1, 1, 1, 21, 7, 15)
			.frame(5)
			.windows(Facing.WEST, 4, 2, 3)
			.windows(Facing.EAST, 4, 2, 3)
			.door(Facing.SOUTH, 11, 5, 4);
		b.fill(10, 1, 3, 12, 1, 15, Role.FLOOR_ACCENT);
		b.fill(2, 2, 2, 20, 2, 14, Role.CARPET);
		b.clear(9, 2, 13, 13, 2, 15); // door throat
		b.hipRoof(0, 0, 22, 16, 8, 4);
		b.marquee("CASINO", 11, 12, 16);
		b.kit(Kits.tablePod(Game.BLACKJACK, null), 5, 2, 6, Facing.SOUTH);
		b.kit(Kits.tablePod(Game.ROULETTE, null), 17, 2, 6, Facing.SOUTH);
		b.kit(Kits.slotBank(Game.SLOTS_COPPER, 5), 3, 2, 2, Facing.SOUTH);
		b.kit(Kits.cashierCage(Game.CASHIER), 11, 2, 3, Facing.SOUTH);
		for (int x = 5; x <= 17; x += 6) {
			for (int z = 4; z <= 12; z += 4) {
				b.kit(Kits.chandelier(2), x, 7, z, Facing.SOUTH);
			}
		}
		b.anchor(11, 5, 9);
		b.jigsaw(11, 1, 1, Facing.NORTH, "burmaldaholic:casino/floor_back", "burmaldaholic:casino/back_front",
			"burmaldaholic:worldgen/casino/demo/back", Palettes.TUDOR.block(Role.FLOOR));
		return b.build();
	}

	@Test
	void demoPiecePassesChecks() {
		Piece p = demoFloor();
		List<String> problems = Checks.run(p);
		assertTrue(problems.isEmpty(), String.join("\n", problems));
		assertEquals(Game.Family.REQUIRED.size(), Game.Family.values().length);
		assertTrue(Checks.families(List.of(p)).containsAll(List.of(Game.Family.BLACKJACK, Game.Family.ROULETTE,
			Game.Family.SLOTS, Game.Family.CASHIER)));
	}

	@Test
	void tableFacingIsThePlayerSideAndDealerIsBehind() {
		Blueprint b = Blueprint.of("casino/demo/t", CasinoKind.VILLAGE_CASINO, 7, 6, 7, Palettes.TUDOR);
		b.fill(0, 0, 0, 6, 0, 6, Role.FLOOR).clear(0, 1, 0, 6, 5, 6);
		b.kit(Kits.tablePod(Game.BACCARAT, NpcRole.BACCARAT_DEALER), 3, 1, 3, Facing.EAST);
		Piece p = b.build();
		assertEquals(BlockSpec.of(Game.BACCARAT.blockId(), "facing", "east", "part", "main"), p.layout().grid().get(3, 1, 3));
		assertEquals(BlockSpec.of(Game.BACCARAT.blockId(), "facing", "east", "part", "back"), p.layout().grid().get(2, 1, 3));
		assertEquals(1, p.layout().markers().getFirst().pos().x()); // dealer west of the table's back row (facing east)
		assertFalse(p.keepClear().isEmpty());
	}

	@Test
	void broken_layoutsAreReported() {
		Blueprint b = Blueprint.of("casino/demo/bad", CasinoKind.VILLAGE_CASINO, 5, 5, 5, Palettes.TUDOR);
		b.fill(0, 0, 0, 4, 0, 4, Role.FLOOR).clear(0, 1, 0, 4, 4, 4);
		b.game(Game.CRAPS, 2, 1, 2, Facing.SOUTH);
		b.set(2, 1, 3, Role.WALL); // blocks the player cell
		b.set(2, 3, 2, Role.WALL); // inside nothing's keep-clear, fine
		b.keepClear(2, 3, 2, 2, 3, 2); // ...until declared
		List<String> problems = Checks.run(b.build());
		assertTrue(problems.stream().anyMatch(s -> s.contains("player cell")), problems.toString());
		assertTrue(problems.stream().anyMatch(s -> s.contains("keep-clear")), problems.toString());
		assertTrue(problems.stream().anyMatch(s -> s.contains("is lit")), problems.toString());
	}

	@Test
	void exportsThroughTheExistingTemplateExporterDeterministically() {
		Piece p = demoFloor();
		Map<String, Object> a = TemplateExporter.template(p.layout());
		Map<String, Object> b = TemplateExporter.template(demoFloor().layout());
		assertArrayEquals(Nbt.writeGzip(a), Nbt.writeGzip(b));
		assertEquals("data/burmaldaholic/structure/worldgen/casino/demo/floor.nbt", TemplateExporter.templateFile(p.layout()));
	}
}

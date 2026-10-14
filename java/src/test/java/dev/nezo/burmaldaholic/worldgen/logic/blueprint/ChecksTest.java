package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoLoot;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every rule of {@link Checks} fires on a broken layout and stays quiet on a good one. */
class ChecksTest {
	/** A lit 9 × 6 × 9 room: floor at y 0, walls, a froglight ceiling grid; entry at the south door. */
	private static Blueprint room() {
		Blueprint b = Blueprint.of("casino/test/checks", CasinoKind.VILLAGE_CASINO, 9, 6, 9, Palettes.TUDOR);
		b.room(0, 0, 0, 8, 5, 8).door(Facing.SOUTH, 4, 1, 2);
		for (int x = 2; x <= 6; x += 2) {
			for (int z = 2; z <= 6; z += 2) {
				b.set(x, 5, z, Role.LIGHT);
			}
		}
		b.entry(4, 1, 8);
		return b;
	}

	private static List<String> problems(Blueprint b) {
		return Checks.run(b.build());
	}

	private static void assertProblem(Blueprint b, String fragment) {
		List<String> p = problems(b);
		assertTrue(p.stream().anyMatch(s -> s.contains(fragment)), "expected '" + fragment + "' in " + p);
	}

	@Test
	void goodRoomPasses() {
		Blueprint b = room();
		b.kit(Kits.tablePod(Game.BLACKJACK, null), 4, 1, 4, Facing.SOUTH);
		b.aisle(3, 1, 6, 5, 7);
		assertEquals(List.of(), problems(b));
	}

	@Test
	void floatingGame() {
		Blueprint b = room();
		b.set(4, 0, 4, Blueprint.AIR).game(Game.CRAPS, 4, 1, 4, Facing.SOUTH);
		assertProblem(b, "floats");
	}

	@Test
	void blockedPlayerCellAndNoHeadRoom() {
		Blueprint b = room();
		b.game(Game.CRAPS, 4, 1, 4, Facing.SOUTH).set(4, 1, 5, Role.WALL);
		assertProblem(b, "player cell");
		Blueprint c = room();
		c.game(Game.CRAPS, 4, 1, 4, Facing.SOUTH).set(4, 2, 5, Role.WALL);
		assertProblem(c, "head room");
	}

	@Test
	void keepClearAndAisles() {
		Blueprint b = room();
		b.keepClear(2, 2, 2, 2, 2, 2).set(2, 2, 2, Role.WALL);
		assertProblem(b, "keep-clear");
		Blueprint c = room();
		c.aisle(2, 1, 2, 2, 6);
		assertProblem(c, "narrower than 2");
		Blueprint d = room();
		d.aisle(2, 1, 2, 3, 6).set(3, 1, 4, BlockSpec.mc("barrel"));
		assertProblem(d, "aisle cell 3,1,4");
	}

	@Test
	void darkCellsAndUnderLitTables() {
		Blueprint b = Blueprint.of("casino/test/dark", CasinoKind.VILLAGE_CASINO, 7, 5, 7, Palettes.TUDOR);
		b.room(0, 0, 0, 6, 4, 6);
		b.game(Game.POKER, 3, 1, 3, Facing.SOUTH);
		assertProblem(b, "dark spawnable cell");
		assertProblem(b, "is lit 0");
	}

	@Test
	void candlesAndCampfiresEmitLight() {
		assertEquals(9, Checks.emission(BlockSpec.mc("red_candle", "candles", "3", "lit", "true")));
		assertEquals(0, Checks.emission(BlockSpec.mc("red_candle", "candles", "3", "lit", "false")));
		assertEquals(15, Checks.emission(BlockSpec.mc("campfire", "lit", "true")));
		assertEquals(0, Checks.emission(BlockSpec.mc("campfire", "lit", "false")));
		assertEquals(15, Checks.emission(BlockSpec.mc("lantern", "hanging", "true")));
	}

	@Test
	void unreachablePlayerCell() {
		Blueprint b = room();
		b.game(Game.UTH, 4, 1, 2, Facing.SOUTH);
		b.fill(1, 1, 5, 7, 2, 5, Role.WALL); // a wall across the room cuts the table off from the door
		assertProblem(b, "not reachable");
	}

	@Test
	void reachabilityClimbsOneStep() {
		Blueprint b = room();
		b.game(Game.UTH, 4, 2, 2, Facing.SOUTH);
		b.fill(1, 1, 1, 7, 1, 4, Role.FLOOR_ACCENT); // a raised dais, one step up
		assertFalse(problems(b).stream().anyMatch(s -> s.contains("reachable")), problems(b).toString());
	}

	@Test
	void markersNeedRoom() {
		Blueprint b = room();
		b.npc(NpcRole.CROUPIER, 2, 1, 2, Facing.SOUTH).set(2, 1, 2, Role.COUNTER);
		assertProblem(b, "no room to stand");
		Blueprint c = room();
		c.set(2, 0, 2, Blueprint.AIR).chest(CasinoLoot.Table.VILLAGE_CASINO, 2, 1, 2, Facing.SOUTH);
		assertProblem(c, "chest marker");
	}

	@Test
	void unsupportedDecor() {
		Blueprint b = room();
		b.set(2, 3, 2, BlockSpec.mc("lantern", "hanging", "true"));
		assertProblem(b, "hangs from nothing");
		Blueprint c = room();
		c.set(2, 2, 2, BlockSpec.mc("red_carpet"));
		assertProblem(c, "floats");
		Blueprint d = room();
		d.set(2, 2, 2, BlockSpec.mc("red_candle", "candles", "1", "lit", "true"));
		assertProblem(d, "stands on nothing");
		Blueprint e = room();
		e.set(4, 2, 4, BlockSpec.mc("red_wall_banner", "facing", "south"));
		assertProblem(e, "no wall behind");
		Blueprint f = room();
		f.set(2, 1, 2, BlockSpec.mc("flowering_azalea"));
		assertProblem(f, "needs moss");
	}

	@Test
	void supportedDecorIsQuiet() {
		Blueprint b = room();
		b.set(2, 4, 2, BlockSpec.mc("iron_chain", "axis", "y")).set(2, 3, 2, BlockSpec.mc("lantern", "hanging", "true"));
		b.set(6, 1, 2, Role.COUNTER).set(6, 2, 2, BlockSpec.mc("red_candle", "candles", "1", "lit", "true"));
		b.set(4, 2, 1, BlockSpec.mc("red_wall_banner", "facing", "south"));
		assertEquals(List.of(), problems(b));
	}

	@Test
	void familiesCollectsEveryGameFamily() {
		Blueprint b = room();
		b.game(Game.BACCARAT_PLAYER_BANKED, 2, 1, 2, Facing.SOUTH).game(Game.SLOTS_NETHERITE, 6, 1, 2, Facing.SOUTH);
		assertEquals(java.util.EnumSet.of(Game.Family.BACCARAT, Game.Family.SLOTS), Checks.families(List.of(b.build())));
	}

	@Test
	void shapesClassifyCommonBlocks() {
		assertTrue(Shapes.fullCube(BlockSpec.mc("stone_bricks")));
		assertFalse(Shapes.fullCube(BlockSpec.mc("oak_slab", "type", "bottom")));
		assertTrue(Shapes.fullCube(BlockSpec.mc("oak_slab", "type", "double")));
		assertFalse(Shapes.fullCube(BlockSpec.of("burmaldaholic:poker_table", "facing", "north")));
		assertTrue(Shapes.passable(BlockSpec.mc("red_carpet")));
		assertTrue(Shapes.transparent(BlockSpec.mc("glass")));
		assertFalse(Shapes.transparent(BlockSpec.mc("white_terracotta")));
		Grid g = new Grid(1, 1, 1);
		assertTrue(g.inside(0, 0, 0));
	}
}

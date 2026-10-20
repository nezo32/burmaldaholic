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

	// ---- clearance R1–R6 (multi-block tables, MT-DESIGN §6) and forbidden blocks --------------------------------

	/** A lit 17 × 6 × 11 hall (interior x 1..15, z 1..9), entry at the south door (8, 1, 10). */
	private static Blueprint hall() {
		return hall(6);
	}

	private static Blueprint hall(int h) {
		Blueprint b = Blueprint.of("casino/test/clearance", CasinoKind.VILLAGE_CASINO, 17, h, 11, Palettes.TUDOR);
		b.room(0, 0, 0, 16, h - 1, 10).door(Facing.SOUTH, 8, 1, 2);
		for (int x = 2; x <= 14; x += 2) {
			for (int z = 2; z <= 8; z += 2) {
				b.set(x, h - 1, z, Role.LIGHT);
			}
		}
		b.entry(8, 1, 10);
		return b;
	}

	private static List<String> clearance(Blueprint b) {
		return problems(b).stream().filter(s -> s.contains(" R") || s.contains("side cell") || s.contains("above the machine")
			|| s.startsWith("R5")).toList();
	}

	private static void assertRule(Blueprint b, String rule) {
		List<String> p = problems(b);
		assertTrue(p.stream().anyMatch(s -> s.contains(rule)), "expected '" + rule + "' in " + p);
	}

	@Test
	void fullTablePodPassesInEveryFacing() {
		for (Facing f : Facing.values()) {
			Blueprint b = hall();
			b.kit(Kits.tablePod(Game.BLACKJACK, null), 8, 1, 5, f);
			assertEquals(List.of(), problems(b), f.toString());
		}
		Blueprint r = hall();
		r.kit(Kits.tablePod(Game.ROULETTE, null), 8, 1, 5, Facing.SOUTH); // dealer at r −1
		assertEquals(List.of(), problems(r));
		assertEquals("air", r.build().layout().grid().get(7, 1, 3).name().substring(10), "dealer cell: two blocks of air (MT)");
	}

	@Test
	void r1Integrity() {
		Blueprint b = hall();
		b.kit(Kits.tablePod(Game.POKER, null), 8, 1, 5, Facing.SOUTH);
		b.set(9, 1, 4, BlockSpec.of(Game.POKER.blockId(), "facing", "south", "part", "back_left")); // wrong part
		assertRule(b, "R1");
		Blueprint c = hall();
		c.kit(Kits.tablePod(Game.POKER, null), 8, 1, 5, Facing.SOUTH);
		c.set(7, 1, 5, Blueprint.AIR);
		assertRule(c, "R1");
	}

	@Test
	void r2Support() {
		Blueprint b = hall();
		b.kit(Kits.tablePod(Game.CRAPS, null), 8, 1, 5, Facing.SOUTH);
		b.set(9, 0, 4, BlockSpec.mc("oak_slab", "type", "bottom", "waterlogged", "false"));
		assertRule(b, "R2");
	}

	@Test
	void r3Walkway() {
		Blueprint b = hall();
		b.kit(Kits.tablePod(Game.BLACKJACK, null), 8, 1, 5, Facing.SOUTH);
		b.set(7, 1, 6, BlockSpec.mc("barrel")); // the stool fixture of old pods: decor in the players' row
		assertRule(b, "R3 walkway cell 7,1,6");
		Blueprint c = hall();
		c.kit(Kits.tablePod(Game.BLACKJACK, null), 8, 1, 5, Facing.SOUTH);
		c.set(9, 2, 6, Role.WALL);
		assertRule(c, "R3 walkway cell 9,2,6");
		Blueprint d = hall();
		d.kit(Kits.tablePod(Game.BLACKJACK, null), 8, 1, 3, Facing.SOUTH);
		d.fill(1, 1, 5, 15, 2, 5, Role.WALL); // a wall cuts the walkway off from the door
		assertRule(d, "not reachable");
		Blueprint e = hall();
		e.kit(Kits.tablePod(Game.BLACKJACK, null), 8, 1, 5, Facing.SOUTH);
		e.set(8, 1, 6, BlockSpec.mc("oak_pressure_plate", "powered", "false"));
		assertEquals(List.of(), clearance(e));
	}

	@Test
	void r4Dealer() {
		Blueprint b = hall();
		b.kit(Kits.tablePod(Game.UTH, null), 8, 1, 5, Facing.SOUTH);
		b.set(8, 1, 3, BlockSpec.mc("barrel"));
		assertRule(b, "R4");
		Blueprint c = hall();
		c.kit(Kits.tablePod(Game.UTH, NpcRole.BACCARAT_DEALER), 8, 1, 5, Facing.SOUTH);
		c.set(8, 2, 3, BlockSpec.mc("lantern", "hanging", "false", "waterlogged", "false"));
		assertRule(c, "R4");
	}

	@Test
	void r5SideBySideNeedsPitchFour() {
		Blueprint b = hall();
		b.kit(Kits.tablePod(Game.BLACKJACK, null), 5, 1, 5, Facing.SOUTH);
		b.kit(Kits.tablePod(Game.BLACKJACK, null), 8, 1, 5, Facing.SOUTH); // pitch 3: footprints touch
		assertRule(b, "R5");
		Blueprint c = hall();
		c.game(Game.BLACKJACK, 5, 1, 5, Facing.SOUTH).game(Game.BLACKJACK, 9, 1, 5, Facing.SOUTH); // pitch 4
		assertEquals(List.of(), clearance(c));
	}

	@Test
	void r5FacingTablesNeedTwoFreeRows() {
		Blueprint b = hall();
		b.game(Game.BLACKJACK, 8, 1, 3, Facing.SOUTH).game(Game.BACCARAT, 8, 1, 5, Facing.NORTH); // one free row
		assertRule(b, "zones");
		Blueprint c = hall();
		c.game(Game.BLACKJACK, 8, 1, 3, Facing.SOUTH).game(Game.BACCARAT, 8, 1, 6, Facing.NORTH); // two free rows
		assertEquals(List.of(), clearance(c));
	}

	@Test
	void r5MachinesKeepAFreeCell() {
		Blueprint b = hall();
		b.game(Game.BLACKJACK, 8, 1, 5, Facing.SOUTH).game(Game.SLOTS_GOLD, 10, 1, 5, Facing.SOUTH);
		assertRule(b, "R5");
		Blueprint c = hall();
		c.game(Game.BLACKJACK, 8, 1, 5, Facing.SOUTH).game(Game.SLOTS_GOLD, 11, 1, 5, Facing.SOUTH);
		assertEquals(List.of(), clearance(c));
		Blueprint d = hall();
		d.game(Game.BLACKJACK, 8, 1, 5, Facing.SOUTH).game(Game.SLOTS_GOLD, 8, 1, 3, Facing.SOUTH); // in the dealer cell
		assertRule(d, "R5");
	}

	@Test
	void r6Headroom() {
		Blueprint b = hall();
		b.kit(Kits.tablePod(Game.ROULETTE, null), 8, 1, 5, Facing.SOUTH);
		b.set(9, 4, 4, Kits.lantern(b, true)); // hangs from the ceiling into the board's air
		assertRule(b, "R6 headroom cell 9,4,4");
	}

	@Test
	void machineSideCells() {
		// compact (1 × 1) machines of legacy pieces keep the side-cell rule
		Blueprint bank = hall().compactTables(true);
		bank.kit(Kits.slotBank(Game.SLOTS_COPPER, 4), 3, 1, 1, Facing.SOUTH);
		assertEquals(List.of(), clearance(bank)); // cabinets side by side: a bank
		Blueprint pot = hall().compactTables(true);
		pot.kit(Kits.slotBank(Game.SLOTS_GOLD, 2), 3, 1, 1, Facing.SOUTH).kit(Kits.pottedPlant(), 5, 1, 1, Facing.SOUTH);
		assertRule(pot, "side cell 5,1,1");
		Blueprint jamb = hall().compactTables(true);
		jamb.game(Game.WHEEL, 8, 1, 1, Facing.SOUTH).set(7, 2, 1, Role.ACCENT); // a jamb at r ±1, y + 1
		assertRule(jamb, "side cell 7,2,1");
		Blueprint alcove = hall(8).compactTables(true);
		alcove.kit(Kits.machineAlcove(Game.WHEEL), 8, 1, 1, Facing.SOUTH);
		assertEquals(List.of(), clearance(alcove));
	}

	/** MT-3 upright machines: integrity of every cell, support, a walkable front walkway across the width. */
	@Test
	void uprightMachines() {
		Blueprint alcove = hall(8);
		alcove.kit(Kits.machineAlcove(Game.WHEEL), 8, 1, 1, Facing.SOUTH);
		assertEquals(List.of(), clearance(alcove));
		assertEquals("upper_left", alcove.build().layout().grid().get(7, 2, 1).properties().get("part"));
		Blueprint blocked = hall(8);
		blocked.kit(Kits.machineAlcove(Game.PLINKO), 8, 1, 1, Facing.SOUTH).set(9, 1, 2, Role.ACCENT);
		assertRule(blocked, "front walkway cell 9,1,2");
		Blueprint broken = hall(8);
		broken.kit(Kits.machineAlcove(Game.WHEEL), 8, 1, 1, Facing.SOUTH).set(9, 3, 1, Role.ACCENT);
		assertRule(broken, "machine cell 9,3,1");
		Blueprint booth = hall();
		booth.kit(Kits.cashierCage(Game.CASHIER), 8, 1, 2, Facing.SOUTH);
		assertEquals(List.of(), clearance(booth));
		Blueprint bank = hall();
		bank.kit(Kits.slotBank(Game.SLOTS_COPPER, 4), 3, 1, 1, Facing.SOUTH);
		assertEquals(List.of(), clearance(bank));
		assertEquals("upper", bank.build().layout().grid().get(3, 2, 1).properties().get("part"));
	}

	@Test
	void forbiddenBlocks() {
		Blueprint b = hall();
		b.set(3, 3, 0, BlockSpec.mc("gold_block"));
		assertRule(b, "forbidden block minecraft:gold_block");
		Blueprint c = hall();
		c.set(3, 3, 0, BlockSpec.mc("red_shulker_box", "facing", "up"));
		assertRule(c, "forbidden");
		Blueprint d = hall();
		d.set(3, 3, 0, BlockSpec.mc("waxed_copper_block")).set(4, 3, 0, BlockSpec.mc("waxed_cut_copper"));
		assertTrue(problems(d).stream().noneMatch(s -> s.contains("forbidden")), problems(d).toString());
		assertTrue(Checks.forbidden(BlockSpec.mc("dragon_head")));
		assertFalse(Checks.forbidden(BlockSpec.mc("iron_bars")));
	}

	@Test
	void pendingLegacyPiecesKeepCompactTablesAndSkipOnlyTheClearance() {
		String id = "casino/tudor/floor_a";
		assertTrue(Checks.PENDING_CLEARANCE.contains(id));
		Blueprint b = Blueprint.of(id, CasinoKind.VILLAGE_CASINO, 17, 6, 11, Palettes.TUDOR);
		assertTrue(b.compactTables());
		b.room(0, 0, 0, 16, 5, 10);
		b.kit(Kits.tablePod(Game.BLACKJACK, null), 8, 1, 5, Facing.SOUTH);
		Grid g = b.build().layout().grid();
		assertEquals(BlockSpec.of(Game.BLACKJACK.blockId(), "facing", "south"), g.get(8, 1, 5));
		assertEquals("minecraft:air", g.get(8, 1, 4).name()); // DL: the legacy dealer cell (f −1) holds a house dealer, not a lectern
		assertFalse(Blueprint.of("casino/cr/hall", CasinoKind.VILLAGE_CASINO, 3, 3, 3, Palettes.TUDOR).compactTables());
	}
}

package dev.nezo.burmaldaholic.worldgen.logic.pieces;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import dev.nezo.burmaldaholic.worldgen.logic.VillageStyle;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Checks;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Piece;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.Shapes;
import dev.nezo.burmaldaholic.worldgen.logic.blueprint.TableClearance;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CR-2 site contract for the placement package (WG) and house dealers (DL): the Parlor's two doors, the Lounge's
 * ground skirt and stair, and two blocks of air on every house table's dealer cell.
 */
class CasinoSiteContractTest {
	private static List<Piece> cr2Pieces() {
		List<Piece> all = new ArrayList<>(OtherworldPieces.all());
		for (VillageStyle v : VillageStyle.values()) {
			all.add(VillageAnnex.build(v));
		}
		return all;
	}

	/** DL spawns a dealer NPC on every table's dealer cell: feet and head rows are plain air (no lectern). */
	@Test
	void everyDealerCellIsTwoBlocksOfAir() {
		for (Piece p : cr2Pieces()) {
			Grid g = p.layout().grid();
			int tables = 0;
			for (Piece.GameSpot s : p.games()) {
				if (s.game().tableGame() == null) {
					continue;
				}
				tables++;
				int[] d = TableClearance.dealer(s);
				assertNotNull(d, p.id() + " " + s);
				for (int dy = 0; dy <= 1; dy++) {
					BlockSpec b = g.get(d[0], s.pos().y() + dy, d[1]);
					assertTrue(b != null && b.is("minecraft:air"), p.id() + " " + s.game() + ": dealer cell " + d[0] + ","
						+ (s.pos().y() + dy) + "," + d[1] + " holds " + b);
				}
				BlockSpec floor = g.get(d[0], s.pos().y() - 1, d[1]);
				assertTrue(floor != null && !Shapes.passable(floor), p.id() + " " + s.game() + ": dealer stands on " + floor);
			}
			assertTrue(tables >= 5, p.id() + " has its house tables");
		}
	}

	/** Each Parlor door alone reaches every game: the building works with either the front or the back connected. */
	@Test
	void parlorFrontAndBackDoorEachReachEveryGame() {
		Piece p = OtherworldPieces.parlor();
		Grid g = p.layout().grid();
		assertEquals(List.of(PiglinParlor.FRONT_ENTRY, PiglinParlor.BACK_ENTRY), p.entries());
		assertEquals(PiglinParlor.SZ - 1, PiglinParlor.FRONT_ENTRY.z(), "front entry on the +z edge");
		assertEquals(0, PiglinParlor.BACK_ENTRY.z(), "back entry on the -z edge");
		for (Vec e : p.entries()) {
			assertDoorCell(g, e, p.id());
			boolean[][][] reach = Checks.reachable(g, List.of(e));
			for (Piece.GameSpot s : p.games()) {
				Vec c = s.playerCell();
				assertTrue(reach[c.x()][c.y()][c.z()], "from " + e + " to " + s.game() + " at " + c);
			}
		}
		// the two doors reach each other (a through route bastion <-> cave)
		assertTrue(Checks.reachable(g, List.of(PiglinParlor.FRONT_ENTRY))[PiglinParlor.BACK_ENTRY.x()][PiglinParlor.BACK_ENTRY.y()][0]);
	}

	/** The Parlor never relies on surrounding rock: no air under the foundation rows, nothing outside reaches lava. */
	@Test
	void parlorFoundationIsSolidUnderTheWholeFootprint() {
		Grid g = OtherworldPieces.parlor().layout().grid();
		for (int x = 0; x < PiglinParlor.SX; x++) {
			for (int z = 0; z < PiglinParlor.SZ; z++) {
				for (int y = 0; y <= 1; y++) {
					BlockSpec b = g.get(x, y, z);
					assertTrue(b != null && Shapes.fullCube(b), "foundation " + x + "," + y + "," + z + " = " + b);
				}
			}
		}
	}

	/** The Lounge stands on the ground: a solid skirt SKIRT rows deep under the whole plate (terrain spread ≤ SKIRT). */
	@Test
	void loungeSkirtIsSolidUnderThePlate() {
		Grid g = OtherworldPieces.lounge().layout().grid();
		assertEquals(4, HighRollerLounge.SKIRT);
		for (int x = 0; x < HighRollerLounge.SX; x++) {
			for (int z = 0; z <= HighRollerLounge.PZ; z++) {
				for (int y = 0; y <= HighRollerLounge.FLOOR; y++) {
					BlockSpec b = g.get(x, y, z);
					assertTrue(b != null && Shapes.fullCube(b), "skirt " + x + "," + y + "," + z + " = " + b);
				}
				BlockSpec above = g.get(x, HighRollerLounge.FR, z);
				assertNotNull(above, "the plate is carved above (no structure void over the floor) at " + x + "," + z);
			}
		}
	}

	/** The stair descends one row per step from the terrace to standing row 1; nothing is carved above it. */
	@Test
	void loungeStairRunsFromTheTerraceToTheLowestGround() {
		Piece p = OtherworldPieces.lounge();
		Grid g = p.layout().grid();
		assertEquals(List.of(HighRollerLounge.TERRACE_EDGE), p.entries());
		assertDoorCell(g, HighRollerLounge.TERRACE_EDGE, p.id());
		assertDoorCell(g, HighRollerLounge.DOOR, p.id());
		int x = HighRollerLounge.CX;
		for (int i = 0; i < HighRollerLounge.SKIRT; i++) {
			int z = HighRollerLounge.PZ + 1 + i, top = HighRollerLounge.FLOOR - 1 - i;
			BlockSpec step = g.get(x, top, z);
			assertTrue(step != null && step.name().endsWith("_stairs") && "north".equals(step.properties().get("facing")), z + ": " + step);
			for (int y = 0; y < top; y++) {
				assertTrue(Shapes.fullCube(g.get(x, y, z)), "solid under step " + x + "," + y + "," + z);
			}
			for (int y = top + 1; y < g.size().y(); y++) {
				assertEquals(null, g.get(x, y, z), "structure void over step " + x + "," + y + "," + z);
			}
		}
		assertEquals(new Vec(x, 1, HighRollerLounge.SZ - 1), HighRollerLounge.STAIR_FOOT);
		boolean[][][] reach = Checks.reachable(g, p.entries());
		for (Piece.GameSpot s : p.games()) {
			Vec c = s.playerCell();
			assertTrue(reach[c.x()][c.y()][c.z()], s.game() + " at " + c);
		}
		assertTrue(reach[HighRollerLounge.DOOR.x()][HighRollerLounge.DOOR.y()][HighRollerLounge.DOOR.z()]);
	}

	private static void assertDoorCell(Grid g, Vec e, String id) {
		BlockSpec under = g.get(e.x(), e.y() - 1, e.z());
		assertTrue(under != null && !Shapes.passable(under), id + " " + e + " stands on " + under);
		for (int dy = 0; dy <= 1; dy++) {
			BlockSpec b = g.get(e.x(), e.y() + dy, e.z());
			assertTrue(b != null && Shapes.passable(b), id + " " + e + " +" + dy + " = " + b);
		}
	}
}

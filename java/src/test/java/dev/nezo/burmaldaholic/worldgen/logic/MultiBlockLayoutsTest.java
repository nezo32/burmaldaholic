package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.table.parts.LocalCell;
import dev.nezo.burmaldaholic.core.table.parts.MachineKind;
import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.core.table.parts.TablePart;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * MT §6 clearance rule on the committed v0.1 layouts (re-laid for 3×2 tables): R1 integrity, R2 support, R3 player
 * walkway, R4 dealer cell, R5 spacing / disjoint zones, R6 headroom. Machines (slots, wheel, Plinko, cashier) are
 * still 1×1 here (MT-3 makes them multi-block); slot banks are exempt from machine–machine spacing.
 */
class MultiBlockLayoutsTest {
	private record Cell(int x, int z) {}

	private record Spot(String id, int x, int y, int z, Facing facing, TableGame game, MachineKind machine, List<Cell> footprint, List<Cell> walkway,
		Cell dealer) {}

	private static boolean isGameId(String name) {
		return name.startsWith("burmaldaholic:") && (TableGame.ofBlockName(name.substring(14)) != null || machine(name));
	}

	private static boolean machine(String name) {
		return name.contains("slot_machine") || name.contains("wheel_of_fortune") || name.contains("plinko_machine") || name.endsWith("cashier");
	}

	private static List<Spot> spots(Layout l) {
		Grid g = l.grid();
		Vec s = l.size();
		List<Spot> out = new ArrayList<>();
		for (int x = 0; x < s.x(); x++)
			for (int y = 0; y < s.y(); y++)
				for (int z = 0; z < s.z(); z++) {
					BlockSpec b = g.get(x, y, z);
					if (b == null || !isGameId(b.name())) continue;
					String part = b.properties().get("part");
					if (part != null && !part.equals("main") && !part.equals("single")) continue;
					Facing f = Facing.byId(b.properties().get("facing"));
					TableGame game = TableGame.ofBlockName(b.name().substring(14));
					if (game != null) {
						if (!"main".equals(b.properties().get("part"))) continue;
						List<Cell> fp = new ArrayList<>();
						for (TableFootprint.Cell c : TableFootprint.cells(game, f.dx(), f.dz())) fp.add(new Cell(x + c.dx(), z + c.dz()));
						List<Cell> walk = new ArrayList<>();
						for (int r = -1; r <= 1; r++) {
							int[] o = TableFootprint.cellOffset(r, 1, f.dx(), f.dz());
							walk.add(new Cell(x + o[0], z + o[1]));
						}
						int[] d = TableFootprint.dealerOffset(game, f.dx(), f.dz());
						out.add(new Spot(b.name(), x, y, z, f, game, null, fp, walk, new Cell(x + d[0], z + d[1])));
					} else {
						MachineKind m = MachineKind.ofBlockName(b.name().substring(14));
						assertNotNull(m, b.name());
						assertEquals("main", b.properties().get("part"), l.id() + " " + b.name() + " @" + x + "," + y + "," + z + " is a full machine");
						if (!"main".equals(b.properties().get("part"))) continue;
						List<Cell> fp = new ArrayList<>();
						List<Cell> walk = new ArrayList<>();
						for (LocalCell c : m.localCells()) {
							if (c.u() != 0) continue;
							int[] o = c.offset(f.dx(), f.dz());
							fp.add(new Cell(x + o[0], z + o[2]));
							walk.add(new Cell(x + o[0] + f.dx(), z + o[2] + f.dz()));
						}
						out.add(new Spot(b.name(), x, y, z, f, null, m, fp, walk, null));
					}
				}
		return out;
	}

	private static boolean air(Grid g, int x, int y, int z) {
		BlockSpec b = g.inside(x, y, z) ? g.get(x, y, z) : null;
		return b != null && (b.is("minecraft:air") || b.name().endsWith("_carpet") || b.name().endsWith("pressure_plate"));
	}

	private static boolean solid(Grid g, int x, int y, int z) {
		BlockSpec b = g.inside(x, y, z) ? g.get(x, y, z) : null;
		return b != null && !b.is("minecraft:air") && !b.name().endsWith("_carpet") && !b.name().contains("glass_pane");
	}

	private static int gap(List<Cell> a, List<Cell> b) {
		int best = Integer.MAX_VALUE;
		for (Cell p : a) for (Cell q : b) best = Math.min(best, Math.max(Math.abs(p.x() - q.x()), Math.abs(p.z() - q.z())));
		return best;
	}

	@Test
	void everyLayoutTableIsAFullFootprintWithClearance() {
		int tables = 0;
		for (Layout l : Layouts.all().values()) {
			Grid g = l.grid();
			List<Spot> spots = spots(l);
			for (Spot t : spots) {
				if (t.game() == null) continue;
				if (!l.id().startsWith("casino/")) tables++; // the v0.1 count; v0.2 pieces (CR-1 Grand Casino) are checked too

				String w = l.id() + " " + t.id() + " @" + t.x() + "," + t.z();
				// R1 integrity: every cell is this block with the facing and the right part
				for (TableFootprint.Cell c : TableFootprint.cells(t.game(), t.facing().dx(), t.facing().dz())) {
					BlockSpec b = g.get(t.x() + c.dx(), t.y(), t.z() + c.dz());
					assertNotNull(b, w + " cell missing");
					assertEquals(t.id(), b.name(), w + " cell block");
					assertEquals(t.facing().id(), b.properties().get("facing"), w + " cell facing");
					assertEquals(c.part().getSerializedName(), b.properties().get("part"), w + " cell part");
				}
				for (Cell c : t.footprint()) {
					// R2 support, R6 headroom
					assertTrue(solid(g, c.x(), t.y() - 1, c.z()), w + " R2 floor under " + c);
					for (int dy = 1; dy <= 3; dy++) assertTrue(air(g, c.x(), t.y() + dy, c.z()), w + " R6 headroom over " + c + " +" + dy);
				}
				for (Cell c : t.walkway()) {
					// R3 walkway rows y, y+1 walkable; R6
					assertTrue(air(g, c.x(), t.y(), c.z()) && air(g, c.x(), t.y() + 1, c.z()), w + " R3 walkway " + c);
					assertTrue(solid(g, c.x(), t.y() - 1, c.z()), w + " R3 walkway floor " + c);
					for (int dy = 2; dy <= 3; dy++) assertTrue(air(g, c.x(), t.y() + dy, c.z()), w + " R6 headroom over walkway " + c);
				}
				// R4 dealer cell: two blocks of air on a floor
				Cell d = t.dealer();
				assertTrue(air(g, d.x(), t.y(), d.z()) && air(g, d.x(), t.y() + 1, d.z()) && solid(g, d.x(), t.y() - 1, d.z()), w + " R4 dealer " + d);
			}
			// R5 spacing between every pair of game spots (slot/machine pairs exempt), disjoint zones, dealer outside zones
			for (int i = 0; i < spots.size(); i++)
				for (int j = i + 1; j < spots.size(); j++) {
					Spot a = spots.get(i);
					Spot b = spots.get(j);
					// slot banks stand side by side (no gap between two slot cabinets); everything else keeps R5
					boolean bothMachines = a.machine() == MachineKind.SLOT && b.machine() == MachineKind.SLOT;
					String w = l.id() + " " + a.id() + "@" + a.x() + "," + a.z() + " / " + b.id() + "@" + b.x() + "," + b.z();
					if (!bothMachines) {
						assertTrue(gap(a.footprint(), b.footprint()) >= 2, w + " R5 gap " + gap(a.footprint(), b.footprint()));
						Set<Cell> za = new HashSet<>(a.footprint());
						za.addAll(a.walkway());
						for (Cell c : b.footprint()) assertFalse(za.contains(c), w + " R5 zones overlap at " + c);
						for (Cell c : b.walkway()) assertFalse(za.contains(c), w + " R5 zones overlap at " + c);
						Set<Cell> zb = new HashSet<>(b.footprint());
						zb.addAll(b.walkway());
						if (a.dealer() != null) assertFalse(zb.contains(a.dealer()), w + " R5 dealer of the first in the other's zone");
						if (b.dealer() != null) assertFalse(za.contains(b.dealer()), w + " R5 dealer of the second in the other's zone");
					}
				}
			// machines: every cell is the machine block with its facing and part (R1), a floor under the bottom row (R2)
			// and a free walkway row in front (R3)
			for (Spot m : spots) {
				if (m.machine() == null) continue;
				String w = l.id() + " " + m.id() + " @" + m.x() + "," + m.z();
				for (LocalCell c : m.machine().localCells()) {
					int[] o = c.offset(m.facing().dx(), m.facing().dz());
					BlockSpec b = g.get(m.x() + o[0], m.y() + o[1], m.z() + o[2]);
					assertNotNull(b, w + " cell missing");
					assertEquals(m.id(), b.name(), w + " cell block");
					assertEquals(c.part().getSerializedName(), b.properties().get("part"), w + " cell part");
					assertEquals(m.facing().id(), b.properties().get("facing"), w + " cell facing");
				}
				for (Cell c : m.footprint()) assertTrue(solid(g, c.x(), m.y() - 1, c.z()), w + " R2 floor under " + c);
				for (Cell c : m.walkway()) {
					assertTrue(air(g, c.x(), m.y(), c.z()) && air(g, c.x(), m.y() + 1, c.z()), w + " R3 walkway " + c);
				}
			}
			// no stray parts: every non-main part belongs to a counted main
			Vec s = l.size();
			for (int x = 0; x < s.x(); x++)
				for (int z = 0; z < s.z(); z++)
					for (int y = 0; y < s.y(); y++) {
						BlockSpec b = g.get(x, y, z);
						if (b != null && b.properties().containsKey("part")) {
							assertFalse(TablePart.SINGLE.getSerializedName().equals(b.properties().get("part")), l.id() + " compact table in a template");
						}
					}
		}
		assertEquals(5 * 4 + 3 + 4, tables, "village ×5 (bj, roulette, uth, baccarat) + parlor 3 + lounge 4");
	}
}

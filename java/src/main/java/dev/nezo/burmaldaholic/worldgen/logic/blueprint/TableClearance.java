package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.ArrayList;
import java.util.List;

/**
 * Clearance of game blocks (PURE; run by {@link Checks#run}). Tables are real 3 × 2 multi-block tables
 * ({@code core/table/parts}, MT-DESIGN §1/§6); every footprint, walkway and dealer cell comes from
 * {@link TableFootprint} — never re-derive offsets here.
 *
 * <p>Tables ({@link Game#tableGame()} non-null), main block at the {@link Piece.GameSpot} pos, local frame r = players'
 * right, f = toward the players:
 * <ol>
 *   <li><b>R1 integrity</b>: every footprint cell holds the table block with the right {@code part} and {@code facing};</li>
 *   <li><b>R2 support</b>: the block below every footprint cell is solid to stand on;</li>
 *   <li><b>R3 walkway</b>: cells r −1..1 at f +1, rows y and y + 1, are walkable (air / carpet / pressure plate) and,
 *       when the piece declares entries, reachable on foot;</li>
 *   <li><b>R4 dealer</b>: the dealer cell ({@code TableFootprint.dealerCell}) is air at y (an NPC) or a lectern, and air
 *       at y + 1;</li>
 *   <li><b>R5 spacing</b>: on one floor row the Chebyshev gap between any two game footprints (tables, machines,
 *       cashiers) is ≥ 2 — at least one free cell — except slot next to slot (a bank); the zones (footprint ∪ walkway)
 *       of two tables are disjoint (tables facing each other need two free rows); no dealer cell lies in another
 *       table's zone. Side-by-side tables therefore stand at pitch ≥ 4;</li>
 *   <li><b>R6 headroom</b>: zone × rows y + 1..y + 3 are air (the in-world board, cards and plates).</li>
 * </ol>
 *
 * <p>Machines (1 × 1, unchanged): the Wheel disc (r 0.8 around y + 1.98, name plate up to y + 3.3) and the Plinko board
 * (1.6 × 1.2 from y + 1) reach 0.3 into the side cells, so side cells r ±1 stay walkable from y up to
 * y + {@link #machineRows} − 1 and the block column above stays air ({@link #machineHead}); a slot next to a slot is a
 * bank (exempt on that side). Cashiers have no clearance rule beyond R5.
 */
public final class TableClearance {
	private TableClearance() {}

	/** Rows above the floor row kept air over a table's zone (R6). */
	public static final int HEADROOM = 3;

	/** Rows (from the machine's own row) of the side cells a machine needs walkable. */
	public static int machineRows(Game g) {
		return switch (g.family()) {
			case WHEEL, PLINKO -> 3;
			default -> 2;
		};
	}

	/** Rows above a machine (in its own column) that must be air: slot marquee, plinko board + plate, wheel + plate. */
	public static int machineHead(Game g) {
		return switch (g.family()) {
			case WHEEL -> 3;
			case PLINKO -> 2;
			default -> 1;
		};
	}

	/** World cells {x, z} of a game's footprint (1 cell for machines / cashiers). */
	public static List<int[]> footprint(Piece.GameSpot s) {
		List<int[]> out = new ArrayList<>();
		TableGame tg = s.game().tableGame();
		if (tg == null) {
			out.add(new int[] {s.pos().x(), s.pos().z()});
			return out;
		}
		Facing f = s.playerSide();
		for (TableFootprint.Cell c : TableFootprint.cells(tg, f.dx(), f.dz())) {
			out.add(new int[] {s.pos().x() + c.dx(), s.pos().z() + c.dz()});
		}
		return out;
	}

	/** World cells {x, z} of a table's walkway (r −1..1 at f +1); empty for machines. */
	public static List<int[]> walkway(Piece.GameSpot s) {
		List<int[]> out = new ArrayList<>();
		if (s.game().tableGame() == null) {
			return out;
		}
		Facing f = s.playerSide();
		for (int r = -1; r <= 1; r++) {
			int[] o = TableFootprint.cellOffset(r, 1, f.dx(), f.dz());
			out.add(new int[] {s.pos().x() + o[0], s.pos().z() + o[1]});
		}
		return out;
	}

	/** World cell {x, z} of a table's dealer cell, null for machines. */
	public static int[] dealer(Piece.GameSpot s) {
		TableGame tg = s.game().tableGame();
		if (tg == null) {
			return null;
		}
		int[] o = TableFootprint.dealerOffset(tg, s.playerSide().dx(), s.playerSide().dz());
		return new int[] {s.pos().x() + o[0], s.pos().z() + o[1]};
	}

	/** Footprint ∪ walkway. */
	public static List<int[]> zone(Piece.GameSpot s) {
		List<int[]> out = footprint(s);
		out.addAll(walkway(s));
		return out;
	}

	/** Walkable for a person: air, carpet, pressure plate. */
	static boolean walkable(BlockSpec b) {
		return Checks.walkable(b) || b != null && b.name().endsWith("_pressure_plate");
	}

	/** Something to stand a table leg on. */
	static boolean supports(BlockSpec b) {
		if (b == null) {
			return false;
		}
		String n = b.name();
		if (n.endsWith("_slab")) {
			return !"bottom".equals(b.properties().get("type"));
		}
		if (n.endsWith("_stairs")) {
			return "top".equals(b.properties().get("half"));
		}
		return Shapes.fullCube(b);
	}

	/** Appends every clearance problem of the piece's games to {@code out}. */
	static void check(Piece p, List<String> out) {
		Grid g = p.layout().grid();
		boolean[][][] reach = p.entries().isEmpty() ? null : Checks.reachable(g, p.entries());
		for (Piece.GameSpot spot : p.games()) {
			if (spot.game().tableGame() != null) {
				table(g, spot, reach, out);
			} else if (spot.game().family() != Game.Family.CASHIER) {
				machine(p, g, spot, out);
			}
		}
		spacing(p, out);
	}

	private static String at(Piece.GameSpot s) {
		return s.game() + " at " + s.pos();
	}

	private static void table(Grid g, Piece.GameSpot spot, boolean[][][] reach, List<String> out) {
		Vec v = spot.pos();
		int y = v.y();
		Facing f = spot.playerSide();
		TableGame tg = spot.game().tableGame();
		// R1 integrity + R2 support
		for (TableFootprint.Cell c : TableFootprint.cells(tg, f.dx(), f.dz())) {
			int x = v.x() + c.dx(), z = v.z() + c.dz();
			if (!g.inside(x, y, z)) {
				out.add(at(spot) + ": R1 footprint cell " + x + "," + y + "," + z + " (" + c.part() + ") is outside the piece");
				continue;
			}
			BlockSpec b = g.get(x, y, z);
			if (b == null || !b.name().equals(spot.game().blockId()) || !f.id().equals(b.properties().get("facing"))
				|| !c.part().getSerializedName().equals(b.properties().get("part"))) {
				out.add(at(spot) + ": R1 footprint cell " + x + "," + y + "," + z + " should be " + c.part() + " facing " + f.id()
					+ " but holds " + b);
			}
			BlockSpec below = y > 0 ? g.get(x, y - 1, z) : null;
			if (!supports(below)) {
				out.add(at(spot) + ": R2 footprint cell " + x + "," + y + "," + z + " (" + c.part() + ") has no support (" + below + ")");
			}
		}
		// R3 walkway
		for (int[] c : walkway(spot)) {
			for (int dy = 0; dy <= 1; dy++) {
				if (g.inside(c[0], y + dy, c[1]) && !walkable(g.get(c[0], y + dy, c[1]))) {
					out.add(at(spot) + ": R3 walkway cell " + c[0] + "," + (y + dy) + "," + c[1] + " holds " + g.get(c[0], y + dy, c[1]));
				}
			}
			if (reach != null && g.inside(c[0], y, c[1]) && !reach[c[0]][y][c[1]]) {
				out.add(at(spot) + ": R3 walkway cell " + c[0] + "," + y + "," + c[1] + " is not reachable on foot from the entries");
			}
		}
		// R4 dealer
		int[] d = dealer(spot);
		if (g.inside(d[0], y, d[1])) {
			BlockSpec feet = g.get(d[0], y, d[1]);
			if (!(Checks.air(feet) || feet != null && feet.is("minecraft:lectern"))) {
				out.add(at(spot) + ": R4 dealer cell " + d[0] + "," + y + "," + d[1] + " holds " + feet);
			}
			if (g.inside(d[0], y + 1, d[1]) && !Checks.air(g.get(d[0], y + 1, d[1]))) {
				out.add(at(spot) + ": R4 dealer cell " + d[0] + "," + (y + 1) + "," + d[1] + " holds " + g.get(d[0], y + 1, d[1]));
			}
		} else {
			out.add(at(spot) + ": R4 dealer cell " + d[0] + "," + y + "," + d[1] + " is outside the piece");
		}
		// R6 headroom
		for (int[] c : zone(spot)) {
			for (int dy = 1; dy <= HEADROOM; dy++) {
				if (g.inside(c[0], y + dy, c[1]) && !Checks.air(g.get(c[0], y + dy, c[1]))) {
					out.add(at(spot) + ": R6 headroom cell " + c[0] + "," + (y + dy) + "," + c[1] + " holds " + g.get(c[0], y + dy, c[1]));
				}
			}
		}
	}

	private static void spacing(Piece p, List<String> out) {
		List<Piece.GameSpot> games = p.games();
		for (int i = 0; i < games.size(); i++) {
			Piece.GameSpot a = games.get(i);
			for (int j = i + 1; j < games.size(); j++) {
				Piece.GameSpot b = games.get(j);
				if (a.pos().y() != b.pos().y()) {
					continue;
				}
				boolean bank = a.game().family() == Game.Family.SLOTS && b.game().family() == Game.Family.SLOTS;
				int gap = gap(footprint(a), footprint(b));
				if (gap < 2 && !bank) {
					out.add("R5 " + at(a) + " and " + at(b) + ": footprints " + (gap == 0 ? "overlap" : "touch")
						+ " (need ≥ 1 free cell between them)");
				}
				if (a.game().tableGame() != null && b.game().tableGame() != null && gap(zone(a), zone(b)) == 0) {
					out.add("R5 " + at(a) + " and " + at(b) + ": zones (footprint ∪ walkway) overlap");
				}
				for (Piece.GameSpot[] pair : new Piece.GameSpot[][] {{a, b}, {b, a}}) {
					int[] d = dealer(pair[0]);
					if (d != null && contains(zone(pair[1]), d)) {
						out.add("R5 dealer cell " + d[0] + "," + d[1] + " of " + at(pair[0]) + " lies in the zone of " + at(pair[1]));
					}
				}
			}
		}
	}

	/** Chebyshev gap between two cell sets: 0 = shared cell, 1 = touching (also diagonally), 2 = one free cell. */
	static int gap(List<int[]> a, List<int[]> b) {
		int best = Integer.MAX_VALUE;
		for (int[] u : a) {
			for (int[] v : b) {
				best = Math.min(best, Math.max(Math.abs(u[0] - v[0]), Math.abs(u[1] - v[1])));
			}
		}
		return best;
	}

	private static boolean contains(List<int[]> cells, int[] c) {
		for (int[] u : cells) {
			if (u[0] == c[0] && u[1] == c[1]) {
				return true;
			}
		}
		return false;
	}

	private static void machine(Piece p, Grid g, Piece.GameSpot spot, List<String> out) {
		Vec v = spot.pos();
		Facing front = spot.playerSide();
		Game game = spot.game();
		Facing right = front.rotate(-1);
		for (int side : new int[] {-1, 1}) {
			int x = v.x() + side * right.dx(), z = v.z() + side * right.dz();
			if (game.family() == Game.Family.SLOTS && slotsAt(p, x, v.y(), z)) {
				continue; // a bank: cabinets side by side are the design
			}
			for (int dy = 0; dy < machineRows(game); dy++) {
				int y = v.y() + dy;
				if (g.inside(x, y, z) && !Checks.walkable(g.get(x, y, z))) {
					out.add(at(spot) + ": side cell " + x + "," + y + "," + z + " (r" + side + ") holds " + g.get(x, y, z));
				}
			}
		}
		for (int dy = 1; dy <= machineHead(game); dy++) {
			int y = v.y() + dy;
			if (g.inside(v.x(), y, v.z()) && !Checks.air(g.get(v.x(), y, v.z()))) {
				out.add(at(spot) + ": cell " + v.x() + "," + y + "," + v.z() + " above the machine holds " + g.get(v.x(), y, v.z()));
			}
		}
	}

	private static boolean slotsAt(Piece p, int x, int y, int z) {
		for (Piece.GameSpot o : p.games()) {
			if (o.pos().x() == x && o.pos().y() == y && o.pos().z() == z) {
				return o.game().family() == Game.Family.SLOTS;
			}
		}
		return false;
	}
}

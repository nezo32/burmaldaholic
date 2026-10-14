package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.worldgen.logic.BlockSpec;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Grid;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.List;

/**
 * Side clearance of game blocks (PURE; run by {@link Checks#run}): the space the in-world renderers draw beyond the
 * block itself must not be inside a neighbouring block.
 *
 * <p><b>Contract with the board renderer.</b> The felt dimensions, the 5 × 5 cell sampling ({@link #covers}) and the
 * blocked predicate ({@link #blocksOverhang}) are COPIES of {@code core/ui/v2/board/BoardGeometry} (Felt, inside,
 * overhangCells, blocksOverhang) and {@code client/board/BoardRenderer#blocked/covers} on the UI branch
 * ({@code feature/v020-table-ui}). This package cannot see those classes (worldgen logic is pure and lives on a
 * different branch until phase 3); the phase-3 {@code BoardClearanceContractTest} asserts both sides agree. Change
 * them together.
 *
 * <ul>
 *   <li>{@link Kind#BOARD} tables draw a felt overhang at the table's level. Every neighbour cell the felt covers must
 *       hold only blocks whose collision top is ≤ the felt ({@code FELT_Y − 0.05}: air, carpet, pressure plates,
 *       bottom slabs, lanterns, a lectern…); no other game block may stand at local (±2, 0) (both overhangs would meet
 *       in the gap, the renderer falls back to the 1 × 1 felt) — so two tables on one row stand ≥ 3 apart.</li>
 *   <li>{@link Kind#MACHINE}s draw above the block: the slot marquee inside the block column (y + 1), the Wheel disc
 *       (r 0.8 around y + 1.98, its name plate up to y + 3.3) and the Plinko board (1.6 × 1.2 from y + 1, plate above)
 *       reach 0.3 into the side cells. Side cells r ±1 stay walkable from y up to y + {@link #machineRows} − 1, the
 *       block column above stays air; a slot next to a slot is a bank (exempt on that side).</li>
 *   <li>{@link Kind#NONE}: the cashier has no world renderer beyond its block.</li>
 * </ul>
 */
public final class TableClearance {
	private TableClearance() {}

	/** y of the felt surface above the table block's bottom (BoardGeometry.TOP = 1 + FELT_LIFT). */
	public static final double FELT_Y = 1.0 + 1.0 / 64.0;
	/** Tolerance of the blocked predicate (BoardGeometry.blocksOverhang). */
	public static final double OVERHANG_EPS = 0.05;
	/** Two tables on one row: centre pitch at least this (one shared free cell is not enough). */
	public static final int MIN_PITCH = 3;

	public enum Kind {
		BOARD, MACHINE, NONE
	}

	/** Felt shapes (BoardGeometry.Felt): width along the table's long axis, depth toward the players, corner radii. */
	public enum Felt {
		/** Blackjack, baccarat, UTH. */
		CRESCENT(2.25, 1.25, 0.45, 0.10),
		/** Roulette, craps. */
		RECT(2.0, 1.4, 0.14, 0.14),
		/** Hold'em. */
		OVAL(2.0, 2.0, 0.75, 0.75);

		public final double w, d, frontR, backR;

		Felt(double w, double d, double frontR, double backR) {
			this.w = w;
			this.d = d;
			this.frontR = frontR;
			this.backR = backR;
		}

		double x0() {
			return 0.5 - w / 2;
		}

		double x1() {
			return 0.5 + w / 2;
		}

		double z0() {
			return 0.5 - d / 2;
		}

		double z1() {
			return 0.5 + d / 2;
		}

		int reachX() {
			return (int) Math.ceil(Math.max(0, (w - 1) / 2) - 1e-4);
		}

		int reachZ() {
			return (int) Math.ceil(Math.max(0, (d - 1) / 2) - 1e-4);
		}
	}

	public static Kind kind(Game g) {
		return switch (g.family()) {
			case BLACKJACK, BACCARAT, UTH, ROULETTE, CRAPS, POKER -> Kind.BOARD;
			case SLOTS, WHEEL, PLINKO -> Kind.MACHINE;
			case CASHIER -> Kind.NONE;
		};
	}

	/** The felt of a {@link Kind#BOARD} game, null otherwise. */
	public static Felt felt(Game g) {
		return switch (g.family()) {
			case BLACKJACK, BACCARAT, UTH -> Felt.CRESCENT;
			case ROULETTE, CRAPS -> Felt.RECT;
			case POKER -> Felt.OVAL;
			default -> null;
		};
	}

	/** Rows (from the machine's own row) of the side cells a machine needs walkable / air. */
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

	/** BoardGeometry.blocksOverhang: a neighbour whose collision top reaches the felt blocks the overhang. */
	public static boolean blocksOverhang(double collisionTopY) {
		return collisionTopY > FELT_Y - OVERHANG_EPS;
	}

	/** BoardGeometry.inside: (x, z) block-local (block = [0, 1]², z toward the players) lies on the felt. */
	static boolean inside(Felt f, double x, double z) {
		if (x < f.x0() || x > f.x1() || z < f.z0() || z > f.z1()) {
			return false;
		}
		double r = z > 0.5 ? f.frontR : f.backR;
		double cx = Math.max(f.x0() + r, Math.min(f.x1() - r, x));
		double cz = z > 0.5 ? Math.min(z, f.z1() - r) : Math.max(z, f.z0() + r);
		double dx = x - cx;
		double dz = z - cz;
		return dx * dx + dz * dz <= r * r + 1e-6;
	}

	/** BoardRenderer.covers: does the felt cover any part of table-local cell (dx, dz)? (5 × 5 samples.) */
	public static boolean covers(Felt f, int dx, int dz) {
		for (int a = 0; a < 5; a++) {
			for (int b = 0; b < 5; b++) {
				if (inside(f, dx + 0.1 + 0.2 * a, dz + 0.1 + 0.2 * b)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Neighbour cells {dx, dz} (dx along the long axis, dz toward the players) the felt covers. */
	public static List<int[]> feltCells(Felt f) {
		List<int[]> out = new java.util.ArrayList<>();
		for (int dx = -f.reachX(); dx <= f.reachX(); dx++) {
			for (int dz = -f.reachZ(); dz <= f.reachZ(); dz++) {
				if ((dx != 0 || dz != 0) && covers(f, dx, dz)) {
					out.add(new int[] {dx, dz});
				}
			}
		}
		return out;
	}

	/** World x of the table-local cell (r along the long axis = {@code front.rotate(-1)}, f toward the players). */
	static int wx(Vec v, Facing front, int r, int f) {
		return v.x() + r * front.rotate(-1).dx() + f * front.dx();
	}

	static int wz(Vec v, Facing front, int r, int f) {
		return v.z() + r * front.rotate(-1).dz() + f * front.dz();
	}

	/** Appends every side-clearance problem of the piece's games to {@code out}. */
	static void check(Piece p, List<String> out) {
		Grid g = p.layout().grid();
		for (Piece.GameSpot spot : p.games()) {
			switch (kind(spot.game())) {
				case BOARD -> board(p, g, spot, out);
				case MACHINE -> machine(p, g, spot, out);
				case NONE -> {
				}
			}
		}
	}

	private static void board(Piece p, Grid g, Piece.GameSpot spot, List<String> out) {
		Vec v = spot.pos();
		Facing front = spot.playerSide();
		for (int[] c : feltCells(felt(spot.game()))) {
			int x = wx(v, front, c[0], c[1]), z = wz(v, front, c[0], c[1]);
			if (!g.inside(x, v.y(), z)) {
				continue;
			}
			BlockSpec b = g.get(x, v.y(), z);
			if (blocksOverhang(Shapes.collisionTop(b))) {
				out.add(spot.game() + " at " + v + ": felt cell " + x + "," + v.y() + "," + z + " (r" + c[0] + " f" + c[1]
					+ ") is blocked by " + b);
			}
		}
		Facing right = front.rotate(-1);
		for (Piece.GameSpot o : p.games()) {
			if (o == spot || o.pos().y() != v.y()) {
				continue;
			}
			int ddx = o.pos().x() - v.x(), ddz = o.pos().z() - v.z();
			int r = ddx * right.dx() + ddz * right.dz();
			int f = ddx * front.dx() + ddz * front.dz();
			if (f == 0 && Math.abs(r) < MIN_PITCH && Math.abs(r) > 0) {
				out.add(spot.game() + " at " + v + ": " + o.game() + " at " + o.pos() + " stands " + Math.abs(r)
					+ " along the felt's long axis (overhangs meet; need ≥ " + MIN_PITCH + ")");
			}
		}
	}

	private static void machine(Piece p, Grid g, Piece.GameSpot spot, List<String> out) {
		Vec v = spot.pos();
		Facing front = spot.playerSide();
		Game game = spot.game();
		for (int side : new int[] {-1, 1}) {
			int x = wx(v, front, side, 0), z = wz(v, front, side, 0);
			if (game.family() == Game.Family.SLOTS && slotsAt(p, x, v.y(), z)) {
				continue; // a bank: cabinets side by side are the design
			}
			for (int dy = 0; dy < machineRows(game); dy++) {
				int y = v.y() + dy;
				if (g.inside(x, y, z) && !Checks.walkable(g.get(x, y, z))) {
					out.add(game + " at " + v + ": side cell " + x + "," + y + "," + z + " (r" + side + ") holds " + g.get(x, y, z));
				}
			}
		}
		for (int dy = 1; dy <= machineHead(game); dy++) {
			int y = v.y() + dy;
			if (g.inside(v.x(), y, v.z()) && !Checks.air(g.get(v.x(), y, v.z()))) {
				out.add(game + " at " + v + ": cell " + v.x() + "," + y + "," + v.z() + " above the machine holds " + g.get(v.x(), y, v.z()));
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

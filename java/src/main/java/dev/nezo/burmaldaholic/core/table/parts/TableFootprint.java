package dev.nezo.burmaldaholic.core.table.parts;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Pure footprint maths of multi-block casino tables (MT-DESIGN §1). Single source of truth for cell
 * offsets, the main-block inverse, seat points and the dealer cell — used by the block, worldgen,
 * bots and the board renderer.
 *
 * <p><b>Frames.</b> FACING is the side the players stand on; a facing is the unit step {@code (fx, fz)}
 * (SOUTH = (0, 1)). The players' right is {@code FACING.getCounterClockWise()} = {@code (fz, -fx)}
 * (SOUTH → EAST). A cell is addressed by local {@code (r, f)}: r cells to the players' right, f cells
 * toward the players. <b>Footprint-local</b> continuous coordinates {@code (x, z)} put the main block
 * at x∈[0,1], z∈[0,1] (x along the players' right, z toward the players), so a 3×2 table spans
 * x∈[-1,2], z∈[-1,1] with the felt centred on (0.5, 0.0).
 */
public final class TableFootprint {
	/** Distance of a seat point outside the outer rail edge. */
	public static final double SEAT_OUTSET = 0.35;
	/** Seat plate height above the table block's y (same as legacy {@code SeatPoints.HEIGHT}). */
	public static final double SEAT_HEIGHT = 1.6;
	/** Legacy compact-table seat radius from the block centre. */
	public static final double COMPACT_SEAT_RADIUS = 1.1;

	/** One footprint cell: world offset (dx, dz) from the main block and its part. */
	public record Cell(int dx, int dz, TablePart part) {}

	/** Placement order of the non-main parts (BFS from the main block). */
	public static final List<TablePart> PART_ORDER = List.of(TablePart.LEFT, TablePart.RIGHT, TablePart.BACK,
		TablePart.BACK_LEFT, TablePart.BACK_RIGHT);

	private TableFootprint() {}

	// ---- frame helpers -------------------------------------------------------------------------

	/** Players' right step x for facing (fx, fz). */
	public static int rightX(int fx, int fz) {
		return fz;
	}

	/** Players' right step z for facing (fx, fz). */
	public static int rightZ(int fx, int fz) {
		return -fx;
	}

	/** World offset {dx, dz} of local cell (r, f) from the main block. */
	public static int[] cellOffset(int r, int f, int fx, int fz) {
		return new int[] {r * rightX(fx, fz) + f * fx, r * rightZ(fx, fz) + f * fz};
	}

	/**
	 * World offset {dx, dz} of footprint-local point (x, z) from the main block's min corner (its
	 * {@code BlockPos}); add it to {@code pos.getX()/getZ()}.
	 */
	public static double[] toWorld(double x, double z, int fx, int fz) {
		double u = x - 0.5;
		double v = z - 0.5;
		return new double[] {0.5 + u * rightX(fx, fz) + v * fx, 0.5 + u * rightZ(fx, fz) + v * fz};
	}

	/** Inverse of {@link #toWorld}: footprint-local {x, z} of a world offset from the main block's min corner. */
	public static double[] toLocal(double dx, double dz, int fx, int fz) {
		double ox = dx - 0.5;
		double oz = dz - 0.5;
		double u = ox * rightX(fx, fz) + oz * rightZ(fx, fz);
		double v = ox * fx + oz * fz;
		return new double[] {u + 0.5, v + 0.5};
	}

	// ---- cells ---------------------------------------------------------------------------------

	/** All cells of a full table (main first, then {@link #PART_ORDER}). */
	public static List<Cell> cells(TableGame game, int fx, int fz) {
		List<Cell> out = new ArrayList<>(game.width() * game.depth());
		out.add(new Cell(0, 0, TablePart.MAIN));
		for (TablePart p : PART_ORDER) {
			int[] o = cellOffset(p.r(), p.f(), fx, fz);
			out.add(new Cell(o[0], o[1], p));
		}
		return out;
	}

	/** Local cells of a full table (main first, then {@link #PART_ORDER}); all at u = 0. */
	public static List<LocalCell> localCells(TableGame game) {
		List<LocalCell> out = new ArrayList<>(game.width() * game.depth());
		out.add(new LocalCell(0, 0, 0, TablePart.MAIN));
		for (TablePart p : PART_ORDER) {
			out.add(new LocalCell(p.r(), 0, p.f(), p));
		}
		return out;
	}

	/** World offset {dx, dz} from a part's block to its main block (inverse of the part's cell offset). */
	public static int[] mainOffset(TablePart part, int fx, int fz) {
		int[] o = cellOffset(part.r(), part.f(), fx, fz);
		return new int[] {-o[0], -o[1]};
	}

	/** Local {r, f} of the dealer NPC cell. */
	public static int[] dealerCell(TableGame game) {
		return new int[] {game.dealerR(), game.dealerF()};
	}

	/** World offset {dx, dz} of the dealer NPC cell from the main block. */
	public static int[] dealerOffset(TableGame game, int fx, int fz) {
		return cellOffset(game.dealerR(), game.dealerF(), fx, fz);
	}

	/** Min/max world offsets {minDx, minDz, maxDx, maxDz} (inclusive cells) of the footprint around the main block. */
	public static int[] bounds(TableGame game, int fx, int fz) {
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (Cell c : cells(game, fx, fz)) {
			minX = Math.min(minX, c.dx());
			minZ = Math.min(minZ, c.dz());
			maxX = Math.max(maxX, c.dx());
			maxZ = Math.max(maxZ, c.dz());
		}
		return new int[] {minX, minZ, maxX, maxZ};
	}

	// ---- seats ---------------------------------------------------------------------------------

	/**
	 * Footprint-local {x, z} of seat {@code index} of {@code seats}. Compact tables ({@code part ==
	 * SINGLE}) keep the legacy half circle of radius 1.1 round the block centre. Full tables put seats
	 * {@link #SEAT_OUTSET} outside the outer rail: CRESCENT/RECT/ROULETTE over the players' half (seat 0
	 * at the dealer's right = players' left, sweeping to the players' right), OVAL all the way round
	 * minus the dealer slot. Height: {@link #SEAT_HEIGHT}.
	 */
	public static double[] seatPoint(TableGame game, TablePart part, int index, int seats) {
		int n = Math.max(1, seats);
		int i = Math.max(0, Math.min(index, n - 1));
		if (part == TablePart.SINGLE) {
			double a = Math.PI * (i + 0.5) / n;
			return new double[] {0.5 - COMPACT_SEAT_RADIUS * Math.cos(a), 0.5 + COMPACT_SEAT_RADIUS * Math.sin(a)};
		}
		double theta = game.shape() == TableGame.FeltShape.OVAL
			? -Math.PI / 2 + 2 * Math.PI * (i + 1) / (n + 1)
			: Math.PI * (i + 0.5) / n;
		double dx = -Math.cos(theta);
		double dz = Math.sin(theta);
		FeltFrame frame = FeltFrame.of(game, TablePart.MAIN);
		// outer rail outline = the whole footprint (centre (0.5, 0)), rounded where the felt is
		double hx = game.width() / 2.0 + SEAT_OUTSET;
		double hz = game.depth() / 2.0 + SEAT_OUTSET;
		double rc = frame.outerCornerRadius(dz > 0) + SEAT_OUTSET;
		double t = rayRoundedRect(dx, dz, hx, hz, rc);
		return new double[] {0.5 + t * dx, 0.0 + t * dz};
	}

	/** World offset {dx, dz} of a seat point from the main block's min corner, for facing (fx, fz). */
	public static double[] seatOffset(TableGame game, TablePart part, int index, int seats, int fx, int fz) {
		double[] p = seatPoint(game, part, index, seats);
		return toWorld(p[0], p[1], fx, fz);
	}

	/** Distance along unit ray (dx, dz) from the centre to a rounded rectangle (half sizes hx, hz, corner radius rc). */
	static double rayRoundedRect(double dx, double dz, double hx, double hz, double rc) {
		double tx = Math.abs(dx) < 1e-12 ? Double.POSITIVE_INFINITY : hx / Math.abs(dx);
		double tz = Math.abs(dz) < 1e-12 ? Double.POSITIVE_INFINITY : hz / Math.abs(dz);
		double t = Math.min(tx, tz);
		double px = t * dx;
		double pz = t * dz;
		double r = Math.min(rc, Math.min(hx, hz));
		if (r > 0 && Math.abs(px) > hx - r && Math.abs(pz) > hz - r) {
			double cx = Math.signum(dx) * (hx - r);
			double cz = Math.signum(dz) * (hz - r);
			double b = dx * cx + dz * cz;
			double disc = b * b - (cx * cx + cz * cz) + r * r;
			t = b + Math.sqrt(Math.max(0, disc));
		}
		return t;
	}

	// ---- Minecraft convenience (no logic; delegates to the pure maths above) --------------------

	public static BlockPos cellPos(BlockPos main, Direction facing, int r, int f) {
		int[] o = cellOffset(r, f, facing.getStepX(), facing.getStepZ());
		return main.offset(o[0], 0, o[1]);
	}

	public static BlockPos partPos(BlockPos main, Direction facing, TablePart part) {
		return cellPos(main, facing, part.r(), part.f());
	}

	/** Main block position for a part at {@code partPos} — pure maths, works when the blocks are gone. */
	public static BlockPos mainPos(BlockPos partPos, Direction facing, TablePart part) {
		int[] o = mainOffset(part, facing.getStepX(), facing.getStepZ());
		return partPos.offset(o[0], 0, o[1]);
	}

	public static BlockPos dealerPos(BlockPos main, Direction facing, TableGame game) {
		return cellPos(main, facing, game.dealerR(), game.dealerF());
	}

	/**
	 * Dealer NPC cell (R3 owner decision: a real dealer stands at every house table): the block the dealer's FEET
	 * occupy, two rows behind the main block ({@code f = -2}; {@code r = -1} behind the wheel for roulette). House
	 * layouts keep it two blocks of air on a floor, reachable from the table's player walkway
	 * ({@code MultiBlockLayoutsTest}, {@code TableClearance} R4). Same as {@link #dealerPos}.
	 */
	public static BlockPos dealerCell(BlockPos main, Direction facing, TableGame game) {
		return dealerPos(main, facing, game);
	}

	/** Direction a dealer at {@link #dealerCell} faces: toward the table and the players, i.e. the table's FACING. */
	public static Direction dealerFacing(Direction facing) {
		return facing;
	}

	/** Entity yaw (degrees, {@code Entity.setYRot / setYHeadRot / yBodyRot}) of a dealer at {@link #dealerCell}. */
	public static float dealerYaw(Direction facing) {
		return dealerFacing(facing).toYRot();
	}

	/** Every cell position of a full table at {@code main}. */
	public static List<BlockPos> positions(BlockPos main, Direction facing, TableGame game) {
		List<BlockPos> out = new ArrayList<>();
		for (Cell c : cells(game, facing.getStepX(), facing.getStepZ())) {
			out.add(main.offset(c.dx(), 0, c.dz()));
		}
		return out;
	}
}

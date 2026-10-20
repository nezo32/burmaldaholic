package dev.nezo.burmaldaholic.core.table.parts;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TableFootprintTest {
	/** NORTH, EAST, SOUTH, WEST as (fx, fz), clockwise. */
	static final int[][] FACINGS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

	@Test
	void playersRightIsCounterClockwiseOfFacing() {
		// SOUTH → EAST, EAST → NORTH, NORTH → WEST, WEST → SOUTH
		assertArrayEquals(new int[] {1, 0}, new int[] {TableFootprint.rightX(0, 1), TableFootprint.rightZ(0, 1)});
		assertArrayEquals(new int[] {0, -1}, new int[] {TableFootprint.rightX(1, 0), TableFootprint.rightZ(1, 0)});
		assertArrayEquals(new int[] {-1, 0}, new int[] {TableFootprint.rightX(0, -1), TableFootprint.rightZ(0, -1)});
		assertArrayEquals(new int[] {0, 1}, new int[] {TableFootprint.rightX(-1, 0), TableFootprint.rightZ(-1, 0)});
	}

	@Test
	void sixDistinctCellsMainFirstAndInverse() {
		for (TableGame g : TableGame.values()) {
			for (int[] f : FACINGS) {
				List<TableFootprint.Cell> cells = TableFootprint.cells(g, f[0], f[1]);
				assertEquals(6, cells.size());
				assertEquals(TablePart.MAIN, cells.get(0).part());
				assertEquals(0, cells.get(0).dx());
				Set<Long> seen = new HashSet<>();
				for (TableFootprint.Cell c : cells) {
					assertTrue(seen.add(((long) c.dx() << 32) ^ (c.dz() & 0xffffffffL)), "distinct " + c);
					int[] back = TableFootprint.mainOffset(c.part(), f[0], f[1]);
					assertEquals(0, c.dx() + back[0], "inverse x " + c);
					assertEquals(0, c.dz() + back[1], "inverse z " + c);
					assertEquals(c.part(), TablePart.at(c.part().r(), c.part().f()));
				}
				// back row is opposite to FACING
				for (TableFootprint.Cell c : cells) {
					int along = c.dx() * f[0] + c.dz() * f[1];
					assertEquals(c.part().f(), along);
				}
				int[] b = TableFootprint.bounds(g, f[0], f[1]);
				assertEquals(3 * 2, (b[2] - b[0] + 1) * (b[3] - b[1] + 1));
			}
		}
	}

	@Test
	void southFacingConcreteCells() {
		// facing SOUTH: right = EAST(+x), back = NORTH(-z)
		assertArrayEquals(new int[] {1, 0}, TableFootprint.cellOffset(TablePart.RIGHT.r(), TablePart.RIGHT.f(), 0, 1));
		assertArrayEquals(new int[] {-1, -1}, TableFootprint.cellOffset(TablePart.BACK_LEFT.r(), TablePart.BACK_LEFT.f(), 0, 1));
		assertArrayEquals(new int[] {0, -2}, TableFootprint.dealerOffset(TableGame.BLACKJACK, 0, 1));
		assertArrayEquals(new int[] {-1, -2}, TableFootprint.dealerOffset(TableGame.ROULETTE, 0, 1));
	}

	@Test
	void rotationInvariance() {
		// rotating the facing 90° clockwise rotates every cell offset 90° clockwise: (x, z) → (-z, x)
		for (TableGame g : TableGame.values()) {
			for (int k = 0; k < 4; k++) {
				int[] f0 = FACINGS[k];
				int[] f1 = FACINGS[(k + 1) % 4];
				List<TableFootprint.Cell> a = TableFootprint.cells(g, f0[0], f0[1]);
				List<TableFootprint.Cell> b = TableFootprint.cells(g, f1[0], f1[1]);
				for (int i = 0; i < a.size(); i++) {
					assertEquals(a.get(i).part(), b.get(i).part());
					assertEquals(-a.get(i).dz(), b.get(i).dx());
					assertEquals(a.get(i).dx(), b.get(i).dz());
				}
				double[] s0 = TableFootprint.seatOffset(g, TablePart.MAIN, 1, 5, f0[0], f0[1]);
				double[] s1 = TableFootprint.seatOffset(g, TablePart.MAIN, 1, 5, f1[0], f1[1]);
				assertEquals(-(s0[1] - 0.5), s1[0] - 0.5, 1e-9);
				assertEquals(s0[0] - 0.5, s1[1] - 0.5, 1e-9);
			}
		}
	}

	@Test
	void mirrorSwapsLeftAndRight() {
		// mirroring world x (FRONT_BACK of a table facing N/S keeps facing; LEFT_RIGHT flips facing) —
		// a table facing N/S mirrored in x keeps facing, cells map to mirrored parts.
		for (TableGame g : TableGame.values()) {
			for (int[] f : new int[][] {{0, 1}, {0, -1}}) {
				for (TableFootprint.Cell c : TableFootprint.cells(g, f[0], f[1])) {
					int[] m = TableFootprint.cellOffset(c.part().mirrored().r(), c.part().mirrored().f(), f[0], f[1]);
					assertEquals(-c.dx(), m[0], g + " " + c);
					assertEquals(c.dz(), m[1]);
				}
			}
			// facing E/W mirrored in x: facing flips, parts swap
			for (int[] f : new int[][] {{1, 0}, {-1, 0}}) {
				for (TableFootprint.Cell c : TableFootprint.cells(g, f[0], f[1])) {
					int[] m = TableFootprint.cellOffset(c.part().mirrored().r(), c.part().mirrored().f(), -f[0], f[1]);
					assertEquals(-c.dx(), m[0], g + " " + c);
					assertEquals(c.dz(), m[1]);
				}
			}
		}
		for (TablePart p : TablePart.values()) {
			assertEquals(p, p.mirrored().mirrored());
		}
	}

	@Test
	void localWorldRoundTrip() {
		for (int[] f : FACINGS) {
			for (double x = -1; x <= 2; x += 0.25) {
				for (double z = -1; z <= 1; z += 0.25) {
					double[] w = TableFootprint.toWorld(x, z, f[0], f[1]);
					double[] l = TableFootprint.toLocal(w[0], w[1], f[0], f[1]);
					assertEquals(x, l[0], 1e-9);
					assertEquals(z, l[1], 1e-9);
				}
			}
			// main block local square maps onto the main block
			double[] c = TableFootprint.toWorld(0.5, 0.5, f[0], f[1]);
			assertArrayEquals(new double[] {0.5, 0.5}, c, 1e-9);
			// a cell's local square centre maps onto that cell's centre
			for (TablePart p : TablePart.values()) {
				double[] pc = TableFootprint.toWorld(p.r() + 0.5, p.f() + 0.5, f[0], f[1]);
				int[] o = TableFootprint.cellOffset(p.r(), p.f(), f[0], f[1]);
				assertEquals(o[0] + 0.5, pc[0], 1e-9);
				assertEquals(o[1] + 0.5, pc[1], 1e-9);
			}
		}
	}

	@Test
	void seatPointsOutsideTheTableAndOnThePlayersSide() {
		for (TableGame g : TableGame.values()) {
			FeltFrame frame = FeltFrame.of(g, TablePart.MAIN);
			for (int n = 1; n <= 7; n++) {
				for (int i = 0; i < n; i++) {
					double[] p = TableFootprint.seatPoint(g, TablePart.MAIN, i, n);
					assertFalse(frame.onFelt(p[0], p[1]), g + " seat " + i + "/" + n + " on felt");
					// outside the outer rail by about SEAT_OUTSET
					double gap = outsideDistance(g, frame, p[0], p[1]);
					assertTrue(gap > TableFootprint.SEAT_OUTSET - 0.02 && gap < TableFootprint.SEAT_OUTSET + 0.02,
						g + " seat " + i + "/" + n + " gap " + gap);
					if (g.shape() != TableGame.FeltShape.OVAL) {
						assertTrue(p[1] > 0, "players' half " + g);
					}
				}
				if (n >= 2) {
					// seat 0 on the players' left, last seat on their right
					double[] first = TableFootprint.seatPoint(g, TablePart.MAIN, 0, n);
					double[] last = TableFootprint.seatPoint(g, TablePart.MAIN, n - 1, n);
					assertTrue(first[0] < last[0], g + " order");
				}
			}
			// OVAL: no seat in the dealer slot (behind the table centre)
			if (g.shape() == TableGame.FeltShape.OVAL) {
				for (int i = 0; i < 6; i++) {
					double[] p = TableFootprint.seatPoint(g, TablePart.MAIN, i, 6);
					assertFalse(Math.abs(p[0] - 0.5) < 0.3 && p[1] < 0, "dealer slot free");
				}
			}
		}
	}

	@Test
	void compactSeatsMatchLegacySeatPoints() {
		// legacy SeatPoints.offset (dealer's right rx = -fz): seat offsets from the block centre, radius 1.1
		for (int[] f : FACINGS) {
			for (int n = 1; n <= 6; n++) {
				for (int i = 0; i < n; i++) {
					double angle = Math.PI * (i + 0.5) / n;
					double rx = -f[1], rz = f[0];
					double ex = 1.1 * (f[0] * Math.sin(angle) + rx * Math.cos(angle));
					double ez = 1.1 * (f[1] * Math.sin(angle) + rz * Math.cos(angle));
					double[] w = TableFootprint.seatOffset(TableGame.BLACKJACK, TablePart.SINGLE, i, n, f[0], f[1]);
					assertEquals(ex, w[0] - 0.5, 1e-9);
					assertEquals(ez, w[1] - 0.5, 1e-9);
				}
			}
		}
	}

	@Test
	void blockNames() {
		assertEquals(TableGame.BACCARAT, TableGame.ofBlockName("baccarat_table_player_banked"));
		assertEquals(TableGame.UTH, TableGame.ofBlockName("uth_table_high_roller"));
		assertEquals(TableGame.ROULETTE, TableGame.ofBlockName("roulette_table"));
		assertNotNull(TableGame.ofBlockName("craps_table"));
		assertNull(TableGame.ofBlockName("cashier"));
		assertNull(TableGame.ofBlockName("plinko_machine"));
		assertNull(TableGame.ofBlockName("wheel_of_fortune"));
		assertNull(TablePart.at(2, 0));
		assertEquals(TablePart.BACK_RIGHT, TablePart.byId("back_right"));
		assertEquals(TablePart.SINGLE, TablePart.values()[0], "default state first");
	}

	/** Distance outside the outer rail outline (rounded rect of the whole footprint), negative inside. */
	private static double outsideDistance(TableGame g, FeltFrame frame, double x, double z) {
		double hx = g.width() / 2.0, hz = g.depth() / 2.0;
		double rc = frame.outerCornerRadius(z > 0);
		double px = Math.abs(x - 0.5) - (hx - rc), pz = Math.abs(z) - (hz - rc);
		double ox = Math.max(px, 0), oz = Math.max(pz, 0);
		return Math.hypot(ox, oz) + Math.min(Math.max(px, pz), 0) - rc;
	}

	/**
	 * R3 dealers: for every game and facing the dealer cell is outside the footprint and outside the player walkway,
	 * directly behind the back row, and one step along the dealer's facing lands on the table (roulette: the wheel
	 * column BACK_LEFT, card tables / craps / poker: BACK); the yaw is the facing's.
	 */
	@Test
	void dealerCellBehindTheTableFacingIt() {
		net.minecraft.core.BlockPos main = new net.minecraft.core.BlockPos(10, 64, -7);
		for (TableGame g : TableGame.values())
			for (net.minecraft.core.Direction facing : new net.minecraft.core.Direction[] {net.minecraft.core.Direction.NORTH,
				net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST}) {
				net.minecraft.core.BlockPos d = TableFootprint.dealerCell(main, facing, g);
				assertEquals(d, TableFootprint.dealerPos(main, facing, g));
				assertEquals(main.getY(), d.getY());
				List<net.minecraft.core.BlockPos> cells = TableFootprint.positions(main, facing, g);
				assertFalse(cells.contains(d), g + " " + facing + " dealer inside the footprint");
				for (int r = -1; r <= 1; r++) assertFalse(d.equals(TableFootprint.cellPos(main, facing, r, 1)), g + " " + facing + " dealer in the walkway");
				net.minecraft.core.Direction look = TableFootprint.dealerFacing(facing);
				assertEquals(facing, look);
				net.minecraft.core.BlockPos ahead = d.relative(look);
				TablePart expected = g == TableGame.ROULETTE ? TablePart.BACK_LEFT : TablePart.BACK;
				assertEquals(TableFootprint.partPos(main, facing, expected), ahead, g + " " + facing + " dealer faces the table");
				assertEquals(facing.toYRot(), TableFootprint.dealerYaw(facing), 1e-6);
				// roulette: the dealer stands behind the wheel bowl (footprint-local x centre -0.5)
				double[] local = TableFootprint.toLocal(d.getX() - main.getX() + 0.5, d.getZ() - main.getZ() + 0.5, facing.getStepX(), facing.getStepZ());
				assertEquals(g == TableGame.ROULETTE ? -0.5 : 0.5, local[0], 1e-9);
				assertEquals(-1.5, local[1], 1e-9);
			}
	}
}

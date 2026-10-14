package dev.nezo.burmaldaholic.core.table.parts;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Collision / outline of the upright machines (MT-3), authored once in the footprint-local frame (x = players' right,
 * y = up from the main block's bottom, z toward the players; the main block is x∈[0,1], y∈[0,1], z∈[0,1]) and cut
 * per part, rotated by facing. Matches the baked models / renderers: slot cabinet, wheel stand + disc, Plinko cabinet,
 * cashier counter + window wall.
 */
public final class MachineShapes {
	/** Local box: x0..x1, y0..y1, z0..z1. */
	public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {}

	private MachineShapes() {}

	private static final double P = 1 / 16.0;

	/** Local boxes of a whole machine. */
	public static List<Box> boxes(MachineKind kind) {
		List<Box> out = new ArrayList<>();
		switch (kind) {
			case SLOT -> {
				out.add(new Box(0, 0, 0, 1, 1, 1)); // base cabinet (the v0.1 block)
				out.add(new Box(P, 1, 2 * P, 1 - P, 2, 14 * P)); // upper cabinet with reels, glass and topper
			}
			case WHEEL -> {
				out.add(new Box(-1 + 2 * P, 0, 4 * P, 2 - 2 * P, 4 * P, 14 * P)); // plinth
				out.add(new Box(6 * P, 0, 4 * P, 10 * P, 1, 8 * P)); // post (behind the wheel plane)
				// disc: stepped circle (1/8 block rows) in the wheel plane
				BoardFrame f = BoardFrame.of(kind, TablePart.MAIN);
				double r = f.w() / 2;
				for (double y = f.bottom(); y < f.top() - 1e-9; y += 0.125) {
					double ym = y + 0.0625 - f.cy();
					double hw = Math.sqrt(Math.max(0, r * r - ym * ym));
					if (hw > 0.05) out.add(new Box(f.cx() - hw, y, 6 * P, f.cx() + hw, y + 0.125, 11 * P));
				}
			}
			case PLINKO -> {
				out.add(new Box(-1 + P, 0, 0, 2 - P, 8 * P, 1)); // base cabinet
				out.add(new Box(-1 + P, 8 * P, 4 * P, 2 - P, 3 - P, 1)); // upright board cabinet
			}
			case CASHIER -> {
				out.add(new Box(-1, 0, 0, 2, 1, 1)); // counter
				out.add(new Box(-1, 1, 8 * P, 2, 2 - 2 * P, 13 * P)); // window wall with the grille
			}
		}
		return out;
	}

	/** Shape of one part (block-local, facing applied); full block for unknown parts. */
	public static VoxelShape shape(MachineKind kind, @Nullable LocalCell cell, Direction facing) {
		if (cell == null) {
			return Shapes.block();
		}
		int fx = facing.getStepX();
		int fz = facing.getStepZ();
		int[] co = TableFootprint.cellOffset(cell.r(), cell.f(), fx, fz);
		VoxelShape shape = Shapes.empty();
		for (Box b : boxes(kind)) {
			double x0 = Math.max(b.x0(), cell.r());
			double x1 = Math.min(b.x1(), cell.r() + 1);
			double y0 = Math.max(b.y0(), cell.u());
			double y1 = Math.min(b.y1(), cell.u() + 1);
			double z0 = Math.max(b.z0(), cell.f());
			double z1 = Math.min(b.z1(), cell.f() + 1);
			if (x1 - x0 < 1e-6 || y1 - y0 < 1e-6 || z1 - z0 < 1e-6) {
				continue;
			}
			double[] a = TableFootprint.toWorld(x0, z0, fx, fz);
			double[] c = TableFootprint.toWorld(x1, z1, fx, fz);
			shape = Shapes.or(shape, Shapes.box(clamp(Math.min(a[0], c[0]) - co[0]), y0 - cell.u(), clamp(Math.min(a[1], c[1]) - co[1]),
				clamp(Math.max(a[0], c[0]) - co[0]), y1 - cell.u(), clamp(Math.max(a[1], c[1]) - co[1])));
		}
		return shape.isEmpty() ? Shapes.box(6 * P, 6 * P, 6 * P, 10 * P, 10 * P, 10 * P) : shape.optimize();
	}

	private static double clamp(double v) {
		return Math.max(0, Math.min(1, v));
	}
}

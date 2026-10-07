package dev.nezo.burmaldaholic.games.roulette.present;

import dev.nezo.burmaldaholic.games.roulette.logic.LayoutGrid;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;

/**
 * Geometry of the roulette table's in-world board (REDESIGN.md §9.1, docs/architecture/ui-v2.md §7.6), in blocks, in
 * the table's local frame: origin at the centre of the block's top face, {@code +x} to the right of a player standing at
 * the front, {@code +z} toward the players (the front rail), {@code y} up from the felt. The renderer rotates this frame
 * by the block's {@code FACING}. Pure, so the placement of the wheel, the printed layout and every stack is tested.
 *
 * <p>Felt 2.0 × 1.4 blocks: the wheel bowl on the left, the printed layout on the right ({@code layout_w_<θ>.png},
 * generated with the same cell sizes as {@link #PRINT}), seat plates along the front rail.
 */
public final class RouletteWorld {
	public static final float FELT_W = 2.0f, FELT_D = 1.4f;
	/** Rail lip outside the felt. */
	public static final float RAIL = 0.08f;
	/** Wheel bowl centre and radius; the head radius (pockets) and the ball's track radius. */
	public static final float WHEEL_X = -0.52f, WHEEL_Z = -0.02f, WHEEL_R = 0.42f;
	public static final float HEAD_R = WHEEL_R * 76f / 104f;
	/** Ball radii on the bowl: resting in a pocket, running on the track (fractions of the bowl as in the GUI art). */
	public static final float BALL_POCKET_R = HEAD_R * 0.73f, BALL_TRACK_R = WHEEL_R * 0.82f;
	/** The printed layout: cell geometry in print px and the print's px per block. */
	public static final LayoutGrid PRINT = new LayoutGrid(10, 10, 12, 10, 8, 8, 0, 2);
	public static final float PX_PER_BLOCK = 144f;
	public static final float LAYOUT_X = 0.02f;
	public static final float LAYOUT_W = PRINT.width() / PX_PER_BLOCK;
	public static final float LAYOUT_D = PRINT.height() / PX_PER_BLOCK;
	public static final float LAYOUT_Z = -LAYOUT_D / 2 - 0.04f;
	/** Felt chip: disc diameter and thickness, ≤ 6 discs per stack. */
	public static final float CHIP_D = 0.07f, CHIP_H = 0.012f;
	public static final int MAX_DISCS = 6;
	/** Plates: 0.12 outside the front rail, 0.35 above the felt (§2.4). */
	public static final float PLATE_OUT = 0.12f, PLATE_UP = 0.35f;
	/** Table board: above the dealer (back) side. */
	public static final float BOARD_Z = -FELT_D / 2, BOARD_UP = 1.25f;

	private RouletteWorld() {}

	/** Local (x, z) of a stack on a spot: the exact cell / line / intersection of the printed layout. */
	public static float[] stack(Spot s) {
		int[] c = PRINT.center(s);
		return new float[] {LAYOUT_X + c[0] / PX_PER_BLOCK, LAYOUT_Z + c[1] / PX_PER_BLOCK};
	}

	/** Local (x, z) of the centre of a pocket's cell (the winning cell's marker). */
	public static float[] cell(int n) {
		var r = PRINT.cell(n);
		return new float[] {LAYOUT_X + (r.x() + r.w() / 2f) / PX_PER_BLOCK, LAYOUT_Z + (r.y() + r.h() / 2f) / PX_PER_BLOCK};
	}

	/** Plate anchor of seat {@code i} of {@code n} along the front rail (local x, z). */
	public static float[] plate(int i, int n) {
		int k = Math.max(1, n);
		float x = -FELT_W / 2 + 0.2f + (FELT_W - 0.4f) * (Math.max(0, Math.min(k - 1, i)) + 0.5f) / k;
		return new float[] {x, FELT_D / 2 + RAIL + PLATE_OUT};
	}

	/** Discs drawn for an amount (≥ 1, ≤ {@link #MAX_DISCS}; the amount itself is on the plate / tag). */
	public static int discs(long amount) {
		if (amount <= 0) return 0;
		int n = 1;
		for (long a = amount; a >= 5 && n < MAX_DISCS; a /= 3) n++;
		return n;
	}
}

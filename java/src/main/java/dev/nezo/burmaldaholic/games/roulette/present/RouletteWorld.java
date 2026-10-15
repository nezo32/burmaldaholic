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
	public static final float HEAD_R = WHEEL_R * 21f / 32f;
	/** Ball radii: resting in a pocket, running on the track (fractions of {@code entity/roulette/wheel_<θ>.png}'s 64-texel bowl). */
	public static final float BALL_POCKET_R = HEAD_R * 22.75f / 32f, BALL_TRACK_R = WHEEL_R * 25f / 32f;
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

	// ---- the 3D bowl (ROULETTE-ANIM-SPEC §4; VQA-INWORLD RO 1): centred on the furniture's bowl ----------------------
	/** Bowl floor of the full table's wheel housing (MT furniture: the bowl floor top is at 13.5/16). */
	public static final float BOWL_FLOOR_Y = 13.5f / 16f;
	/** Ball-track wall radius Rw as a fraction of the bowl opening radius (FeltFrame wheelDiameter/2 × 13/16 = 6.5 px). */
	public static final float BOWL_OPENING = 13f / 16f;
	public static final float RW_OF_OPENING = 0.985f;
	/** Heights above the bowl floor (blocks): rotor top, stator cone foot / top, ball track, track wall top. */
	public static final float Y_ROTOR = 0.018f, Y_CONE_FOOT = 0.022f, Y_TRACK = 0.046f, Y_WALL = 0.064f;
	/** Radii (fractions of Rw): rotor edge (the head's lip, 76/92), the cone's foot and its top (the track's inner edge). */
	public static final float R_ROTOR = 76f / 92f, R_CONE_FOOT = 0.835f, R_TRACK_IN = 0.935f;
	/** Ball billboard size (blocks per Rw) and its art-px height unit (1 art px = Rw / 92). */
	public static final float BALL_D = 0.068f, ART_PX = 1f / 92f;

	/** Rw (blocks) of a full table's bowl of diameter {@code wheelDiameter}. */
	public static float rw(double wheelDiameter) {
		return (float) (wheelDiameter / 2 * BOWL_OPENING * RW_OF_OPENING);
	}

	/** Surface height above the bowl floor at radius {@code r} (fraction of Rw): rotor, stator cone, track. */
	public static float surfaceY(double r, float rw) {
		float k = rw / 0.40f; // heights scale with the bowl
		if (r < R_ROTOR) return Y_ROTOR * k;
		if (r < R_CONE_FOOT) return Y_CONE_FOOT * k;
		if (r < R_TRACK_IN) return (float) (Y_CONE_FOOT + (Y_TRACK - Y_CONE_FOOT) * (r - R_CONE_FOOT) / (R_TRACK_IN - R_CONE_FOOT)) * k;
		return Y_TRACK * k;
	}

	/** LOD of the in-world wheel by camera distance (blocks): A ≤ 8 (all), B ≤ 16 (no streak), C ≤ 32 (ball hidden until rest). */
	public enum Lod {
		A, B, C;

		public static Lod of(double dist) {
			return dist <= 8 ? A : dist <= 16 ? B : C;
		}
	}

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

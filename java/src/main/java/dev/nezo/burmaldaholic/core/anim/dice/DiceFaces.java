package dev.nezo.burmaldaholic.core.anim.dice;

/**
 * Face-to-orientation table for rendered dice cubes (tables.md §2.6: "the final orientation shows d1 and d2 on the top
 * faces"). A standard die: opposite faces sum to 7. Pure.
 *
 * <p>Since v0.2.0 (PHASE2B §3.3) this is a thin view on {@link CubeGroup}: the canonical model there carries the same
 * labels ({@link #modelNormal} = {@code CubeGroup.normal} with the tray → MC axis swap {@code (x, y, z) → (x, z, y)}),
 * and {@link #groupIndex} names the group element of each {@link #layout}. The item model keeps using
 * {@link #upRotation}.
 */
public final class DiceFaces {
	/** Face indices of {@link #layout}. */
	public static final int TOP = 0;
	public static final int BOTTOM = 1;
	public static final int NORTH = 2;
	public static final int SOUTH = 3;
	public static final int EAST = 4;
	public static final int WEST = 5;

	private DiceFaces() {}

	/** Faces of a cube resting with {@code top} up: {top, bottom, north, south, east, west}. */
	public static int[] layout(int top) {
		if (top < 1 || top > 6) {
			throw new IllegalArgumentException("face " + top);
		}
		int north = 1;
		while (north == top || north == 7 - top) {
			north++;
		}
		int east = 1;
		while (east == top || east == 7 - top || east == north || east == 7 - north) {
			east++;
		}
		return new int[] {top, 7 - top, north, 7 - north, east, 7 - east};
	}

	/**
	 * The rotation that turns a cube modelled like {@link #layout layout(1)} (1 up, 6 down, 2 north, 5 south, 3 east,
	 * 4 west; the {@code extras/dice_display} item model) so that {@code face} points up, as {@code {axisX, axisY,
	 * axisZ, degrees}} (right-handed, counter-clockwise about the axis).
	 */
	public static double[] upRotation(int face) {
		return switch (face) {
			case 1 -> new double[] {1, 0, 0, 0};
			case 6 -> new double[] {1, 0, 0, 180};
			case 2 -> new double[] {1, 0, 0, 90};
			case 5 -> new double[] {1, 0, 0, -90};
			case 3 -> new double[] {0, 0, 1, 90};
			case 4 -> new double[] {0, 0, 1, -90};
			default -> throw new IllegalArgumentException("face " + face);
		};
	}

	/**
	 * The {@link CubeGroup} element (tray frame) of a die resting with {@code top} up and {@link #layout layout(top)}'s
	 * north face toward {@code −y}. (The legacy {@link #layout} picks east/west by label order, which is the mirror image
	 * of a real die for some tops; the group element is always a real rotation, so only top and north are matched.)
	 */
	public static int groupIndex(int top) {
		int north = layout(top)[NORTH];
		double[] v = new double[3];
		for (int k = 0; k < 4; k++) {
			int i = CubeGroup.index(top, k);
			int[] n = CubeGroup.normal(north);
			Quat.rotate(CubeGroup.element(i), n[0], n[1], n[2], v, 0);
			if (v[1] < -0.5) return i;
		}
		throw new IllegalStateException("no element for " + top);
	}

	/** Outward normal of {@code face} on the unrotated model ({@link #upRotation}). */
	public static int[] modelNormal(int face) {
		return switch (face) {
			case 1 -> new int[] {0, 1, 0};
			case 6 -> new int[] {0, -1, 0};
			case 2 -> new int[] {0, 0, -1};
			case 5 -> new int[] {0, 0, 1};
			case 3 -> new int[] {1, 0, 0};
			case 4 -> new int[] {-1, 0, 0};
			default -> throw new IllegalArgumentException("face " + face);
		};
	}
}

package dev.nezo.burmaldaholic.core.anim.dice;

/**
 * Face-to-orientation table for rendered dice cubes (tables.md §2.6: "the final orientation shows d1 and d2 on the top
 * faces"). A standard die: opposite faces sum to 7. Pure.
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

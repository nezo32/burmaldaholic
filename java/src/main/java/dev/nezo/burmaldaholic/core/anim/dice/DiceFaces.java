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
}

package dev.nezo.burmaldaholic.worldgen.logic.site;

import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import java.util.Arrays;

/**
 * A straight walk corridor (PURE): {@link #WIDTH} wide, {@link #HEIGHT} high, one step per block along {@code dir}
 * starting at the centre column {@code (x, z)}. {@code feet[i]} is the feet row of step {@code i}; neighbouring steps
 * differ by at most one block (a stair). The floor block of step {@code i} is {@code feet[i] - 1}; the shell (walls,
 * ceiling, the cell under the floor) is one block around the walk volume.
 *
 * @param x    centre column of step 0
 * @param z    centre column of step 0
 * @param dir  walking direction (away from the door)
 * @param feet feet row of every step
 */
public record Tunnel(int x, int z, Facing dir, int[] feet) {
	public static final int WIDTH = 3;
	public static final int HEIGHT = 4;

	public Tunnel {
		if (feet.length == 0) {
			throw new IllegalArgumentException("empty tunnel");
		}
		feet = feet.clone();
		for (int i = 1; i < feet.length; i++) {
			if (Math.abs(feet[i] - feet[i - 1]) > 1) {
				throw new IllegalArgumentException("step " + i + " climbs " + (feet[i] - feet[i - 1]));
			}
		}
	}

	public int length() {
		return feet.length;
	}

	public int feet(int i) {
		return feet[i];
	}

	public int[] feet() {
		return feet.clone();
	}

	/** Centre column of step {@code i}. */
	public int cx(int i) {
		return x + dir.dx() * i;
	}

	public int cz(int i) {
		return z + dir.dz() * i;
	}

	/** Sideways axis (offsets -1..1 are the walk columns, ±2 the walls). */
	public Facing side() {
		return dir.rotate(1);
	}

	/**
	 * The stair on step {@code i}'s floor, as the direction its high side faces (= the way up), or {@code null} for a
	 * full floor block. A step higher than the one before it is a stair facing forward; a step higher than the one
	 * after it is a stair facing back; a peak or a valley stays a full block.
	 */
	public Facing stair(int i) {
		boolean upFromPrev = i > 0 && feet[i] > feet[i - 1];
		boolean downToNext = i + 1 < feet.length && feet[i + 1] < feet[i];
		if (upFromPrev && !downToNext) {
			return dir;
		}
		if (downToNext && !upFromPrev) {
			return dir.opposite();
		}
		return null;
	}

	/** Box of the walk volume plus its shell. */
	public Geometry.Box box() {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int f : feet) {
			lo = Math.min(lo, f);
			hi = Math.max(hi, f);
		}
		Facing s = side();
		int ax = x - 2 * Math.abs(s.dx());
		int az = z - 2 * Math.abs(s.dz());
		int bx = cx(feet.length - 1) + 2 * Math.abs(s.dx());
		int bz = cz(feet.length - 1) + 2 * Math.abs(s.dz());
		return new Geometry.Box(Math.min(ax, bx), lo - 2, Math.min(az, bz), Math.max(ax, bx), hi + HEIGHT, Math.max(az, bz));
	}

	/** Box of the walk volume of steps {@code from..to} (no shell). */
	public Geometry.Box walkBox(int from, int to) {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int i = from; i <= to; i++) {
			lo = Math.min(lo, feet[i]);
			hi = Math.max(hi, feet[i]);
		}
		Facing s = side();
		int ax = cx(from) - Math.abs(s.dx());
		int az = cz(from) - Math.abs(s.dz());
		int bx = cx(to) + Math.abs(s.dx());
		int bz = cz(to) + Math.abs(s.dz());
		return new Geometry.Box(Math.min(ax, bx), lo, Math.min(az, bz), Math.max(ax, bx), hi + HEIGHT - 1, Math.max(az, bz));
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Tunnel t && t.x == x && t.z == z && t.dir == dir && Arrays.equals(t.feet, feet);
	}

	@Override
	public int hashCode() {
		return ((x * 31 + z) * 31 + dir.hashCode()) * 31 + Arrays.hashCode(feet);
	}

	@Override
	public String toString() {
		return "Tunnel[" + x + "," + z + " " + dir + " feet=" + Arrays.toString(feet) + "]";
	}
}

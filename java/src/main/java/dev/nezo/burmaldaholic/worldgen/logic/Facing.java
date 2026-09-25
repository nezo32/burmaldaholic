package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.Locale;

/**
 * Horizontal direction in template-local space, in clockwise order (like Minecraft's
 * {@code Direction}/{@code Rotation}: north → east → south → west).
 */
public enum Facing {
	NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

	private final int dx;
	private final int dz;

	Facing(int dx, int dz) {
		this.dx = dx;
		this.dz = dz;
	}

	public int dx() {
		return dx;
	}

	public int dz() {
		return dz;
	}

	/** Lower-case id as used in block states ({@code facing=south}). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Facing byId(String id) {
		for (Facing f : values()) {
			if (f.id().equals(id)) {
				return f;
			}
		}
		throw new IllegalArgumentException("unknown facing '" + id + "'");
	}

	/** This facing after {@code quarterTurns} clockwise quarter turns (negative = counter-clockwise). */
	public Facing rotate(int quarterTurns) {
		return values()[Math.floorMod(ordinal() + quarterTurns, 4)];
	}

	public Facing opposite() {
		return rotate(2);
	}

	/** Minecraft yaw of an entity looking this way (south = 0, west = 90, north = 180, east = -90). */
	public float yaw() {
		return switch (this) {
			case SOUTH -> 0f;
			case WEST -> 90f;
			case NORTH -> 180f;
			case EAST -> -90f;
		};
	}
}

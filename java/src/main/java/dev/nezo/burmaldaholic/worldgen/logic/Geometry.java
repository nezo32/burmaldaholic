package dev.nezo.burmaldaholic.worldgen.logic;

/**
 * Block-box math for placing casino templates (PURE). Rotations are clockwise quarter turns around
 * the template origin, exactly like Minecraft's {@code StructureTemplate.transform} with pivot 0:
 * {@code 1 = CLOCKWISE_90: (x, z) -> (-z, x)}, {@code 2 = 180: (-x, -z)}, {@code 3 = COUNTERCLOCKWISE_90: (z, -x)}.
 */
public final class Geometry {
	private Geometry() {}

	/** Inclusive block box. */
	public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		public static Box corners(Vec a, Vec b) {
			return new Box(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()),
				Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
		}

		public boolean contains(int x, int y, int z) {
			return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
		}

		/** Contains a (fractional) entity position, with an optional margin in blocks. */
		public boolean contains(double x, double y, double z, double margin) {
			return x >= minX - margin && x < maxX + 1 + margin && y >= minY - margin && y < maxY + 1 + margin
				&& z >= minZ - margin && z < maxZ + 1 + margin;
		}

		public boolean intersects(Box o) {
			return maxX >= o.minX && minX <= o.maxX && maxY >= o.minY && minY <= o.maxY && maxZ >= o.minZ && minZ <= o.maxZ;
		}

		public int xSpan() {
			return maxX - minX + 1;
		}

		public int zSpan() {
			return maxZ - minZ + 1;
		}

		public int centerX() {
			return (minX + maxX) / 2;
		}

		public int centerZ() {
			return (minZ + maxZ) / 2;
		}

		public Vec min() {
			return new Vec(minX, minY, minZ);
		}
	}

	/** Local offset after {@code quarterTurns} clockwise rotations around the origin. */
	public static Vec rotate(Vec local, int quarterTurns) {
		return switch (Math.floorMod(quarterTurns, 4)) {
			case 1 -> new Vec(-local.z(), local.y(), local.x());
			case 2 -> new Vec(-local.x(), local.y(), -local.z());
			case 3 -> new Vec(local.z(), local.y(), -local.x());
			default -> local;
		};
	}

	/** World position of a template-local position for a template placed at {@code origin}. */
	public static Vec toWorld(Vec origin, Vec local, int quarterTurns) {
		return origin.add(rotate(local, quarterTurns));
	}

	/** World box of a template of {@code size} placed at {@code origin} with the given rotation. */
	public static Box templateBox(Vec origin, Vec size, int quarterTurns) {
		Vec far = new Vec(size.x() - 1, size.y() - 1, size.z() - 1);
		return Box.corners(toWorld(origin, Vec.ZERO, quarterTurns), toWorld(origin, far, quarterTurns));
	}

	/** Template origin so that its rotated box starts at {@code min}. */
	public static Vec originForMin(Vec min, Vec size, int quarterTurns) {
		Box atZero = templateBox(Vec.ZERO, size, quarterTurns);
		return min.subtract(atZero.min());
	}

	/** Rotation that turns the template's +z (south) entrance to face {@code outward}. */
	public static int turnsToFace(Facing outward) {
		return switch (outward) {
			case SOUTH -> 0;
			case WEST -> 1;
			case NORTH -> 2;
			case EAST -> 3;
		};
	}

	/** Placement of a template: origin + rotation + resulting world box. */
	public record Placement(Vec origin, int quarterTurns, Box box) {}

	/**
	 * Places a template right next to {@code host} on its {@code side}, centred along that side, on
	 * the host's lowest level, entrance facing away from the host (the back faces it).
	 */
	public static Placement beside(Box host, Vec size, Facing side, int gap) {
		int turns = turnsToFace(side);
		boolean swap = turns % 2 == 1;
		int fx = swap ? size.z() : size.x();
		int fz = swap ? size.x() : size.z();
		int minX;
		int minZ;
		switch (side) {
			case EAST -> {
				minX = host.maxX() + 1 + gap;
				minZ = host.centerZ() - fz / 2;
			}
			case WEST -> {
				minX = host.minX() - gap - fx;
				minZ = host.centerZ() - fz / 2;
			}
			case SOUTH -> {
				minX = host.centerX() - fx / 2;
				minZ = host.maxZ() + 1 + gap;
			}
			default -> {
				minX = host.centerX() - fx / 2;
				minZ = host.minZ() - gap - fz;
			}
		}
		Vec min = new Vec(minX, host.minY(), minZ);
		Vec origin = originForMin(min, size, turns);
		return new Placement(origin, turns, templateBox(origin, size, turns));
	}

	/** Places a template centred on top of {@code base} (floor one block above it), entrance {@code facing}. */
	public static Placement onTop(Box base, Vec size, Facing facing) {
		int turns = turnsToFace(facing);
		boolean swap = turns % 2 == 1;
		int fx = swap ? size.z() : size.x();
		int fz = swap ? size.x() : size.z();
		Vec min = new Vec(base.centerX() - fx / 2, base.maxY() + 1, base.centerZ() - fz / 2);
		Vec origin = originForMin(min, size, turns);
		return new Placement(origin, turns, templateBox(origin, size, turns));
	}

	/**
	 * Places a template in front of a viewer standing on block {@code feet} and looking {@code look}:
	 * centred on the view line, {@code gap} blocks away, floor replacing the ground layer, entrance
	 * facing the viewer. Used by the op build command.
	 */
	public static Placement inFront(Vec feet, Vec size, Facing look, int gap) {
		int turns = turnsToFace(look.opposite());
		boolean swap = turns % 2 == 1;
		int fx = swap ? size.z() : size.x();
		int fz = swap ? size.x() : size.z();
		int minX;
		int minZ;
		switch (look) {
			case EAST -> {
				minX = feet.x() + 1 + gap;
				minZ = feet.z() - fz / 2;
			}
			case WEST -> {
				minX = feet.x() - gap - fx;
				minZ = feet.z() - fz / 2;
			}
			case SOUTH -> {
				minX = feet.x() - fx / 2;
				minZ = feet.z() + 1 + gap;
			}
			default -> {
				minX = feet.x() - fx / 2;
				minZ = feet.z() - gap - fz;
			}
		}
		Vec min = new Vec(minX, feet.y() - 1, minZ);
		Vec origin = originForMin(min, size, turns);
		return new Placement(origin, turns, templateBox(origin, size, turns));
	}

	/** Largest horizontal (Chebyshev) chunk distance from {@code chunkX/chunkZ} to any chunk the box touches. */
	public static int chunkReach(Box box, int chunkX, int chunkZ) {
		int dx = Math.max(Math.abs((box.minX() >> 4) - chunkX), Math.abs((box.maxX() >> 4) - chunkX));
		int dz = Math.max(Math.abs((box.minZ() >> 4) - chunkZ), Math.abs((box.maxZ() >> 4) - chunkZ));
		return Math.max(dx, dz);
	}
}

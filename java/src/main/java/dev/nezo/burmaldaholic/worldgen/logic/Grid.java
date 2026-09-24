package dev.nezo.burmaldaholic.worldgen.logic;

/** 3D block grid; {@code null} cells are structure void (the world block is kept). */
public final class Grid {
	private final int sx;
	private final int sy;
	private final int sz;
	private final BlockSpec[] cells;

	public Grid(int sx, int sy, int sz) {
		this.sx = sx;
		this.sy = sy;
		this.sz = sz;
		this.cells = new BlockSpec[sx * sy * sz];
	}

	public Vec size() {
		return new Vec(sx, sy, sz);
	}

	private int index(int x, int y, int z) {
		return (y * sz + z) * sx + x;
	}

	public boolean inside(int x, int y, int z) {
		return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz;
	}

	public void set(int x, int y, int z, BlockSpec b) {
		if (!inside(x, y, z)) {
			throw new IllegalArgumentException("grid set out of bounds " + x + "," + y + "," + z);
		}
		cells[index(x, y, z)] = b;
	}

	public BlockSpec get(int x, int y, int z) {
		return cells[index(x, y, z)];
	}

	/** Fills the inclusive box between the two corners. */
	public void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockSpec b) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					set(x, y, z, b);
				}
			}
		}
	}

	/** Hollow walls (no floor/ceiling) of the box x0..x1 × z0..z1 for rows y0..y1. */
	public void walls(int x0, int y0, int z0, int x1, int y1, int z1, BlockSpec b) {
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				set(x, y, z0, b);
				set(x, y, z1, b);
			}
			for (int z = z0; z <= z1; z++) {
				set(x0, y, z, b);
				set(x1, y, z, b);
			}
		}
	}
}

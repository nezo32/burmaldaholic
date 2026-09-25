package dev.nezo.burmaldaholic.worldgen.logic;

/** Integer block position / size (pure, no Minecraft types). */
public record Vec(int x, int y, int z) {
	public static final Vec ZERO = new Vec(0, 0, 0);

	public Vec add(Vec o) {
		return new Vec(x + o.x, y + o.y, z + o.z);
	}

	public Vec subtract(Vec o) {
		return new Vec(x - o.x, y - o.y, z - o.z);
	}
}

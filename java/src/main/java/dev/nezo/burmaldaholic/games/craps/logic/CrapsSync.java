package dev.nezo.burmaldaholic.games.craps.logic;

/**
 * What a craps table publishes to spectators and its in-world renderer (block entity update tag; tables.md §2.7): the
 * last roll's dice, its time and seed, the point before and after, the shooter's side and the table theme. Encoded as an
 * int array so the update tag stays tiny. Pure.
 *
 * @param rolls       roll counter (a change = a new throw)
 * @param rollTime    game time of the roll
 * @param seed        cosmetic seed of the throw path
 * @param pointBefore point before the roll (puck origin)
 * @param point       point now (0 = OFF)
 * @param shooterDir  0–3 horizontal direction from the table to the shooter (south, west, north, east)
 * @param event       {@link RollEvent.Kind} ordinal, -1 = none
 * @param theme       0 village, 1 bastion, 2 end
 */
public record CrapsSync(int rolls, long rollTime, int d1, int d2, int seed, int pointBefore, int point, int shooterDir, int event, int theme) {
	public static final int VERSION = 1;

	public int[] encode() {
		return new int[] {VERSION, rolls, (int) (rollTime >>> 32), (int) rollTime, d1, d2, seed, pointBefore, point, shooterDir, event, theme};
	}

	/** Decodes {@link #encode}; throws on an unknown format. */
	public static CrapsSync decode(int[] a) {
		if (a.length != 12 || a[0] != VERSION) {
			throw new IllegalArgumentException("craps sync v" + (a.length > 0 ? a[0] : -1));
		}
		long time = ((long) a[2] << 32) | (a[3] & 0xFFFFFFFFL);
		return new CrapsSync(a[1], time, a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11]);
	}
}

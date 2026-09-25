package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Casino attract mode (global.md §4.13) and the arrival flourish, PURE: the per-theme palette, the roofline marquee
 * lights of a generated casino (a chase that never shows symbols, numbers or "win" words — global §6.7), the ambient
 * roll schedule of casino blocks and the attract-chime timer. Client code samples these; the seeds are public
 * (block positions, tick counts).
 */
public final class AttractRules {
	/** Distance between two marquee bulbs along the roofline (blocks). */
	public static final int BULB_SPACING = 2;
	/** Chase: one bulb in {@link #CHASE_GROUP} is lit, advancing every {@link #CHASE_TICKS} ticks. */
	public static final int CHASE_GROUP = 4;
	public static final int CHASE_TICKS = 3;
	/** Bulbs further than this from the viewer are not drawn (budget). */
	public static final double BULB_VIEW = 40;
	/** Arrival: a light wave runs once around the roofline in this many ticks. */
	public static final int ARRIVAL_WAVE_TICKS = 30;
	/** Blocks within this radius of the viewer roll ambient particles (global §2.9). */
	public static final int AMBIENT_RADIUS = 16;
	/** Each casino block rolls once per this many ticks (staggered by position). */
	public static final int ROLL_TICKS = 40;
	/** Attract chime: idle for 60 s with a viewer within 8 blocks, then not again for 120 s. */
	public static final int CHIME_IDLE_TICKS = 1200;
	public static final int CHIME_COOLDOWN_TICKS = 2400;
	public static final double CHIME_RADIUS = 8;
	/** A player this close to a table / machine counts as playing it (ambient FX and chime pause). */
	public static final double BUSY_RADIUS = 2.5;

	/**
	 * Look of a casino theme (docs/design/visual/extras.md: themed locations): bulb colours (ARGB), the core particle
	 * used for bulbs and the one for ambient motes.
	 */
	public record Theme(int bulbOn, int bulbAlt, String bulbParticle, String moteParticle) {}

	public static final Theme VILLAGE = new Theme(0xFFFFD640, 0xFFFFF3A0, "chip_glint", "golden_mote");
	public static final Theme PIGLIN = new Theme(0xFFFF7A3C, 0xFFFFD640, "gold_burst", "golden_mote");
	public static final Theme END = new Theme(0xFFD696FF, 0xFF5CE8E0, "sparkle", "diamond_glint");

	private AttractRules() {}

	public static Theme theme(CasinoKind kind) {
		return switch (kind) {
			case VILLAGE_CASINO -> VILLAGE;
			case PIGLIN_PARLOR -> PIGLIN;
			case HIGH_ROLLER -> END;
		};
	}

	/**
	 * Bulb positions along the roofline of {@code box} (block-corner coordinates, y = top + 1): the perimeter walked
	 * clockwise from the min corner, one bulb every {@link #BULB_SPACING} blocks. Each entry is {@code {x, y, z}}.
	 */
	public static List<double[]> bulbs(Geometry.Box box) {
		List<double[]> out = new ArrayList<>();
		double y = box.maxY() + 1.15;
		int x0 = box.minX();
		int z0 = box.minZ();
		int x1 = box.maxX() + 1;
		int z1 = box.maxZ() + 1;
		for (int x = x0; x < x1; x += BULB_SPACING) out.add(new double[] {x, y, z0});
		for (int z = z0; z < z1; z += BULB_SPACING) out.add(new double[] {x1, y, z});
		for (int x = x1; x > x0; x -= BULB_SPACING) out.add(new double[] {x, y, z1});
		for (int z = z1; z > z0; z -= BULB_SPACING) out.add(new double[] {x0, y, z});
		return out;
	}

	/** Chase: is bulb {@code i} lit at client tick {@code tick}? One in four, stepping every three ticks. */
	public static boolean lit(int i, long tick) {
		return Math.floorMod(i - tick / CHASE_TICKS, (long) CHASE_GROUP) == 0;
	}

	/**
	 * Arrival wave: is bulb {@code i} of {@code n} lit {@code ticks} after the arrival? A bright head runs once around
	 * the roofline (with a 3-bulb tail) in {@link #ARRIVAL_WAVE_TICKS}; afterwards the normal chase takes over.
	 */
	public static boolean arrivalLit(int i, int n, long ticks) {
		if (n <= 0 || ticks < 0 || ticks >= ARRIVAL_WAVE_TICKS) return false;
		int head = (int) (ticks * n / ARRIVAL_WAVE_TICKS);
		int d = head - i;
		return d >= 0 && d < 3;
	}

	/** Does the block at (x, y, z) roll its ambient particle at {@code tick}? Once per {@link #ROLL_TICKS}, staggered. */
	public static boolean rolls(int x, int y, int z, long tick) {
		int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
		return Math.floorMod(tick + h, (long) ROLL_TICKS) == 0;
	}

	/**
	 * Attract-chime decision for one machine.
	 *
	 * @param now         client tick
	 * @param idleSince   tick since which nobody has been at the machine (first seen counts)
	 * @param lastChime   tick of the last chime ({@code Long.MIN_VALUE / 2} never)
	 * @param viewerNear  a viewer is within {@link #CHIME_RADIUS}
	 * @param busy        somebody is at the machine now
	 */
	public static boolean chime(long now, long idleSince, long lastChime, boolean viewerNear, boolean busy) {
		return viewerNear && !busy && now - idleSince >= CHIME_IDLE_TICKS && now - lastChime >= CHIME_COOLDOWN_TICKS;
	}
}

package dev.nezo.burmaldaholic.core.anim.dice;

/**
 * One sample of one die (PHASE2B §3.3, DI §2.1). Mutable and reused: {@link DiceRoll#sample} fills it without
 * allocating. Tray units (u = die half-edge), tray frame (x, y floor, z up).
 */
public final class DieSample {
	/** In the cup / hand, before release. */
	public static final int HELD = 0;
	/** Ballistic flight (between floor contacts; wall banks happen in the air). */
	public static final int AIR = 1;
	/** Settling flat and quarter-rolls over an edge (pivot about a floor edge). */
	public static final int ROLL = 2;
	/** Balanced on an edge near 45° before falling forward or back. */
	public static final int TEETER = 3;
	/** Flat, sliding to a stop while the yaw squares up to 90°. */
	public static final int SLIDE = 4;
	/** A damped partial tip over the leading edge and back, in place. */
	public static final int ROCK = 5;
	/** At rest: exact {@link CubeGroup} element at the rest point. */
	public static final int REST = 6;

	/** Centre position on the floor plane. */
	public double x;
	public double y;
	/** Height of the lowest point above the floor (≥ 0). */
	public double zLow;
	/** Height of the centre above the floor ({@code zLow + Quat.support(q)}). */
	public double zc;
	/** Vertical speed of the lowest point (u/s; ballistic in {@link #AIR}, 0 on the floor). */
	public double vz;
	/** MODEL orientation (relabelled: shows the server's face at rest), {@code {w, x, y, z}}. */
	public final double[] q = new double[4];
	/** Angular speed, rad/s. */
	public double omega;
	/** Horizontal speed of the centre, u/s. */
	public double speed;
	/** One of {@link #HELD} … {@link #REST}. */
	public int phase;
	/** Scratch quaternions of the hand-off blend ({@link Seg#BLEND}); keeps sampling allocation-free. */
	final double[] tmpA = new double[4];
	final double[] tmpB = new double[4];

	/** Copies {@code o} into this sample. */
	public DieSample set(DieSample o) {
		x = o.x;
		y = o.y;
		zLow = o.zLow;
		zc = o.zc;
		vz = o.vz;
		Quat.copy(o.q, q);
		omega = o.omega;
		speed = o.speed;
		phase = o.phase;
		return this;
	}

	/** Name of a phase (logs, tests). */
	public static String phaseName(int phase) {
		return switch (phase) {
			case HELD -> "HELD";
			case AIR -> "AIR";
			case ROLL -> "ROLL";
			case TEETER -> "TEETER";
			case SLIDE -> "SLIDE";
			case ROCK -> "ROCK";
			case REST -> "REST";
			default -> "?" + phase;
		};
	}
}

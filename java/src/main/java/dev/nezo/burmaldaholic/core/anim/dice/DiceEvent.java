package dev.nezo.burmaldaholic.core.anim.dice;

/**
 * Event kinds of a dice roll (PHASE2B §3.3, DI §2.1, CS §4.3), carried by
 * {@link dev.nezo.burmaldaholic.core.anim.motion.MotionEvent#kind()}. Fields of the events a {@link DiceRoll}
 * emits: {@code tMs} on the roll clock (0 = die 0 released), {@code obj} = die index (−1 for pair events),
 * {@code strength} 0–1 (impact speed / {@link #STRENGTH_SPEED}), {@code x, y} = contact point in tray units,
 * {@code aux} as documented per kind.
 */
public final class DiceEvent {
	/** A die hits the cup wall while shaking ({@link Shake}); aux = reversal index. */
	public static final int RATTLE = 0;
	/** A die leaves the cup / hand; aux = 0. */
	public static final int RELEASE = 1;
	/** A floor impact; aux = landing number (1 = first landing). */
	public static final int FLOOR = 2;
	/** A bank off a wall (rail, divider, pyramids); aux = wall index in {@link DiceTray#allWalls()} × 1000 + diamond
	 * index (pyramid walls; 0 on rails). */
	public static final int WALL = 3;
	/** A bank off a round bumper; aux = bumper index. */
	public static final int BUMPER = 4;
	/** Die against die: a brief brush near the floor (≤ {@link DiceSolver#CLACK_MS}); obj = −1, aux = the later die. */
	public static final int CLACK = 5;
	/** A quarter-roll lands on its next face; aux = roll number (1, 2, 3). */
	public static final int TIP = 6;
	/** The die balances on an edge; aux = +1 falls forward, −1 falls back. */
	public static final int TEETER = 7;
	/** The final rock starts; aux = 0. */
	public static final int ROCK = 8;
	/** The die is at rest; aux = 0 (events never depend on the result: read the face from the roll). */
	public static final int REST = 9;

	/** Impact speed (u/s) mapped to strength 1. */
	public static final double STRENGTH_SPEED = 30;

	private DiceEvent() {}

	/** Name of a kind (logs, tests, golden vectors). */
	public static String name(int kind) {
		return switch (kind) {
			case RATTLE -> "RATTLE";
			case RELEASE -> "RELEASE";
			case FLOOR -> "FLOOR";
			case WALL -> "WALL";
			case BUMPER -> "BUMPER";
			case CLACK -> "CLACK";
			case TIP -> "TIP";
			case TEETER -> "TEETER";
			case ROCK -> "ROCK";
			case REST -> "REST";
			default -> "?" + kind;
		};
	}
}

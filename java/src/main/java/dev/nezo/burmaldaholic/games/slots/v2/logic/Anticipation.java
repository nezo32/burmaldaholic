package dev.nezo.burmaldaholic.games.slots.v2.logic;

/**
 * Honest anticipation (SLOTS.md §10.3): {@code stopTimes(tape)} is the ONLY source of reel stop times for
 * the server settle timer, the Java screen, the BER and Bedrock. A reel slows down only because of symbols
 * already visible on stopped reels. SKELETON — lane S-J3 / S-B3.
 */
public final class Anticipation {
	/** Base stop of reel r (0-based): 600 + 150 × r ms (SLOTS.md §10.2). */
	public static final int FIRST_STOP_MS = 600;
	public static final int STAGGER_MS = 150;
	public static final int ANTICIPATE_GAP_MS = 1000;

	private Anticipation() {}

	/** Stop time of each reel in ms from spin start, at normal speed; {@code enabled} = {@code slots.anticipation}. */
	public static int[] stopTimes(MachineDef def, Window landed, boolean enabled) {
		throw new UnsupportedOperationException("slots v2 anticipation: lane S-J3");
	}

	/** Schedule without anticipation (used when the rule never fires). */
	public static int[] baseStopTimes() {
		int[] t = new int[5];
		for (int r = 0; r < 5; r++) t[r] = FIRST_STOP_MS + STAGGER_MS * r;
		return t;
	}
}

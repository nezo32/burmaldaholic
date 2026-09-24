package dev.nezo.burmaldaholic.core.anim;

/**
 * Which clock a beat runs on (tables.md §0.1, cards.md §0.2; docs/architecture/animation.md §3.2).
 *
 * <ul>
 *   <li>{@link #SHARED}: server-paced. Every viewer (player, seated players, spectators, BER, Bedrock entity)
 *       sees it at the same server tick. Ignores {@code anim.speed} and skip. Reduce motion changes how it
 *       looks, never when.</li>
 *   <li>{@link #LOCAL}: per-viewer decoration after the shared part (payout flights, banners, celebrations).
 *       Respects {@code anim.speed}; a click / sneak skips it to its terminal frame.</li>
 * </ul>
 */
public enum Clock {
	SHARED("S"),
	LOCAL("L");

	private final String code;

	Clock(String code) {
		this.code = code;
	}

	/** One-letter code used in the canonical timeline JSON. */
	public String code() {
		return code;
	}
}

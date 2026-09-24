package dev.nezo.burmaldaholic.games.slots.v2.logic;

/**
 * Compact, versioned text encoding of {@link SpinTape} for persistence (Java block entity / SavedData,
 * Bedrock dynamic property; worst case End + 40 free spins &lt; 1 200 characters, SLOTS.md §8.1). The SAME
 * string format in both editions (a Java-encoded tape decodes in TypeScript and vice versa; vectors in
 * {@code slots_engine.json}). SKELETON — lane S-J2 / S-B2.
 */
public final class TapeCodec {
	private TapeCodec() {}

	public static String encode(SpinTape tape) {
		throw new UnsupportedOperationException("slots v2 tape codec: lane S-J2");
	}

	public static SpinTape decode(String s) {
		throw new UnsupportedOperationException("slots v2 tape codec: lane S-J2");
	}
}

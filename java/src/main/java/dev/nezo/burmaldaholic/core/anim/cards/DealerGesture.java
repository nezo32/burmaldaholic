package dev.nezo.burmaldaholic.core.anim.cards;

/**
 * Dealer NPC gestures (docs/design/animation/cards.md §1.4): the id travels as a byte in the dealer entity's synced data
 * ({@code GESTURE} + {@code GESTURE_TICK}), set by the table on its beats; the shared {@code DealerModel} (lane J-L5)
 * maps each id to its pose curve. PURE: ids and lengths only. {@link #NONE} = idle.
 */
public enum DealerGesture {
	NONE(0),
	DEAL(300),
	FLIP(350),
	PEEK(600),
	PAY(450),
	SWEEP(500),
	SHUFFLE(1600),
	WAVE_OFF(300);

	/** Length in ms (the renderer returns to idle afterwards). */
	public final int ms;

	DealerGesture(int ms) {
		this.ms = ms;
	}

	public byte id() {
		return (byte) ordinal();
	}

	public static DealerGesture byId(int id) {
		DealerGesture[] v = values();
		return id >= 0 && id < v.length ? v[id] : NONE;
	}

	/** Length in ticks (rounded up). */
	public int ticks() {
		return (ms + 49) / 50;
	}

	/** Progress 0..1 of a gesture started {@code ageMs} ago; ≥ 1 means idle again. */
	public double progress(double ageMs) {
		return ms <= 0 || ageMs != ageMs ? 1 : Math.max(0, Math.min(1, ageMs / ms));
	}
}

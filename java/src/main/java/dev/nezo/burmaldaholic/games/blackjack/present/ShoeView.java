package dev.nezo.burmaldaholic.games.blackjack.present;

/**
 * The countable shoe as the table shows it (E5; owner: card counting must be possible). Counts come from the server's
 * {@code shoe} state and only cover cards that are ON THE FELT (a drawn-but-not-dealt card is not counted). There is
 * deliberately no running count: players count themselves. PURE: geometry of the shoe-depth bar and the tray fill.
 *
 * @param size    cards in the shoe (decks × 52); 0 = unknown (nothing is drawn)
 * @param dealt   cards dealt from this shoe (discard tray + cards on the felt)
 * @param cut     the cut card comes out when this many cards have been dealt
 * @param discard cards in the discard tray (dealt in earlier rounds)
 */
public record ShoeView(int size, int dealt, int cut, int discard) {
	public static final ShoeView NONE = new ShoeView(0, 0, 0, 0);

	public ShoeView {
		size = Math.max(0, size);
		dealt = Math.max(0, Math.min(size, dealt));
		cut = Math.max(0, Math.min(size, cut));
		discard = Math.max(0, Math.min(dealt, discard));
	}

	public boolean known() {
		return size > 0;
	}

	public int left() {
		return size - dealt;
	}

	/** The cut card has come out: the shoe is reshuffled when this round is collected. */
	public boolean cutOut() {
		return known() && dealt >= cut;
	}

	/** Width of the "cards left" fill in a bar of width {@code w} (full = a fresh shoe, shrinking to the left). */
	public int fillW(int w) {
		return known() ? (int) Math.round((double) w * left() / size) : 0;
	}

	/** Offset of the 1-px cut-card marker in a bar of width {@code w}: the fill reaches it when the cut card comes out. */
	public int cutX(int w) {
		if (!known() || w <= 0) return 0;
		return Math.max(0, Math.min(w - 1, (int) Math.round((double) w * (size - cut) / size)));
	}

	/** Height of the card stack in a discard tray of inner height {@code h} (a full tray = the whole shoe). */
	public int trayH(int h) {
		if (!known() || discard == 0 || h <= 0) return 0;
		return Math.max(1, (int) Math.round((double) h * discard / size));
	}
}

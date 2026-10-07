package dev.nezo.burmaldaholic.core.ui.v2;

/**
 * Result stamps (docs/architecture/ui-v2.md §2.1, D8): the outcome WORD of a hand / bet in the seat's outcome slot, the
 * net in its value pill. This class maps outcomes to words and colours and gives {@link SeatColumn#table} the widths
 * of a seat's outcome levels (hero: 2× word, 1× word; others: 1× word; then the implied dot), and decodes the chosen
 * level back into what to draw. Pure; widths are measured by the caller (font) so EN and RU both fit.
 */
public final class Stamps {
	private Stamps() {}

	/** Fill colour family of a stamp. */
	public enum Kind {
		WIN,
		LOSE,
		PUSH,
		GOLD
	}

	public enum Outcome {
		WIN(Kind.WIN, V2Keys.STAMP_WIN),
		LOSE(Kind.LOSE, V2Keys.STAMP_LOSE),
		PUSH(Kind.PUSH, V2Keys.STAMP_PUSH),
		BUST(Kind.LOSE, V2Keys.STAMP_BUST),
		BLACKJACK(Kind.GOLD, V2Keys.STAMP_BLACKJACK),
		NATURAL(Kind.GOLD, V2Keys.STAMP_NATURAL),
		SURRENDER(Kind.PUSH, V2Keys.STAMP_SURRENDER);

		public final Kind kind;
		public final String wordKey;

		Outcome(Kind kind, String wordKey) {
			this.kind = kind;
			this.wordKey = wordKey;
		}

		/** The outcome a plain net implies (win / lose / push). */
		public static Outcome ofNet(long net) {
			return net > 0 ? WIN : net < 0 ? LOSE : PUSH;
		}
	}

	/** Horizontal padding of a stamp at 1× (each side) and at 2×. */
	public static final int PAD_1X = 4, PAD_2X = 6;
	/** Stamp heights: same as the pills ({@link SeatColumn#PILL_1X}, {@link SeatColumn#PILL_2X}). */
	public static final int H_1X = SeatColumn.PILL_1X, H_2X = SeatColumn.PILL_2X;

	/** Width of a stamp of a word {@code wordW} px wide (1× font) at {@code scale} 1 or 2. */
	public static int width(int wordW, int scale) {
		return scale >= 2 ? 2 * wordW + 2 * PAD_2X : wordW + 2 * PAD_1X;
	}

	/** The levels to offer {@link SeatColumn#table}: hero {2×, 1×}, others {1×}. */
	public static int[] levels(int wordW, boolean hero) {
		return hero ? new int[] {width(wordW, 2), width(wordW, 1)} : new int[] {width(wordW, 1)};
	}

	/** What a chosen level means: the scale (2, 1) or 0 = the coloured dot. */
	public static int scaleOf(int level, boolean hero) {
		if (hero) return level == 0 ? 2 : level == 1 ? 1 : 0;
		return level == 0 ? 1 : 0;
	}
}

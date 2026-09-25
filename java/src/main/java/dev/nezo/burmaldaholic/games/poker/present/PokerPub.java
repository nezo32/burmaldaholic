package dev.nezo.burmaldaholic.games.poker.present;

import java.util.Arrays;

/**
 * The PUBLIC state of a Hold'em table for the in-world renderer (animation/cards.md §2.4, §2.7 "public tag"): what
 * every passer-by may see. Travels as one int array in the block entity's update tag (≤ 1 KB); pure, so the
 * encoding is unit-tested.
 *
 * <p>Never contains a hole card that is not face up on the felt (exposed at an all-in, or shown at the showdown)
 * and never a board card before its slide beat: the server builds it from the same publication rule as the
 * players' screens.
 *
 * @param seq       presentation segment counter ({@code PokerBeats}); 0 = nothing yet
 * @param kind      0 none, 1 deal, 2 street, 3 finish
 * @param startTick server game time of the segment's beat 0
 * @param args      the segment's builder arguments (public counts)
 * @param seed      cosmetic seed of the segment
 * @param button    seat of the dealer button, -1 none
 * @param board     board cards dealt on the felt (0..5)
 * @param pot       chips in the middle (0 when none)
 * @param seats     per seat index: {@link #flags} bit set (0 = empty seat)
 * @param bets      per seat: chips in front of the seat this street
 * @param cards     per seat: two face-up cards, or -1 / -1
 */
public record PokerPub(int seq, int kind, long startTick, int[] args, int seed, int button, int[] board, long pot, int[] seats,
		long[] bets, int[] cards) {
	public static final int VERSION = 1;
	public static final int SEATED = 1, DEALT = 2, FOLDED = 4, ALL_IN = 8, WINNER = 16, BOT = 32, ACTING = 64;
	public static final PokerPub EMPTY = new PokerPub(0, 0, 0, new int[0], 0, -1, new int[0], 0, new int[0], new long[0], new int[0]);

	public PokerPub {
		args = args.clone();
		board = board.clone();
		seats = seats.clone();
		bets = bets.clone();
		cards = cards.clone();
		if (cards.length != seats.length * 2 || bets.length != seats.length) throw new IllegalArgumentException("seat arrays");
	}

	public int seatCount() {
		return seats.length;
	}

	public boolean has(int seat, int flag) {
		return seat >= 0 && seat < seats.length && (seats[seat] & flag) != 0;
	}

	public int card(int seat, int k) {
		return cards[seat * 2 + k];
	}

	public static int kindCode(String kind) {
		return switch (kind) {
			case "deal" -> 1;
			case "street" -> 2;
			case "finish" -> 3;
			default -> 0;
		};
	}

	public static String kindName(int code) {
		return switch (code) {
			case 1 -> "deal";
			case 2 -> "street";
			case 3 -> "finish";
			default -> "";
		};
	}

	/** Flat encoding: {@code [v, seq, kind, startHi, startLo, seed, button, nArgs, args…, nBoard, board…, potHi, potLo, nSeats, (flags, betHi, betLo, c0, c1)…]}. */
	public int[] encode() {
		int n = 8 + args.length + 1 + board.length + 2 + 1 + seats.length * 5;
		int[] out = new int[n];
		int i = 0;
		out[i++] = VERSION;
		out[i++] = seq;
		out[i++] = kind;
		out[i++] = (int) (startTick >>> 32);
		out[i++] = (int) startTick;
		out[i++] = seed;
		out[i++] = button;
		out[i++] = args.length;
		for (int a : args) out[i++] = a;
		out[i++] = board.length;
		for (int c : board) out[i++] = c;
		out[i++] = (int) (pot >>> 32);
		out[i++] = (int) pot;
		out[i++] = seats.length;
		for (int s = 0; s < seats.length; s++) {
			out[i++] = seats[s];
			out[i++] = (int) (bets[s] >>> 32);
			out[i++] = (int) bets[s];
			out[i++] = cards[s * 2];
			out[i++] = cards[s * 2 + 1];
		}
		return out;
	}

	/** Decodes {@link #encode()}; a malformed or other-version array gives {@link #EMPTY}. */
	public static PokerPub decode(int[] d) {
		try {
			int i = 0;
			if (d[i++] != VERSION) return EMPTY;
			int seq = d[i++];
			int kind = d[i++];
			long start = ((long) d[i++] << 32) | (d[i++] & 0xFFFFFFFFL);
			int seed = d[i++];
			int button = d[i++];
			int[] args = Arrays.copyOfRange(d, i + 1, i + 1 + d[i]);
			i += 1 + args.length;
			int[] board = Arrays.copyOfRange(d, i + 1, i + 1 + d[i]);
			i += 1 + board.length;
			long pot = ((long) d[i++] << 32) | (d[i++] & 0xFFFFFFFFL);
			int n = d[i++];
			if (n < 0 || n > 16 || board.length > 5 || args.length > 16) return EMPTY;
			int[] seats = new int[n];
			long[] bets = new long[n];
			int[] cards = new int[n * 2];
			for (int s = 0; s < n; s++) {
				seats[s] = d[i++];
				bets[s] = ((long) d[i++] << 32) | (d[i++] & 0xFFFFFFFFL);
				cards[s * 2] = d[i++];
				cards[s * 2 + 1] = d[i++];
			}
			return new PokerPub(seq, kind, start, args, seed, button, board, pot, seats, bets, cards);
		} catch (RuntimeException malformed) {
			return EMPTY;
		}
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof PokerPub p && Arrays.equals(encode(), p.encode());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(encode());
	}
}

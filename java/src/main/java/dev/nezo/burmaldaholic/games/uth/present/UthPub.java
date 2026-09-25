package dev.nezo.burmaldaholic.games.uth.present;

import java.util.Arrays;

/**
 * The PUBLIC state of an Ultimate Texas Hold'em table for the in-world renderer and the dealer NPC
 * (animation/cards.md §3.4, §3.6): the running segment, the board cards turned face up, the dealer's cards (only
 * from the showdown on) and per seat the combined bet and the hole cards once they are public (showdown). Pure,
 * one int array in the block update tag.
 *
 * @param seq       segment counter (a change restarts the clients' players)
 * @param kind      {@link #DEAL}, {@link #FLOP}, {@link #RIVER}, {@link #SHOWDOWN}, {@link #RESULT} or 0 (betting)
 * @param startTick server game time of the segment's beat 0
 * @param seed      cosmetic seed
 * @param board     face-up board cards (0, 3 or 5)
 * @param dealer    the dealer's two cards, or empty before the showdown
 * @param seats     seat index per dealt-in seat (table order = lane order of {@code UthBeats})
 * @param flags     per dealt-in seat: {@link #BOT}, {@link #FOLDED}, the Play multiple (bits {@link #PLAY_SHIFT}…), {@link #WON}
 * @param bets      per dealt-in seat: chips on its circles
 * @param cards     per dealt-in seat: two face-up cards or -1 / -1
 */
public record UthPub(int seq, int kind, long startTick, int seed, int[] board, int[] dealer, int[] seats, int[] flags, long[] bets,
		int[] cards) {
	public static final int VERSION = 1;
	public static final int DEAL = 1, FLOP = 2, RIVER = 3, SHOWDOWN = 4, RESULT = 5;
	public static final int BOT = 1, FOLDED = 2, WON = 4, PLAY_SHIFT = 3;
	public static final UthPub EMPTY = new UthPub(0, 0, 0, 0, new int[0], new int[0], new int[0], new int[0], new long[0], new int[0]);

	public UthPub {
		board = board.clone();
		dealer = dealer.clone();
		seats = seats.clone();
		flags = flags.clone();
		bets = bets.clone();
		cards = cards.clone();
		if (flags.length != seats.length || bets.length != seats.length || cards.length != seats.length * 2) {
			throw new IllegalArgumentException("seat arrays");
		}
	}

	public int play(int i) {
		return (flags[i] >> PLAY_SHIFT) & 7;
	}

	public boolean has(int i, int flag) {
		return (flags[i] & flag) != 0;
	}

	/** {@code play} multiple packed into the flags (bits 3..5). */
	public static int flags(boolean bot, boolean folded, boolean won, int play) {
		return (bot ? BOT : 0) | (folded ? FOLDED : 0) | (won ? WON : 0) | (Math.max(0, Math.min(7, play)) << PLAY_SHIFT);
	}

	public int[] encode() {
		int n = 7 + board.length + 1 + dealer.length + 1 + seats.length * 6;
		int[] out = new int[n];
		int i = 0;
		out[i++] = VERSION;
		out[i++] = seq;
		out[i++] = kind;
		out[i++] = (int) (startTick >>> 32);
		out[i++] = (int) startTick;
		out[i++] = seed;
		out[i++] = board.length;
		for (int c : board) out[i++] = c;
		out[i++] = dealer.length;
		for (int c : dealer) out[i++] = c;
		out[i++] = seats.length;
		for (int s = 0; s < seats.length; s++) {
			out[i++] = seats[s];
			out[i++] = flags[s];
			out[i++] = (int) (bets[s] >>> 32);
			out[i++] = (int) bets[s];
			out[i++] = cards[s * 2];
			out[i++] = cards[s * 2 + 1];
		}
		return out;
	}

	public static UthPub decode(int[] d) {
		try {
			int i = 0;
			if (d[i++] != VERSION) return EMPTY;
			int seq = d[i++];
			int kind = d[i++];
			long start = ((long) d[i++] << 32) | (d[i++] & 0xFFFFFFFFL);
			int seed = d[i++];
			int[] board = Arrays.copyOfRange(d, i + 1, i + 1 + d[i]);
			i += 1 + board.length;
			int[] dealer = Arrays.copyOfRange(d, i + 1, i + 1 + d[i]);
			i += 1 + dealer.length;
			int n = d[i++];
			if (n < 0 || n > 8 || board.length > 5 || dealer.length > 2) return EMPTY;
			int[] seats = new int[n];
			int[] flags = new int[n];
			long[] bets = new long[n];
			int[] cards = new int[n * 2];
			for (int s = 0; s < n; s++) {
				seats[s] = d[i++];
				flags[s] = d[i++];
				bets[s] = ((long) d[i++] << 32) | (d[i++] & 0xFFFFFFFFL);
				cards[s * 2] = d[i++];
				cards[s * 2 + 1] = d[i++];
			}
			return new UthPub(seq, kind, start, seed, board, dealer, seats, flags, bets, cards);
		} catch (RuntimeException malformed) {
			return EMPTY;
		}
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof UthPub p && Arrays.equals(encode(), p.encode());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(encode());
	}
}

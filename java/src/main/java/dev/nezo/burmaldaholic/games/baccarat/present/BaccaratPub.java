package dev.nezo.burmaldaholic.games.baccarat.present;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The PUBLIC state of a baccarat / chemin de fer table for the in-world board (docs/architecture/ui-v2.md §7.2).
 * One int array in the block entity's update tag; pure, encode/decode unit-tested.
 *
 * <p>Cards follow the reveal schedule of the players' screens: a card is listed once its deal beat passed and is a
 * back ({@code -1}) until its own flip / squeeze; the winner only after the reveal.
 *
 * @param seq       coup number (the board rebuilds its text cache when it changes)
 * @param phase     {@link #IDLE} … {@link #WAITING}
 * @param chemmy    chemin de fer (the Player box holds the punts, the Banker box the bank)
 * @param min       minimum bet
 * @param max       maximum bet
 * @param deadline  server game time at which the running timer ends, -1 none
 * @param revStart  server game time of the reveal's beat 0 (deal / rev ticks are relative to it)
 * @param player    Player hand: codes (-1 = back); {@code playerDeal} / {@code playerRev} per card (rev -1 = not shown)
 * @param banker    Banker hand, same layout
 * @param boxes     total on each box: P, B, T, P.PAIR, B.PAIR (bots' virtual bets included)
 * @param winner    0 none yet, 1 Player, 2 Banker, 3 Tie
 * @param bank      chemmy: the bank (0 on house tables)
 * @param seats     seated players and bots (ascending seat index)
 */
public record BaccaratPub(int seq, int phase, boolean chemmy, long min, long max, long deadline, long revStart, int[] player, int[] playerDeal,
		int[] playerRev, int[] banker, int[] bankerDeal, int[] bankerRev, long[] boxes, int winner, long bank, List<SeatPub> seats) {
	public static final int VERSION = 1;
	public static final int IDLE = 0, BETTING = 1, NO_MORE_BETS = 2, SHUFFLE = 3, REVEAL = 4, RESULT = 5, BANK_OFFER = 6, WAITING = 7;
	private static final String[] PHASES = {"idle", "betting", "no_more_bets", "shuffle", "reveal", "result", "bank_offer", "waiting"};
	public static final int P = 0, B = 1, T = 2, PP = 3, BP = 4, BOXES = 5;
	/** Box ids in {@link #boxes} order ({@code BetKind} ids). */
	public static final String[] BOX_IDS = {"player", "banker", "tie", "player_pair", "banker_pair"};
	/** Seat flags. */
	public static final int BOT = 1, BANKER = 2, READY = 4;
	public static final BaccaratPub EMPTY = new BaccaratPub(0, IDLE, false, 0, 0, -1, 0, new int[0], new int[0], new int[0], new int[0], new int[0],
		new int[0], new long[BOXES], 0, 0, List.of());

	/**
	 * @param seat  seat index
	 * @param flags {@link #BOT} / {@link #BANKER} / {@link #READY}
	 * @param bets  per box (P, B, T, P.PAIR, B.PAIR); chemmy: the punt on P
	 */
	public record SeatPub(int seat, int flags, long[] bets) {
		public SeatPub {
			bets = Arrays.copyOf(bets, BOXES);
		}

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		public long total() {
			long t = 0;
			for (long b : bets) t += b;
			return t;
		}

		/** The box holding most of the seat's chips (-1 = none). */
		public int mainBox() {
			int best = -1;
			for (int i = 0; i < BOXES; i++) if (bets[i] > 0 && (best < 0 || bets[i] > bets[best])) best = i;
			return best;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof SeatPub s && s.seat == seat && s.flags == flags && Arrays.equals(s.bets, bets);
		}

		@Override
		public int hashCode() {
			return seat * 31 + flags * 17 + Arrays.hashCode(bets);
		}
	}

	public BaccaratPub {
		player = player.clone();
		playerDeal = playerDeal.clone();
		playerRev = playerRev.clone();
		banker = banker.clone();
		bankerDeal = bankerDeal.clone();
		bankerRev = bankerRev.clone();
		boxes = Arrays.copyOf(boxes, BOXES);
		seats = List.copyOf(seats);
		if (player.length != playerDeal.length || player.length != playerRev.length || banker.length != bankerDeal.length
			|| banker.length != bankerRev.length) {
			throw new IllegalArgumentException("hand arrays");
		}
	}

	public static int phaseCode(String phase) {
		for (int i = 0; i < PHASES.length; i++) if (PHASES[i].equals(phase)) return i;
		return IDLE;
	}

	public String phaseId() {
		return phase >= 0 && phase < PHASES.length ? PHASES[phase] : "idle";
	}

	public SeatPub seat(int index) {
		for (SeatPub s : seats) if (s.seat == index) return s;
		return null;
	}

	/** Baccarat total of the face-up cards of a hand (backs count 0). */
	public static int total(int[] codes) {
		int t = 0;
		for (int c : codes) {
			if (c < 0) continue;
			int rank = c % 13 + 1;
			t += rank >= 10 ? 0 : rank;
		}
		return t % 10;
	}

	/** Every card of the hand is face up (and there is at least one). */
	public static boolean allShown(int[] codes) {
		if (codes.length == 0) return false;
		for (int c : codes) if (c < 0) return false;
		return true;
	}

	public int[] encode() {
		Buf o = new Buf();
		o.add(VERSION).add(seq).add(phase).add(chemmy ? 1 : 0).addLong(min).addLong(max).addLong(deadline).addLong(revStart);
		o.hand(player, playerDeal, playerRev).hand(banker, bankerDeal, bankerRev);
		for (long b : boxes) o.addLong(b);
		o.add(winner).addLong(bank).add(seats.size());
		for (SeatPub s : seats) {
			o.add(s.seat).add(s.flags);
			for (long b : s.bets) o.addLong(b);
		}
		return o.toArray();
	}

	/** Growable int array. */
	private static final class Buf {
		private int[] a = new int[96];
		private int n;

		Buf add(int v) {
			if (n == a.length) a = Arrays.copyOf(a, n * 2);
			a[n++] = v;
			return this;
		}

		Buf addLong(long v) {
			return add((int) (v >>> 32)).add((int) v);
		}

		Buf hand(int[] c, int[] d, int[] r) {
			add(c.length);
			for (int i = 0; i < c.length; i++) add(c[i]).add(d[i]).add(r[i]);
			return this;
		}

		int[] toArray() {
			return Arrays.copyOf(a, n);
		}
	}

	public static BaccaratPub decode(int[] d) {
		try {
			int[] i = {0};
			java.util.function.IntSupplier next = () -> d[i[0]++];
			java.util.function.LongSupplier nextLong = () -> ((long) d[i[0]++] << 32) | (d[i[0]++] & 0xFFFFFFFFL);
			if (next.getAsInt() != VERSION) return EMPTY;
			int seq = next.getAsInt();
			int phase = next.getAsInt();
			boolean chemmy = next.getAsInt() != 0;
			long min = nextLong.getAsLong();
			long max = nextLong.getAsLong();
			long deadline = nextLong.getAsLong();
			long revStart = nextLong.getAsLong();
			int[][] p = hand(next);
			int[][] b = hand(next);
			long[] boxes = new long[BOXES];
			for (int k = 0; k < BOXES; k++) boxes[k] = nextLong.getAsLong();
			int winner = next.getAsInt();
			long bank = nextLong.getAsLong();
			int ns = next.getAsInt();
			if (ns < 0 || ns > 16) return EMPTY;
			List<SeatPub> seats = new ArrayList<>(ns);
			for (int s = 0; s < ns; s++) {
				int seat = next.getAsInt();
				int flags = next.getAsInt();
				long[] bets = new long[BOXES];
				for (int k = 0; k < BOXES; k++) bets[k] = nextLong.getAsLong();
				seats.add(new SeatPub(seat, flags, bets));
			}
			if (i[0] != d.length) return EMPTY;
			return new BaccaratPub(seq, phase, chemmy, min, max, deadline, revStart, p[0], p[1], p[2], b[0], b[1], b[2], boxes, winner, bank, seats);
		} catch (RuntimeException malformed) {
			return EMPTY;
		}
	}

	private static int[][] hand(java.util.function.IntSupplier next) {
		int n = next.getAsInt();
		if (n < 0 || n > 3) throw new IllegalArgumentException("hand " + n);
		int[][] out = new int[3][n];
		for (int k = 0; k < n; k++) {
			out[0][k] = next.getAsInt();
			out[1][k] = next.getAsInt();
			out[2][k] = next.getAsInt();
		}
		return out;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof BaccaratPub p && Arrays.equals(encode(), p.encode());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(encode());
	}
}

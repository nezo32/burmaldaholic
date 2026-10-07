package dev.nezo.burmaldaholic.games.blackjack.present;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The PUBLIC state of a blackjack table for the in-world board (docs/architecture/ui-v2.md §7.2): what every
 * passer-by may see. Travels as one int array in the block entity's update tag; pure, so the encoding is unit-tested.
 *
 * <p>Built from the same publication rule as the players' screens ({@code BlackjackBeats}): a card is here only once
 * its deal beat is reached, the dealer's hole card is a back ({@code -1}) until its flip beat, outcomes only once the
 * reveal gate passed. It never carries a face-down hole card.
 *
 * @param seq        round counter (0 = no round yet); the board rebuilds its text cache when it changes
 * @param startTick  server game time of the round's beat 0 (card {@code at} ticks are relative to it)
 * @param phase      {@link #BETTING} … {@link #RESULT}
 * @param min        table minimum
 * @param max        table maximum
 * @param acting     seat index whose turn it is, -1 none
 * @param actingHand hand index of the acting seat, -1 none
 * @param deadline   server game time at which the current timer (bet / insurance / turn) ends, -1 none
 * @param dealer     the dealer's public cards (code 0..51 = {@code suit × 13 + rank − 1}, -1 = back)
 * @param dealerAt   per dealer card: its deal tick − {@code startTick}
 * @param seats      seated players and bots (ascending seat index)
 */
public record BlackjackPub(int seq, long startTick, int phase, long min, long max, int acting, int actingHand, long deadline, int[] dealer,
		int[] dealerAt, List<SeatPub> seats) {
	public static final int VERSION = 1;
	public static final int BETTING = 0, INSURANCE = 1, TURNS = 2, DEALER = 3, RESULT = 4;
	/** Seat flags. */
	public static final int BOT = 1, SETTLED = 2, AWAY = 4;
	/** Hand flags. */
	public static final int DOUBLED = 1, SPLIT = 2, DONE = 4;
	/** Outcome codes (0 = none yet), in {@code BlackjackRound.Outcome} order + 1. */
	public static final String[] OUTCOMES = {"", "blackjack", "win", "dealer_bust", "push", "bust", "lose", "surrender", "dealer_blackjack",
		"even_money"};
	public static final BlackjackPub EMPTY = new BlackjackPub(0, 0, BETTING, 0, 0, -1, -1, -1, new int[0], new int[0], List.of());

	/**
	 * One hand of a seat.
	 *
	 * @param bet     stake of the hand (doubles included)
	 * @param flags   {@link #DOUBLED} / {@link #SPLIT} / {@link #DONE}
	 * @param outcome index into {@link #OUTCOMES} (0 = playing)
	 * @param ret     total return once settled
	 * @param cards   public cards (codes)
	 * @param at      per card: deal tick − round start
	 */
	public record HandPub(long bet, int flags, int outcome, long ret, int[] cards, int[] at) {
		public HandPub {
			cards = cards.clone();
			at = at.clone();
			if (cards.length != at.length) throw new IllegalArgumentException("cards / at");
		}

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		public String outcomeId() {
			return outcome > 0 && outcome < OUTCOMES.length ? OUTCOMES[outcome] : "";
		}
	}

	/**
	 * A seat at the table.
	 *
	 * @param seat  seat index
	 * @param flags {@link #BOT} / {@link #SETTLED} / {@link #AWAY}
	 * @param bet   the main bet (or, while betting, the bet placed for the next round; 0 = none yet)
	 * @param net   settled net of the whole seat (0 until {@link #SETTLED})
	 * @param hands the seat's hands this round (empty while betting)
	 */
	public record SeatPub(int seat, int flags, long bet, long net, List<HandPub> hands) {
		public SeatPub {
			hands = List.copyOf(hands);
		}

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}
	}

	public BlackjackPub {
		dealer = dealer.clone();
		dealerAt = dealerAt.clone();
		seats = List.copyOf(seats);
		if (dealer.length != dealerAt.length) throw new IllegalArgumentException("dealer / at");
	}

	public static int outcomeCode(String id) {
		for (int i = 1; i < OUTCOMES.length; i++) if (OUTCOMES[i].equals(id)) return i;
		return 0;
	}

	public static int phaseCode(String phase) {
		return switch (phase) {
			case "insurance" -> INSURANCE;
			case "turns" -> TURNS;
			case "dealer" -> DEALER;
			case "result" -> RESULT;
			default -> BETTING;
		};
	}

	public SeatPub seat(int index) {
		for (SeatPub s : seats) if (s.seat == index) return s;
		return null;
	}

	/**
	 * Flat encoding: {@code [v, seq, startHi, startLo, phase, minHi, minLo, maxHi, maxLo, acting, actingHand, deadHi,
	 * deadLo, nDealer, (code, at)…, nSeats, (seat, flags, betHi, betLo, netHi, netLo, nHands, (betHi, betLo, flags,
	 * outcome, retHi, retLo, nCards, (code, at)…)…)…]}.
	 */
	public int[] encode() {
		IntBuf b = new IntBuf();
		b.add(VERSION).add(seq).addLong(startTick).add(phase).addLong(min).addLong(max).add(acting).add(actingHand).addLong(deadline);
		b.add(dealer.length);
		for (int i = 0; i < dealer.length; i++) b.add(dealer[i]).add(dealerAt[i]);
		b.add(seats.size());
		for (SeatPub s : seats) {
			b.add(s.seat).add(s.flags).addLong(s.bet).addLong(s.net).add(s.hands.size());
			for (HandPub h : s.hands) {
				b.addLong(h.bet).add(h.flags).add(h.outcome).addLong(h.ret).add(h.cards.length);
				for (int i = 0; i < h.cards.length; i++) b.add(h.cards[i]).add(h.at[i]);
			}
		}
		return b.toArray();
	}

	/** Decodes {@link #encode()}; a malformed or other-version array gives {@link #EMPTY}. */
	public static BlackjackPub decode(int[] d) {
		try {
			Reader r = new Reader(d);
			if (r.next() != VERSION) return EMPTY;
			int seq = r.next();
			long start = r.nextLong();
			int phase = r.next();
			long min = r.nextLong();
			long max = r.nextLong();
			int acting = r.next();
			int actingHand = r.next();
			long deadline = r.nextLong();
			int nd = r.count(16);
			int[] dealer = new int[nd];
			int[] dealerAt = new int[nd];
			for (int i = 0; i < nd; i++) {
				dealer[i] = r.next();
				dealerAt[i] = r.next();
			}
			int ns = r.count(8);
			List<SeatPub> seats = new ArrayList<>(ns);
			for (int s = 0; s < ns; s++) {
				int seat = r.next();
				int flags = r.next();
				long bet = r.nextLong();
				long net = r.nextLong();
				int nh = r.count(8);
				List<HandPub> hands = new ArrayList<>(nh);
				for (int h = 0; h < nh; h++) {
					long hb = r.nextLong();
					int hf = r.next();
					int oc = r.next();
					long ret = r.nextLong();
					int nc = r.count(16);
					int[] cards = new int[nc];
					int[] at = new int[nc];
					for (int i = 0; i < nc; i++) {
						cards[i] = r.next();
						at[i] = r.next();
					}
					hands.add(new HandPub(hb, hf, oc, ret, cards, at));
				}
				seats.add(new SeatPub(seat, flags, bet, net, hands));
			}
			if (!r.done()) return EMPTY;
			return new BlackjackPub(seq, start, phase, min, max, acting, actingHand, deadline, dealer, dealerAt, seats);
		} catch (RuntimeException malformed) {
			return EMPTY;
		}
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof BlackjackPub p && Arrays.equals(encode(), p.encode());
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(encode());
	}

	/** Growable int array. */
	static final class IntBuf {
		private int[] a = new int[64];
		private int n;

		IntBuf add(int v) {
			if (n == a.length) a = Arrays.copyOf(a, n * 2);
			a[n++] = v;
			return this;
		}

		IntBuf addLong(long v) {
			return add((int) (v >>> 32)).add((int) v);
		}

		int[] toArray() {
			return Arrays.copyOf(a, n);
		}
	}

	/** Bounds-checked reader. */
	static final class Reader {
		private final int[] d;
		private int i;

		Reader(int[] d) {
			this.d = d;
		}

		int next() {
			return d[i++];
		}

		long nextLong() {
			return ((long) d[i++] << 32) | (d[i++] & 0xFFFFFFFFL);
		}

		int count(int max) {
			int c = next();
			if (c < 0 || c > max) throw new IllegalArgumentException("count " + c);
			return c;
		}

		boolean done() {
			return i == d.length;
		}
	}
}

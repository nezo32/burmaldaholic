package dev.nezo.burmaldaholic.games.uth.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One Ultimate Texas Hold'em round at a shared table (GAME_DESIGN §21.4): every seat has its own hole
 * cards and plays only against the one dealer hand; all seats share the board. Pure and deterministic:
 * the whole deck is fixed at {@link #deal} (the round is "drawn", §4.1), decisions only choose bets.
 *
 * <pre>
 * PREFLOP  (board hidden)     Check · Bet ×4 · Bet ×3
 * FLOP     (3 board cards)    seats without a Play bet: Check · Bet ×2
 * RIVER    (all 5)            seats without a Play bet: Bet ×1 · Fold
 * SHOWDOWN                    dealer cards shown, every seat settled ({@link #result})
 * </pre>
 */
public final class UthRound {
	public enum Street {
		PREFLOP, FLOP, RIVER, SHOWDOWN;

		public boolean decisions() {
			return this != SHOWDOWN;
		}
	}

	/**
	 * A seat's occupant and bets at the deal (seat index 0-based, table order). The occupant is abstract:
	 * {@code player} is a participant id — a real player's UUID, or (later, BOTS.md) a virtual bot participant
	 * with {@code bot = true} whose decisions come from its own {@link SeatDecider}.
	 */
	public record Entry(int seat, UUID player, String name, long ante, long trips, boolean bot) {
		public Entry(int seat, UUID player, String name, long ante, long trips) {
			this(seat, player, name, ante, trips, false);
		}
	}

	public static final class Seat {
		public final int seat;
		public final UUID player;
		public final String name;
		public final long ante;
		public final long trips;
		/** Virtual participant (bot) rather than a player. */
		public final boolean bot;
		public final int[] hole = new int[2];
		/** Play bet in Antes (0 = none yet). */
		public int playMultiple;
		public boolean folded;
		/** Decided on the current street (reset on every street). */
		public boolean decided;
		/** Last decision (for the public status tag); null = still deciding / not yet asked. */
		public @Nullable Decision last;
		/** Street of the Play bet (null = none). */
		public @Nullable Street playStreet;
		public Settlement.@Nullable Result result;

		Seat(Entry e) {
			this.seat = e.seat();
			this.player = e.player();
			this.name = e.name();
			this.ante = e.ante();
			this.trips = e.trips();
			this.bot = e.bot();
		}

		public long play() {
			return playMultiple * ante;
		}

		/** Everything this seat has at risk now. */
		public long staked() {
			return 2 * ante + play() + trips;
		}

		public boolean active() {
			return playMultiple == 0 && !folded;
		}
	}

	private final int[] deck;
	private final List<Seat> seats = new ArrayList<>();
	private final int[] dealer = new int[2];
	private final int[] board = new int[5];
	private Street street = Street.PREFLOP;

	private UthRound(int[] deck) {
		this.deck = deck.clone();
	}

	/**
	 * Deals from the top of {@code deck} (index 0): one card to each seat (table order), one to the dealer,
	 * again, then the five board cards (dealt face down now, revealed street by street).
	 */
	public static UthRound deal(List<Entry> entries, int[] deck) {
		if (entries.isEmpty() || entries.size() > 7) {
			throw new IllegalArgumentException("1..7 seats");
		}
		if (deck.length != UthCards.DECK_SIZE) {
			throw new IllegalArgumentException("need a full deck");
		}
		UthRound r = new UthRound(deck);
		List<Entry> sorted = new ArrayList<>(entries);
		sorted.sort((a, b) -> Integer.compare(a.seat(), b.seat()));
		for (Entry e : sorted) {
			r.seats.add(new Seat(e));
		}
		int k = 0;
		for (int pass = 0; pass < 2; pass++) {
			for (Seat s : r.seats) {
				s.hole[pass] = r.deck[k++];
			}
			r.dealer[pass] = r.deck[k++];
		}
		for (int i = 0; i < 5; i++) {
			r.board[i] = r.deck[k++];
		}
		return r;
	}

	public List<Seat> seats() {
		return Collections.unmodifiableList(seats);
	}

	public @Nullable Seat seat(UUID player) {
		for (Seat s : seats) {
			if (s.player.equals(player)) {
				return s;
			}
		}
		return null;
	}

	public Street street() {
		return street;
	}

	public int[] deck() {
		return deck.clone();
	}

	public int[] dealerCards() {
		return dealer.clone();
	}

	public int[] board() {
		return board.clone();
	}

	/** Board cards face up on the current street: 0, 3 or 5. */
	public int visibleBoard() {
		return switch (street) {
			case PREFLOP -> 0;
			case FLOP -> 3;
			default -> 5;
		};
	}

	public int[] visibleBoardCards() {
		int n = visibleBoard();
		int[] out = new int[n];
		System.arraycopy(board, 0, out, 0, n);
		return out;
	}

	/** Best hand value of the seat with the board cards visible now (-1 before the flop). */
	public int visibleValue(Seat s) {
		int n = visibleBoard();
		if (n == 0) {
			return -1;
		}
		int[] cards = new int[2 + n];
		cards[0] = s.hole[0];
		cards[1] = s.hole[1];
		System.arraycopy(board, 0, cards, 2, n);
		return UthCards.evaluate(cards, cards.length);
	}

	/** Final (7-card) value of a seat — known from the deal on, used for the river default. */
	public int finalValue(Seat s) {
		return Settlement.value(s.hole, board);
	}

	public int dealerValue() {
		return Settlement.value(dealer, board);
	}

	// ---- decisions ------------------------------------------------------------------------------

	/** Whether the seat still has to decide on this street. */
	public boolean pending(Seat s) {
		return street.decisions() && !s.decided && s.active();
	}

	public List<Seat> pendingSeats() {
		List<Seat> out = new ArrayList<>();
		for (Seat s : seats) {
			if (pending(s)) {
				out.add(s);
			}
		}
		return out;
	}

	public boolean anyPending() {
		for (Seat s : seats) {
			if (pending(s)) {
				return true;
			}
		}
		return false;
	}

	/** Legal decisions of a pending seat on this street (before affordability). */
	public List<Decision> legal(Seat s, boolean allow3x) {
		if (!pending(s)) {
			return List.of();
		}
		return switch (street) {
			case PREFLOP -> allow3x ? List.of(Decision.CHECK, Decision.BET_3X, Decision.BET_4X) : List.of(Decision.CHECK, Decision.BET_4X);
			case FLOP -> List.of(Decision.CHECK, Decision.BET_2X);
			case RIVER -> List.of(Decision.FOLD, Decision.BET_1X);
			case SHOWDOWN -> List.of();
		};
	}

	/**
	 * Applies a decision. The caller has already collected the Play bet ({@code d.multiple() × ante})
	 * when {@code d.isBet()}. Returns false (nothing changed) if the decision is not legal now.
	 */
	public boolean decide(Seat s, Decision d, boolean allow3x) {
		if (!legal(s, allow3x).contains(d)) {
			return false;
		}
		s.decided = true;
		s.last = d;
		if (d == Decision.FOLD) {
			s.folded = true;
		} else if (d.isBet()) {
			s.playMultiple = d.multiple();
			s.playStreet = street;
		}
		return true;
	}

	/**
	 * Safe default when time runs out or the player is gone (§21.4): Check preflop and on the flop; at the
	 * river Fold, except Bet ×1 when {@code autoPlayMadeHands} and the seat's hand is a straight or better
	 * and {@code canAffordPlay} (the balance covers 1 × Ante).
	 */
	public Decision defaultDecision(Seat s, boolean autoPlayMadeHands, boolean canAffordPlay) {
		if (street != Street.RIVER) {
			return Decision.CHECK;
		}
		boolean made = UthCards.category(finalValue(s)) >= UthCards.STRAIGHT;
		return autoPlayMadeHands && made && canAffordPlay ? Decision.BET_1X : Decision.FOLD;
	}

	/**
	 * Next street (reveals board cards); on reaching SHOWDOWN every seat is settled. Only valid when no
	 * decision is pending.
	 */
	public void advance(Paytables pays) {
		if (anyPending()) {
			throw new IllegalStateException("decisions pending");
		}
		if (street == Street.SHOWDOWN) {
			return;
		}
		street = Street.values()[street.ordinal() + 1];
		for (Seat s : seats) {
			s.decided = false;
		}
		if (street == Street.SHOWDOWN) {
			int dv = dealerValue();
			for (Seat s : seats) {
				s.result = Settlement.settle(s.ante, s.play(), s.trips, s.folded, finalValue(s), dv, pays);
			}
		}
	}

	public boolean settled() {
		return street == Street.SHOWDOWN;
	}

	public boolean dealerQualifies() {
		return Settlement.qualifies(dealerValue());
	}
}

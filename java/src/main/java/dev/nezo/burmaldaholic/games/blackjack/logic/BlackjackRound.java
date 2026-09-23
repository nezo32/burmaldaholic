package dev.nezo.burmaldaholic.games.blackjack.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One blackjack round at a multi-seat table (GAME_DESIGN §6.3). PURE state machine; the block entity
 * moves money, runs timers and syncs clients.
 *
 * <pre>
 * new BlackjackRound(rules, source, bets)  DEAL: a card to each seat (seat order), dealer up-card,
 *                                          second card to each seat, dealer hole card
 * INSURANCE  dealer shows an ace: every seat decides — insurance 0..bet/2 ({@link #insure}) or,
 *            with a natural, even money ({@link #evenMoney})
 * (peek)     up-card A or 10-value: a dealer blackjack settles everything → DONE (player naturals
 *            push, insurance pays 2:1). Otherwise player naturals are paid 3:2 at once.
 * TURNS      {@link #current()} acts: hit / stand / double / split / surrender;
 *            {@link #standAll} for timeouts and disconnects
 * (dealer)   reveal; draw to 17 (S17) or hit soft 17 (H17) — skipped when no live hand remains
 * DONE       every seat settled: {@link Hand#outcome}, {@link #returnOf}
 * </pre>
 *
 * All returns are TOTAL returns (stake included), like {@code CasinoTableBlockEntity#settle}.
 */
public final class BlackjackRound {
	public enum Phase {
		INSURANCE, TURNS, DONE
	}

	public enum Action {
		HIT, STAND, DOUBLE, SPLIT, SURRENDER;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Action byId(String id) {
			for (Action a : values()) {
				if (a.id().equals(id)) {
					return a;
				}
			}
			return null;
		}
	}

	public enum Outcome {
		BLACKJACK, WIN, DEALER_BUST, PUSH, BUST, LOSE, SURRENDER, DEALER_BLACKJACK, EVEN_MONEY;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public enum Offer {
		INSURANCE, EVEN_MONEY
	}

	public record SeatBet(int seat, UUID player, long bet) {}

	public static final class Hand {
		public final List<Card> cards = new ArrayList<>();
		public long bet;
		/** produced by a split (a 21 is then not a blackjack) */
		public boolean split;
		/** a split ace: one card only, no hit/double */
		public boolean splitAces;
		public boolean doubled;
		public boolean surrendered;
		public boolean done;
		public Outcome outcome;
		/** total return once settled */
		public long ret;

		Hand(long bet) {
			this.bet = bet;
		}
	}

	public static final class Seat {
		public final int seat;
		public final UUID player;
		/** original main bet */
		public final long bet;
		public final List<Hand> hands = new ArrayList<>();
		public long insurance;
		public boolean insuranceDecided;
		/** 3 × insurance when the dealer has blackjack */
		public long insuranceReturn;
		/** every hand resolved; amounts final */
		public boolean settled;

		Seat(int seat, UUID player, long bet) {
			this.seat = seat;
			this.player = player;
			this.bet = bet;
			hands.add(new Hand(bet));
		}
	}

	/** The hand to act. */
	public record Turn(Seat seat, Hand hand, int handIndex) {}

	private final BlackjackRules rules;
	private final CardSource source;
	private final List<Seat> seats = new ArrayList<>();
	private final List<Card> dealer = new ArrayList<>();
	private Phase phase = Phase.TURNS;
	private boolean holeRevealed;
	private boolean dealerBlackjack;
	private boolean peeked;
	private int cur = -1;

	public BlackjackRound(BlackjackRules rules, CardSource source, List<SeatBet> bets) {
		this.rules = rules;
		this.source = source;
		bets.stream().filter(b -> b.bet() > 0).sorted(Comparator.comparingInt(SeatBet::seat))
			.forEach(b -> seats.add(new Seat(b.seat(), b.player(), b.bet())));
		if (seats.isEmpty()) {
			throw new IllegalArgumentException("blackjack round without bets");
		}
		for (Seat s : seats) {
			s.hands.getFirst().cards.add(source.draw());
		}
		dealer.add(source.draw());
		for (Seat s : seats) {
			s.hands.getFirst().cards.add(source.draw());
		}
		dealer.add(source.draw());

		if (rules.insurance() && upCard().isAce()) {
			phase = Phase.INSURANCE;
			for (Seat s : seats) {
				if (offer(s.seat) == null) {
					s.insuranceDecided = true;
				}
			}
			afterInsuranceIfDecided();
		} else {
			afterInsurance();
		}
	}

	// ---- queries -------------------------------------------------------------------------------

	public BlackjackRules rules() {
		return rules;
	}

	public Phase phase() {
		return phase;
	}

	public List<Seat> seats() {
		return seats;
	}

	public Seat seat(int seatNo) {
		for (Seat s : seats) {
			if (s.seat == seatNo) {
				return s;
			}
		}
		return null;
	}

	public List<Card> dealerCards() {
		return dealer;
	}

	public Card upCard() {
		return dealer.getFirst();
	}

	/** Hole card visible (dealer blackjack or the dealer played). */
	public boolean holeRevealed() {
		return holeRevealed;
	}

	public boolean dealerBlackjack() {
		return dealerBlackjack;
	}

	/** The dealer checked the hole card for blackjack this round. */
	public boolean peeked() {
		return peeked;
	}

	// ---- insurance -----------------------------------------------------------------------------

	/** What this seat is offered right now (null = nothing to decide). */
	public Offer offer(int seatNo) {
		Seat s = seat(seatNo);
		if (s == null || phase != Phase.INSURANCE || s.insuranceDecided || s.settled) {
			return null;
		}
		if (Hands.isNatural(s.hands.getFirst().cards)) {
			return Offer.EVEN_MONEY;
		}
		return BlackjackRules.maxInsurance(s.bet) >= 1 ? Offer.INSURANCE : null;
	}

	/** Seats still deciding insurance / even money. */
	public List<Seat> pendingInsurance() {
		return phase == Phase.INSURANCE ? seats.stream().filter(s -> !s.insuranceDecided).toList() : List.of();
	}

	/**
	 * Insurance for {@code amount} chips (0 = no insurance, else 1..bet/2 — GAME_DESIGN §6.3). The caller
	 * collects the stake first. False (nothing changed) if not offered or the amount is out of range.
	 */
	public boolean insure(int seatNo, long amount) {
		Seat s = seat(seatNo);
		if (offer(seatNo) != Offer.INSURANCE || amount < 0 || amount > BlackjackRules.maxInsurance(s.bet)) {
			return false;
		}
		s.insurance = amount;
		s.insuranceDecided = true;
		afterInsuranceIfDecided();
		return true;
	}

	/** Even money for a player blackjack vs an ace: pays 1:1 immediately (take) or plays it out. */
	public boolean evenMoney(int seatNo, boolean take) {
		Seat s = seat(seatNo);
		if (offer(seatNo) != Offer.EVEN_MONEY) {
			return false;
		}
		s.insuranceDecided = true;
		if (take) {
			Hand h = s.hands.getFirst();
			h.done = true;
			h.outcome = Outcome.EVEN_MONEY;
			h.ret = s.bet * 2;
			s.settled = true;
		}
		afterInsuranceIfDecided();
		return true;
	}

	/** Declines whatever is offered (timer expiry, disconnect). */
	public void decline(int seatNo) {
		Offer o = offer(seatNo);
		if (o == Offer.INSURANCE) {
			insure(seatNo, 0);
		} else if (o == Offer.EVEN_MONEY) {
			evenMoney(seatNo, false);
		}
	}

	private void afterInsuranceIfDecided() {
		if (phase == Phase.INSURANCE && seats.stream().allMatch(s -> s.insuranceDecided)) {
			afterInsurance();
		}
	}

	/** Peek, settle naturals, start the player turns. */
	private void afterInsurance() {
		Card up = upCard();
		if (up.isAce() || up.isTenValue()) {
			peeked = true;
			if (Hands.isNatural(dealer)) {
				dealerBlackjack = true;
				holeRevealed = true;
				for (Seat s : seats) {
					s.insuranceReturn = s.insurance * 3;
					if (s.settled) {
						continue;
					}
					Hand h = s.hands.getFirst();
					h.done = true;
					if (Hands.isNatural(h.cards)) {
						h.outcome = Outcome.PUSH;
						h.ret = h.bet;
					} else {
						h.outcome = Outcome.DEALER_BLACKJACK;
						h.ret = 0;
					}
					s.settled = true;
				}
				phase = Phase.DONE;
				return;
			}
		}
		for (Seat s : seats) {
			if (s.settled) {
				continue;
			}
			Hand h = s.hands.getFirst();
			if (Hands.isNatural(h.cards)) {
				h.done = true;
				h.outcome = Outcome.BLACKJACK;
				h.ret = rules.blackjackReturn(h.bet);
				s.settled = true;
			}
		}
		phase = Phase.TURNS;
		cur = -1;
		advance();
	}

	// ---- player turns ----------------------------------------------------------------------------

	/** The seat and hand to act while in TURNS, else null. */
	public Turn current() {
		if (phase != Phase.TURNS || cur < 0 || cur >= seats.size()) {
			return null;
		}
		Seat s = seats.get(cur);
		for (int i = 0; i < s.hands.size(); i++) {
			if (!s.hands.get(i).done) {
				return new Turn(s, s.hands.get(i), i);
			}
		}
		return null;
	}

	/**
	 * Legal actions for the current hand of {@code seatNo}. {@code balance} = chips the player can still
	 * add (double and split need one more bet equal to the hand's bet).
	 */
	public List<Action> legal(int seatNo, long balance) {
		Turn t = current();
		if (t == null || t.seat().seat != seatNo) {
			return List.of();
		}
		Seat seat = t.seat();
		Hand hand = t.hand();
		boolean canSplit = Hands.isPair(hand.cards) && seat.hands.size() < rules.maxHands() && balance >= hand.bet
			&& (!hand.splitAces || rules.resplitAces());
		List<Action> out = new ArrayList<>();
		if (hand.splitAces) {
			out.add(Action.STAND);
			if (canSplit) {
				out.add(Action.SPLIT);
			}
			return out;
		}
		out.add(Action.HIT);
		out.add(Action.STAND);
		if (hand.cards.size() == 2 && balance >= hand.bet && (!hand.split || rules.doubleAfterSplit())) {
			out.add(Action.DOUBLE);
		}
		if (canSplit) {
			out.add(Action.SPLIT);
		}
		if (rules.lateSurrender() && seat.hands.size() == 1 && !hand.split && hand.cards.size() == 2) {
			out.add(Action.SURRENDER);
		}
		return out;
	}

	public List<Action> legal(int seatNo) {
		return legal(seatNo, Long.MAX_VALUE);
	}

	/** Extra chips the action puts at risk (collect them before {@link #act}). */
	public long extraStake(int seatNo, Action action) {
		Turn t = current();
		if (t == null || t.seat().seat != seatNo) {
			return 0;
		}
		return action == Action.DOUBLE || action == Action.SPLIT ? t.hand().bet : 0;
	}

	/** Applies an action (the caller checked the balance). False if not legal now. */
	public boolean act(int seatNo, Action action) {
		if (!legal(seatNo).contains(action)) {
			return false;
		}
		Turn t = current();
		Seat seat = t.seat();
		Hand hand = t.hand();
		switch (action) {
			case HIT -> {
				hand.cards.add(source.draw());
				if (Hands.total(hand.cards) >= 21) {
					hand.done = true;
				}
			}
			case STAND -> hand.done = true;
			case DOUBLE -> {
				hand.bet *= 2;
				hand.doubled = true;
				hand.cards.add(source.draw());
				hand.done = true;
			}
			case SURRENDER -> {
				hand.surrendered = true;
				hand.done = true;
				hand.outcome = Outcome.SURRENDER;
				hand.ret = BlackjackRules.surrenderReturn(hand.bet);
			}
			case SPLIT -> {
				boolean aces = hand.cards.getFirst().isAce();
				Hand second = new Hand(hand.bet);
				second.cards.add(hand.cards.removeLast());
				seat.hands.add(t.handIndex() + 1, second);
				for (Hand h : List.of(hand, second)) {
					h.split = true;
					h.splitAces = aces;
					h.cards.add(source.draw());
					if (aces) {
						h.done = !(rules.resplitAces() && Hands.isPair(h.cards) && seat.hands.size() < rules.maxHands());
					} else if (Hands.total(h.cards) == 21) {
						h.done = true;
					}
				}
			}
		}
		if (current() == null) {
			advance();
		}
		return true;
	}

	/** Stands every open hand of a seat (turn timeout, disconnect) and declines pending insurance. */
	public void standAll(int seatNo) {
		Seat s = seat(seatNo);
		if (s == null) {
			return;
		}
		if (phase == Phase.INSURANCE && !s.insuranceDecided) {
			decline(seatNo);
		}
		for (Hand h : s.hands) {
			h.done = true;
		}
		if (phase == Phase.TURNS && current() == null) {
			advance();
		}
	}

	private void advance() {
		while (phase == Phase.TURNS) {
			if (cur >= 0 && cur < seats.size()) {
				Seat s = seats.get(cur);
				if (!s.settled && s.hands.stream().anyMatch(h -> !h.done)) {
					return;
				}
			}
			cur++;
			if (cur >= seats.size()) {
				playDealer();
				return;
			}
		}
	}

	// ---- dealer and settlement -------------------------------------------------------------------

	/** Some unsettled, non-bust, non-surrendered hand remains, so the dealer must draw. */
	private boolean anyLiveHand() {
		for (Seat s : seats) {
			if (s.settled) {
				continue;
			}
			for (Hand h : s.hands) {
				if (!h.surrendered && Hands.total(h.cards) <= 21) {
					return true;
				}
			}
		}
		return false;
	}

	/** Dealer draws while below 17, and on soft 17 under H17. PURE helper (also used by tests). */
	public static boolean dealerShouldHit(List<Card> dealerCards, boolean hitSoft17) {
		Hands.Value v = Hands.value(dealerCards);
		return v.total() < 17 || (v.total() == 17 && v.soft() && hitSoft17);
	}

	private void playDealer() {
		holeRevealed = true;
		if (anyLiveHand()) {
			while (dealerShouldHit(dealer, rules.dealerHitsSoft17())) {
				dealer.add(source.draw());
			}
		}
		int d = Hands.total(dealer);
		for (Seat s : seats) {
			if (s.settled) {
				continue;
			}
			for (Hand h : s.hands) {
				h.done = true;
				if (h.surrendered) {
					continue;
				}
				int v = Hands.total(h.cards);
				if (v > 21) {
					h.outcome = Outcome.BUST;
					h.ret = 0;
				} else if (d > 21) {
					h.outcome = Outcome.DEALER_BUST;
					h.ret = h.bet * 2;
				} else if (v > d) {
					h.outcome = Outcome.WIN;
					h.ret = h.bet * 2;
				} else if (v == d) {
					h.outcome = Outcome.PUSH;
					h.ret = h.bet;
				} else {
					h.outcome = Outcome.LOSE;
					h.ret = 0;
				}
			}
			s.settled = true;
		}
		phase = Phase.DONE;
	}

	/** Total return of a seat (hands + insurance). */
	public long returnOf(int seatNo) {
		Seat s = seat(seatNo);
		return s == null ? 0 : s.hands.stream().mapToLong(h -> h.ret).sum() + s.insuranceReturn;
	}

	/** Total chips a seat put at risk (bets incl. doubles/splits + insurance). */
	public long stakedOf(int seatNo) {
		Seat s = seat(seatNo);
		return s == null ? 0 : s.hands.stream().mapToLong(h -> h.bet).sum() + s.insurance;
	}
}

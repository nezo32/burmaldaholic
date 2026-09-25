package dev.nezo.burmaldaholic.games.poker.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cash-game table model (GAME_DESIGN.md §7.1, §7.3): seats (humans + money bots), button movement,
 * sit-out and timeout bookkeeping, the humans' public stats (the HARD opponent model) and the EASY tilt.
 * Pure — the block entity drives it and moves the money. Bot seats are filled / emptied by core
 * {@code TableBots} at the safe point (between hands) through {@link #seatBot} / {@link #unseatBot}; the
 * seats are exposed as core {@link SeatOccupant}s. Same model as Bedrock {@code games/poker/logic/table.ts}.
 */
public final class PokerTable {
	/** One occupied seat. Humans use their UUID string as id, bots their core key {@code "bot:<id>"}. */
	public static final class Seat {
		public final String id;
		/** player name ("" for bots: shown from the profile's name id) */
		public final String name;
		public final boolean human;
		/** bots: the core profile (level = the level it plays here, after the stake gate); null for humans */
		public final BotProfile bot;
		/** bots: where its chips return to; null for humans */
		public final Purse purse;
		public long stack;
		/** chips this human bought in with (buy-in + top-ups), to tell refunds from winnings on cash-out */
		public long invested;
		/** auto check/fold, still posts blinds */
		public boolean sittingOut;
		/** consecutive action timeouts */
		public int timeouts;
		/** hands played while sitting out */
		public int sitOutHands;
		/** leaves at the end of the current hand (stood up, disconnected) */
		public boolean leaving;
		public boolean disconnected;
		/** humans: public stats of the last 50 hands (the HARD opponent model; bots are never modelled) */
		public final List<Ranges.HandStat> history = Ranges.newHistory();
		/** sit-down order (host = longest seated) */
		public final int since;
		/** EASY bots: hands of tilt left (lost a pot &gt; 40 BB) */
		public int tilt;

		Seat(String id, String name, boolean human, BotProfile bot, Purse purse, long stack, int since) {
			this.id = id;
			this.name = name;
			this.human = human;
			this.bot = bot;
			this.purse = purse;
			this.stack = stack;
			this.invested = human ? stack : 0;
			this.since = since;
		}

		/** The seat as a core occupant. */
		public SeatOccupant occupant() {
			if (!human && bot != null) {
				return new SeatOccupant.Bot(bot, BotRole.MONEY, purse == null ? Purse.BANK : purse);
			}
			try {
				return new SeatOccupant.Human(UUID.fromString(id), name);
			} catch (IllegalArgumentException e) {
				return new SeatOccupant.Human(new UUID(0, id.hashCode()), name);
			}
		}
	}

	private final Seat[] seats;
	private long bb;
	private Pots.RakeConfig rake;
	private int button = -1;
	private int handNo;
	private Hand hand;
	private Hand lastHand;
	private int[] handSeats = new int[0];
	/** The Seat objects dealt into the current / last hand (review B1: hand entries match by seat identity, not id). */
	private Seat[] handSeatRefs = new Seat[0];
	private int sitCounter;

	public PokerTable(int maxSeats, long bb, Pots.RakeConfig rake) {
		this.seats = new Seat[Math.max(2, maxSeats)];
		this.bb = bb;
		this.rake = rake;
	}

	public int size() {
		return seats.length;
	}

	public long bb() {
		return bb;
	}

	public long sb() {
		return StakeLevel.smallBlind(bb);
	}

	/** Blinds / rake may change between hands (config reload, stake change on an empty table). */
	public void configure(long bb, Pots.RakeConfig rake) {
		if (!inHand()) {
			this.bb = bb;
		}
		this.rake = rake;
	}

	public int button() {
		return button;
	}

	public int handNo() {
		return handNo;
	}

	public Hand hand() {
		return hand;
	}

	/** Hand player index → seat index. */
	public int[] handSeats() {
		return handSeats.clone();
	}

	public Seat seat(int index) {
		return index < 0 || index >= seats.length ? null : seats[index];
	}

	public List<Seat> occupied() {
		List<Seat> out = new ArrayList<>();
		for (Seat s : seats) {
			if (s != null) {
				out.add(s);
			}
		}
		return out;
	}

	public List<Seat> humans() {
		return occupied().stream().filter(s -> s.human).toList();
	}

	public List<Seat> bots() {
		return occupied().stream().filter(s -> !s.human).toList();
	}

	public int seatIndexOf(String id) {
		for (int i = 0; i < seats.length; i++) {
			if (seats[i] != null && seats[i].id.equals(id)) {
				return i;
			}
		}
		return -1;
	}

	public Seat seatOf(String id) {
		int i = seatIndexOf(id);
		return i < 0 ? null : seats[i];
	}

	public boolean inHand() {
		return hand != null && !hand.complete();
	}

	/**
	 * Hand player index of the seat currently held by {@code id} in the current hand, or -1. Matches the dealt
	 * {@link Seat} object (review B1): a player who left and bought in again is a new seat, not the old entry.
	 */
	public int handIndexOf(String id) {
		Seat s = seatOf(id);
		return s == null ? -1 : handIndexOf(s);
	}

	/** Hand player index of this seat object in the current hand, or -1. */
	public int handIndexOf(Seat s) {
		if (hand == null) {
			return -1;
		}
		for (int k = 0; k < handSeatRefs.length; k++) {
			if (handSeatRefs[k] == s) {
				return k;
			}
		}
		return -1;
	}

	/**
	 * True while {@code id} has an entry in the running hand, seated or not (a folded player who already
	 * stood up still has one). Such a player may not buy in again until the hand is over (review B1).
	 */
	public boolean dealtInto(String id) {
		return inHand() && hand.indexOf(id) >= 0;
	}

	/** True while the player is dealt into the running hand and has not folded. */
	public boolean liveInHand(String id) {
		int k = handIndexOf(id);
		return inHand() && k >= 0 && !hand.player(k).folded();
	}

	/** Current chips of a seat (the hand stack while dealt into a not yet settled hand). */
	public long liveStack(String id) {
		int k = handIndexOf(id);
		if (hand != null && k >= 0) {
			return hand.player(k).stack();
		}
		Seat s = seatOf(id);
		return s == null ? 0 : s.stack;
	}

	/** Chips a seat started the running hand with (what a crash refund returns), else its stack. */
	public long refundableStack(String id) {
		int k = handIndexOf(id);
		if (inHand() && k >= 0) {
			return hand.player(k).startStack;
		}
		Seat s = seatOf(id);
		return s == null ? 0 : s.stack;
	}

	/** Seats a human in the first empty seat; returns the seat index or -1 when full. */
	public int addHuman(String id, String name, long stack) {
		for (int i = 0; i < seats.length; i++) {
			if (seats[i] == null) {
				seats[i] = new Seat(id, name, true, null, null, stack, ++sitCounter);
				return i;
			}
		}
		return -1;
	}

	public Seat removeSeat(String id) {
		int i = seatIndexOf(id);
		if (i < 0) {
			return null;
		}
		Seat s = seats[i];
		seats[i] = null;
		return s;
	}

	/** A money bot sits down (core TableBots safe point); returns the seat index or -1 (full / hand running). */
	public int seatBot(BotProfile profile, Purse purse, long stack) {
		if (inHand() || stack <= 0) {
			return -1;
		}
		for (int i = 0; i < seats.length; i++) {
			if (seats[i] == null) {
				seats[i] = new Seat(profile.key(), "", false, profile, purse, stack, ++sitCounter);
				return i;
			}
		}
		return -1;
	}

	/** A bot leaves (safe point / session end): returns what it holds (its hand stack while dealt in). */
	public long unseatBot(String key) {
		int i = seatIndexOf(key);
		Seat s = i >= 0 ? seats[i] : null;
		if (s == null || s.human) {
			return 0;
		}
		int k = inHand() ? handIndexOf(s) : -1;
		long held = k >= 0 ? hand.player(k).stack() : s.stack;
		seats[i] = null;
		return Math.max(0, held);
	}

	/** Seats as core occupants (null = empty seat), for TableBots. */
	public List<SeatOccupant> occupants() {
		List<SeatOccupant> out = new ArrayList<>(seats.length);
		for (Seat s : seats) {
			out.add(s == null ? null : s.occupant());
		}
		return out;
	}

	/** Seated humans in sit-down order (not those standing up after this hand). */
	public List<String> seatedHumans() {
		return humans().stream().filter(s -> !s.leaving).sorted(java.util.Comparator.comparingInt(s -> s.since)).map(s -> s.id).toList();
	}

	/**
	 * Poker yield rule (BOTS.md §3.3): hands since this seat posted the big blind, counted in seats
	 * clockwise from the last hand's big blind (0 = just posted it, yields first). No hand yet → large.
	 */
	public int handsSinceBigBlind(String id) {
		Hand h = hand != null ? hand : lastHand;
		int seat = seatIndexOf(id);
		if (h == null || seat < 0 || h.bbIndex() >= handSeats.length) {
			return Integer.MAX_VALUE;
		}
		int bbSeat = handSeats[h.bbIndex()];
		return Math.floorMod(bbSeat - seat, seats.length);
	}

	/** Public stats of a seated human (the HARD opponent model; null for bots / too few hands). */
	public Ranges.HumanStats statsOf(String id) {
		Seat s = seatOf(id);
		return s != null && s.human ? Ranges.statsOf(s.history) : null;
	}

	/** Seats that will be dealt in: occupied, chips &gt; 0, not leaving. */
	public List<Integer> dealable() {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < seats.length; i++) {
			Seat s = seats[i];
			if (s != null && s.stack > 0 && !s.leaving) {
				out.add(i);
			}
		}
		return out;
	}

	public boolean canStart() {
		if (inHand()) {
			return false;
		}
		boolean human = false;
		for (Seat s : humans()) {
			if (!s.leaving && s.stack > 0 && !s.sittingOut) {
				human = true;
			}
		}
		return human && dealable().size() >= 2;
	}

	/** Moves the button one seat clockwise (to the next dealt-in seat) and deals. Null if the hand cannot start. */
	public Hand startHand(PokerRng rng) {
		return startHand(rng, null);
	}

	/** {@code deck}: pre-arranged deck for tests (null = shuffle). */
	public Hand startHand(PokerRng rng, int[] deck) {
		if (!canStart()) {
			return null;
		}
		List<Integer> dealt = dealable();
		int n = seats.length;
		int btnSeat = dealt.get(0);
		if (button >= 0) {
			for (int k = 1; k <= n; k++) {
				int i = (button + k) % n;
				if (dealt.contains(i)) {
					btnSeat = i;
					break;
				}
			}
		}
		button = btnSeat;
		handSeats = dealt.stream().mapToInt(Integer::intValue).toArray();
		handSeatRefs = new Seat[handSeats.length];
		for (int k = 0; k < handSeats.length; k++) {
			handSeatRefs[k] = seats[handSeats[k]];
		}
		handNo++;
		List<Hand.Seed> seeds = new ArrayList<>();
		for (int i : dealt) {
			Seat s = seats[i];
			seeds.add(new Hand.Seed(s.id, s.human, s.stack));
		}
		hand = new Hand(seeds, rng, new Hand.Options(sb(), bb, dealt.indexOf(btnSeat), rake, deck));
		return hand;
	}

	/** The last settled hand (results display between hands), or null. */
	public Hand lastHand() {
		return lastHand;
	}

	/**
	 * Copies the finished hand's stacks back to the seats and updates sit-out / VPIP counters. The hand
	 * then moves to {@link #lastHand()}.
	 */
	public void settleHand() {
		if (hand == null || !hand.complete()) {
			return;
		}
		lastHand = hand;
		Hand hand = this.hand;
		this.hand = null;
		for (int k = 0; k < hand.players().size(); k++) {
			Hand.Player p = hand.player(k);
			Seat s = seats[handSeats[k]];
			if (s == null || s != handSeatRefs[k]) {
				continue; // the dealt seat left (and was paid out); never write its hand stack onto a new seat
			}
			s.stack = p.stack();
			if (s.human) {
				Ranges.record(s.history, Ranges.handStat(hand, k));
				if (s.sittingOut) {
					s.sitOutHands++;
				}
			} else {
				long net = hand.result() != null ? hand.result().net()[k] : 0;
				s.tilt = net < -40 * hand.bb() ? 5 : Math.max(0, s.tilt - 1);
			}
		}
	}

	/**
	 * What every seat still at the table holds at the end of a drawn play-out {@code end} (a finished copy
	 * of the current hand, GAME_DESIGN §4.1): humans and bots from the SAME play-out, so a crash mid-hand
	 * returns exactly what the hand would have left on both sides (never the humans' play-out against the
	 * bots' pre-hand stacks). Key = seat id. Empty when no hand runs or {@code end} is not finished.
	 */
	public java.util.Map<String, Long> drawnHoldings(Hand end) {
		java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
		if (hand == null || end == null || !end.complete() || end.players().size() != handSeatRefs.length) {
			return out;
		}
		for (int k = 0; k < handSeatRefs.length; k++) {
			Seat s = seats[handSeats[k]];
			if (s != null && s == handSeatRefs[k] && s.id.equals(end.player(k).id)) {
				out.put(s.id, Math.max(0, end.player(k).stack()));
			}
		}
		return out;
	}

	/** Undoes a hand in progress (table broken / casino off): everyone keeps their start stack. */
	public void abortHand() {
		if (hand == null || hand.complete()) {
			hand = null;
			return;
		}
		for (int k = 0; k < hand.players().size(); k++) {
			Hand.Player p = hand.player(k);
			Seat s = seats[handSeats[k]];
			if (s != null && s == handSeatRefs[k]) {
				s.stack = p.startStack;
			}
		}
		hand = null;
	}

	/** Records a timed-out action; true when the player has just been moved to sitting out. */
	public boolean recordTimeout(String id, int limit) {
		Seat s = seatOf(id);
		if (s == null) {
			return false;
		}
		s.timeouts++;
		if (!s.sittingOut && s.timeouts >= limit) {
			s.sittingOut = true;
			s.sitOutHands = 0;
			return true;
		}
		return false;
	}

	/** A manual action resets the timeout streak. */
	public void recordAction(String id) {
		Seat s = seatOf(id);
		if (s != null) {
			s.timeouts = 0;
		}
	}

	public void sitIn(String id) {
		Seat s = seatOf(id);
		if (s != null) {
			s.sittingOut = false;
			s.timeouts = 0;
			s.sitOutHands = 0;
		}
	}

	public void sitOut(String id) {
		Seat s = seatOf(id);
		if (s != null && !s.sittingOut) {
			s.sittingOut = true;
			s.sitOutHands = 0;
		}
	}

	/** Humans to remove after a hand: leaving, disconnected, broke, or sitting out too long. */
	public List<Seat> toRemove(int sitOutHandsToRemove) {
		return humans().stream()
			.filter(s -> s.leaving || s.disconnected || s.stack <= 0 || (s.sittingOut && s.sitOutHands >= sitOutHandsToRemove))
			.toList();
	}

	/** True when the seat may top up now: seated and not dealt into the running hand. */
	public boolean canTopUp(String id) {
		return seatOf(id) != null && !(inHand() && handIndexOf(id) >= 0);
	}

	/** Adds chips to a seated human between hands (§7.1 top-up). False if not allowed right now. */
	public boolean topUp(String id, long amount) {
		Seat s = seatOf(id);
		if (s == null || amount <= 0 || !canTopUp(id)) {
			return false;
		}
		s.stack += amount;
		s.invested += amount;
		return true;
	}

	/** Ends the idle wait while every human sits out: counts a missed hand for each of them. */
	public void countIdleHand() {
		for (Seat s : humans()) {
			if (s.sittingOut) {
				s.sitOutHands++;
			}
		}
	}
}

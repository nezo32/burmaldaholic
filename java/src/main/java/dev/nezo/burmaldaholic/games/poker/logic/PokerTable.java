package dev.nezo.burmaldaholic.games.poker.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Cash-game table model (GAME_DESIGN.md §7.1, §7.3, §7.4): seats (humans + house bots), button movement,
 * bot filling, sit-out and timeout bookkeeping. Pure — the block entity drives it and moves the money.
 */
public final class PokerTable {
	/** One occupied seat. Humans use their UUID string as id, bots {@code "bot:<n>"}. */
	public static final class Seat {
		public final String id;
		public final String name;
		public final boolean human;
		public final Bots.Tier tier;
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
		/** VPIP of the last 20 hands (humans; sharks read it) */
		public final Deque<Boolean> vpipHistory = new ArrayDeque<>();

		Seat(String id, String name, boolean human, Bots.Tier tier, long stack) {
			this.id = id;
			this.name = name;
			this.human = human;
			this.tier = tier;
			this.stack = stack;
			this.invested = human ? stack : 0;
		}
	}

	public record BotFill(boolean enabled, int[] mix, long buyIn) {}

	/** Bots that joined / left while filling (for announcements). */
	public record FillResult(List<Seat> joined, List<Seat> left) {}

	private final Seat[] seats;
	private long bb;
	private Pots.RakeConfig rake;
	private int button = -1;
	private int handNo;
	private Hand hand;
	private Hand lastHand;
	private int[] handSeats = new int[0];

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

	/** Hand player index of a seat id in the current hand, or -1. */
	public int handIndexOf(String id) {
		return hand == null ? -1 : hand.indexOf(id);
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
				seats[i] = new Seat(id, name, true, null, stack);
				return i;
			}
		}
		return -1;
	}

	/**
	 * Frees a seat for a waiting human: between hands a bot is removed right away (returned); during a
	 * hand the last bot is marked leaving so the seat frees at the end of the hand (returns null).
	 */
	public Seat makeRoom() {
		for (Seat s : seats) {
			if (s == null) {
				return null;
			}
		}
		for (int i = seats.length - 1; i >= 0; i--) {
			Seat s = seats[i];
			if (s != null && !s.human) {
				if (inHand()) {
					s.leaving = true;
					return null;
				}
				seats[i] = null;
				return s;
			}
		}
		return null;
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

	/** Bots wanted: 0 without humans, else max seats − humans − 1 (a seat kept for walk-ins), at least 1 for a lone human. */
	public int botTarget(boolean enabled) {
		int humans = humans().size();
		if (!enabled || humans == 0) {
			return 0;
		}
		int free = seats.length - humans;
		return Math.max(humans == 1 ? Math.min(1, free) : 0, free - 1);
	}

	/** Between hands: removes busted, leaving and surplus bots and adds bots up to the target. */
	public FillResult fillBots(BotFill o, PokerRng rng, Supplier<String> nextId) {
		List<Seat> joined = new ArrayList<>();
		List<Seat> left = new ArrayList<>();
		if (inHand()) {
			return new FillResult(joined, left);
		}
		for (int i = 0; i < seats.length; i++) {
			Seat s = seats[i];
			if (s != null && !s.human && (s.stack <= 0 || s.leaving)) {
				left.add(s);
				seats[i] = null;
			}
		}
		int target = botTarget(o.enabled());
		List<Seat> bots = new ArrayList<>(bots());
		while (bots.size() > target) {
			Seat b = bots.remove(bots.size() - 1);
			removeSeat(b.id);
			left.add(b);
		}
		Set<String> used = new HashSet<>();
		for (Seat b : bots()) {
			used.add(b.name);
		}
		while (bots().size() < target) {
			int free = -1;
			for (int i = 0; i < seats.length; i++) {
				if (seats[i] == null) {
					free = i;
					break;
				}
			}
			if (free < 0) {
				break;
			}
			List<String> names = Bots.NAMES.stream().filter(n -> !used.contains(n)).toList();
			String name = rng.pick(names.isEmpty() ? Bots.NAMES : names);
			used.add(name);
			Seat seat = new Seat(nextId.get(), name, false, Bots.pickTier(rng, o.mix()), o.buyIn());
			seats[free] = seat;
			joined.add(seat);
		}
		return new FillResult(joined, left);
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
			if (s == null || !s.id.equals(p.id)) {
				continue;
			}
			s.stack = p.stack();
			if (s.human) {
				s.vpipHistory.addLast(p.vpip());
				while (s.vpipHistory.size() > 20) {
					s.vpipHistory.removeFirst();
				}
				if (s.sittingOut) {
					s.sitOutHands++;
				}
			}
		}
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
			if (s != null && s.id.equals(p.id)) {
				s.stack = p.startStack;
			}
		}
		hand = null;
	}

	/** VPIP (0..1) per human id, for sharks; only humans with ≥ 5 recorded hands. */
	public Map<String, Double> vpipMap() {
		Map<String, Double> m = new HashMap<>();
		for (Seat s : humans()) {
			if (s.vpipHistory.size() >= 5) {
				long v = s.vpipHistory.stream().filter(Boolean::booleanValue).count();
				m.put(s.id, (double) v / s.vpipHistory.size());
			}
		}
		return m;
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

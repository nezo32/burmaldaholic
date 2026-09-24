package dev.nezo.burmaldaholic.games.poker.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One No-Limit Texas Hold'em hand (GAME_DESIGN.md §7.3). Pure state machine:
 *
 * <pre>
 * new Hand(players, rng, options) → [apply(action)…] → street transitions (burn + deal) →
 * showdown / uncontested → result() (stacks already updated)
 * </pre>
 *
 * Players are indexed clockwise (only those dealt in). Heads-up: the button posts the small blind and
 * acts first preflop. Min bet = BB; min raise = the last full bet/raise increment of the street (≥ BB);
 * an all-in short of a full raise does not reopen betting for players who already acted. The uncalled
 * excess is returned before pots are built. Mirrors the Bedrock engine so both editions behave alike.
 */
public final class Hand {
	public enum Street {
		PREFLOP, FLOP, TURN, RIVER;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public enum ActionType {
		FOLD, CHECK, CALL, BET, RAISE;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Player input. {@code RAISE} covers bets too; {@code to} is the total street bet after the action. */
	public record Action(Kind kind, long to) {
		public enum Kind { FOLD, CHECK, CALL, RAISE, ALL_IN }

		public static Action fold() {
			return new Action(Kind.FOLD, 0);
		}

		public static Action check() {
			return new Action(Kind.CHECK, 0);
		}

		public static Action call() {
			return new Action(Kind.CALL, 0);
		}

		public static Action raiseTo(long to) {
			return new Action(Kind.RAISE, to);
		}

		public static Action allIn() {
			return new Action(Kind.ALL_IN, 0);
		}
	}

	/** Dealt-in player. */
	public static final class Player {
		public final String id;
		public final boolean human;
		public final long startStack;
		long stack;
		/** chips put in during the current street */
		long bet;
		/** chips put in during the whole hand */
		long total;
		final int[] hole = new int[2];
		int holeCount;
		boolean folded;
		boolean allIn;
		/** acted since the last full raise (reopen rule) */
		boolean acted;
		/** voluntarily put chips in preflop */
		boolean vpip;
		/** raised preflop */
		boolean pfr;

		Player(String id, boolean human, long stack) {
			this.id = id;
			this.human = human;
			this.startStack = stack;
			this.stack = stack;
		}

		private Player(Player o) {
			this.id = o.id;
			this.human = o.human;
			this.startStack = o.startStack;
			this.stack = o.stack;
			this.bet = o.bet;
			this.total = o.total;
			System.arraycopy(o.hole, 0, this.hole, 0, 2);
			this.holeCount = o.holeCount;
			this.folded = o.folded;
			this.allIn = o.allIn;
			this.acted = o.acted;
			this.vpip = o.vpip;
			this.pfr = o.pfr;
		}

		public long stack() {
			return stack;
		}

		public long bet() {
			return bet;
		}

		public long total() {
			return total;
		}

		public int[] hole() {
			return Arrays.copyOf(hole, holeCount);
		}

		public boolean folded() {
			return folded;
		}

		public boolean allIn() {
			return allIn;
		}

		public boolean vpip() {
			return vpip;
		}

		public boolean pfr() {
			return pfr;
		}

		/** Adds chips to the stack of a player who is not live in this hand (top-up after folding). */
		public void addChips(long amount) {
			stack += Math.max(0, amount);
		}
	}

	public record Seed(String id, boolean human, long stack) {}

	/** @param deck pre-arranged deck for tests (top = index 0), null = shuffle */
	public record Options(long sb, long bb, int button, Pots.RakeConfig rake, int[] deck) {}

	/** Hand log entry (blinds, actions, streets). */
	public sealed interface Event permits Blind, Acted, Dealt {}

	public record Blind(int player, boolean big, long amount, boolean allIn) implements Event {}

	/** {@code amount}: chips added for a call, the total street bet for bet/raise. */
	public record Acted(int player, ActionType type, long amount, boolean allIn) implements Event {}

	public record Dealt(Street street, int[] cards) implements Event {}

	/** @param paid chips each player (by index) put into this pot */
	public record PotResult(long amount, List<Integer> eligible, List<Integer> contributors, long rake, List<Integer> winners,
			long[] shares, int value, long[] paid) {
		public long paidBy(int i) {
			return i >= 0 && i < paid.length ? paid[i] : 0;
		}

		/** Chips player i won from this pot. */
		public long wonBy(int i) {
			long w = 0;
			for (int k = 0; k < winners.size(); k++) {
				if (winners.get(k) == i && k < shares.length) {
					w += shares[k];
				}
			}
			return w;
		}
	}

	/**
	 * @param won    chips won per player (excluding the returned uncalled bet)
	 * @param net    per player vs. the start of the hand
	 * @param values hand value per player (0 = not evaluated / folded)
	 * @param shown  players who show at showdown, in showing order
	 */
	public record Result(boolean uncontested, Pots.Uncalled uncalled, List<PotResult> pots, long[] won, long[] net, int[] values,
			List<Integer> shown, long rake) {}

	/**
	 * @param minRaiseTo min total street bet for a raise (all-in if the stack is shorter)
	 * @param maxRaiseTo max total street bet (all-in)
	 * @param isBet      nothing bet yet this street (the raise is a "bet")
	 */
	public record Legal(long toCall, boolean canCheck, boolean canRaise, long minRaiseTo, long maxRaiseTo, boolean isBet) {
		static final Legal NONE = new Legal(0, false, false, 0, 0, false);
	}

	private final List<Player> players;
	private final int button;
	private final int sbIndex;
	private final int bbIndex;
	private final long sb;
	private final long bb;
	private final Pots.RakeConfig rake;
	private final int[] deck;
	private int deckPos;
	private final List<Integer> board = new ArrayList<>();
	private final List<Event> events = new ArrayList<>();
	private Street street = Street.PREFLOP;
	private int toAct = -1;
	private long currentBet;
	private long minRaise;
	private int aggressor = -1;
	private int lastAggressor = -1;
	private int preflopRaiser = -1;
	private boolean sawFlop;
	private int seq;
	private boolean complete;
	private Result result;

	/** Deals a new hand. {@code seeds} must be ≥ 2, clockwise, all with stack &gt; 0. */
	public Hand(List<Seed> seeds, PokerRng rng, Options o) {
		int n = seeds.size();
		if (n < 2) {
			throw new IllegalArgumentException("need at least 2 players");
		}
		List<Player> ps = new ArrayList<>(n);
		for (Seed s : seeds) {
			if (s.stack() <= 0) {
				throw new IllegalArgumentException("player " + s.id() + " has no chips");
			}
			ps.add(new Player(s.id(), s.human(), s.stack()));
		}
		this.players = Collections.unmodifiableList(ps);
		this.button = Math.floorMod(o.button(), n);
		this.sbIndex = n == 2 ? button : (button + 1) % n;
		this.bbIndex = (sbIndex + 1) % n;
		this.sb = o.sb();
		this.bb = o.bb();
		this.rake = o.rake() == null ? Pots.RakeConfig.NONE : o.rake();
		this.deck = o.deck() != null ? o.deck().clone() : Cards.shuffledDeck(rng);
		this.currentBet = bb;
		this.minRaise = bb;
		postBlind(sbIndex, sb, false);
		postBlind(bbIndex, bb, true);
		for (int round = 0; round < 2; round++) {
			for (int k = 1; k <= n; k++) {
				Player p = ps.get((button + k) % n);
				p.hole[p.holeCount++] = draw();
			}
		}
		int first = n == 2 ? sbIndex : (bbIndex + 1) % n;
		toAct = findNext(first);
		if (toAct < 0) {
			endStreet();
		}
	}

	/** Deep copy (the drawn-outcome play-out runs on a copy; the dealt deck order is kept). */
	private Hand(Hand o) {
		List<Player> ps = new ArrayList<>(o.players.size());
		for (Player p : o.players) {
			ps.add(new Player(p));
		}
		this.players = Collections.unmodifiableList(ps);
		this.button = o.button;
		this.sbIndex = o.sbIndex;
		this.bbIndex = o.bbIndex;
		this.sb = o.sb;
		this.bb = o.bb;
		this.rake = o.rake;
		this.deck = o.deck.clone();
		this.deckPos = o.deckPos;
		this.board.addAll(o.board);
		this.events.addAll(o.events);
		this.street = o.street;
		this.toAct = o.toAct;
		this.currentBet = o.currentBet;
		this.minRaise = o.minRaise;
		this.aggressor = o.aggressor;
		this.lastAggressor = o.lastAggressor;
		this.preflopRaiser = o.preflopRaiser;
		this.sawFlop = o.sawFlop;
		this.seq = o.seq;
		this.complete = o.complete;
		this.result = o.result;
	}

	/** An independent copy of this hand in its current state. */
	public Hand copy() {
		return new Hand(this);
	}

	/** Who acts in a play-out: the action for player {@code i} of the (copied) hand. */
	@FunctionalInterface
	public interface Actor {
		Action act(Hand h, int i);
	}

	/**
	 * Plays a COPY of this hand to the end (GAME_DESIGN §4.1 drawn outcome): every decision comes from
	 * {@code actor} (coerced to a legal action; errors check/fold), the board comes from the deck already
	 * dealt. Returns the finished copy, or null if it did not finish within {@code maxActions}. This hand
	 * is not changed.
	 */
	public Hand playOut(Actor actor, int maxActions) {
		Hand h = copy();
		for (int n = 0; n < maxActions && !h.complete && h.toAct >= 0; n++) {
			Action a;
			try {
				a = h.coerce(actor.act(h, h.toAct));
				h.apply(a);
			} catch (RuntimeException e) {
				h.apply(h.legal().canCheck() ? Action.check() : Action.fold());
			}
		}
		return h.complete ? h : null;
	}

	/** The dealt deck (tests: RNG independence checks compare it). */
	public int[] deck() {
		return deck.clone();
	}

	// ---- accessors ------------------------------------------------------------------------------

	public List<Player> players() {
		return players;
	}

	public Player player(int i) {
		return players.get(i);
	}

	public int indexOf(String id) {
		for (int i = 0; i < players.size(); i++) {
			if (players.get(i).id.equals(id)) {
				return i;
			}
		}
		return -1;
	}

	public int button() {
		return button;
	}

	public int sbIndex() {
		return sbIndex;
	}

	public int bbIndex() {
		return bbIndex;
	}

	public long sb() {
		return sb;
	}

	public long bb() {
		return bb;
	}

	public Street street() {
		return street;
	}

	public List<Integer> board() {
		return Collections.unmodifiableList(board);
	}

	public List<Event> events() {
		return Collections.unmodifiableList(events);
	}

	/** Index of the player to act, -1 when nobody (hand complete). */
	public int toAct() {
		return toAct;
	}

	public long currentBet() {
		return currentBet;
	}

	public int preflopRaiser() {
		return preflopRaiser;
	}

	public boolean sawFlop() {
		return sawFlop;
	}

	/** Increments on every applied action / transition (stale input guard). */
	public int seq() {
		return seq;
	}

	public boolean complete() {
		return complete;
	}

	public Result result() {
		return result;
	}

	// ---- mechanics ------------------------------------------------------------------------------

	private int draw() {
		if (deckPos >= deck.length) {
			throw new IllegalStateException("deck exhausted");
		}
		return deck[deckPos++];
	}

	private static long put(Player p, long amount) {
		long a = Math.max(0, Math.min(p.stack, amount));
		p.stack -= a;
		p.bet += a;
		p.total += a;
		if (p.stack == 0) {
			p.allIn = true;
		}
		return a;
	}

	private void postBlind(int i, long amount, boolean big) {
		Player p = players.get(i);
		long paid = put(p, amount);
		events.add(new Blind(i, big, paid, p.allIn));
	}

	private static boolean canAct(Player p) {
		return !p.folded && !p.allIn;
	}

	private boolean needsAction(Player p) {
		if (!canAct(p)) {
			return false;
		}
		if (p.bet < currentBet) {
			return true;
		}
		if (p.acted) {
			return false;
		}
		for (Player q : players) {
			if (q != p && canAct(q)) {
				return true;
			}
		}
		return false;
	}

	private int findNext(int start) {
		int n = players.size();
		for (int k = 0; k < n; k++) {
			int i = (start + k) % n;
			if (needsAction(players.get(i))) {
				return i;
			}
		}
		return -1;
	}

	/** Players still contesting the pot. */
	public List<Integer> livePlayers() {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < players.size(); i++) {
			if (!players.get(i).folded) {
				out.add(i);
			}
		}
		return out;
	}

	/** Legal options for the player to act. */
	public Legal legal() {
		return legal(toAct);
	}

	public Legal legal(int i) {
		if (complete || i < 0 || i >= players.size()) {
			return Legal.NONE;
		}
		Player p = players.get(i);
		long toCall = Math.min(p.stack, Math.max(0, currentBet - p.bet));
		long maxRaiseTo = p.bet + p.stack;
		boolean othersCanAct = false;
		for (int j = 0; j < players.size(); j++) {
			if (j != i && canAct(players.get(j))) {
				othersCanAct = true;
				break;
			}
		}
		boolean canRaise = !p.acted && maxRaiseTo > currentBet && othersCanAct;
		return new Legal(toCall, p.bet >= currentBet, canRaise, Math.min(currentBet + minRaise, maxRaiseTo), maxRaiseTo, currentBet == 0);
	}

	/** Normalizes a requested action into a legal one (timeouts, bots, stale client input). */
	public Action coerce(Action a) {
		Legal l = legal();
		return switch (a.kind()) {
			case FOLD -> l.canCheck() ? Action.check() : a;
			case CHECK -> l.canCheck() ? a : Action.fold();
			case CALL -> l.canCheck() ? Action.check() : a;
			case ALL_IN -> l.canRaise() ? a : (l.toCall() > 0 ? Action.call() : Action.check());
			case RAISE -> {
				if (!l.canRaise()) {
					yield l.canCheck() ? Action.check() : Action.call();
				}
				yield Action.raiseTo(Math.max(l.minRaiseTo(), Math.min(l.maxRaiseTo(), a.to())));
			}
		};
	}

	/** Applies the current player's action. Throws {@link IllegalStateException} on an illegal action. */
	public void apply(Action a) {
		if (complete || toAct < 0) {
			throw new IllegalStateException("no player to act");
		}
		int i = toAct;
		Player p = players.get(i);
		Legal l = legal(i);
		boolean pre = street == Street.PREFLOP;
		switch (a.kind()) {
			case FOLD -> {
				p.folded = true;
				events.add(new Acted(i, ActionType.FOLD, 0, false));
			}
			case CHECK -> {
				if (!l.canCheck()) {
					throw new IllegalStateException("cannot check");
				}
				events.add(new Acted(i, ActionType.CHECK, 0, false));
			}
			case CALL -> {
				if (l.toCall() <= 0) {
					throw new IllegalStateException("nothing to call");
				}
				long added = put(p, l.toCall());
				if (pre) {
					p.vpip = true;
				}
				events.add(new Acted(i, ActionType.CALL, added, p.allIn));
			}
			case RAISE, ALL_IN -> {
				long to = a.kind() == Action.Kind.ALL_IN ? l.maxRaiseTo() : a.to();
				if (a.kind() == Action.Kind.ALL_IN && to <= currentBet) {
					long added = put(p, to - p.bet);
					if (pre) {
						p.vpip = true;
					}
					events.add(new Acted(i, ActionType.CALL, added, p.allIn));
					break;
				}
				if (!l.canRaise()) {
					throw new IllegalStateException("cannot raise");
				}
				if (to > l.maxRaiseTo()) {
					throw new IllegalStateException("raise exceeds stack");
				}
				if (to < l.minRaiseTo()) {
					throw new IllegalStateException("raise below minimum " + l.minRaiseTo());
				}
				if (to <= currentBet) {
					throw new IllegalStateException("raise must exceed the current bet");
				}
				long increment = to - currentBet;
				boolean isBet = currentBet == 0;
				put(p, to - p.bet);
				if (increment >= minRaise) {
					minRaise = increment;
					for (Player q : players) {
						if (q != p) {
							q.acted = false;
						}
					}
				}
				currentBet = to;
				aggressor = i;
				if (pre) {
					p.vpip = true;
					p.pfr = true;
					preflopRaiser = i;
				}
				events.add(new Acted(i, isBet ? ActionType.BET : ActionType.RAISE, to, p.allIn));
			}
		}
		p.acted = true;
		seq++;
		if (livePlayers().size() == 1) {
			finish();
			return;
		}
		int next = findNext((i + 1) % players.size());
		if (next >= 0) {
			toAct = next;
		} else {
			endStreet();
		}
	}

	private void endStreet() {
		if (aggressor >= 0) {
			lastAggressor = aggressor;
		}
		while (true) {
			if (livePlayers().size() <= 1 || street == Street.RIVER) {
				finish();
				return;
			}
			for (Player p : players) {
				p.bet = 0;
				p.acted = false;
			}
			currentBet = 0;
			minRaise = bb;
			aggressor = -1;
			draw(); // burn
			Street next = Street.values()[street.ordinal() + 1];
			int count = next == Street.FLOP ? 3 : 1;
			int[] cards = new int[count];
			for (int k = 0; k < count; k++) {
				cards[k] = draw();
				board.add(cards[k]);
			}
			street = next;
			if (next == Street.FLOP) {
				sawFlop = true;
			}
			events.add(new Dealt(next, cards));
			seq++;
			int first = findNext((button + 1) % players.size());
			if (first >= 0) {
				toAct = first;
				return;
			}
			// nobody can act (all-in run-out): keep dealing
		}
	}

	private int[] sevenOf(Player p) {
		int[] cards = new int[2 + board.size()];
		cards[0] = p.hole[0];
		cards[1] = p.hole[1];
		for (int k = 0; k < board.size(); k++) {
			cards[2 + k] = board.get(k);
		}
		return cards;
	}

	/** Value of player i's best hand with the current board (needs ≥ 3 board cards), 0 otherwise. */
	public int valueOf(int i) {
		if (board.size() < 3) {
			return 0;
		}
		return HandEvaluator.evaluate(sevenOf(players.get(i)));
	}

	/** Completes the hand: returns the uncalled bet, builds pots, rakes, awards, updates stacks. */
	private void finish() {
		toAct = -1;
		complete = true;
		seq++;
		int n = players.size();
		long[] totals = new long[n];
		boolean[] folded = new boolean[n];
		for (int i = 0; i < n; i++) {
			totals[i] = players.get(i).total;
			folded[i] = players.get(i).folded;
		}
		Pots.Uncalled unc = Pots.uncalledBet(totals);
		if (unc != null) {
			totals[unc.player()] -= unc.amount();
			Player p = players.get(unc.player());
			p.stack += unc.amount();
			p.total -= unc.amount();
			if (p.stack > 0) {
				p.allIn = false;
			}
		}
		List<Integer> alive = livePlayers();
		boolean uncontested = alive.size() == 1;
		int[] values = new int[n];
		if (!uncontested) {
			for (int i : alive) {
				values[i] = HandEvaluator.evaluate(sevenOf(players.get(i)));
			}
		}
		long[] won = new long[n];
		List<PotResult> results = new ArrayList<>();
		long totalRake = 0;
		for (Pots.Pot pot : Pots.buildPots(totals, folded)) {
			int humans = 0;
			for (int c : pot.contributors()) {
				if (players.get(c).human) {
					humans++;
				}
			}
			long botContrib = 0;
			for (int c = 0; c < n; c++) {
				if (!players.get(c).human) {
					botContrib += pot.paidBy(c);
				}
			}
			long r = Pots.rakeFor(pot.amount(), humans, sawFlop, bb, rake, botContrib);
			long net = pot.amount() - r;
			int best = 0;
			for (int e : pot.eligible()) {
				best = Math.max(best, values[e]);
			}
			final int bestValue = best;
			List<Integer> winners = new ArrayList<>();
			for (int e : pot.eligible()) {
				if (uncontested || values[e] == bestValue) {
					winners.add(e);
				}
			}
			winners.sort((a, b) -> Integer.compare(Pots.orderFromButton(a, button, n), Pots.orderFromButton(b, button, n)));
			long[] shares = Pots.splitPot(net, winners.size());
			for (int k = 0; k < winners.size(); k++) {
				won[winners.get(k)] += shares[k];
			}
			if (winners.isEmpty()) {
				r = pot.amount(); // cannot happen (someone always remains) — never mint chips
			}
			totalRake += r;
			results.add(new PotResult(pot.amount(), pot.eligible(), pot.contributors(), r, List.copyOf(winners), shares,
				uncontested ? 0 : bestValue, pot.paid()));
		}
		long[] net = new long[n];
		for (int i = 0; i < n; i++) {
			Player p = players.get(i);
			p.stack += won[i];
			net[i] = p.stack - p.startStack;
		}
		List<Integer> shown = uncontested ? List.of() : showdownOrder(alive, results);
		result = new Result(uncontested, unc, List.copyOf(results), won, net, values, shown, totalRake);
	}

	/**
	 * Showdown: the last aggressor shows first, else the first live player left of the button; then
	 * clockwise. Later players show only if they win (or split) a pot — losers muck — unless the hand was
	 * an all-in run-out, where everybody shows.
	 */
	private List<Integer> showdownOrder(List<Integer> alive, List<PotResult> pots) {
		int n = players.size();
		int start = lastAggressor >= 0 && !players.get(lastAggressor).folded ? lastAggressor : -1;
		List<Integer> ordered = new ArrayList<>(alive);
		ordered.sort((a, b) -> Integer.compare(Pots.orderFromButton(a, button, n), Pots.orderFromButton(b, button, n)));
		int first = start >= 0 ? start : ordered.get(0);
		List<Integer> rest = new ArrayList<>();
		for (int i : ordered) {
			if (i != first) {
				rest.add(i);
			}
		}
		rest.sort((a, b) -> Integer.compare(Math.floorMod(a - first, n), Math.floorMod(b - first, n)));
		Set<Integer> winners = new HashSet<>();
		for (PotResult p : pots) {
			winners.addAll(p.winners());
		}
		int notAllIn = 0;
		for (int i : alive) {
			if (!players.get(i).allIn) {
				notAllIn++;
			}
		}
		boolean allInShowdown = notAllIn <= 1;
		List<Integer> out = new ArrayList<>();
		out.add(first);
		for (int i : rest) {
			if (allInShowdown || winners.contains(i)) {
				out.add(i);
			}
		}
		return List.copyOf(out);
	}

	/** Total chips in the middle (pots + current street bets). */
	public long potTotal() {
		long t = 0;
		for (Player p : players) {
			t += p.total;
		}
		return t;
	}

	/**
	 * Pot amounts for display while the hand runs: the main pot first, then one side pot per all-in level
	 * (blinds and unmatched bets of players who can still act do not create side pots). Sums to
	 * {@link #potTotal()}.
	 */
	public List<Long> displayPots() {
		java.util.TreeSet<Long> levels = new java.util.TreeSet<>();
		long max = 0;
		for (Player p : players) {
			max = Math.max(max, p.total);
			if (!p.folded && p.allIn && p.total > 0) {
				levels.add(p.total);
			}
		}
		levels.add(max);
		List<Long> out = new ArrayList<>();
		long prev = 0;
		for (long level : levels) {
			if (level <= prev) {
				continue;
			}
			long amount = 0;
			for (Player p : players) {
				amount += Math.min(p.total, level) - Math.min(p.total, prev);
			}
			if (amount > 0) {
				out.add(amount);
			}
			prev = level;
		}
		return out;
	}

	/** Pots as they stand now (display), built from contributions so far. */
	public List<Pots.Pot> currentPots() {
		int n = players.size();
		long[] totals = new long[n];
		boolean[] folded = new boolean[n];
		for (int i = 0; i < n; i++) {
			totals[i] = players.get(i).total;
			folded[i] = players.get(i).folded;
		}
		return Pots.buildPots(totals, folded);
	}
}

package dev.nezo.burmaldaholic.games.poker.logic;

import dev.nezo.burmaldaholic.games.poker.logic.Hand.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * House bots: Fish / Regular / Shark (GAME_DESIGN.md §7.4). A bot sees only its own cards and public
 * information (no collusion): the game builds a {@link View} with {@link #view}, computes the equity
 * when {@link #samplesFor} says so, then calls {@link #decide}.
 */
public final class Bots {
	public enum Tier {
		FISH, REGULAR, SHARK;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Tier byId(String id) {
			for (Tier t : values()) {
				if (t.id().equals(id)) {
					return t;
				}
			}
			return REGULAR;
		}
	}

	public enum Position { EARLY, MIDDLE, LATE, SB, BB }

	/** Fixed, non-localized bot names (GAME_DESIGN.md §7.4, same list as Bedrock). */
	public static final List<String> NAMES = List.of("Lucky Steve", "Grandpa Pavel", "Creeper42", "Mr. Blocksworth", "Diamond Dave",
		"Aunt Zoya", "Redstone Rick", "Nether Nick", "Emerald Emma", "Sir Oinksalot", "Baba Valya", "Enderman Ed");

	/**
	 * What a bot may know.
	 *
	 * @param pot              all chips in the middle (pots + current street bets)
	 * @param limpers          preflop: players who just called the big blind
	 * @param facingRaise      preflop: someone raised above the big blind
	 * @param preflopRaiser    this bot made the last preflop raise
	 * @param opponents        opponents still in the hand
	 * @param loosestHumanVpip highest VPIP (0..1) among live human opponents, -1 if unknown
	 */
	public record View(int[] hole, int[] board, Hand.Street street, long pot, long toCall, long stack, long bet, long currentBet,
			long bb, boolean canCheck, boolean canRaise, long minRaiseTo, long maxRaiseTo, Position position, int limpers,
			boolean facingRaise, boolean preflopRaiser, int opponents, double loosestHumanVpip) {}

	private Bots() {}

	/** Preflop position of player i (early = first 2 to act, late = cutoff/button). */
	public static Position positionOf(Hand h, int i) {
		int n = h.players().size();
		if (n == 2) {
			return i == h.button() ? Position.SB : Position.BB;
		}
		if (i == h.sbIndex()) {
			return Position.SB;
		}
		if (i == h.bbIndex()) {
			return Position.BB;
		}
		if (i == h.button()) {
			return Position.LATE;
		}
		int first = (h.bbIndex() + 1) % n;
		int order = Math.floorMod(i - first, n);
		if (order < 2) {
			return Position.EARLY;
		}
		if ((i + 1) % n == h.button()) {
			return Position.LATE;
		}
		return Position.MIDDLE;
	}

	/** Public view for the bot at hand index i. {@code vpip}: human id → VPIP (0..1). */
	public static View view(Hand h, int i, Map<String, Double> vpip) {
		Hand.Player p = h.player(i);
		Hand.Legal l = h.legal(i);
		List<Integer> live = h.livePlayers();
		double loosest = -1;
		int opponents = 0;
		for (int j : live) {
			if (j == i) {
				continue;
			}
			opponents++;
			Hand.Player q = h.player(j);
			Double v = q.human && vpip != null ? vpip.get(q.id) : null;
			if (v != null) {
				loosest = Math.max(loosest, v);
			}
		}
		int limpers = 0;
		boolean preflop = h.street() == Hand.Street.PREFLOP;
		if (preflop && h.currentBet() <= h.bb()) {
			for (int j = 0; j < h.players().size(); j++) {
				Hand.Player q = h.player(j);
				if (j != i && q.vpip && !q.pfr) {
					limpers++;
				}
			}
		}
		int[] board = new int[h.board().size()];
		for (int k = 0; k < board.length; k++) {
			board[k] = h.board().get(k);
		}
		return new View(p.hole(), board, h.street(), h.potTotal(), l.toCall(), p.stack, p.bet, h.currentBet(), h.bb(), l.canCheck(),
			l.canRaise(), l.minRaiseTo(), l.maxRaiseTo(), positionOf(h, i), limpers, preflop && h.currentBet() > h.bb(),
			h.preflopRaiser() == i, opponents, loosest);
	}

	/** Shark equity ranges: live opponents who raised preflop hold a top-40 % Chen hand. */
	public static int[] opponentRanges(Hand h, int i) {
		List<Integer> out = new ArrayList<>();
		for (int j : h.livePlayers()) {
			if (j != i) {
				out.add(h.player(j).pfr ? Equity.top40Chen() : Integer.MIN_VALUE);
			}
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	/** Monte-Carlo samples a tier needs on this street (0 = no simulation). */
	public static int samplesFor(Tier tier, Hand.Street street, int regularSamples, int sharkSamples) {
		if (tier == Tier.FISH || street == Hand.Street.PREFLOP) {
			return 0;
		}
		return tier == Tier.REGULAR ? regularSamples : sharkSamples;
	}

	/** Picks a tier from Fish/Regular/Shark percentages (normalized; all zero = Regular). */
	public static Tier pickTier(PokerRng rng, int[] mix) {
		int total = 0;
		int[] w = new int[3];
		for (int k = 0; k < 3; k++) {
			w[k] = mix != null && k < mix.length ? Math.max(0, mix[k]) : 0;
			total += w[k];
		}
		if (total <= 0) {
			return Tier.REGULAR;
		}
		int roll = rng.nextInt(total);
		for (int k = 0; k < 3; k++) {
			roll -= w[k];
			if (roll < 0) {
				return Tier.values()[k];
			}
		}
		return Tier.SHARK;
	}

	/** Total street bet for a bet/raise of {@code fraction} of the pot. */
	public static long sizeTo(View v, double fraction) {
		if (v.currentBet() == 0) {
			return Math.max(v.bb(), Math.round(v.pot() * fraction));
		}
		return Math.round(v.currentBet() + fraction * (v.pot() + v.toCall()));
	}

	private static Action passive(View v) {
		return v.canCheck() ? Action.check() : Action.fold();
	}

	private static Action callOrCheck(View v) {
		return v.toCall() > 0 ? Action.call() : Action.check();
	}

	private static double potOdds(View v) {
		return v.toCall() > 0 ? (double) v.toCall() / (v.pot() + v.toCall()) : 0;
	}

	/** Made-hand category that uses at least one hole card (0 = nothing beyond the board). */
	public static int madeCategory(int[] hole, int[] board) {
		if (board.length < 3) {
			return 0;
		}
		int[] all = new int[2 + board.length];
		all[0] = hole[0];
		all[1] = hole[1];
		System.arraycopy(board, 0, all, 2, board.length);
		int cat = HandEvaluator.category(HandEvaluator.evaluate(all));
		return cat > boardCategory(board) ? cat : 0;
	}

	private static int boardCategory(int[] board) {
		if (board.length >= 5) {
			return HandEvaluator.category(HandEvaluator.evaluate(board, Math.min(board.length, 7)));
		}
		int[] counts = new int[15];
		for (int c : board) {
			counts[Cards.rank(c)]++;
		}
		int first = 0, second = 0;
		for (int n : counts) {
			if (n > first) {
				second = first;
				first = n;
			} else if (n > second) {
				second = n;
			}
		}
		if (first == 4) {
			return HandEvaluator.FOUR_OF_A_KIND;
		}
		if (first == 3) {
			return second == 2 ? HandEvaluator.FULL_HOUSE : HandEvaluator.THREE_OF_A_KIND;
		}
		if (first == 2) {
			return second == 2 ? HandEvaluator.TWO_PAIR : HandEvaluator.PAIR;
		}
		return HandEvaluator.HIGH_CARD;
	}

	private static boolean pocketPair(int[] h) {
		return Cards.rank(h[0]) == Cards.rank(h[1]);
	}

	private static boolean suited(int[] h) {
		return Cards.suit(h[0]) == Cards.suit(h[1]);
	}

	private static boolean connected(int[] h) {
		return Math.abs(Cards.rank(h[0]) - Cards.rank(h[1])) == 1;
	}

	private static boolean hasAce(int[] h) {
		return Cards.rank(h[0]) == 14 || Cards.rank(h[1]) == 14;
	}

	// ---- Fish -------------------------------------------------------------------------------------

	private static Action fish(View v, PokerRng rng) {
		double jitter = 0.9 + rng.nextDouble() * 0.2;
		if (v.street() == Hand.Street.PREFLOP) {
			int c = Equity.chen(v.hole()[0], v.hole()[1]);
			if (c >= 12 && v.currentBet() < 3 * v.bb()) {
				return Action.raiseTo((long) (3 * v.bb() * jitter));
			}
			if (v.facingRaise()) {
				if (pocketPair(v.hole()) && v.currentBet() <= 10 * v.bb()) {
					return callOrCheck(v);
				}
				if (c >= 6 && v.toCall() <= 0.2 * (v.stack() + v.bet())) {
					return callOrCheck(v);
				}
				return passive(v);
			}
			return c >= 4 ? callOrCheck(v) : passive(v);
		}
		int made = madeCategory(v.hole(), v.board());
		if (v.toCall() > 0) {
			return made >= 1 && v.toCall() <= v.pot() ? Action.call() : Action.fold();
		}
		if (made >= 2) {
			return Action.raiseTo(sizeTo(v, 0.5 * jitter));
		}
		if (made == 0 && rng.nextDouble() < 0.05) {
			return Action.raiseTo(sizeTo(v, 0.5 * jitter));
		}
		return Action.check();
	}

	// ---- Regular / Shark preflop ---------------------------------------------------------------------

	private static int openThreshold(Position pos) {
		return switch (pos) {
			case EARLY -> 8;
			case MIDDLE, BB -> 7;
			case LATE, SB -> 6;
		};
	}

	private static Action preflopSolid(View v, PokerRng rng, boolean shark) {
		int c = Equity.chen(v.hole()[0], v.hole()[1]);
		int d = shark ? 1 : 0;
		int widen = shark && v.loosestHumanVpip() > 0.4 ? 1 : 0;
		if (!v.facingRaise()) {
			if (c >= openThreshold(v.position()) - d) {
				return Action.raiseTo(3 * v.bb() + v.limpers() * v.bb());
			}
			return passive(v);
		}
		if (c >= 11 - d) {
			return Action.raiseTo(3 * v.currentBet());
		}
		if (shark && suited(v.hole()) && (connected(v.hole()) || hasAce(v.hole())) && rng.nextDouble() < 0.08) {
			return Action.raiseTo(3 * v.currentBet());
		}
		if (c >= 9 - d - widen) {
			return callOrCheck(v);
		}
		return passive(v);
	}

	// ---- Regular / Shark postflop -------------------------------------------------------------------

	private static Action regularPost(View v, double e, PokerRng rng) {
		if (e >= 0.65) {
			return Action.raiseTo(sizeTo(v, 0.66));
		}
		if (v.toCall() > 0) {
			return e >= potOdds(v) + 0.05 ? Action.call() : Action.fold();
		}
		if (v.street() == Hand.Street.FLOP && v.preflopRaiser() && rng.nextDouble() < 0.3) {
			return Action.raiseTo(sizeTo(v, 0.5));
		}
		return Action.check();
	}

	private static Action sharkPost(View v, double e, PokerRng rng) {
		if (e >= 0.85) {
			double r = rng.nextDouble();
			if (r < 0.2) {
				return Action.allIn();
			}
			if (r < 0.35) {
				return callOrCheck(v);
			}
			return Action.raiseTo(sizeTo(v, 0.75));
		}
		if (e >= 0.6) {
			return v.toCall() > 0 ? Action.call() : Action.raiseTo(sizeTo(v, 0.5 + rng.nextDouble() * 0.25));
		}
		if (v.street() == Hand.Street.FLOP && e >= 0.3 && rng.nextDouble() < 0.4) {
			return Action.raiseTo(sizeTo(v, 0.5 + rng.nextDouble() * 0.25));
		}
		if (v.toCall() > 0) {
			return e >= potOdds(v) ? Action.call() : Action.fold();
		}
		return Action.check();
	}

	/** One step less aggressive: raise → call/check, call → fold/check. */
	private static Action lower(View v, Action a) {
		return switch (a.kind()) {
			case RAISE, ALL_IN -> callOrCheck(v);
			case CALL -> passive(v);
			default -> a;
		};
	}

	/** Makes an action legal for the view (clamps sizes, raise → call when raising is closed). */
	public static Action legalize(View v, Action a) {
		return switch (a.kind()) {
			case RAISE, ALL_IN -> {
				if (!v.canRaise()) {
					yield callOrCheck(v);
				}
				if (a.kind() == Action.Kind.ALL_IN) {
					yield a;
				}
				long to = Math.max(v.minRaiseTo(), Math.min(v.maxRaiseTo(), a.to()));
				if (to <= v.currentBet()) {
					yield callOrCheck(v);
				}
				yield to >= v.maxRaiseTo() ? Action.allIn() : Action.raiseTo(to);
			}
			case CALL -> v.toCall() > 0 ? a : Action.check();
			case CHECK -> v.canCheck() ? a : Action.fold();
			case FOLD -> v.canCheck() ? Action.check() : a;
		};
	}

	/**
	 * The bot's action. {@code equity} is the Monte-Carlo result (needed for Regular/Shark after the
	 * flop; ignored otherwise).
	 */
	public static Action decide(Tier tier, View v, double equity, PokerRng rng) {
		Action a;
		if (tier == Tier.FISH) {
			a = fish(v, rng);
		} else if (v.street() == Hand.Street.PREFLOP) {
			a = preflopSolid(v, rng, tier == Tier.SHARK);
		} else if (tier == Tier.REGULAR) {
			a = regularPost(v, equity, rng);
		} else {
			a = sharkPost(v, equity, rng);
		}
		if (tier == Tier.REGULAR && rng.nextDouble() < 0.1) {
			a = lower(v, a);
		}
		return legalize(v, a);
	}

	/** Convenience: full decision incl. the Monte-Carlo run for the bot to act in {@code h}. */
	public static Action decide(Hand h, Tier tier, Map<String, Double> vpip, int regularSamples, int sharkSamples, PokerRng rng) {
		int i = h.toAct();
		View v = view(h, i, vpip);
		int samples = samplesFor(tier, h.street(), regularSamples, sharkSamples);
		double e = 0;
		if (samples > 0) {
			int[] ranges = tier == Tier.SHARK ? opponentRanges(h, i) : null;
			e = Equity.equity(v.hole(), v.board(), v.opponents(), samples, ranges, rng);
		}
		return decide(tier, v, e, rng);
	}
}

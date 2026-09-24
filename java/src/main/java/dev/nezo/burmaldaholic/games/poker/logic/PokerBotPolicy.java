package dev.nezo.burmaldaholic.games.poker.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotWork;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.games.poker.logic.Hand.Action;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The poker bots (BOTS.md §4.3; replaces GAME_DESIGN §7.4's table). Pure; same rules and numbers as
 * Bedrock {@code games/poker/logic/bots.ts}.
 *
 * <p>Levels EASY / NORMAL / HARD are shown with the poker names Fish / Regular / Shark. A bot sees only
 * what a human in its seat sees ({@link #view}: own cards, board, bets, public actions, the humans'
 * public stats) and decides through this {@link BotPolicy}: {@link #work} = the Monte-Carlo job
 * (range-aware {@link Equity.Work}), {@link #decide} = the level's rules, {@link #legalize} = the mandatory
 * last step. Personalities shift thresholds but never cross levels. Randomness = the BOT rng only.
 *
 * <p>Research flaws fixed (docs/research/bots.md §1.6): EASY always calls / shoves with C ≥ 12 or TT+ (it
 * used to fold aces to a shove); EASY calls draws and floats (it never called without a pair); NORMAL /
 * HARD equity is against public ranges (it was against random hands); HARD opens by position, defends
 * by minimum-defence frequency and models the humans.
 */
public final class PokerBotPolicy implements BotPolicy<PokerBotPolicy.View, Action> {
	/** Budget fallback: fewer samples than this → HARD uses the NORMAL rule (BOTS.md §4.3). */
	public static final int MIN_SAMPLES = 50;
	/** Monte-Carlo samples per bot decision in the drawn-outcome play-out (BOTS.md §4.3, both editions). */
	public static final int PLAYOUT_SAMPLES = 16;

	/** @param regularSamples {@code poker.bot.regularSamples}; @param sharkSamples {@code poker.bot.sharkSamples} */
	public record Config(int regularSamples, int sharkSamples) {}

	public enum Position { EP, MP, CO, BTN, SB, BB }

	/**
	 * A live opponent as the bot sees it.
	 *
	 * @param tag   tightest public range tag this hand
	 * @param stats measured public stats (humans with enough hands; bots are never modelled), may be null
	 */
	public record Opp(boolean human, Ranges.Tag tag, Ranges.HumanStats stats, Ranges.OpponentType type, boolean allIn) {}

	/**
	 * What a bot may know (never another seat's hole cards, the deck order or the rng).
	 *
	 * @param pot           all chips in the middle (pots + current street bets)
	 * @param inPosition    postflop: acts after every live opponent
	 * @param limpers       preflop: players who just called the big blind
	 * @param facingRaise   preflop: someone raised above the big blind
	 * @param raises        bets / raises so far on this street (preflop: raises above the BB)
	 * @param foldedTo      preflop: nobody entered the pot before this bot
	 * @param preflopRaiser this bot made the last preflop raise
	 * @param opponents     opponents still in the hand
	 * @param ownTag        the bot's own public range tag
	 * @param tilt          EASY tilt (lost a pot &gt; 40 BB in the last 5 hands)
	 */
	public record View(int[] hole, int[] board, Hand.Street street, long pot, long toCall, long stack, long bet, long currentBet, long bb,
			boolean canCheck, boolean canRaise, long minRaiseTo, long maxRaiseTo, Position position, boolean inPosition, int limpers,
			boolean facingRaise, int raises, boolean foldedTo, boolean preflopRaiser, int opponents, List<Opp> opps, Ranges.Tag ownTag,
			boolean tilt) {
		public View {
			hole = hole.clone();
			board = board.clone();
			opps = List.copyOf(opps);
		}

		public boolean preflop() {
			return street == Hand.Street.PREFLOP;
		}

		long effective() {
			return stack + bet;
		}
	}

	private final Config cfg;

	public PokerBotPolicy(Config cfg) {
		this.cfg = cfg;
	}

	public Config config() {
		return cfg;
	}

	// ---- levels ↔ legacy tiers ------------------------------------------------------------------------

	/** Legacy tier id ({@code fish/regular/shark}, lang {@code gui.burmaldaholic.poker.bot.<tier>}) of a level. */
	public static String tierOf(BotDifficulty level) {
		return level == BotDifficulty.EASY ? "fish" : level == BotDifficulty.HARD ? "shark" : "regular";
	}

	// ---- view ------------------------------------------------------------------------------------------

	/** Seat position (6-max names; heads-up: button / big blind). */
	public static Position positionOf(Hand h, int i) {
		int n = h.players().size();
		if (n == 2) {
			return i == h.button() ? Position.BTN : Position.BB;
		}
		if (i == h.button()) {
			return Position.BTN;
		}
		if (i == h.sbIndex()) {
			return Position.SB;
		}
		if (i == h.bbIndex()) {
			return Position.BB;
		}
		int before = Math.floorMod(h.button() - i, n);
		if (before == 1) {
			return Position.CO;
		}
		if (before == 2) {
			return Position.MP;
		}
		return Position.EP;
	}

	/**
	 * Public view for the bot at hand index {@code i}. {@code stats}: public stats of a human by player id
	 * (null / unknown → not modelled); {@code tilt}: the EASY tilt flag of this bot.
	 */
	public static View view(Hand h, int i, Function<String, Ranges.HumanStats> stats, boolean tilt) {
		Hand.Player p = h.player(i);
		Hand.Legal l = h.legal(i);
		int n = h.players().size();
		Ranges.Tag[] tags = Ranges.publicTags(h);
		boolean pre = h.street() == Hand.Street.PREFLOP;
		int myOrder = Pots.orderFromButton(i, h.button(), n);
		boolean inPosition = true;
		List<Opp> opps = new ArrayList<>();
		for (int j : h.livePlayers()) {
			if (j == i) {
				continue;
			}
			Hand.Player q = h.player(j);
			Ranges.HumanStats st = q.human && stats != null ? stats.apply(q.id) : null;
			opps.add(new Opp(q.human, tags[j], st, q.human ? Ranges.opponentType(st) : Ranges.OpponentType.REGULAR, q.allIn()));
			if (!q.allIn() && Pots.orderFromButton(j, h.button(), n) >= myOrder) {
				inPosition = false;
			}
		}
		int limpers = 0;
		boolean foldedTo = pre && h.currentBet() <= h.bb();
		for (int j = 0; j < n; j++) {
			Hand.Player q = h.player(j);
			if (j == i) {
				continue;
			}
			if (pre && h.currentBet() <= h.bb() && q.vpip() && !q.pfr()) {
				limpers++;
			}
			if (q.vpip()) {
				foldedTo = false;
			}
		}
		int[] board = new int[h.board().size()];
		for (int k = 0; k < board.length; k++) {
			board[k] = h.board().get(k);
		}
		return new View(p.hole(), board, h.street(), h.potTotal(), l.toCall(), p.stack(), p.bet(), h.currentBet(), h.bb(), l.canCheck(),
			l.canRaise(), l.minRaiseTo(), l.maxRaiseTo(), positionOf(h, i), inPosition, limpers, pre && h.currentBet() > h.bb(),
			Ranges.streetRaises(h), foldedTo, h.preflopRaiser() == i, opps.size(), opps, tags[i], tilt);
	}

	// ---- hand reading helpers ---------------------------------------------------------------------------

	private static boolean isPair(int[] h) {
		return Cards.rank(h[0]) == Cards.rank(h[1]);
	}

	private static boolean isSuited(int[] h) {
		return Cards.suit(h[0]) == Cards.suit(h[1]);
	}

	private static int hi(int[] h) {
		return Math.max(Cards.rank(h[0]), Cards.rank(h[1]));
	}

	private static int lo(int[] h) {
		return Math.min(Cards.rank(h[0]), Cards.rank(h[1]));
	}

	private static boolean hasAce(int[] h) {
		return hi(h) == 14;
	}

	/** EASY's "always continue" hands: C ≥ 12 or TT+. */
	public static boolean fishPremium(int[] h) {
		return Equity.chen(h) >= 12 || (isPair(h) && Cards.rank(h[0]) >= 10);
	}

	/** HARD's top 5 % vs a 4-bet / shove: QQ+, AK. */
	public static boolean topFive(int[] h) {
		return (isPair(h) && Cards.rank(h[0]) >= 12) || (hi(h) == 14 && lo(h) == 13);
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
		int first = 0;
		int second = 0;
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

	/** Draw outs with a hole card involved: flush draw 9, open-ended straight draw 8 (flop / turn only). */
	public static int drawOuts(int[] hole, int[] board) {
		if (board.length < 3 || board.length > 4) {
			return 0;
		}
		int[] all = new int[2 + board.length];
		all[0] = hole[0];
		all[1] = hole[1];
		System.arraycopy(board, 0, all, 2, board.length);
		for (int s = 0; s < 4; s++) {
			int n = 0;
			for (int c : all) {
				if (Cards.suit(c) == s) {
					n++;
				}
			}
			if (n == 4 && (Cards.suit(hole[0]) == s || Cards.suit(hole[1]) == s)) {
				return 9;
			}
		}
		boolean[] mask = new boolean[15];
		for (int c : all) {
			mask[Cards.rank(c)] = true;
		}
		if (mask[14]) {
			mask[1] = true;
		}
		boolean[] holeRanks = new boolean[15];
		holeRanks[Cards.rank(hole[0])] = true;
		holeRanks[Cards.rank(hole[1])] = true;
		for (int low = 1; low <= 10; low++) {
			boolean run = true;
			boolean uses = false;
			for (int r = low; r < low + 4; r++) {
				run &= mask[r];
				uses |= holeRanks[r] || (r == 1 && holeRanks[14]);
			}
			if (run && uses && low > 1 && low + 3 < 14) {
				return 8;
			}
		}
		return 0;
	}

	/** Two hole cards above every board card (flop floats). */
	private static boolean overcards(int[] hole, int[] board) {
		if (board.length == 0) {
			return false;
		}
		int top = 0;
		for (int c : board) {
			top = Math.max(top, Cards.rank(c));
		}
		return lo(hole) > top;
	}

	/** Rough equity when no Monte-Carlo result exists (0 samples): made hand / draw only. */
	public static double roughEquity(View v) {
		if (v.board().length < 3) {
			int c = Equity.chen(v.hole());
			return Math.max(0.1, Math.min(0.85, 0.3 + c * 0.025) - 0.05 * Math.max(0, v.opponents() - 1));
		}
		int made = madeCategory(v.hole(), v.board());
		double base = made >= 3 ? 0.85 : made == 2 ? 0.72 : made == 1 ? 0.55 : drawOuts(v.hole(), v.board()) >= 8 ? 0.35 : 0.2;
		return Math.max(0.05, base - 0.06 * Math.max(0, v.opponents() - 1));
	}

	// ---- sizing / action helpers --------------------------------------------------------------------------

	/** Total street bet for a bet/raise of {@code fraction} of the pot. */
	public static long sizeTo(View v, double fraction) {
		if (v.currentBet() == 0) {
			return Math.max(v.bb(), Math.round(v.pot() * fraction));
		}
		return Math.round(v.currentBet() + fraction * (v.pot() + v.toCall()));
	}

	private static Action raise(double to) {
		return Action.raiseTo((long) Math.floor(to));
	}

	private static Action passive(View v) {
		return v.canCheck() ? Action.check() : Action.fold();
	}

	private static Action callOrCheck(View v) {
		return v.toCall() > 0 ? Action.call() : Action.check();
	}

	/** Pot odds of a call: toCall / (pot + toCall). */
	public static double potOdds(View v) {
		return v.toCall() > 0 ? (double) v.toCall() / (v.pot() + v.toCall()) : 0;
	}

	/** Size of the bet faced relative to the pot before it. */
	private static double betFraction(View v) {
		return v.toCall() > 0 ? (double) v.toCall() / Math.max(1, v.pot() - v.toCall()) : 0;
	}

	/** Personality modifiers ({@code bots.personalities} off → every bot plays TAG). */
	private record Traits(int open, double aggression, double bluff, double wide) {
		static Traits of(Personality p) {
			// LAG −1.5 rounds toward the looser integer at use.
			int open = (int) (p.openDelta < 0 ? Math.floor(p.openDelta) : Math.ceil(p.openDelta));
			return new Traits(open, p.aggression, p.bluffFactor, p.callWider);
		}
	}

	/** Raise : call aggression: aggressive personalities turn some calls into raises, passive ones the reverse. */
	private static Action aggress(View v, Action a, double aggression, BotRng rng, boolean strong) {
		if (aggression > 1 && a.kind() == Action.Kind.CALL && strong && rng.nextDouble() < (aggression - 1) * 0.25) {
			return raise(sizeTo(v, 0.75));
		}
		if (aggression < 1 && a.kind() == Action.Kind.RAISE && !strong && rng.nextDouble() < (1 - aggression) * 0.5) {
			return callOrCheck(v);
		}
		return a;
	}

	/** One step less aggressive: raise → call/check, call → fold/check. */
	public static Action lower(View v, Action a) {
		return switch (a.kind()) {
			case RAISE, ALL_IN -> callOrCheck(v);
			case CALL -> passive(v);
			default -> a;
		};
	}

	/** One step more aggressive: fold → call/check, check/call → min raise. */
	public static Action higher(View v, Action a) {
		if (a.kind() == Action.Kind.FOLD) {
			return v.toCall() > 0 ? Action.call() : Action.check();
		}
		if ((a.kind() == Action.Kind.CHECK || a.kind() == Action.Kind.CALL) && v.canRaise()) {
			return Action.raiseTo(v.minRaiseTo());
		}
		return a;
	}

	/** Makes an action legal for the view (clamp sizes, raise → call when raising is closed, fold → check when free). */
	public static Action legalizeAction(View v, Action a) {
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

	// ---- EASY (Fish) ---------------------------------------------------------------------------------------

	private record Fish(Action action, boolean locked) {}

	private static Fish fish(BotProfile bot, View v, BotRng rng) {
		Traits tr = Traits.of(bot.personality());
		int d = tr.open() + (v.tilt() ? -1 : 0);
		if (v.preflop()) {
			int c = Equity.chen(v.hole());
			if (!v.facingRaise()) {
				if (c >= 10 + d) {
					return new Fish(raise(3 * v.bb() + v.limpers() * v.bb()), false);
				}
				if (c >= 4 + d && rng.nextDouble() < 0.65) {
					return new Fish(aggress(v, callOrCheck(v), tr.aggression(), rng, c >= 8), false);
				}
				return new Fish(passive(v), false);
			}
			// Always call / shove with C ≥ 12 or TT+ (research §1.6.1: fish folded aces to a shove).
			if (fishPremium(v.hole())) {
				return new Fish(v.toCall() >= 0.35 * v.effective() ? Action.allIn() : Action.call(), true);
			}
			boolean playable = isPair(v.hole()) || hasAce(v.hole()) || (isSuited(v.hole()) && lo(v.hole()) >= 10);
			if (playable && v.toCall() <= 0.15 * tr.wide() * v.effective()) {
				return new Fish(callOrCheck(v), false);
			}
			return new Fish(passive(v), false);
		}
		int made = madeCategory(v.hole(), v.board());
		int outs = drawOuts(v.hole(), v.board());
		if (v.toCall() > 0) {
			double b = betFraction(v);
			if (made >= 2) {
				return new Fish(aggress(v, Action.call(), tr.aggression(), rng, true), false);
			}
			if (made >= 1 && b <= 1 * tr.wide()) {
				return new Fish(Action.call(), false);
			}
			if (outs >= 8 && b <= 0.5 * tr.wide()) {
				return new Fish(Action.call(), false);
			}
			if (v.street() == Hand.Street.FLOP && overcards(v.hole(), v.board()) && b <= 0.5 && rng.nextDouble() < 0.35) {
				return new Fish(Action.call(), false);
			}
			return new Fish(Action.fold(), false);
		}
		if (made >= 2) {
			return new Fish(raise(sizeTo(v, 0.5)), false);
		}
		if (made == 0 && rng.nextDouble() < 0.05 * tr.bluff()) {
			return new Fish(raise(sizeTo(v, 0.5)), false);
		}
		return new Fish(aggress(v, Action.check(), tr.aggression(), rng, made >= 1), false);
	}

	/** EASY's readable tell (+40 t think): a strong hand (≈ E ≥ 0.7). */
	public static boolean fishStrong(View v) {
		if (v.preflop()) {
			return fishPremium(v.hole());
		}
		return madeCategory(v.hole(), v.board()) >= 2;
	}

	// ---- NORMAL (Regular) -----------------------------------------------------------------------------------

	private static int regularOpen(Position p) {
		return switch (p) {
			case EP, BB -> 8;
			case MP -> 7;
			case CO, BTN, SB -> 6;
		};
	}

	/** Folded to the small blind / heads-up button: only the big blind is left, open 2 points wider. */
	private static int blindBattle(View v) {
		return v.foldedTo() && v.opponents() == 1 && (v.position() == Position.SB || v.position() == Position.BTN) ? 2 : 0;
	}

	private static Action regularPre(BotProfile bot, View v) {
		Traits tr = Traits.of(bot.personality());
		int c = Equity.chen(v.hole());
		if (!v.facingRaise()) {
			if (c >= regularOpen(v.position()) + tr.open() - blindBattle(v)) {
				return raise(3 * v.bb() + v.limpers() * v.bb());
			}
			return passive(v);
		}
		if (c >= 11 + Math.max(0, tr.open())) {
			return raise(3 * v.currentBet());
		}
		if (c >= 9 + tr.open()) {
			return callOrCheck(v);
		}
		return passive(v);
	}

	private static Action regularPost(BotProfile bot, View v, double e, BotRng rng) {
		Traits tr = Traits.of(bot.personality());
		if (e >= 0.65) {
			return v.raises() >= 2 ? callOrCheck(v) : raise(sizeTo(v, 0.66));
		}
		if (v.toCall() > 0) {
			return e >= potOdds(v) + 0.05 ? Action.call() : Action.fold();
		}
		if (v.street() == Hand.Street.FLOP && v.preflopRaiser()) {
			double p = (v.opponents() <= 1 ? 0.5 : 0.25) * tr.bluff();
			if (rng.nextDouble() < p) {
				return raise(sizeTo(v, 0.5));
			}
		}
		return Action.check();
	}

	// ---- HARD (Shark) -------------------------------------------------------------------------------------

	private static int sharkOpen(Position p) {
		return switch (p) {
			case EP -> 9;
			case MP, BB -> 8;
			case CO -> 7;
			case BTN -> 5;
			case SB -> 6;
		};
	}

	private static boolean wheelAce(int[] h) {
		return isSuited(h) && hi(h) == 14 && lo(h) <= 5;
	}

	private static boolean suitedConnector(int[] h) {
		return isSuited(h) && hi(h) - lo(h) == 1 && lo(h) >= 4;
	}

	/** Facing a 4-bet / shove / huge raise preflop (the "top 5 % or E ≥ po" rule). */
	public static boolean bigPreflop(View v) {
		if (!v.preflop() || !v.facingRaise()) {
			return false;
		}
		if (v.raises() >= 3 || v.toCall() >= 0.3 * v.effective()) {
			return true;
		}
		for (Opp o : v.opps()) {
			if (o.allIn() && o.tag() == Ranges.Tag.STRONG) {
				return true;
			}
		}
		return false;
	}

	private static Action sharkPre(BotProfile bot, View v, Equity.Result work, BotRng rng) {
		Traits tr = Traits.of(bot.personality());
		int c = Equity.chen(v.hole());
		boolean ip = v.position() == Position.BTN || v.position() == Position.CO;
		if (!v.facingRaise()) {
			if (v.position() == Position.BB && v.canCheck()) {
				return c >= 8 + tr.open() ? raise(2.5 * v.bb() + v.limpers() * v.bb()) : Action.check();
			}
			boolean nitsBehind = v.opps().stream().anyMatch(o -> o.type() == Ranges.OpponentType.NIT);
			int thr = sharkOpen(v.position()) + tr.open() - (nitsBehind ? 1 : 0);
			if (c >= thr) {
				return raise(2.5 * v.bb() + v.limpers() * v.bb());
			}
			boolean steal = v.foldedTo() && (v.position() == Position.CO || v.position() == Position.BTN || v.position() == Position.SB);
			if (steal && c >= 4 + tr.open()
				&& rng.nextDouble() < Math.min(0.9, 0.4 * (nitsBehind ? 1.5 : 1) * Math.max(0.5, Math.min(1.5, tr.bluff())))) {
				return raise(2.5 * v.bb());
			}
			return passive(v);
		}
		if (bigPreflop(v)) {
			if (topFive(v.hole())) {
				return Action.allIn();
			}
			if (work != null && work.samples() > 0 && work.equity() >= potOdds(v)) {
				return Action.call();
			}
			return passive(v);
		}
		// Raisers read by their PFR: a maniac / frequent raiser gets called wider, a passive or nitty one tighter.
		List<Opp> raisers = v.opps().stream().filter(o -> o.tag().aggressive()).toList();
		boolean looseRaiser = raisers.stream().anyMatch(o -> o.type() == Ranges.OpponentType.MANIAC || (o.stats() != null && o.stats().pfr() > 0.25));
		boolean tightRaiser = !raisers.isEmpty()
			&& raisers.stream().allMatch(o -> o.type() == Ranges.OpponentType.NIT || (o.stats() != null && o.stats().pfr() < Ranges.PASSIVE_PFR));
		if (c >= 11 + Math.max(0, tr.open()) + (tightRaiser ? 2 : 0)) {
			return raise((ip ? 3 : 3.5) * v.currentBet());
		}
		if (!tightRaiser && ip && (wheelAce(v.hole()) || suitedConnector(v.hole())) && rng.nextDouble() < 0.1 * tr.bluff()) {
			return raise(3 * v.currentBet());
		}
		if (c >= (ip ? 8 : 9) + tr.open() - (looseRaiser ? 1 : 0) + (tightRaiser ? 2 : 0)) {
			return callOrCheck(v);
		}
		return passive(v);
	}

	private static Action sharkPost(BotProfile bot, View v, Equity.Result work, BotRng rng) {
		Traits tr = Traits.of(bot.personality());
		double e = work.equity();
		List<Opp> aggressors = v.opps().stream().filter(o -> o.tag().aggressive()).toList();
		boolean station = !v.opps().isEmpty() && v.opps().stream().allMatch(o -> o.type() == Ranges.OpponentType.STATION);
		// Passive players' bets are honest: no minimum defence against a station's or a nit's bet.
		boolean nitBet = v.toCall() > 0 && !aggressors.isEmpty()
			&& aggressors.stream().allMatch(o -> o.type() == Ranges.OpponentType.NIT || o.type() == Ranges.OpponentType.STATION);
		boolean maniacBet = v.toCall() > 0 && aggressors.stream().anyMatch(o -> o.type() == Ranges.OpponentType.MANIAC);
		// Never bluff into a calling station (it calls anyway).
		double bluffs = v.opps().stream().anyMatch(o -> o.type() == Ranges.OpponentType.STATION) ? 0 : tr.bluff();
		double valueAt = station ? 0.58 : 0.7;
		boolean hu = v.opponents() <= 1;
		if (v.toCall() > 0) {
			double b = betFraction(v);
			if (e >= valueAt && v.raises() < 3) {
				return raise(sizeTo(v, 0.6 + rng.nextDouble() * 0.2));
			}
			if (!v.inPosition() && e >= 0.8 && rng.nextDouble() < 0.3) {
				return raise(sizeTo(v, 0.75));
			}
			double need = potOdds(v) + (nitBet ? 0.05 : maniacBet ? -0.05 : 0);
			if (e >= need) {
				return Action.call();
			}
			// Minimum defence: continue with the top 1/(1+b) of the own range.
			if (!nitBet && work.samples() >= MIN_SAMPLES && work.pct() >= b / (1 + b)) {
				return Action.call();
			}
			if (v.street() == Hand.Street.FLOP && v.inPosition() && hu && e >= 0.25 && b <= 0.75) {
				return Action.call();
			}
			return Action.fold();
		}
		if (e >= valueAt) {
			if (v.street() == Hand.Street.RIVER && e >= 0.95 && rng.nextDouble() < 0.2) {
				return raise(sizeTo(v, 1.25));
			}
			// Vs a station: thin value small (it calls any pair), strong value big (it calls top pair+).
			return raise(sizeTo(v, station ? (e >= 0.75 ? 1 : 0.5) : 0.6 + rng.nextDouble() * 0.2));
		}
		if (v.street() == Hand.Street.FLOP && e >= 0.3 && rng.nextDouble() < 0.4 * bluffs) {
			return raise(sizeTo(v, 0.6));
		}
		if (v.street() == Hand.Street.FLOP && v.preflopRaiser() && hu && rng.nextDouble() < 0.5 * bluffs) {
			return raise(sizeTo(v, 0.5));
		}
		if (v.street() == Hand.Street.RIVER && e < 0.3 && drawOuts(v.hole(), java.util.Arrays.copyOf(v.board(), 4)) >= 8) {
			double b = 0.75;
			if (rng.nextDouble() < (b / (1 + b)) * bluffs) {
				return raise(sizeTo(v, b));
			}
		}
		if (v.inPosition() && v.street() != Hand.Street.FLOP && e >= 0.55) {
			return raise(sizeTo(v, 0.6));
		}
		return Action.check();
	}

	// ---- the policy -----------------------------------------------------------------------------------------

	/** Opponent ranges for a bot's equity (NORMAL: plain tags; HARD: limp exclusion + VPIP scaling). */
	public static List<Ranges.Spec> rangesFor(BotDifficulty level, View v) {
		boolean hard = level == BotDifficulty.HARD;
		List<Ranges.Spec> out = new ArrayList<>();
		for (Opp o : v.opps()) {
			boolean modelled = hard && o.human() && o.stats() != null;
			out.add(Ranges.rangeOf(o.tag(), hard, modelled ? o.stats().vpip() : -1, modelled && o.stats().pfr() < Ranges.PASSIVE_PFR));
		}
		return out;
	}

	/** Monte-Carlo samples a level needs for this decision (0 = no simulation). */
	public static int samplesFor(BotDifficulty level, View v, Config cfg) {
		if (level == BotDifficulty.EASY) {
			return 0;
		}
		if (v.preflop()) {
			return level == BotDifficulty.HARD && bigPreflop(v) ? cfg.sharkSamples() : 0;
		}
		return level == BotDifficulty.NORMAL ? cfg.regularSamples() : cfg.sharkSamples();
	}

	/** The heavy job for a decision with {@code samples} samples (null = none). */
	public static Equity.Work equityWork(BotProfile bot, View v, BotRng rng, int samples) {
		if (samples <= 0) {
			return null;
		}
		boolean hard = bot.level() == BotDifficulty.HARD;
		Ranges.Spec own = hard && !v.preflop() ? Ranges.rangeOf(v.ownTag(), true) : null;
		return new Equity.Work(new Equity.Input(v.hole(), v.board(), rangesFor(bot.level(), v), own, samples), rng);
	}

	@Override
	public BotWork work(BotProfile bot, View v, BotRng rng) {
		return equityWork(bot, v, rng, samplesFor(bot.level(), v, cfg));
	}

	private static Equity.Result asResult(Object work) {
		if (work instanceof Equity.Result r) {
			return r;
		}
		if (work instanceof Equity.Work w) {
			return w.result();
		}
		return null;
	}

	/** The level's decision before {@link #legalize} (mistakes included). */
	@Override
	public Action decide(BotProfile bot, View v, Object work, BotRng rng) {
		Equity.Result res = asResult(work);
		Action a;
		if (bot.level() == BotDifficulty.EASY) {
			Fish f = fish(bot, v, rng);
			a = f.action();
			// 15 % of decisions take the next-lower or next-higher action (never the premium call / shove).
			if (!f.locked() && rng.nextDouble() < 0.15) {
				a = rng.nextDouble() < 0.5 ? lower(v, a) : higher(v, a);
			}
			return a;
		}
		boolean hardRule = bot.level() == BotDifficulty.HARD && (v.preflop() || (res != null && res.samples() >= MIN_SAMPLES));
		if (v.preflop()) {
			a = hardRule ? sharkPre(bot, v, res, rng) : regularPre(bot, v);
		} else {
			Equity.Result e = res != null && res.samples() > 0 ? res : new Equity.Result(roughEquity(v), 0.5, 0);
			a = hardRule ? sharkPost(bot, v, e, rng) : regularPost(bot, v, e.equity(), rng);
		}
		Traits tr = Traits.of(bot.personality());
		if (bot.level() == BotDifficulty.NORMAL) {
			boolean strong = v.preflop() ? Equity.chen(v.hole()) >= 11 : (res != null ? res.equity() : roughEquity(v)) >= 0.65;
			a = aggress(v, a, tr.aggression(), rng, strong);
			if (rng.nextDouble() < 0.08) {
				a = lower(v, a);
			}
		} else if (bot.level() == BotDifficulty.HARD) {
			a = aggress(v, a, tr.aggression(), rng, (res != null ? res.equity() : 0) >= 0.55);
		}
		return a;
	}

	@Override
	public Action legalize(View v, Action a) {
		return legalizeAction(v, a);
	}

	/** Decides synchronously (tests, drawn-outcome play-out): runs the work (≤ {@code maxSamples}) then decide + legalize. */
	public Action decideNow(BotProfile bot, View v, BotRng rng, int maxSamples) {
		int samples = Math.min(maxSamples, samplesFor(bot.level(), v, cfg));
		Equity.Work w = equityWork(bot, v, rng, samples);
		return act(bot, v, w == null ? null : w.run(), rng);
	}

	public Action decideNow(BotProfile bot, View v, BotRng rng) {
		return decideNow(bot, v, rng, Integer.MAX_VALUE);
	}

	// ---- levels, stakes and mixes ----------------------------------------------------------------------------

	/** EASY allowed at this stake ({@code bots.poker.easyMaxStake}, MICRO…HIGH). Unknown stake → like LOW. */
	public static boolean easyAllowed(StakeLevel stake, String easyMaxStake) {
		int s = stake == null ? -1 : stake.ordinal();
		StakeLevel m = StakeLevel.byId(easyMaxStake == null ? "" : easyMaxStake.toLowerCase(Locale.ROOT));
		if (s < 0 || m == null) {
			return s <= StakeLevel.LOW.ordinal();
		}
		return s <= m.ordinal();
	}

	/** MIXED weights with Easy forced to 0 above the stake gate. */
	public static int[] gatedMix(int[] mix, StakeLevel stake, String easyMaxStake) {
		int[] w = new int[3];
		for (int i = 0; i < 3; i++) {
			w[i] = mix != null && i < mix.length ? Math.max(0, mix[i]) : 0;
		}
		if (!easyAllowed(stake, easyMaxStake)) {
			w[0] = 0;
		}
		return w;
	}

	/** The level a bot plays at this stake (an EASY bot above the gate plays NORMAL). */
	public static BotDifficulty gatedLevel(BotDifficulty level, StakeLevel stake, String easyMaxStake) {
		return level == BotDifficulty.EASY && !easyAllowed(stake, easyMaxStake) ? BotDifficulty.NORMAL : level;
	}
}

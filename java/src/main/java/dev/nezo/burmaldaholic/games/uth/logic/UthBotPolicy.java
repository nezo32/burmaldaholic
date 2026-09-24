package dev.nezo.burmaldaholic.games.uth.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotWork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Ultimate Texas Hold'em bots (BOTS.md §4.5; pvp-bots.md §4.6). Pure. Twin of Bedrock
 * {@code games/uth/logic/bots.ts}.
 *
 * <p>UTH bots are ATMOSPHERE bots: their Ante / Blind / Trips / Play bets are virtual (shown, never debited,
 * paid, reserved or reported). The level only changes what the table sees them do:
 *
 * <pre>
 * Street   EASY                                             NORMAL / HARD (strategy R, GAME_DESIGN §21.3)
 * Preflop  ×4 any pair or any Ace; 15 % of the rest ×3       R (never ×3)
 * Flop     ×2 any pair (board pairs too) or a flush draw     R
 * River    ×1 any pair or better; else fold 70 % / ×1 30 %   R: exact mean over the dealer's 990 hole pairs
 *                                                            ({@link RiverWork}, a {@link BotWork} job)
 * Trips    50 % of rounds, 1 × Ante                          never
 * </pre>
 *
 * The {@link View} is what a human in that seat sees (own hole cards, the visible board, the legal
 * options). Every random choice uses the BOT rng handed in by the driver; NORMAL / HARD draw nothing.
 */
public final class UthBotPolicy implements BotPolicy<UthBotPolicy.View, Decision> {
	public static final UthBotPolicy INSTANCE = new UthBotPolicy();

	/** Share of the other preflop hands EASY bets ×3 ("scared money"). */
	public static final double EASY_SCARED_3X = 0.15;
	/** Share of the river hands without a pair EASY still bets ×1. */
	public static final double EASY_RIVER_CALL = 0.3;
	/** Share of rounds EASY also bets Trips (1 × Ante). */
	public static final double EASY_TRIPS = 0.5;
	/** Fewer dealer hands than this at the deadline → the cheap fallback rule decides (BOTS.md §4.3). */
	public static final int RIVER_MIN_SAMPLES = 50;

	/** A legal option of the seat now; {@code affordable} = the seat's chips cover it (bots: always). */
	public record Option(Decision decision, long amount, boolean affordable) {}

	/**
	 * What a human in the seat sees.
	 *
	 * @param board the VISIBLE board (0, 3 or 5 cards)
	 */
	public record View(UthRound.Street street, int[] hole, int[] board, long ante, long trips, List<Option> options, Paytables pays) {
		public View {
			Objects.requireNonNull(street);
			hole = hole.clone();
			board = board.clone();
			options = List.copyOf(options);
			Objects.requireNonNull(pays);
		}

		/** The seat's view of a round (never the dealer's cards or unrevealed board cards). */
		public static View of(UthRound round, UthRound.Seat seat, long balance, boolean allow3x, Paytables pays) {
			List<Option> opts = new ArrayList<>();
			for (Decision d : round.legal(seat, allow3x)) {
				long amount = (long) d.multiple() * seat.ante;
				opts.add(new Option(d, amount, !d.isBet() || balance >= amount));
			}
			return new View(round.street(), seat.hole, round.visibleBoardCards(), seat.ante, seat.trips, opts, pays);
		}

		public boolean offers(Decision d) {
			for (Option o : options) {
				if (o.decision() == d && o.affordable()) {
					return true;
				}
			}
			return false;
		}
	}

	private UthBotPolicy() {}

	// ---- EASY rules ----------------------------------------------------------------------------------

	/** EASY preflop ×4: any pair or any Ace. */
	public static boolean easyPreflopBet4(int[] hole) {
		int a = UthCards.rank(hole[0]);
		int b = UthCards.rank(hole[1]);
		return a == b || a == 14 || b == 14;
	}

	/** Four (or more) cards of one suit among hole + flop, at least one of them a hole card. */
	public static boolean fourToFlush(int[] hole, int[] flop) {
		for (int s = 0; s < 4; s++) {
			int n = 0;
			boolean mine = false;
			for (int c : hole) {
				if (UthCards.suit(c) == s) {
					n++;
					mine = true;
				}
			}
			for (int c : flop) {
				if (UthCards.suit(c) == s) {
					n++;
				}
			}
			if (n >= 4 && mine) {
				return true;
			}
		}
		return false;
	}

	/** EASY flop ×2: any pair on the five known cards (board-only pairs too — a visible mistake) or a flush draw. */
	public static boolean easyFlopBet2(int[] hole, int[] flop) {
		return UthCards.category(UthCards.evaluate(concat(hole, flop))) >= UthCards.PAIR || fourToFlush(hole, flop);
	}

	/** EASY river ×1 without a coin flip: any pair or better on the seven cards. */
	public static boolean easyRiverBet1(int[] hole, int[] board) {
		return UthCards.category(UthCards.evaluate(concat(hole, board))) >= UthCards.PAIR;
	}

	// ---- the river enumeration as BotWork -----------------------------------------------------------

	/**
	 * Partial / complete result of the river enumeration.
	 *
	 * @param ev      mean result of Bet ×1 in Antes over the dealer hands seen so far
	 * @param samples dealer hands evaluated
	 * @param total   all dealer hole pairs (990)
	 */
	public record RiverEstimate(double ev, int samples, int total) {}

	/**
	 * Exact river decision of strategy R: Bet ×1 iff the mean over the dealer's 990 possible hole pairs beats
	 * the fold (−2 Antes). One unit = one dealer-hand evaluation. The pairs are visited with a stride coprime
	 * to 990, so a deadline-cut partial result is an evenly spread sample, not the lowest cards first.
	 */
	public static final class RiverWork implements BotWork {
		private final int[] a;
		private final int[] b;
		private final int[] seven = new int[7];
		private final int pv;
		private final double blindPay;
		private final int stride;
		private int k;
		private double sum;

		public RiverWork(int[] hole, int[] board, Paytables pays) {
			if (board.length != 5) {
				throw new IllegalArgumentException("the river needs the whole board");
			}
			boolean[] used = new boolean[UthCards.DECK_SIZE];
			for (int c : hole) {
				used[c] = true;
			}
			for (int c : board) {
				used[c] = true;
			}
			int[] unseen = new int[UthCards.DECK_SIZE];
			int u = 0;
			for (int c = 0; c < UthCards.DECK_SIZE; c++) {
				if (!used[c]) {
					unseen[u++] = c;
				}
			}
			int n = u * (u - 1) / 2;
			a = new int[n];
			b = new int[n];
			int p = 0;
			for (int i = 0; i < u; i++) {
				for (int j = i + 1; j < u; j++) {
					a[p] = unseen[i];
					b[p] = unseen[j];
					p++;
				}
			}
			System.arraycopy(board, 0, seven, 2, 5);
			pv = Settlement.value(hole, board);
			blindPay = pays.blindPay(PayHand.of(pv));
			// golden-ratio stride (coprime to n): consecutive samples land far apart
			int s = Math.max(1, (int) Math.round(n * 0.618));
			while (n > 1 && gcd(s, n) != 1) {
				s++;
			}
			stride = s;
		}

		public int total() {
			return a.length;
		}

		@Override
		public int step(int budget) {
			int n = a.length;
			int used = 0;
			while (used < budget && k < n) {
				int idx = (int) ((long) k * stride % n);
				seven[0] = a[idx];
				seven[1] = b[idx];
				int dv = UthCards.evaluate(seven, 7);
				sum += betResult(pv, dv, blindPay);
				k++;
				used++;
			}
			return used;
		}

		@Override
		public boolean done() {
			return k >= a.length;
		}

		@Override
		public @Nullable RiverEstimate result() {
			return k > 0 ? new RiverEstimate(sum / k, k, a.length) : null;
		}

		@Override
		public int progress() {
			return k;
		}

		/** Result in Antes of Bet ×1 (Ante + Blind + Play) against one dealer hand. */
		private static double betResult(int pv, int dv, double blindPay) {
			boolean q = Settlement.qualifies(dv);
			if (pv > dv) {
				return 1 + (q ? 1 : 0) + blindPay;
			}
			if (pv < dv) {
				return -(2 + (q ? 1 : 0));
			}
			return 0;
		}
	}

	private static int gcd(int x, int y) {
		return y == 0 ? x : gcd(y, x % y);
	}

	/**
	 * Deadline fallback when the enumeration did not get far enough: Bet ×1 with a pair or better whose
	 * category the hole cards improve (a board-only hand does not play for the seat).
	 */
	public static boolean riverFallbackBet1(int[] hole, int[] board) {
		int cat = UthCards.category(Settlement.value(hole, board));
		return cat >= UthCards.PAIR && cat > UthCards.category(UthCards.evaluate(board));
	}

	/** Strategy R's river choice from an estimate (complete or partial, ≥ {@link #RIVER_MIN_SAMPLES}), else the fallback rule. */
	public static boolean riverChoice(int[] hole, int[] board, @Nullable Object work) {
		if (work instanceof RiverEstimate e && e.samples() >= RIVER_MIN_SAMPLES) {
			return e.ev() > -2.0;
		}
		return riverFallbackBet1(hole, board);
	}

	// ---- the policy ----------------------------------------------------------------------------------

	/** No-risk option of a street (the §21.4 default without auto-play). */
	public static Decision safeDefault(View view) {
		return view.street() == UthRound.Street.RIVER ? Decision.FOLD : Decision.CHECK;
	}

	@Override
	public Decision decide(BotProfile bot, View view, @Nullable Object work, BotRng rng) {
		return bot.level() == BotDifficulty.EASY ? easyDecide(view, rng) : referenceDecide(view, work);
	}

	private static Decision easyDecide(View v, BotRng rng) {
		return switch (v.street()) {
			case PREFLOP -> easyPreflopBet4(v.hole()) ? Decision.BET_4X : rng.chance(EASY_SCARED_3X) ? Decision.BET_3X : Decision.CHECK;
			case FLOP -> easyFlopBet2(v.hole(), v.board()) ? Decision.BET_2X : Decision.CHECK;
			case RIVER -> easyRiverBet1(v.hole(), v.board()) || rng.chance(EASY_RIVER_CALL) ? Decision.BET_1X : Decision.FOLD;
			case SHOWDOWN -> Decision.CHECK;
		};
	}

	private static Decision referenceDecide(View v, @Nullable Object work) {
		return switch (v.street()) {
			case PREFLOP -> ReferenceStrategy.preflopBet(v.hole()) ? Decision.BET_4X : Decision.CHECK;
			case FLOP -> ReferenceStrategy.flopBet(v.hole(), v.board()) ? Decision.BET_2X : Decision.CHECK;
			case RIVER -> riverChoice(v.hole(), v.board(), work) ? Decision.BET_1X : Decision.FOLD;
			case SHOWDOWN -> Decision.CHECK;
		};
	}

	@Override
	public Decision legalize(View view, Decision action) {
		return action != null && view.offers(action) ? action : safeDefault(view);
	}

	/** NORMAL / HARD rivers: the 990-hand enumeration. EASY and the other streets need no work. */
	@Override
	public @Nullable BotWork work(BotProfile bot, View view, BotRng rng) {
		return bot.level() != BotDifficulty.EASY && view.street() == UthRound.Street.RIVER && view.board().length == 5
			? new RiverWork(view.hole(), view.board(), view.pays()) : null;
	}

	/**
	 * The same bot as the module's synchronous {@link SeatDecider} (tests, simulations): the river work, if
	 * any, runs to completion inline.
	 */
	public static SeatDecider decider(BotProfile bot, BotRng rng, Paytables pays) {
		return (round, seat, ctx) -> {
			View v = View.of(round, seat, ctx.balance(), ctx.allow3x(), pays);
			BotWork w = INSTANCE.work(bot, v, rng);
			if (w != null) {
				while (!w.done()) {
					w.step(1000);
				}
			}
			return INSTANCE.act(bot, v, w == null ? null : w.result(), rng);
		};
	}

	// ---- published cost per level (BOTS.md §4.5 …bots.uth_edge, §12.3) ------------------------------------

	/**
	 * What each level costs a bot per round, as a share of the Ante (virtual chips): NORMAL = strategy R
	 * (GAME_DESIGN §21.3), HARD = the published near-optimal figure, EASY measured by {@link UthBotSim}
	 * (2 × 10⁵ boards: 19.45 % ± 0.23 %; mostly the 15 % "scared" ×3 bets plus the 50 % Trips habit).
	 */
	public static double edge(BotDifficulty level) {
		return switch (level) {
			case EASY -> 0.194;
			case HARD -> 0.0219;
			default -> 0.0227;
		};
	}

	/** "2.27 %" / "19.4 %" (two decimals below 3 %, else one, as the design tables write them). */
	public static String edgePercent(BotDifficulty level) {
		double p = edge(level) * 100;
		return String.format(Locale.ROOT, p >= 3 ? "%.1f %%" : "%.2f %%", p);
	}

	static int[] concat(int[] x, int[] y) {
		int[] out = new int[x.length + y.length];
		System.arraycopy(x, 0, out, 0, x.length);
		System.arraycopy(y, 0, out, x.length, y.length);
		return out;
	}
}

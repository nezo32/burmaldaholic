package dev.nezo.burmaldaholic.games.extras.pvp.coin;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;

/**
 * Double-or-nothing chain arithmetic of Coin Flip Duel (PVP.md §4.2, §4.4; bots BOTS.md §4.8). Pure.
 *
 * <p>Every flip is its own match (the engine links them with {@code chainOf} / {@code link}); this class only
 * says what the next link costs and whether it may be offered. Let D be the chain loser's total deficit before
 * rake (after the first flip D = S). The next flip's stake is D for each player; if the chain loser loses again
 * D doubles, if they win the chain is ALL SQUARE and ends. After {@code pvp.coin.maxDoubles} doubles (so at most
 * {@code maxDoubles + 1} flips) no further offer is made.
 *
 * <p>Decision options ({@code PvpService.decide(player, decision, option)} and {@code botDecide}):
 * <ul>
 *   <li>{@link #DON_OFFER} (chain loser): {@link #WALK_AWAY} 0, {@link #DON_HEADS} 1, {@link #DON_TAILS} 2 —
 *       any non-zero value means "double or nothing"; 2 calls Tails, everything else Heads;</li>
 *   <li>{@link #LET_IT_RIDE} (chain winner): {@link #RIDE} 1 = let it ride, {@link #TAKE} 0 = take the money;</li>
 *   <li>{@link #SIDE} (challenger's side, a bot challenger / bot target's cosmetic pick): 0 heads, 1 tails.</li>
 * </ul>
 * Timeouts are the safe option: the loser walks away, the winner takes the money (§4.2, test K6).
 */
public final class CoinChain {
	public static final String DON_OFFER = "coin.don_offer";
	public static final String LET_IT_RIDE = "coin.let_it_ride";
	public static final String SIDE = "coin.side";

	public static final long WALK_AWAY = 0;
	public static final long DON_HEADS = 1;
	public static final long DON_TAILS = 2;
	public static final long TAKE = 0;
	public static final long RIDE = 1;

	public static final String DON_LIMIT = "gui.burmaldaholic.pvp.coin.don_limit";
	/** Arg %1$s = the stake each player needs (chips). */
	public static final String DON_UNAFFORDABLE = "gui.burmaldaholic.pvp.coin.don_unaffordable";

	private CoinChain() {}

	/**
	 * State of a chain after a flip.
	 *
	 * @param link      flips played so far (1-based link number of the flip just settled)
	 * @param loser     chain loser as a player key (the engine's own id: UUID string / bot key); null when all square
	 * @param deficit   D: the chain loser's deficit before rake (0 when all square)
	 * @param allSquare the chain loser just won a double-or-nothing flip: both back to 0 before rake; the chain ends
	 */
	public record State(int link, String loser, long deficit, boolean allSquare) {
		/** The next flip's stake for each player (= D). */
		public long nextStake() {
			return deficit;
		}
	}

	/** After the first flip (stake {@code s}): the loser is down {@code s}. */
	public static State first(long stake, String loser) {
		return new State(1, loser, stake, false);
	}

	/**
	 * After a linked flip ({@code prev.link + 1}) played at stake {@code prev.deficit}: the chain loser lost again
	 * → D doubles; won → all square.
	 */
	public static State next(State prev, boolean chainLoserWon) {
		if (prev.allSquare()) {
			throw new IllegalStateException("chain already ended");
		}
		if (chainLoserWon) {
			return new State(prev.link() + 1, null, 0, true);
		}
		return new State(prev.link() + 1, prev.loser(), Math.multiplyExact(prev.deficit(), 2L), false);
	}

	/** The stake of flip {@code link} (1-based) when the same player keeps losing: S, S, 2S, 4S, 8S, … */
	public static long stakeOfLink(long s, int link) {
		if (link <= 1) {
			return s;
		}
		return Math.multiplyExact(s, 1L << Math.min(62, link - 2));
	}

	/** May another link follow flip {@code link} at all (the {@code pvp.coin.maxDoubles} limit)? */
	public static boolean withinLimit(int link, int maxDoubles) {
		return link <= maxDoubles;
	}

	/**
	 * Why the Double-or-nothing button is replaced by a disabled line (PVP.md §4.2, test K5), or null when it may
	 * be offered. Tier max = each player's VIP max bet (house bots: {@link Long#MAX_VALUE}); balance = what each
	 * can pay (a bot: what its purse can fund).
	 */
	public static String offerBlock(State state, int maxDoubles, long tierMaxA, long tierMaxB, long balanceA, long balanceB) {
		if (state.allSquare() || !withinLimit(state.link(), maxDoubles)) {
			return DON_LIMIT;
		}
		long d = state.deficit();
		if (d > tierMaxA || d > tierMaxB) {
			return DON_LIMIT;
		}
		if (d > balanceA || d > balanceB) {
			return DON_UNAFFORDABLE;
		}
		return null;
	}

	/** Chain loser's option → does it continue, and which side (true = heads) is called. */
	public static boolean continues(long donOption) {
		return donOption != WALK_AWAY;
	}

	public static boolean calledHeads(long donOption) {
		return donOption != DON_TAILS;
	}

	// ---- bots (BOTS.md §4.8: "Style" — no choice changes EV, Lemma 3) --------------------------------------

	/** Side call: random for every level. Returns 0 heads, 1 tails. */
	public static long botSide(BotRng rng) {
		return rng.nextInt(2);
	}

	/**
	 * Chain loser: Wild (EASY) always doubles (the engine only asks while the offer is allowed), Steady (NORMAL)
	 * 50 %, Cool-headed (HARD) walks away. The side is random.
	 */
	public static long botDoubleOrNothing(BotDifficulty level, BotRng rng) {
		boolean go = switch (effective(level)) {
			case EASY -> true;
			case HARD -> false;
			default -> rng.chance(0.5);
		};
		if (!go) {
			return WALK_AWAY;
		}
		return rng.nextInt(2) == 0 ? DON_HEADS : DON_TAILS;
	}

	/** Chain winner: Wild always lets it ride, Steady 50 %, Cool-headed takes the money. */
	public static long botLetItRide(BotDifficulty level, BotRng rng) {
		return switch (effective(level)) {
			case EASY -> RIDE;
			case HARD -> TAKE;
			default -> rng.chance(0.5) ? RIDE : TAKE;
		};
	}

	/** A profile never holds MIXED (BotDifficulty.pick resolves it); if one arrives, play Steady. */
	static BotDifficulty effective(BotDifficulty level) {
		return level == null || level.isMixed() ? BotDifficulty.NORMAL : level;
	}
}

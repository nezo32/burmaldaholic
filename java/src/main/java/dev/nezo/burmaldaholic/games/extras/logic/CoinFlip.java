package dev.nezo.burmaldaholic.games.extras.logic;

import dev.nezo.burmaldaholic.core.rng.CasinoRng;

/**
 * Coin Flip (GAME_DESIGN.md §11.1). The player picks a side; a win pays {@code payout}:1 (floor),
 * i.e. 1.96× total at the default 0.96: RTP = ½ × (1 + payout) = 98 %. The Soul Wager (§4.4) pays 1:1.
 * Pure Java.
 */
public final class CoinFlip {
	public static final double DEFAULT_PAYOUT = 0.96;

	private CoinFlip() {}

	public enum Side {
		HEADS("heads"), TAILS("tails");

		private final String id;

		Side(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public Side other() {
			return this == HEADS ? TAILS : HEADS;
		}

		/** Lang key {@code gui.burmaldaholic.extras.coin.<id>}. */
		public String key() {
			return "gui.burmaldaholic.extras.coin." + id;
		}

		public static Side parse(String s) {
			for (Side side : values()) {
				if (side.id.equals(s)) {
					return side;
				}
			}
			return null;
		}
	}

	/** @param landed the side shown by the animation (follows from the win) */
	public record Result(Side pick, Side landed, boolean win) {}

	/**
	 * One flip. The win is a player-favourable event with base chance ½ ({@link CasinoRng#chance}, so VIP /
	 * chaos / Last Chance modifiers apply); the streak re-draw is applied by the caller via {@code OddsService.play}.
	 */
	public static Result flip(CasinoRng rng, Side pick) {
		boolean win = rng.chance(0.5);
		return new Result(pick, win ? pick : pick.other(), win);
	}

	/** Chip winnings on top of the stake for a win (0 on loss). */
	public static long winnings(long stake, boolean win, double payout) {
		return win ? Payouts.floorPay(stake, payout) : 0;
	}

	/** Total return (stake included) for a chip / pawn stake of value {@code stake}. */
	public static long totalReturn(long stake, boolean win, double payout) {
		return win ? stake + winnings(stake, true, payout) : 0;
	}

	/** Total return of a Soul Wager worth V (1:1). */
	public static long soulReturn(long value, boolean win) {
		return win ? 2 * value : 0;
	}

	/** RTP before flooring. */
	public static double rtp(double payout) {
		return 0.5 * (1 + payout);
	}
}

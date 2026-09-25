package dev.nezo.burmaldaholic.games.roulette.logic;

import dev.nezo.burmaldaholic.games.roulette.logic.Bets.Bet;
import java.util.List;
import java.util.Optional;

/**
 * Roulette limits (GAME_DESIGN.md §9 "Limits", CONFIG.md {@code roulette.*}):
 * <ul>
 *   <li>every individual bet ≥ {@code minBet};</li>
 *   <li>each inside spot (after merging) ≤ {@code insideMax} (tier max × insideMaxFraction);</li>
 *   <li>total per spin ≤ {@code totalMax} (tier max; High-Roller: tier max × multiplier);</li>
 *   <li>High-Roller: total per spin ≥ {@code minTotal} before it can spin.</li>
 * </ul>
 */
public record SlipLimits(long minBet, long insideMax, long totalMax, long minTotal) {
	public enum Code {
		INVALID_AMOUNT, INVALID_POSITION, BET_TOO_LOW, INSIDE_MAX, TOTAL_MAX, MIN_TOTAL
	}

	/** A violation with the limit value to show. */
	public record Violation(Code code, long value) {}

	/**
	 * @param tierMax            the player's max per spin before the High-Roller multiplier (VIP tier max, owner max)
	 * @param highRollerMultiplier 1 for normal tables
	 */
	public static SlipLimits of(long minBet, long tierMax, double insideMaxFraction, double highRollerMultiplier, long minTotal) {
		long totalMax = Math.max(0, (long) Math.floor(tierMax * Math.max(1.0, highRollerMultiplier)));
		// Same as Bedrock: the inside max is a share of the table's max per spin (incl. the High-Roller
		// multiplier), but never below the minimum bet so a single chip is always possible.
		long insideMax = Math.max(Math.max(1, minBet), (long) Math.floor(totalMax * insideMaxFraction));
		return new SlipLimits(Math.max(1, minBet), Math.min(insideMax, totalMax), totalMax, Math.max(0, minTotal));
	}

	/** Validates adding {@code bets} (one bet, or a whole Rebet) to {@code slip}. */
	public Optional<Violation> checkAdd(List<Bet> slip, List<Bet> bets) {
		List<Bet> next = slip;
		for (Bet b : bets) {
			if (b.amount() <= 0) {
				return Optional.of(new Violation(Code.INVALID_AMOUNT, 0));
			}
			if (!b.spot().isValid()) {
				return Optional.of(new Violation(Code.INVALID_POSITION, 0));
			}
			if (b.amount() < minBet) {
				return Optional.of(new Violation(Code.BET_TOO_LOW, minBet));
			}
			next = Bets.merge(next, b);
			if (b.type().inside() && Bets.amountOn(next, b.spot()) > insideMax) {
				return Optional.of(new Violation(Code.INSIDE_MAX, insideMax));
			}
		}
		if (Bets.totalStaked(next) > totalMax) {
			return Optional.of(new Violation(Code.TOTAL_MAX, totalMax));
		}
		return Optional.empty();
	}

	/** Can this slip be spun (High-Roller minimum per spin)? */
	public Optional<Violation> checkSpin(List<Bet> slip) {
		long total = Bets.totalStaked(slip);
		if (total > 0 && total < minTotal) {
			return Optional.of(new Violation(Code.MIN_TOTAL, minTotal));
		}
		return Optional.empty();
	}

	/** Largest amount that could still be added on a spot. */
	public long maxAddable(List<Bet> slip, Spot spot) {
		long room = totalMax - Bets.totalStaked(slip);
		if (!spot.type().inside()) {
			return Math.max(0, room);
		}
		return Math.max(0, Math.min(room, insideMax - Bets.amountOn(slip, spot)));
	}
}

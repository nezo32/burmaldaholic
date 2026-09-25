package dev.nezo.burmaldaholic.games.baccarat.logic;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * A bettor's slip for one coup ({@code BetKind → amount}) and the §20.4 limits. PURE.
 */
public final class Slips {
	private Slips() {}

	public static long total(Map<BetKind, Long> slip) {
		long sum = 0;
		for (long v : slip.values()) {
			sum += v;
		}
		return sum;
	}

	public static EnumMap<BetKind, Long> copy(Map<BetKind, Long> slip) {
		EnumMap<BetKind, Long> out = new EnumMap<>(BetKind.class);
		slip.forEach((k, v) -> {
			if (v > 0) {
				out.put(k, v);
			}
		});
		return out;
	}

	/** Why a bet is refused; {@code value} is the limit to show. */
	public enum Code {
		INVALID_AMOUNT, BET_TOO_LOW, BANKER_STEP, SIDE_MAX, TOTAL_MAX, MIN_TOTAL, PAIRS_OFF
	}

	public record Violation(Code code, long value) {}

	/**
	 * Limits of one bettor at one table (§20.4).
	 *
	 * @param minBet   every individual bet ≥ this
	 * @param totalMax total per coup ≤ this (min(table max, tier max); High Roller: 2 × tier max)
	 * @param sideMax  Tie and each Pair ≤ this ({@code floor(max × sideMaxFraction)})
	 * @param step     Banker step k; Banker bets are multiples of k and ≥ max(k, minBet)
	 * @param minTotal High Roller: total per coup ≥ this (0 = none)
	 * @param pairs    pair bets offered
	 */
	public record Limits(long minBet, long totalMax, long sideMax, long step, long minTotal, boolean pairs) {
		public static Limits of(long minBet, long totalMax, double sideMaxFraction, long step, long minTotal, boolean pairs) {
			long side = (long) Math.floor(totalMax * sideMaxFraction + 1e-9);
			return new Limits(Math.max(1, minBet), Math.max(0, totalMax), Math.max(0, side), Math.max(1, step), Math.max(0, minTotal), pairs);
		}

		public long bankerMin() {
			long m = Math.max(step, minBet);
			long rem = m % step;
			return rem == 0 ? m : m + step - rem;
		}

		/** Banker amount snapped DOWN to the step (UI.md §14 / §20.1). */
		public long snapBanker(long amount) {
			return amount <= 0 ? 0 : amount - amount % step;
		}

		/** Checks adding {@code amount} to box {@code kind} of {@code current}; empty = allowed. */
		public Optional<Violation> checkAdd(Map<BetKind, Long> current, BetKind kind, long amount) {
			if (amount <= 0) {
				return Optional.of(new Violation(Code.INVALID_AMOUNT, 0));
			}
			if (kind.pair() && !pairs) {
				return Optional.of(new Violation(Code.PAIRS_OFF, 0));
			}
			long after = current.getOrDefault(kind, 0L) + amount;
			if (kind == BetKind.BANKER) {
				if (after % step != 0) {
					return Optional.of(new Violation(Code.BANKER_STEP, step));
				}
				if (after < bankerMin()) {
					return Optional.of(new Violation(Code.BET_TOO_LOW, bankerMin()));
				}
			} else if (after < minBet) {
				return Optional.of(new Violation(Code.BET_TOO_LOW, minBet));
			}
			if (kind.side() && after > sideMax) {
				return Optional.of(new Violation(Code.SIDE_MAX, sideMax));
			}
			if (total(current) + amount > totalMax) {
				return Optional.of(new Violation(Code.TOTAL_MAX, totalMax));
			}
			return Optional.empty();
		}

		/** Checks a complete slip before the coup (every box, total max, High-Roller minimum). */
		public Optional<Violation> checkSlip(Map<BetKind, Long> slip) {
			EnumMap<BetKind, Long> acc = new EnumMap<>(BetKind.class);
			for (Map.Entry<BetKind, Long> e : slip.entrySet()) {
				Optional<Violation> v = checkAdd(acc, e.getKey(), e.getValue());
				if (v.isPresent()) {
					return v;
				}
				acc.put(e.getKey(), e.getValue());
			}
			if (minTotal > 0 && total(slip) < minTotal) {
				return Optional.of(new Violation(Code.MIN_TOTAL, minTotal));
			}
			return Optional.empty();
		}

		/**
		 * Rebet (§20.5): the last coup's slip snapped to the current limits — Banker down to the step,
		 * side boxes to the side max, then everything that still fits the total max (in box order).
		 */
		public EnumMap<BetKind, Long> fit(Map<BetKind, Long> last) {
			EnumMap<BetKind, Long> out = new EnumMap<>(BetKind.class);
			long room = totalMax;
			for (BetKind k : BetKind.values()) {
				long v = last.getOrDefault(k, 0L);
				if (v <= 0 || (k.pair() && !pairs)) {
					continue;
				}
				if (k.side()) {
					v = Math.min(v, sideMax);
				}
				v = Math.min(v, room);
				if (k == BetKind.BANKER) {
					v = snapBanker(v);
					if (v < bankerMin()) {
						continue;
					}
				} else if (v < minBet) {
					continue;
				}
				out.put(k, v);
				room -= v;
			}
			return out;
		}
	}
}

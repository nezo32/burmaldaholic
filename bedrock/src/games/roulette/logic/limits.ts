/**
 * Roulette bet limits (GAME_DESIGN §9 "Limits", CONFIG.md roulette.*). PURE.
 *  - every individual bet ≥ roulette.minBet
 *  - each inside bet (after merging with a bet on the same spot) ≤ tier max × insideMaxFraction
 *  - total per spin ≤ tier max (High-Roller table: tier max × highRollerMaxMultiplier)
 *  - High-Roller table: total per spin ≥ highRollerMinTotal before the spin
 */
import { type Bet, isInside, isValidSpot, mergeBet, spotKey, totalStaked } from './bets';

export interface SlipLimits {
  minBet: number;
  /** max amount on one inside spot */
  insideMax: number;
  /** max total per spin */
  totalMax: number;
  /** min total per spin (High-Roller), 0 = none */
  minTotal: number;
}

export interface LimitInputs {
  /** VIP tier max bet (already multiplied for High-Roller tables) */
  tierMax: number;
  minBet: number;
  insideMaxFraction: number;
  minTotal?: number;
}

export function slipLimits(i: LimitInputs): SlipLimits {
  const totalMax = Math.max(0, Math.floor(i.tierMax));
  return {
    minBet: Math.max(1, Math.floor(i.minBet)),
    insideMax: Math.floor(totalMax * i.insideMaxFraction),
    totalMax,
    minTotal: Math.max(0, Math.floor(i.minTotal ?? 0)),
  };
}

export type SlipError =
  | { code: 'invalid_amount' }
  | { code: 'invalid_position' }
  | { code: 'bet_too_low'; min: number }
  | { code: 'inside_max'; max: number }
  | { code: 'total_max'; max: number }
  | { code: 'min_total'; min: number };

/** Validate adding `bets` (one or several, e.g. a Rebet) to the current slip. */
export function checkAdd(slip: readonly Bet[], bets: readonly Bet[], l: SlipLimits): SlipError | undefined {
  let next = slip.slice();
  for (const b of bets) {
    if (!Number.isSafeInteger(b.amount) || b.amount <= 0) return { code: 'invalid_amount' };
    if (!isValidSpot(b)) return { code: 'invalid_position' };
    if (b.amount < l.minBet) return { code: 'bet_too_low', min: l.minBet };
    next = mergeBet(next, b);
    if (isInside(b.type)) {
      const merged = next.find((x) => spotKey(x) === spotKey(b))!;
      if (merged.amount > l.insideMax) return { code: 'inside_max', max: l.insideMax };
    }
  }
  if (totalStaked(next) > l.totalMax) return { code: 'total_max', max: l.totalMax };
  return undefined;
}

/** Can this slip be spun (High-Roller minimum total)? */
export function checkSpin(slip: readonly Bet[], l: SlipLimits): SlipError | undefined {
  const total = totalStaked(slip);
  if (total > 0 && total < l.minTotal) return { code: 'min_total', min: l.minTotal };
  return undefined;
}

/** Largest amount that could still be added on a spot (for the amount slider). */
export function maxAddable(slip: readonly Bet[], bet: Omit<Bet, 'amount'>, l: SlipLimits): number {
  const room = l.totalMax - totalStaked(slip);
  if (!isInside(bet.type)) return Math.max(0, room);
  const cur = slip.find((x) => spotKey(x) === spotKey(bet))?.amount ?? 0;
  return Math.max(0, Math.min(room, l.insideMax - cur));
}

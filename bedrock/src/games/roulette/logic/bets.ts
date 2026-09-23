/**
 * Roulette bet types, layout geometry, payouts and settlement (GAME_DESIGN §9). PURE.
 *
 * A bet is identified by its type and the set of numbers it covers ("spot"). Every spot the
 * UI offers comes from `allSpots(type)`, and the server re-validates any spot with
 * `isValidSpot` (geometry on the 3 × 12 layout + the 0 pocket).
 */
import { RED_NUMBERS, isPocket, layoutCol, layoutRow } from './wheel';

export const BET_TYPES = [
  'straight', 'split', 'street', 'trio', 'corner', 'first_four', 'six_line',
  'dozen', 'column', 'red', 'black', 'odd', 'even', 'low', 'high',
] as const;
export type BetType = (typeof BET_TYPES)[number];

export const INSIDE_TYPES: readonly BetType[] = ['straight', 'split', 'street', 'trio', 'corner', 'first_four', 'six_line'];
export const OUTSIDE_TYPES: readonly BetType[] = ['dozen', 'column', 'red', 'black', 'odd', 'even', 'low', 'high'];
export const EVEN_MONEY_TYPES: readonly BetType[] = ['red', 'black', 'odd', 'even', 'low', 'high'];

/** "X:1" payouts: a win returns stake × (PAYOUT + 1). */
export const PAYOUT: Readonly<Record<BetType, number>> = {
  straight: 35, split: 17, street: 11, trio: 11, corner: 8, first_four: 8, six_line: 5,
  dozen: 2, column: 2, red: 1, black: 1, odd: 1, even: 1, low: 1, high: 1,
};

export const isBetType = (s: unknown): s is BetType => typeof s === 'string' && (BET_TYPES as readonly string[]).includes(s);
export const isInside = (t: BetType): boolean => INSIDE_TYPES.includes(t);
export const isEvenMoney = (t: BetType): boolean => EVEN_MONEY_TYPES.includes(t);

export interface Spot {
  readonly type: BetType;
  /** covered numbers, ascending */
  readonly numbers: readonly number[];
}

export interface Bet extends Spot {
  readonly amount: number;
}

const range = (a: number, b: number): number[] => Array.from({ length: b - a + 1 }, (_, i) => a + i);
const street = (row: number): number[] => [row * 3 + 1, row * 3 + 2, row * 3 + 3];

/** Numbers covered by the fixed outside bets (dozen/column take an index 1..3). */
export function outsideNumbers(type: BetType, index = 1): number[] {
  switch (type) {
    case 'dozen':
      return range((index - 1) * 12 + 1, index * 12);
    case 'column':
      return range(1, 36).filter((n) => layoutCol(n) === index - 1);
    case 'red':
      return range(1, 36).filter((n) => RED_NUMBERS.has(n));
    case 'black':
      return range(1, 36).filter((n) => !RED_NUMBERS.has(n));
    case 'odd':
      return range(1, 36).filter((n) => n % 2 === 1);
    case 'even':
      return range(1, 36).filter((n) => n % 2 === 0);
    case 'low':
      return range(1, 18);
    case 'high':
      return range(19, 36);
    default:
      throw new Error(`${type} is not an outside bet`);
  }
}

const sameSet = (a: readonly number[], b: readonly number[]): boolean => a.length === b.length && a.every((x, i) => x === b[i]);

/** Normalize a spot: sorted, unique numbers. */
export function makeSpot(type: BetType, numbers: readonly number[]): Spot {
  return { type, numbers: [...new Set(numbers)].sort((x, y) => x - y) };
}

/** Geometry validation of a spot on the European layout. */
export function isValidSpot(s: Spot): boolean {
  if (!isBetType(s.type)) return false;
  const n = [...s.numbers].sort((x, y) => x - y);
  if (!n.every(isPocket) || new Set(n).size !== n.length) return false;
  const [a = -1, b = -1] = n;
  switch (s.type) {
    case 'straight':
      return n.length === 1;
    case 'split':
      if (n.length !== 2) return false;
      if (a === 0) return b >= 1 && b <= 3;
      return (layoutRow(a) === layoutRow(b) && b - a === 1) || (layoutCol(a) === layoutCol(b) && b - a === 3);
    case 'street':
      return n.length === 3 && a >= 1 && layoutCol(a) === 0 && sameSet(n, street(layoutRow(a)));
    case 'trio':
      return sameSet(n, [0, 1, 2]) || sameSet(n, [0, 2, 3]);
    case 'corner':
      return n.length === 4 && a >= 1 && layoutCol(a) < 2 && a <= 32 && sameSet(n, [a, a + 1, a + 3, a + 4]);
    case 'first_four':
      return sameSet(n, [0, 1, 2, 3]);
    case 'six_line':
      return n.length === 6 && a >= 1 && layoutCol(a) === 0 && a <= 31 && sameSet(n, [...street(layoutRow(a)), ...street(layoutRow(a) + 1)]);
    case 'dozen':
    case 'column':
      return [1, 2, 3].some((i) => sameSet(n, outsideNumbers(s.type, i)));
    default:
      return sameSet(n, outsideNumbers(s.type));
  }
}

/** Every valid spot of a type, in layout order (what the position dropdown offers). */
export function allSpots(type: BetType): Spot[] {
  const out: Spot[] = [];
  switch (type) {
    case 'straight':
      for (let x = 0; x <= 36; x++) out.push(makeSpot(type, [x]));
      break;
    case 'split':
      out.push(makeSpot(type, [0, 1]), makeSpot(type, [0, 2]), makeSpot(type, [0, 3]));
      for (let x = 1; x <= 36; x++) {
        if (layoutCol(x) < 2) out.push(makeSpot(type, [x, x + 1]));
        if (x <= 33) out.push(makeSpot(type, [x, x + 3]));
      }
      break;
    case 'street':
      for (let r = 0; r < 12; r++) out.push(makeSpot(type, street(r)));
      break;
    case 'trio':
      out.push(makeSpot(type, [0, 1, 2]), makeSpot(type, [0, 2, 3]));
      break;
    case 'corner':
      for (let x = 1; x <= 32; x++) if (layoutCol(x) < 2) out.push(makeSpot(type, [x, x + 1, x + 3, x + 4]));
      break;
    case 'first_four':
      out.push(makeSpot(type, [0, 1, 2, 3]));
      break;
    case 'six_line':
      for (let r = 0; r < 11; r++) out.push(makeSpot(type, [...street(r), ...street(r + 1)]));
      break;
    case 'dozen':
    case 'column':
      for (let i = 1; i <= 3; i++) out.push(makeSpot(type, outsideNumbers(type, i)));
      break;
    default:
      out.push(makeSpot(type, outsideNumbers(type)));
  }
  return out;
}

/** Stable key of a spot, e.g. `split:17-20`. */
export const spotKey = (s: Spot): string => `${s.type}:${s.numbers.join('-')}`;

/** Index 1..3 of a dozen/column spot (for its label). */
export function outsideIndex(s: Spot): number {
  for (let i = 1; i <= 3; i++) if (sameSet(s.numbers, outsideNumbers(s.type, i))) return i;
  return 0;
}

/** Position label for the dropdown / bet list: `17-20`, `0-1-2` (digits only). */
export const spotLabel = (s: Spot): string => s.numbers.join('-');

export const covers = (s: Spot, result: number): boolean => s.numbers.includes(result);

/**
 * Total return of one bet (stake included, chips floored). Outside bets lose on 0; with
 * la partage even-money bets get half their stake back on 0.
 */
export function betReturn(b: Bet, result: number, laPartage = false): number {
  if (covers(b, result)) return b.amount * (PAYOUT[b.type] + 1);
  if (result === 0 && laPartage && isEvenMoney(b.type)) return Math.floor(b.amount / 2);
  return 0;
}

export interface SlipSettlement {
  staked: number;
  totalReturn: number;
  /** per bet, same order as the slip */
  returns: number[];
}

export function settleSlip(bets: readonly Bet[], result: number, laPartage = false): SlipSettlement {
  const returns = bets.map((b) => betReturn(b, result, laPartage));
  return { staked: totalStaked(bets), totalReturn: returns.reduce((s, x) => s + x, 0), returns };
}

export const totalStaked = (bets: readonly Bet[]): number => bets.reduce((s, b) => s + b.amount, 0);

/** Max total return over the 37 outcomes (bankroll reservation, GAME_DESIGN §18.2). */
export function worstCase(bets: readonly Bet[], laPartage = false): number {
  let max = 0;
  for (let r = 0; r <= 36; r++) max = Math.max(max, settleSlip(bets, r, laPartage).totalReturn);
  return max;
}

/** Add a bet to a slip, merging with an existing bet on the same spot. */
export function mergeBet(slip: readonly Bet[], bet: Bet): Bet[] {
  const key = spotKey(bet);
  const i = slip.findIndex((b) => spotKey(b) === key);
  if (i < 0) return [...slip, bet];
  const out = slip.slice();
  out[i] = { ...slip[i]!, amount: slip[i]!.amount + bet.amount };
  return out;
}

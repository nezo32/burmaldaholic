/**
 * Punto Banco bets, payouts, limits and the reservation worst case (GAME_DESIGN §20.1, §20.4,
 * §20.6). PURE. Every amount is a whole number of chips; every payout is floored.
 */
import type { Coup, Winner } from './rules';

export const BOXES = ['player', 'banker', 'tie', 'player_pair', 'banker_pair'] as const;
export type Box = (typeof BOXES)[number];
export const isBox = (x: unknown): x is Box => typeof x === 'string' && (BOXES as readonly string[]).includes(x);
export const isSideBox = (b: Box): boolean => b === 'tie' || b === 'player_pair' || b === 'banker_pair';

/** One bettor's bets on one coup: chips per box (missing = 0). */
export type Slip = Partial<Record<Box, number>>;

export interface PayRules {
  /** Banker commission c (0.05) */
  readonly commission: number;
  /** Tie pays X:1 (8) */
  readonly tiePays: number;
  /** Pair pays X:1 (11) */
  readonly pairPays: number;
}

export const DEFAULT_RULES: PayRules = { commission: 0.05, tiePays: 8, pairPays: 11 };

const EPS = 1e-9;

/**
 * The Banker step k (§20.1): the smallest integer 1…100 with k × c a whole number, so a Banker
 * bet of k·n wins exactly k·n·(1 − c). c = 0.05 → 20, 0.04 → 25, 0 → 1, 0.03 → 100. When no such
 * k ≤ 100 exists the step is 100 and the payout floors.
 */
export function bankerStep(c: number): number {
  if (!(c > 0)) return 1;
  for (let k = 1; k <= 100; k++) {
    const x = k * c;
    if (Math.abs(x - Math.round(x)) < EPS) return k;
  }
  return 100;
}

/** Commission taken from a Banker win of `amount` (profit = amount − commission, floored payout). */
export function bankerCommission(amount: number, c: number): number {
  if (!(c > 0) || amount <= 0) return 0;
  return Math.max(0, Math.ceil(amount * c - EPS));
}

/** Profit of a winning Banker bet: floor(amount × (1 − c)). */
export const bankerProfit = (amount: number, c: number): number => amount - bankerCommission(amount, c);

/** Snap a Banker amount down to a multiple of the step. */
export const snapToStep = (amount: number, step: number): number => Math.floor(amount / step) * step;

export interface OutcomeClass {
  readonly winner: Winner;
  readonly playerPair: boolean;
  readonly bankerPair: boolean;
}

/** The 12 outcome classes {Player, Banker, Tie} × {P pair y/n} × {B pair y/n} (§20.6). */
export const OUTCOME_CLASSES: readonly OutcomeClass[] = (['player', 'banker', 'tie'] as const).flatMap((winner) =>
  [false, true].flatMap((playerPair) => [false, true].map((bankerPair) => ({ winner, playerPair, bankerPair }))),
);

/** Total return (stake included) of one box bet. */
export function boxReturn(box: Box, amount: number, o: OutcomeClass, r: PayRules): number {
  if (!(amount > 0)) return 0;
  switch (box) {
    case 'player':
      return o.winner === 'player' ? 2 * amount : o.winner === 'tie' ? amount : 0;
    case 'banker':
      return o.winner === 'banker' ? amount + bankerProfit(amount, r.commission) : o.winner === 'tie' ? amount : 0;
    case 'tie':
      return o.winner === 'tie' ? amount * (r.tiePays + 1) : 0;
    case 'player_pair':
      return o.playerPair ? amount * (r.pairPays + 1) : 0;
    case 'banker_pair':
      return o.bankerPair ? amount * (r.pairPays + 1) : 0;
  }
}

export const slipTotal = (s: Slip): number => BOXES.reduce((a, b) => a + (s[b] ?? 0), 0);
export const slipBoxes = (s: Slip): Box[] => BOXES.filter((b) => (s[b] ?? 0) > 0);

export interface SlipResult {
  staked: number;
  totalReturn: number;
  /** per box: total return (only boxes with a bet) */
  returns: Partial<Record<Box, number>>;
  /** commission kept on a winning Banker bet */
  commission: number;
}

export function settleSlip(s: Slip, o: OutcomeClass | Coup, r: PayRules): SlipResult {
  const returns: Partial<Record<Box, number>> = {};
  let totalReturn = 0;
  for (const b of slipBoxes(s)) {
    const ret = boxReturn(b, s[b]!, o, r);
    returns[b] = ret;
    totalReturn += ret;
  }
  const banker = s.banker ?? 0;
  const commission = o.winner === 'banker' && banker > 0 ? bankerCommission(banker, r.commission) : 0;
  return { staked: slipTotal(s), totalReturn, returns, commission };
}

/** Max total return of a slip over the 12 outcome classes (per-ticket bankroll reservation). */
export function worstCaseReturn(s: Slip, r: PayRules): number {
  let m = 0;
  for (const o of OUTCOME_CLASSES) m = Math.max(m, settleSlip(s, o, r).totalReturn);
  return m;
}

/**
 * §20.6 table exposure: the house's worst net loss over the 12 classes, summed over bettors,
 * never negative. (Core reserves per ticket, which is more conservative; this is the spec
 * figure for tests and the insolvency floor.)
 */
export function tableExposure(slips: readonly Slip[], r: PayRules): number {
  let m = 0;
  for (const o of OUTCOME_CLASSES) {
    let loss = 0;
    for (const s of slips) loss += settleSlip(s, o, r).totalReturn - slipTotal(s);
    m = Math.max(m, loss);
  }
  return m;
}

// ---- limits -----------------------------------------------------------------------------

export interface SlipLimits {
  /** every individual bet ≥ this */
  readonly minBet: number;
  /** Banker bets are multiples of this (and ≥ max(step, minBet)) */
  readonly step: number;
  /** Tie and EACH pair ≤ this */
  readonly sideMax: number;
  /** total per coup ≤ this */
  readonly max: number;
  /** total per coup ≥ this at Deal / Ready (High Roller; 0 = none) */
  readonly minTotal: number;
  readonly pairs: boolean;
}

export interface LimitInput {
  /** min(table max, tier max) already multiplied for High Roller tables */
  readonly max: number;
  readonly minBet: number;
  readonly commission: number;
  readonly sideMaxFraction: number;
  readonly minTotal?: number;
  readonly pairs?: boolean;
}

export function slipLimits(i: LimitInput): SlipLimits {
  const max = Math.max(0, Math.floor(i.max));
  const minBet = Math.max(1, Math.floor(i.minBet));
  return {
    minBet,
    step: bankerStep(i.commission),
    sideMax: Math.floor(max * i.sideMaxFraction),
    max,
    minTotal: Math.max(0, Math.floor(i.minTotal ?? 0)),
    pairs: i.pairs !== false,
  };
}

/** The minimum of one box (Banker: max(step, minBet) rounded up to a step multiple). */
export function boxMin(box: Box, l: SlipLimits): number {
  if (box !== 'banker') return l.minBet;
  return Math.ceil(Math.max(l.step, l.minBet) / l.step) * l.step;
}

export type SlipError =
  | { code: 'invalid_amount' }
  | { code: 'pairs_off' }
  | { code: 'bet_too_low'; min: number }
  | { code: 'banker_step'; step: number }
  | { code: 'side_max'; max: number }
  | { code: 'total_max'; max: number }
  | { code: 'min_total'; min: number };

/** Most chips that can still be added to `box` (0 when none); Banker snapped to the step. */
export function maxAddable(s: Slip, box: Box, l: SlipLimits): number {
  if ((box === 'player_pair' || box === 'banker_pair') && !l.pairs) return 0;
  let room = l.max - slipTotal(s);
  if (isSideBox(box)) room = Math.min(room, l.sideMax - (s[box] ?? 0));
  if (box === 'banker') room = snapToStep(room, l.step);
  return Math.max(0, room);
}

/** Validate adding `amount` to `box` (the server-side check; the form snaps Banker first). */
export function checkAdd(s: Slip, box: Box, amount: number, l: SlipLimits): SlipError | undefined {
  if (!Number.isSafeInteger(amount) || amount <= 0) return { code: 'invalid_amount' };
  if ((box === 'player_pair' || box === 'banker_pair') && !l.pairs) return { code: 'pairs_off' };
  if (box === 'banker' && amount % l.step !== 0) return { code: 'banker_step', step: l.step };
  const after = (s[box] ?? 0) + amount;
  if (after < boxMin(box, l)) return { code: 'bet_too_low', min: boxMin(box, l) };
  if (isSideBox(box) && after > l.sideMax) return { code: 'side_max', max: l.sideMax };
  if (slipTotal(s) + amount > l.max) return { code: 'total_max', max: l.max };
  return undefined;
}

/** Check a whole slip (Rebet, Deal): every rule of checkAdd plus the per-coup minimum. */
export function checkSlip(s: Slip, l: SlipLimits, forDeal = false): SlipError | undefined {
  let acc: Slip = {};
  for (const b of slipBoxes(s)) {
    const e = checkAdd(acc, b, s[b]!, l);
    if (e) return e;
    acc = { ...acc, [b]: s[b] };
  }
  if (forDeal && slipTotal(s) > 0 && slipTotal(s) < l.minTotal) return { code: 'min_total', min: l.minTotal };
  return undefined;
}

/**
 * Fit a previous slip into the current limits (Rebet, §20.5 "snapped to the current limits"):
 * Banker snapped to the step, side bets and the total capped, boxes below their minimum dropped.
 */
export function fitSlip(s: Slip, l: SlipLimits): Slip {
  const out: Slip = {};
  for (const b of slipBoxes(s)) {
    const room = maxAddable(out, b, l);
    let a = Math.min(s[b]!, room);
    if (b === 'banker') a = snapToStep(a, l.step);
    if (a >= boxMin(b, l)) out[b] = a;
  }
  return out;
}

/** Merge `amount` into a slip (no validation). */
export const addToSlip = (s: Slip, box: Box, amount: number): Slip => ({ ...s, [box]: (s[box] ?? 0) + amount });

/** Theoretical house edge per box for 8 decks (§20.2), for the VIP theoretical loss. */
export function boxEdge(box: Box, r: PayRules): number {
  // Exact 8-deck probabilities (§20.2).
  const pB = 0.458597;
  const pP = 0.446247;
  const pT = 0.095156;
  const pPair = 31 / 415;
  switch (box) {
    case 'player':
      return Math.max(0, pB - pP);
    case 'banker':
      return Math.max(0, pP - (1 - r.commission) * pB);
    case 'tie':
      return Math.max(0, 1 - (r.tiePays + 1) * pT);
    default:
      return Math.max(0, 1 - (r.pairPays + 1) * pPair);
  }
}

/** Stake-weighted edge of a slip. */
export function slipEdge(s: Slip, r: PayRules): number {
  const total = slipTotal(s);
  if (total <= 0) return 0;
  return slipBoxes(s).reduce((a, b) => a + s[b]! * boxEdge(b, r), 0) / total;
}

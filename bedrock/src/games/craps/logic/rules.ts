/**
 * Craps rules (GAME_DESIGN §10). PURE: no @minecraft imports.
 *
 * Every bet is resolved independently from the pre-roll table point, so the same function
 * drives the live table and the "bets stay working" auto-completion of a player who left.
 * Returns are TOTAL returns (stake included), like `ctx.wagers.settle` expects.
 */
import { type Rng, randInt } from '../../../core/logic/rng';

export type BetKind = 'pass' | 'dont_pass' | 'come' | 'dont_come' | 'field';
export const BET_KINDS: readonly BetKind[] = ['pass', 'dont_pass', 'come', 'dont_come', 'field'];
export type PointNumber = 4 | 5 | 6 | 8 | 9 | 10;
export const POINT_NUMBERS: readonly PointNumber[] = [4, 5, 6, 8, 9, 10];
export type Dice = readonly [number, number];
/** 'take' = odds behind Pass/Come (right side), 'lay' = behind Don't Pass/Don't Come. */
export type OddsSide = 'take' | 'lay';

export interface CrapsRules {
  /** Field pays X:1 on 2 (default 2). */
  fieldPays2: number;
  /** Field pays X:1 on 12 (default 3). */
  fieldPays12: number;
  /** Max odds as a multiple of the flat bet (3-4-5×). */
  maxOdds4_10: number;
  maxOdds5_9: number;
  maxOdds6_8: number;
}

export const DEFAULT_RULES: CrapsRules = { fieldPays2: 2, fieldPays12: 3, maxOdds4_10: 3, maxOdds5_9: 4, maxOdds6_8: 5 };

export interface Bet {
  readonly id: string;
  /** player id */
  readonly owner: string;
  readonly kind: BetKind;
  /** flat (contract) amount */
  readonly flat: number;
  /** odds amount behind the bet (0 = none) */
  odds: number;
  /** Come / Don't Come point once the bet has "moved" (Pass / Don't Pass use the table point). */
  point?: PointNumber;
}

export const isPointNumber = (n: number): n is PointNumber => n === 4 || n === 5 || n === 6 || n === 8 || n === 9 || n === 10;

export const rollDice = (rng: Rng): Dice => [randInt(rng, 1, 6), randInt(rng, 1, 6)];
export const diceTotal = (d: Dice): number => d[0] + d[1];

export const oddsSide = (kind: BetKind): OddsSide | undefined =>
  kind === 'pass' || kind === 'come' ? 'take' : kind === 'dont_pass' || kind === 'dont_come' ? 'lay' : undefined;

/** Odds payout ratio [numerator, denominator] (win = amount × n / d). True odds: 0 % edge. */
export function oddsRatio(side: OddsSide, point: PointNumber): readonly [number, number] {
  const take: readonly [number, number] = point === 4 || point === 10 ? [2, 1] : point === 5 || point === 9 ? [3, 2] : [6, 5];
  return side === 'take' ? take : [take[1], take[0]];
}

/** Odds amounts must be a multiple of this so the payout is whole (§10.1). */
export function oddsUnit(side: OddsSide, point: PointNumber): number {
  return oddsRatio(side, point)[1];
}

/** Winnings (excluding the returned odds stake) of an odds bet. */
export function oddsWin(side: OddsSide, point: PointNumber, amount: number): number {
  const [n, d] = oddsRatio(side, point);
  return Math.floor((amount * n) / d);
}

export function maxOddsMultiple(rules: CrapsRules, point: PointNumber): number {
  return point === 4 || point === 10 ? rules.maxOdds4_10 : point === 5 || point === 9 ? rules.maxOdds5_9 : rules.maxOdds6_8;
}

/** Snap an amount down to a valid odds multiple. */
export const snapOdds = (amount: number, unit: number): number => Math.max(0, Math.floor(Math.floor(amount) / unit) * unit);

/**
 * Maximum total odds behind a flat bet. Take: `multiple × flat` (3-4-5×). Lay: the amount
 * whose win is `multiple × flat` (i.e. 6× the flat bet at 3-4-5×). Snapped to whole payouts.
 */
export function maxOdds(side: OddsSide, point: PointNumber, flat: number, rules: CrapsRules): number {
  const m = Math.max(0, maxOddsMultiple(rules, point));
  const [n, d] = oddsRatio(side, point);
  const raw = side === 'take' ? m * flat : (m * flat * d) / n;
  return snapOdds(raw, oddsUnit(side, point));
}

/** Field total return for a flat bet (0 = lost). */
export function fieldReturn(total: number, flat: number, rules: CrapsRules): number {
  if (total === 2) return flat * (1 + rules.fieldPays2);
  if (total === 12) return flat * (1 + rules.fieldPays12);
  if (total === 3 || total === 4 || total === 9 || total === 10 || total === 11) return flat * 2;
  return 0;
}

/**
 * Worst-case TOTAL return of a flat bet (bankroll reservation, §18.2). Odds add
 * `oddsWorstCase` when they are placed.
 */
export function flatWorstCase(kind: BetKind, flat: number, rules: CrapsRules): number {
  return kind === 'field' ? flat * (1 + Math.max(1, rules.fieldPays2, rules.fieldPays12)) : flat * 2;
}

export const oddsWorstCase = (side: OddsSide, point: PointNumber, amount: number): number => amount + oddsWin(side, point, amount);

// ---- resolution ----------------------------------------------------------------------------

export type BetOutcome =
  /** resolved: paid `totalReturn` (> flat + odds) */
  | 'win'
  /** resolved: `totalReturn` is 0, or only the (off) odds returned */
  | 'lose'
  /** resolved: everything returned (bar 12) */
  | 'push'
  /** Come / Don't Come moved to its point (still working) */
  | 'move'
  /** nothing happened to it */
  | 'stay';

export interface BetResolution {
  bet: Bet;
  outcome: BetOutcome;
  /** total return for resolved bets (stake included) */
  totalReturn: number;
  /** come odds were off on the come-out roll and returned as part of totalReturn */
  oddsReturned?: boolean;
  /** new come point for 'move' */
  movedTo?: PointNumber;
}

export type RollEvent =
  | { kind: 'natural'; total: number }
  | { kind: 'craps'; total: number }
  | { kind: 'point_set'; point: PointNumber }
  | { kind: 'point_made'; point: PointNumber }
  | { kind: 'seven_out'; point: PointNumber }
  | { kind: 'roll'; total: number; point: PointNumber };

/** Table-level effect of a roll given the current point (undefined = come-out). */
export function rollEvent(point: PointNumber | undefined, total: number): { event: RollEvent; nextPoint: PointNumber | undefined } {
  if (point === undefined) {
    if (total === 7 || total === 11) return { event: { kind: 'natural', total }, nextPoint: undefined };
    if (total === 2 || total === 3 || total === 12) return { event: { kind: 'craps', total }, nextPoint: undefined };
    const p = total as PointNumber;
    return { event: { kind: 'point_set', point: p }, nextPoint: p };
  }
  if (total === point) return { event: { kind: 'point_made', point }, nextPoint: undefined };
  if (total === 7) return { event: { kind: 'seven_out', point }, nextPoint: undefined };
  return { event: { kind: 'roll', total, point }, nextPoint: point };
}

const resolved = (bet: Bet, outcome: 'win' | 'lose' | 'push', totalReturn: number, oddsReturned?: boolean): BetResolution => ({
  bet,
  outcome,
  totalReturn,
  ...(oddsReturned ? { oddsReturned } : {}),
});
const stay = (bet: Bet): BetResolution => ({ bet, outcome: 'stay', totalReturn: 0 });

/** Resolve one bet against a roll. `tablePoint` is the point BEFORE the roll. */
export function resolveBet(bet: Bet, tablePoint: PointNumber | undefined, total: number, rules: CrapsRules): BetResolution {
  const comeOut = tablePoint === undefined;
  const f = bet.flat;
  switch (bet.kind) {
    case 'field': {
      const r = fieldReturn(total, f, rules);
      return resolved(bet, r > 0 ? 'win' : 'lose', r);
    }
    case 'pass': {
      if (comeOut) {
        if (total === 7 || total === 11) return resolved(bet, 'win', 2 * f + bet.odds);
        if (total === 2 || total === 3 || total === 12) return resolved(bet, 'lose', 0);
        return stay(bet);
      }
      if (total === tablePoint) return resolved(bet, 'win', 2 * f + bet.odds + oddsWin('take', tablePoint, bet.odds));
      if (total === 7) return resolved(bet, 'lose', 0);
      return stay(bet);
    }
    case 'dont_pass': {
      if (comeOut) {
        if (total === 2 || total === 3) return resolved(bet, 'win', 2 * f + bet.odds);
        if (total === 12) return resolved(bet, 'push', f + bet.odds);
        if (total === 7 || total === 11) return resolved(bet, 'lose', 0);
        return stay(bet);
      }
      if (total === 7) return resolved(bet, 'win', 2 * f + bet.odds + oddsWin('lay', tablePoint, bet.odds));
      if (total === tablePoint) return resolved(bet, 'lose', 0);
      return stay(bet);
    }
    case 'come': {
      const p = bet.point;
      if (p === undefined) {
        if (total === 7 || total === 11) return resolved(bet, 'win', 2 * f + bet.odds);
        if (total === 2 || total === 3 || total === 12) return resolved(bet, 'lose', bet.odds, bet.odds > 0);
        return { bet, outcome: 'move', totalReturn: 0, movedTo: total as PointNumber };
      }
      if (total === p) {
        // Come odds are OFF on the come-out roll: the flat wins, the odds come back untouched.
        const win = comeOut ? 0 : oddsWin('take', p, bet.odds);
        return resolved(bet, 'win', 2 * f + bet.odds + win, comeOut && bet.odds > 0);
      }
      if (total === 7) return comeOut ? resolved(bet, 'lose', bet.odds, bet.odds > 0) : resolved(bet, 'lose', 0);
      return stay(bet);
    }
    case 'dont_come': {
      const p = bet.point;
      if (p === undefined) {
        if (total === 2 || total === 3) return resolved(bet, 'win', 2 * f + bet.odds);
        if (total === 12) return resolved(bet, 'push', f + bet.odds);
        if (total === 7 || total === 11) return resolved(bet, 'lose', 0);
        return { bet, outcome: 'move', totalReturn: 0, movedTo: total as PointNumber };
      }
      // Lay odds are always working.
      if (total === 7) return resolved(bet, 'win', 2 * f + bet.odds + oddsWin('lay', p, bet.odds));
      if (total === p) return resolved(bet, 'lose', 0);
      return stay(bet);
    }
  }
}

export interface RollResult {
  dice: Dice;
  total: number;
  event: RollEvent;
  pointBefore: PointNumber | undefined;
  nextPoint: PointNumber | undefined;
  /** every bet that was on the table, with its resolution */
  resolutions: BetResolution[];
  /** bets still working after the roll (moved come bets carry their new point) */
  remaining: Bet[];
}

/** Apply a roll to a set of bets. Does not mutate the input bets. */
export function applyRoll(point: PointNumber | undefined, bets: readonly Bet[], dice: Dice, rules: CrapsRules): RollResult {
  const total = diceTotal(dice);
  const { event, nextPoint } = rollEvent(point, total);
  const resolutions = bets.map((b) => resolveBet(b, point, total, rules));
  const remaining: Bet[] = [];
  for (const r of resolutions) {
    if (r.outcome === 'stay') remaining.push(r.bet);
    else if (r.outcome === 'move') remaining.push({ ...r.bet, point: r.movedTo });
  }
  return { dice, total, event, pointBefore: point, nextPoint, resolutions, remaining };
}

/**
 * Auto-complete a player's bets after they left the table (GAME_DESIGN §4.1: craps bets stay
 * working until resolved): keep rolling honest dice for them alone until every bet is
 * resolved. Returns the total return per bet id. Terminates with probability 1; `maxRolls`
 * is a safety net (bets still open then are returned as a push).
 */
export function autoComplete(point: PointNumber | undefined, bets: readonly Bet[], rng: Rng, rules: CrapsRules, maxRolls = 10_000): Map<string, number> {
  const out = new Map<string, number>();
  let live = bets.slice();
  let p = point;
  for (let i = 0; i < maxRolls && live.length; i++) {
    const r = applyRoll(p, live, rollDice(rng), rules);
    for (const x of r.resolutions) if (x.outcome === 'win' || x.outcome === 'lose' || x.outcome === 'push') out.set(x.bet.id, x.totalReturn);
    live = r.remaining;
    p = r.nextPoint;
  }
  for (const b of live) out.set(b.id, b.flat + b.odds);
  return out;
}

// ---- shooter rotation -----------------------------------------------------------------------

export interface SeatInfo {
  id: string;
  /** 1-based seat number (clockwise order) */
  seat: number;
  /** has a Pass or Don't Pass bet */
  hasLineBet: boolean;
}

/**
 * Next shooter clockwise after `current` (by seat number, wrapping). With `requireLineBet`
 * only players with a line bet qualify. `includeCurrent` lets the current shooter keep the
 * dice when nobody else qualifies (seven-out at a one-player table).
 */
export function nextShooter(seats: readonly SeatInfo[], current: string | undefined, requireLineBet: boolean, includeCurrent = true): string | undefined {
  const sorted = seats.slice().sort((a, b) => a.seat - b.seat);
  if (!sorted.length) return undefined;
  const curSeat = sorted.find((s) => s.id === current)?.seat ?? seats.find((s) => s.id === current)?.seat;
  const after = curSeat === undefined ? -1 : sorted.findIndex((s) => s.seat > curSeat);
  const start = after === -1 ? 0 : after;
  const order = curSeat === undefined ? sorted : [...sorted.slice(start), ...sorted.slice(0, start)];
  for (const s of order) {
    if (s.id === current && !includeCurrent) continue;
    if (!requireLineBet || s.hasLineBet) return s.id;
  }
  return undefined;
}

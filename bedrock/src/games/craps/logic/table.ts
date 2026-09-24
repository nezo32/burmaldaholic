/**
 * Craps table state machine (GAME_DESIGN §10.2). PURE: no @minecraft imports.
 *
 *   COME_OUT (puck OFF): Pass, Don't Pass, Field (+ odds adjustments on come points)
 *     7/11 natural · 2/3/12 craps (12 = bar for Don't) · 4,5,6,8,9,10 -> point, puck ON
 *   POINT (puck ON): Come, Don't Come, Field, Odds
 *     point -> pass wins, puck OFF (same shooter) · 7 -> seven-out, puck OFF, next shooter
 *
 * Money is not handled here: the runtime maps every Bet id to a wager ticket.
 */
import {
  type Bet,
  type BetKind,
  type CrapsRules,
  type Dice,
  type OddsSide,
  type PointNumber,
  type RollResult,
  type SeatInfo,
  applyRoll,
  autoComplete,
  maxOdds,
  nextShooter,
  oddsSide,
  oddsUnit,
} from './rules';
import type { Rng } from '../../../core/logic/rng';

export type PlaceError = 'line_only_come_out' | 'needs_point' | 'already_placed';

export interface OddsInfo {
  bet: Bet;
  side: OddsSide;
  point: PointNumber;
  /** amounts must be a multiple of this */
  unit: number;
  /** max total odds behind this bet */
  max: number;
  /** how much more may be added (snapped) */
  room: number;
  /** come odds placed/held while the puck is OFF are not working */
  off: boolean;
}

export type OddsError = { code: 'multiple'; unit: number } | { code: 'max'; max: number } | { code: 'no_point' };

export interface TableRoll extends RollResult {
  /** shooter who threw */
  shooter: string | undefined;
  /** seven-out: the dice pass to the next shooter */
  sevenOut: boolean;
  /** points made in a row by this shooter (after this roll) */
  pointsInRow: number;
}

export class CrapsTable {
  point: PointNumber | undefined;
  bets: Bet[] = [];
  shooter: string | undefined;
  lastRoll: Dice | undefined;
  pointsInRow = 0;
  /** rolls thrown at this table (used to invalidate stale timers) */
  rollCount = 0;
  private seq = 0;

  constructor(public rules: CrapsRules) {}

  get comeOut(): boolean {
    return this.point === undefined;
  }

  betsOf(owner: string): Bet[] {
    return this.bets.filter((b) => b.owner === owner);
  }

  hasLineBet(owner: string): boolean {
    return this.bets.some((b) => b.owner === owner && (b.kind === 'pass' || b.kind === 'dont_pass'));
  }

  /** Why `kind` cannot be placed now by `owner` (undefined = allowed). */
  placeError(owner: string, kind: BetKind): PlaceError | undefined {
    const mine = this.betsOf(owner);
    switch (kind) {
      case 'pass':
      case 'dont_pass':
        if (!this.comeOut) return 'line_only_come_out';
        return mine.some((b) => b.kind === kind) ? 'already_placed' : undefined;
      case 'come':
      case 'dont_come':
        if (this.comeOut) return 'needs_point';
        return mine.some((b) => b.kind === kind && b.point === undefined) ? 'already_placed' : undefined;
      case 'field':
        return mine.some((b) => b.kind === 'field') ? 'already_placed' : undefined;
    }
  }

  /** Add a flat bet (validate with placeError first). */
  addBet(owner: string, kind: BetKind, flat: number): Bet {
    const bet: Bet = { id: `b${++this.seq}`, owner, kind, flat: Math.floor(flat), odds: 0 };
    this.bets.push(bet);
    return bet;
  }

  /** Remove a bet that could not be funded (runtime rollback). */
  removeBet(id: string): void {
    this.bets = this.bets.filter((b) => b.id !== id);
  }

  bet(id: string): Bet | undefined {
    return this.bets.find((b) => b.id === id);
  }

  /** The point an odds bet behind `bet` would be on, if any. */
  pointOf(bet: Bet): PointNumber | undefined {
    if (bet.kind === 'pass' || bet.kind === 'dont_pass') return this.point;
    if (bet.kind === 'come' || bet.kind === 'dont_come') return bet.point;
    return undefined;
  }

  /**
   * Contract bets whose point is established: a roll already decided part of their outcome
   * (e.g. Pass on a point of 4), so they are played out, never refunded (review M1).
   */
  pointBets(): Bet[] {
    return this.bets.filter((b) => this.pointOf(b) !== undefined);
  }

  /**
   * A play-out of every point bet with fresh honest dice (like a player leaving, §4.1): the
   * total return per bet id. Does not change the table.
   */
  playOut(rng: Rng): Map<string, number> {
    const bets = this.pointBets().map((b) => ({ ...b }));
    return bets.length ? autoComplete(this.point, bets, rng, this.rules) : new Map();
  }

  oddsInfo(bet: Bet): OddsInfo | undefined {
    const side = oddsSide(bet.kind);
    const point = this.pointOf(bet);
    if (!side || point === undefined) return undefined;
    const unit = oddsUnit(side, point);
    const max = maxOdds(side, point, bet.flat, this.rules);
    const room = Math.max(0, Math.floor((max - bet.odds) / unit) * unit);
    return { bet, side, point, unit, max, room, off: bet.kind === 'come' && this.comeOut };
  }

  /** Bets of `owner` that can take (more) odds right now. */
  oddsTargets(owner: string): OddsInfo[] {
    return this.betsOf(owner)
      .map((b) => this.oddsInfo(b))
      .filter((x): x is OddsInfo => !!x && x.room > 0);
  }

  oddsError(bet: Bet, amount: number): OddsError | undefined {
    const info = this.oddsInfo(bet);
    if (!info) return { code: 'no_point' };
    if (amount <= 0 || amount % info.unit !== 0) return { code: 'multiple', unit: info.unit };
    if (bet.odds + amount > info.max) return { code: 'max', max: info.max };
    return undefined;
  }

  addOdds(id: string, amount: number): void {
    const b = this.bet(id);
    if (b) b.odds += Math.floor(amount);
  }

  /** Take every bet of a player off the table (they left); returns them for auto-completion. */
  removeOwner(owner: string): Bet[] {
    const mine = this.betsOf(owner);
    this.bets = this.bets.filter((b) => b.owner !== owner);
    return mine;
  }

  /**
   * Keep the shooter valid for the seated players: on the come-out the shooter must have a
   * line bet (the dice move clockwise to the next player who has one); during a point the
   * shooter only has to be seated. With `passIfNoLineBet` false a seated shooter keeps the
   * dice while they are still placing their line bet (the runtime passes them on once the
   * betting window closes). Returns true when the shooter changed.
   */
  ensureShooter(seats: readonly SeatInfo[], passIfNoLineBet = true): boolean {
    const before = this.shooter;
    const seated = seats.some((s) => s.id === this.shooter);
    if (!seats.length) {
      this.shooter = undefined;
    } else if (this.comeOut) {
      if (!seated || (passIfNoLineBet && !this.hasLineBet(this.shooter!))) {
        const withLine = seats.map((s) => ({ ...s, hasLineBet: this.hasLineBet(s.id) }));
        const next = nextShooter(withLine, this.shooter, true);
        if (next) this.shooter = next;
        else if (!seated) this.shooter = nextShooter(seats, this.shooter, false);
      }
    } else if (!seated) {
      this.shooter = nextShooter(seats, this.shooter, false);
    }
    if (this.shooter !== before) this.pointsInRow = 0;
    return this.shooter !== before;
  }

  /** Whether the shooter may throw now (come-out needs their line bet). */
  canRoll(): boolean {
    if (!this.shooter) return false;
    return !this.comeOut || this.hasLineBet(this.shooter);
  }

  /** Throw the dice: resolves every bet, moves the puck, rotates the shooter on a seven-out. */
  roll(dice: Dice, seats: readonly SeatInfo[] = []): TableRoll {
    const r = applyRoll(this.point, this.bets, dice, this.rules);
    const shooter = this.shooter;
    this.bets = r.remaining;
    this.point = r.nextPoint;
    this.lastRoll = dice;
    this.rollCount++;
    const sevenOut = r.event.kind === 'seven_out';
    if (r.event.kind === 'point_made') this.pointsInRow++;
    const pointsInRow = this.pointsInRow;
    if (sevenOut) {
      this.pointsInRow = 0;
      this.shooter = nextShooter(seats, shooter, false) ?? shooter;
    }
    return { ...r, shooter, sevenOut, pointsInRow };
  }
}

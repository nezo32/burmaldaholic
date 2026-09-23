/**
 * Shared-spin state machine of one roulette table (GAME_DESIGN §9). PURE.
 *
 *   BETTING ──(everyone seated is ready | bet timer expired)──▶ NO_MORE_BETS (20 t)
 *      ▲                                                            │ draw result
 *      │                                                            ▼
 *   RESULT (60 t, settle) ◀──────────────────────────────────── SPIN (spinTicks)
 *
 * - The bet timer (betTicks) starts at the first bet once 2+ players are seated; a single
 *   player spins with "Spin" (= ready). Players who left the table count as ready, so a round
 *   whose only bettor walked away spins immediately ("roulette: spin proceeds", §4.1).
 * - At NO_MORE_BETS, slips below `minTotal` (High-Roller) are dropped (the caller refunds).
 * - Slips are keyed by player id; money is handled by the caller.
 */
import { type Bet, mergeBet, totalStaked } from './bets';
import type { Rng } from '../../../core/logic/rng';
import { spinWheel } from './wheel';

export type Phase = 'betting' | 'no_more_bets' | 'spin' | 'result';

export interface Timings {
  betTicks: number;
  noMoreBetsTicks: number;
  spinTicks: number;
  resultTicks: number;
}

export const DEFAULT_TIMINGS: Timings = { betTicks: 500, noMoreBetsTicks: 20, spinTicks: 100, resultTicks: 60 };

export type Transition =
  | { to: 'no_more_bets'; dropped: string[] }
  /** betting closed but nothing was left to spin (all slips dropped/cleared) */
  | { to: 'betting'; dropped: string[]; reset: false }
  | { to: 'spin'; result: number }
  | { to: 'result'; result: number; slips: ReadonlyMap<string, readonly Bet[]> }
  | { to: 'betting'; dropped: string[]; reset: true };

interface Slip {
  bets: Bet[];
  ready: boolean;
}

export class RouletteRound {
  phase: Phase = 'betting';
  /** tick at which the current phase ends (betting: undefined until the timer starts) */
  endsAt: number | undefined;
  result: number | undefined;
  /** most recent first */
  readonly history: number[] = [];
  private slips = new Map<string, Slip>();

  constructor(
    public timings: Timings = DEFAULT_TIMINGS,
    public historyLength = 12,
  ) {}

  canBet(): boolean {
    return this.phase === 'betting';
  }

  bets(id: string): readonly Bet[] {
    return this.slips.get(id)?.bets ?? [];
  }

  total(id: string): number {
    return totalStaked(this.bets(id));
  }

  bettors(): string[] {
    return [...this.slips.keys()];
  }

  hasBets(): boolean {
    return this.slips.size > 0;
  }

  isReady(id: string): boolean {
    return this.slips.get(id)?.ready ?? false;
  }

  /** Add bets (already validated and paid by the caller). Clears the player's ready flag. */
  addBets(id: string, bets: readonly Bet[]): void {
    if (!this.canBet()) throw new Error('betting is closed');
    let slip = this.slips.get(id);
    if (!slip) this.slips.set(id, (slip = { bets: [], ready: false }));
    for (const b of bets) slip.bets = mergeBet(slip.bets, b);
    slip.ready = false;
  }

  /** Remove a player's slip (clear bets / refund). Returns what was removed. */
  clear(id: string): Bet[] {
    const s = this.slips.get(id);
    this.slips.delete(id);
    return s?.bets ?? [];
  }

  setReady(id: string, ready = true): void {
    const s = this.slips.get(id);
    if (s) s.ready = ready;
  }

  /** Seconds-style helper: ticks left in the current phase (undefined = no timer). */
  remaining(now: number): number | undefined {
    return this.endsAt === undefined ? undefined : Math.max(0, this.endsAt - now);
  }

  /**
   * Advance the machine. `seated` = ids of players currently seated at the table.
   * At most one transition per call.
   */
  update(now: number, seated: readonly string[], rng: Rng, minTotal = 0): Transition | undefined {
    switch (this.phase) {
      case 'betting': {
        if (!this.hasBets()) {
          this.endsAt = undefined;
          return undefined;
        }
        if (this.endsAt === undefined && seated.length >= 2) this.endsAt = now + this.timings.betTicks;
        // Early close: every slip is ready or abandoned (its owner left the table). Seated
        // players without bets never block the spin.
        const seatedSet = new Set(seated);
        const allReady = [...this.slips.entries()].every(([id, s]) => s.ready || !seatedSet.has(id));
        const timeUp = this.endsAt !== undefined && now >= this.endsAt;
        if (!timeUp && !allReady) return undefined;
        // High-Roller minimum: drop slips below it (the caller refunds them).
        const dropped: string[] = [];
        if (minTotal > 0) {
          for (const [id, s] of this.slips) if (totalStaked(s.bets) < minTotal) dropped.push(id);
          for (const id of dropped) this.slips.delete(id);
        }
        if (!this.hasBets()) {
          this.endsAt = undefined;
          return { to: 'betting', dropped, reset: false };
        }
        this.phase = 'no_more_bets';
        this.endsAt = now + this.timings.noMoreBetsTicks;
        return { to: 'no_more_bets', dropped };
      }
      case 'no_more_bets': {
        if (now < (this.endsAt ?? 0)) return undefined;
        this.result = spinWheel(rng);
        this.phase = 'spin';
        this.endsAt = now + this.timings.spinTicks;
        return { to: 'spin', result: this.result };
      }
      case 'spin': {
        if (now < (this.endsAt ?? 0)) return undefined;
        const result = this.result!;
        this.phase = 'result';
        this.endsAt = now + this.timings.resultTicks;
        if (this.historyLength > 0) {
          this.history.unshift(result);
          this.history.length = Math.min(this.history.length, this.historyLength);
        }
        const slips = new Map<string, readonly Bet[]>();
        for (const [id, s] of this.slips) slips.set(id, s.bets);
        return { to: 'result', result, slips };
      }
      case 'result': {
        if (now < (this.endsAt ?? 0)) return undefined;
        this.slips = new Map();
        this.phase = 'betting';
        this.endsAt = undefined;
        this.result = undefined;
        return { to: 'betting', dropped: [], reset: true };
      }
    }
  }
}

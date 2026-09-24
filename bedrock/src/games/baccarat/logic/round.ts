/**
 * Shared-coup table timing (GAME_DESIGN §20.5). PURE: the runtime feeds it the current tick
 * and the seated player ids and acts on the returned transition.
 *
 *   betting ─(timer from the FIRST bet, or every seated bettor Ready)→ no_more_bets (20 t)
 *     → shuffle (40 t, only when due) → reveal (revealTicks; the coup is dealt and persisted on
 *     entry) → result (60 t, settle) → betting
 *
 * The round only keeps timing and readiness; the slips live in the runtime (house bets) or
 * the chemin de fer state.
 */
export type Phase = 'betting' | 'no_more_bets' | 'shuffle' | 'reveal' | 'result';

export interface Timings {
  betTicks: number;
  noMoreBetsTicks: number;
  shuffleTicks: number;
  revealTicks: number;
  resultTicks: number;
}

export const DEFAULT_TIMINGS: Timings = { betTicks: 400, noMoreBetsTicks: 20, shuffleTicks: 40, revealTicks: 80, resultTicks: 60 };

export type Transition = { to: 'no_more_bets' } | { to: 'shuffle' } | { to: 'reveal' } | { to: 'result' } | { to: 'betting' };

export class CoupClock {
  phase: Phase = 'betting';
  /** tick the current timed phase ends (betting: undefined until the first bet) */
  until: number | undefined;
  private readonly ready = new Set<string>();

  constructor(public timings: Timings) {}

  canBet(): boolean {
    return this.phase === 'betting';
  }

  /** A bet was placed at `now`: the first bet of the coup starts the window. */
  onBet(now: number): void {
    if (this.phase === 'betting' && this.until === undefined) this.until = now + this.timings.betTicks;
  }

  setReady(id: string): void {
    if (this.phase === 'betting') this.ready.add(id);
  }

  unready(id: string): void {
    this.ready.delete(id);
  }

  isReady(id: string): boolean {
    return this.ready.has(id);
  }

  /** Ticks left in the current timed phase. */
  remaining(now: number): number | undefined {
    return this.until === undefined ? undefined : Math.max(0, this.until - now);
  }

  /** Close betting right away (Banco, a single bettor's Deal). */
  closeNow(now: number): void {
    if (this.phase === 'betting') this.until = now;
  }

  /**
   * Advance. `bettors` = ids with bets on this coup; `seated` = ids seated now. Betting ends
   * on the timer or when every SEATED bettor is Ready (bettors who disconnected ride along).
   * `shuffleDue` is asked when no_more_bets ends.
   */
  update(now: number, bettors: readonly string[], seated: readonly string[], shuffleDue: () => boolean): Transition | undefined {
    switch (this.phase) {
      case 'betting': {
        if (!bettors.length) {
          this.until = undefined;
          this.ready.clear();
          return undefined;
        }
        const seatedBettors = bettors.filter((id) => seated.includes(id));
        const allReady = seatedBettors.length > 0 && seatedBettors.every((id) => this.ready.has(id));
        if (this.until === undefined) this.until = now + this.timings.betTicks;
        if (!allReady && now < this.until) return undefined;
        return this.go('no_more_bets', now + this.timings.noMoreBetsTicks);
      }
      case 'no_more_bets':
        if (now < this.until!) return undefined;
        if (shuffleDue()) return this.go('shuffle', now + this.timings.shuffleTicks);
        return this.go('reveal', now + this.timings.revealTicks);
      case 'shuffle':
        if (now < this.until!) return undefined;
        return this.go('reveal', now + this.timings.revealTicks);
      case 'reveal':
        if (now < this.until!) return undefined;
        return this.go('result', now + this.timings.resultTicks);
      case 'result':
        if (now < this.until!) return undefined;
        this.ready.clear();
        return this.go('betting', undefined);
    }
  }

  /** Back to an idle betting phase (table reset). */
  reset(): void {
    this.phase = 'betting';
    this.until = undefined;
    this.ready.clear();
  }

  private go(phase: Phase, until: number | undefined): Transition {
    this.phase = phase;
    this.until = until;
    return { to: phase };
  }
}

export interface RevealFrame {
  /** ticks after the reveal started */
  at: number;
  /** cards shown so far per hand */
  player: number;
  banker: number;
  /** announcement of this frame */
  note?: 'player_draws' | 'player_stands' | 'banker_draws' | 'banker_stands' | 'natural';
}

/**
 * Reveal schedule (§20.5, UI.md §14): P1, B1, P2, B2 one step apart, then Player's third card
 * (or "Player stands"), then Banker's (or "Banker stands"). step = revealTicks / 8 (10 t at 80).
 */
export function revealFrames(playerCards: number, bankerCards: number, natural: boolean, revealTicks: number): RevealFrame[] {
  const step = Math.max(2, Math.floor(revealTicks / 8));
  const f: RevealFrame[] = [
    { at: 0, player: 1, banker: 0 },
    { at: step, player: 1, banker: 1 },
    { at: 2 * step, player: 2, banker: 1 },
    { at: 3 * step, player: 2, banker: 2 },
  ];
  if (natural) {
    f.push({ at: 4 * step, player: 2, banker: 2, note: 'natural' });
    return f;
  }
  f.push(playerCards === 3 ? { at: 5 * step, player: 3, banker: 2, note: 'player_draws' } : { at: 4 * step, player: 2, banker: 2, note: 'player_stands' });
  const p = playerCards;
  f.push(bankerCards === 3 ? { at: 7 * step, player: p, banker: 3, note: 'banker_draws' } : { at: 6 * step, player: p, banker: 2, note: 'banker_stands' });
  return f;
}

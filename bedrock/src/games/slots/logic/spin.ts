/**
 * One complete spin: draw (with the §14 streak re-draw supplied by the caller), evaluate,
 * feed and pay the progressive jackpot. PURE.
 */
import type { Rng } from '../../../core/logic/rng';
import { type SlotTable, type SpinEval, type Tier, TIER_LINES, drawGrid, evaluateGrid, weightEntries } from './engine';
import { type PoolState, contribute, payJackpot } from './jackpot';

/** Same shape as `ctx.odds.draw` bound to a player and RTP (tests pass drawWithReroll). */
export type StreakDraw = <T>(drawFn: () => T, isLosing: (x: T) => boolean) => { result: T; rerolled: boolean };

export const noStreak: StreakDraw = (drawFn) => ({ result: drawFn(), rerolled: false });

export interface JackpotParams {
  state: PoolState;
  /** contribution rate (0.01 = 1 % of every spin bet) */
  rate: number;
  seed: number;
  /** machine maximum spin bet (max line bet × lines) */
  maxSpinBet: number;
}

export interface SpinOutcome extends SpinEval {
  lineBet: number;
  spinBet: number;
  /** jackpot award credited with this spin (0 if none) */
  jackpotAward: number;
  /** base payout + jackpot award (what wagers.settle receives) */
  totalReturn: number;
  rerolled: boolean;
  /** new pool state (undefined for machines without a progressive) */
  pool?: PoolState;
  /** chips minted by the bank to restore the seed after a win */
  toppedUp: number;
}

export function resolveSpin(o: { table: SlotTable; lineBet: number; rng: Rng; draw?: StreakDraw; jackpot?: JackpotParams }): SpinOutcome {
  const { table, lineBet, rng } = o;
  const spinBet = lineBet * table.lines;
  const entries = weightEntries(table);
  const draw = o.draw ?? noStreak;
  // A jackpot hit is never "losing" (its award is certainly ≥ the bet in practice).
  const { result: ev, rerolled } = draw(
    () => evaluateGrid(drawGrid(rng, table, entries), table, lineBet),
    (e) => !e.jackpotHit && e.basePayout < spinBet,
  );
  let pool: PoolState | undefined;
  let jackpotAward = 0;
  let toppedUp = 0;
  if (o.jackpot && table.progressive) {
    pool = contribute(o.jackpot.state, spinBet, o.jackpot.rate).state;
    if (ev.jackpotHit) {
      const r = payJackpot(pool, spinBet, o.jackpot.maxSpinBet, o.jackpot.seed);
      pool = r.state;
      jackpotAward = r.award;
      toppedUp = r.toppedUp;
    }
  }
  return { ...ev, lineBet, spinBet, jackpotAward, totalReturn: ev.basePayout + jackpotAward, rerolled, pool, toppedUp };
}

/** Line-bet limits of a machine (§8.2–8.4, CONFIG.md slots.*). */
export interface LineBetConfig {
  minLineBet: number;
  maxLineBet: number;
}

export const DEFAULT_LINE_BETS: Record<Tier, LineBetConfig> = {
  copper: { minLineBet: 1, maxLineBet: 50 },
  gold: { minLineBet: 1, maxLineBet: 100 },
  netherite: { minLineBet: 2, maxLineBet: 500 },
};

/**
 * Effective line-bet range for a player: [min, min(maxLineBet, floor(tierMax / lines))].
 * `max < min` means the player's VIP max is too low for this machine.
 */
export function lineBetRange(tier: Tier, cfg: LineBetConfig, tierMax: number, lines = TIER_LINES[tier]): { min: number; max: number } {
  const min = Math.max(1, Math.floor(cfg.minLineBet));
  const max = Math.min(Math.floor(cfg.maxLineBet), Math.floor(tierMax / lines));
  return { min, max };
}

/** Machine maximum spin bet (jackpot share denominator). */
export const machineMaxSpinBet = (tier: Tier, cfg: LineBetConfig, lines = TIER_LINES[tier]): number => Math.max(1, Math.floor(cfg.maxLineBet)) * lines;

/** Clamp a remembered line bet into the current range (min when nothing fits). */
export function clampLineBet(v: number | undefined, r: { min: number; max: number }): number {
  if (r.max < r.min) return r.min;
  const x = typeof v === 'number' && Number.isFinite(v) ? Math.floor(v) : r.min;
  return Math.max(r.min, Math.min(r.max, x));
}

/** Auto-spin stops on a single spin returning ≥ 20 × the spin bet (UI.md §6). */
export const AUTO_BIG_WIN_MULTIPLE = 20;
export const AUTO_SPINS = 10;

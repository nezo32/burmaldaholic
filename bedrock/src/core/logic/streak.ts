/**
 * Lucky / Unlucky streak (GAME_DESIGN.md §14). PURE.
 *
 * S ∈ [−max, +max] per player. Win: S = max(S,0)+1; loss: S = min(S,0)−1; push unchanged;
 * S decays 1 step toward 0 per `decayTicks` of world time without a settled wager.
 * Effect (RNG games only): a LOSING outcome is re-drawn once with probability
 *   r = min(r_raw, r_cap),  r_raw = S>0 ? lucky×S : S<0 ? pity×|S| : 0,
 *   r_cap = max(0, (1 − minHouseEdge) / RTP_game − 1)
 * so RTP' ≤ RTP·(1+r) ≤ 1 − minHouseEdge: the house edge never drops below minHouseEdge.
 */
import type { Rng } from './rng';

export interface StreakConfig {
  /** streak.enabled: false = still tracked (HUD, achievements) but no odds effect. */
  enabled: boolean;
  max: number;
  luckyPerStep: number;
  pityPerStep: number;
  minHouseEdge: number;
  /** 0 = no decay */
  decayTicks: number;
}

export const DEFAULT_STREAK: StreakConfig = { enabled: true, max: 10, luckyPerStep: 0.005, pityPerStep: 0.003, minHouseEdge: 0.01, decayTicks: 12000 };

export interface StreakState {
  /** current streak value */
  s: number;
  /** absolute world tick of the last settled wager (or of the last applied decay step) */
  t: number;
}

export type RoundOutcome = 'win' | 'loss' | 'push';

/** Outcome of a settled wager from its net result (net = total return − stake). */
export const outcomeOfNet = (net: number): RoundOutcome => (net > 0 ? 'win' : net < 0 ? 'loss' : 'push');

/** Apply decay up to `now` (whole steps only; the remainder keeps counting). */
export function decay(st: StreakState, now: number, cfg: Pick<StreakConfig, 'decayTicks'>): StreakState {
  if (cfg.decayTicks <= 0 || st.s === 0 || now <= st.t) return st.s === 0 ? { s: 0, t: Math.max(st.t, now) } : st;
  const steps = Math.floor((now - st.t) / cfg.decayTicks);
  if (steps <= 0) return st;
  const mag = Math.max(0, Math.abs(st.s) - steps);
  return { s: mag === 0 ? 0 : Math.sign(st.s) * mag, t: mag === 0 ? now : st.t + steps * cfg.decayTicks };
}

/** Record a settled wager (stake ≥ 1) at world tick `now`. */
export function record(st: StreakState, outcome: RoundOutcome, now: number, cfg: Pick<StreakConfig, 'max' | 'decayTicks'>): StreakState {
  const cur = decay(st, now, cfg).s;
  let s = cur;
  if (outcome === 'win') s = Math.min(cfg.max, Math.max(cur, 0) + 1);
  else if (outcome === 'loss') s = Math.max(-cfg.max, Math.min(cur, 0) - 1);
  return { s, t: now };
}

/** r_cap for a game with the given RTP (fraction, e.g. 0.98). */
export function rerollCap(rtp: number, minHouseEdge: number): number {
  if (!(rtp > 0)) return 0;
  return Math.max(0, (1 - Math.max(0.005, minHouseEdge)) / rtp - 1);
}

/** Probability to re-draw a losing outcome for streak `s` in a game with `rtp`. */
export function rerollChance(s: number, rtp: number, cfg: StreakConfig): number {
  if (!cfg.enabled) return 0;
  const raw = s > 0 ? cfg.luckyPerStep * s : s < 0 ? cfg.pityPerStep * -s : 0;
  return Math.min(raw, rerollCap(rtp, cfg.minHouseEdge));
}

/**
 * Draw an outcome, applying the streak re-draw: if `isLosing(first)` and a roll < r, the
 * outcome is drawn once more and the second draw is final (even if it also loses).
 */
export function drawWithReroll<T>(rng: Rng, r: number, draw: () => T, isLosing: (x: T) => boolean): { result: T; rerolled: boolean } {
  const first = draw();
  if (r > 0 && isLosing(first) && rng.next() < r) return { result: draw(), rerolled: true };
  return { result: first, rerolled: false };
}

/**
 * Chat announcement for a streak change (STRINGS.md §streak), or undefined.
 * Reaching ±5 / ±10, and breaking a streak of |S| ≥ 5 when the sign flips / resets.
 */
export function streakMessageKey(prev: number, next: number): string | undefined {
  if (next === prev) return undefined;
  if (next > 0 && next > prev && (next === 5 || next === 10)) return `msg.burmaldaholic.streak.lucky_${next}`;
  if (next < 0 && next < prev && (next === -5 || next === -10)) return `msg.burmaldaholic.streak.unlucky_${-next}`;
  if (prev >= 5 && next <= 0) return 'msg.burmaldaholic.streak.broken_lucky';
  if (prev <= -5 && next >= 0) return 'msg.burmaldaholic.streak.broken_unlucky';
  return undefined;
}

/** Game RTP constants (§17) used for r_cap. Slots include the jackpot contribution. */
export const GAME_RTP = {
  slots_copper: 0.8976,
  slots_gold: 0.9371,
  slots_netherite: 0.9604,
  coin_flip: 0.98,
  wheel: 51.5 / 54,
  scratch_basic: 0.795,
  scratch_gold: 0.85,
  plinko_low: 0.9656,
  plinko_medium: 0.9657,
  plinko_high: 0.967,
} as const;

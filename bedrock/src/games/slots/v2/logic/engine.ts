/**
 * Slots v2 engine entry points (SKELETON; lanes S-B1…S-B3 implement them against the shared vectors
 * `test/fx/vectors/slots_engine.json` and SLOTS.md §7.5). PURE.
 */
import type { MachineDef, SlotRng, SpinTape, TumbleChain, Window, WaysResult } from './types';

const todo = (what: string): never => {
  throw new Error(`slots v2 ${what}: not implemented yet (see docs/architecture/animation.md §9)`);
};

/** 243-ways evaluation (SLOTS.md §1.1); `stickyMask` bits 0–2 force reels 2–4 to WWW. Lane S-B1. */
export function evaluateWays(_def: MachineDef, _w: Window, _stickyMask = 0): WaysResult {
  return todo('ways evaluator');
}

/** Nether tumble chain (SLOTS.md §3.2). Lane S-B1. */
export function runTumbles(_def: MachineDef, _stops: readonly number[], _ladder: readonly number[]): TumbleChain {
  return todo('tumble chain');
}

export interface DrawRequest {
  def: MachineDef;
  bet: number;
  buy: boolean;
  owned: boolean;
  /** pool values per tier 1..4 (index 0 unused) */
  pools: number[];
}

/** CONFIRM → DRAW TAPE (SLOTS.md §1.2). Lane S-B2. */
export function drawSpin(_req: DrawRequest, _rng: SlotRng): SpinTape {
  return todo('draw');
}

/** Owned-casino reservation per spin (SLOTS.md §8.6): cap × bet. */
export const reservation = (def: MachineDef, bet: number): number => def.capMultiple * bet;

/** Weighted pick with integer weights (Σ ≤ 2^31 − 1). */
export function weighted(rng: SlotRng, weights: readonly number[]): number {
  let total = 0;
  for (const w of weights) total += w;
  let r = rng.nextInt(total);
  for (let i = 0; i < weights.length; i++) {
    r -= weights[i]!;
    if (r < 0) return i;
  }
  return weights.length - 1;
}

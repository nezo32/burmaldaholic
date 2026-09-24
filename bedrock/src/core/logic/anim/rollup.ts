/** Roll-up maths (global.md §2.6, SLOTS.md §10.1); twin of Java `core.anim.RollUp`. */
import { ease } from './ease';

export function rollUpDurationMs(ret: number, stake: number, minMs: number, maxMs: number): number {
  const ratio = stake <= 0 ? 0 : ret / stake;
  const d = Math.floor(600 + 900 * Math.log10(1 + Math.max(0, ratio)) + 1e-9);
  return Math.max(minMs, Math.min(maxMs, d));
}

/** floor(total × outCubic(t)), exact at t ≥ 1; monotonic. */
export function rollUpValue(total: number, t: number): number {
  if (t >= 1) return total;
  if (t <= 0) return 0;
  return Math.floor(total * ease('outCubic', t));
}

export const tickPitch = (n: number): number => Math.min(1.4, Math.pow(1.01, n));

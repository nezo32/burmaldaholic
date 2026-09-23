/**
 * Plinko (GAME_DESIGN §11.4). PURE.
 * 12 rows of pegs: the ball makes 12 independent left/right choices (p = ½ each); the bin is
 * the number of rights, so bin ~ Binomial(12, ½). Payout = floor(bet × multiplier[bin]).
 */
import { type Rng } from '../../../core/logic/rng';
import { floorPay } from './payout';

export const PLINKO_ROWS = 12;
export const PLINKO_BINS = PLINKO_ROWS + 1;
export type PlinkoRisk = 'low' | 'medium' | 'high';
export const PLINKO_RISKS: readonly PlinkoRisk[] = ['low', 'medium', 'high'];

export const DEFAULT_PLINKO: Readonly<Record<PlinkoRisk, readonly number[]>> = {
  low: [10, 3, 1.6, 1.4, 1.0, 1.0, 0.5, 1.0, 1.0, 1.4, 1.6, 3, 10],
  medium: [33, 11, 4, 2, 1.0, 0.6, 0.3, 0.6, 1.0, 2, 4, 11, 33],
  high: [170, 24, 8.1, 2, 0.6, 0.2, 0.2, 0.2, 0.6, 2, 8.1, 24, 170],
};

/** Binomial(12, ½) weights ×4096. */
export const BIN_WEIGHTS: readonly number[] = (() => {
  const w: number[] = [1];
  for (let k = 1; k <= PLINKO_ROWS; k++) w.push(((w[k - 1] as number) * (PLINKO_ROWS - k + 1)) / k);
  return w;
})();

/** Config list → 13 multipliers; anything malformed falls back to the default for that risk. */
export function plinkoTable(value: unknown, risk: PlinkoRisk): readonly number[] {
  if (Array.isArray(value) && value.length === PLINKO_BINS && value.every((v) => typeof v === 'number' && Number.isFinite(v) && v >= 0)) {
    return value as number[];
  }
  return DEFAULT_PLINKO[risk];
}

export interface PlinkoDrop {
  /** false = left, true = right, one per row (the exact animated path) */
  path: boolean[];
  bin: number;
  multiplier: number;
}

export function dropBall(rng: Rng, table: readonly number[]): PlinkoDrop {
  const path: boolean[] = [];
  for (let i = 0; i < PLINKO_ROWS; i++) path.push(rng.next() < 0.5);
  const bin = path.filter(Boolean).length;
  return { path, bin, multiplier: table[bin] ?? 0 };
}

export const plinkoReturn = (bet: number, drop: Pick<PlinkoDrop, 'multiplier'>): number => floorPay(bet, drop.multiplier);

/** Exact RTP (before flooring) by enumeration of the 4096 paths. */
export function plinkoRtp(table: readonly number[]): number {
  return table.reduce((s, m, i) => s + m * (BIN_WEIGHTS[i] ?? 0), 0) / 2 ** PLINKO_ROWS;
}

export const maxPlinkoMultiplier = (table: readonly number[]): number => Math.max(...table);

/** Position of the ball after each row (0 = far left … row+1). */
export function pathPositions(path: readonly boolean[]): number[] {
  let x = 0;
  return path.map((r) => (x += r ? 1 : 0));
}

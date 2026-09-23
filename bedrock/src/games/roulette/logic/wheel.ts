/**
 * European single-zero wheel (GAME_DESIGN §9). PURE.
 */
import { type Rng, randInt } from '../../../core/logic/rng';

export const RED_NUMBERS: ReadonlySet<number> = new Set([1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36]);

/** Pocket order clockwise, starting at 0 (for the spin animation). */
export const WHEEL_ORDER: readonly number[] = [
  0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26,
];

export type PocketColor = 'red' | 'black' | 'green';

export const isPocket = (n: number): boolean => Number.isInteger(n) && n >= 0 && n <= 36;

export function pocketColor(n: number): PocketColor {
  if (n === 0) return 'green';
  return RED_NUMBERS.has(n) ? 'red' : 'black';
}

/** Index of a pocket in WHEEL_ORDER. */
export function wheelIndex(n: number): number {
  const i = WHEEL_ORDER.indexOf(n);
  if (i < 0) throw new Error(`not a pocket: ${n}`);
  return i;
}

/** Uniform draw of a pocket 0..36 (table games are honest: no streak re-draw). */
export const spinWheel = (rng: Rng): number => randInt(rng, 0, 36);

/** Layout position of 1..36: column 0..2 (1st/2nd/3rd column), row 0..11 (1-2-3 is row 0). */
export const layoutCol = (n: number): number => (n - 1) % 3;
export const layoutRow = (n: number): number => Math.floor((n - 1) / 3);

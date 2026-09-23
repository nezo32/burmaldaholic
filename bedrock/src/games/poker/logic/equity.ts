/**
 * Chen hand score and Monte-Carlo equity for the bots (GAME_DESIGN §7.4). PURE.
 */
import { type Rng } from '../../../core/logic/rng';
import { FULL_DECK, type PCard, rankOf, suitOf } from './cards';
import { evaluate } from './evaluator';

const HIGH_POINTS: Record<number, number> = { 14: 10, 13: 8, 12: 7, 11: 6 };

/** Bill Chen's formula, rounded up to an integer. */
export function chen(a: PCard, b: PCard): number {
  const ra = rankOf(a);
  const rb = rankOf(b);
  const hi = Math.max(ra, rb);
  const lo = Math.min(ra, rb);
  let score = HIGH_POINTS[hi] ?? hi / 2;
  if (hi === lo) return Math.ceil(Math.max(5, score * 2));
  if (suitOf(a) === suitOf(b)) score += 2;
  const gap = hi - lo - 1;
  score -= gap === 0 ? 0 : gap === 1 ? 1 : gap === 2 ? 2 : gap === 3 ? 4 : 5;
  if (gap <= 1 && hi < 12) score += 1;
  return Math.ceil(score);
}

/** Minimum Chen score of the top `fraction` of all 1326 starting hands. */
export function chenThreshold(fraction: number): number {
  const scores: number[] = [];
  for (let i = 0; i < 52; i++) for (let j = i + 1; j < 52; j++) scores.push(chen(i, j));
  scores.sort((x, y) => y - x);
  const k = Math.max(0, Math.min(scores.length - 1, Math.ceil(scores.length * fraction) - 1));
  return scores[k]!;
}

let top40: number | undefined;
/** Chen score cut-off for the "top 40 %" range sharks assume for preflop raisers. */
export const TOP40_CHEN = (): number => (top40 ??= chenThreshold(0.4));

export interface EquityInput {
  hole: readonly PCard[];
  board: readonly PCard[];
  /** number of opponents still in the hand */
  opponents: number;
  samples: number;
  /** per-opponent minimum Chen score (undefined = any two cards) */
  ranges?: readonly (number | undefined)[];
}

/**
 * Monte-Carlo equity (share of the pot won vs random opponent hands, board completed at
 * random). A generator so the game can spread the work over ticks (`system.runJob`); it yields
 * every `yieldEvery` samples and returns the equity in [0, 1].
 */
export function* equityJob(input: EquityInput, rng: Rng, yieldEvery = 25): Generator<void, number, void> {
  const known = new Set<PCard>([...input.hole, ...input.board]);
  const pool = FULL_DECK.filter((c) => !known.has(c));
  const opp = Math.max(0, Math.floor(input.opponents));
  const need = 5 - input.board.length;
  const samples = Math.max(1, Math.floor(input.samples));
  if (opp === 0) return 1;
  let total = 0;
  const arr = pool.slice();
  const hero = [...input.hole, ...input.board];
  for (let it = 0; it < samples; it++) {
    let k = 0;
    const take = (): PCard => {
      const j = k + Math.floor(rng.next() * (arr.length - k));
      const tmp = arr[k]!;
      arr[k] = arr[j]!;
      arr[j] = tmp;
      return arr[k++]!;
    };
    const oppHands: PCard[][] = [];
    for (let o = 0; o < opp; o++) {
      const min = input.ranges?.[o];
      if (min === undefined) {
        oppHands.push([take(), take()]);
        continue;
      }
      // rejection-sample a hand inside the range (give up after a few tries)
      let hand: PCard[] | undefined;
      for (let tries = 0; tries < 25 && !hand; tries++) {
        const i1 = k + Math.floor(rng.next() * (arr.length - k));
        let i2 = k + Math.floor(rng.next() * (arr.length - k - 1));
        if (i2 >= i1) i2++;
        if (chen(arr[i1]!, arr[i2]!) >= min || tries === 24) {
          const c1 = arr[i1]!;
          const c2 = arr[i2]!;
          // move both to the front of the undealt area
          swapTo(arr, k, arr.indexOf(c1, k));
          swapTo(arr, k + 1, arr.indexOf(c2, k));
          k += 2;
          hand = [c1, c2];
        }
      }
      oppHands.push(hand!);
    }
    const extra: PCard[] = [];
    for (let b = 0; b < need; b++) extra.push(take());
    const heroVal = evaluate([...hero, ...extra]);
    let ties = 0;
    let lost = false;
    for (const h of oppHands) {
      const v = evaluate([...h, ...input.board, ...extra]);
      if (v > heroVal) {
        lost = true;
        break;
      }
      if (v === heroVal) ties++;
    }
    if (!lost) total += 1 / (ties + 1);
    if ((it + 1) % yieldEvery === 0) yield;
  }
  return total / samples;
}

function swapTo(arr: PCard[], to: number, from: number): void {
  const tmp = arr[to]!;
  arr[to] = arr[from]!;
  arr[from] = tmp;
}

/** Run an equity job to completion synchronously (tests, small sample counts). */
export function equity(input: EquityInput, rng: Rng): number {
  const g = equityJob(input, rng, Number.MAX_SAFE_INTEGER);
  for (;;) {
    const r = g.next();
    if (r.done) return r.value;
  }
}

/**
 * Exact Trips edge (GAME_DESIGN §21.3, §21.8). PURE.
 *
 * `countSevenCardHands()` classifies all C(52,7) = 133 784 560 seven-card hands with the shared
 * evaluator without visiting each one: it walks every rank multiset (≤ 4 per rank), counts the
 * suit assignments that are flushes (exactly one suit holds a given subset F of ≥ 5 distinct
 * ranks; two flushes are impossible with 7 cards) and the rest, and evaluates one representative
 * hand per class. A flush class is decided by F alone (full house / quads need ≤ 4 distinct ranks
 * and cannot coexist with 5 suited distinct ranks); a non-flush class by the ranks alone.
 *
 * Runtime code uses the verified constant `SEVEN_CARD_COUNTS` (integer arithmetic, instant).
 */
import { type HandName, HAND_CATEGORIES, evaluate, handName } from '../../poker/logic/evaluator';
import { type PCard, makeCard } from '../../poker/logic/cards';
import { PAY_HANDS, PAY_HAND_NAME, type Paytable } from './paytable';

export const TOTAL_SEVEN_CARD_HANDS = 133_784_560;

/** §21.3 category counts of all 7-card hands (royal separate from straight flush). */
export const SEVEN_CARD_COUNTS: Readonly<Record<HandName, number>> = {
  royal_flush: 4_324,
  straight_flush: 37_260,
  four_of_a_kind: 224_848,
  full_house: 3_473_184,
  flush: 4_047_644,
  straight: 6_180_020,
  three_of_a_kind: 6_461_620,
  two_pair: 31_433_400,
  pair: 58_627_800,
  high_card: 23_294_460,
};

const C = (n: number, k: number): number => {
  if (k < 0 || k > n) return 0;
  let r = 1;
  for (let i = 0; i < k; i++) r = (r * (n - i)) / (i + 1);
  return Math.round(r);
};

/** Exact category counts of every 7-card hand, computed with `evaluate`. */
export function countSevenCardHands(): Record<HandName, number> {
  const out = Object.fromEntries([...HAND_CATEGORIES, 'royal_flush'].map((h) => [h, 0])) as Record<HandName, number>;
  const counts = new Array<number>(13).fill(0);
  const visit = (): void => {
    let totalWays = 1;
    for (const n of counts) totalWays *= C(4, n);
    const present: number[] = [];
    counts.forEach((n, r) => {
      if (n > 0) present.push(r);
    });
    // Flush classes: suit 0 holds exactly the ranks F (|F| ≥ 5), ×4 suits.
    let flushWays = 0;
    if (present.length >= 5) {
      const m = present.length;
      for (let mask = 0; mask < 1 << m; mask++) {
        let bits = 0;
        for (let x = mask; x; x &= x - 1) bits++;
        if (bits < 5) continue;
        let ways = 4;
        const cards: PCard[] = [];
        for (let k = 0; k < m; k++) {
          const r = present[k]!;
          const n = counts[r]!;
          const inF = (mask >> k) & 1;
          ways *= inF ? C(3, n - 1) : C(3, n);
          if (inF) cards.push(makeCard(r + 2, 0));
          // the other copies in suits 1..3 (their exact suits do not change the class)
          for (let c = 0; c < n - inF; c++) cards.push(makeCard(r + 2, 1 + c));
        }
        if (!ways) continue;
        flushWays += ways;
        out[handName(evaluate(cards))] += ways;
      }
    }
    const rest = totalWays - flushWays;
    if (rest > 0) {
      // Representative without a flush: deal the cards round-robin over the 4 suits.
      const cards: PCard[] = [];
      let p = 0;
      counts.forEach((n, r) => {
        for (let c = 0; c < n; c++) cards.push(makeCard(r + 2, (p + c) % 4));
        p += n;
      });
      out[handName(evaluate(cards))] += rest;
    }
  };
  const rec = (r: number, left: number): void => {
    if (r === 13) {
      if (left === 0) visit();
      return;
    }
    for (let n = 0; n <= Math.min(4, left); n++) {
      counts[r] = n;
      rec(r + 1, left - n);
    }
    counts[r] = 0;
  };
  rec(0, 7);
  return out;
}

/**
 * Trips expected value per chip as an exact fraction: numerator over TOTAL (−2 547 324 /
 * 133 784 560 for 50-40-30-8-6-5-3). Integer pays give an integer numerator.
 */
export function tripsEvNumerator(pays: Readonly<Paytable>, counts: Readonly<Record<HandName, number>> = SEVEN_CARD_COUNTS): number {
  let ev = 0;
  let paid = 0;
  for (const h of PAY_HANDS) {
    const m = pays[h];
    const n = counts[PAY_HAND_NAME[h]];
    if (m === undefined || m <= 0) continue;
    ev += n * m;
    paid += n;
  }
  const total = Object.values(counts).reduce((s, x) => s + x, 0);
  return ev - (total - paid);
}

/** Trips house edge as a fraction (0.019040… for the default table). */
export const tripsEdge = (pays: Readonly<Paytable>): number => -tripsEvNumerator(pays) / TOTAL_SEVEN_CARD_HANDS;

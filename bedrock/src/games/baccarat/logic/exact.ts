/**
 * Exact coup probabilities by enumeration over value classes (GAME_DESIGN §20.8). PURE.
 *
 * 10 value classes: value 0 has 16 × decks cards (10, J, Q, K), A…9 have 4 × decks each. Loop
 * P1, B1, P2, B2 (weight = product of the remaining class counts), apply the §20.3 drawing
 * rules for the third cards, then extend every k-card sequence by (N − k)(N − k − 1)…(N − 5) so
 * all leaves share the denominator N!/(N − 6)!. All figures stay below 2^53 for ≤ 8 decks, so
 * plain numbers are exact.
 */
import { bankerDraws, playerDraws } from './rules';

export interface ExactCounts {
  banker: number;
  player: number;
  tie: number;
  /** N (N − 1) … (N − 5) */
  total: number;
  /** probability of a pair on one given side: (4·decks − 1)/(N − 1) */
  pair: number;
}

export function exactCounts(decks: number): ExactCounts {
  const N = 52 * decks;
  const count = [16 * decks, 4 * decks, 4 * decks, 4 * decks, 4 * decks, 4 * decks, 4 * decks, 4 * decks, 4 * decks, 4 * decks];
  // ext[k] = (N − k)(N − k − 1)…(N − 5): the weight of the unused tail of a k-card sequence.
  const ext = (k: number): number => {
    let w = 1;
    for (let i = k; i <= 5; i++) w *= N - i;
    return w;
  };
  const out = { banker: 0, player: 0, tie: 0 };
  const add = (p: number, b: number, w: number) => {
    if (p > b) out.player += w;
    else if (b > p) out.banker += w;
    else out.tie += w;
  };
  for (let p1 = 0; p1 < 10; p1++) {
    const w1 = count[p1]!;
    count[p1]!--;
    for (let b1 = 0; b1 < 10; b1++) {
      const w2 = w1 * count[b1]!;
      count[b1]!--;
      for (let p2 = 0; p2 < 10; p2++) {
        const w3 = w2 * count[p2]!;
        count[p2]!--;
        for (let b2 = 0; b2 < 10; b2++) {
          const w4 = w3 * count[b2]!;
          if (w4 === 0) continue;
          count[b2]!--;
          const p = (p1 + p2) % 10;
          const b = (b1 + b2) % 10;
          if (p >= 8 || b >= 8) add(p, b, w4 * ext(4));
          else if (!playerDraws(p)) {
            // Player stands on 6–7; Banker plays 0–5 draws.
            if (bankerDraws(b, undefined)) {
              for (let b3 = 0; b3 < 10; b3++) {
                const w5 = w4 * count[b3]!;
                if (w5) add(p, (b + b3) % 10, w5 * ext(5));
              }
            } else add(p, b, w4 * ext(4));
          } else {
            for (let p3 = 0; p3 < 10; p3++) {
              const w5 = w4 * count[p3]!;
              if (!w5) continue;
              count[p3]!--;
              const pf = (p + p3) % 10;
              if (bankerDraws(b, p3)) {
                for (let b3 = 0; b3 < 10; b3++) {
                  const w6 = w5 * count[b3]!;
                  if (w6) add(pf, (b + b3) % 10, w6);
                }
              } else add(pf, b, w5 * ext(5));
              count[p3]!++;
            }
          }
          count[b2]!++;
        }
        count[p2]!++;
      }
      count[b1]!++;
    }
    count[p1]!++;
  }
  return { ...out, total: ext(0), pair: (4 * decks - 1) / (N - 1) };
}

export interface Edges {
  pBanker: number;
  pPlayer: number;
  pTie: number;
  /** house edge per chip bet (positive = house advantage) */
  banker: number;
  player: number;
  tie: number;
  pair: number;
}

/** House edges of every bet from exact counts (§20.2 table). */
export function exactEdges(decks: number, commission: number, tiePays: number, pairPays: number): Edges {
  const c = exactCounts(decks);
  const pB = c.banker / c.total;
  const pP = c.player / c.total;
  const pT = c.tie / c.total;
  return {
    pBanker: pB,
    pPlayer: pP,
    pTie: pT,
    banker: -((1 - commission) * pB - pP),
    player: -(pP - pB),
    tie: -((tiePays + 1) * pT - 1),
    pair: -((pairPays + 1) * c.pair - 1),
  };
}

/**
 * House-edge measurement of the base game (Ante + Blind + Play) under reference strategy R
 * (GAME_DESIGN §21.3 / §21.8). PURE; used by the tests.
 *
 * Monte-Carlo over boards with exact inner sums: for a random 5-card board every one of the
 * 1 081 hole pairs from the other 47 cards is played with strategy R against every one of the
 * dealer's 990 hole pairs from the remaining 45 cards. That is exactly the joint distribution
 * (board, hole, dealer) of a round, but each board costs ~2 000 evaluations instead of
 * 1 081 × 990, and the dealer / hole-card variance disappears (only the board is sampled).
 * The river decision of R uses the same exact dealer counts it would enumerate.
 */
import { evaluate } from '../../poker/logic/evaluator';
import { FULL_DECK, type PCard, shuffledDeck } from '../../poker/logic/cards';
import type { Rng } from '../../../core/logic/rng';
import { type Paytable, blindMultiplier } from './paytable';
import { flopBet2, preflopBet4 } from './strategy';

/** Dealer qualifies iff value ≥ this (category pair = 1, packed as category << 20). */
const QUALIFY = 1 << 20;

export interface EdgeStats {
  /** player hands played (each weighs 1) */
  hands: number;
  /** Σ result in Antes (negative = the house wins) */
  sumResult: number;
  /** Σ chips wagered in Antes (2 + Play) */
  sumWagered: number;
  bet4: number;
  bet2: number;
  bet1: number;
  fold: number;
  /** per-board mean results (for the standard error) */
  boardMeans: number[];
}

export const emptyStats = (): EdgeStats => ({ hands: 0, sumResult: 0, sumWagered: 0, bet4: 0, bet2: 0, bet1: 0, fold: 0, boardMeans: [] });

function lowerBound(a: Int32Array | number[], x: number): number {
  let lo = 0;
  let hi = a.length;
  while (lo < hi) {
    const m = (lo + hi) >> 1;
    if (a[m]! < x) lo = m + 1;
    else hi = m;
  }
  return lo;
}

/** Add one board's exact results to `st`. */
export function addBoard(st: EdgeStats, board: readonly PCard[], blindPays: Readonly<Paytable>): void {
  const inBoard = new Set(board);
  const rest = FULL_DECK.filter((c) => !inBoard.has(c));
  const n = rest.length; // 47
  // Dealer value for every pair of the 47 cards.
  const dv: number[][] = rest.map(() => new Array<number>(n).fill(0));
  const all: number[] = [];
  const b0 = board[0]!;
  const b1 = board[1]!;
  const b2 = board[2]!;
  const b3 = board[3]!;
  const b4 = board[4]!;
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      const v = evaluate([rest[i]!, rest[j]!, b0, b1, b2, b3, b4]);
      dv[i]![j] = v;
      dv[j]![i] = v;
      all.push(v);
    }
  }
  const sorted = Int32Array.from(all).sort();
  const total = sorted.length; // 1081
  const lowerQ = lowerBound(sorted, QUALIFY);
  const flop = board.slice(0, 3);
  let boardSum = 0;
  let boardHands = 0;
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      const hole = [rest[i]!, rest[j]!];
      const pv = dv[i]![j]!; // the seat plays hole + board: same 7 cards as a "dealer" with these hole cards
      // Dealer outcome counts over the 1081 pairs, then remove the 91 pairs using i or j.
      const lo = lowerBound(sorted, pv);
      const up = lowerBound(sorted, pv + 1);
      let winNQ = pv <= QUALIFY ? lo : lowerQ;
      let winQ = pv > QUALIFY ? lo - lowerQ : 0;
      let tie = up - lo;
      let loseNQ = pv < QUALIFY ? lowerQ - up : 0;
      let loseQ = total - Math.max(up, lowerQ);
      const remove = (x: number): void => {
        if (x < pv) {
          if (x >= QUALIFY) winQ--;
          else winNQ--;
        } else if (x > pv) {
          if (x >= QUALIFY) loseQ--;
          else loseNQ--;
        } else tie--;
      };
      const di = dv[i]!;
      const dj = dv[j]!;
      for (let k = 0; k < n; k++) {
        if (k === i || k === j) continue;
        remove(di[k]!);
        remove(dj[k]!);
      }
      // the pair (i, j) itself
      tie--;
      const dealers = winQ + winNQ + loseQ + loseNQ + tie; // 990
      const bp = blindMultiplier(blindPays, pv);
      const ev = (m: number): number => (winQ * (m + 1 + bp) + winNQ * (m + bp) - loseQ * (m + 2) - loseNQ * (m + 1)) / dealers;
      let result: number;
      let wagered: number;
      if (preflopBet4(hole)) {
        result = ev(4);
        wagered = 6;
        st.bet4++;
      } else if (flopBet2(hole, flop)) {
        result = ev(2);
        wagered = 4;
        st.bet2++;
      } else {
        const r1 = ev(1);
        if (r1 > -2) {
          result = r1;
          wagered = 3;
          st.bet1++;
        } else {
          result = -2;
          wagered = 2;
          st.fold++;
        }
      }
      st.hands++;
      st.sumResult += result;
      st.sumWagered += wagered;
      boardSum += result;
      boardHands++;
    }
  }
  st.boardMeans.push(boardSum / boardHands);
}

/** Run `boards` random boards. */
export function measureEdge(rng: Rng, boards: number, blindPays: Readonly<Paytable>): EdgeStats {
  const st = emptyStats();
  for (let b = 0; b < boards; b++) addBoard(st, shuffledDeck(rng).slice(0, 5), blindPays);
  return st;
}

/** House edge (fraction of the Ante) and its standard error from the per-board means. */
export function edgeOf(st: EdgeStats): { edge: number; se: number; elementOfRisk: number } {
  const m = st.boardMeans;
  const mean = m.reduce((s, x) => s + x, 0) / m.length;
  const varB = m.reduce((s, x) => s + (x - mean) ** 2, 0) / Math.max(1, m.length - 1);
  return { edge: -st.sumResult / st.hands, se: Math.sqrt(varB / m.length), elementOfRisk: -st.sumResult / st.sumWagered };
}

/**
 * Basic strategy for multi-deck, dealer stands on soft 17, double after split, no surrender
 * (the §6.1 defaults). PURE. Used by the RTP Monte-Carlo test and as the "hint" for players.
 */
import type { Card } from '../../../core/logic/cards';
import { isPair, rankValue, valueOf } from './hand';
import type { Action } from './round';

/** Dealer up-card value 2..11 (ace = 11). */
const upValue = (c: Card): number => rankValue(c.rank);

type Want = 'H' | 'S' | 'D' | 'Ds' | 'P';

function pairWant(rank: Card['rank'], up: number): Want | undefined {
  const v = rankValue(rank);
  if (rank === 'A' || v === 8) return 'P';
  if (v === 10 || v === 5) return undefined; // play as hard 20 / 10
  if (v === 9) return up <= 6 || up === 8 || up === 9 ? 'P' : 'S';
  if (v === 7) return up <= 7 ? 'P' : undefined;
  if (v === 6) return up <= 6 ? 'P' : undefined;
  if (v === 4) return up === 5 || up === 6 ? 'P' : undefined;
  return up <= 7 ? 'P' : undefined; // 2,2 and 3,3
}

function softWant(total: number, up: number): Want {
  if (total >= 19) return 'S';
  if (total === 18) return up >= 3 && up <= 6 ? 'Ds' : up <= 8 ? 'S' : 'H';
  if (total === 17) return up >= 3 && up <= 6 ? 'D' : 'H';
  if (total >= 15) return up >= 4 && up <= 6 ? 'D' : 'H';
  return up >= 5 && up <= 6 ? 'D' : 'H';
}

function hardWant(total: number, up: number): Want {
  if (total >= 17) return 'S';
  if (total >= 13) return up <= 6 ? 'S' : 'H';
  if (total === 12) return up >= 4 && up <= 6 ? 'S' : 'H';
  if (total === 11) return up <= 10 ? 'D' : 'H';
  if (total === 10) return up <= 9 ? 'D' : 'H';
  if (total === 9) return up >= 3 && up <= 6 ? 'D' : 'H';
  return 'H';
}

/** Pick the basic-strategy action among the legal ones. */
export function basicStrategy(cards: readonly Card[], dealerUp: Card, legal: readonly Action[]): Action {
  const up = upValue(dealerUp);
  if (legal.includes('split') && isPair(cards)) {
    const p = pairWant(cards[0]!.rank, up);
    if (p === 'P') return 'split';
  }
  if (!legal.includes('hit')) return 'stand'; // split aces
  const v = valueOf(cards);
  const want = v.soft ? softWant(v.total, up) : hardWant(v.total, up);
  if (want === 'D') return legal.includes('double') ? 'double' : 'hit';
  if (want === 'Ds') return legal.includes('double') ? 'double' : 'stand';
  return want === 'S' ? 'stand' : 'hit';
}

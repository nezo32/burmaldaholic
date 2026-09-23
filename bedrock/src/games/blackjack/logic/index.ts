/**
 * Pure blackjack logic (no @minecraft imports). Example of the pattern; the blackjack
 * owner replaces/extends this following docs/design.
 */
import type { Rank } from '../../../core/logic/cards';

export interface HandValue {
  total: number;
  soft: boolean;
}

export function handValue(ranks: readonly Rank[]): HandValue {
  let total = 0;
  let aces = 0;
  for (const r of ranks) {
    if (r === 'A') {
      aces++;
      total += 11;
    } else if (r === 'J' || r === 'Q' || r === 'K') total += 10;
    else total += Number(r);
  }
  while (total > 21 && aces > 0) {
    total -= 10;
    aces--;
  }
  return { total, soft: aces > 0 };
}

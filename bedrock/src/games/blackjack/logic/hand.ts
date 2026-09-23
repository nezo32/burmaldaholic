/**
 * Hand values (GAME_DESIGN §6.2). PURE.
 * A = 1 or 11, 2–10 face value, J/Q/K = 10. Soft = an ace is counted as 11.
 */
import type { Card, Rank } from '../../../core/logic/cards';

export interface HandValue {
  total: number;
  soft: boolean;
}

export const rankValue = (r: Rank): number => (r === 'A' ? 11 : r === 'J' || r === 'Q' || r === 'K' ? 10 : Number(r));
export const isTenValue = (r: Rank): boolean => rankValue(r) === 10;

export function handValue(ranks: readonly Rank[]): HandValue {
  let total = 0;
  let aces = 0;
  for (const r of ranks) {
    if (r === 'A') aces++;
    total += rankValue(r);
  }
  while (total > 21 && aces > 0) {
    total -= 10;
    aces--;
  }
  return { total, soft: aces > 0 };
}

export const valueOf = (cards: readonly Card[]): HandValue => handValue(cards.map((c) => c.rank));

/** Natural: exactly two cards totalling 21 on an unsplit hand. */
export const isNatural = (cards: readonly Card[], fromSplit = false): boolean => !fromSplit && cards.length === 2 && valueOf(cards).total === 21;

/** Split needs two cards of the same RANK (K+K yes, K+Q no). */
export const isPair = (cards: readonly Card[]): boolean => cards.length === 2 && cards[0]!.rank === cards[1]!.rank;

/** Totals for display: soft hands below 21 show both ("7/17"), everything else one number. */
export function displayTotals(cards: readonly Card[]): { low?: number; high: number } {
  const v = valueOf(cards);
  return v.soft && v.total < 21 ? { low: v.total - 10, high: v.total } : { high: v.total };
}

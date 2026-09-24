/**
 * Reference strategy R (GAME_DESIGN §21.3). PURE. Used by the tests (house-edge Monte-Carlo)
 * only - never shown to players as advice.
 *
 * - Preflop Bet ×4 with: any pair 3-3 or better; any Ace; K-x suited, K-5+ offsuit; Q-6+ suited,
 *   Q-8+ offsuit; J-8+ suited, J-T+ offsuit. Otherwise check.
 * - Flop Bet ×2 with: a pair that uses at least one hole card (except pocket 2s); any made hand
 *   of two pair or better on the 5 known cards; four to a flush including a hole card of that
 *   suit of rank 10 or higher. Otherwise check.
 * - River: Bet ×1 iff the mean result of betting over the dealer's 990 possible hole pairs is
 *   greater than −2 Antes (the fold).
 */
import { CAT, categoryOf, evaluate } from '../../poker/logic/evaluator';
import { FULL_DECK, type PCard, rankOf, suitOf } from '../../poker/logic/cards';
import { type Paytable, blindMultiplier } from './paytable';
import { dealerQualifies } from './settle';

export function preflopBet4(hole: readonly PCard[]): boolean {
  const [a, b] = hole as [PCard, PCard];
  const hi = Math.max(rankOf(a), rankOf(b));
  const lo = Math.min(rankOf(a), rankOf(b));
  const suited = suitOf(a) === suitOf(b);
  if (hi === lo) return hi >= 3;
  if (hi === 14) return true;
  if (hi === 13) return suited || lo >= 5;
  if (hi === 12) return suited ? lo >= 6 : lo >= 8;
  if (hi === 11) return suited ? lo >= 8 : lo >= 10;
  return false;
}

export function flopBet2(hole: readonly PCard[], flop: readonly PCard[]): boolean {
  const [a, b] = hole as [PCard, PCard];
  const ra = rankOf(a);
  const rb = rankOf(b);
  const flopRanks = flop.map(rankOf);
  // A pair using at least one hole card (pocket 2s excluded).
  if (ra === rb && ra !== 2) return true;
  if (flopRanks.includes(ra) || flopRanks.includes(rb)) return true;
  // Two pair or better on the 5 known cards.
  if (categoryOf(evaluate([...hole, ...flop])) >= CAT.two_pair) return true;
  // Four to a flush with a hole card of that suit, rank 10+.
  const cards = [...hole, ...flop];
  for (let s = 0; s < 4; s++) {
    if (cards.filter((c) => suitOf(c) === s).length < 4) continue;
    if (hole.some((c) => suitOf(c) === s && rankOf(c) >= 10)) return true;
  }
  return false;
}

/** Result of a Play bet of `multiple` Antes against one dealer hand, in Antes (§21.3). */
export function betResultAntes(playerValue: number, dealerValue: number, multiple: number, blindPays: Readonly<Paytable>): number {
  const q = dealerQualifies(dealerValue) ? 1 : 0;
  if (playerValue > dealerValue) return multiple + q + blindMultiplier(blindPays, playerValue);
  if (playerValue < dealerValue) return -(multiple + q + 1);
  return 0;
}

/** Mean river result of Bet ×1 over every dealer hole pair from the unseen cards. */
export function riverBetEv(hole: readonly PCard[], board: readonly PCard[], blindPays: Readonly<Paytable>): number {
  const used = new Set<PCard>([...hole, ...board]);
  const unseen = FULL_DECK.filter((c) => !used.has(c));
  const pv = evaluate([...hole, ...board]);
  let sum = 0;
  let n = 0;
  for (let i = 0; i < unseen.length; i++) {
    for (let j = i + 1; j < unseen.length; j++) {
      sum += betResultAntes(pv, evaluate([unseen[i]!, unseen[j]!, ...board]), 1, blindPays);
      n++;
    }
  }
  return sum / n;
}

export const riverBet1 = (hole: readonly PCard[], board: readonly PCard[], blindPays: Readonly<Paytable>): boolean => riverBetEv(hole, board, blindPays) > -2;

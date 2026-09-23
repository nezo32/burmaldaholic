/**
 * Shared playing-card primitives (blackjack, poker, extras). PURE.
 * Card ids are compact strings like 'AS', '10H', 'QD' - handy for dynamic properties.
 */
import { type Rng, shuffle } from './rng';

export const SUITS = ['S', 'H', 'D', 'C'] as const;
export const RANKS = ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A'] as const;
export type Suit = (typeof SUITS)[number];
export type Rank = (typeof RANKS)[number];
export interface Card {
  rank: Rank;
  suit: Suit;
}

export const cardId = (c: Card): string => `${c.rank}${c.suit}`;

export function parseCard(id: string): Card {
  const rank = id.slice(0, -1) as Rank;
  const suit = id.slice(-1) as Suit;
  if (!RANKS.includes(rank) || !SUITS.includes(suit)) throw new Error(`bad card id ${id}`);
  return { rank, suit };
}

/** `decks` standard 52-card decks, shuffled. */
export function newShoe(rng: Rng, decks = 1): Card[] {
  const cards: Card[] = [];
  for (let d = 0; d < decks; d++) for (const suit of SUITS) for (const rank of RANKS) cards.push({ rank, suit });
  return shuffle(rng, cards);
}

/** Lang keys for card names (defined in lang/core): msg.burmaldaholic.core.rank.<R>, msg.burmaldaholic.core.suit.<S>. */
export const rankKey = (r: Rank): string => `msg.burmaldaholic.core.rank.${r}`;
export const suitKey = (s: Suit): string => `msg.burmaldaholic.core.suit.${s}`;

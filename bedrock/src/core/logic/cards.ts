/**
 * Shared playing-card primitives (blackjack, poker, extras). PURE.
 * Card ids are compact strings like 'AS', '10H', 'QD' - handy for dynamic properties.
 */
import { type Raw, lit, t } from './rawtext';
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

const SUIT_NAMES: Record<Suit, string> = { S: 'spades', H: 'hearts', D: 'diamonds', C: 'clubs' };
const FACE = new Set<Rank>(['A', 'K', 'Q', 'J', '10']);

/** Short rank for text rendering: keyed for A/K/Q/J/10 (RU: Т/К/Д/В), digits 2–9 literal. */
export const rankLabel = (r: Rank): Raw => (FACE.has(r) ? t(`gui.burmaldaholic.card.rank.${r.toLowerCase()}`) : lit(r));
/** Suit name ("Spades" / «Пики»). */
export const suitLabel = (s: Suit): Raw => t(`gui.burmaldaholic.card.suit.${SUIT_NAMES[s]}`);
/** Narration "Ace of Spades" / «Туз, пики» (digits and 10 stay numeric). */
export function cardName(c: Card): Raw {
  const name = c.rank === 'A' || c.rank === 'K' || c.rank === 'Q' || c.rank === 'J' ? t(`gui.burmaldaholic.card.name.${c.rank.toLowerCase()}`) : lit(c.rank);
  return t('gui.burmaldaholic.card.narration', name, suitLabel(c.suit));
}
/** Hidden (face-down) card text. */
export const hiddenCard = (): Raw => t('gui.burmaldaholic.card.hidden');

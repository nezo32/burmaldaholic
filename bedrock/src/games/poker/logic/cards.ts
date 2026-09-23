/**
 * Compact numeric cards for poker (PURE). A card is an int 0..51: `rankIdx * 4 + suitIdx`,
 * rankIdx 0..12 = 2..A (rank value = rankIdx + 2), suitIdx 0..3 = S H D C (core SUITS order).
 * Numbers keep the Monte-Carlo bots fast; convert to core `Card` for display.
 */
import { type Card, RANKS, SUITS } from '../../../core/logic/cards';
import { type Rng } from '../../../core/logic/rng';

export type PCard = number;

/** Rank value 2..14 (A = 14). */
export const rankOf = (c: PCard): number => (c >> 2) + 2;
/** Suit index 0..3 (S H D C). */
export const suitOf = (c: PCard): number => c & 3;
export const makeCard = (rankValue: number, suit: number): PCard => (rankValue - 2) * 4 + suit;

export function toCard(c: PCard): Card {
  return { rank: RANKS[(c >> 2)]!, suit: SUITS[c & 3]! };
}

export function fromCard(c: Card): PCard {
  return RANKS.indexOf(c.rank) * 4 + SUITS.indexOf(c.suit);
}

/** Parse 'As', 'Td', '10h', '2C' (case-insensitive suit, T or 10 for ten). Test helper. */
export function pc(id: string): PCard {
  const s = id.slice(-1).toUpperCase();
  let r = id.slice(0, -1).toUpperCase();
  if (r === 'T') r = '10';
  const ri = (RANKS as readonly string[]).indexOf(r);
  const si = (SUITS as readonly string[]).indexOf(s);
  if (ri < 0 || si < 0) throw new Error(`bad card ${id}`);
  return ri * 4 + si;
}

/** Parse a space-separated list: pcs('As Kd 7h'). */
export const pcs = (s: string): PCard[] => s.trim().split(/\s+/).filter(Boolean).map(pc);

export const FULL_DECK: readonly PCard[] = Array.from({ length: 52 }, (_, i) => i);

/** Fresh shuffled 52-card deck (Fisher-Yates). */
export function shuffledDeck(rng: Rng): PCard[] {
  const a = FULL_DECK.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rng.next() * (i + 1));
    const tmp = a[i]!;
    a[i] = a[j]!;
    a[j] = tmp;
  }
  return a;
}

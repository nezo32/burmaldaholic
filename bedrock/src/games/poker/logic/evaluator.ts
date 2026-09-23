/**
 * Texas Hold'em hand evaluator (GAME_DESIGN §7.2). PURE.
 *
 * Contract (shared with the Java edition): `evaluate(cards) -> int`, higher is better,
 * `category << 20 | ranks packed 4 bits × 5` (most significant rank first). Ranks are
 * 2..14 (A = 14); the ace in a wheel (A-2-3-4-5) is packed as 1. Suits never break ties.
 * Accepts 5, 6 or 7 cards and returns the value of the best 5-card hand.
 */
import { type PCard, rankOf, suitOf } from './cards';

export const HAND_CATEGORIES = [
  'high_card',
  'pair',
  'two_pair',
  'three_of_a_kind',
  'straight',
  'flush',
  'full_house',
  'four_of_a_kind',
  'straight_flush',
] as const;
export type HandCategory = (typeof HAND_CATEGORIES)[number];
/** Display name incl. royal flush (lang `gui.burmaldaholic.poker.hand.<name>`). */
export type HandName = HandCategory | 'royal_flush';

export const CAT = {
  high_card: 0,
  pair: 1,
  two_pair: 2,
  three_of_a_kind: 3,
  straight: 4,
  flush: 5,
  full_house: 6,
  four_of_a_kind: 7,
  straight_flush: 8,
} as const;

function pack(cat: number, r: readonly number[]): number {
  let v = cat;
  for (let i = 0; i < 5; i++) v = v * 16 + (r[i] ?? 0);
  return v;
}

/** Highest straight in a rank bitmask (bit r = rank r present, ace also at bit 1); 0 if none. */
function straightHigh(mask: number): number {
  for (let h = 14; h >= 5; h--) {
    if (((mask >> (h - 4)) & 0x1f) === 0x1f) return h;
  }
  return 0;
}

function straightRanks(h: number): number[] {
  return [h, h - 1, h - 2, h - 3, h - 4];
}

/** Top `n` ranks of a rank mask (bits 2..14), descending. */
function topRanks(mask: number, n: number): number[] {
  const out: number[] = [];
  for (let r = 14; r >= 2 && out.length < n; r--) if (mask & (1 << r)) out.push(r);
  return out;
}

/** Evaluate the best 5-card hand among 5..7 cards. */
export function evaluate(cards: readonly PCard[]): number {
  if (cards.length < 5 || cards.length > 7) throw new Error(`evaluate needs 5-7 cards, got ${cards.length}`);
  const counts = new Array<number>(15).fill(0);
  const suitMask = [0, 0, 0, 0];
  const suitCount = [0, 0, 0, 0];
  let mask = 0;
  for (const c of cards) {
    const r = rankOf(c);
    const s = suitOf(c);
    counts[r]!++;
    suitMask[s]! |= 1 << r;
    suitCount[s]!++;
    mask |= 1 << r;
  }
  if (mask & (1 << 14)) mask |= 1 << 1;

  // Flush / straight flush.
  let flushSuit = -1;
  for (let s = 0; s < 4; s++) if (suitCount[s]! >= 5) flushSuit = s;
  if (flushSuit >= 0) {
    let fm = suitMask[flushSuit]!;
    if (fm & (1 << 14)) fm |= 1 << 1;
    const sf = straightHigh(fm);
    if (sf) return pack(CAT.straight_flush, straightRanks(sf));
  }

  // Group ranks by count (descending rank).
  let quad = 0;
  const trips: number[] = [];
  const pairs: number[] = [];
  for (let r = 14; r >= 2; r--) {
    const n = counts[r]!;
    if (n === 4) quad = r;
    else if (n === 3) trips.push(r);
    else if (n === 2) pairs.push(r);
  }
  const rankMask = mask & ~(1 << 1);

  if (quad) {
    const k = topRanks(rankMask & ~(1 << quad), 1);
    return pack(CAT.four_of_a_kind, [quad, quad, quad, quad, k[0] ?? 0]);
  }
  if (trips.length && (trips.length > 1 || pairs.length)) {
    const t = trips[0]!;
    const p = Math.max(trips[1] ?? 0, pairs[0] ?? 0);
    return pack(CAT.full_house, [t, t, t, p, p]);
  }
  if (flushSuit >= 0) return pack(CAT.flush, topRanks(suitMask[flushSuit]!, 5));
  const st = straightHigh(mask);
  if (st) return pack(CAT.straight, st === 5 ? [5, 4, 3, 2, 1] : straightRanks(st));
  if (trips.length) {
    const t = trips[0]!;
    const k = topRanks(rankMask & ~(1 << t), 2);
    return pack(CAT.three_of_a_kind, [t, t, t, ...k]);
  }
  if (pairs.length >= 2) {
    const [a, b] = [pairs[0]!, pairs[1]!];
    const k = topRanks(rankMask & ~(1 << a) & ~(1 << b), 1);
    return pack(CAT.two_pair, [a, a, b, b, k[0] ?? 0]);
  }
  if (pairs.length === 1) {
    const p = pairs[0]!;
    return pack(CAT.pair, [p, p, ...topRanks(rankMask & ~(1 << p), 3)]);
  }
  return pack(CAT.high_card, topRanks(rankMask, 5));
}

/** Category index 0..8 of an evaluated value. */
export const categoryOf = (value: number): number => value >>> 20;

/** The 5 packed ranks of an evaluated value (most significant first). */
export function ranksOf(value: number): number[] {
  const out: number[] = [];
  for (let i = 4; i >= 0; i--) out.push((value >>> (i * 4)) & 0xf);
  return out;
}

/** Display name of an evaluated value (straight flush to the ace = royal flush). */
export function handName(value: number): HandName {
  const cat = categoryOf(value);
  if (cat === CAT.straight_flush && ranksOf(value)[0] === 14) return 'royal_flush';
  return HAND_CATEGORIES[cat]!;
}

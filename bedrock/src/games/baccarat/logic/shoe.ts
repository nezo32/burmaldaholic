/**
 * The baccarat shoe (GAME_DESIGN §20.1): `decks` × 52 cards shared by the whole table,
 * reshuffled BEFORE a coup once `penetration` of it has been dealt, with the burn procedure
 * after every shuffle (the first card is shown and burned, then as many more face down as its
 * value: A = 1, 2–9 face, 10/J/Q/K = 10 → 2–11 cards). Serializable so it survives restarts
 * (§20.5). PURE.
 */
import { type Card, cardId, newShoe, parseCard } from '../../../core/logic/cards';
import type { Rng } from '../../../core/logic/rng';

/** Burn count value of the shown card (tens and faces count 10 here, unlike the hand value). */
export function burnValue(c: Card): number {
  if (c.rank === 'A') return 1;
  if (c.rank === '10' || c.rank === 'J' || c.rank === 'Q' || c.rank === 'K') return 10;
  return Number(c.rank);
}

export interface ShoeData {
  /** decks */
  d: number;
  /** card ids, space separated */
  c: string;
  /** next card index */
  p: number;
}

export interface ShuffleResult {
  /** the shown burn card (undefined when burning is off) */
  shown?: Card;
  /** cards burned in total (0 when burning is off) */
  burned: number;
}

export class BaccaratShoe {
  private cards: Card[];
  private pos: number;

  constructor(
    readonly decks: number,
    cards?: Card[],
    pos = 0,
  ) {
    this.cards = cards ?? [];
    this.pos = pos;
  }

  get size(): number {
    return this.cards.length;
  }
  get dealt(): number {
    return this.pos;
  }
  get remaining(): number {
    return Math.max(0, this.cards.length - this.pos);
  }
  /** A fresh (never shuffled) shoe or a finished one. */
  get isNew(): boolean {
    return this.cards.length === 0;
  }

  /** Shuffle due before the next coup (new shoe, penetration reached, or too few cards left). */
  needsShuffle(penetration: number): boolean {
    return this.isNew || this.pos >= this.cards.length * penetration || this.remaining < 6;
  }

  /** New order + burn (§20.1). */
  shuffle(rng: Rng, burn: boolean): ShuffleResult {
    this.cards = newShoe(rng, this.decks);
    this.pos = 0;
    if (!burn) return { burned: 0 };
    const shown = this.cards[this.pos++]!;
    this.pos += burnValue(shown);
    return { shown, burned: 1 + burnValue(shown) };
  }

  draw(): Card {
    // Cannot run dry mid-coup: needsShuffle() keeps ≥ 6 cards before every coup.
    const c = this.cards[this.pos++];
    if (!c) throw new Error('baccarat shoe exhausted');
    return c;
  }

  toData(): ShoeData {
    return { d: this.decks, c: this.cards.map(cardId).join(' '), p: this.pos };
  }

  /** Restore a saved shoe; a corrupt or mismatched save yields a new (unshuffled) shoe. */
  static fromData(x: unknown, decks: number): BaccaratShoe {
    const d = x as Partial<ShoeData> | undefined;
    if (!d || d.d !== decks || typeof d.c !== 'string' || typeof d.p !== 'number') return new BaccaratShoe(decks);
    try {
      const cards = d.c ? d.c.split(' ').map(parseCard) : [];
      if (cards.length !== decks * 52 || d.p < 0 || d.p > cards.length) return new BaccaratShoe(decks);
      return new BaccaratShoe(decks, cards, Math.floor(d.p));
    } catch {
      return new BaccaratShoe(decks);
    }
  }
}

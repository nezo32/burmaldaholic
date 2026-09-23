/**
 * Multi-deck shoe with a cut card (GAME_DESIGN §6.1: reshuffle when ≥ penetration of the shoe
 * has been dealt, before the next round). PURE.
 */
import { type Card, newShoe } from '../../../core/logic/cards';
import type { Rng } from '../../../core/logic/rng';

export interface CardSource {
  draw(): Card;
}

export class Shoe implements CardSource {
  private cards: Card[];
  private pos = 0;

  constructor(
    private readonly rng: Rng,
    readonly decks: number,
  ) {
    this.cards = newShoe(rng, decks);
  }

  get size(): number {
    return this.cards.length;
  }
  get dealt(): number {
    return this.pos;
  }
  get remaining(): number {
    return this.cards.length - this.pos;
  }

  /** True once the cut card has come out (checked between rounds). */
  needsShuffle(penetration: number): boolean {
    return this.pos >= this.cards.length * penetration;
  }

  shuffle(): void {
    this.cards = newShoe(this.rng, this.decks);
    this.pos = 0;
  }

  draw(): Card {
    // Cannot happen with ≤ 7 seats × 4 hands and penetration ≤ 0.9, but stay safe.
    if (this.pos >= this.cards.length) this.shuffle();
    return this.cards[this.pos++]!;
  }
}

/** Deals a fixed sequence first (tests, replays), then falls back to `rest`. */
export class StackedSource implements CardSource {
  private readonly queue: Card[];
  constructor(
    cards: readonly Card[],
    private readonly rest?: CardSource,
  ) {
    this.queue = [...cards];
  }
  draw(): Card {
    const c = this.queue.shift() ?? this.rest?.draw();
    if (!c) throw new Error('stacked source exhausted');
    return c;
  }
}

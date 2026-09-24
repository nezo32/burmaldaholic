/**
 * Punto Banco card rules (GAME_DESIGN §20.1, §20.3). PURE.
 *
 * Card values: A = 1, 2–9 face value, 10/J/Q/K = 0; a hand's total is the sum mod 10. Deal
 * order P1, B1, P2, B2, then Player's third card, then Banker's third card. Nobody decides
 * anything: a coup is fully determined by the next 4–6 cards of the shoe.
 */
import type { Card, Rank } from '../../../core/logic/cards';

export type Side = 'player' | 'banker';
export type Winner = Side | 'tie';

/** Baccarat point value 0–9 of a rank. */
export function rankValue(r: Rank): number {
  if (r === 'A') return 1;
  if (r === '10' || r === 'J' || r === 'Q' || r === 'K') return 0;
  return Number(r);
}

export const cardValue = (c: Card): number => rankValue(c.rank);

/** Hand total: sum of the card values mod 10 (7 + 8 = 5). */
export function handTotal(cards: readonly Card[]): number {
  let s = 0;
  for (const c of cards) s += cardValue(c);
  return s % 10;
}

/** Player's rule on its two-card total: 0–5 draws, 6–7 stands (8–9 is a natural). */
export const playerDraws = (total: number): boolean => total <= 5;

/**
 * Banker's rule (§20.3). `playerThird` = the VALUE (0–9) of Player's third card, or undefined
 * when Player stood (then Banker plays like Player: 0–5 draws).
 */
export function bankerDraws(bankerTotal: number, playerThird: number | undefined): boolean {
  if (playerThird === undefined) return bankerTotal <= 5;
  switch (bankerTotal) {
    case 0:
    case 1:
    case 2:
      return true;
    case 3:
      return playerThird !== 8;
    case 4:
      return playerThird >= 2 && playerThird <= 7;
    case 5:
      return playerThird >= 4 && playerThird <= 7;
    case 6:
      return playerThird === 6 || playerThird === 7;
    default:
      return false;
  }
}

export interface Coup {
  /** Player's cards in deal order (2 or 3) */
  readonly player: readonly Card[];
  /** Banker's cards in deal order (2 or 3) */
  readonly banker: readonly Card[];
  readonly playerTotal: number;
  readonly bankerTotal: number;
  readonly winner: Winner;
  /** Player's first two cards have the same rank */
  readonly playerPair: boolean;
  /** Banker's first two cards have the same rank */
  readonly bankerPair: boolean;
  /** either two-card hand is 8 or 9 (nobody draws) */
  readonly natural: boolean;
}

/** Two-card total of a hand (the natural check). */
const twoCardTotal = (cards: readonly Card[]): number => handTotal(cards.slice(0, 2));

/** The natural of the WINNING hand, if any (8 or 9), else undefined. */
export function winningNatural(c: Coup): number | undefined {
  if (!c.natural || c.winner === 'tie') return undefined;
  const t = twoCardTotal(c.winner === 'player' ? c.player : c.banker);
  return t >= 8 ? t : undefined;
}

/**
 * Deal a complete coup from a card source (called 4–6 times, in deal order). The same
 * function replays a stored coup from its card list.
 */
export function dealCoup(draw: () => Card): Coup {
  const p1 = draw();
  const b1 = draw();
  const p2 = draw();
  const b2 = draw();
  const player: Card[] = [p1, p2];
  const banker: Card[] = [b1, b2];
  const p = handTotal(player);
  const b = handTotal(banker);
  const natural = p >= 8 || b >= 8;
  if (!natural) {
    let third: number | undefined;
    if (playerDraws(p)) {
      const c = draw();
      player.push(c);
      third = cardValue(c);
    }
    if (bankerDraws(b, third)) banker.push(draw());
  }
  const playerTotal = handTotal(player);
  const bankerTotal = handTotal(banker);
  return {
    player,
    banker,
    playerTotal,
    bankerTotal,
    winner: playerTotal > bankerTotal ? 'player' : bankerTotal > playerTotal ? 'banker' : 'tie',
    playerPair: p1.rank === p2.rank,
    bankerPair: b1.rank === b2.rank,
    natural,
  };
}

/** Cards of a coup in deal order (P1 B1 P2 B2 [P3] [B3]) — what the store persists. */
export function dealOrder(c: Coup): Card[] {
  const out: Card[] = [c.player[0]!, c.banker[0]!, c.player[1]!, c.banker[1]!];
  if (c.player[2]) out.push(c.player[2]);
  if (c.banker[2]) out.push(c.banker[2]);
  return out;
}

/** Replay a coup from its dealt cards (deal order). Throws when the list is too short. */
export function coupFromCards(cards: readonly Card[]): Coup {
  let i = 0;
  return dealCoup(() => {
    const c = cards[i++];
    if (!c) throw new Error('coup card list too short');
    return c;
  });
}

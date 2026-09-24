/**
 * Ultimate Texas Hold'em seat settlement (GAME_DESIGN §21.1). PURE.
 *
 *   A = Ante = Blind, P = Play bet, T = Trips. "wins" compares full evaluate() values.
 *   | Seat result                        | Play | Ante | Blind                     |
 *   | wins, dealer qualifies             | +P   | +A   | Blind paytable, else push |
 *   | wins, dealer does not qualify      | +P   | push | Blind paytable, else push |
 *   | loses, dealer qualifies            | −P   | −A   | −A                        |
 *   | loses, dealer does not qualify     | −P   | push | −A                        |
 *   | tie                                | push | push | push                      |
 *   | folded (river)                     | —    | −A   | −A                        |
 *   Trips: +T × tripsPays[hand] for three of a kind or better, else −T - always (even folded).
 *   Rounding: the Blind's fractional pays are floored (3:2 flush on an odd Ante loses half a chip).
 */
import { CAT, categoryOf, evaluate } from '../../poker/logic/evaluator';
import type { PCard } from '../../poker/logic/cards';
import { type PayHand, type Paytable, blindMultiplier, payHandOf, tripsMultiplier } from './paytable';

export type SeatOutcome = 'win' | 'lose' | 'tie' | 'folded';

export interface SeatBets {
  ante: number;
  trips: number;
  /** Play bet (0 = none) */
  play: number;
  folded: boolean;
}

export interface Settlement {
  outcome: SeatOutcome;
  qualifies: boolean;
  /** net per bet (positive = won) */
  play: number;
  ante: number;
  blind: number;
  trips: number;
  /** paytable rows that paid (for the result lines / advancements / chaos) */
  blindHand?: PayHand;
  tripsHand?: PayHand;
  net: number;
  /** chips at risk: 2A + T + P */
  staked: number;
  /** staked + net (what the house pays back, stake included) */
  totalReturn: number;
}

/** Dealer qualifies with one pair or better (board pairs count). */
export const dealerQualifies = (dealerValue: number): boolean => categoryOf(dealerValue) >= CAT.pair;

export function settleSeat(bets: SeatBets, playerValue: number, dealerValue: number, blindPays: Readonly<Paytable>, tripsPays: Readonly<Paytable>): Settlement {
  const A = Math.max(0, Math.floor(bets.ante));
  const T = Math.max(0, Math.floor(bets.trips));
  const P = bets.folded ? 0 : Math.max(0, Math.floor(bets.play));
  const qualifies = dealerQualifies(dealerValue);
  let outcome: SeatOutcome;
  let play = 0;
  let ante = 0;
  let blind = 0;
  let blindHand: PayHand | undefined;
  if (bets.folded || P === 0) {
    // A seat without a Play bet at showdown has folded (the river offers only Bet ×1 / Fold).
    outcome = 'folded';
    ante = -A;
    blind = -A;
  } else if (playerValue > dealerValue) {
    outcome = 'win';
    play = P;
    ante = qualifies ? A : 0;
    const m = blindMultiplier(blindPays, playerValue);
    if (m > 0) {
      blind = Math.floor(A * m);
      blindHand = payHandOf(playerValue);
    }
  } else if (playerValue < dealerValue) {
    outcome = 'lose';
    play = -P;
    ante = qualifies ? -A : 0;
    blind = -A;
  } else {
    outcome = 'tie';
  }
  let trips = 0;
  let tripsHand: PayHand | undefined;
  if (T > 0) {
    const m = tripsMultiplier(tripsPays, playerValue);
    if (m !== undefined) {
      trips = Math.floor(T * m);
      tripsHand = payHandOf(playerValue);
    } else trips = -T;
  }
  const net = play + ante + blind + trips;
  const staked = 2 * A + T + P;
  return { outcome, qualifies, play, ante, blind, trips, blindHand, tripsHand, net, staked, totalReturn: staked + net };
}

/** Convenience: evaluate the hands from cards (hole 2 + board 5, dealer 2 + board 5). */
export function settleCards(bets: SeatBets, hole: readonly PCard[], dealer: readonly PCard[], board: readonly PCard[], blindPays: Readonly<Paytable>, tripsPays: Readonly<Paytable>): Settlement {
  return settleSeat(bets, evaluate([...hole, ...board]), evaluate([...dealer, ...board]), blindPays, tripsPays);
}

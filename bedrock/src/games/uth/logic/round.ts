/**
 * Ultimate Texas Hold'em round state machine (GAME_DESIGN §21.4). PURE.
 *
 * One shared deck, board and dealer hand; every seat has its own hole cards and plays only
 * against the dealer. Streets: preflop (Check · Bet ×3 · Bet ×4) → flop (Check · Bet ×2) →
 * river (Bet ×1 · Fold) → showdown. A seat makes at most one Play bet; after it the seat only
 * watches. All seats decide at the same time; a street ends when nobody has a pending decision.
 *
 * The whole deck is fixed at DEAL (§4.1 "drawn"), so `projected(seat)` - "every pending
 * decision takes its default action now" - is the result persisted with `wagers.draw`.
 */
import { CAT, categoryOf, evaluate } from '../../poker/logic/evaluator';
import type { PCard } from '../../poker/logic/cards';
import type { Paytable } from './paytable';
import { type Settlement, settleSeat } from './settle';

export type Street = 'preflop' | 'flop' | 'river' | 'showdown';
export type Decision = 'check' | 'bet4' | 'bet3' | 'bet2' | 'bet1' | 'fold';

/** Play-bet multiple of the Ante per decision. */
export const PLAY_MULTIPLE: Readonly<Record<Decision, number>> = { check: 0, fold: 0, bet4: 4, bet3: 3, bet2: 2, bet1: 1 };

export interface UthRules {
  allow3x: boolean;
  autoPlayMadeHands: boolean;
  blindPays: Readonly<Paytable>;
  tripsPays: Readonly<Paytable>;
}

export interface SeatInit {
  seat: number;
  id: string;
  ante: number;
  trips: number;
}

export interface SeatState extends SeatInit {
  hole: PCard[];
  /** Play bet placed (chips) */
  play: number;
  /** Play multiple (0 = none yet) */
  multiple: number;
  folded: boolean;
  /** last decision and its street (public tag) */
  last?: Decision;
  decidedOn?: Street;
  /** the last decision was a timeout / away default */
  auto?: boolean;
}

export interface LegalOption {
  decision: Decision;
  /** chips this option puts on the Play bet */
  amount: number;
  affordable: boolean;
}

const NEXT: Readonly<Record<Street, Street>> = { preflop: 'flop', flop: 'river', river: 'showdown', showdown: 'showdown' };

export class UthRound {
  street: Street = 'preflop';
  readonly seats: SeatState[];
  readonly dealer: readonly PCard[];
  readonly board: readonly PCard[];

  /**
   * Deal from `deck` (already shuffled): one card to each seat in seat order, one to the dealer,
   * again; then the 5 board cards (no burns, §21.1).
   */
  constructor(
    readonly deck: readonly PCard[],
    seats: readonly SeatInit[],
    readonly rules: UthRules,
  ) {
    const order = [...seats].sort((a, b) => a.seat - b.seat);
    if (order.length * 2 + 7 > deck.length) throw new Error('not enough cards');
    let i = 0;
    const holes = order.map(() => [] as PCard[]);
    const dealer: PCard[] = [];
    for (let r = 0; r < 2; r++) {
      for (const h of holes) h.push(deck[i++]!);
      dealer.push(deck[i++]!);
    }
    this.dealer = dealer;
    this.board = deck.slice(i, i + 5);
    this.seats = order.map((s, k) => ({ ...s, ante: Math.floor(s.ante), trips: Math.floor(s.trips), hole: holes[k]!, play: 0, multiple: 0, folded: false }));
  }

  seat(n: number): SeatState | undefined {
    return this.seats.find((s) => s.seat === n);
  }

  /** Board cards visible on the current street. */
  visibleBoard(): readonly PCard[] {
    return this.board.slice(0, this.street === 'preflop' ? 0 : this.street === 'flop' ? 3 : 5);
  }

  /** Whether the seat still has to decide on the current street. */
  needsDecision(s: SeatState): boolean {
    return this.street !== 'showdown' && !s.folded && s.play === 0 && s.decidedOn !== this.street;
  }

  pending(): SeatState[] {
    return this.seats.filter((s) => this.needsDecision(s));
  }

  /** Options on the current street, with the Play amount and whether `balance` covers it. */
  legal(seatNo: number, balance: number): LegalOption[] {
    const s = this.seat(seatNo);
    if (!s || !this.needsDecision(s)) return [];
    const opt = (decision: Decision): LegalOption => {
      const amount = PLAY_MULTIPLE[decision] * s.ante;
      return { decision, amount, affordable: amount <= balance };
    };
    switch (this.street) {
      case 'preflop':
        return [opt('check'), ...(this.rules.allow3x ? [opt('bet3')] : []), opt('bet4')];
      case 'flop':
        return [opt('check'), opt('bet2')];
      case 'river':
        return [opt('fold'), opt('bet1')];
      default:
        return [];
    }
  }

  /** Whether `d` is a legal decision for the seat now (ignoring affordability). */
  isLegal(seatNo: number, d: Decision): boolean {
    return this.legal(seatNo, Number.MAX_SAFE_INTEGER).some((o) => o.decision === d);
  }

  /**
   * Record a decision. Returns the Play amount to take from the player (0 for check / fold).
   * Throws on an illegal decision (the caller re-checks after every await).
   */
  decide(seatNo: number, d: Decision, auto = false): number {
    const s = this.seat(seatNo);
    if (!s || !this.isLegal(seatNo, d)) throw new Error(`illegal ${d} for seat ${seatNo} on ${this.street}`);
    s.last = d;
    s.decidedOn = this.street;
    s.auto = auto;
    if (d === 'fold') s.folded = true;
    const m = PLAY_MULTIPLE[d];
    if (m > 0) {
      s.multiple = m;
      s.play = m * s.ante;
    }
    return s.play && m > 0 ? s.play : 0;
  }

  /** Best hand of the seat on the full board (hole + 5). */
  playerValue(s: SeatState): number {
    return evaluate([...s.hole, ...this.board]);
  }

  /** Seat's best hand with the cards visible now (for "Your hand" in the forms). */
  visibleValue(s: SeatState): number | undefined {
    const b = this.visibleBoard();
    return b.length >= 3 ? evaluate([...s.hole, ...b]) : undefined;
  }

  dealerValue(): number {
    return evaluate([...this.dealer, ...this.board]);
  }

  /**
   * Default action (§21.4): preflop / flop → Check; river → Fold, except with
   * `autoPlayMadeHands` and a straight or better (on the full board) that `canAffordAnte`
   * covers → Bet ×1 (a timeout never throws away a Blind bonus).
   */
  defaultDecision(seatNo: number, canAffordAnte: boolean): Decision {
    const s = this.seat(seatNo);
    if (this.street !== 'river') return 'check';
    if (s && this.rules.autoPlayMadeHands && canAffordAnte && categoryOf(this.playerValue(s)) >= CAT.straight) return 'bet1';
    return 'fold';
  }

  /** Move to the next street when nobody is pending. Returns true when the street changed. */
  advance(): boolean {
    if (this.street === 'showdown' || this.pending().length) return false;
    this.street = NEXT[this.street];
    return true;
  }

  /** Settlement at showdown (or of a folded seat). */
  settle(seatNo: number): Settlement {
    const s = this.seat(seatNo);
    if (!s) throw new Error(`no seat ${seatNo}`);
    return settleSeat(s, this.playerValue(s), this.dealerValue(), this.rules.blindPays, this.rules.tripsPays);
  }

  /**
   * What the seat gets if every pending decision (this street and the later ones) takes its
   * default action now (§21.4 / §21.5). `canAffordAnte`: whether an automatic ×1 river bet is
   * possible. Also returns the extra Play amount that default would add.
   */
  projected(seatNo: number, canAffordAnte: boolean): { settlement: Settlement; extraPlay: number } {
    const s = this.seat(seatNo);
    if (!s) throw new Error(`no seat ${seatNo}`);
    if (s.folded || s.play > 0) return { settlement: this.settle(seatNo), extraPlay: 0 };
    const bet = this.rules.autoPlayMadeHands && canAffordAnte && categoryOf(this.playerValue(s)) >= CAT.straight;
    const settlement = settleSeat({ ...s, play: bet ? s.ante : 0, folded: !bet }, this.playerValue(s), this.dealerValue(), this.rules.blindPays, this.rules.tripsPays);
    return { settlement, extraPlay: bet ? s.ante : 0 };
  }
}

/**
 * Total return to hand to `wagers.settle` / `wagers.draw` when part of the Play bet in the
 * settlement was never debited (an automatic ×1 for a player who is offline): the net stays
 * the same relative to the chips actually staked (never below 0).
 */
export function returnForStaked(s: Settlement, unpaidPlay: number): number {
  return Math.max(0, s.totalReturn - Math.max(0, unpaidPlay));
}

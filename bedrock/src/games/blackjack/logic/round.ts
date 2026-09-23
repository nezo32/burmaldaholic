/**
 * One blackjack round at a multi-seat table (GAME_DESIGN §6.3). PURE state machine; the
 * runtime (../table.ts) moves money, shows forms and runs the timers.
 *
 *   new BlackjackRound(rules, shoe, bets)   DEAL: card to each seat, dealer up, second card to
 *                                           each seat, dealer hole card
 *   phase 'insurance'  dealer shows an ace: decideInsurance(seat, yes/no) for every seat
 *                      (even money for a player blackjack)
 *   (peek)             dealer up A or 10-value: a dealer blackjack settles everything -> 'done'.
 *                      Otherwise player blackjacks are settled at once (3:2), insurance is lost.
 *   phase 'turns'      current() seat/hand acts: hit / stand / double / split / surrender;
 *                      standAll(seat) for timeouts and disconnects
 *   (dealer)           reveal; draw to 17 (S17) or hit soft 17 (H17) unless nobody is live
 *   phase 'done'       every seat settled: hand.outcome / hand.ret, returnOf(seat)
 *
 * All returns are TOTAL returns (stake included), like ctx.wagers.settle.
 */
import type { Card } from '../../../core/logic/cards';
import { isNatural, isPair, isTenValue, valueOf } from './hand';
import type { BlackjackRules } from './rules';
import type { CardSource } from './shoe';

export type Phase = 'insurance' | 'turns' | 'done';
export type Action = 'hit' | 'stand' | 'double' | 'split' | 'surrender';
export const ACTIONS: readonly Action[] = ['hit', 'stand', 'double', 'split', 'surrender'];
export type HandOutcome = 'blackjack' | 'win' | 'dealer_bust' | 'push' | 'bust' | 'lose' | 'surrender' | 'dealer_blackjack' | 'even_money';
export type InsuranceOffer = 'insurance' | 'even_money';

export interface SeatBet {
  seat: number;
  /** player id (opaque here) */
  id: string;
  bet: number;
}

export interface Hand {
  cards: Card[];
  bet: number;
  /** produced by a split (a 21 is then not a blackjack) */
  split: boolean;
  /** a split ace: one card only, no hit/double */
  splitAces: boolean;
  doubled: boolean;
  surrendered: boolean;
  done: boolean;
  outcome?: HandOutcome;
  /** total return of this hand once settled */
  ret: number;
}

export interface SeatState {
  seat: number;
  id: string;
  /** original main bet */
  bet: number;
  hands: Hand[];
  insurance: number;
  insuranceDecided: boolean;
  /** insurance total return (3 × insurance when the dealer has blackjack) */
  insuranceReturn: number;
  /** every hand resolved and paid out amounts final */
  settled: boolean;
}

/** Blackjack payout: stake + floor(stake × payout) (3:2 on 5 -> +7). */
export const blackjackReturn = (bet: number, payout: number): number => bet + Math.floor(bet * payout);
/** Late surrender returns half the bet, floored. */
export const surrenderReturn = (bet: number): number => Math.floor(bet / 2);
/** Maximum insurance: half the main bet, floored. */
export const maxInsurance = (bet: number): number => Math.floor(bet / 2);

export class BlackjackRound {
  readonly seats: SeatState[];
  readonly dealer: Card[] = [];
  phase: Phase = 'turns';
  /** hole card visible (after a dealer blackjack or when the dealer plays) */
  holeRevealed = false;
  dealerBlackjack = false;
  /** the dealer checked the hole card for blackjack */
  peeked = false;
  private cur = -1;

  constructor(
    readonly rules: BlackjackRules,
    private readonly src: CardSource,
    bets: readonly SeatBet[],
  ) {
    this.seats = [...bets]
      .filter((b) => b.bet > 0)
      .sort((a, b) => a.seat - b.seat)
      .map((b) => ({
        seat: b.seat,
        id: b.id,
        bet: b.bet,
        hands: [newHand(b.bet)],
        insurance: 0,
        insuranceDecided: false,
        insuranceReturn: 0,
        settled: false,
      }));
    if (!this.seats.length) throw new Error('blackjack round without bets');
    for (const s of this.seats) s.hands[0]!.cards.push(this.src.draw());
    this.dealer.push(this.src.draw());
    for (const s of this.seats) s.hands[0]!.cards.push(this.src.draw());
    this.dealer.push(this.src.draw());

    if (this.rules.insurance && this.upCard.rank === 'A') {
      this.phase = 'insurance';
      for (const s of this.seats) if (!this.insuranceOffer(s.seat)) s.insuranceDecided = true;
      this.afterInsuranceIfDecided();
    } else {
      this.afterInsurance();
    }
  }

  get upCard(): Card {
    return this.dealer[0]!;
  }

  seat(seatNo: number): SeatState | undefined {
    return this.seats.find((s) => s.seat === seatNo);
  }

  // ---- insurance ---------------------------------------------------------------------------

  /** What this seat is offered right now (undefined = nothing to decide). */
  insuranceOffer(seatNo: number): InsuranceOffer | undefined {
    const s = this.seat(seatNo);
    if (!s || this.phase !== 'insurance' || s.insuranceDecided || s.settled) return undefined;
    if (isNatural(s.hands[0]!.cards)) return 'even_money';
    return maxInsurance(s.bet) >= 1 ? 'insurance' : undefined;
  }

  /** Seats still deciding insurance / even money. */
  pendingInsurance(): SeatState[] {
    return this.phase === 'insurance' ? this.seats.filter((s) => !s.insuranceDecided) : [];
  }

  /**
   * Take (true) or decline insurance / even money. Insurance is always half the main bet
   * (the runtime raises the wager first). Even money settles the seat at 1:1 immediately.
   */
  decideInsurance(seatNo: number, take: boolean): boolean {
    const offer = this.insuranceOffer(seatNo);
    const s = this.seat(seatNo);
    if (!offer || !s) return false;
    s.insuranceDecided = true;
    if (take && offer === 'even_money') {
      const h = s.hands[0]!;
      h.done = true;
      h.outcome = 'even_money';
      h.ret = s.bet * 2;
      s.settled = true;
    } else if (take) {
      s.insurance = maxInsurance(s.bet);
    }
    this.afterInsuranceIfDecided();
    return true;
  }

  private afterInsuranceIfDecided(): void {
    if (this.phase === 'insurance' && this.seats.every((s) => s.insuranceDecided)) this.afterInsurance();
  }

  /** Peek, settle naturals, start the player turns. */
  private afterInsurance(): void {
    const up = this.upCard.rank;
    if (up === 'A' || isTenValue(up)) {
      this.peeked = true;
      if (isNatural(this.dealer)) {
        this.dealerBlackjack = true;
        this.holeRevealed = true;
        for (const s of this.seats) {
          if (s.settled) continue;
          const h = s.hands[0]!;
          h.done = true;
          if (isNatural(h.cards)) {
            h.outcome = 'push';
            h.ret = h.bet;
          } else {
            h.outcome = 'dealer_blackjack';
            h.ret = 0;
          }
          s.insuranceReturn = s.insurance * 3;
          s.settled = true;
        }
        this.phase = 'done';
        return;
      }
    }
    for (const s of this.seats) {
      if (s.settled) continue;
      const h = s.hands[0]!;
      if (isNatural(h.cards)) {
        h.done = true;
        h.outcome = 'blackjack';
        h.ret = blackjackReturn(h.bet, this.rules.blackjackPayout);
        s.settled = true;
      }
    }
    this.phase = 'turns';
    this.cur = -1;
    this.advance();
  }

  // ---- player turns ------------------------------------------------------------------------

  /** The seat and hand to act, while phase is 'turns'. */
  current(): { seat: SeatState; hand: Hand; handIndex: number } | undefined {
    if (this.phase !== 'turns') return undefined;
    const seat = this.seats[this.cur];
    if (!seat) return undefined;
    const handIndex = seat.hands.findIndex((h) => !h.done);
    if (handIndex < 0) return undefined;
    return { seat, hand: seat.hands[handIndex]!, handIndex };
  }

  /**
   * Legal actions for the current hand of `seatNo`. `balance` = chips the player can still add
   * (double and split need one more bet equal to the hand's bet).
   */
  legal(seatNo: number, balance = Number.POSITIVE_INFINITY): Action[] {
    const c = this.current();
    if (!c || c.seat.seat !== seatNo) return [];
    const { seat, hand } = c;
    const canSplit = isPair(hand.cards) && seat.hands.length < this.rules.maxHands && balance >= hand.bet && (!hand.splitAces || this.rules.resplitAces);
    if (hand.splitAces) return canSplit ? ['stand', 'split'] : ['stand'];
    const out: Action[] = ['hit', 'stand'];
    if (hand.cards.length === 2 && balance >= hand.bet && (!hand.split || this.rules.doubleAfterSplit)) out.push('double');
    if (canSplit) out.push('split');
    if (this.rules.lateSurrender && seat.hands.length === 1 && !hand.split && hand.cards.length === 2) out.push('surrender');
    return out;
  }

  /** Extra chips the action puts at risk (the runtime raises the wager before act()). */
  extraStake(seatNo: number, action: Action): number {
    const c = this.current();
    if (!c || c.seat.seat !== seatNo) return 0;
    return action === 'double' || action === 'split' ? c.hand.bet : 0;
  }

  /** Apply an action (balance already checked by the caller). False if not legal now. */
  act(seatNo: number, action: Action): boolean {
    if (!this.legal(seatNo).includes(action)) return false;
    const { seat, hand, handIndex } = this.current()!;
    switch (action) {
      case 'hit':
        hand.cards.push(this.src.draw());
        if (valueOf(hand.cards).total >= 21) hand.done = true;
        break;
      case 'stand':
        hand.done = true;
        break;
      case 'double':
        hand.bet *= 2;
        hand.doubled = true;
        hand.cards.push(this.src.draw());
        hand.done = true;
        break;
      case 'surrender':
        hand.surrendered = true;
        hand.done = true;
        hand.outcome = 'surrender';
        hand.ret = surrenderReturn(hand.bet);
        break;
      case 'split': {
        const aces = hand.cards[0]!.rank === 'A';
        const second: Hand = { ...newHand(hand.bet), cards: [hand.cards.pop()!] };
        seat.hands.splice(handIndex + 1, 0, second);
        for (const h of [hand, second]) {
          h.split = true;
          h.splitAces = aces;
          h.cards.push(this.src.draw());
          if (aces) h.done = !(this.rules.resplitAces && isPair(h.cards) && seat.hands.length < this.rules.maxHands);
          else if (valueOf(h.cards).total === 21) h.done = true;
        }
        break;
      }
    }
    if (!this.current()) this.advance();
    return true;
  }

  /** Stand every open hand of a seat (turn timeout, disconnect) and decline insurance. */
  standAll(seatNo: number): void {
    const s = this.seat(seatNo);
    if (!s) return;
    if (this.phase === 'insurance' && !s.insuranceDecided) this.decideInsurance(seatNo, false);
    for (const h of s.hands) h.done = true;
    if (this.phase === 'turns' && !this.current()) this.advance();
  }

  private advance(): void {
    while (this.phase === 'turns') {
      const seat = this.seats[this.cur];
      if (seat && !seat.settled && seat.hands.some((h) => !h.done)) return;
      this.cur++;
      if (this.cur >= this.seats.length) {
        this.playDealer();
        return;
      }
    }
  }

  // ---- dealer and settlement ---------------------------------------------------------------

  /** Hands the dealer must beat: not bust, not surrendered, not already settled. */
  private liveHands(): Hand[] {
    return this.seats.filter((s) => !s.settled).flatMap((s) => s.hands.filter((h) => !h.surrendered && valueOf(h.cards).total <= 21));
  }

  private playDealer(): void {
    this.holeRevealed = true;
    if (this.liveHands().length) {
      for (;;) {
        const v = valueOf(this.dealer);
        if (v.total < 17 || (v.total === 17 && v.soft && this.rules.dealerHitsSoft17)) this.dealer.push(this.src.draw());
        else break;
      }
    }
    const dealer = valueOf(this.dealer).total;
    for (const s of this.seats) {
      if (s.settled) continue;
      for (const h of s.hands) {
        h.done = true;
        if (h.surrendered) continue;
        const v = valueOf(h.cards).total;
        if (v > 21) [h.outcome, h.ret] = ['bust', 0];
        else if (dealer > 21) [h.outcome, h.ret] = ['dealer_bust', h.bet * 2];
        else if (v > dealer) [h.outcome, h.ret] = ['win', h.bet * 2];
        else if (v === dealer) [h.outcome, h.ret] = ['push', h.bet];
        else [h.outcome, h.ret] = ['lose', 0];
      }
      s.settled = true;
    }
    this.phase = 'done';
  }

  /** Total return of a seat (hands + insurance). */
  returnOf(seatNo: number): number {
    const s = this.seat(seatNo);
    return s ? s.hands.reduce((a, h) => a + h.ret, 0) + s.insuranceReturn : 0;
  }

  /** Total chips a seat put at risk (bets incl. doubles/splits + insurance). */
  stakedOf(seatNo: number): number {
    const s = this.seat(seatNo);
    return s ? s.hands.reduce((a, h) => a + h.bet, 0) + s.insurance : 0;
  }
}

function newHand(bet: number): Hand {
  return { cards: [], bet, split: false, splitAces: false, doubled: false, surrendered: false, done: false, ret: 0 };
}

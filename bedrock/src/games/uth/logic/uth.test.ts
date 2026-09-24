import { describe, expect, it } from 'vitest';
import { seededRng } from '../../../core/logic/rng';
import { FULL_DECK, type PCard, pcs, shuffledDeck } from '../../poker/logic/cards';
import { evaluate } from '../../poker/logic/evaluator';
import {
  DEFAULT_BLIND_PAYS,
  DEFAULT_TRIPS_PAYS,
  VARIANT_TRIPS_PAYS,
  anteRange,
  bankRound,
  bankTooLow,
  betResultAntes,
  checkBanker,
  confirmCost,
  countSevenCardHands,
  dealerQualifies,
  flopBet2,
  houseSeatEarned,
  seatHooks,
  maxCoveredAnte,
  nextClockwise,
  normalizePaytable,
  payHandOf,
  preflopBet4,
  referencePolicy,
  reservationPerAnte,
  returnForStaked,
  seatReservation,
  settleCards,
  settleSeat,
  SEVEN_CARD_COUNTS,
  tableLimits,
  tripsEdge,
  tripsEvNumerator,
  TOTAL_SEVEN_CARD_HANDS,
  UthRound,
  type UthRules,
  validateBets,
  worstCaseTotal,
} from './index';
import { edgeOf, measureEdge } from './sim';

const RULES: UthRules = { allow3x: true, autoPlayMadeHands: true, blindPays: DEFAULT_BLIND_PAYS, tripsPays: DEFAULT_TRIPS_PAYS };
const ev = (s: string) => evaluate(pcs(s));

/**
 * Build a deck so that UthRound deals the given cards: seats in order get hole cards
 * (card 1 of every seat, dealer card 1, card 2 of every seat, dealer card 2), then the board.
 */
function stackedDeck(holes: string[], dealer: string, board: string): PCard[] {
  const hs = holes.map(pcs);
  const d = pcs(dealer);
  const top: PCard[] = [];
  for (let r = 0; r < 2; r++) {
    for (const h of hs) top.push(h[r]!);
    top.push(d[r]!);
  }
  top.push(...pcs(board));
  const used = new Set(top);
  return [...top, ...FULL_DECK.filter((c) => !used.has(c))];
}

function vector(hole: string, dealer: string, board: string, ante: number, trips: number, decisions: ('check' | 'bet4' | 'bet3' | 'bet2' | 'bet1' | 'fold')[]) {
  const r = new UthRound(stackedDeck([hole], dealer, board), [{ seat: 1, id: 'p', ante, trips }], RULES);
  for (const d of decisions) {
    r.decide(1, d);
    while (r.advance() && r.street !== 'showdown' && !r.pending().length);
  }
  while (r.advance());
  expect(r.street).toBe('showdown');
  return r.settle(1);
}

describe('uth: §21.1 test vectors', () => {
  it('1: royal flush, dealer does not qualify → +5 290', () => {
    const s = vector('As Ks', '7d 2c', 'Qs Js Ts 3h 4d', 10, 5, ['bet4']);
    expect(s).toMatchObject({ outcome: 'win', qualifies: false, play: 40, ante: 0, blind: 5000, trips: 250, net: 5290, blindHand: 'royal', tripsHand: 'royal' });
  });
  it('2: trips beat kings, dealer qualifies → +80', () => {
    const s = vector('9h 9c', 'Kd Kc', '9d 5s 2h Jc 3d', 10, 10, ['bet4']);
    expect(s).toMatchObject({ outcome: 'win', qualifies: true, play: 40, ante: 10, blind: 0, trips: 30, net: 80 });
  });
  it('3: check, check, fold → −30', () => {
    const s = vector('Qc 7d', '4h 4d', '2s 5h 9d Js 3c', 10, 10, ['check', 'check', 'fold']);
    expect(s).toMatchObject({ outcome: 'folded', play: 0, ante: -10, blind: -10, trips: -10, net: -30 });
  });
  it('4: both play the board → only Trips loses (−5)', () => {
    const s = vector('2c 3d', '4s 5d', 'As Ah Kd Kc Qh', 10, 5, ['check', 'check', 'bet1']);
    expect(s).toMatchObject({ outcome: 'tie', play: 0, ante: 0, blind: 0, trips: -5, net: -5 });
  });
  it('5: dealer A-high wins without qualifying → −20', () => {
    const s = vector('7c 2d', 'Ac 8d', 'Ks Qh 9c 5d 3s', 10, 0, ['check', 'check', 'bet1']);
    expect(s).toMatchObject({ outcome: 'lose', qualifies: false, play: -10, ante: 0, blind: -10, trips: 0, net: -20 });
  });
  it('6: odd Ante flush floors the 3:2 Blind (+7)', () => {
    const s = settleCards({ ante: 5, trips: 0, play: 20, folded: false }, pcs('Ah 9h'), pcs('Kc Kd'), pcs('2h 5h 7h Js 3c'), DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS);
    expect(s.blind).toBe(7);
    expect(s.blindHand).toBe('flush');
  });
});

describe('uth: settlement table (every row)', () => {
  const A = 10;
  const P = 20;
  const bets = { ante: A, trips: 0, play: P, folded: false };
  const straight = ev('9s 8h 7d 6c 5s 2d 2c');
  const kingsQ = ev('Kd Kc 9d 5s 2h Jc 3d');
  const highNQ = ev('Ac 8d Ks Qh 9c 5d 3s');
  const pairLow = ev('2c 2d 9s 5h 7c Jd 3s');
  it('wins, dealer qualifies: +P, +A, Blind paytable', () => {
    expect(settleSeat(bets, straight, kingsQ, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS)).toMatchObject({ play: P, ante: A, blind: A, net: P + 2 * A });
    expect(settleSeat(bets, ev('As Ad 9s 5h 7c Jd 3s'), kingsQ, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS)).toMatchObject({ play: P, ante: A, blind: 0 });
  });
  it('wins, dealer does not qualify: +P, Ante push, Blind paytable', () => {
    expect(settleSeat(bets, straight, highNQ, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS)).toMatchObject({ play: P, ante: 0, blind: A, qualifies: false });
    expect(settleSeat(bets, pairLow, highNQ, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS)).toMatchObject({ play: P, ante: 0, blind: 0 });
  });
  it('loses, dealer qualifies: −P, −A, −A', () => {
    expect(settleSeat(bets, pairLow, kingsQ, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS)).toMatchObject({ play: -P, ante: -A, blind: -A, net: -P - 2 * A, totalReturn: 0 });
  });
  it('loses, dealer does not qualify: −P, Ante push, −A', () => {
    const lowHigh = ev('7c 2d Ks Qh 9c 5d 3s');
    expect(settleSeat(bets, lowHigh, highNQ, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS)).toMatchObject({ play: -P, ante: 0, blind: -A, totalReturn: A });
  });
  it('tie: everything pushes (Trips still plays)', () => {
    expect(settleSeat({ ...bets, trips: 5 }, kingsQ, kingsQ, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS)).toMatchObject({ outcome: 'tie', play: 0, ante: 0, blind: 0, trips: -5 });
  });
  it('folded: −A, −A, Trips still pays', () => {
    const s = settleSeat({ ante: A, trips: 5, play: 0, folded: true }, ev('8s 8h 8d Kc 2s 4d 6c'), kingsQ, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS);
    expect(s).toMatchObject({ outcome: 'folded', ante: -A, blind: -A, trips: 15, net: -5, staked: 25, totalReturn: 20 });
  });
  it('dealer qualification: pair or better, board pairs count', () => {
    expect(dealerQualifies(ev('Ac 8d As Qh 9c 5d 3s'))).toBe(true);
    expect(dealerQualifies(highNQ)).toBe(false);
  });
  it('Blind pays each row only on a win', () => {
    const rows: [string, number][] = [
      ['As Ks Qs Js Ts 2d 3c', 500],
      ['9h 8h 7h 6h 5h Ac Ad', 50],
      ['7s 7h 7d 7c Kd 2c 3h', 10],
      ['Qs Qh Qd 9c 9d 2c 3h', 3],
      ['2s 8s Js Ks 4s Ad Ac', 1.5],
      ['Ts 9h 8d 7c 6s 2d 2c', 1],
      ['8s 8h 8d Kc 2s 4d 6c', 0],
    ];
    for (const [cards, m] of rows) {
      expect(settleSeat({ ante: 2, trips: 0, play: 8, folded: false }, ev(cards), highNQ, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS).blind).toBe(2 * m);
    }
  });
  it('Trips paytable 50-40-30-8-6-5-3, lower loses', () => {
    const rows: [string, number][] = [
      ['As Ks Qs Js Ts 2d 3c', 50],
      ['9h 8h 7h 6h 5h Ac Ad', 40],
      ['7s 7h 7d 7c Kd 2c 3h', 30],
      ['Qs Qh Qd 9c 9d 2c 3h', 8],
      ['2s 8s Js Ks 4s Ad Ac', 6],
      ['Ts 9h 8d 7c 6s 2d 2c', 5],
      ['8s 8h 8d Kc 2s 4d 6c', 3],
      ['Js Jh 4d 4c As 7d 2c', -1],
    ];
    for (const [cards, m] of rows) expect(settleSeat({ ante: 1, trips: 10, play: 0, folded: true }, ev(cards), highNQ, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS).trips).toBe(10 * m);
  });
  it('payHandOf / normalizePaytable', () => {
    expect(payHandOf(ev('Js Jh 4d 4c As 7d 2c'))).toBeUndefined();
    expect(payHandOf(ev('As Ks Qs Js Ts 2d 3c'))).toBe('royal');
    expect(normalizePaytable({ royal: 2000, flush: 'x', bogus: 3, trips: 2.7 }, DEFAULT_TRIPS_PAYS, true)).toEqual({ royal: 1000, trips: 2 });
    expect(normalizePaytable(null, DEFAULT_TRIPS_PAYS)).toEqual(DEFAULT_TRIPS_PAYS);
  });
});

describe('uth: round state machine', () => {
  const deck = stackedDeck(['As Ks', '2c 7d'], 'Qh Qd', 'Qs Js Ts 3h 4d');
  const seats = [
    { seat: 3, id: 'b', ante: 10, trips: 0 },
    { seat: 1, id: 'a', ante: 10, trips: 5 },
  ];
  it('deals seat order 1→6, dealer, again; then the board', () => {
    const r = new UthRound(deck, seats, RULES);
    expect(r.seat(1)!.hole).toEqual(pcs('As Ks'));
    expect(r.seat(3)!.hole).toEqual(pcs('2c 7d'));
    expect(r.dealer).toEqual(pcs('Qh Qd'));
    expect(r.board).toEqual(pcs('Qs Js Ts 3h 4d'));
    expect(r.visibleBoard()).toEqual([]);
  });
  it('legal options per street, ×3 optional, affordability', () => {
    const r = new UthRound(deck, seats, RULES);
    expect(r.legal(1, 1000).map((o) => [o.decision, o.amount])).toEqual([
      ['check', 0],
      ['bet3', 30],
      ['bet4', 40],
    ]);
    expect(r.legal(1, 35).find((o) => o.decision === 'bet4')!.affordable).toBe(false);
    const no3 = new UthRound(deck, seats, { ...RULES, allow3x: false });
    expect(no3.legal(1, 1000).map((o) => o.decision)).toEqual(['check', 'bet4']);
    expect(() => r.decide(1, 'bet2')).toThrow();
  });
  it('one Play bet per round; street ends when nobody is pending', () => {
    const r = new UthRound(deck, seats, RULES);
    expect(r.decide(1, 'bet4')).toBe(40);
    expect(r.advance()).toBe(false);
    expect(r.decide(3, 'check')).toBe(0);
    expect(r.advance()).toBe(true);
    expect(r.street).toBe('flop');
    expect(r.visibleBoard()).toHaveLength(3);
    expect(r.legal(1, 1000)).toEqual([]);
    expect(r.pending().map((s) => s.seat)).toEqual([3]);
    r.decide(3, 'check');
    expect(r.advance()).toBe(true);
    expect(r.legal(3, 1000).map((o) => o.decision)).toEqual(['fold', 'bet1']);
    r.decide(3, 'fold');
    expect(r.advance()).toBe(true);
    expect(r.street).toBe('showdown');
    expect(r.settle(1).net).toBe(40 + 10 + 5000 + 250);
    expect(r.settle(3).net).toBe(-20);
  });
  it('street without pending decisions only reveals', () => {
    const r = new UthRound(deck, [seats[1]!], RULES);
    r.decide(1, 'bet4');
    expect(r.advance()).toBe(true);
    expect(r.pending()).toEqual([]);
    expect(r.advance()).toBe(true);
    expect(r.advance()).toBe(true);
    expect(r.street).toBe('showdown');
  });
});

describe('uth: timeouts and defaults (§21.4, §21.8)', () => {
  it('preflop / flop timeouts check, river timeout folds', () => {
    const r = new UthRound(stackedDeck(['Qc 7d'], '4h 4d', '2s 5h 9d Js 3c'), [{ seat: 1, id: 'p', ante: 10, trips: 0 }], RULES);
    expect(r.defaultDecision(1, true)).toBe('check');
    r.decide(1, r.defaultDecision(1, true), true);
    r.advance();
    expect(r.defaultDecision(1, true)).toBe('check');
    r.decide(1, 'check', true);
    r.advance();
    expect(r.defaultDecision(1, true)).toBe('fold');
  });
  it('a straight at the river with autoPlayMadeHands bets ×1 (if the Ante is affordable)', () => {
    const mk = (rules: UthRules) => {
      const r = new UthRound(stackedDeck(['9c 8d'], 'Qh Qd', '7s 6h 5d Kc 3c'), [{ seat: 1, id: 'p', ante: 10, trips: 0 }], rules);
      r.decide(1, 'check');
      r.advance();
      r.decide(1, 'check');
      r.advance();
      return r;
    };
    expect(mk(RULES).street).toBe('river');
    expect(mk(RULES).defaultDecision(1, true)).toBe('bet1');
    expect(mk(RULES).defaultDecision(1, false)).toBe('fold');
    expect(mk({ ...RULES, autoPlayMadeHands: false }).defaultDecision(1, true)).toBe('fold');
  });
  it('restart after DEAL settles at "every pending decision takes its default now"', () => {
    // straight on the full board → auto ×1; dealer pair of queens qualifies; straight wins
    const r = new UthRound(stackedDeck(['9c 8d'], 'Qh Qd', '7s 6h 5d Kc 3c'), [{ seat: 1, id: 'p', ante: 10, trips: 0 }], RULES);
    const p = r.projected(1, true);
    expect(p.extraPlay).toBe(10);
    expect(p.settlement).toMatchObject({ outcome: 'win', play: 10, ante: 10, blind: 10, net: 30, totalReturn: 60 });
    // player offline: the ×1 was never debited, the net stays the same relative to 20 staked
    expect(returnForStaked(p.settlement, p.extraPlay)).toBe(50);
    expect(r.projected(1, false).settlement).toMatchObject({ outcome: 'folded', net: -20, totalReturn: 0 });
    // decisions already taken stay
    r.decide(1, 'bet4');
    expect(r.projected(1, true)).toMatchObject({ extraPlay: 0, settlement: { play: 40 } });
  });
  it('a lost virtual ×1 never goes below zero', () => {
    const s = settleSeat({ ante: 10, trips: 0, play: 10, folded: false }, ev('2c 2d 7s 6h 5d Kc 3c'), ev('9c 8d 7s 6h 5d Kc 3c'), DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS);
    expect(s.totalReturn).toBe(0);
    expect(returnForStaked(s, 10)).toBe(0);
  });
});

describe('uth: limits, reservation, player bank', () => {
  const lim = { minAnte: 1, maxTotal: 100, tripsEnabled: true };
  it('W = 6 × Ante + Trips; Bronze (100) → Ante ≤ 16 without Trips', () => {
    expect(worstCaseTotal(16, 0)).toBe(96);
    expect(confirmCost(10, 5)).toBe(25);
    expect(anteRange(lim)).toEqual({ min: 1, max: 16 });
    expect(anteRange(lim, 10)).toEqual({ min: 1, max: 15 });
  });
  it('validateBets errors', () => {
    expect(validateBets(16, 0, lim, 1000)).toBeUndefined();
    expect(validateBets(17, 0, lim, 1000)).toEqual({ key: 'worst_case_max', max: 100 });
    expect(validateBets(10, 41, lim, 1000)).toEqual({ key: 'worst_case_max', max: 100 });
    expect(validateBets(0, 0, lim, 1000)).toEqual({ key: 'ante_min', min: 1 });
    expect(validateBets(0, 5, lim, 1000)).toEqual({ key: 'trips_needs_ante' });
    expect(validateBets(5, 5, { ...lim, tripsEnabled: false }, 1000)).toEqual({ key: 'trips_off' });
    expect(validateBets(5, 2, { ...lim, minAnte: 3 }, 1000)).toEqual({ key: 'trips_min', min: 3 });
    expect(validateBets(10, 0, lim, 19)).toEqual({ key: 'insufficient', balance: 19 });
    expect(validateBets(10, 0, lim, 29)).toEqual({ key: 'keep_for_river', keep: 10 });
    expect(validateBets(10, 0, lim, 30)).toBeUndefined();
  });
  it('reservation 505 × Ante + 50 × Trips (Ante 10 + Trips 10 → 5 550)', () => {
    expect(seatReservation(10, 10, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS)).toBe(5550);
    expect(seatReservation(1, 0, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS)).toBe(505);
    expect(reservationPerAnte(DEFAULT_BLIND_PAYS)).toBe(505);
  });
  it('bank coverage: largest Ante the bank still covers', () => {
    expect(maxCoveredAnte(5550, 10, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS)).toBe(10);
    expect(maxCoveredAnte(5549, 10, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS)).toBe(9);
    expect(maxCoveredAnte(100, 10, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS)).toBe(0);
  });
  it('banker settlement: rake only on a positive net', () => {
    expect(bankRound([-30, 10], 0.01)).toEqual({ bankerNet: 20, rake: 0, bankDelta: 20 });
    expect(bankRound([-300, -50], 0.01)).toEqual({ bankerNet: 350, rake: 3, bankDelta: 347 });
    expect(bankRound([5290, -20], 0.01)).toEqual({ bankerNet: -5270, rake: 0, bankDelta: -5270 });
  });
  it('taking the dealer seat', () => {
    const ok = { vipTier: 2, minVip: 2, owes: false, isOwner: false, seatTaken: false, betsConfirmed: false, bank: 1000, minBank: 1000, balance: 5000 };
    expect(checkBanker(ok)).toBeUndefined();
    expect(checkBanker({ ...ok, vipTier: 1 })).toBe('vip');
    expect(checkBanker({ ...ok, owes: true })).toBe('pvp_owing');
    expect(checkBanker({ ...ok, isOwner: true })).toBe('owner');
    expect(checkBanker({ ...ok, seatTaken: true })).toBe('seat_taken');
    expect(checkBanker({ ...ok, betsConfirmed: true })).toBe('seat_next_round');
    expect(checkBanker({ ...ok, bank: 999 })).toBe('min_bank');
    expect(checkBanker({ ...ok, bank: 6000 })).toBe('insufficient');
    expect(bankTooLow(999, 1000, 1, DEFAULT_BLIND_PAYS)).toBe(true);
    expect(bankTooLow(1000, 1000, 1, DEFAULT_BLIND_PAYS)).toBe(false);
    expect(bankTooLow(1010, 505, 2, DEFAULT_BLIND_PAYS)).toBe(false);
    expect(bankTooLow(1009, 505, 2, DEFAULT_BLIND_PAYS)).toBe(true);
  });
  it('rotation goes clockwise', () => {
    expect(nextClockwise([1, 3, 5], 3)).toBe(5);
    expect(nextClockwise([1, 3, 5], 5)).toBe(1);
    expect(nextClockwise([2], 2)).toBeUndefined();
  });
});

describe('uth: advancements and chaos hooks (§21.7)', () => {
  const royal = ev('As Ks Qs Js Ts 3h 4d');
  const dealerNQ = ev('7d 2c Qs Js Ts 3h 4d');
  it('uth_four_x needs a winning ×4 Play bet', () => {
    const win = settleSeat({ ante: 10, trips: 0, play: 40, folded: false }, ev('Ah Ad 9s 5h 7c Jd 3s'), dealerNQ, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS);
    expect(seatHooks(4, win, ev('Ah Ad 9s 5h 7c Jd 3s')).achievements).toEqual(['uth_four_x']);
    expect(seatHooks(3, { ...win, play: 30 }, ev('Ah Ad 9s 5h 7c Jd 3s')).achievements).toEqual([]);
    const lose = settleSeat({ ante: 10, trips: 0, play: 40, folded: false }, ev('7c 2d Ks Qh 9c 5d 3s'), ev('Ac 8d Ks Qh 9c 5d 3s'), DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS);
    expect(seatHooks(4, lose, ev('7c 2d Ks Qh 9c 5d 3s')).achievements).toEqual([]);
  });
  it('uth_royal when the Blind or the Trips paid on a royal; diamond rain only for the Blind', () => {
    const s = settleSeat({ ante: 10, trips: 5, play: 40, folded: false }, royal, dealerNQ, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS);
    expect(seatHooks(4, s, royal)).toEqual({ achievements: ['uth_four_x', 'uth_royal'], royalBlind: true });
    // folded royal (only Trips pays): advancement, no diamond rain
    const folded = settleSeat({ ante: 10, trips: 5, play: 0, folded: true }, royal, dealerNQ, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS);
    expect(seatHooks(0, folded, royal)).toEqual({ achievements: ['uth_royal'], royalBlind: false });
    // royal on the board, tie with the dealer, no Trips: nothing paid on it
    const tie = settleSeat({ ante: 10, trips: 0, play: 10, folded: false }, royal, royal, DEFAULT_BLIND_PAYS, DEFAULT_TRIPS_PAYS);
    expect(seatHooks(1, tie, royal)).toEqual({ achievements: [], royalBlind: false });
  });
  it('uth_house_seat: dealer seat in profit against at least two players', () => {
    expect(houseSeatEarned(347, 2)).toBe(true);
    expect(houseSeatEarned(347, 1)).toBe(false);
    expect(houseSeatEarned(0, 3)).toBe(false);
  });
});

describe('uth: worldgen preset hook / table kinds', () => {
  const cfg = { highRollerMinAnte: 50, highRollerMaxMultiplier: 2, highRollerMinVipTier: 2, minAnte: 1 };
  it('variants and presets', () => {
    expect(tableLimits(undefined, undefined, cfg)).toEqual({ kind: 'standard', min: 1, tierMultiplier: undefined, minTier: undefined });
    expect(tableLimits('high_roller', undefined, cfg)).toEqual({ kind: 'high_roller', min: 50, tierMultiplier: 2, minTier: 2 });
    expect(tableLimits('player_banked', undefined, cfg).kind).toBe('player_banked');
    expect(tableLimits(undefined, { id: 'high_roller_uth', minBet: 100 }, cfg)).toEqual({ kind: 'high_roller', min: 100, tierMultiplier: 2, minTier: 2 });
    expect(tableLimits(undefined, { id: 'standard' }, cfg).kind).toBe('standard');
  });
});

describe('uth: Trips edge, exact (§21.3)', () => {
  it('classifies all C(52,7) hands with the poker evaluator', () => {
    const counts = countSevenCardHands();
    expect(counts).toEqual(SEVEN_CARD_COUNTS);
    expect(Object.values(counts).reduce((s, x) => s + x, 0)).toBe(TOTAL_SEVEN_CARD_HANDS);
  });
  it('50-40-30-8-6-5-3: EV = −2 547 324 / 133 784 560 = −1.904 %', () => {
    expect(tripsEvNumerator(DEFAULT_TRIPS_PAYS)).toBe(-2_547_324);
    expect(tripsEdge(DEFAULT_TRIPS_PAYS)).toBeCloseTo(0.019040, 6);
  });
  it('variant 9-7-4: −1 206 516 (0.90 %)', () => {
    expect(tripsEvNumerator(VARIANT_TRIPS_PAYS)).toBe(-1_206_516);
    expect(tripsEdge(VARIANT_TRIPS_PAYS)).toBeCloseTo(0.009018, 6);
  });
});

describe('uth: reference strategy R and the base-game edge (§21.3, §21.8)', () => {
  it('preflop ×4 set is 500 of 1 326 hole pairs (37.7 %)', () => {
    let n = 0;
    let all = 0;
    for (let i = 0; i < 52; i++) {
      for (let j = i + 1; j < 52; j++) {
        all++;
        if (preflopBet4([i, j])) n++;
      }
    }
    expect(all).toBe(1326);
    expect(n).toBe(500);
    expect(n / all).toBeCloseTo(0.377, 3);
  });
  it('strategy details', () => {
    expect(preflopBet4(pcs('3c 3d'))).toBe(true);
    expect(preflopBet4(pcs('2c 2d'))).toBe(false);
    expect(preflopBet4(pcs('Kh 2h'))).toBe(true);
    expect(preflopBet4(pcs('Kh 4d'))).toBe(false);
    expect(preflopBet4(pcs('Qh 6h'))).toBe(true);
    expect(preflopBet4(pcs('Qh 7d'))).toBe(false);
    expect(preflopBet4(pcs('Jh Td'))).toBe(true);
    expect(preflopBet4(pcs('Jh 9d'))).toBe(false);
    expect(flopBet2(pcs('2c 2d'), pcs('9s 5h 7c'))).toBe(false);
    expect(flopBet2(pcs('7d 3c'), pcs('9s 5h 7c'))).toBe(true);
    expect(flopBet2(pcs('Th 3h'), pcs('9h 5c 7c'))).toBe(false);
    expect(flopBet2(pcs('9h 3h'), pcs('Th 5h 7c'))).toBe(false); // hole suit card below 10
    expect(flopBet2(pcs('Th 3h'), pcs('9h 5h 7h'))).toBe(true);
    expect(flopBet2(pcs('9h 3h'), pcs('8h 5h 7h'))).toBe(true); // made flush
    expect(flopBet2(pcs('4d 3h'), pcs('9s 9h 9c'))).toBe(true); // board trips
  });
  it('the fast measurement agrees with the round engine and the policy', () => {
    const rng = seededRng(99);
    for (let k = 0; k < 40; k++) {
      const deck = shuffledDeck(rng);
      const r = new UthRound(deck, [{ seat: 1, id: 'p', ante: 2, trips: 0 }], RULES);
      while (r.street !== 'showdown') {
        const s = r.seat(1)!;
        if (r.needsDecision(s)) {
          const d = referencePolicy.decide({ street: r.street, hole: s.hole, board: r.visibleBoard(), ante: 2, trips: 0, options: r.legal(1, 1e9), blindPays: DEFAULT_BLIND_PAYS });
          r.decide(1, d);
        }
        r.advance();
      }
      const s = r.seat(1)!;
      const expected = s.folded ? -2 : betResultAntes(r.playerValue(s), r.dealerValue(), s.multiple, DEFAULT_BLIND_PAYS);
      expect(r.settle(1).net / 2).toBeCloseTo(expected, 9);
    }
  });
  it('Monte-Carlo over boards: house edge ≈ 2.27 % of the Ante with strategy R', () => {
    // UTH_EDGE_BOARDS=130000 gives SE ≈ 0.1 % (the §21.8 precision; minutes). CI default: 1 500.
    const boards = Number(process.env.UTH_EDGE_BOARDS ?? 1500);
    const st = measureEdge(seededRng(20260924), boards, DEFAULT_BLIND_PAYS);
    const { edge, se, elementOfRisk } = edgeOf(st);
    if (process.env.UTH_EDGE_BOARDS) console.info(`UTH edge ${(edge * 100).toFixed(3)} % ± ${(se * 100).toFixed(3)} (boards ${boards}), element of risk ${(elementOfRisk * 100).toFixed(3)} %, ×4 ${st.bet4 / st.hands}, ×2 ${st.bet2 / st.hands}, ×1 ${st.bet1 / st.hands}, fold ${st.fold / st.hands}`);
    expect(Math.abs(edge - 0.0227)).toBeLessThan(Math.max(4 * se, 0.0015));
    if (boards >= 100_000) {
      expect(edge).toBeGreaterThanOrEqual(0.0195);
      expect(edge).toBeLessThanOrEqual(0.026);
    }
    expect(elementOfRisk).toBeGreaterThan(0.002);
    expect(elementOfRisk).toBeLessThan(0.009);
    expect(st.bet4 / st.hands).toBeCloseTo(0.377, 2);
    expect(Math.abs(st.fold / st.hands - 0.191)).toBeLessThan(0.02);
    expect(Math.abs(st.bet2 / st.hands - 0.214)).toBeLessThan(0.02);
    expect(Math.abs(st.bet1 / st.hands - 0.217)).toBeLessThan(0.02);
  }, 600_000);
});

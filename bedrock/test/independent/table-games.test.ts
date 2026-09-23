/**
 * Independent tester suite: roulette (§9), blackjack (§6), craps (§10).
 * Expected values are derived from GAME_DESIGN.md by hand or by exact enumeration here.
 */
import { describe, expect, it } from 'vitest';
import { parseCard } from '../../src/core/logic/cards';
import { seededRng } from '../../src/core/logic/rng';
import { BlackjackRound, DEFAULT_RULES, Shoe, StackedSource, type BlackjackRules } from '../../src/games/blackjack/logic';
import { applyRoll, DEFAULT_RULES as CRAPS, fieldReturn, maxOdds, oddsWin, resolveBet, type Bet as CrapsBet, type BetKind, type PointNumber } from '../../src/games/craps/logic/rules';
import { allSpots, BET_TYPES, betReturn, isValidSpot, makeSpot, type BetType } from '../../src/games/roulette/logic/bets';
import { RED_NUMBERS, WHEEL_ORDER } from '../../src/games/roulette/logic/wheel';

// ---------------------------------------------------------------------------------------------
describe('roulette §9', () => {
  const red = [1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36];
  it('red numbers and wheel order are the European ones', () => {
    expect([...RED_NUMBERS].sort((a, b) => a - b)).toEqual(red);
    const order = '0-32-15-19-4-21-2-25-17-34-6-27-13-36-11-30-8-23-10-5-24-16-33-1-20-14-31-9-22-18-29-7-28-12-35-3-26'.split('-').map(Number);
    expect([...WHEEL_ORDER]).toEqual(order);
    // colors alternate around the wheel after the zero
    for (let i = 1; i < order.length - 1; i++) expect(RED_NUMBERS.has(order[i]!)).not.toBe(RED_NUMBERS.has(order[i + 1]!));
  });

  // layout: row r = 0..11 holds 3r+1, 3r+2, 3r+3 (columns 0..2)
  const at = (r: number, c: number) => 3 * r + c + 1;
  const expected: Record<string, number[][]> = {
    straight: Array.from({ length: 37 }, (_, n) => [n]),
    split: [
      [0, 1],
      [0, 2],
      [0, 3],
      ...Array.from({ length: 12 }, (_, r) => [0, 1].map((c) => [at(r, c), at(r, c + 1)])).flat(),
      ...Array.from({ length: 11 }, (_, r) => [0, 1, 2].map((c) => [at(r, c), at(r + 1, c)])).flat(),
    ],
    street: Array.from({ length: 12 }, (_, r) => [at(r, 0), at(r, 1), at(r, 2)]),
    trio: [
      [0, 1, 2],
      [0, 2, 3],
    ],
    corner: Array.from({ length: 11 }, (_, r) => [0, 1].map((c) => [at(r, c), at(r, c + 1), at(r + 1, c), at(r + 1, c + 1)])).flat(),
    first_four: [[0, 1, 2, 3]],
    six_line: Array.from({ length: 11 }, (_, r) => [0, 1, 2].flatMap((c) => [at(r, c), at(r + 1, c)])),
  };
  const key = (ns: readonly number[]) => [...ns].sort((a, b) => a - b).join('-');
  for (const [type, spots] of Object.entries(expected)) {
    it(`${type}: exactly the ${spots.length} spots of the layout`, () => {
      expect(new Set(allSpots(type as BetType).map((s) => key(s.numbers)))).toEqual(new Set(spots.map(key)));
      expect(allSpots(type as BetType)).toHaveLength(spots.length);
    });
  }
  it('counts: 60 splits, 22 corners, 11 six lines', () => {
    expect(expected.split).toHaveLength(60);
    expect(expected.corner).toHaveLength(22);
    expect(expected.six_line).toHaveLength(11);
  });
  it('rejects non-adjacent geometry', () => {
    expect(isValidSpot(makeSpot('split', [3, 4]))).toBe(false); // row wrap
    expect(isValidSpot(makeSpot('split', [0, 4]))).toBe(false);
    expect(isValidSpot(makeSpot('corner', [3, 4, 6, 7]))).toBe(false);
    expect(isValidSpot(makeSpot('street', [2, 3, 4]))).toBe(false);
    expect(isValidSpot(makeSpot('six_line', [34, 35, 36, 37, 38, 39]))).toBe(false);
    expect(isValidSpot(makeSpot('trio', [0, 1, 3]))).toBe(false);
  });
  it('every bet type and spot has RTP 36/37 (HE 2.70 %)', () => {
    for (const type of BET_TYPES) {
      for (const s of allSpots(type)) {
        let ret = 0;
        for (let r = 0; r <= 36; r++) ret += betReturn({ ...s, amount: 1 }, r);
        expect(ret / 37).toBeCloseTo(36 / 37, 12);
      }
    }
  });
  it('outside bets lose on 0; la partage halves even-money losses on 0 (HE 1.35 %)', () => {
    for (const type of ['dozen', 'column', 'red', 'black', 'odd', 'even', 'low', 'high'] as const) expect(betReturn({ ...allSpots(type)[0]!, amount: 10 }, 0)).toBe(0);
    let ret = 0;
    for (let r = 0; r <= 36; r++) ret += betReturn({ ...allSpots('red')[0]!, amount: 2 }, r, true);
    expect(1 - ret / 2 / 37).toBeCloseTo(0.0135, 4);
    expect(betReturn({ ...allSpots('dozen')[0]!, amount: 10 }, 0, true)).toBe(0); // only even-money
  });
});

// ---------------------------------------------------------------------------------------------
describe('blackjack §6', () => {
  const deal = (ids: string[], bets = [10], rules: Partial<BlackjackRules> = {}) =>
    new BlackjackRound({ ...DEFAULT_RULES, ...rules }, new StackedSource(ids.map(parseCard), new Shoe(seededRng(1), 6)), bets.map((bet, i) => ({ seat: i + 1, id: `p${i}`, bet })));
  // deal order: seat cards 1, dealer up, seat cards 2, dealer hole, then draws

  it('defaults: 6 decks, S17, 3:2, DAS, 4 hands, no RSA, no surrender', () => {
    expect(DEFAULT_RULES).toMatchObject({ decks: 6, dealerHitsSoft17: false, blackjackPayout: 1.5, doubleAfterSplit: true, maxHands: 4, resplitAces: false, lateSurrender: false, penetration: 0.75 });
  });
  it('blackjack pays 3:2, floored (bet 5 -> +7)', () => {
    const r = deal(['AS', '9H', 'KD', '7C'], [5]);
    expect(r.phase).toBe('done');
    expect(r.returnOf(1)).toBe(12);
    const r2 = deal(['AS', '9H', 'KD', '7C'], [10]);
    expect(r2.returnOf(1)).toBe(25);
  });
  it('dealer peeks on a 10-value up card: dealer BJ settles before any action; player BJ pushes', () => {
    const r3 = deal(['9S', 'KH', '8D', 'AC'], [10]); // player 9,8 vs dealer K + A (BJ)
    expect(r3.peeked).toBe(true);
    expect(r3.dealerBlackjack).toBe(true);
    expect(r3.phase).toBe('done');
    expect(r3.returnOf(1)).toBe(0);
    const r4 = deal(['AS', 'KH', 'QD', 'AC'], [10]); // player A,Q vs dealer K + A
    expect(r4.returnOf(1)).toBe(10);
  });
  it('no peek (and no dealer BJ possible) on a 9 up card', () => {
    const r = deal(['9S', '9H', '8D', 'AC', '5C'], [10]);
    expect(r.peeked).toBe(false);
    expect(r.phase).toBe('turns');
  });
  it('insurance: offered on an ace, half the bet, pays 2:1; main bet lost to dealer BJ', () => {
    const r = deal(['9S', 'AH', '8D', 'KC'], [10]);
    expect(r.phase).toBe('insurance');
    expect(r.insuranceOffer(1)).toBe('insurance');
    r.decideInsurance(1, true);
    expect(r.stakedOf(1)).toBe(15);
    expect(r.returnOf(1)).toBe(15); // 5 insurance × 3 ; net 0 ("insurance saves the bet")
  });
  it('insurance is lost when the dealer has no blackjack, and play continues', () => {
    const r = deal(['9S', 'AH', '8D', '7C'], [10]);
    r.decideInsurance(1, true);
    expect(r.phase).toBe('turns');
    r.act(1, 'stand'); // 17 vs A7 soft 18 (S17 stands)
    expect(r.returnOf(1)).toBe(0);
    expect(r.stakedOf(1)).toBe(15);
  });
  it('even money: player BJ vs ace pays 1:1 immediately, whatever the hole card', () => {
    for (const hole of ['KC', '7C']) {
      const r = deal(['AS', 'AH', 'KD', hole], [10]);
      expect(r.insuranceOffer(1)).toBe('even_money');
      r.decideInsurance(1, true);
      expect(r.returnOf(1)).toBe(20);
    }
  });
  it('split aces: one card each, no further action, A+10 = 21 pays 1:1 (not blackjack)', () => {
    const r = deal(['AS', '9H', 'AD', '7C', 'KD', 'QH', '5S'], [10]);
    expect(r.legal(1)).toContain('split');
    r.act(1, 'split');
    const s = r.seat(1)!;
    expect(s.hands.map((h) => h.cards.length)).toEqual([2, 2]);
    expect(r.phase).toBe('done'); // both hands done -> dealer played (9+7=16, draws 5 = 21?)
    // dealer 9,7 then draws the next card; with 5S dealer makes 21: both 21s push
    expect(s.hands.every((h) => h.outcome !== 'blackjack')).toBe(true);
  });
  it('split aces vs a dealer bust: each 21 pays exactly 1:1', () => {
    const r = deal(['AS', '9H', 'AD', '7C', 'KD', 'QH', '6S'], [10]);
    r.act(1, 'split');
    expect(r.returnOf(1)).toBe(40); // 2 hands × 10 × 2
  });
  it('no re-split of aces by default; other pairs re-split to 4 hands', () => {
    const a = deal(['AS', '9H', 'AD', '7C', 'AC', 'AH'], [10]);
    a.act(1, 'split');
    expect(a.seat(1)!.hands).toHaveLength(2);
    const r = deal(['8S', '9H', '8D', '7C', '8C', '8H', '8S', '2C', '2D', '2H', '2S'], [10]);
    for (let i = 0; i < 5; i++) if (r.legal(1).includes('split')) r.act(1, 'split');
    expect(r.seat(1)!.hands.length).toBe(4);
    expect(r.legal(1)).not.toContain('split');
  });
  it('K+Q is not a pair (same rank only)', () => {
    const r = deal(['KS', '9H', 'QD', '7C'], [10]);
    expect(r.legal(1)).not.toContain('split');
  });
  it('double after split is allowed; double takes exactly one card', () => {
    const r = deal(['8S', '6H', '8D', '9C', '3C', '2D', 'KH', 'KS', 'KD'], [10]);
    r.act(1, 'split'); // hands 8,3 and 8,2
    expect(r.legal(1)).toContain('double');
    r.act(1, 'double');
    expect(r.seat(1)!.hands[0]!.cards).toHaveLength(3);
    expect(r.seat(1)!.hands[0]!.bet).toBe(20);
    expect(r.stakedOf(1)).toBe(30);
  });
  it('dealer stands on soft 17 (S17) and hits it under H17', () => {
    const s17 = deal(['KS', 'AH', '9D', '6C', '5S'], [10]);
    s17.decideInsurance(1, false); // ace up: insurance first, then the peek (A+6 is no BJ)
    s17.act(1, 'stand');
    expect(s17.dealer).toHaveLength(2);
    expect(s17.returnOf(1)).toBe(20); // 19 beats soft 17
    const h17 = deal(['KS', 'AH', '9D', '6C', '5S'], [10], { dealerHitsSoft17: true });
    h17.decideInsurance(1, false);
    h17.act(1, 'stand');
    expect(h17.dealer.length).toBeGreaterThan(2);
  });
  it('dealer does not draw when every player hand is bust', () => {
    const r = deal(['KS', '5H', '6D', 'KC', 'QS'], [10]);
    r.act(1, 'hit'); // 26
    expect(r.dealer).toHaveLength(2);
    expect(r.returnOf(1)).toBe(0);
  });
  it('insurance HE for 6 decks is 7.40 % (spec §6.1 says 7.47 %, the 8-deck figure)', () => {
    const tens = 6 * 16;
    const unseen = 6 * 52 - 1; // the dealer's ace
    expect(1 - (3 * tens) / unseen).toBeCloseTo(0.074, 3);
    expect(1 - (3 * 8 * 16) / (8 * 52 - 1)).toBeCloseTo(0.0747, 4);
  });
});

// ---------------------------------------------------------------------------------------------
describe('craps §10', () => {
  const P: number[] = [];
  for (let t = 2; t <= 12; t++) P[t] = (6 - Math.abs(7 - t)) / 36;
  const bet = (kind: BetKind, flat = 1, odds = 0, point?: PointNumber): CrapsBet => ({ id: 'b', owner: 'o', kind, flat, odds, point });

  /** Exact expected total return of one bet, by value iteration over the table state. */
  function expectedReturn(b: CrapsBet, tablePoint: PointNumber | undefined): number {
    const memo = new Map<string, number>();
    const V = (bb: CrapsBet, tp: PointNumber | undefined, depth: number): number => {
      const k = `${bb.point}|${tp}|${depth}`;
      if (memo.has(k)) return memo.get(k)!;
      if (depth > 400) return bb.flat + bb.odds; // negligible tail
      let v = 0;
      for (let t = 2; t <= 12; t++) {
        const r = applyRoll(tp, [bb], [Math.min(6, t - 1), t - Math.min(6, t - 1)], CRAPS);
        const res = r.resolutions[0]!;
        if (res.outcome === 'stay' || res.outcome === 'move') v += P[t]! * V(r.remaining[0]!, r.nextPoint, depth + 1);
        else v += P[t]! * res.totalReturn;
      }
      memo.set(k, v);
      return v;
    };
    return V(b, tablePoint, 0);
  }

  it('Pass line HE 1.41 % (7/495)', () => expect(1 - expectedReturn(bet('pass'), undefined) / 1).toBeCloseTo(7 / 495, 6));
  it("Don't Pass HE 1.36 % (bar 12)", () => expect(1 - expectedReturn(bet('dont_pass'), undefined)).toBeCloseTo(3 / 220, 6));
  it('Come / Don\'t Come have the line-bet edges', () => {
    expect(1 - expectedReturn(bet('come'), 6)).toBeCloseTo(7 / 495, 6);
    expect(1 - expectedReturn(bet('dont_come'), 6)).toBeCloseTo(3 / 220, 6);
  });
  it('Field: 2 pays 2:1, 12 pays 3:1, HE 2.78 %', () => {
    expect(fieldReturn(2, 10, CRAPS)).toBe(30);
    expect(fieldReturn(12, 10, CRAPS)).toBe(40);
    for (const t of [3, 4, 9, 10, 11]) expect(fieldReturn(t, 10, CRAPS)).toBe(20);
    for (const t of [5, 6, 7, 8]) expect(fieldReturn(t, 10, CRAPS)).toBe(0);
    let ev = 0;
    for (let t = 2; t <= 12; t++) ev += P[t]! * fieldReturn(t, 1, CRAPS);
    expect(1 - ev).toBeCloseTo(1 / 36, 10);
  });
  it('Odds pay true odds (0 % edge) for take and lay on every point', () => {
    for (const p of [4, 5, 6, 8, 9, 10] as PointNumber[]) {
      const pw = P[p]! / (P[p]! + P[7]!);
      const take = 60; // divisible by every unit
      expect(pw * (take + oddsWin('take', p, take)) + (1 - pw) * 0).toBeCloseTo(take, 9);
      expect((1 - pw) * (take + oddsWin('lay', p, take))).toBeCloseTo(take, 9);
    }
  });
  // §10.1 says the lay max is the "amount that wins 6× the flat bet"; the implementation (and
  // real 3-4-5× tables) lay up to 6× the flat bet, i.e. the lay WINS 3/4/5× the flat bet.
  // Reported as a spec wording issue; both are 0 % edge.
  it('3-4-5× max take odds; lay max is 6× the flat bet (wins 3-4-5×)', () => {
    expect(maxOdds('take', 4, 10, CRAPS)).toBe(30);
    expect(maxOdds('take', 5, 10, CRAPS)).toBe(40);
    expect(maxOdds('take', 6, 10, CRAPS)).toBe(50);
    for (const p of [4, 5, 6, 8, 9, 10] as PointNumber[]) expect(maxOdds('lay', p, 10, CRAPS)).toBe(60);
  });
  it('Pass + full 3-4-5× odds ≈ 0.37 % combined edge', () => {
    // flat 10; after a point p the player adds max odds
    let ret = 0;
    let staked = 0;
    for (let t = 2; t <= 12; t++) {
      if (t === 7 || t === 11) {
        ret += P[t]! * 20;
        staked += P[t]! * 10;
      } else if (t === 2 || t === 3 || t === 12) staked += P[t]! * 10;
      else {
        const p = t as PointNumber;
        const odds = maxOdds('take', p, 10, CRAPS);
        const pw = P[p]! / (P[p]! + P[7]!);
        ret += P[t]! * pw * (20 + odds + oddsWin('take', p, odds));
        staked += P[t]! * (10 + odds);
      }
    }
    expect(1 - ret / staked).toBeCloseTo(0.0037, 4);
  });
  it('come-bet odds are off on the come-out roll (returned on a 7)', () => {
    const r = resolveBet(bet('come', 10, 20, 6), undefined, 7, CRAPS);
    expect(r.outcome).toBe('lose');
    expect(r.totalReturn).toBe(20);
    const w = resolveBet(bet('come', 10, 20, 6), undefined, 6, CRAPS);
    expect(w.totalReturn).toBe(40); // flat 1:1, odds returned without winnings
  });
  it("seven-out: pass loses, don't pass wins, come points lose, don't come points win", () => {
    const bets = [bet('pass', 10), { ...bet('dont_pass', 10), id: 'dp' }, { ...bet('come', 10, 0, 8), id: 'c' }, { ...bet('dont_come', 10, 0, 9), id: 'dc' }];
    const r = applyRoll(5, bets, [3, 4], CRAPS);
    expect(r.event.kind).toBe('seven_out');
    expect(r.resolutions.map((x) => x.totalReturn)).toEqual([0, 20, 0, 20]);
    expect(r.nextPoint).toBeUndefined();
  });
});

import { describe, expect, it } from 'vitest';
import { seededRng } from '../../../core/logic/rng';
import {
  type Bet,
  type BetKind,
  type CrapsRules,
  type PointNumber,
  DEFAULT_RULES,
  POINT_NUMBERS,
  applyRoll,
  autoComplete,
  fieldReturn,
  flatWorstCase,
  maxOdds,
  nextShooter,
  oddsUnit,
  oddsWin,
  oddsWorstCase,
  resolveBet,
  rollDice,
  rollEvent,
  snapOdds,
} from './rules';

const R = DEFAULT_RULES;
const bet = (kind: BetKind, flat = 10, extra: Partial<Bet> = {}): Bet => ({ id: 'x', owner: 'p', kind, flat, odds: 0, ...extra });
/** ways to roll each total with 2d6 */
const WAYS: Record<number, number> = { 2: 1, 3: 2, 4: 3, 5: 4, 6: 5, 7: 6, 8: 5, 9: 4, 10: 3, 11: 2, 12: 1 };
const P = (t: number) => (WAYS[t] ?? 0) / 36;

describe('payout tables', () => {
  it('field pays 1:1 on 3,4,9,10,11, 2:1 on 2, 3:1 on 12, loses on 5-8', () => {
    const expected: Record<number, number> = { 2: 30, 3: 20, 4: 20, 5: 0, 6: 0, 7: 0, 8: 0, 9: 20, 10: 20, 11: 20, 12: 40 };
    for (let t = 2; t <= 12; t++) expect(fieldReturn(t, 10, R)).toBe(expected[t]);
    expect(fieldReturn(12, 10, { ...R, fieldPays12: 2 })).toBe(30);
  });

  it('take odds pay true odds 2:1, 3:2, 6:5', () => {
    expect(oddsWin('take', 4, 10)).toBe(20);
    expect(oddsWin('take', 10, 10)).toBe(20);
    expect(oddsWin('take', 5, 10)).toBe(15);
    expect(oddsWin('take', 9, 10)).toBe(15);
    expect(oddsWin('take', 6, 10)).toBe(12);
    expect(oddsWin('take', 8, 25)).toBe(30);
  });

  it('lay odds pay 1:2, 2:3, 5:6', () => {
    expect(oddsWin('lay', 4, 20)).toBe(10);
    expect(oddsWin('lay', 5, 30)).toBe(20);
    expect(oddsWin('lay', 6, 60)).toBe(50);
  });

  it('odds units make payouts whole', () => {
    expect(POINT_NUMBERS.map((p) => oddsUnit('take', p))).toEqual([1, 2, 5, 5, 2, 1]);
    expect(POINT_NUMBERS.map((p) => oddsUnit('lay', p))).toEqual([2, 3, 6, 6, 3, 2]);
    for (const p of POINT_NUMBERS)
      for (const side of ['take', 'lay'] as const) {
        const u = oddsUnit(side, p);
        for (let k = 1; k < 20; k++) expect(Number.isInteger(oddsWin(side, p, k * u))).toBe(true);
      }
  });

  it('max odds are 3-4-5x (take) and win 3-4-5x (lay, i.e. 6x the flat bet)', () => {
    expect(POINT_NUMBERS.map((p) => maxOdds('take', p, 10, R))).toEqual([30, 40, 50, 50, 40, 30]);
    expect(POINT_NUMBERS.map((p) => maxOdds('lay', p, 10, R))).toEqual([60, 60, 60, 60, 60, 60]);
    // snapped to whole payouts
    expect(maxOdds('take', 5, 1, { ...R, maxOdds5_9: 3 })).toBe(2);
    expect(maxOdds('take', 6, 3, { ...R, maxOdds6_8: 1 })).toBe(0);
    expect(maxOdds('lay', 9, 1, { ...R, maxOdds5_9: 1 })).toBe(0);
    expect(maxOdds('take', 4, 10, { ...R, maxOdds4_10: 0 })).toBe(0);
  });

  it('snapOdds rounds down to the unit', () => {
    expect(snapOdds(27, 5)).toBe(25);
    expect(snapOdds(4, 5)).toBe(0);
    expect(snapOdds(12.9, 6)).toBe(12);
  });

  it('worst cases', () => {
    expect(flatWorstCase('pass', 10, R)).toBe(20);
    expect(flatWorstCase('field', 10, R)).toBe(40);
    expect(oddsWorstCase('take', 4, 30)).toBe(90);
    expect(oddsWorstCase('lay', 4, 60)).toBe(90);
  });
});

describe('come-out and point', () => {
  it('rollEvent state machine', () => {
    expect(rollEvent(undefined, 7).event.kind).toBe('natural');
    expect(rollEvent(undefined, 11).event.kind).toBe('natural');
    for (const t of [2, 3, 12]) expect(rollEvent(undefined, t).event.kind).toBe('craps');
    for (const p of POINT_NUMBERS) expect(rollEvent(undefined, p)).toEqual({ event: { kind: 'point_set', point: p }, nextPoint: p });
    expect(rollEvent(6, 6)).toEqual({ event: { kind: 'point_made', point: 6 }, nextPoint: undefined });
    expect(rollEvent(6, 7)).toEqual({ event: { kind: 'seven_out', point: 6 }, nextPoint: undefined });
    expect(rollEvent(6, 11)).toEqual({ event: { kind: 'roll', total: 11, point: 6 }, nextPoint: 6 });
  });

  it('pass line', () => {
    expect(resolveBet(bet('pass'), undefined, 7, R)).toMatchObject({ outcome: 'win', totalReturn: 20 });
    expect(resolveBet(bet('pass'), undefined, 11, R)).toMatchObject({ outcome: 'win', totalReturn: 20 });
    for (const t of [2, 3, 12]) expect(resolveBet(bet('pass'), undefined, t, R)).toMatchObject({ outcome: 'lose', totalReturn: 0 });
    expect(resolveBet(bet('pass'), undefined, 6, R).outcome).toBe('stay');
    expect(resolveBet(bet('pass', 10, { odds: 50 }), 6, 6, R)).toMatchObject({ outcome: 'win', totalReturn: 20 + 50 + 60 });
    expect(resolveBet(bet('pass', 10, { odds: 50 }), 6, 7, R)).toMatchObject({ outcome: 'lose', totalReturn: 0 });
    expect(resolveBet(bet('pass'), 6, 11, R).outcome).toBe('stay');
  });

  it("don't pass with bar 12", () => {
    expect(resolveBet(bet('dont_pass'), undefined, 2, R)).toMatchObject({ outcome: 'win', totalReturn: 20 });
    expect(resolveBet(bet('dont_pass'), undefined, 3, R)).toMatchObject({ outcome: 'win', totalReturn: 20 });
    expect(resolveBet(bet('dont_pass'), undefined, 12, R)).toMatchObject({ outcome: 'push', totalReturn: 10 });
    expect(resolveBet(bet('dont_pass'), undefined, 7, R)).toMatchObject({ outcome: 'lose' });
    expect(resolveBet(bet('dont_pass'), undefined, 11, R)).toMatchObject({ outcome: 'lose' });
    expect(resolveBet(bet('dont_pass', 10, { odds: 60 }), 4, 7, R)).toMatchObject({ outcome: 'win', totalReturn: 20 + 60 + 30 });
    expect(resolveBet(bet('dont_pass', 10, { odds: 60 }), 4, 4, R)).toMatchObject({ outcome: 'lose', totalReturn: 0 });
  });

  it('come bet travels and resolves; odds off on the come-out', () => {
    expect(resolveBet(bet('come'), 6, 7, R)).toMatchObject({ outcome: 'win', totalReturn: 20 });
    expect(resolveBet(bet('come'), 6, 12, R)).toMatchObject({ outcome: 'lose', totalReturn: 0 });
    expect(resolveBet(bet('come'), 6, 5, R)).toMatchObject({ outcome: 'move', movedTo: 5 });
    const onFive = bet('come', 10, { point: 5, odds: 20 });
    expect(resolveBet(onFive, 6, 5, R)).toMatchObject({ outcome: 'win', totalReturn: 20 + 20 + 30 });
    expect(resolveBet(onFive, 6, 7, R)).toMatchObject({ outcome: 'lose', totalReturn: 0 });
    // come-out roll: odds are off
    expect(resolveBet(onFive, undefined, 5, R)).toMatchObject({ outcome: 'win', totalReturn: 20 + 20, oddsReturned: true });
    expect(resolveBet(onFive, undefined, 7, R)).toMatchObject({ outcome: 'lose', totalReturn: 20, oddsReturned: true });
    expect(resolveBet(onFive, undefined, 6, R).outcome).toBe('stay');
  });

  it("don't come mirrors don't pass; lay odds always work", () => {
    expect(resolveBet(bet('dont_come'), 6, 12, R)).toMatchObject({ outcome: 'push', totalReturn: 10 });
    expect(resolveBet(bet('dont_come'), 6, 3, R)).toMatchObject({ outcome: 'win', totalReturn: 20 });
    expect(resolveBet(bet('dont_come'), 6, 11, R)).toMatchObject({ outcome: 'lose' });
    expect(resolveBet(bet('dont_come'), 6, 9, R)).toMatchObject({ outcome: 'move', movedTo: 9 });
    const onNine = bet('dont_come', 10, { point: 9, odds: 30 });
    expect(resolveBet(onNine, undefined, 7, R)).toMatchObject({ outcome: 'win', totalReturn: 20 + 30 + 20 });
    expect(resolveBet(onNine, 6, 9, R)).toMatchObject({ outcome: 'lose', totalReturn: 0 });
  });

  it('seven-out settles line and come bets together', () => {
    const bets: Bet[] = [
      { id: 'a', owner: 'p', kind: 'pass', flat: 10, odds: 30 },
      { id: 'b', owner: 'p', kind: 'dont_pass', flat: 10, odds: 0 },
      { id: 'c', owner: 'p', kind: 'come', flat: 10, odds: 0, point: 8 },
      { id: 'd', owner: 'p', kind: 'dont_come', flat: 10, odds: 0, point: 5 },
      { id: 'e', owner: 'p', kind: 'come', flat: 10, odds: 0 },
      { id: 'f', owner: 'p', kind: 'field', flat: 10, odds: 0 },
    ];
    const r = applyRoll(4, bets, [3, 4], R);
    expect(r.event.kind).toBe('seven_out');
    expect(r.nextPoint).toBeUndefined();
    expect(r.remaining).toEqual([]);
    expect(Object.fromEntries(r.resolutions.map((x) => [x.bet.id, x.totalReturn]))).toEqual({ a: 0, b: 20, c: 0, d: 20, e: 20, f: 0 });
  });

  it('applyRoll keeps moved come bets with their point', () => {
    const r = applyRoll(4, [bet('come')], [2, 4], R);
    expect(r.remaining).toEqual([{ ...bet('come'), point: 6 }]);
  });

  it('rollDice covers 1..6 uniformly', () => {
    const rng = seededRng(1);
    const counts = new Array(7).fill(0) as number[];
    for (let i = 0; i < 60000; i++) {
      const [a, b] = rollDice(rng);
      counts[a]!++;
      counts[b]!++;
    }
    for (let f = 1; f <= 6; f++) expect(Math.abs(counts[f]! / 120000 - 1 / 6)).toBeLessThan(0.01);
  });
});

describe('shooter rotation', () => {
  const seats = [
    { id: 'a', seat: 1, hasLineBet: true },
    { id: 'b', seat: 3, hasLineBet: false },
    { id: 'c', seat: 4, hasLineBet: true },
  ];
  it('moves clockwise and wraps', () => {
    expect(nextShooter(seats, 'a', false)).toBe('b');
    expect(nextShooter(seats, 'a', true)).toBe('c');
    expect(nextShooter(seats, 'c', true)).toBe('a');
    expect(nextShooter(seats, 'c', false)).toBe('a');
    expect(nextShooter(seats, undefined, true)).toBe('a');
  });
  it('keeps the only player', () => {
    expect(nextShooter([seats[0]!], 'a', false)).toBe('a');
    expect(nextShooter([seats[0]!], 'a', false, false)).toBeUndefined();
    expect(nextShooter([], 'a', false)).toBeUndefined();
    expect(nextShooter([seats[1]!], 'b', true)).toBeUndefined();
  });
  it('works when the current shooter already left', () => {
    expect(nextShooter(seats, 'gone', false)).toBe('a');
  });
});

// ---- house edge: exact enumeration + Monte-Carlo ----------------------------------------------

/** Exact expected TOTAL return of a bet from a state, by first-step analysis. */
function exact(b: Bet, tp: PointNumber | undefined, rules: CrapsRules, attachOdds?: (b: Bet, p: PointNumber) => Bet, depth = 0): number {
  if (depth > 4) throw new Error('state cycle');
  let acc = 0;
  let loop = 0;
  for (let t = 2; t <= 12; t++) {
    const r = resolveBet(b, tp, t, rules);
    const np = rollEvent(tp, t).nextPoint;
    if (r.outcome === 'win' || r.outcome === 'lose' || r.outcome === 'push') acc += P(t) * r.totalReturn;
    else if (r.outcome === 'move') acc += P(t) * exact({ ...b, point: r.movedTo }, 4, rules, attachOdds, depth + 1);
    else if ((b.kind === 'come' || b.kind === 'dont_come') && b.point !== undefined) loop += P(t);
    else if (np === tp) loop += P(t);
    else acc += P(t) * exact(attachOdds && np ? attachOdds(b, np) : b, np, rules, attachOdds, depth + 1);
  }
  return acc / (1 - loop);
}

describe('house edge (exact)', () => {
  it('Pass / Come 1.41 %', () => {
    expect(exact(bet('pass', 1), undefined, R) - 1).toBeCloseTo(-7 / 495, 12);
    expect(exact(bet('come', 1), 4, R) - 1).toBeCloseTo(-7 / 495, 12);
  });
  it("Don't Pass / Don't Come 1.36 %", () => {
    expect(exact(bet('dont_pass', 1), undefined, R) - 1).toBeCloseTo(-3 / 220, 12);
    expect(exact(bet('dont_come', 1), 4, R) - 1).toBeCloseTo(-3 / 220, 12);
  });
  it('Field 2.78 % (5.56 % when 12 pays 2:1)', () => {
    expect(exact(bet('field', 1), undefined, R) - 1).toBeCloseTo(-1 / 36, 12);
    expect(exact(bet('field', 1), undefined, { ...R, fieldPays12: 2 }) - 1).toBeCloseTo(-2 / 36, 12);
  });
  it('Odds have no edge', () => {
    for (const p of POINT_NUMBERS) {
      const take = exact({ ...bet('pass', 0), odds: 10 * oddsUnit('take', p) }, p, R);
      expect(take).toBeCloseTo(10 * oddsUnit('take', p), 9);
      const lay = exact({ ...bet('dont_pass', 0), odds: 10 * oddsUnit('lay', p) }, p, R);
      expect(lay).toBeCloseTo(10 * oddsUnit('lay', p), 9);
    }
  });
  it('Pass + full 3-4-5x odds ~0.37 % of the total wagered', () => {
    const flat = 10;
    const withOdds = (b: Bet, p: PointNumber): Bet => ({ ...b, odds: maxOdds('take', p, flat, R) });
    const ret = exact(bet('pass', flat), undefined, R, withOdds);
    const wagered = flat + POINT_NUMBERS.reduce((s, p) => s + P(p) * maxOdds('take', p, flat, R), 0);
    expect((wagered - ret) / wagered).toBeCloseTo(0.00374, 4);
  });
});

describe('house edge (Monte-Carlo)', () => {
  const N = 1_000_000;
  function play(kind: BetKind, seed: number, withOdds = false): { staked: number; returned: number } {
    const rng = seededRng(seed);
    let staked = 0;
    let returned = 0;
    for (let i = 0; i < N; i++) {
      let live: Bet[] = [{ id: 'x', owner: 'p', kind, flat: 10, odds: 0 }];
      // Come / Don't Come are placed with a point on (table point 4 stands in for "puck ON").
      let point: PointNumber | undefined = kind === 'come' || kind === 'dont_come' ? 4 : undefined;
      staked += 10;
      while (live.length) {
        const r = applyRoll(point, live, rollDice(rng), R);
        for (const x of r.resolutions) if (x.outcome === 'win' || x.outcome === 'lose' || x.outcome === 'push') returned += x.totalReturn;
        live = r.remaining;
        if (kind === 'come' || kind === 'dont_come') point = r.nextPoint ?? 4;
        else point = r.nextPoint;
        if (withOdds && r.event.kind === 'point_set' && live[0]) {
          const o = maxOdds(kind === 'pass' ? 'take' : 'lay', r.event.point, 10, R);
          live[0].odds = o;
          staked += o;
        }
      }
    }
    return { staked, returned };
  }
  const rtp = (x: { staked: number; returned: number }) => x.returned / x.staked;

  it('Pass RTP 98.59 %', () => expect(Math.abs(rtp(play('pass', 11)) - 0.98586)).toBeLessThan(0.003));
  it("Don't Pass RTP 98.64 %", () => expect(Math.abs(rtp(play('dont_pass', 12)) - 0.98636)).toBeLessThan(0.003));
  it('Come RTP 98.59 %', () => expect(Math.abs(rtp(play('come', 13)) - 0.98586)).toBeLessThan(0.003));
  it("Don't Come RTP 98.64 %", () => expect(Math.abs(rtp(play('dont_come', 14)) - 0.98636)).toBeLessThan(0.003));
  it('Field RTP 97.22 %', () => expect(Math.abs(rtp(play('field', 15)) - 0.97222)).toBeLessThan(0.003));
  it('Pass + 3-4-5x odds RTP ~99.63 %', () => expect(Math.abs(rtp(play('pass', 16, true)) - 0.99626)).toBeLessThan(0.003));
});

describe('autoComplete (player left: bets stay working)', () => {
  it('resolves every bet', () => {
    const bets: Bet[] = [
      { id: 'a', owner: 'p', kind: 'pass', flat: 10, odds: 50 },
      { id: 'b', owner: 'p', kind: 'come', flat: 5, odds: 0 },
      { id: 'c', owner: 'p', kind: 'dont_come', flat: 5, odds: 6, point: 8 },
      { id: 'd', owner: 'p', kind: 'field', flat: 3, odds: 0 },
    ];
    const out = autoComplete(6, bets, seededRng(3), R);
    expect([...out.keys()].sort()).toEqual(['a', 'b', 'c', 'd']);
    for (const v of out.values()) expect(v).toBeGreaterThanOrEqual(0);
  });
  it('has the same expected return as playing on', () => {
    const rng = seededRng(99);
    let ret = 0;
    const n = 200_000;
    for (let i = 0; i < n; i++) ret += autoComplete(undefined, [bet('pass', 1)], rng, R).get('x')!;
    expect(Math.abs(ret / n - (1 - 7 / 495))).toBeLessThan(0.006);
  });
  it('returns unresolved bets as a push at the safety cap', () => {
    expect(autoComplete(undefined, [bet('pass', 10, { odds: 0 })], seededRng(1), R, 0).get('x')).toBe(10);
  });
});

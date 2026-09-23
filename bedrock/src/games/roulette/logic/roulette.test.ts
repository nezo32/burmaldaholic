import { describe, expect, it } from 'vitest';
import { seededRng } from '../../../core/logic/rng';
import {
  BET_TYPES,
  type Bet,
  type BetType,
  PAYOUT,
  RED_NUMBERS,
  RouletteRound,
  WHEEL_ORDER,
  allSpots,
  betReturn,
  checkAdd,
  checkSpin,
  isValidSpot,
  makeSpot,
  maxAddable,
  outsideIndex,
  pocketColor,
  settleSlip,
  slipLimits,
  spinFrames,
  spinWheel,
  spotKey,
  wheelIndex,
  wheelWindow,
  worstCase,
} from './index';

const bet = (type: BetType, numbers: number[], amount = 10): Bet => ({ ...makeSpot(type, numbers), amount });

describe('wheel', () => {
  it('has 37 distinct pockets in the spec order', () => {
    expect(WHEEL_ORDER).toHaveLength(37);
    expect(new Set(WHEEL_ORDER).size).toBe(37);
    expect(WHEEL_ORDER.slice(0, 5)).toEqual([0, 32, 15, 19, 4]);
    expect(WHEEL_ORDER[36]).toBe(26);
  });
  it('colors: 18 red, 18 black, 0 green', () => {
    expect(RED_NUMBERS.size).toBe(18);
    expect(pocketColor(0)).toBe('green');
    expect(pocketColor(1)).toBe('red');
    expect(pocketColor(2)).toBe('black');
    expect(pocketColor(36)).toBe('red');
    expect(pocketColor(35)).toBe('black');
    // red and black alternate around the wheel
    for (let i = 1; i < 36; i++) expect(pocketColor(WHEEL_ORDER[i]!)).not.toBe(pocketColor(WHEEL_ORDER[i + 1]!));
  });
  it('spinWheel is uniform over 0..36', () => {
    const rng = seededRng(7);
    const counts = new Array(37).fill(0);
    const N = 370_000;
    for (let i = 0; i < N; i++) counts[spinWheel(rng)]++;
    for (const c of counts) expect(Math.abs(c - N / 37)).toBeLessThan(5 * Math.sqrt(N / 37));
  });
});

describe('bet geometry', () => {
  it('enumerates the standard number of positions', () => {
    const count = Object.fromEntries(BET_TYPES.map((t) => [t, allSpots(t).length]));
    expect(count).toEqual({
      straight: 37, split: 60, street: 12, trio: 2, corner: 22, first_four: 1, six_line: 11,
      dozen: 3, column: 3, red: 1, black: 1, odd: 1, even: 1, low: 1, high: 1,
    });
  });
  it('every enumerated spot is valid, unique and covers the right count', () => {
    const size: Record<BetType, number> = {
      straight: 1, split: 2, street: 3, trio: 3, corner: 4, first_four: 4, six_line: 6,
      dozen: 12, column: 12, red: 18, black: 18, odd: 18, even: 18, low: 18, high: 18,
    };
    for (const t of BET_TYPES) {
      const spots = allSpots(t);
      expect(new Set(spots.map(spotKey)).size).toBe(spots.length);
      for (const s of spots) {
        expect(isValidSpot(s)).toBe(true);
        expect(s.numbers).toHaveLength(size[t]);
      }
    }
  });
  it('accepts valid splits and rejects non-adjacent ones', () => {
    for (const ok of [[0, 1], [0, 2], [0, 3], [1, 2], [2, 3], [1, 4], [17, 18], [17, 20], [33, 36], [35, 36]]) expect(isValidSpot(makeSpot('split', ok))).toBe(true);
    for (const bad of [[3, 4], [6, 7], [1, 3], [1, 5], [0, 4], [36, 37], [5, 5], [17], [1, 2, 3]]) expect(isValidSpot(makeSpot('split', bad))).toBe(false);
  });
  it('streets, corners, six lines, trios, first four', () => {
    expect(isValidSpot(makeSpot('street', [1, 2, 3]))).toBe(true);
    expect(isValidSpot(makeSpot('street', [34, 35, 36]))).toBe(true);
    expect(isValidSpot(makeSpot('street', [2, 3, 4]))).toBe(false);
    expect(isValidSpot(makeSpot('street', [0, 1, 2]))).toBe(false);
    expect(isValidSpot(makeSpot('corner', [1, 2, 4, 5]))).toBe(true);
    expect(isValidSpot(makeSpot('corner', [2, 3, 5, 6]))).toBe(true);
    expect(isValidSpot(makeSpot('corner', [32, 33, 35, 36]))).toBe(true);
    expect(isValidSpot(makeSpot('corner', [3, 4, 6, 7]))).toBe(false);
    expect(isValidSpot(makeSpot('corner', [0, 1, 2, 3]))).toBe(false);
    expect(isValidSpot(makeSpot('six_line', [1, 2, 3, 4, 5, 6]))).toBe(true);
    expect(isValidSpot(makeSpot('six_line', [31, 32, 33, 34, 35, 36]))).toBe(true);
    expect(isValidSpot(makeSpot('six_line', [2, 3, 4, 5, 6, 7]))).toBe(false);
    expect(isValidSpot(makeSpot('trio', [0, 1, 2]))).toBe(true);
    expect(isValidSpot(makeSpot('trio', [0, 2, 3]))).toBe(true);
    expect(isValidSpot(makeSpot('trio', [0, 1, 3]))).toBe(false);
    expect(isValidSpot(makeSpot('first_four', [0, 1, 2, 3]))).toBe(true);
    expect(isValidSpot(makeSpot('first_four', [1, 2, 3, 4]))).toBe(false);
  });
  it('outside bets must cover exactly their fixed sets', () => {
    expect(isValidSpot(makeSpot('dozen', allSpots('dozen')[1]!.numbers))).toBe(true);
    expect(allSpots('dozen')[1]!.numbers[0]).toBe(13);
    expect(allSpots('column')[0]!.numbers.slice(0, 3)).toEqual([1, 4, 7]);
    expect(allSpots('column')[2]!.numbers.at(-1)).toBe(36);
    expect(outsideIndex(allSpots('column')[2]!)).toBe(3);
    expect(isValidSpot(makeSpot('red', [1, 3]))).toBe(false);
    expect(isValidSpot(makeSpot('low', allSpots('high')[0]!.numbers))).toBe(false);
    expect(allSpots('even')[0]!.numbers).not.toContain(0);
    expect(allSpots('odd')[0]!.numbers).not.toContain(0);
  });
  it('rejects out-of-range numbers and unknown types', () => {
    expect(isValidSpot(makeSpot('straight', [37]))).toBe(false);
    expect(isValidSpot(makeSpot('straight', [-1]))).toBe(false);
    expect(isValidSpot({ type: 'bogus' as BetType, numbers: [1] })).toBe(false);
  });
});

describe('payouts', () => {
  it('pays X:1 plus the stake', () => {
    expect(PAYOUT).toMatchObject({ straight: 35, split: 17, street: 11, trio: 11, corner: 8, first_four: 8, six_line: 5, dozen: 2, column: 2, red: 1 });
    expect(betReturn(bet('straight', [17]), 17)).toBe(360);
    expect(betReturn(bet('straight', [0]), 0)).toBe(360);
    expect(betReturn(bet('split', [17, 20]), 20)).toBe(180);
    expect(betReturn(bet('street', [16, 17, 18]), 16)).toBe(120);
    expect(betReturn(bet('trio', [0, 1, 2]), 0)).toBe(120);
    expect(betReturn(bet('corner', [17, 18, 20, 21]), 21)).toBe(90);
    expect(betReturn(bet('first_four', [0, 1, 2, 3]), 3)).toBe(90);
    expect(betReturn(bet('six_line', [16, 17, 18, 19, 20, 21]), 19)).toBe(60);
    expect(betReturn({ ...allSpots('dozen')[1]!, amount: 10 }, 24)).toBe(30);
    expect(betReturn({ ...allSpots('column')[1]!, amount: 10 }, 17)).toBe(30);
    expect(betReturn({ ...allSpots('red')[0]!, amount: 10 }, 1)).toBe(20);
    expect(betReturn({ ...allSpots('black')[0]!, amount: 10 }, 1)).toBe(0);
    expect(betReturn({ ...allSpots('straight')[5]!, amount: 10 }, 6)).toBe(0);
  });
  it('outside bets lose on zero; la partage returns half on even-money only', () => {
    for (const t of BET_TYPES) {
      if (t === 'straight' || t === 'split' || t === 'trio' || t === 'first_four') continue;
      for (const s of allSpots(t)) expect(betReturn({ ...s, amount: 11 }, 0)).toBe(0);
    }
    expect(betReturn({ ...allSpots('red')[0]!, amount: 11 }, 0, true)).toBe(5);
    expect(betReturn({ ...allSpots('low')[0]!, amount: 10 }, 0, true)).toBe(5);
    expect(betReturn({ ...allSpots('dozen')[0]!, amount: 10 }, 0, true)).toBe(0);
    expect(betReturn({ ...allSpots('red')[0]!, amount: 10 }, 1, true)).toBe(20);
  });
  it('settles a multi-bet slip and computes the worst case', () => {
    const slip = [bet('straight', [17], 5), { ...allSpots('black')[0]!, amount: 20 }, bet('split', [17, 18], 10)];
    const s = settleSlip(slip, 17);
    expect(s.staked).toBe(35);
    expect(s.returns).toEqual([180, 40, 180]);
    expect(s.totalReturn).toBe(400);
    expect(worstCase(slip)).toBe(400);
    expect(settleSlip(slip, 0).totalReturn).toBe(0);
    expect(worstCase([])).toBe(0);
  });
  it('every bet type has RTP exactly 36/37 (exact enumeration)', () => {
    for (const t of BET_TYPES) {
      for (const s of allSpots(t)) {
        let ret = 0;
        for (let r = 0; r <= 36; r++) ret += betReturn({ ...s, amount: 1 }, r);
        expect(ret / 37).toBeCloseTo(36 / 37, 12);
      }
    }
  });
  it('la partage halves the house edge of even-money bets (exact)', () => {
    for (const t of ['red', 'black', 'odd', 'even', 'low', 'high'] as const) {
      let ret = 0;
      for (let r = 0; r <= 36; r++) ret += betReturn({ ...allSpots(t)[0]!, amount: 2 }, r, true);
      expect(1 - ret / 2 / 37).toBeCloseTo(0.5 / 37, 12);
    }
  });
  it('Monte-Carlo: a mixed slip of every bet type returns 97.30 % ± 0.3 %', () => {
    const rng = seededRng(20260923);
    const N = 1_000_000;
    let staked = 0;
    let returned = 0;
    const pools = BET_TYPES.map((t) => allSpots(t));
    for (let i = 0; i < N; i++) {
      const slip = pools.map((p) => ({ ...p[Math.floor(rng.next() * p.length)]!, amount: 1 }));
      const s = settleSlip(slip, spinWheel(rng));
      staked += s.staked;
      returned += s.totalReturn;
    }
    expect(Math.abs(returned / staked - 36 / 37)).toBeLessThan(0.003);
  });
});

describe('limits', () => {
  const l = slipLimits({ tierMax: 100, minBet: 2, insideMaxFraction: 0.25 });
  it('derives inside and total caps', () => {
    expect(l).toEqual({ minBet: 2, insideMax: 25, totalMax: 100, minTotal: 0 });
    expect(slipLimits({ tierMax: 1000, minBet: 1, insideMaxFraction: 0.25, minTotal: 100 }).minTotal).toBe(100);
  });
  it('checks min bet, inside cap (merged per spot), total cap and geometry', () => {
    expect(checkAdd([], [bet('straight', [5], 1)], l)).toEqual({ code: 'bet_too_low', min: 2 });
    expect(checkAdd([], [bet('straight', [5], 25)], l)).toBeUndefined();
    expect(checkAdd([], [bet('straight', [5], 26)], l)).toEqual({ code: 'inside_max', max: 25 });
    expect(checkAdd([bet('straight', [5], 20)], [bet('straight', [5], 6)], l)).toEqual({ code: 'inside_max', max: 25 });
    expect(checkAdd([bet('straight', [5], 20)], [bet('straight', [6], 6)], l)).toBeUndefined();
    expect(checkAdd([], [{ ...allSpots('red')[0]!, amount: 100 }], l)).toBeUndefined();
    expect(checkAdd([bet('straight', [5], 20)], [{ ...allSpots('red')[0]!, amount: 81 }], l)).toEqual({ code: 'total_max', max: 100 });
    expect(checkAdd([], [bet('split', [3, 4], 5)], l)).toEqual({ code: 'invalid_position' });
    expect(checkAdd([], [bet('straight', [5], 2.5)], l)).toEqual({ code: 'invalid_amount' });
    expect(checkAdd([], [bet('straight', [5], 0)], l)).toEqual({ code: 'invalid_amount' });
  });
  it('rebet validates the whole set at once', () => {
    const last = [bet('straight', [5], 20), { ...allSpots('red')[0]!, amount: 70 }];
    expect(checkAdd([], last, l)).toBeUndefined();
    expect(checkAdd([bet('straight', [9], 20)], last, l)).toEqual({ code: 'total_max', max: 100 });
  });
  it('maxAddable respects both caps', () => {
    expect(maxAddable([], makeSpot('straight', [1]), l)).toBe(25);
    expect(maxAddable([bet('straight', [1], 10)], makeSpot('straight', [1]), l)).toBe(15);
    expect(maxAddable([{ ...allSpots('red')[0]!, amount: 90 }], makeSpot('straight', [1]), l)).toBe(10);
    expect(maxAddable([{ ...allSpots('red')[0]!, amount: 90 }], allSpots('black')[0]!, l)).toBe(10);
  });
  it('high-roller minimum total per spin', () => {
    const hr = slipLimits({ tierMax: 2000, minBet: 1, insideMaxFraction: 0.25, minTotal: 100 });
    expect(checkSpin([bet('straight', [5], 50)], hr)).toEqual({ code: 'min_total', min: 100 });
    expect(checkSpin([bet('straight', [5], 50), bet('straight', [6], 50)], hr)).toBeUndefined();
    expect(checkSpin([], hr)).toBeUndefined();
  });
});

describe('round state machine', () => {
  const T = { betTicks: 500, noMoreBetsTicks: 20, spinTicks: 100, resultTicks: 60 };
  const red = (amount: number): Bet => ({ ...allSpots('red')[0]!, amount });

  it('single player: nothing happens until Spin, then the full cycle', () => {
    const r = new RouletteRound(T, 3);
    const rng = seededRng(1);
    expect(r.update(0, ['a'], rng)).toBeUndefined();
    r.addBets('a', [red(10)]);
    expect(r.update(10_000, ['a'], rng)).toBeUndefined(); // no timer alone
    expect(r.endsAt).toBeUndefined();
    r.setReady('a');
    expect(r.update(10_001, ['a'], rng)).toEqual({ to: 'no_more_bets', dropped: [] });
    expect(r.canBet()).toBe(false);
    expect(() => r.addBets('a', [red(1)])).toThrow();
    expect(r.update(10_020, ['a'], rng)).toBeUndefined();
    const spin = r.update(10_021, ['a'], rng);
    expect(spin?.to).toBe('spin');
    expect(r.update(10_120, ['a'], rng)).toBeUndefined();
    const res = r.update(10_121, ['a'], rng);
    expect(res?.to).toBe('result');
    if (res?.to === 'result') {
      expect(res.slips.get('a')).toEqual([red(10)]);
      expect(r.history[0]).toBe(res.result);
    }
    expect(r.update(10_181, ['a'], rng)).toEqual({ to: 'betting', dropped: [], reset: true });
    expect(r.hasBets()).toBe(false);
    expect(r.canBet()).toBe(true);
  });

  it('multiplayer: timer starts at the first bet with 2+ seated; bets merge; ready clears on new bet', () => {
    const r = new RouletteRound(T);
    const rng = seededRng(2);
    r.addBets('a', [red(10)]);
    r.update(100, ['a', 'b'], rng);
    expect(r.endsAt).toBe(600);
    r.addBets('a', [red(5)]);
    expect(r.bets('a')).toEqual([red(15)]);
    r.setReady('a');
    r.addBets('b', [red(1)]);
    // b has bets but is not ready: wait
    expect(r.update(200, ['a', 'b'], rng)).toBeUndefined();
    r.addBets('a', [red(1)]);
    expect(r.isReady('a')).toBe(false);
    // timer expiry closes betting regardless of ready
    expect(r.update(600, ['a', 'b'], rng)?.to).toBe('no_more_bets');
  });

  it('multiplayer: closes early when every bettor is ready; idle seated players never block', () => {
    const r = new RouletteRound(T);
    const rng = seededRng(3);
    r.addBets('a', [red(10)]);
    r.addBets('b', [red(10)]);
    r.setReady('a');
    expect(r.update(1, ['a', 'b', 'c'], rng)).toBeUndefined();
    r.setReady('b');
    expect(r.update(2, ['a', 'b', 'c'], rng)?.to).toBe('no_more_bets');
  });

  it('abandoned slips count as ready (spin proceeds when the only bettor leaves)', () => {
    const r = new RouletteRound(T);
    r.addBets('a', [red(10)]);
    expect(r.update(1, [], seededRng(4))?.to).toBe('no_more_bets');
  });

  it('clearing all bets stops the timer', () => {
    const r = new RouletteRound(T);
    const rng = seededRng(5);
    r.addBets('a', [red(10)]);
    r.update(0, ['a', 'b'], rng);
    expect(r.endsAt).toBe(500);
    expect(r.clear('a')).toEqual([red(10)]);
    r.update(1, ['a', 'b'], rng);
    expect(r.endsAt).toBeUndefined();
    expect(r.update(600, ['a', 'b'], rng)).toBeUndefined();
  });

  it('high-roller minimum drops small slips at close', () => {
    const r = new RouletteRound(T);
    const rng = seededRng(6);
    r.addBets('a', [red(50)]);
    r.addBets('b', [red(150)]);
    r.setReady('a');
    r.setReady('b');
    expect(r.update(1, ['a', 'b'], rng, 100)).toEqual({ to: 'no_more_bets', dropped: ['a'] });
    expect(r.bettors()).toEqual(['b']);

    const r2 = new RouletteRound(T);
    r2.addBets('a', [red(50)]);
    r2.setReady('a');
    expect(r2.update(1, ['a'], rng, 100)).toEqual({ to: 'betting', dropped: ['a'], reset: false });
    expect(r2.canBet()).toBe(true);
  });

  it('keeps at most historyLength results, newest first', () => {
    const r = new RouletteRound({ betTicks: 1, noMoreBetsTicks: 0, spinTicks: 0, resultTicks: 0 }, 3);
    const rng = seededRng(8);
    const results: number[] = [];
    let now = 0;
    for (let i = 0; i < 5; i++) {
      r.addBets('a', [red(1)]);
      r.setReady('a');
      for (let k = 0; k < 4; k++) {
        const tr = r.update(now++, ['a'], rng);
        if (tr?.to === 'result') results.unshift(tr.result);
      }
    }
    expect(results).toHaveLength(5);
    expect(r.history).toEqual(results.slice(0, 3));
  });
});

describe('animation', () => {
  it('ends on the result within the spin time, with increasing gaps', () => {
    for (const result of [0, 17, 26, 32]) {
      const f = spinFrames(result, 100, 5);
      const last = f[f.length - 1]!;
      expect(WHEEL_ORDER[last.index]).toBe(result);
      expect(last.at).toBeLessThanOrEqual(100);
      for (let i = 1; i < f.length; i++) expect(f[i]!.at).toBeGreaterThan(f[i - 1]!.at);
      expect(f[f.length - 1]!.at - f[f.length - 2]!.at).toBeGreaterThan(f[1]!.at - f[0]!.at);
    }
  });
  it('wheel window wraps around', () => {
    expect(wheelWindow(0, 2)).toEqual([3, 26, 0, 32, 15]);
    expect(wheelWindow(wheelIndex(26), 1)).toEqual([3, 26, 0]);
  });
});

/**
 * Independent tester suite: RNG games (§11) and the streak cap (§14).
 * Expected RTPs are computed here from the §11 tables / Appendix B, then compared with the
 * implementation's constants and payout functions.
 */
import { describe, expect, it } from 'vitest';
import { seededRng } from '../../src/core/logic/rng';
import { GAME_RTP, rerollCap, rerollChance, DEFAULT_STREAK, drawWithReroll } from '../../src/core/logic/streak';
import { HOUSE_EDGE } from '../../src/core/logic/house-edge';
import { coinReturn } from '../../src/games/extras/logic/coin';
import { houseReturn, houseRtp, judgeHouse } from '../../src/games/extras/logic/dice';
import { BIN_WEIGHTS, DEFAULT_PLINKO, dropBall, plinkoReturn, plinkoRtp } from '../../src/games/extras/logic/plinko';
import { buildFace, DEFAULT_PRICES, DEFAULT_PRIZES, drawScratch, faceMatches, scratchRtp, symbolCounts } from '../../src/games/extras/logic/scratch';
import { DEFAULT_SEGMENTS, DEFAULT_WHEEL, wheelReturn, wheelRtp } from '../../src/games/extras/logic/wheel';

describe('coin flip §11.1', () => {
  it('pays 0.96:1 floored: RTP 98 %', () => {
    expect(coinReturn(100, true, 0.96)).toBe(196);
    expect(coinReturn(7, true, 0.96)).toBe(7 + 6);
    expect(coinReturn(100, false, 0.96)).toBe(0);
    expect(0.5 * 1.96).toBeCloseTo(0.98, 12);
    expect(GAME_RTP.coin_flip).toBe(0.98);
  });
});

describe('wheel of fortune §11.2 / Appendix B', () => {
  const B = 'X B M B D B M B H B D B M B T B M B D B H B M B D B E B M B D B H B M B T B M B D B H B M B T B C M H D M B'.split(' ');
  it('segment order is exactly Appendix B with the stated counts', () => {
    expect([...DEFAULT_SEGMENTS]).toEqual(B);
    const counts: Record<string, number> = {};
    for (const c of B) counts[c] = (counts[c] ?? 0) + 1;
    expect(counts).toEqual({ B: 25, C: 1, H: 5, M: 11, D: 7, T: 3, E: 1, X: 1 });
  });
  it('RTP = 51.5 / 54 = 95.37 %', () => {
    const mult: Record<string, number> = { B: 0, C: 0, H: 0.5, M: 1, D: 2, T: 3, E: 5, X: 10 };
    const rtp = B.reduce((s, c) => s + mult[c]!, 0) / 54;
    expect(rtp).toBeCloseTo(51.5 / 54, 12);
    expect(wheelRtp(DEFAULT_WHEEL)).toBeCloseTo(rtp, 12);
    expect(rtp).toBeCloseTo(0.9537, 4);
    expect(wheelReturn(9, { multiplier: 0.5 })).toBe(4); // floor
  });
});

describe('plinko §11.4', () => {
  const P = [1, 12, 66, 220, 495, 792, 924, 792, 495, 220, 66, 12, 1];
  const TABLES: Record<'low' | 'medium' | 'high', [number[], number]> = {
    low: [[10, 3, 1.6, 1.4, 1.0, 1.0, 0.5, 1.0, 1.0, 1.4, 1.6, 3, 10], 0.9656],
    medium: [[33, 11, 4, 2, 1.0, 0.6, 0.3, 0.6, 1.0, 2, 4, 11, 33], 0.9657],
    high: [[170, 24, 8.1, 2, 0.6, 0.2, 0.2, 0.2, 0.6, 2, 8.1, 24, 170], 0.967],
  };
  it('bin probabilities are Binomial(12, ½)', () => expect([...BIN_WEIGHTS]).toEqual(P));
  for (const [risk, [table, rtp]] of Object.entries(TABLES)) {
    it(`${risk}: table and RTP ${(rtp * 100).toFixed(2)} %`, () => {
      expect([...DEFAULT_PLINKO[risk as 'low']]).toEqual(table);
      const exact = table.reduce((s, m, i) => s + m * P[i]!, 0) / 4096;
      expect(exact).toBeCloseTo(rtp, 4);
      expect(plinkoRtp(table)).toBeCloseTo(exact, 12);
      expect(GAME_RTP[`plinko_${risk}` as 'plinko_low']).toBeCloseTo(rtp, 4);
    });
  }
  it('ball bin = number of right bounces; payout floored', () => {
    const d = dropBall(seededRng(3), TABLES.high[0]);
    expect(d.path).toHaveLength(12);
    expect(d.bin).toBe(d.path.filter(Boolean).length);
    expect(plinkoReturn(3, { multiplier: 0.6 })).toBe(1);
  });
});

describe('scratch cards §11.3', () => {
  it('prize tables and RTP 79.5 % / 85.0 %', () => {
    expect(DEFAULT_PRIZES.basic.map(([a, p]) => [a, p])).toEqual([
      [10, 0.22],
      [20, 0.1],
      [50, 0.03],
      [100, 0.01],
      [500, 0.002],
      [2500, 0.0001],
    ]);
    expect(DEFAULT_PRICES).toEqual({ basic: 10, gold: 100 });
    expect(scratchRtp(DEFAULT_PRIZES.basic, 10)).toBeCloseTo(0.795, 10);
    expect(scratchRtp(DEFAULT_PRIZES.gold, 100)).toBeCloseTo(0.85, 10);
  });
  it('card faces: winner shows the prize exactly 3×, others ≤ 2×; loser never shows a triple', () => {
    const rng = seededRng(11);
    for (let i = 0; i < 2000; i++) {
      const o = drawScratch(rng, DEFAULT_PRIZES.basic, 0.01);
      const face = buildFace(rng, DEFAULT_PRIZES.basic, o);
      expect(face).toHaveLength(9);
      expect(faceMatches(face, o)).toBe(true);
      const triples = [...symbolCounts(face).entries()].filter(([, n]) => n >= 3);
      if (o.prize > 0) expect(triples).toEqual([[o.prize, 3]]);
      else if (!o.creeper) expect(triples).toEqual([]);
    }
  });
});

describe('dice duel §11.5', () => {
  it('ties push except a tie on 7 (house): HE = (6/36)² = 2.78 %', () => {
    expect(judgeHouse([3, 4], [2, 5], [7])).not.toBe('push');
    expect(houseReturn(10, judgeHouse([3, 4], [2, 5], [7]))).toBe(0);
    expect(houseReturn(10, judgeHouse([3, 3], [2, 4], [7]))).toBe(10);
    let ret = 0;
    for (let a = 1; a <= 6; a++) for (let b = 1; b <= 6; b++) for (let c = 1; c <= 6; c++) for (let d = 1; d <= 6; d++) ret += houseReturn(1, judgeHouse([a, b], [c, d], [7]));
    expect(1 - ret / 6 ** 4).toBeCloseTo((6 / 36) ** 2, 12);
    expect(houseRtp([7])).toBeCloseTo(1 - (6 / 36) ** 2, 12);
  });
});

describe('streak re-draw cap §14 (house edge never below 1 %)', () => {
  it('spec examples: coin r_cap 0.0102, copper r_cap 0.103', () => {
    expect(rerollCap(0.98, 0.01)).toBeCloseTo(0.0102, 4);
    expect(rerollCap(0.8976, 0.01)).toBeCloseTo(0.103, 3);
  });
  it('r = min(0.005·S, 0.003·|S|, r_cap), for every RNG game and streak', () => {
    for (const rtp of Object.values(GAME_RTP)) {
      for (let s = -10; s <= 10; s++) {
        const raw = s > 0 ? 0.005 * s : s < 0 ? 0.003 * -s : 0;
        expect(rerollChance(s, rtp, DEFAULT_STREAK)).toBeCloseTo(Math.min(raw, Math.max(0, 0.99 / rtp - 1)), 12);
        // upper bound of the proof: RTP' ≤ RTP·(1 + r) ≤ 0.99
        expect(rtp * (1 + rerollChance(s, rtp, DEFAULT_STREAK))).toBeLessThanOrEqual(0.99 + 1e-12);
      }
    }
  });
  it('simulated coin flip at streak +10 keeps a house edge ≥ 1 %', () => {
    const rng = seededRng(99);
    const r = rerollChance(10, GAME_RTP.coin_flip, DEFAULT_STREAK);
    let ret = 0;
    const N = 400_000;
    for (let i = 0; i < N; i++) {
      const { result } = drawWithReroll(rng, r, () => rng.next() < 0.5, (w) => !w);
      ret += coinReturn(1000, result, 0.96);
    }
    expect(ret / (N * 1000)).toBeLessThan(0.99 + 0.004); // 0.4 % Monte-Carlo slack
  });
  it('VIP cashback edges (house-edge table) never exceed the §17 edges', () => {
    const spec: Record<string, number> = { blackjack: 0.0041, slots: 0.0396, roulette: 0.027, craps: 0.0136, coin_flip: 0.02, wheel: 1 - 51.5 / 54, scratch: 0.15, plinko: 0.033, dice_duel: (6 / 36) ** 2 };
    for (const [g, e] of Object.entries(spec)) expect(HOUSE_EDGE[g]).toBeLessThanOrEqual(e + 1e-4);
  });
});

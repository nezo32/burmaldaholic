/**
 * RTP verification (GAME_DESIGN §17): every RNG game gets a 10⁷-round Monte-Carlo run
 * asserting |RTP − expected| < 0.3 %; Plinko High is checked by exact enumeration of all
 * 4096 paths. Bets are large enough (100) that flooring does not bias the result.
 */
import { describe, expect, it } from 'vitest';
import { type Rng, seededRng } from '../../../core/logic/rng';
import { DEFAULT_STREAK, drawWithReroll, rerollChance } from '../../../core/logic/streak';
import { coinReturn, flipCoin } from './coin';
import { duelHouse, houseReturn } from './dice';
import { DEFAULT_PLINKO, type PlinkoRisk, PLINKO_ROWS, dropBall, plinkoReturn } from './plinko';
import { DEFAULT_PRICES, DEFAULT_PRIZES, type ScratchKind, drawScratch } from './scratch';
import { DEFAULT_WHEEL, spinWheel, wheelReturn, wheelRtp } from './wheel';

const N = 10_000_000;
const TOL = 0.003;
const BET = 100;
const SLOW = { timeout: 120_000 };

/** Mean return per unit staked over n rounds of `round` (which returns the total return). */
function simulate(n: number, stake: number, round: () => number): number {
  let ret = 0;
  for (let i = 0; i < n; i++) ret += round();
  return ret / (n * stake);
}

describe('Monte-Carlo RTP (10⁷ rounds, ±0.3 %)', () => {
  it('coin flip 98.0 %', SLOW, () => {
    const rng = seededRng(101);
    const rtp = simulate(N, BET, () => coinReturn(BET, flipCoin(rng, 'heads').win, 0.96));
    expect(Math.abs(rtp - 0.98)).toBeLessThan(TOL);
  });

  it('wheel of fortune 95.37 %', SLOW, () => {
    const rng = seededRng(202);
    const rtp = simulate(N, BET, () => wheelReturn(BET, spinWheel(rng, DEFAULT_WHEEL)));
    expect(wheelRtp()).toBeCloseTo(0.9537, 4);
    expect(Math.abs(rtp - 51.5 / 54)).toBeLessThan(TOL);
  });

  for (const [kind, expected] of [
    ['basic', 0.795],
    ['gold', 0.85],
  ] as [ScratchKind, number][]) {
    it(`scratch ${kind} ${expected * 100} %`, SLOW, () => {
      const rng = seededRng(kind === 'basic' ? 303 : 304);
      const price = DEFAULT_PRICES[kind];
      const rtp = simulate(N, price, () => drawScratch(rng, DEFAULT_PRIZES[kind], 0.01).prize);
      expect(Math.abs(rtp - expected)).toBeLessThan(TOL);
    });
  }

  for (const [risk, expected] of [
    ['low', 0.9656],
    ['medium', 0.9657],
  ] as [PlinkoRisk, number][]) {
    it(`plinko ${risk} ${expected * 100} %`, SLOW, () => {
      const rng = seededRng(risk === 'low' ? 405 : 406);
      const table = DEFAULT_PLINKO[risk];
      const rtp = simulate(N, BET, () => plinkoReturn(BET, dropBall(rng, table)));
      expect(Math.abs(rtp - expected)).toBeLessThan(TOL);
    });
  }

  it('dice duel vs house 97.22 %', SLOW, () => {
    const rng = seededRng(507);
    const rtp = simulate(N, BET, () => houseReturn(BET, duelHouse(rng, [7]).outcome));
    expect(Math.abs(rtp - (1 - (6 / 36) ** 2))).toBeLessThan(TOL);
  });
});

describe('exact enumeration', () => {
  it('plinko: every one of the 4096 paths (all risks, bet 100, floored)', () => {
    const expected: Record<PlinkoRisk, number> = { low: 0.9656, medium: 0.9657, high: 0.967 };
    for (const risk of ['low', 'medium', 'high'] as PlinkoRisk[]) {
      let total = 0;
      for (let mask = 0; mask < 2 ** PLINKO_ROWS; mask++) {
        let bit = 0;
        const rng: Rng = { next: () => ((mask >> bit++) & 1 ? 0.25 : 0.75) };
        total += plinkoReturn(BET, dropBall(rng, DEFAULT_PLINKO[risk]));
      }
      expect(total / (2 ** PLINKO_ROWS * BET)).toBeCloseTo(expected[risk], 4);
    }
  });
});

describe('streak re-draw never pushes the house edge below 1 %', () => {
  it('lucky ×10 on the wheel (10⁷ rounds)', SLOW, () => {
    const rng = seededRng(808);
    const base = wheelRtp();
    const r = rerollChance(10, base, DEFAULT_STREAK);
    expect(r).toBeGreaterThan(0);
    const rtp = simulate(N, BET, () => {
      const { result } = drawWithReroll(rng, r, () => spinWheel(rng), (s) => wheelReturn(BET, s) < BET);
      return wheelReturn(BET, result);
    });
    expect(rtp).toBeGreaterThan(base);
    expect(rtp).toBeLessThan(1 - DEFAULT_STREAK.minHouseEdge + TOL);
  });

  it('lucky ×10 on the coin flip is capped (r = 0.0102)', SLOW, () => {
    const rng = seededRng(909);
    const r = rerollChance(10, 0.98, DEFAULT_STREAK);
    expect(r).toBeCloseTo(0.0102, 4);
    const rtp = simulate(N, BET, () => {
      const { result } = drawWithReroll(rng, r, () => flipCoin(rng, 'tails'), (x) => !x.win);
      return coinReturn(BET, result.win, 0.96);
    });
    expect(rtp).toBeLessThan(0.99 + TOL);
    expect(Math.abs(rtp - 0.98 * (1 + r * 0.5))).toBeLessThan(TOL);
  });
});

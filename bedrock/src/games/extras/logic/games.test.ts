import { describe, expect, it } from 'vitest';
import { type Rng, seededRng } from '../../../core/logic/rng';
import { coinReturn, coinRtp, flipCoin, isSoulConfirmWord, otherSide, soulReturn } from './coin';
import {
  BIN_WEIGHTS,
  DEFAULT_PLINKO,
  PLINKO_BINS,
  dropBall,
  maxPlinkoMultiplier,
  pathPositions,
  plinkoReturn,
  plinkoRtp,
  plinkoTable,
} from './plinko';
import { floorPay } from './payout';
import {
  CREEPER,
  DEFAULT_PRIZES,
  buildFace,
  drawScratch,
  faceMatches,
  isScratchCard,
  newCard,
  prizeTable,
  scratch,
  scratchRtp,
  symbolCounts,
  topPrize,
} from './scratch';
import {
  DEFAULT_SEGMENTS,
  maxWheelMultiplier,
  segmentCounts,
  spinFrames,
  spinWheel,
  wheelFromConfig,
  wheelLegend,
  wheelReturn,
  wheelRtp,
} from './wheel';
import { ChallengeBook, duelHouse, duelPvp, houseReturn, houseRtp, judgeHouse, pvpPayout, tieTotals } from './dice';

/** Rng returning a fixed sequence (then repeating the last value). */
function seq(...vals: number[]): Rng {
  let i = 0;
  return { next: () => vals[Math.min(i++, vals.length - 1)] as number };
}

describe('floorPay', () => {
  it('floors and survives float error', () => {
    expect(floorPay(15, 1.4)).toBe(21);
    expect(floorPay(75, 0.96)).toBe(72);
    expect(floorPay(1, 0.96)).toBe(0);
    expect(floorPay(3, 0.5)).toBe(1);
    expect(floorPay(10, 8.1)).toBe(81);
    expect(floorPay(0, 5)).toBe(0);
    expect(floorPay(10, 0)).toBe(0);
  });
});

describe('coin flip', () => {
  it('pays 0.96:1 floored, 1:1 for the soul', () => {
    expect(coinReturn(100, true, 0.96)).toBe(196);
    expect(coinReturn(100, false, 0.96)).toBe(0);
    expect(coinReturn(1, true, 0.96)).toBe(1);
    expect(coinReturn(25, true, 0.96)).toBe(49);
    expect(soulReturn(1000, true)).toBe(2000);
    expect(soulReturn(1000, false)).toBe(0);
    expect(coinRtp(0.96)).toBeCloseTo(0.98, 10);
  });
  it('landed side follows the win', () => {
    expect(flipCoin(seq(0.1), 'heads')).toEqual({ pick: 'heads', landed: 'heads', win: true });
    expect(flipCoin(seq(0.9), 'heads')).toEqual({ pick: 'heads', landed: 'tails', win: false });
    expect(flipCoin(seq(0.55), 'tails', 0.6).win).toBe(true);
    expect(otherSide('tails')).toBe('heads');
  });
  it('accepts the EN and RU confirm words', () => {
    expect(isSoulConfirmWord(' deal ')).toBe(true);
    expect(isSoulConfirmWord('СДЕЛКА')).toBe(true);
    expect(isSoulConfirmWord('yes')).toBe(false);
  });
});

describe('wheel of fortune', () => {
  it('has the Appendix B order and counts', () => {
    expect(DEFAULT_SEGMENTS).toHaveLength(54);
    expect(DEFAULT_SEGMENTS[0]).toBe('X');
    expect(DEFAULT_SEGMENTS[26]).toBe('E');
    expect(DEFAULT_SEGMENTS[48]).toBe('C');
    expect(DEFAULT_SEGMENTS[53]).toBe('B');
    expect(Object.fromEntries(segmentCounts())).toEqual({ B: 25, C: 1, H: 5, M: 11, D: 7, T: 3, E: 1, X: 1 });
  });
  it('matches the appendix string exactly', () => {
    const a = 'X B M B D B M B H B D B M B T B M B D B H B M B D B E';
    const b = 'B M B D B H B M B T B M B D B H B M B T B C M H D M B';
    expect(DEFAULT_SEGMENTS.join(' ')).toBe(`${a} ${b}`);
  });
  it('RTP is 51.5/54', () => {
    expect(wheelRtp()).toBeCloseTo(51.5 / 54, 12);
    expect(maxWheelMultiplier()).toBe(10);
  });
  it('pays floor(stake × multiplier)', () => {
    expect(wheelReturn(7, { multiplier: 0.5 })).toBe(3);
    expect(wheelReturn(7, { multiplier: 10 })).toBe(70);
    expect(wheelReturn(7, { multiplier: 0 })).toBe(0);
  });
  it('spins uniformly onto an index', () => {
    expect(spinWheel(seq(0)).code).toBe('X');
    expect(spinWheel(seq(0.9999)).index).toBe(53);
    const s = spinWheel(seq(26 / 54 + 1e-9));
    expect(s).toEqual({ index: 26, code: 'E', multiplier: 5 });
  });
  it('reads config with fallbacks', () => {
    const w = wheelFromConfig(['B', 'X', 'Q'], { X: 20, B: -1, Z: 3 });
    expect(w.segments).toEqual(['B', 'X']);
    expect(w.multipliers.X).toBe(20);
    expect(w.multipliers.B).toBe(0);
    expect(wheelFromConfig('nope', null).segments).toBe(DEFAULT_SEGMENTS);
    expect(wheelFromConfig(['B'], {}).segments).toBe(DEFAULT_SEGMENTS);
  });
  it('legend is ordered by multiplier and counts sum to 54', () => {
    const l = wheelLegend();
    expect(l[0]?.code).toBe('B');
    expect(l[1]?.code).toBe('C');
    expect(l.at(-1)?.code).toBe('X');
    expect(l.reduce((s, r) => s + r.count, 0)).toBe(54);
  });
  it('spin animation ends on the target and slows down', () => {
    for (const target of [0, 1, 26, 53]) {
      const f = spinFrames(target, 54);
      expect(f.at(-1)?.index).toBe(target);
      expect(f[0]!.delay).toBeLessThanOrEqual(f.at(-1)!.delay);
      for (const x of f) expect(x.index).toBeGreaterThanOrEqual(0);
    }
  });
});

describe('plinko', () => {
  it('binomial weights sum to 4096', () => {
    expect(BIN_WEIGHTS).toEqual([1, 12, 66, 220, 495, 792, 924, 792, 495, 220, 66, 12, 1]);
    expect(BIN_WEIGHTS.reduce((a, b) => a + b, 0)).toBe(4096);
  });
  it('exact RTPs match the spec (§11.4)', () => {
    expect(plinkoRtp(DEFAULT_PLINKO.low)).toBeCloseTo(0.9656, 4);
    expect(plinkoRtp(DEFAULT_PLINKO.medium)).toBeCloseTo(0.9657, 4);
    expect(plinkoRtp(DEFAULT_PLINKO.high)).toBeCloseTo(0.967, 4);
    expect(maxPlinkoMultiplier(DEFAULT_PLINKO.high)).toBe(170);
  });
  it('tables are symmetric with 13 bins', () => {
    for (const t of Object.values(DEFAULT_PLINKO)) {
      expect(t).toHaveLength(PLINKO_BINS);
      expect([...t].reverse()).toEqual([...t]);
    }
  });
  it('bin = number of rights along the path', () => {
    const allLeft = dropBall(seq(0.9), DEFAULT_PLINKO.high);
    expect(allLeft.bin).toBe(0);
    expect(allLeft.multiplier).toBe(170);
    const allRight = dropBall(seq(0.1), DEFAULT_PLINKO.low);
    expect(allRight.bin).toBe(12);
    const d = dropBall(seededRng(5), DEFAULT_PLINKO.medium);
    expect(d.path).toHaveLength(12);
    expect(d.bin).toBe(d.path.filter(Boolean).length);
    expect(pathPositions(d.path).at(-1)).toBe(d.bin);
  });
  it('pays floor(bet × mult)', () => {
    expect(plinkoReturn(5, { multiplier: 0.2 })).toBe(1);
    expect(plinkoReturn(3, { multiplier: 0.2 })).toBe(0);
    expect(plinkoReturn(10, { multiplier: 8.1 })).toBe(81);
  });
  it('config fallback', () => {
    expect(plinkoTable([1, 2], 'low')).toBe(DEFAULT_PLINKO.low);
    const custom = Array(13).fill(1);
    expect(plinkoTable(custom, 'high')).toBe(custom);
  });
});

describe('scratch cards', () => {
  it('default RTPs are 79.5 % / 85 %', () => {
    expect(scratchRtp(DEFAULT_PRIZES.basic, 10)).toBeCloseTo(0.795, 10);
    expect(scratchRtp(DEFAULT_PRIZES.gold, 100)).toBeCloseTo(0.85, 10);
    expect(topPrize(DEFAULT_PRIZES.basic)).toBe(2500);
    expect(topPrize(DEFAULT_PRIZES.gold)).toBe(25000);
  });
  it('draws from the table in order', () => {
    expect(drawScratch(seq(0.1), DEFAULT_PRIZES.basic, 0.01)).toEqual({ prize: 10, creeper: false });
    expect(drawScratch(seq(0.25), DEFAULT_PRIZES.basic, 0.01)).toEqual({ prize: 20, creeper: false });
    expect(drawScratch(seq(0.36205), DEFAULT_PRIZES.basic, 0.01)).toEqual({ prize: 2500, creeper: false });
    expect(drawScratch(seq(0.5, 0.005), DEFAULT_PRIZES.basic, 0.01)).toEqual({ prize: 0, creeper: true });
    expect(drawScratch(seq(0.5, 0.5), DEFAULT_PRIZES.basic, 0.01)).toEqual({ prize: 0, creeper: false });
  });
  it('faces follow the §11.3 rules for every outcome', () => {
    const rng = seededRng(42);
    for (const kind of ['basic', 'gold'] as const) {
      const table = DEFAULT_PRIZES[kind];
      const outcomes = [...table.map(([p]) => ({ prize: p, creeper: false })), { prize: 0, creeper: true }, { prize: 0, creeper: false }];
      for (const o of outcomes) {
        for (let i = 0; i < 300; i++) {
          const face = buildFace(rng, table, o);
          expect(faceMatches(face, o)).toBe(true);
          const counts = symbolCounts(face);
          for (const [sym, n] of counts) if (sym !== (o.prize || (o.creeper ? CREEPER : -1))) expect(n).toBeLessThanOrEqual(2);
        }
      }
    }
  });
  it('small custom tables still build valid losing faces (decoys)', () => {
    const table = prizeTable([[50, 0.1]], 'basic');
    const rng = seededRng(1);
    for (let i = 0; i < 200; i++) expect(faceMatches(buildFace(rng, table, { prize: 0, creeper: false }), { prize: 0, creeper: false })).toBe(true);
  });
  it('prize table config: merges, sorts, scales Σp > 1, falls back', () => {
    expect(prizeTable([[20, 0.5], [10, 0.5], [10, 0.5]], 'basic')).toEqual([[10, 2 / 3], [20, 1 / 3]]);
    expect(prizeTable('x', 'gold')).toBe(DEFAULT_PRIZES.gold);
    expect(prizeTable([[1, 'a']], 'gold')).toBe(DEFAULT_PRIZES.gold);
    expect(prizeTable([], 'basic')).toBe(DEFAULT_PRIZES.basic);
  });
  it('scratching reveals cell by cell or all', () => {
    const card = newCard(seededRng(3), 'basic', 10, DEFAULT_PRIZES.basic, { prize: 2500, creeper: false });
    expect(card.top).toBe(true);
    expect(isScratchCard(card)).toBe(true);
    expect(scratch(card)).toBe(false);
    expect(card.revealed).toBe(1);
    expect(scratch(card, true)).toBe(true);
    expect(card.revealed).toBe(9);
    expect(isScratchCard({ ...card, cells: [1] })).toBe(false);
  });
});

describe('dice duel', () => {
  it('judges vs house', () => {
    expect(judgeHouse([6, 5], [3, 3], [7])).toBe('win');
    expect(judgeHouse([1, 1], [3, 3], [7])).toBe('lose');
    expect(judgeHouse([3, 3], [2, 4], [7])).toBe('push');
    expect(judgeHouse([3, 4], [2, 5], [7])).toBe('house_tie');
    expect(judgeHouse([3, 4], [2, 5], [])).toBe('push');
    expect(houseReturn(50, 'win')).toBe(100);
    expect(houseReturn(50, 'push')).toBe(50);
    expect(houseReturn(50, 'house_tie')).toBe(0);
    expect(houseReturn(50, 'lose')).toBe(0);
  });
  it('house edge is (6/36)² = 2.78 %', () => {
    expect(1 - houseRtp([7])).toBeCloseTo((6 / 36) ** 2, 12);
    expect(houseRtp([])).toBeCloseTo(1, 12);
  });
  it('rolls are 1..6', () => {
    const rng = seededRng(9);
    for (let i = 0; i < 1000; i++) {
      const d = duelHouse(rng);
      for (const v of [...d.player, ...d.dealer]) expect(v >= 1 && v <= 6).toBe(true);
    }
  });
  it('tie totals config', () => {
    expect(tieTotals([7, 7, 13, 2.5, 12])).toEqual([7, 12]);
    expect(tieTotals(undefined)).toEqual([7]);
  });
  it('PvP re-rolls ties and refunds after three', () => {
    // each die: randInt(1,6) = 1 + floor(x*6)
    const tie = [0, 0, 0, 0]; // 1+1 vs 1+1
    expect(duelPvp(seq(...tie, ...tie, ...tie), 3)).toMatchObject({ result: 'refund' });
    expect(duelPvp(seq(...tie, ...tie, ...tie), 3).rounds).toHaveLength(3);
    const r = duelPvp(seq(...tie, 0.99, 0.99, 0, 0), 3);
    expect(r.result).toBe('a');
    expect(r.rounds).toHaveLength(2);
    expect(duelPvp(seq(0, 0, 0.5, 0.5), 3).result).toBe('b');
  });
  it('pot and rake', () => {
    expect(pvpPayout(100, 0)).toEqual({ pot: 200, rake: 0, winnerGets: 200 });
    expect(pvpPayout(33, 5)).toEqual({ pot: 66, rake: 3, winnerGets: 63 });
  });
  it('challenge book rules', () => {
    const b = new ChallengeBook();
    expect(b.create('a', 'a', 10, 0, 600)).toEqual({ ok: false, error: 'self' });
    const c = b.create('a', 'b', 10, 0, 600);
    expect(c.ok).toBe(true);
    expect(b.create('a', 'c', 10, 10, 600)).toEqual({ ok: false, error: 'already_pending' });
    expect(b.create('c', 'b', 20, 10, 600).ok).toBe(true);
    expect(b.incoming('b', 20).map((x) => x.from)).toEqual(['a', 'c']);
    const id = c.ok ? c.challenge.id : -1;
    expect(b.take(id, 700)).toBeUndefined(); // expired
    expect(b.create('a', 'd', 5, 700, 600).ok).toBe(true);
    expect(b.expire(605).map((x) => x.from)).toEqual([]);
    expect(b.expire(700).map((x) => x.from)).toEqual(['c']);
    expect(b.dropPlayer('a')).toHaveLength(1);
    expect(b.size()).toBe(0);
  });
});

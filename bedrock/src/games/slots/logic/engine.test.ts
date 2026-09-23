import { describe, expect, it } from 'vitest';
import { seededRng } from '../../../core/logic/rng';
import { drawWithReroll, rerollChance, DEFAULT_STREAK, GAME_RTP } from '../../../core/logic/streak';
import {
  type Grid,
  type Sym,
  type Tier,
  defaultTable,
  evaluateGrid,
  evaluateLine,
  makeTable,
  worstCaseReturn,
} from './engine';
import { awardFor, contribute, loadPool, newPool, payJackpot } from './jackpot';
import { lineStats, totalRtp } from './rtp';
import { DEFAULT_LINE_BETS, clampLineBet, lineBetRange, machineMaxSpinBet, resolveSpin } from './spin';

const copper = defaultTable('copper');
const gold = defaultTable('gold');
const neth = defaultTable('netherite');

describe('line evaluation (§8.1)', () => {
  it('three of a kind pays the table multiplier', () => {
    expect(evaluateLine('seven', 'seven', 'seven', copper)).toEqual({ kind: 'three', symbol: 'seven', multiplier: 150 });
    expect(evaluateLine('apple', 'apple', 'apple', gold)?.multiplier).toBe(7);
  });
  it('wild substitutes for regular symbols and the best candidate wins', () => {
    expect(evaluateLine('wild', 'seven', 'wild', gold)).toEqual({ kind: 'three', symbol: 'seven', multiplier: 100 });
    expect(evaluateLine('wild', 'wild', 'apple', gold)?.symbol).toBe('apple');
    // berries (8) vs nothing else possible
    expect(evaluateLine('berries', 'wild', 'berries', gold)).toEqual({ kind: 'three', symbol: 'berries', multiplier: 8 });
  });
  it('three wilds use the wild pay, not a substituted regular', () => {
    expect(evaluateLine('wild', 'wild', 'wild', gold)).toEqual({ kind: 'wild', symbol: 'wild', multiplier: 200 });
    expect(evaluateLine('wild', 'wild', 'wild', neth)?.multiplier).toBe(250);
  });
  it('wild never substitutes for specials', () => {
    expect(evaluateLine('pearl', 'wild', 'pearl', gold)).toBeUndefined();
    expect(evaluateLine('star', 'star', 'wild', gold)).toBeUndefined();
    expect(evaluateLine('wild', 'creeper', 'creeper', gold)).toBeUndefined();
  });
  it('specials pay their table value and flag the event', () => {
    expect(evaluateLine('creeper', 'creeper', 'creeper', copper)).toEqual({ kind: 'special', symbol: 'creeper', multiplier: 0 });
    expect(evaluateLine('pearl', 'pearl', 'pearl', gold)?.multiplier).toBe(10);
    expect(evaluateLine('clock', 'clock', 'clock', neth)?.multiplier).toBe(50);
    expect(evaluateLine('star', 'star', 'star', gold)).toEqual({ kind: 'special', symbol: 'star', multiplier: 0 });
  });
  it('owned machines pay a fixed 1000× for three stars', () => {
    expect(evaluateLine('star', 'star', 'star', defaultTable('gold', true))?.multiplier).toBe(1000);
    expect(defaultTable('gold', true).progressive).toBe(false);
  });
  it('leading berries (wild does not count)', () => {
    expect(evaluateLine('berries', 'apple', 'berries', copper)).toEqual({ kind: 'berry1', symbol: 'berries', multiplier: 2 });
    expect(evaluateLine('berries', 'berries', 'apple', copper)).toEqual({ kind: 'berry2', symbol: 'berries', multiplier: 3 });
    expect(evaluateLine('berries', 'wild', 'apple', gold)?.kind).toBe('berry1');
    expect(evaluateLine('wild', 'berries', 'apple', gold)).toBeUndefined();
    expect(evaluateLine('apple', 'berries', 'berries', copper)).toBeUndefined();
  });
});

describe('grid evaluation', () => {
  const g = (rows: Sym[][]): Grid => rows;
  it('copper plays only the middle row', () => {
    const e = evaluateGrid(g([['seven', 'seven', 'seven'], ['apple', 'apple', 'apple'], ['seven', 'seven', 'seven']]), copper, 3);
    expect(e.wins).toEqual([{ kind: 'three', symbol: 'apple', multiplier: 10, line: 1, payout: 30 }]);
    expect(e.basePayout).toBe(30);
  });
  it('netherite plays rows and both diagonals, jackpot awarded once, priority star > clock > pearl', () => {
    const e = evaluateGrid(
      g([
        ['star', 'pearl', 'star'],
        ['clock', 'star', 'clock'],
        ['star', 'pearl', 'star'],
      ]),
      neth,
      2,
    );
    expect(e.wins.map((w) => w.line)).toEqual([4, 5]);
    expect(e.jackpotHit).toBe(true);
    expect(e.special).toBe('star');
    const e2 = evaluateGrid(g([['pearl', 'pearl', 'pearl'], ['clock', 'clock', 'clock'], ['tnt', 'tnt', 'tnt']]), neth, 2);
    expect(e2.special).toBe('clock');
    expect(e2.basePayout).toBe(10 * 2 + 50 * 2);
  });
  it('payout is floor(multiplier × line bet)', () => {
    const t = makeTable({ weights: { apple: 1 }, pays: { apple: 2.5 }, lines: 1, progressive: false });
    expect(evaluateGrid(g([['apple', 'apple', 'apple'], ['apple', 'apple', 'apple'], ['apple', 'apple', 'apple']]), t, 3).basePayout).toBe(7);
  });
  it('worst case covers the best line on every line', () => {
    expect(worstCaseReturn(neth, 10)).toBe(250 * 10 * 5);
    expect(worstCaseReturn(defaultTable('netherite', true), 10)).toBe(1000 * 10 * 5);
  });
});

describe('exact RTP (§8.2–8.4, Appendix A)', () => {
  const near = (a: number | undefined, b: number, d = 6e-7) => expect(Math.abs((a ?? 0) - b)).toBeLessThan(d);

  it('Copper Bandit: RTP 89.76 %, appendix probabilities, 1 in 296 creepers', () => {
    const s = lineStats(copper);
    near(s.baseRtp, 0.8976, 1e-5);
    near(s.outcomes.get('berry1'), 0.1824);
    near(s.outcomes.get('berry2'), 0.043776);
    near(s.outcomes.get('three:berries'), 0.013824);
    near(s.outcomes.get('three:apple'), 0.008);
    near(s.outcomes.get('three:golden_carrot'), 0.004096);
    near(s.outcomes.get('three:emerald'), 0.001728);
    near(s.outcomes.get('three:diamond'), 0.000512);
    near(s.outcomes.get('three:seven'), 0.000125);
    near(s.outcomes.get('special:creeper'), 0.003375);
    near(s.pNoWin, 0.74554, 1e-5);
    expect(Math.round(1 / s.outcomes.get('special:creeper')!)).toBe(296);
    expect(totalRtp(copper, 0.01)).toBeCloseTo(0.8976, 4); // no progressive: contribution ignored
  });

  it('Golden Reels: base 92.71 % + 1 % = 93.71 %, jackpot 1 in 125 000 lines', () => {
    const s = lineStats(gold);
    near(s.baseRtp, 0.92713, 1e-5);
    near(s.outcomes.get('berry1'), 0.16995);
    near(s.outcomes.get('berry2'), 0.0363);
    near(s.outcomes.get('three:berries'), 0.015598);
    near(s.outcomes.get('three:apple'), 0.010621);
    near(s.outcomes.get('three:golden_carrot'), 0.006832);
    near(s.outcomes.get('three:emerald'), 0.003348);
    near(s.outcomes.get('three:diamond'), 0.001304);
    near(s.outcomes.get('three:seven'), 0.000485);
    near(s.outcomes.get('wild'), 0.000027);
    near(s.outcomes.get('special:pearl'), 0.000125);
    near(s.outcomes.get('special:creeper'), 0.000512);
    near(s.outcomes.get('special:star'), 0.000008);
    near(s.pNoWin, 0.75541, 1e-5);
    expect(Math.round(1 / s.pJackpot)).toBe(125000);
    expect(totalRtp(gold, 0.01)).toBeCloseTo(0.9371, 4);
    expect(totalRtp(gold, 0.01)).toBeCloseTo(GAME_RTP.slots_gold, 4);
  });

  it('Netherite High Roller: base 94.54 % + 1.5 % = 96.04 %', () => {
    const s = lineStats(neth);
    near(s.baseRtp, 0.94535, 1e-5);
    near(s.outcomes.get('berry1'), 0.15862);
    near(s.outcomes.get('berry2'), 0.0308);
    near(s.outcomes.get('three:berries'), 0.01214);
    near(s.outcomes.get('three:apple'), 0.010621);
    near(s.outcomes.get('three:golden_carrot'), 0.006832);
    near(s.outcomes.get('three:emerald'), 0.003348);
    near(s.outcomes.get('three:diamond'), 0.001701);
    near(s.outcomes.get('three:seven'), 0.000702);
    near(s.outcomes.get('wild'), 0.000027);
    near(s.outcomes.get('special:pearl'), 0.000125);
    near(s.outcomes.get('special:clock'), 0.000008);
    near(s.outcomes.get('special:tnt'), 0.000216);
    near(s.outcomes.get('special:star'), 0.000008);
    near(s.pNoWin, 0.77508, 1e-5);
    // spec rounds 0.960347 to 96.04 %
    expect(Math.abs(totalRtp(neth, 0.015) - 0.9604)).toBeLessThan(1e-4);
    expect(Math.abs(totalRtp(neth, 0.015) - GAME_RTP.slots_netherite)).toBeLessThan(1e-4);
  });

  it('owned machines: 3 stars at 1000× give 93.51 % / 95.34 %', () => {
    expect(totalRtp(defaultTable('gold', true, 1000), 0.01)).toBeCloseTo(0.9351, 4);
    expect(Math.abs(totalRtp(defaultTable('netherite', true, 1000), 0.015) - 0.9534)).toBeLessThan(1e-4);
  });

  it('copper hit frequency 25.4 %', () => expect(lineStats(copper).hitRate).toBeCloseTo(0.254, 3));
  it('empty table is safe', () => expect(lineStats(makeTable({ weights: {}, pays: {}, lines: 1, progressive: false })).baseRtp).toBe(0));
});

describe('progressive jackpot pool (§8.5)', () => {
  it('contributions keep the exact long-run rate via the hidden remainder', () => {
    let s = newPool(5000);
    let added = 0;
    for (let i = 0; i < 1000; i++) {
      const r = contribute(s, 3, 0.01);
      s = r.state;
      added += r.added;
    }
    expect(added).toBe(30);
    expect(s.pool).toBe(5030);
    let n = newPool(0);
    for (let i = 0; i < 200; i++) n = contribute(n, 10, 0.015).state; // 0.15 per spin
    expect(n.pool).toBe(30);
    expect(contribute(newPool(0), 100, 0.015).state.pool).toBe(1);
  });
  it('award is proportional to the spin bet, capped at the full pool', () => {
    const s = { pool: 61240, rem: 0 };
    expect(awardFor(s, 300, 300)).toBe(61240);
    expect(awardFor(s, 3, 300)).toBe(612);
    expect(awardFor(s, 600, 300)).toBe(61240);
  });
  it('pool below the seed is topped up by the bank', () => {
    const r = payJackpot({ pool: 6000, rem: 0.5 }, 300, 300, 5000);
    expect(r.award).toBe(6000);
    expect(r.state).toEqual({ pool: 5000, rem: 0.5 });
    expect(r.toppedUp).toBe(5000);
    const small = payJackpot({ pool: 60000, rem: 0 }, 3, 300, 5000);
    expect(small.award).toBe(600);
    expect(small.state.pool).toBe(59400);
    expect(small.toppedUp).toBe(0);
  });
  it('loads corrupt state as the seed', () => {
    expect(loadPool(undefined, 5000)).toEqual({ pool: 5000, rem: 0 });
    expect(loadPool({ pool: -3 }, 7)).toEqual({ pool: 7, rem: 0 });
    expect(loadPool({ pool: 12.7, rem: 5 }, 7)).toEqual({ pool: 12, rem: 0 });
  });
});

describe('spin resolution and bet limits', () => {
  it('line-bet range honours machine and VIP limits', () => {
    expect(lineBetRange('copper', DEFAULT_LINE_BETS.copper, 100)).toEqual({ min: 1, max: 50 });
    expect(lineBetRange('gold', DEFAULT_LINE_BETS.gold, 100)).toEqual({ min: 1, max: 33 });
    expect(lineBetRange('gold', DEFAULT_LINE_BETS.gold, 1000)).toEqual({ min: 1, max: 100 });
    expect(lineBetRange('netherite', DEFAULT_LINE_BETS.netherite, 1000)).toEqual({ min: 2, max: 200 });
    expect(lineBetRange('netherite', DEFAULT_LINE_BETS.netherite, 2500)).toEqual({ min: 2, max: 500 });
    expect(machineMaxSpinBet('gold', DEFAULT_LINE_BETS.gold)).toBe(300);
    expect(machineMaxSpinBet('netherite', DEFAULT_LINE_BETS.netherite)).toBe(2500);
    expect(clampLineBet(999, { min: 2, max: 200 })).toBe(200);
    expect(clampLineBet(undefined, { min: 2, max: 200 })).toBe(2);
  });
  it('a jackpot spin credits base + award and updates the pool', () => {
    const allStars = makeTable({ weights: { star: 1 }, pays: {}, lines: 3, progressive: true });
    const o = resolveSpin({ table: allStars, lineBet: 100, rng: seededRng(1), jackpot: { state: newPool(5000), rate: 0.01, seed: 5000, maxSpinBet: 300 } });
    expect(o.jackpotHit).toBe(true);
    expect(o.jackpotAward).toBe(5003);
    expect(o.totalReturn).toBe(5003);
    expect(o.pool?.pool).toBe(5000);
    expect(o.toppedUp).toBe(5000);
  });
  it('the streak re-draw only re-draws losing spins', () => {
    let calls = 0;
    const always = <T,>(drawFn: () => T, isLosing: (x: T) => boolean) => {
      calls++;
      return drawWithReroll(seededRng(2), 1, drawFn, isLosing);
    };
    const winner = makeTable({ weights: { apple: 1 }, pays: { apple: 10 }, lines: 1, progressive: false });
    const r = resolveSpin({ table: winner, lineBet: 1, rng: seededRng(3), draw: always });
    expect(calls).toBe(1);
    expect(r.rerolled).toBe(false);
    const loser = makeTable({ weights: { apple: 1, emerald: 1 }, pays: {}, lines: 1, progressive: false });
    expect(resolveSpin({ table: loser, lineBet: 1, rng: seededRng(3), draw: always }).rerolled).toBe(true);
  });
});

// ---- Monte-Carlo (GAME_DESIGN §17: |RTP − expected| < 0.3 %) -----------------------------------

function simulate(tier: Tier, spins: number, seed: number, r = 0): { rtp: number; sd: number } {
  const table = defaultTable(tier);
  const rng = seededRng(seed);
  const draw = <T,>(drawFn: () => T, isLosing: (x: T) => boolean) => drawWithReroll(rng, r, drawFn, isLosing);
  let staked = 0;
  let returned = 0;
  for (let i = 0; i < spins; i++) {
    const o = resolveSpin({ table, lineBet: 1, rng, draw });
    staked += o.spinBet;
    returned += o.basePayout;
  }
  const s = lineStats(table);
  // per-spin standard deviation of return/bet (lines are identically distributed; rows are
  // independent, diagonals share cells, so bound the variance with full correlation)
  const lineSd = Math.sqrt(s.secondMoment - s.baseRtp * s.baseRtp);
  return { rtp: returned / staked, sd: lineSd / Math.sqrt(spins) };
}

describe('Monte-Carlo RTP (10⁷ spins per tier)', () => {
  for (const tier of ['copper', 'gold', 'netherite'] as const) {
    it(`${tier}: simulated base RTP matches the exact value within 0.3 %`, () => {
      const exact = lineStats(defaultTable(tier)).baseRtp;
      const { rtp, sd } = simulate(tier, 10_000_000, 12345 + tier.length);
      expect(Math.abs(rtp - exact)).toBeLessThan(0.003);
      expect(Math.abs(rtp - exact)).toBeLessThan(5 * sd);
    }, 120_000);
  }

  it('the streak re-draw at its cap never pushes the house edge below 1 % (Copper, S = +10)', () => {
    const rtp = GAME_RTP.slots_copper;
    const r = rerollChance(10, rtp, DEFAULT_STREAK);
    expect(r).toBeCloseTo(0.05, 6); // full 5 % applies (cap 0.103)
    const { rtp: sim } = simulate('copper', 2_000_000, 99, r);
    expect(sim).toBeGreaterThan(lineStats(copper).baseRtp);
    expect(sim).toBeLessThan(0.99);
  }, 60_000);
});

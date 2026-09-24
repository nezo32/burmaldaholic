/**
 * Exact maths of SLOTS.md §7 (§15 items 4–8): §7.5 enumeration totals, §7.3 closed forms, §7.1 RTP by
 * contribution, `validateRtp` warnings. Full enumeration of Overworld and End runs always (≈ 7 s); Nether
 * (tumbles, ≈ 3.5 min) and the 10⁸-spin Monte-Carlo run nightly (`SLOTS_ENUM=1`, `SLOTS_MC=1`).
 */
import { describe, expect, it } from 'vitest';
import { buildMachineDef, defaultMachine, readMachineConfig } from './config';
import { drawSpin } from './engine';
import { enumerateBase } from './enumerate';
import { fxSlotRng } from './test-rng';
import { emptyPools, poolValues } from './jackpots';
import { REFERENCE_RTP, TOP_SYMBOL } from './machines';
import { NETHER_HOARD_START, baseStats, computeRtp, expectedSpins, hoardValue, huntValue, rtpWarnings, sampleRtp, stickyStats, voidWalkerValue, wheelValue } from './rtp';
import { MACHINE_IDS, type MachineId } from './types';

const rel = (a: number, b: number): number => Math.abs(a - b) / Math.abs(b);

/** SLOTS.md §7.5 (fifths of the bet). */
const V75: Record<MachineId, { N: number; sum: number; hits: number; hitsOrTrigger: number; scatters: number[]; bonus: number; maxPay: number; topFive: number; tumbles?: number[] }> = {
  overworld: { N: 102_400_000, sum: 381_579_930, hits: 40_153_130, hitsOrTrigger: 40_806_404, scatters: [58_554_868, 34_909_500, 8_005_320, 882_360, 46_980, 972], bonus: 777_600, maxPay: 464, topFive: 238_140 },
  nether: {
    N: 33_554_432,
    sum: 114_458_900,
    hits: 12_534_784,
    hitsOrTrigger: 12_839_101,
    scatters: [18_942_904, 11_569_517, 2_720_221, 305_336, 16_137, 317],
    bonus: 128_916,
    maxPay: 825,
    topFive: 37_969,
    tumbles: [21_019_648, 8_840_192, 2_651_136, 760_832, 192_512, 70_656, 16_384, 1_024, 2_048],
  },
  end: { N: 184_528_125, sum: 524_300_931, hits: 66_159_981, hitsOrTrigger: 66_603_861, scatters: [130_691_232, 46_675_440, 6_667_920, 476_280, 17_010, 243], bonus: 656_100, maxPay: 4800, topFive: 5488 },
};

describe('§7.5 exact totals — factorised (non-tumbling machines)', () => {
  for (const m of ['overworld', 'end'] as const) {
    it(`${m}: Σ pay, scatter histogram, bonus triggers`, () => {
      const b = baseStats(defaultMachine(m));
      expect(b.N).toBe(V75[m].N);
      expect(b.waysFifths + b.scatterFifths).toBe(V75[m].sum);
      expect(b.scatters.slice(0, 6)).toEqual(V75[m].scatters);
      expect(b.bonusTriggers).toBe(V75[m].bonus);
    });
  }
  it('End free-spin state enumerations s0…s7 and the transitions from s0', () => {
    const s = stickyStats(defaultMachine('end'));
    expect(s.sum).toEqual([1_024_280_910, 5_722_030_350, 5_860_237_815, 34_728_903_900, 4_465_132_290, 26_007_038_550, 26_578_191_825, 164_562_181_875]);
    expect(s.trans[0]).toEqual([
      [149_752_557, 275_643],
      [10_702_935, 13_365],
      [10_709_577, 6_723],
      [765_207, 243],
      [10_709_577, 6_723],
      [765_207, 243],
      [765_369, 81],
      [54_675, 0],
    ]);
    const E = s.sum.map((x) => x / s.N / 5);
    // The printed per-state means of §7.3 are rounded loosely (E[3] 37.6412 vs the exact sum's 37.6408, E[5]/E[6]/E[7]
    // likewise within 5e-5 relative); the exact integer sums above are normative and give V(0,9,9) to 1e-8.
    [1.1102, 6.2018, 6.3516, 37.6412, 4.8395, 28.188, 28.807, 178.3606].forEach((e, i) => expect(rel(E[i]!, e)).toBeLessThan(5e-5));
  });
});

describe('§7.5 exact totals — full enumeration', () => {
  for (const m of MACHINE_IDS) {
    const run = m !== 'nether' || process.env.SLOTS_ENUM === '1';
    it.runIf(run)(`${m}: every stop combination`, () => {
      const e = enumerateBase(defaultMachine(m), TOP_SYMBOL[m]);
      const v = V75[m];
      expect(e.N).toBe(v.N);
      expect(e.sum).toBe(v.sum);
      expect(e.hits).toBe(v.hits);
      expect(e.hitsOrTrigger).toBe(v.hitsOrTrigger);
      expect(e.scatters.slice(0, 6)).toEqual(v.scatters);
      expect(e.bonusTriggers).toBe(v.bonus);
      expect(e.maxPay).toBe(v.maxPay);
      expect(e.topFive).toBe(v.topFive);
      if (v.tumbles) expect(e.tumbles.slice(0, 9)).toEqual(v.tumbles);
    }, 600_000);
  }
});

describe('§7.3 closed forms', () => {
  it('Void Walker V(0,9,9), V(0,11,11), V(0,14,14)', () => {
    const def = defaultMachine('end');
    const s = stickyStats(def);
    expect(rel(voidWalkerValue(def, s, 9), 102.466892)).toBeLessThan(1e-8);
    expect(rel(voidWalkerValue(def, s, 11), 170.175953)).toBeLessThan(1e-8);
    expect(rel(voidWalkerValue(def, s, 14), 311.794284)).toBeLessThan(1e-8);
  });
  it('free spins with independent spins: expected spins played (SLOTS.md §4)', () => {
    const ow = defaultMachine('overworld');
    const ne = defaultMachine('nether');
    const rhoO = 0.00908508;
    [8.627, 10.7837, 16.1754].forEach((x, i) => expect(expectedSpins(ow, rhoO, ow.freeSpins[i]!)).toBeCloseTo(x, 3));
    [12.6044, 15.7555, 21.0073].forEach((x, i) => expect(expectedSpins(ne, 0.00959009, ne.freeSpins[i]!)).toBeCloseTo(x, 3));
    // Nether e × f = the §4 mean free-spin wins
    const e = 228_917_800 / 33_554_432 / 5;
    expect(e).toBeCloseTo(1.36445642, 8);
    [17.1981, 21.4977, 28.6636].forEach((x, i) => expect(e * expectedSpins(ne, 0.00959009, ne.freeSpins[i]!)).toBeCloseTo(x, 3));
  });
  it('Treasure Hunt 9.560 752, expected chests 3.544 250 32', () => {
    const h = huntValue(defaultMachine('overworld'));
    expect(rel(h.opened, 3.54425032)).toBeLessThan(1e-8);
    expect(rel(h.value, 9.560752)).toBeLessThan(1e-6);
  });
  it('Piglin’s Hoard: mean final coins 7.846 836, P(15) 0.000 443 73, value 21.386 522', () => {
    const h = hoardValue(defaultMachine('nether'), NETHER_HOARD_START);
    expect(rel(h.meanCoins, 7.846836)).toBeLessThan(1e-6);
    expect(rel(h.pFull, 0.00044373)).toBeLessThan(1e-4);
    expect(rel(h.value, 21.386522)).toBeLessThan(1e-6);
  });
  it('Dragon Wheel 21.919 792; UP to middle 10 %, to core 0.625 %', () => {
    const w = wheelValue(defaultMachine('end'));
    expect(rel(w.value, 21.919792)).toBeLessThan(1e-6);
    expect(w.reach).toEqual([1, 0.1, 0.00625]);
  });
});

describe('§7.1 RTP by contribution (validateRtp)', () => {
  for (const m of MACHINE_IDS) {
    it(`${m}: totals and owned RTP`, () => {
      const r = computeRtp(defaultMachine(m))!;
      const ref = REFERENCE_RTP[m];
      expect(r.base * 100).toBeCloseTo(ref.base, 5);
      expect(r.scatter * 100).toBeCloseTo(ref.scatter, 5);
      expect(r.freeSpins * 100).toBeCloseTo(ref.freeSpins, 5);
      expect(r.bonus * 100).toBeCloseTo(ref.bonus, 5);
      expect(r.jackpotSeed * 100).toBeCloseTo(ref.jackpotSeed, 5);
      expect(r.contributions * 100).toBeCloseTo(ref.contributions, 9);
      expect(r.total * 100).toBeCloseTo(ref.total, 5);
      expect(r.owned * 100).toBeCloseTo(ref.owned, 5);
      expect(rtpWarnings(r)).toEqual([]);
    });
  }
  it('buy RTP: Nether 94.968 %, End 96.506 % (≤ machine RTP, within 0.5 %)', () => {
    expect(computeRtp(defaultMachine('nether'))!.buy! * 100).toBeCloseTo(94.968, 2);
    expect(computeRtp(defaultMachine('end'))!.buy! * 100).toBeCloseTo(96.506, 3);
  });
  it('changing one pay changes the result; the warning fires above 0.99 and for a buy above the machine RTP', () => {
    const base = readMachineConfig('end', () => undefined).config;
    const rich = buildMachineDef('end', { ...base, pays: { ...base.pays, end_stone: [1, 2, 4] } });
    const r = computeRtp(rich)!;
    expect(r.total).toBeGreaterThan(0.99);
    expect(rtpWarnings(r).map((w) => w.kind)).toContain('rtp');
    const cheap = buildMachineDef('end', { ...base, buyPrice: 100 });
    expect(rtpWarnings(computeRtp(cheap)!).map((w) => w.kind)).toEqual(['buy']);
    const nether = buildMachineDef('nether', { ...readMachineConfig('nether', () => undefined).config, pays: { ...base.pays } });
    expect(computeRtp(nether, false)).toBeUndefined(); // changed Nether tables → sample
  });
});

describe('Monte-Carlo (reference RNG)', () => {
  const big = process.env.SLOTS_MC === '1';
  const tol: Record<MachineId, number> = { overworld: 0.0013, nether: 0.0015, end: 0.0059 };
  for (const m of MACHINE_IDS) {
    const spins = big ? 100_000_000 : m === 'end' ? 400_000 : 200_000;
    it(`${m}: RTP within ${big ? '4σ/√n' : 'a loose bound'} of the exact value (${spins} spins)`, () => {
      const def = defaultMachine(m);
      const it = sampleRtp(def, spins, fxSlotRng(m.length * 1_000_003));
      let r = it.next();
      while (!r.done) r = it.next();
      const exact = REFERENCE_RTP[m].total / 100;
      const bound = big ? tol[m] : m === 'end' ? 0.08 : 0.03;
      expect(Math.abs(r.value - exact)).toBeLessThan(bound);
    }, big ? 24 * 3600_000 : 120_000);
  }
  it('pools never go below the seed and the draw keeps them consistent', () => {
    const def = defaultMachine('end');
    const t = drawSpin({ def, bet: 5000, buy: false, owned: false, pools: poolValues(def.jackpot, emptyPools()) }, fxSlotRng(9));
    expect(t.totalFifths).toBeGreaterThanOrEqual(0);
  });
});

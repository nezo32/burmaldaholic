/**
 * Independent tester suite: slots (GAME_DESIGN §8, Appendix A).
 * Numbers are typed in from the spec, not imported from the implementation; the spec's line
 * rules are re-implemented here and compared with the engine on every possible payline.
 */
import { describe, expect, it } from 'vitest';
import { defaultTable, evaluateGrid, evaluateLine, type Sym } from '../../src/games/slots/logic/engine';
import { lineStats, totalRtp } from '../../src/games/slots/logic/rtp';

type Spec = { weights: Record<string, number>; pays: Record<string, number>; lines: number; jackpot: number; rtpBase: number; rtpTotal: number; hit: number };
// §8.2–8.4 (pays are "3 of a kind pays N×" multipliers of the line bet)
const SPEC: Record<'copper' | 'gold' | 'netherite', Spec> = {
  copper: {
    weights: { berries: 24, apple: 20, golden_carrot: 16, emerald: 12, diamond: 8, seven: 5, creeper: 15 },
    pays: { berries: 10, apple: 10, golden_carrot: 20, emerald: 30, diamond: 60, seven: 150, creeper: 0 },
    lines: 1,
    jackpot: 0,
    rtpBase: 0.8976,
    rtpTotal: 0.8976,
    hit: 0.254,
  },
  gold: {
    weights: { berries: 22, apple: 19, golden_carrot: 16, emerald: 12, diamond: 8, seven: 5, wild: 3, creeper: 8, pearl: 5, star: 2 },
    pays: { berries: 8, apple: 7, golden_carrot: 11, emerald: 25, diamond: 50, seven: 100, wild: 200, creeper: 0, pearl: 10 },
    lines: 3,
    jackpot: 0.01,
    rtpBase: 0.9271,
    rtpTotal: 0.9371,
    hit: 0.245,
  },
  netherite: {
    weights: { berries: 20, apple: 19, golden_carrot: 16, emerald: 12, diamond: 9, seven: 6, wild: 3, tnt: 6, pearl: 5, clock: 2, star: 2 },
    pays: { berries: 8, apple: 9, golden_carrot: 14, emerald: 25, diamond: 50, seven: 100, wild: 250, tnt: 0, pearl: 10, clock: 50 },
    lines: 5,
    jackpot: 0.015,
    rtpBase: 0.94535, // Appendix A (§8.4 rounds to 94.54 %)
    rtpTotal: 0.9604,
    hit: 0.225,
  },
};
const REGULAR = ['berries', 'apple', 'golden_carrot', 'emerald', 'diamond', 'seven'];
const SPECIAL = ['creeper', 'tnt', 'pearl', 'clock', 'star'];

/** §8.1 rules 1–4, written from the spec text. Returns the line multiplier (star = 0 on progressive). */
function specLine(a: string, b: string, c: string, pays: Record<string, number>, starPays = 0): number {
  if (a === b && b === c && SPECIAL.includes(a)) return a === 'star' ? starPays : (pays[a] ?? 0);
  if (a === 'wild' && b === 'wild' && c === 'wild') return pays.wild ?? 0;
  let best = -1;
  for (const s of REGULAR) if ([a, b, c].every((x) => x === s || x === 'wild')) best = Math.max(best, pays[s] ?? 0);
  if (best >= 0) return best;
  if (a === 'berries') return b === 'berries' ? 3 : 2;
  return 0;
}

function enumerate(spec: Spec, fn: (a: string, b: string, c: string, p: number) => void): void {
  const syms = Object.keys(spec.weights);
  const tot = syms.reduce((s, k) => s + spec.weights[k]!, 0);
  for (const a of syms) for (const b of syms) for (const c of syms) fn(a, b, c, (spec.weights[a]! * spec.weights[b]! * spec.weights[c]!) / tot ** 3);
}

describe('slots: spec tables are self-consistent', () => {
  for (const [tier, spec] of Object.entries(SPEC)) {
    it(`${tier}: weights total 100`, () => expect(Object.values(spec.weights).reduce((a, b) => a + b, 0)).toBe(100));
    it(`${tier}: exact per-line RTP and hit frequency match §8`, () => {
      let rtp = 0;
      let hit = 0;
      enumerate(spec, (a, b, c, p) => {
        const m = specLine(a, b, c, spec.pays);
        rtp += p * m;
        if (m > 0) hit += p;
      });
      expect(rtp).toBeCloseTo(spec.rtpBase, 4);
      expect(rtp + spec.jackpot).toBeCloseTo(spec.rtpTotal, 3);
      expect(hit).toBeCloseTo(spec.hit, 3);
      // §17: house edge stays positive, streak cap needs RTP < 0.99
      expect(rtp + spec.jackpot).toBeLessThan(0.99);
    });
  }
  it('Appendix A: Copper per-line probabilities', () => {
    const s = SPEC.copper;
    const p = (x: string) => s.weights[x]! / 100;
    expect(p('berries') * (1 - p('berries'))).toBeCloseTo(0.1824, 6);
    expect(p('berries') ** 2 * (1 - p('berries'))).toBeCloseTo(0.043776, 6);
    expect(p('creeper') ** 3).toBeCloseTo(0.003375, 6);
    expect(1 / p('creeper') ** 3).toBeCloseTo(296.3, 1); // "3 Creepers 1 in 296 spins"
  });
  it('owned casino machines pay 1000× for three stars (§8.5): 93.51 % / ≈95.3 %', () => {
    for (const [tier, want] of [
      ['gold', 0.9351],
      ['netherite', 0.9533],
    ] as const) {
      let rtp = 0;
      enumerate(SPEC[tier], (a, b, c, p) => (rtp += p * specLine(a, b, c, SPEC[tier].pays, 1000)));
      expect(rtp).toBeCloseTo(want, 3);
    }
  });
});

describe('slots: engine agrees with the spec on every payline', () => {
  for (const [tier, spec] of Object.entries(SPEC) as [keyof typeof SPEC, Spec][]) {
    it(`${tier}: evaluateLine == §8.1 for all ${Object.keys(spec.weights).length ** 3} lines`, () => {
      const table = defaultTable(tier);
      let mismatches = 0;
      enumerate(spec, (a, b, c) => {
        const want = specLine(a, b, c, spec.pays);
        const got = evaluateLine(a as Sym, b as Sym, c as Sym, table)?.multiplier ?? 0;
        if (got !== want) mismatches++;
      });
      expect(mismatches).toBe(0);
    });
    it(`${tier}: engine RTP (base + jackpot) matches §17`, () => {
      const table = defaultTable(tier);
      expect(table.lines).toBe(spec.lines);
      expect(lineStats(table).baseRtp).toBeCloseTo(spec.rtpBase, 4);
      expect(totalRtp(table, spec.jackpot)).toBeCloseTo(spec.rtpTotal, 3);
    });
  }
  it('owned machines: star pays the configured fixed multiplier', () => {
    const t = defaultTable('gold', true);
    expect(t.progressive).toBe(false);
    expect(evaluateLine('star', 'star', 'star', t)?.multiplier).toBe(1000);
  });
  it('wild never substitutes for specials; three wilds use the wild pay', () => {
    const t = defaultTable('netherite');
    expect(evaluateLine('wild', 'clock', 'clock', t)?.multiplier ?? 0).toBe(0);
    expect(evaluateLine('wild', 'wild', 'wild', t)?.multiplier).toBe(250);
    expect(evaluateLine('wild', 'seven', 'wild', t)?.multiplier).toBe(100);
    // wild does not count as a leading berry (rule 4)
    expect(evaluateLine('wild', 'berries', 'apple', t)?.multiplier ?? 0).toBe(0);
    expect(evaluateLine('berries', 'wild', 'apple', t)?.multiplier).toBe(2);
  });
  it('payout = floor(mult × line bet), summed over lines; jackpot awarded once for several star lines', () => {
    const t = defaultTable('netherite');
    const g: Sym[][] = [
      ['star', 'star', 'star'],
      ['star', 'star', 'star'],
      ['berries', 'apple', 'apple'],
    ];
    const e = evaluateGrid(g, t, 3);
    expect(e.jackpotHit).toBe(true);
    expect(e.special).toBe('star');
    // bottom row: 1 leading berry = 2× line bet; diagonals: star,star,apple / berries,star,star -> berry 2×
    expect(e.basePayout).toBe(2 * 3 + 2 * 3);
  });
});

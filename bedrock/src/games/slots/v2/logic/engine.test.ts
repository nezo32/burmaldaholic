/**
 * Slots v2 engine: strips, 243 ways, tumbles, draw, cap, codec, pools (SLOTS.md §1–§5, §15 items 1–3, 9, 10,
 * 13). Exact enumeration totals are in `rtp.test.ts`; shared vectors in `vectors.test.ts`.
 */
import { describe, expect, it } from 'vitest';
import { buildMachineDef, defaultMachine, readMachineConfig, stripError } from './config';
import { applySticky, drawSpin, evaluateSpin, evaluateWays, featureCode, huntOpens, poolsAfter, reservation, runTumbles, stakeOf } from './engine';
import { applyAwards, contribute, emptyPools, loadPools, migrateV1Pools, poolValues, resetPools } from './jackpots';
import { DEFAULT_CONFIG, SYMBOLS } from './machines';
import { decodeTape, encodeTape } from './tape-codec';
import { fxSlotRng, scriptedRng } from './test-rng';
import { type MachineDef, type MachineId, type SpinTape, MACHINE_IDS, windowFromStops } from './types';

const code = (def: MachineDef, c: string): number => def.codes.indexOf(c);
/** Window from 5 columns of 3 codes (top → bottom). */
const win = (def: MachineDef, cols: string[]): number[] => cols.flatMap((col) => col.split(' ').map((c) => code(def, c)));

const COUNTS: Record<MachineId, Array<Record<string, number>>> = {
  overworld: [
    { SC: 2, BN: 3, DI: 3, EM: 3, GO: 4, IR: 4, AP: 5, CA: 5, WH: 5, BE: 6 },
    { WD: 4, SC: 1, DI: 3, EM: 3, GO: 4, IR: 4, AP: 5, CA: 5, WH: 5, BE: 6 },
    { WD: 4, SC: 1, BN: 2, DI: 3, EM: 3, GO: 4, IR: 4, AP: 5, CA: 5, WH: 4, BE: 5 },
    { WD: 4, SC: 1, DI: 3, EM: 3, GO: 4, IR: 4, AP: 5, CA: 5, WH: 5, BE: 6 },
    { SC: 2, BN: 3, DI: 3, EM: 3, GO: 4, IR: 4, AP: 5, CA: 5, WH: 5, BE: 6 },
  ],
  nether: [
    { SC: 1, CN: 2, SK: 2, BR: 3, MC: 3, QZ: 3, NW: 5, CF: 5, WF: 4, GD: 4 },
    { WD: 2, SC: 1, CN: 2, SK: 2, BR: 2, MC: 3, QZ: 3, NW: 4, CF: 4, WF: 5, GD: 4 },
    { WD: 2, SC: 1, CN: 3, SK: 2, BR: 2, MC: 3, QZ: 3, NW: 4, CF: 4, WF: 4, GD: 4 },
    { WD: 2, SC: 1, CN: 2, SK: 2, BR: 2, MC: 3, QZ: 3, NW: 4, CF: 4, WF: 5, GD: 4 },
    { SC: 1, CN: 2, SK: 2, BR: 3, MC: 3, QZ: 3, NW: 5, CF: 5, WF: 4, GD: 4 },
  ],
  end: [
    { SC: 1, DH: 2, EL: 3, SS: 4, CH: 5, EP: 7, PU: 7, ER: 8, ES: 8 },
    { WD: 1, SC: 1, BN: 2, DH: 2, EL: 3, SS: 4, CH: 5, EP: 6, PU: 6, ER: 7, ES: 8 },
    { WD: 1, SC: 1, BN: 3, DH: 2, EL: 3, SS: 4, CH: 4, EP: 6, PU: 7, ER: 7, ES: 7 },
    { WD: 1, SC: 1, BN: 2, DH: 2, EL: 3, SS: 4, CH: 5, EP: 6, PU: 6, ER: 7, ES: 8 },
    { SC: 1, DH: 2, EL: 3, SS: 4, CH: 5, EP: 7, PU: 7, ER: 8, ES: 8 },
  ],
};

describe('strips = Appendix A (SLOTS.md §15.1)', () => {
  for (const m of MACHINE_IDS) {
    it(`${m}: per-reel symbol counts of §3, lengths, placement rules`, () => {
      const def = defaultMachine(m);
      def.strips.forEach((s, r) => {
        expect(s.length).toBe({ overworld: 40, nether: 32, end: 45 }[m]);
        const counts: Record<string, number> = {};
        for (const x of s) counts[def.codes[x]!] = (counts[def.codes[x]!] ?? 0) + 1;
        expect(counts).toEqual(COUNTS[m][r]);
      });
      expect(stripError(m, DEFAULT_CONFIG[m].strips)).toBeUndefined();
    });
  }
  it('stacks: Overworld totems 2 stacks of 2; Nether coins and End dragon heads stacked', () => {
    const ow = defaultMachine('overworld');
    for (const r of [1, 2, 3]) {
      const s = ow.strips[r]!;
      const runs = s.map((x, i) => (x === 0 && s[(i + 1) % s.length] === 0 ? 1 : 0)).reduce((a: number, b) => a + b, 0);
      expect(runs).toBe(2);
    }
  });
  it('validation rejects wilds on reels 1/5, misplaced bonus symbols, close scatters, unknown codes', () => {
    const s = DEFAULT_CONFIG.overworld.strips.slice();
    expect(stripError('overworld', [s[1]!, s[1]!, s[2]!, s[3]!, s[4]!])).toMatch(/wild/);
    expect(stripError('overworld', [s[0]!, s[0]!, s[2]!, s[3]!, s[4]!])).toMatch(/bonus/);
    expect(stripError('overworld', [s[0]!, s[1]!, s[2]!, s[3]!, 'SC AP SC AP AP AP'])).toMatch(/scatters/);
    expect(stripError('overworld', [s[0]!, s[1]!, s[2]!, s[3]!, 'XX AP AP'])).toMatch(/unknown/);
    expect(stripError('overworld', s.slice(0, 4))).toMatch(/5 strips/);
  });
});

describe('243-ways evaluator (SLOTS.md §1.1, §15.2)', () => {
  const ow = defaultMachine('overworld');
  it('reel counts 2,3,1 → 6 ways, wilds count on reels 2–4', () => {
    const w = win(ow, ['DI DI AP', 'DI WD DI', 'WD CA CA', 'AP AP AP', 'CA CA CA']);
    const r = evaluateWays(ow, w);
    const di = r.wins.find((x) => x.symbol === code(ow, 'DI'))!;
    expect(di).toMatchObject({ k: 3, ways: 6, payFifths: 4 * 6 });
    // apple: reel 1 AP, reels 2–3 through the wilds (1 × 1), reel 4 three apples → 4 of a kind, 3 ways × 2
    expect(r.wins.find((x) => x.symbol === code(ow, 'AP'))).toMatchObject({ k: 4, ways: 3, payFifths: 6 });
    expect(r.payFifths).toBe(24 + 6);
  });
  it('a symbol stopping at reel 3, several symbols at once, 5 of a kind with 3×3×3×3×3', () => {
    const r = evaluateWays(ow, win(ow, ['AP AP AP', 'AP AP AP', 'AP AP AP', 'AP AP AP', 'AP AP AP']));
    expect(r.wins).toEqual([{ symbol: code(ow, 'AP'), k: 5, ways: 243, payFifths: 4 * 243, cellMask: 0x7fff }]);
    const r2 = evaluateWays(ow, win(ow, ['AP CA BE', 'AP CA WH', 'WD CA BE', 'EM EM EM', 'EM EM EM']));
    expect(r2.wins.map((x) => [ow.codes[x.symbol], x.k, x.ways])).toEqual([
      ['AP', 3, 1],
      ['CA', 3, 2],
    ]);
    expect(r2.payFifths).toBe(1 + 2);
  });
  it('no wild-only ways, wild never on reel 1, scatters/chests do not pay by ways', () => {
    const r = evaluateWays(ow, win(ow, ['SC BN AP', 'WD WD WD', 'WD WD WD', 'WD WD WD', 'SC BN CA']));
    expect(r.wins.map((x) => ow.codes[x.symbol])).toEqual(['AP']);
    expect(r.scatters).toBe(2);
    expect(r.bonusCount).toBe(2);
  });
  it('winning cell mask = the symbol and the wilds on reels 1…k only', () => {
    const r = evaluateWays(ow, win(ow, ['DI AP AP', 'WD AP AP', 'DI AP AP', 'CA CA CA', 'DI DI DI']));
    const di = r.wins.find((x) => x.symbol === code(ow, 'DI'))!;
    expect(di.cellMask).toBe((1 << 0) | (1 << 3) | (1 << 6));
    expect(di.k).toBe(3);
  });
  it('sticky mask forces reels 2–4 to WWW and hides their scatters', () => {
    const end = defaultMachine('end');
    const w = win(end, ['DH SC EL', 'SC ER ER', 'ES ES ES', 'SC EP EP', 'DH ES ES']);
    const r = evaluateWays(end, w, 0b111);
    expect(r.scatters).toBe(1);
    const dh = r.wins.find((x) => x.symbol === code(end, 'DH'))!;
    expect(dh).toMatchObject({ k: 5, ways: 27, payFifths: 150 * 27 });
    expect(applySticky(end, w, 0b010).slice(6, 9)).toEqual([0, 0, 0]);
  });
});

describe('Nether tumbles (SLOTS.md §3.2)', () => {
  const ne = defaultMachine('nether');
  it('the chain is a pure function of the stops; refill from above the window', () => {
    const stops = [10, 16, 17, 0, 0];
    const c = runTumbles(ne, stops, ne.ladder);
    expect(c.steps.length - 1).toBe(8);
    expect(c.steps.map((s) => s.multiplier)).toEqual([1, 2, 3, 5, 5, 5, 5, 5, 5]);
    // refill: after step 0, reel r's top moved up by the number of removed cells
    const s0 = c.steps[0]!;
    const s1 = c.steps[1]!;
    for (let r = 0; r < 5; r++) {
      let m = 0;
      for (let y = 0; y < 3; y++) if (s0.result.winMask & (1 << (r * 3 + y))) m++;
      const L = ne.strips[r]!.length;
      expect(s1.tops[r]).toBe((((s0.tops[r]! - m) % L) + L) % L);
      for (let i = 0; i < m; i++) expect(s1.window[r * 3 + i]).toBe(ne.strips[r]![(((s1.tops[r]! + i) % L) + L) % L]);
    }
    expect(c.payFifths).toBe(c.steps.reduce((a, s) => a + s.payFifths, 0));
    expect(runTumbles(ne, stops, ne.ladder)).toEqual(c);
  });
  it('scatters and coins never explode', () => {
    for (const stops of [
      [11, 18, 15, 0, 0],
      [2, 23, 15, 0, 0],
      [0, 22, 16, 0, 0],
    ]) {
      const c = runTumbles(ne, stops, ne.ladder);
      for (const st of c.steps) for (let i = 0; i < 15; i++) if (st.result.winMask & (1 << i)) expect(['SC', 'CN']).not.toContain(ne.codes[st.window[i]!]);
    }
  });
  it('free-spin ladder ×2/×4/×6/×10', () => {
    const c = runTumbles(ne, [11, 18, 15, 0, 0], ne.ladderFree);
    expect(c.steps.map((s) => s.multiplier)).toEqual([2, 4, 6, 10, 10, 10, 10, 10]);
  });
});

describe('draw (SLOTS.md §1.2): tapes, features, cap, jackpots', () => {
  const pools = (m: MachineId): number[] => poolValues(defaultMachine(m).jackpot, emptyPools());

  it('the draw is deterministic in the rng and every intermediate amount is an integer at every ladder bet', () => {
    for (const m of MACHINE_IDS) {
      const def = defaultMachine(m);
      for (const bet of DEFAULT_CONFIG[m].bets) {
        const rng = fxSlotRng(bet * 7919 + m.length);
        for (let i = 0; i < 400; i++) {
          const t = drawSpin({ def, bet, buy: false, owned: false, pools: pools(m) }, rng);
          expect(Number.isInteger((t.totalFifths * bet) / 5)).toBe(true);
          for (const j of t.jackpots) expect(Number.isInteger(j.chips)).toBe(true);
          expect(t.totalFifths).toBeLessThanOrEqual(def.capMultiple * 5);
        }
      }
      const a = drawSpin({ def, bet: 100, buy: false, owned: false, pools: pools(m) }, fxSlotRng(5));
      const b = drawSpin({ def, bet: 100, buy: false, owned: false, pools: pools(m) }, fxSlotRng(5));
      expect(a).toEqual(b);
    }
  });

  it('base total = evaluated spin + features; free spins sum', () => {
    for (const m of MACHINE_IDS) {
      const def = defaultMachine(m);
      const rng = fxSlotRng(99);
      let fs = 0;
      for (let i = 0; i < 20_000 && fs < 5; i++) {
        const t = drawSpin({ def, bet: 10, buy: false, owned: false, pools: pools(m) }, rng);
        const base = evaluateSpin(def, t.stops, false).payFifths;
        if (!t.freeSpins && !t.hunt && !t.hoard && !t.wheel) expect(t.totalFifths).toBe(Math.min(base, def.capMultiple * 5));
        if (t.freeSpins) {
          fs++;
          expect(t.freeSpins.payFifths).toBe(t.freeSpins.spins.reduce((a, s) => a + s.payFifths, 0));
          expect(t.freeSpins.spins.length).toBeGreaterThanOrEqual(t.freeSpins.awarded);
          expect(t.freeSpins.spins.length).toBeLessThanOrEqual(def.fsCap);
        }
      }
      expect(fs).toBeGreaterThan(0);
    }
  });

  it('Overworld Treasure Hunt: i.i.d. entries in reveal order, ends at the Creeper, remaining entries are real draws', () => {
    const def = defaultMachine('overworld');
    // reels 1, 3, 5 show a chest: stops 13 (R1), 6 (R3), 9 (R5)
    const stops = [13, 0, 6, 0, 9];
    expect(evaluateWays(def, windowFromStops(def, stops)).bonusCount).toBe(3);
    const entries = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14];
    // weights order: x1 x2 x3 x5 x10 x25 mini minor major grand creeper → r values picking x5, x25, mini, creeper, …
    const W = [30000, 22000, 14000, 9000, 3500, 800, 600, 150, 20, 3, 22000];
    const cum = (i: number): number => W.slice(0, i).reduce((a, b) => a + b, 0);
    const picks = [3, 5, 6, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0].map(cum);
    const t = drawSpin({ def, bet: 10, buy: false, owned: false, pools: pools('overworld') }, scriptedRng([...stops, ...picks]));
    expect(t.hunt!.entries.slice(0, 5)).toEqual([5, 25, -1, 0, 1]);
    expect(t.hunt!.entries).toHaveLength(entries.length);
    expect(t.hunt!.opened).toBe(0); // pick progress
    expect(huntOpens(def, t)).toBe(4); // x5, x25, Mini, Creeper
    const base = evaluateSpin(def, stops, false).payFifths;
    expect(t.totalFifths).toBe(base + 30 * 5);
    expect(t.jackpots).toEqual([{ tier: 1, chips: Math.floor((1000 * 10) / 100), owned: false }]);
  });

  it('Nether Hoard: coins lock, respins reset on a new coin, all 15 wins the Grand; Hoard before free spins', () => {
    const def = defaultMachine('nether');
    const rng = fxSlotRng(4242);
    let seen = 0;
    for (let i = 0; i < 300_000 && seen < 3; i++) {
      const t = drawSpin({ def, bet: 10, buy: false, owned: false, pools: pools('nether') }, rng);
      if (!t.hoard) continue;
      seen++;
      const h = t.hoard;
      expect(h.initialCells.length).toBeGreaterThanOrEqual(6);
      const all = new Set([...h.initialCells, ...h.respinCells.flat()]);
      expect(all.size).toBe(h.initialCells.length + h.respinCells.flat().length);
      // respins end after 3 empty respins in a row (or a full board)
      let left = 3;
      for (const c of h.respinCells) left = c.length ? 3 : left - 1;
      expect(left === 0 || all.size === 15).toBe(true);
      const coins = [...h.initialValues, ...h.respinValues.flat()];
      const cash = coins.filter((v) => v > 0).reduce((a, b) => a + b, 0) * 5;
      expect(t.totalFifths).toBeGreaterThanOrEqual(Math.min(cash, def.capMultiple * 5));
    }
    expect(seen).toBe(3);
  });

  it('End Dragon Wheel: UP climbs rings, the tape holds one segment per ring reached', () => {
    const def = defaultMachine('end');
    // crystals on reels 2, 3, 4: R2 21, R3 5, R4 24; outer segment 1 = UP, middle 7 = UP, core 4 = GRAND
    const t = drawSpin({ def, bet: 5000, buy: false, owned: false, pools: pools('end') }, scriptedRng([0, 21, 5, 24, 0, 1, 7, 4]));
    expect(t.wheel!.segments).toEqual([1, 7, 4]);
    expect(t.jackpots).toEqual([{ tier: 4, chips: 12_500_000, owned: false }]);
    const owned = drawSpin({ def, bet: 100, buy: false, owned: true, pools: [] }, scriptedRng([0, 21, 5, 24, 0, 1, 7, 4]));
    expect(owned.jackpots).toEqual([{ tier: 4, chips: 100 * 1000, owned: true }]);
    // owned fixed prize is inside the spin total (and the cap)
    expect(owned.totalFifths).toBe(Math.min(evaluateSpin(def, owned.stops, false).payFifths + 1000 * 5, def.capMultiple * 5));
  });

  it('Void Walker: eggs expand and stay sticky for the rest of the feature', () => {
    const def = defaultMachine('end');
    const rng = fxSlotRng(77);
    let checked = 0;
    for (let i = 0; i < 2000; i++) {
      const t = drawSpin({ def, bet: 50, buy: true, owned: false, pools: pools('end') }, rng);
      expect(t.stops).toEqual([]);
      let sticky = 0;
      for (const s of t.freeSpins!.spins) {
        expect(s.stickyMaskAfter & sticky).toBe(sticky);
        sticky = s.stickyMaskAfter;
        checked++;
      }
    }
    expect(checked).toBeGreaterThan(2000 * 9 - 1);
  });

  it('buy feature: as a 3-scatter trigger, no base spin, stake = price', () => {
    const def = defaultMachine('nether');
    const t = drawSpin({ def, bet: 10, buy: true, owned: false, pools: pools('nether') }, fxSlotRng(3));
    expect(t.bought).toBe(true);
    expect(t.freeSpins!.awarded).toBe(12);
    expect(stakeOf(def, 10, true)).toBe(184);
    expect(stakeOf(defaultMachine('end'), 50, true)).toBe(5450);
    expect(() => drawSpin({ def: defaultMachine('overworld'), bet: 10, buy: true, owned: false, pools: [] }, fxSlotRng(1))).toThrow();
  });

  it('max-win cap: forced tapes above the cap end the feature at the cap and set MAX WIN (§15.10)', () => {
    const low = buildMachineDef('nether', { ...readMachineConfig('nether', () => undefined).config, maxWinMultiple: 50 });
    const rng = fxSlotRng(11);
    let hits = 0;
    for (let i = 0; i < 400; i++) {
      const t = drawSpin({ def: low, bet: 10, buy: true, owned: false, pools: pools('nether') }, rng);
      if (!t.capHit) {
        expect(t.totalFifths).toBeLessThan(250);
        continue;
      }
      hits++;
      expect(t.totalFifths).toBe(250);
      // the feature stopped right after the spin that crossed the cap
      const spins = t.freeSpins!.spins;
      const before = spins.slice(0, -1).reduce((a, s) => a + s.payFifths, 0);
      expect(before).toBeLessThan(250);
      expect(before + spins[spins.length - 1]!.payFifths).toBeGreaterThanOrEqual(250);
    }
    expect(hits).toBeGreaterThan(0);
    expect(reservation(low, 10)).toBe(500);
    expect(reservation(defaultMachine('end'), 5000)).toBe(25_000_000);
  });
});

describe('tape codec (SLOTS.md §8.1)', () => {
  it('round trips every drawn tape; worst case < 1 200 chars', () => {
    let longest = 0;
    for (const m of MACHINE_IDS) {
      const def = defaultMachine(m);
      const rng = fxSlotRng(m.length * 31);
      for (let i = 0; i < 3000; i++) {
        const t = drawSpin({ def, bet: DEFAULT_CONFIG[m].bets.at(-1)!, buy: i % 50 === 0 && def.buyPriceFifths > 0, owned: i % 7 === 0, pools: poolValues(def.jackpot, emptyPools()) }, rng);
        const s = encodeTape(t);
        expect(decodeTape(s)).toEqual(t);
        longest = Math.max(longest, s.length);
      }
    }
    expect(longest).toBeLessThan(1200);
  });
  it('End with 40 free spins stays below 1 200 characters', () => {
    const t: SpinTape = {
      machine: 'end',
      bet: 5000,
      bought: false,
      stops: [44, 44, 44, 44, 44],
      freeSpins: { awarded: 14, spins: Array.from({ length: 40 }, () => ({ stops: [44, 44, 44, 44, 44], stickyMaskAfter: 7, retrigger: true, payFifths: 24_999 })), payFifths: 40 * 24_999 },
      wheel: { segments: [19, 15, 11] },
      jackpots: [{ tier: 4, chips: 99_999_999, owned: false }],
      totalFifths: 25_000,
      capHit: true,
    };
    const s = encodeTape(t);
    expect(s.length).toBeLessThan(1200);
    expect(decodeTape(s)).toEqual(t);
  });
  it('rejects malformed strings', () => {
    for (const bad of ['', '1;o;a;;0;1.2.3.4.5;;;;;', '2;x;a;;0;1.2.3.4.5;;;;;', '2;o;a;z;0;1.2.3.4.5;;;;;', '2;o;a;;0;1.2.3;;;;;', '2;o;a;;0;1.2.3.4.5;;;;;9.1.1'])
      expect(() => decodeTape(bad)).toThrow();
  });
});

describe('progressive pools (SLOTS.md §5, §15.9)', () => {
  it('contributions are exact over many stakes (hidden remainder)', () => {
    const j = defaultMachine('overworld').jackpot;
    const p = emptyPools();
    let added = 0;
    for (let i = 0; i < 1000; i++) added += contribute(j, p, 5).reduce((a, b) => a + b, 0);
    // 1 % of 5 000 chips = 50 (0.4 + 0.3 + 0.2 + 0.1 %)
    expect(added).toBe(20 + 15 + 10 + 5);
    expect(p[1]).toEqual({ inc: 20, rem: 0 });
  });
  it('conservation: Σ contributions + minted seeds = Σ awards + pool growth', () => {
    const def = defaultMachine('overworld');
    const pools = emptyPools();
    const rng = fxSlotRng(2024);
    let contributed = 0;
    let awarded = 0;
    let minted = 0;
    for (let i = 0; i < 60_000; i++) {
      const bet = [5, 10, 100][i % 3]!;
      contributed += contribute(def.jackpot, pools, bet).reduce((a, b) => a + b, 0);
      const before = poolValues(def.jackpot, pools);
      const t = drawSpin({ def, bet, buy: false, owned: false, pools: before }, rng);
      expect(poolsAfter(def, before, t)).toEqual(poolValues(def.jackpot, (() => {
        const copy = pools.map((x) => ({ ...x }));
        applyAwards(def, copy, t);
        return copy;
      })()));
      for (const a of t.jackpots) {
        const seed = def.jackpot.seeds[a.tier]!;
        minted += Math.floor((seed * Math.min(bet, 100)) / 100);
      }
      awarded += applyAwards(def, pools, t);
    }
    const incNow = pools.reduce((a, p) => a + p.inc, 0);
    // awards = minted seed share + increment share (floor rounding may keep ≤ 1 chip per award in the pool)
    expect(Math.abs(contributed + minted - (awarded + incNow))).toBeLessThanOrEqual(awarded > 0 ? 200 : 0);
    expect(contributed).toBeGreaterThan(0);
  });
  it('award at the reference bet takes the whole pool; smaller bets a share; the rest stays', () => {
    const def = defaultMachine('overworld');
    const pools = emptyPools();
    pools[4] = { inc: 2345, rem: 7 };
    const tape = (bet: number): SpinTape => ({ machine: 'overworld', bet, bought: false, stops: [0, 0, 0, 0, 0], jackpots: [{ tier: 4, chips: Math.floor(((50_000 + 2345) * Math.min(bet, 100)) / 100), owned: false }], totalFifths: 0, capHit: false });
    const half = pools.map((x) => ({ ...x }));
    expect(applyAwards(def, half, tape(50))).toBe(26_172);
    expect(half[4]).toEqual({ inc: 1172, rem: 7 });
    expect(applyAwards(def, pools, tape(100))).toBe(52_345);
    expect(pools[4]).toEqual({ inc: 0, rem: 7 });
  });
  it('load / reset / v1 migration', () => {
    expect(loadPools('junk')).toEqual(emptyPools());
    expect(loadPools([null, { inc: 5, rem: 3 }, { inc: -1 }])[1]).toEqual({ inc: 5, rem: 3 });
    const p = loadPools([null, { inc: 5, rem: 3 }, { inc: 7, rem: 0 }]);
    expect(resetPools(p)).toBe(12);
    const all = { overworld: emptyPools(), nether: emptyPools(), end: emptyPools() };
    expect(migrateV1Pools(all, { gold: { pool: 61_240 }, netherite: { pool: 49_000 } }, { gold: 5000, netherite: 50_000 })).toEqual({ nether: 56_240, end: 0 });
    expect(all.nether[4]!.inc).toBe(56_240);
  });
});

describe('config (SLOTS.md §12)', () => {
  it('defaults build the §3 tables in fifths', () => {
    const ow = defaultMachine('overworld');
    expect(ow.paysFifths[code(ow, 'DI')]).toEqual([4, 10, 20]);
    expect(ow.scatterFifths).toEqual([5, 50, 250]);
    expect(defaultMachine('nether').buyPriceFifths).toBe(92);
    expect(defaultMachine('end').buyPriceFifths).toBe(545);
    expect(defaultMachine('end').wheel!.rings.map((r) => r.length)).toEqual([20, 16, 12]);
    expect(SYMBOLS.end[3]!.id).toBe('dragon_head');
  });
  it('wheel wedge counts (SLOTS.md §3.3)', () => {
    const count = (ring: number[]): Record<number, number> => ring.reduce((a: Record<number, number>, c) => ((a[c] = (a[c] ?? 0) + 1), a), {});
    const [o, m, c] = defaultMachine('end').wheel!.rings as [number[], number[], number[]];
    expect(count(o)).toEqual({ 10: 4, 12: 3, 15: 3, 20: 2, 25: 2, 40: 1, 75: 1, [-1]: 2, 0: 2 });
    expect(count(m)).toEqual({ 30: 4, 50: 4, 75: 3, 100: 2, [-2]: 2, 0: 1 });
    expect(count(c)).toEqual({ 150: 4, 250: 3, 500: 1, [-3]: 3, [-4]: 1 });
  });
  it('invalid keys are rejected one by one and the default kept', () => {
    const over: Record<string, unknown> = {
      'slots.overworld.bets': [5, 12, 20],
      'slots.overworld.defaultBet': 7,
      'slots.overworld.pays': { ...DEFAULT_CONFIG.overworld.pays, diamond: [0.8, 2, 4.1] },
      'slots.overworld.maxWinMultiple': 750,
      'slots.overworld.pick.weights': { ...DEFAULT_CONFIG.overworld.pick!.weights, creeper: 44_000 },
    };
    const { config, errors } = readMachineConfig('overworld', (k) => over[k]);
    expect(errors.map((e) => e.split(':')[0])).toEqual(['slots.overworld.bets', 'slots.overworld.pays']);
    expect(config.bets).toEqual([5, 10, 20, 50, 100]);
    expect(config.defaultBet).toBe(5);
    expect(config.maxWinMultiple).toBe(750);
    expect(config.pick!.weights.creeper).toBe(44_000);
    const e = readMachineConfig('end', (k) => (k === 'slots.end.wheel.core' ? ['150', 'UP', '250', '500'] : undefined));
    expect(e.errors).toHaveLength(1);
  });
  it('feature codes', () => {
    const t = drawSpin({ def: defaultMachine('end'), bet: 50, buy: true, owned: false, pools: [] }, fxSlotRng(1));
    expect(featureCode(t)).toBe(1);
  });
});

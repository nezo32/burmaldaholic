/**
 * Adversarial / property tests of the slots v2 engine (lane B-L8 review): an independent ways reference, a replay
 * of every tape's total from its own draws (cap placement inside features, jackpot order), tumble termination,
 * pool conservation per award, buy pricing and `validateRtp` on out-of-range configs.
 */
import { describe, expect, it } from 'vitest';
import { buildMachineDef, defaultMachine, readMachineConfig, rtpTablesDefault } from './config';
import { MAX_TUMBLE_STEPS, drawSpin, evaluateSpin, evaluateWays, freeSpinsFor, huntOpens, poolAward, runTumbles, stakeOf, wildOf } from './engine';
import { DEFAULT_CONFIG } from './machines';
import { computeRtp, rtpWarnings } from './rtp';
import { decodeTape, encodeTape } from './tape-codec';
import { fxSlotRng } from './test-rng';
import { type MachineDef, type SpinTape, MACHINE_IDS, REELS, ROWS } from './types';

// ---- independent 243-ways reference: count every distinct k-prefix path ------------------------------

function refWays(def: MachineDef, cells: readonly number[]): number {
  const wd = wildOf(def);
  let total = 0;
  for (let p = 0; p < def.roles.length; p++) {
    if (def.roles[p] !== 'PAY') continue;
    const match = (r: number, y: number): boolean => cells[r * ROWS + y] === p || cells[r * ROWS + y] === wd;
    // number of paths of exactly length k (reel k+1 has no match, or k = 5)
    const walk = (r: number): void => {
      for (let y = 0; y < ROWS; y++) {
        if (!match(r, y)) continue;
        const k = r + 1;
        const next = k < REELS && [0, 1, 2].some((yy) => match(k, yy));
        if (next) walk(k);
        else if (k >= 3) total += def.paysFifths[p]![k - 3]!;
      }
    };
    walk(0);
  }
  return total;
}

describe('243 ways: independent path-count reference (random windows incl. wilds everywhere allowed)', () => {
  for (const m of MACHINE_IDS) {
    it(`${m}`, () => {
      const def = defaultMachine(m);
      const rng = fxSlotRng(99);
      const pays = def.roles.map((r, i) => (r === 'PAY' ? i : -1)).filter((i) => i >= 0);
      const wd = wildOf(def);
      for (let n = 0; n < 4000; n++) {
        // bias towards few symbols so long ways are common
        const pool = [pays[rng.nextInt(pays.length)]!, pays[rng.nextInt(pays.length)]!, wd, rng.nextInt(def.roles.length)];
        const cells: number[] = [];
        for (let r = 0; r < REELS; r++)
          for (let y = 0; y < ROWS; y++) {
            let s = pool[rng.nextInt(pool.length)]!;
            if (s === wd && (r === 0 || r === 4)) s = pays[0]!; // wilds never on reels 1/5
            cells.push(s);
          }
        const w = evaluateWays(def, cells);
        expect(w.payFifths).toBe(refWays(def, cells));
        // every win's cells are the symbol or wilds on reels 1..k, nothing beyond
        for (const win of w.wins) for (let c = 0; c < 15; c++) if (win.cellMask & (1 << c)) expect(Math.floor(c / 3)).toBeLessThan(win.k);
      }
    }, 60_000);
  }
});

// ---- tape replay: the total is a pure function of the tape's own draws ------------------------------

/** Recompute total, capHit and jackpot tiers from the draws of a tape (never from its totals). */
function replay(def: MachineDef, tape: SpinTape, owned: boolean): { total: number; capHit: boolean; tiers: number[]; fsPlayed: number } {
  const cap = def.capMultiple * 5;
  let total = 0;
  let hit = false;
  const tiers: number[] = [];
  const add = (f: number): boolean => {
    if (hit) return true;
    total += f;
    if (total >= cap) {
      total = cap;
      hit = true;
    }
    return hit;
  };
  const prize = (c: number): boolean => {
    if (c > 0) return add(c * 5);
    if (c < 0) {
      tiers.push(-c);
      return owned ? add(def.jackpot.owned[-c]! * 5) : hit;
    }
    return hit;
  };
  let fsAward: number;
  if (!tape.bought) {
    const base = evaluateSpin(def, tape.stops, false);
    add(base.payFifths);
    fsAward = freeSpinsFor(def, base.scatters);
    if (!hit && tape.hunt) {
      for (const e of tape.hunt.entries) {
        if (e === 0) break;
        if (prize(e)) break;
      }
    }
    if (!hit && tape.hoard) {
      const h = tape.hoard;
      for (const v of [...h.initialValues, ...h.respinValues.flat()]) if (prize(v)) break;
      const n = h.initialCells.length + h.respinCells.flat().length;
      if (!hit && n >= 15) prize(-4);
    }
    if (!hit && tape.wheel) {
      const rings = def.wheel!.rings;
      const segs = tape.wheel.segments;
      segs.forEach((s, i) => {
        if (i < segs.length - 1) expect(rings[i]![s]).toBe(0); // only UP climbs
      });
      prize(rings[segs.length - 1]![segs[segs.length - 1]!]!);
    }
  } else fsAward = def.freeSpins[0];
  let played = 0;
  if (tape.freeSpins) {
    let sticky = 0;
    let remaining = Math.min(fsAward, def.fsCap);
    let given = remaining;
    for (const s of tape.freeSpins.spins) {
      expect(hit).toBe(false); // nothing is played after the cap
      expect(remaining).toBeGreaterThan(0);
      remaining--;
      const e = evaluateSpin(def, s.stops, true, sticky);
      expect(s.payFifths).toBe(e.payFifths);
      expect(s.stickyMaskAfter).toBe(e.stickyAfter);
      expect(s.retrigger).toBe(e.scatters >= 3);
      sticky = e.stickyAfter;
      if (s.retrigger) {
        const d = Math.min(def.retrigger, def.fsCap - given);
        remaining += d;
        given += d;
      }
      played++;
      add(e.payFifths);
    }
    if (!hit) expect(remaining).toBe(0); // every awarded spin was played unless the cap ended the feature
    expect(given).toBeLessThanOrEqual(def.fsCap);
  } else if (fsAward > 0) expect(hit).toBe(true);
  return { total, capHit: hit, tiers, fsPlayed: played };
}

describe('tape replay (cap at the right point, inside features; jackpots in tape order)', () => {
  const cases: Array<[string, (d: MachineDef) => MachineDef, boolean]> = [
    ['defaults', (d) => d, false],
    ['owned', (d) => d, true],
    ['tiny cap (3× bet)', (d) => ({ ...d, capMultiple: 3 }), false],
    ['tiny cap, owned', (d) => ({ ...d, capMultiple: 3 }), true],
    ['cap 20×', (d) => ({ ...d, capMultiple: 20 }), true],
  ];
  for (const m of MACHINE_IDS)
    for (const [name, mod, owned] of cases)
      it(`${m}: ${name}`, () => {
        const def = mod(defaultMachine(m));
        const rng = fxSlotRng(m.length * 31 + name.length);
        const pools = def.jackpot.seeds.slice();
        let features = 0;
        for (let i = 0; i < 3000; i++) {
          const buy = def.buyPriceFifths > 0 && i % 7 === 0;
          const bet = [5, 10, 50, 100, 500, 5000][i % 6]!;
          // force features: overwrite stops with a trigger now and then is not needed; the default rates suffice
          const tape = drawSpin({ def, bet, buy, owned, pools }, rng);
          const r = replay(def, tape, owned);
          expect(tape.totalFifths).toBe(r.total);
          expect(tape.capHit).toBe(r.capHit);
          expect(tape.totalFifths).toBeLessThanOrEqual(def.capMultiple * 5);
          expect(tape.jackpots.map((j) => j.tier)).toEqual(r.tiers);
          for (const j of tape.jackpots) expect(j.owned).toBe(owned);
          if (tape.hunt) expect(huntOpens(def, tape)).toBeGreaterThan(0);
          if (tape.freeSpins || tape.hunt || tape.hoard || tape.wheel) features++;
          // codec round trip keeps everything settlement depends on
          const back = decodeTape(encodeTape(tape));
          expect(back.totalFifths).toBe(tape.totalFifths);
          expect(back.jackpots).toEqual(tape.jackpots);
          expect(Number.isInteger((tape.totalFifths * bet) / 5)).toBe(true);
        }
        expect(features).toBeGreaterThan(10);
      }, 60_000);
});

describe('forced features at a tiny cap', () => {
  it('Nether at a 4× cap and bought End free spins at a 60× cap replay exactly; the cap ends the feature', () => {
    const nether = { ...defaultMachine('nether'), capMultiple: 4 };
    const end = { ...defaultMachine('end'), capMultiple: 60 };
    let fsCapped = 0;
    for (let seed = 0; seed < 400; seed++) {
      const t = drawSpin({ def: nether, bet: 10, buy: false, owned: true, pools: [] }, fxSlotRng(seed));
      const r = replay(nether, t, true);
      expect(t.totalFifths).toBe(r.total);
      const b = drawSpin({ def: end, bet: 50, buy: true, owned: false, pools: end.jackpot.seeds.slice() }, fxSlotRng(seed));
      const rb = replay(end, b, false);
      expect(b.totalFifths).toBe(rb.total);
      if (b.capHit) {
        fsCapped++;
        // the last spin played is the one that reached the cap
        const before = b.freeSpins!.spins.slice(0, -1).reduce((a, s) => a + s.payFifths, 0);
        expect(before).toBeLessThan(end.capMultiple * 5);
      }
    }
    expect(fsCapped).toBeGreaterThan(0);
  }, 60_000);
});

// ---- tumbles ---------------------------------------------------------------------------------------

describe('tumble termination', () => {
  it('default strips: every sampled chain ends within 9 evaluations and each tumble removes ≥ 3 cells', () => {
    const def = defaultMachine('nether');
    const rng = fxSlotRng(5);
    for (let i = 0; i < 15_000; i++) {
      const stops = def.strips.map((s) => rng.nextInt(s.length));
      for (const ladder of [def.ladder, def.ladderFree]) {
        const c = runTumbles(def, stops, ladder);
        expect(c.steps.length).toBeLessThanOrEqual(9);
        expect(c.steps.at(-1)!.result.payFifths).toBe(0);
        for (const s of c.steps.slice(0, -1)) {
          let n = 0;
          for (let b = s.result.winMask; b; b &= b - 1) n++;
          expect(n).toBeGreaterThanOrEqual(3);
        }
      }
    }
  }, 60_000);

  it('a configured strip set that always wins cannot hang the server (bounded chain, capped total)', () => {
    const strips = ['NW NW NW NW', 'NW NW WD NW', 'NW NW NW NW', 'NW WD NW NW', 'NW NW NW NW'];
    const { config, errors } = readMachineConfig('nether', (k) => (k === 'slots.nether.strips' ? strips : undefined));
    expect(errors).toEqual([]); // accepted by validation — hence the engine bound
    const def = buildMachineDef('nether', config);
    const c = runTumbles(def, [0, 0, 0, 0, 0], def.ladder);
    expect(c.steps.length).toBe(MAX_TUMBLE_STEPS);
    const t = drawSpin({ def, bet: 10, buy: false, owned: true, pools: [] }, fxSlotRng(1));
    expect(t.totalFifths).toBe(def.capMultiple * 5);
    expect(t.capHit).toBe(true);
  });
});

// ---- pools -----------------------------------------------------------------------------------------

describe('pool award conservation (SLOTS.md §5.2)', () => {
  it('per award: award = pool-increment debit + minted seed share, minted within ±1 of seed × r; never negative', () => {
    const rng = fxSlotRng(11);
    for (const m of MACHINE_IDS) {
      const def = defaultMachine(m);
      const ref = def.jackpot.ref;
      for (let i = 0; i < 20_000; i++) {
        const tier = 1 + rng.nextInt(4);
        const seed = def.jackpot.seeds[tier]!;
        const inc = rng.nextInt(1 << 20) * 1000 + rng.nextInt(1000);
        const bet = 5 * (1 + rng.nextInt(Math.min(1 << 20, ref / 5 + 50))); // incl. bets above ref
        const { award, pool } = poolAward(def, seed + inc, tier, bet);
        const r = Math.min(bet, ref) / ref;
        const debit = seed + inc - pool;
        const minted = award - debit;
        expect(award).toBeGreaterThanOrEqual(0);
        expect(pool).toBeGreaterThanOrEqual(seed);
        expect(Math.abs(minted - seed * r)).toBeLessThanOrEqual(1 + 1e-6);
        if (bet >= ref) expect(award).toBe(seed + inc);
      }
    }
  });
});

// ---- buy pricing, reservation ---------------------------------------------------------------------

describe('buy feature pricing (SLOTS.md §6.3)', () => {
  it('Nether 18.4 × bet, End 109 × bet, integer chips at every ladder bet; Overworld has none', () => {
    for (const b of DEFAULT_CONFIG.nether.bets) expect(stakeOf(defaultMachine('nether'), b, true)).toBe(Math.round(18.4 * b));
    for (const b of DEFAULT_CONFIG.end.bets) expect(stakeOf(defaultMachine('end'), b, true)).toBe(109 * b);
    for (const b of DEFAULT_CONFIG.nether.bets) expect(Number.isInteger(stakeOf(defaultMachine('nether'), b, true))).toBe(true);
    expect(() => drawSpin({ def: defaultMachine('overworld'), bet: 10, buy: true, owned: false, pools: [] }, fxSlotRng(1))).toThrow();
  });
});

// ---- validateRtp on out-of-range configs -------------------------------------------------------------

describe('validateRtp flags out-of-range configs', () => {
  const cfg = (m: 'nether' | 'end' | 'overworld', over: Record<string, unknown>) => readMachineConfig(m, (k) => over[k]).config;
  it('Nether: a changed buy price is still validated exactly (not only by the unbuyable sample)', () => {
    const cheap = cfg('nether', { 'slots.nether.buy.price': 10 });
    expect(rtpTablesDefault('nether', cheap)).toBe(true);
    const r = computeRtp(buildMachineDef('nether', cheap), rtpTablesDefault('nether', cheap))!;
    expect(rtpWarnings(r).map((w) => w.kind)).toContain('buy');
    const dear = cfg('nether', { 'slots.nether.buy.price': 25 });
    expect(rtpWarnings(computeRtp(buildMachineDef('nether', dear), true)!).map((w) => w.kind)).toEqual(['buy_parity']);
  });
  it('Nether: a raised contribution raises the total (defaults path adds contributions live) and can trip 0.99', () => {
    const c = cfg('nether', { 'slots.nether.jackpot.contribution': { mini: 0.05, minor: 0.05, major: 0.05, grand: 0.05 } });
    expect(rtpTablesDefault('nether', c)).toBe(true);
    const r = computeRtp(buildMachineDef('nether', c), true)!;
    expect(r.total).toBeCloseTo(0.95394383 - 0.015 + 0.2, 6);
    expect(rtpWarnings(r).some((w) => w.kind === 'rtp')).toBe(true);
  });
  it('Nether: changed game tables are not "default" (sampled instead)', () => {
    expect(rtpTablesDefault('nether', cfg('nether', { 'slots.nether.tumble.ladder': [1, 2, 3, 10] }))).toBe(false);
    expect(rtpTablesDefault('nether', cfg('nether', { 'slots.nether.jackpot.owned': { mini: 10, minor: 30, major: 150, grand: 5000 } }))).toBe(false);
  });
  it('End / Overworld: generous pays, a cheap buy or rich owned jackpots are flagged; defaults are clean', () => {
    for (const m of MACHINE_IDS) expect(rtpWarnings(computeRtp(defaultMachine(m))!)).toEqual([]);
    const rich = cfg('end', { 'slots.end.pays': { ...DEFAULT_CONFIG.end.pays, dragon_head: [10, 40, 150] } });
    expect(rtpWarnings(computeRtp(buildMachineDef('end', rich))!).length).toBeGreaterThan(0);
    const owned = cfg('overworld', { 'slots.overworld.jackpot.owned': { mini: 10, minor: 25, major: 100, grand: 100_000 } });
    expect(rtpWarnings(computeRtp(buildMachineDef('overworld', owned))!).map((w) => w.kind)).toContain('rtp');
    const cheap = cfg('end', { 'slots.end.buy.price': 90 });
    expect(rtpWarnings(computeRtp(buildMachineDef('end', cheap))!).map((w) => w.kind)).toEqual(['buy']);
  });
});

describe('config: string-valued keys are accepted when valid', () => {
  it('strips and wheel rings can be configured (they were rejected as a whole before)', () => {
    const strips = DEFAULT_CONFIG.end.strips.slice();
    const outer = ['10', 'UP', '12', '15', 'MINI', '10', '20', '12'];
    const { config, errors } = readMachineConfig('end', (k) => (k === 'slots.end.strips' ? strips : k === 'slots.end.wheel.outer' ? outer : undefined));
    expect(errors).toEqual([]);
    expect(config.wheel!.outer).toEqual(outer);
    const badWheel = readMachineConfig('end', (k) => (k === 'slots.end.wheel.core' ? ['150', 'UP', '250', '500'] : undefined));
    expect(badWheel.errors).toEqual(['slots.end.wheel.core: bad wheel token UP']);
    const badStrip = readMachineConfig('end', (k) => (k === 'slots.end.strips' ? ['WD ES ES', ...strips.slice(1)] : undefined));
    expect(badStrip.errors[0]).toMatch(/wild only on reels 2–4/);
  });
});

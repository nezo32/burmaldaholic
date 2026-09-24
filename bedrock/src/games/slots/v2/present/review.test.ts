/**
 * Adversarial review tests for lane B-L9 (slots v2 form presentation): an independent SLOTS.md §10.3 oracle
 * for anticipation, stop order and cadence, no early spoilers (F6), Returned presentation (F9), anticipation
 * blinks only the cells that caused it, and the server tier is never exceeded by the escalating word.
 */
import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { FxRng } from '../../../../core/logic/anim/seed';
import { SHARED_PROFILE, type TimingProfile, beatEnd } from '../../../../core/logic/anim/timeline';
import { tierOrdinal } from '../../../../core/logic/anim/win-tier';
import type { Raw } from '../../../../core/logic/rawtext';
import { SLOT_BEAT } from '../logic/timeline';
import { type MachineId, REELS, ROWS } from '../logic/types';
import { cuesBetween, rollupFrom, screenAt, terminalScreen, waysRaw } from './features';
import { TURBO, fakeDef, fakeRound, fakeTape, randomStops, timelineOf } from './fixtures.test-util';
import { PLANE_WIN, TINT_DIM, type SlotRound, cellIndex, reelFrame, terminalFrame } from './frames';

const MACHINES: MachineId[] = ['overworld', 'nether', 'end'];
const json = (x: unknown): string => JSON.stringify(x ?? null);

function keysOf(r: Raw | undefined): string[] {
  if (!r) return [];
  const out: string[] = [];
  const walk = (x: Raw): void => {
    if (x.translate) out.push(x.translate);
    for (const c of x.rawtext ?? []) walk(c);
    const w = x.with;
    if (w && !Array.isArray(w)) walk(w);
  };
  walk(r);
  return out;
}

/** Independent SLOTS.md §10.3 oracle: the set of reels that anticipate. */
function oracle(round: SlotRound): Set<number> {
  const w = round.base!.landed;
  const role = (r: number, y: number) => round.roles[w[cellIndex(r, y)]!];
  const count = (k: number, want: string) => {
    let n = 0;
    for (let r = 0; r < k; r++) for (let y = 0; y < ROWS; y++) if (role(r, y) === want) n++;
    return n;
  };
  const has = (r: number, want: string) => [0, 1, 2].some((y) => role(r, y) === want);
  const stripHas = (r: number, want: string) => round.strips[r]!.some((s) => round.roles[s] === want);
  // most coins a window of reel r can show (from its real strip)
  const maxCoins = (r: number): number => {
    const st = round.strips[r]!;
    let best = 0;
    for (let p = 0; p < st.length; p++) best = Math.max(best, [0, 1, 2].filter((y) => round.roles[st[(p + y) % st.length]!] === 'COIN').length);
    return best;
  };
  // reel k (0-based) is anticipated iff the condition holds on the reels already stopped (0…k−1), SLOTS.md §10.3
  // test (b); the End crystal rule only concerns reel 4 (the last bonus reel), Overworld's reels 4–5
  const out = new Set<number>();
  for (let k = 1; k < REELS; k++) {
    const laterScatter = [...Array(REELS - k).keys()].some((i) => stripHas(k + i, 'SCATTER'));
    let coinsLater = 0;
    for (let r = k; r < REELS; r++) coinsLater += maxCoins(r);
    const cond =
      (count(k, 'SCATTER') >= 2 && laterScatter) ||
      (round.machine === 'overworld' && k >= 3 && k < 5 && has(0, 'BONUS') && has(2, 'BONUS')) ||
      (round.machine === 'end' && k === 3 && has(1, 'BONUS') && has(2, 'BONUS')) ||
      (round.machine === 'nether' && count(k, 'COIN') >= 4 && count(k, 'COIN') + coinsLater >= 6);
    if (cond) out.add(k);
  }
  return out;
}

describe('review: anticipation is honest and the reels stop left to right', () => {
  it('ANTICIPATE lanes == the §10.3 oracle; stops strictly left→right, gaps 150 / 1000 ms (× turbo)', () => {
    for (const m of MACHINES) {
      const def = fakeDef(m);
      const rng = new FxRng(9001);
      let hits = 0;
      for (let i = 0; i < 400; i++) {
        const round = fakeRound(def, fakeTape(def, randomStops(rng)));
        for (const [shared, k] of [
          [SHARED_PROFILE, 1],
          [TURBO, 0.5],
        ] as Array<[TimingProfile, number]>) {
          const tl = timelineOf(round, shared, shared);
          const lanes = new Set(tl.beats.filter((b) => b.kind === SLOT_BEAT.ANTICIPATE).map((b) => b.lane));
          expect([...lanes].sort()).toEqual([...oracle(round)].sort());
          if (lanes.size) hits++;
          const ends = tl.beats.filter((b) => b.kind === SLOT_BEAT.REEL_LAND).map((b) => ({ lane: b.lane, end: beatEnd(b) }));
          expect(ends.map((e) => e.lane)).toEqual([0, 1, 2, 3, 4]);
          for (let r = 1; r < REELS; r++) {
            const gap = ends[r]!.end - ends[r - 1]!.end;
            const want = (lanes.has(r) ? 1000 : 150) * k;
            expect(Math.abs(gap - want)).toBeLessThanOrEqual(50);
          }
        }
      }
      expect(hits).toBeGreaterThan(0);
    }
  });

  it('while reels anticipate, only the cells that caused it blink (Nether coins, never unrelated scatters)', () => {
    const def = fakeDef('nether');
    const rng = new FxRng(77);
    let checked = 0;
    for (let i = 0; i < 4000 && checked < 20; i++) {
      const round = fakeRound(def, fakeTape(def, randomStops(rng)));
      const tl = timelineOf(round);
      const a = tl.beats.find((b) => b.kind === SLOT_BEAT.ANTICIPATE);
      if (!a) continue;
      const stopped = a.lane;
      const w = round.base!.landed;
      let scat = 0;
      for (let c = 0; c < stopped * ROWS; c++) if (round.roles[w[c]!] === 'SCATTER') scat++;
      if (scat >= 2) continue; // want coin-only anticipations
      checked++;
      // sample both blink phases
      const lit = new Set<number>();
      for (let t = a.at + 1; t < beatEnd(a); t += 50) {
        reelFrame(round, tl, t).cells.forEach((c, idx) => {
          if (Math.floor(idx / ROWS) < stopped && c.plane === PLANE_WIN && t - a.at > 250) lit.add(idx);
        });
      }
      for (const idx of lit) expect(round.roles[w[idx]!]).toBe('COIN');
      expect(lit.size).toBeGreaterThan(0);
    }
    expect(checked).toBeGreaterThan(0);
  });
});

describe('review: no early spoilers (F6)', () => {
  it('no amount, tier word, win sound or glow before the last base reel lands', () => {
    for (const m of MACHINES) {
      const def = fakeDef(m);
      const rng = new FxRng(123);
      for (let i = 0; i < 150; i++) {
        const round = fakeRound(def, fakeTape(def, randomStops(rng)));
        const tl = timelineOf(round);
        const last = Math.max(...tl.beats.filter((b) => b.kind === SLOT_BEAT.REEL_LAND).map(beatEnd));
        for (let t = 0; t < last; t += 50) {
          const s = screenAt(round, tl, t);
          expect(s.header).toBeUndefined();
          expect(s.status).toBeUndefined();
        }
        const ids = cuesBetween(round, tl, -1, last - 1)
          .filter((c) => c.kind === 'sound')
          .map((c) => (c as { id: string }).id);
        for (const bad of ['slots.win_small', 'slots.rollup_tick', 'slots.win_nice', 'slots.big_win', 'slots.returned']) expect(ids).not.toContain(bad);
      }
    }
  });
});

describe('review: Returned (F9) and tier words', () => {
  it('a return below the bet never shows win colours, dim, glow or win sounds', () => {
    for (const m of MACHINES) {
      const def = fakeDef(m);
      const rng = new FxRng(31);
      let n = 0;
      for (let i = 0; i < 3000 && n < 15; i++) {
        const round = fakeRound(def, fakeTape(def, randomStops(rng)));
        if (!(round.totalChips > 0 && round.totalChips < round.bet)) continue;
        n++;
        const tl = timelineOf(round);
        expect(tl.beats.some((b) => b.kind === SLOT_BEAT.WIN_SHOW || b.kind === SLOT_BEAT.WAY_CYCLE)).toBe(false);
        const last = Math.max(...tl.beats.filter((b) => b.kind === SLOT_BEAT.REEL_LAND).map(beatEnd));
        for (let t = last + 250; t <= tl.endMs(); t += 50) {
          const f = reelFrame(round, tl, t);
          expect(f.cells.some((c) => c.plane === PLANE_WIN || c.tint === TINT_DIM)).toBe(false);
          const s = screenAt(round, tl, t);
          expect(json(s.status)).not.toContain('"gui.burmaldaholic.slots.win"');
          expect(json(s.status)).not.toContain('§a');
        }
        expect(terminalFrame(round).cells.some((c) => c.plane === PLANE_WIN)).toBe(false);
        expect(keysOf(terminalScreen(round).status)).toContain('gui.burmaldaholic.slots.returned');
        const ids = cuesBetween(round, tl, -1, tl.endMs())
          .filter((c) => c.kind === 'sound')
          .map((c) => (c as { id: string }).id);
        expect(ids).not.toContain('slots.win_small');
      }
      expect(n).toBeGreaterThan(0);
    }
  });

  it('the escalating word never exceeds the server tier, and the final word is the server tier', () => {
    const def = fakeDef('overworld');
    // 70× the bet would be MEGA by the table, but the server says BIG: the client must not re-derive it
    const round = fakeRound(def, fakeTape(def, [0, 0, 0, 0, 0], { totalFifths: 5 * 70 }), 'BIG');
    const tl = timelineOf(round);
    let maxOrd = -1;
    for (let t = 0; t <= tl.endMs() + 100; t += 50) {
      const ks = keysOf(screenAt(round, tl, t).header).filter((k) => k.includes('.tier.'));
      for (const k of ks) {
        const tier = k.split('.').pop()!.toUpperCase() as 'NICE' | 'BIG' | 'MEGA' | 'EPIC';
        expect(tierOrdinal(tier)).toBeLessThanOrEqual(tierOrdinal('BIG'));
        expect(tierOrdinal(tier)).toBeGreaterThanOrEqual(maxOrd); // escalates, never drops back
        maxOrd = tierOrdinal(tier);
      }
    }
    expect(keysOf(terminalScreen(round).header)).toContain('gui.burmaldaholic.slots.tier.big');
  });
});

// ---------------------------------------------------------------------------------------------------------
// Coordinator decisions (1), (3), (5) — pure parts (2) and (4) are in celebrate/ddui tests
// ---------------------------------------------------------------------------------------------------------

/** Chip amount of a `slots.win` status line (plural unit arg), else undefined. */
function winAmount(r: Raw | undefined): number | undefined {
  let node: Raw | undefined;
  const walk = (x: Raw): void => {
    if (node) return;
    if (x.translate === 'gui.burmaldaholic.slots.win') node = x;
    for (const c of x.rawtext ?? []) walk(c);
  };
  if (r) walk(r);
  if (!node) return undefined;
  const s = JSON.stringify(node.with ?? null).replace(/"translate":"[^"]*"/g, '');
  return Number(s.replace(/\D/g, ''));
}

/** A base-only win of at least `minX` × the bet (pays scaled up so big wins exist). */
function bigBaseWin(m: MachineId, minX: number, scale = 12) {
  const def = fakeDef(m);
  def.paysFifths = def.paysFifths.map((p) => p.map((x) => x * scale));
  const rng = new FxRng(2024);
  for (let i = 0; i < 20000; i++) {
    const round = fakeRound(def, fakeTape(def, randomStops(rng)));
    if (round.free.length === 0 && round.totalChips >= minX * round.bet && round.totalChips < 1000 * round.bet) return { def, round };
  }
  throw new Error('no fixture');
}

describe('decision (1): the Win line never resets when the roll-up starts (F5 continuity)', () => {
  it('Win line is monotonic from the first win show to the end, for every machine, normal and turbo', () => {
    for (const m of MACHINES) {
      const def = fakeDef(m);
      const rng = new FxRng(55);
      let n = 0;
      for (let i = 0; i < 2000 && n < 25; i++) {
        const round = fakeRound(def, fakeTape(def, randomStops(rng)));
        if (round.totalChips < round.bet) continue;
        n++;
        for (const prof of [SHARED_PROFILE, TURBO]) {
          const tl = timelineOf(round, prof, prof);
          let last = -1;
          for (let t = 0; t <= tl.endMs() + 50; t += 50) {
            const v = winAmount(screenAt(round, tl, t).status);
            if (v === undefined) continue;
            expect(v).toBeGreaterThanOrEqual(last);
            last = v;
          }
          expect(last).toBe(round.totalChips);
        }
      }
      expect(n).toBeGreaterThan(0);
    }
  });

  it('win show already at the total: the roll-up does not recount (no ticks) but the word still escalates', () => {
    const { round } = bigBaseWin('overworld', 15);
    const tl = timelineOf(round);
    const roll = tl.beats.find((b) => b.kind === SLOT_BEAT.ROLLUP)!;
    expect(rollupFrom(round, tl, roll)).toBe(round.totalChips);
    const words: string[] = [];
    for (let t = roll.at; t <= beatEnd(roll); t += 50) {
      const s = screenAt(round, tl, t);
      expect(winAmount(s.status)).toBe(round.totalChips);
      const w = keysOf(s.header).find((k) => k.includes('.tier.'));
      if (w && words[words.length - 1] !== w) words.push(w);
    }
    expect(words[0]).toBe('gui.burmaldaholic.slots.tier.nice');
    expect(words[words.length - 1]).toBe(`gui.burmaldaholic.slots.tier.${round.tier.toLowerCase()}`);
    expect(words.length).toBeGreaterThanOrEqual(2);
    const ids = cuesBetween(round, tl, roll.at - 1, beatEnd(roll))
      .filter((c) => c.kind === 'sound')
      .map((c) => (c as { id: string }).id);
    expect(ids).not.toContain('slots.rollup_tick');
    expect(ids).toContain('slots.win_nice');
    expect(ids).toContain('slots.big_win');
  });

  it('with features the roll-up continues from the base Win line, not from 0', () => {
    const def = fakeDef('overworld');
    const rng = new FxRng(8);
    for (let i = 0; i < 5000; i++) {
      const stops = randomStops(rng);
      const base = fakeRound(def, fakeTape(def, stops));
      if (base.totalChips < base.bet) continue;
      const round = fakeRound(def, fakeTape(def, stops, { free: [{ stops: [1, 2, 3, 4, 5] }, { stops: [6, 7, 8, 9, 10] }], totalFifths: base.tape.totalFifths + 500 }));
      const tl = timelineOf(round);
      const roll = tl.beats.find((b) => b.kind === SLOT_BEAT.ROLLUP)!;
      expect(winAmount(screenAt(round, tl, roll.at).status)).toBeGreaterThanOrEqual(base.totalChips);
      return;
    }
    throw new Error('no fixture');
  });
});

describe('decision (3): ways use the plural helper', () => {
  const read = (f: string): Map<string, string> => {
    const m = new Map<string, string>();
    for (const l of fs.readFileSync(path.join(__dirname, '../../../../../lang/slots', f), 'utf8').split('\n')) {
      const i = l.indexOf('=');
      if (i > 0 && !l.startsWith('#')) m.set(l.slice(0, i), l.slice(i + 1));
    }
    return m;
  };

  it('"1 way" / «1 способ»; RU p21/p2/p5 forms read right', () => {
    const en = read('en_US.lang');
    const ru = read('ru_RU.lang');
    const render = (lang: Map<string, string>, n: number): string => lang.get(keysOf(waysRaw(n))[0]!)!.replace('%1', String(n));
    expect(render(en, 1)).toBe('1 way');
    expect(render(en, 243)).toBe('243 ways');
    expect(render(ru, 1)).toBe('1 способ');
    expect(render(ru, 21)).toBe('21 способ');
    expect(render(ru, 3)).toBe('3 способа');
    expect(render(ru, 11)).toBe('11 способов');
    expect(render(ru, 243)).toBe('243 способа');
    for (const lang of [en, ru]) expect(lang.get('gui.burmaldaholic.slots.symbol_win')).toBe('%1 ×%2 · %3 · %4');
  });

  it('the way-cycle status passes the plural ways as %3', () => {
    const { round } = bigBaseWin('overworld', 1, 1);
    const tl = timelineOf(round);
    // the cycle runs next to the roll-up (local beats): its line takes the status once the amount has settled
    const roll = tl.beats.find((b) => b.kind === SLOT_BEAT.ROLLUP)!;
    const at = beatEnd(roll) + 10;
    expect(tl.beats.some((b) => b.kind === SLOT_BEAT.WAY_CYCLE && b.at <= at && at < beatEnd(b))).toBe(true);
    const ks = keysOf(screenAt(round, tl, at).status);
    expect(ks[0]).toBe('gui.burmaldaholic.slots.symbol_win');
    expect(ks.some((k) => /^gui\.burmaldaholic\.slots\.ways\.p(1|21|2|5)$/.test(k))).toBe(true);
  });
});

describe('decision (5): jackpots after the spin roll-up (slots.md §2.5)', () => {
  it('builder order is ROLLUP then JACKPOT; the jackpot screen shows during its beat; the end is terminal', () => {
    const def = fakeDef('end');
    const round = fakeRound(def, fakeTape(def, [1, 2, 3, 4, 5], { jackpots: [{ tier: 2, chips: 3000, owned: true }], totalFifths: 40 }));
    const tl = timelineOf(round);
    const kinds = tl.beats.filter((b) => b.kind === SLOT_BEAT.ROLLUP || b.kind === SLOT_BEAT.JACKPOT).map((b) => b.kind);
    expect(kinds).toEqual([SLOT_BEAT.ROLLUP, SLOT_BEAT.JACKPOT]);
    const jp = tl.beats.find((b) => b.kind === SLOT_BEAT.JACKPOT)!;
    expect(keysOf(screenAt(round, tl, jp.at + 10).header)).toContain('gui.burmaldaholic.slots.jackpot.won');
    expect(json(screenAt(round, tl, tl.endMs()))).toBe(json(terminalScreen(round)));
  });
});

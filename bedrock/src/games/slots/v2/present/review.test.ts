/**
 * Adversarial review tests for lane B-L9 (slots v2 form presentation): an independent SLOTS.md §10.3 oracle
 * for anticipation, stop order and cadence, no early spoilers (F6), Returned presentation (F9), anticipation
 * blinks only the cells that caused it, and the server tier is never exceeded by the escalating word.
 */
import { describe, expect, it } from 'vitest';
import { FxRng } from '../../../../core/logic/anim/seed';
import { SHARED_PROFILE, type TimingProfile, beatEnd } from '../../../../core/logic/anim/timeline';
import { tierOrdinal } from '../../../../core/logic/anim/win-tier';
import type { Raw } from '../../../../core/logic/rawtext';
import { SLOT_BEAT } from '../logic/timeline';
import { type MachineId, REELS, ROWS } from '../logic/types';
import { cuesBetween, screenAt, terminalScreen } from './features';
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
  for (let k = 1; k < REELS; k++) {
    const laterScatter = [...Array(REELS - k).keys()].some((i) => stripHas(k + i, 'SCATTER'));
    const cond =
      (count(k, 'SCATTER') >= 2 && laterScatter) ||
      (round.machine === 'overworld' && k >= 3 && k < 5 && has(0, 'BONUS') && has(2, 'BONUS')) ||
      (round.machine === 'end' && k === 3 && has(1, 'BONUS') && has(2, 'BONUS')) ||
      (round.machine === 'nether' && count(k, 'COIN') >= 4 && count(k, 'COIN') + 3 * (REELS - k) >= 6);
    if (cond) return new Set([...Array(REELS - k).keys()].map((i) => k + i));
  }
  return new Set();
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

import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { FxRng } from '../../../../core/logic/anim/seed';
import { SHARED_PROFILE, beatEnd } from '../../../../core/logic/anim/timeline';
import type { Raw } from '../../../../core/logic/rawtext';
import { SLOT_BEAT } from '../logic/timeline';
import { REDUCED, TURBO, fakeDef, fakeRound, fakeTape, randomStops, timelineOf } from './fixtures.test-util';
import { DEFAULT_FRAME_OPTIONS } from './frames';
import { type Cue, WHEEL_RINGS, cuesBetween, hoardState, huntOver, huntScreen, screenAt, terminalScreen, wheelCenter, wheelState } from './features';

const REDUCED_OPTS = { reduceMotion: true, flashes: false, tinting: true };

/** All translate keys of a Raw (depth first). */
export function keysOf(r: Raw | undefined): string[] {
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
/** Literal text of a Raw (numbers included). */
const textOf = (r: Raw | undefined): string => {
  if (!r) return '';
  let s = r.text ?? '';
  for (const c of r.rawtext ?? []) s += textOf(c);
  const w = r.with;
  if (w && !Array.isArray(w)) s += textOf(w);
  return s;
};
const json = (x: unknown): string => JSON.stringify(x);

const hoardTape = () => ({
  initialCells: [0, 3, 7, 9, 12, 14],
  initialValues: [1, 2, 5, 1, 10, -1],
  respinCells: [[], [4], [], [], []],
  respinValues: [[], [3], [], [], []],
});

describe('slot screen: fidelity', () => {
  it('screen at the end == terminal screen for plain spins, features, jackpots and max win', () => {
    const rng = new FxRng(9);
    for (const m of ['overworld', 'nether', 'end'] as const) {
      const def = fakeDef(m);
      for (let i = 0; i < 25; i++) {
        const extras =
          i % 5 === 0
            ? { free: [{ stops: randomStops(rng) }, { stops: randomStops(rng), retrigger: true }, { stops: randomStops(rng) }] }
            : i % 5 === 1 && m === 'nether'
              ? { hoard: hoardTape() }
              : i % 5 === 2 && m === 'end'
                ? { wheel: [1, 7, 4] }
                : i % 5 === 3
                  ? { jackpots: [{ tier: 2 as const, chips: 1500, owned: false }], capHit: true }
                  : {};
        const round = fakeRound(def, fakeTape(def, randomStops(rng), extras));
        for (const [sh, lo] of [
          [SHARED_PROFILE, SHARED_PROFILE],
          [TURBO, TURBO],
          [SHARED_PROFILE, REDUCED],
        ] as const) {
          const tl = timelineOf(round, sh, lo);
          for (const o of [DEFAULT_FRAME_OPTIONS, REDUCED_OPTS]) expect(json(screenAt(round, tl, tl.endMs(), o))).toBe(json(terminalScreen(round, o)));
        }
      }
    }
  });

  it('roll-up is monotonic, ends exact, and the tier word upgrades Nice → … → the server tier only', () => {
    const def = fakeDef('overworld');
    const tape = fakeTape(def, [0, 0, 0, 0, 0], { totalFifths: 5 * 130 }, 50); // 130× the bet: EPIC
    const round = fakeRound(def, tape, 'EPIC');
    const tl = timelineOf(round);
    const roll = tl.beats.find((b) => b.kind === SLOT_BEAT.ROLLUP)!;
    let last = -1;
    const words: string[] = [];
    for (let t = roll.at; t <= beatEnd(roll); t += 100) {
      const s = screenAt(round, tl, t);
      const v = Number(textOf(s.status).replace(/\D/g, '') || '0');
      expect(v).toBeGreaterThanOrEqual(last);
      last = v;
      const w = keysOf(s.header).find((k) => k.includes('.tier.'));
      if (w && words[words.length - 1] !== w) words.push(w);
    }
    expect(words).toEqual(['nice', 'big', 'mega', 'epic'].map((x) => `gui.burmaldaholic.slots.tier.${x}`));
    const end = screenAt(round, tl, tl.endMs());
    expect(keysOf(end.header)).toContain('gui.burmaldaholic.slots.tier.epic');
    expect(Number(textOf(end.status).replace(/§./g, '').replace(/\D/g, ''))).toBe(round.totalChips);
  });

  it('a return below the bet is "Returned": no tier word, muted cue, no stems (F9)', () => {
    const def = fakeDef('overworld');
    const round = fakeRound(def, fakeTape(def, [0, 0, 0, 0, 0], { totalFifths: 2 }, 50), 'RETURN');
    const tl = timelineOf(round);
    const s = terminalScreen(round);
    expect(s.header).toBeUndefined();
    expect(keysOf(s.status)).toContain('gui.burmaldaholic.slots.returned');
    const cues = cuesBetween(round, tl, -1, tl.endMs());
    const ids = cues.filter((c): c is Extract<Cue, { kind: 'sound' }> => c.kind === 'sound').map((c) => c.id);
    expect(ids).toContain('slots.returned');
    for (const bad of ['slots.win_nice', 'slots.big_win', 'slots.mega_win', 'slots.epic_win', 'slots.rollup_end']) expect(ids).not.toContain(bad);
  });
});

describe('slot features: honesty', () => {
  it('Hoard: a respin coin appears only when its respin lands; empties never show a coin while spinning (D5)', () => {
    const def = fakeDef('nether');
    const round = fakeRound(def, fakeTape(def, [1, 2, 3, 4, 5], { hoard: hoardTape() }));
    const tl = timelineOf(round);
    const respins = tl.beats.filter((b) => b.kind === SLOT_BEAT.HOARD_RESPIN);
    expect(respins.length).toBe(5);
    const r1 = respins[1]!;
    expect(hoardState(round, tl, r1.at + 10).cells.has(4)).toBe(false);
    expect(hoardState(round, tl, r1.at + 10).spinning.has(4)).toBe(true);
    expect(hoardState(round, tl, beatEnd(r1)).cells.get(4)?.value).toBe(3);
    // respins: 3 → 2 → reset 3 → 2 → 1 → 0
    const left = respins.map((b) => hoardState(round, tl, beatEnd(b)).respinsLeft);
    expect(left).toEqual([2, 3, 2, 1, 0]);
    // screen rows during the ember phase contain no coin glyph for empty cells: only 6 coins visible
    const coin = String.fromCodePoint(0xe212);
    const rows = textOf(screenAt(round, tl, r1.at + 100).rows);
    expect(rows.split(coin).length - 1).toBe(5); // 5 coins + 1 jackpot badge
  });

  it('Dragon Wheel lands on the tape wedge of each ring; reduce motion does not spoil the result', () => {
    const def = fakeDef('end');
    for (const segs of [[0], [1, 7], [1, 7, 4], [19]]) {
      const round = fakeRound(def, fakeTape(def, [1, 2, 3, 4, 5], { wheel: segs }));
      const tl = timelineOf(round);
      const spins = tl.beats.filter((b) => b.kind === SLOT_BEAT.WHEEL_SPIN);
      spins.forEach((b, ring) => {
        expect(wheelState(round, tl, beatEnd(b) - 1, DEFAULT_FRAME_OPTIONS).center).toBe(segs[ring]);
        const reduced = wheelState(round, tl, b.at + 100, REDUCED_OPTS).center;
        expect(reduced).not.toBe(segs[ring]);
      });
    }
    for (let ring = 0; ring < 3; ring++) {
      const n = WHEEL_RINGS[ring]!.length;
      for (let f = 0; f < n; f++) expect(wheelCenter(ring, f, 1, false)).toBe(f);
    }
  });

  it('Treasure Hunt: the i-th pick reveals entry i; later entries never influence the board (F7)', () => {
    const def = fakeDef('overworld');
    const a = fakeRound(def, fakeTape(def, [1, 2, 3, 4, 5], { hunt: [5, 2, 0, 25, 10, 1] }));
    const b = fakeRound(def, fakeTape(def, [1, 2, 3, 4, 5], { hunt: [5, 2, 3, -4, 10, 1] }));
    for (const opened of [0, 1, 2]) {
      expect(json(huntScreen(a, { opened }))).toBe(json(huntScreen(b, { opened })));
      for (const ms of [0, 150]) expect(json(huntScreen(a, { opened, opening: { i: opened, ms } }))).toBe(json(huntScreen(b, { opened, opening: { i: opened, ms } })));
    }
    // the chest rattles (win plane, closed) before the lid opens
    expect(textOf(huntScreen(a, { opened: 0, opening: { i: 0, ms: 50 } }).rows)).toContain(String.fromCodePoint(0xe334));
    expect(keysOf(huntScreen(a, { opened: 3 }).header)).toContain('gui.burmaldaholic.slots.pick.creeper');
    expect(huntOver(a, 3)).toBe(true);
    expect(huntOver(b, 3)).toBe(false);
  });
});

describe('slot features: free spins', () => {
  it('intro banner, counters (spin i of n, multiplier / sticky), retrigger banner, outro total', () => {
    const def = fakeDef('end');
    const tape = fakeTape(def, [1, 2, 3, 4, 5], { free: [{ stops: [2, 5, 9, 12, 7], stickyMaskAfter: 1 }, { stops: [4, 8, 1, 3, 11], stickyMaskAfter: 1, retrigger: true }, { stops: [0, 0, 0, 0, 0], stickyMaskAfter: 1 }] });
    const round = fakeRound(def, tape);
    const tl = timelineOf(round);
    const intro = tl.beats.find((b) => b.kind === SLOT_BEAT.FS_INTRO)!;
    expect(keysOf(screenAt(round, tl, intro.at + 10).header)).toEqual(['gui.burmaldaholic.slots.fs.title', 'gui.burmaldaholic.slots.fs.name.end']);
    const spins = tl.beats.filter((b) => b.kind === SLOT_BEAT.FS_SPIN);
    const h1 = screenAt(round, tl, spins[1]!.at + 10);
    expect(keysOf(h1.header)).toEqual(['gui.burmaldaholic.slots.fs.left', 'gui.burmaldaholic.slots.sticky']);
    expect(textOf(h1.header)).toContain('2');
    const re = tl.beats.find((b) => b.kind === SLOT_BEAT.FS_RETRIGGER)!;
    expect(keysOf(screenAt(round, tl, re.at + 10).header)).toEqual(['gui.burmaldaholic.slots.fs.retrigger']);
    const outro = tl.beats.find((b) => b.kind === SLOT_BEAT.FS_OUTRO)!;
    expect(keysOf(screenAt(round, tl, beatEnd(outro) - 10).header)[0]).toBe('gui.burmaldaholic.slots.fs.end');
    const cues = cuesBetween(round, tl, -1, tl.endMs());
    expect(cues.filter((c) => c.kind === 'music').map((c) => (c as { on: boolean }).on)).toEqual([true, false]);
  });
});

describe('slot cues', () => {
  it('reel stops walk the pentatonic ladder; anticipation start/stop pair up; roll-up ticks ≤ 15/s', () => {
    const def = fakeDef('overworld');
    const rng = new FxRng(21);
    for (let i = 0; i < 50; i++) {
      const round = fakeRound(def, fakeTape(def, randomStops(rng)));
      const tl = timelineOf(round);
      // frame-by-frame, like the runtime
      const all: Array<Cue & { at: number }> = [];
      let prev = -1;
      for (let t = 0; t <= tl.endMs() + 100; t += 100) {
        for (const c of cuesBetween(round, tl, prev, t)) all.push({ ...c, at: t });
        prev = t;
      }
      const stops = all.filter((c) => c.kind === 'sound' && c.id === 'slots.reel_stop') as Array<{ pitch: number }>;
      expect(stops.map((c) => c.pitch)).toEqual([1.0, 1.12, 1.26, 1.5, 1.68]);
      const starts = all.filter((c) => c.kind === 'sound' && c.id === 'slots.anticipation').length;
      const ends = all.filter((c) => c.kind === 'stop' && c.id === 'slots.anticipation').length;
      expect(starts).toBe(ends);
      const ticks = all.filter((c) => c.kind === 'sound' && c.id === 'slots.rollup_tick');
      for (let k = 15; k < ticks.length; k++) expect(ticks[k]!.at - ticks[k - 15]!.at).toBeGreaterThanOrEqual(1000);
      expect(all.filter((c) => c.kind === 'loop' && c.id === 'slots.spin_loop').map((c) => (c as { on: boolean }).on)).toEqual([true, false]);
    }
  });
});

describe('slot strings (RU fit, TEMP-ANIM keys present)', () => {
  const lang = (l: string): Map<string, string> => {
    const m = new Map<string, string>();
    for (const dir of ['slots', 'core']) {
      const file = path.resolve(__dirname, `../../../../../lang/${dir}/${l}.lang`);
      for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
        const i = line.indexOf('=');
        if (i > 0 && !line.startsWith('#')) m.set(line.slice(0, i), line.slice(i + 1));
      }
    }
    return m;
  };
  it('every key the screens use exists in EN and RU; header/status lines fit 36 RU characters', () => {
    const en = lang('en_US');
    const ru = lang('ru_RU');
    const def = fakeDef('nether');
    const rng = new FxRng(4);
    const used = new Set<string>();
    for (let i = 0; i < 20; i++) {
      const round = fakeRound(def, fakeTape(def, randomStops(rng), i % 2 ? { hoard: hoardTape(), jackpots: [{ tier: 1, chips: 500, owned: false }] } : { free: [{ stops: randomStops(rng) }, { stops: randomStops(rng), retrigger: true }] }));
      const tl = timelineOf(round);
      for (let t = 0; t <= tl.endMs(); t += 100) {
        const s = screenAt(round, tl, t);
        for (const k of [...keysOf(s.header), ...keysOf(s.status), ...keysOf(s.rows)]) used.add(k);
      }
    }
    const endDef = fakeDef('end');
    const wr = fakeRound(endDef, fakeTape(endDef, [1, 2, 3, 4, 5], { wheel: [1, 7, 4] }));
    const wtl = timelineOf(wr);
    for (let t = 0; t <= wtl.endMs(); t += 100) for (const k of keysOf(screenAt(wr, wtl, t).rows)) used.add(k);
    const ow = fakeDef('overworld');
    const hr = fakeRound(ow, fakeTape(ow, [1, 2, 3, 4, 5], { hunt: [5, 0] }));
    for (const v of [{ opened: 0 }, { opened: 1 }, { opened: 2 }]) for (const k of [...keysOf(huntScreen(hr, v).header), ...keysOf(huntScreen(hr, v).status)]) used.add(k);
    expect(used.size).toBeGreaterThan(10);
    for (const k of used) {
      if (k.startsWith('unit.')) continue;
      expect(en.has(k), `missing EN ${k}`).toBe(true);
      expect(ru.has(k), `missing RU ${k}`).toBe(true);
      const v = ru.get(k)!.replace(/%\d/g, '99 999');
      if (!k.includes('symbol_win') && !k.includes('.hint')) expect(v.length, `${k}: ${v}`).toBeLessThanOrEqual(36);
    }
  });
});

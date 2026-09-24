import { describe, expect, it } from 'vitest';
import { FxRng } from '../../../../core/logic/anim/seed';
import { SHARED_PROFILE, beatEnd } from '../../../../core/logic/anim/timeline';
import { stopTimes } from '../logic/anticipation';
import { SLOT_BEAT } from '../logic/timeline';
import { type MachineId, REELS, ROWS, windowFromStops } from '../logic/types';
import { REDUCED, TURBO, fakeDef, fakeRound, fakeTape, randomStops, refTumbles, timelineOf } from './fixtures.test-util';
import {
  DEFAULT_FRAME_OPTIONS,
  PLANE_BASE,
  PLANE_BLUR,
  PLANE_WIN,
  SLOT_FRAME_MODEL,
  SLOT_GLYPH,
  TINT_ANTIC,
  TINT_DIM,
  TINT_NONE,
  cellIndex,
  onPlane,
  reelFrame,
  renderRows,
  sameFrame,
  terminalFrame,
} from './frames';

const MACHINES: MachineId[] = ['overworld', 'nether', 'end'];
const REDUCED_OPTS = { reduceMotion: true, flashes: false, tinting: true };
const NO_TINT = { reduceMotion: false, flashes: true, tinting: false };

function landTimes(tl: ReturnType<typeof timelineOf>): number[] {
  const out: number[] = [];
  for (const b of tl.beats) if (b.kind === SLOT_BEAT.REEL_LAND && out[b.lane] === undefined) out[b.lane] = beatEnd(b);
  return out;
}

describe('slot frames: fidelity (F2, F8)', () => {
  it('final frame == terminal == the paid window, for 60 tapes per machine × profiles × options', () => {
    for (const m of MACHINES) {
      const def = fakeDef(m);
      const rng = new FxRng(42);
      for (let i = 0; i < 60; i++) {
        const round = fakeRound(def, fakeTape(def, randomStops(rng)));
        const term = terminalFrame(round);
        const paid = m === 'nether' ? refTumbles(def, round.base!.stops, def.ladder).finalWindow : windowFromStops(def, round.base!.stops);
        expect(term.cells.map((c) => round.glyphs.indexOf(c.g))).toEqual(paid.map((s) => round.glyphs.indexOf(round.glyphs[s]!)));
        for (const [shared, local] of [
          [SHARED_PROFILE, SHARED_PROFILE],
          [TURBO, TURBO],
          [SHARED_PROFILE, REDUCED],
        ] as const) {
          const tl = timelineOf(round, shared, local);
          for (const o of [DEFAULT_FRAME_OPTIONS, REDUCED_OPTS, NO_TINT]) {
            expect(sameFrame(reelFrame(round, tl, tl.endMs(), o), term)).toBe(true);
            expect(sameFrame(SLOT_FRAME_MODEL.frame(round, tl, tl.endMs() + 5000), SLOT_FRAME_MODEL.terminal(round))).toBe(true);
          }
        }
      }
    }
  });

  it('every reel lands on its exact window at its stop time (no row overshoot, F4)', () => {
    const def = fakeDef('overworld');
    const rng = new FxRng(3);
    for (let i = 0; i < 40; i++) {
      const round = fakeRound(def, fakeTape(def, randomStops(rng)));
      const tl = timelineOf(round);
      const stops = landTimes(tl);
      stops.forEach((st, r) => {
        for (const dt of [0, 50, 100, 250]) {
          const f = reelFrame(round, tl, st + dt);
          for (let y = 0; y < ROWS; y++) expect(f.cells[cellIndex(r, y)]!.g).toBe(round.glyphs[round.base!.landed[cellIndex(r, y)]!]);
        }
      });
    }
  });

  it('a scrolling reel runs through the real strip and its sequence ends at the stop (F2)', () => {
    const def = fakeDef('end');
    const round = fakeRound(def, fakeTape(def, [3, 9, 14, 20, 1]));
    const tl = timelineOf(round);
    const stops = landTimes(tl);
    for (let r = 0; r < REELS; r++) {
      const strip = def.strips[r]!;
      const seen: number[] = [];
      for (let t = 0; t < stops[r]!; t += 50) {
        const f = reelFrame(round, tl, t);
        const col = [0, 1, 2].map((y) => round.glyphs.indexOf(f.cells[cellIndex(r, y)]!.g));
        // find p with strip[p..p+2] == col
        const p = [...Array(strip.length).keys()].filter((q) => [0, 1, 2].every((y) => strip[(q + y) % strip.length] === col[y]));
        expect(p.length).toBeGreaterThan(0);
        seen.push(t);
      }
      // positions count down one row per 100 ms and reach stop + 1 in the last frame before the stop
      const last = reelFrame(round, tl, stops[r]! - 1);
      const want = [1, 2, 3].map((y) => round.glyphs[strip[(round.base!.stops[r]! + y) % strip.length]!]);
      expect([0, 1, 2].map((y) => last.cells[cellIndex(r, y)]!.g)).toEqual(want);
      expect(seen.length).toBeGreaterThan(0);
    }
  });

  it('blur plane at full speed, base plane for spin-up and the last 3 rows, 1-frame land flash', () => {
    const def = fakeDef('overworld');
    const round = fakeRound(def, fakeTape(def, [0, 0, 0, 0, 0]));
    const tl = timelineOf(round);
    const st = landTimes(tl);
    expect(reelFrame(round, tl, 50).cells[0]!.plane).toBe(PLANE_BASE);
    expect(reelFrame(round, tl, 300).cells[cellIndex(4, 0)]!.plane).toBe(PLANE_BLUR);
    expect(reelFrame(round, tl, st[0]! - 250).cells[0]!.plane).toBe(PLANE_BASE);
    const landed = reelFrame(round, tl, st[0]! + 50).cells;
    for (let y = 0; y < ROWS; y++) expect(landed[cellIndex(0, y)]!.plane).toBe(PLANE_WIN);
    const after = reelFrame(round, tl, st[0]! + 220).cells;
    for (let y = 0; y < ROWS; y++) {
      const sym = round.base!.landed[cellIndex(0, y)]!;
      if (round.roles[sym] === 'PAY') expect(after[cellIndex(0, y)]!.plane).toBe(PLANE_BASE);
    }
  });

  it('reduce motion: no scrolling — each column is one static blurred strip window, then the result', () => {
    const def = fakeDef('nether');
    const round = fakeRound(def, fakeTape(def, [5, 6, 7, 8, 9]));
    const tl = timelineOf(round);
    const st = landTimes(tl);
    const a = reelFrame(round, tl, 10, REDUCED_OPTS);
    const b = reelFrame(round, tl, st[0]! - 10, REDUCED_OPTS);
    expect(sameFrame(a, b)).toBe(true);
    expect(a.cells[0]!.plane).toBe(PLANE_BLUR);
    // no land flash under reduce motion
    expect(reelFrame(round, tl, st[0]! + 10, REDUCED_OPTS).cells[0]!.plane).toBe(PLANE_BASE);
  });
});

describe('slot frames: honest anticipation (F3)', () => {
  it('a column is tinted/arrowed iff the timeline anticipates it; the condition is visible on stopped reels', () => {
    for (const m of MACHINES) {
      const def = fakeDef(m);
      const rng = new FxRng(11);
      let anticipated = 0;
      for (let i = 0; i < 300; i++) {
        const round = fakeRound(def, fakeTape(def, randomStops(rng)));
        const tl = timelineOf(round);
        const antic = new Set(tl.beats.filter((b) => b.kind === SLOT_BEAT.ANTICIPATE).map((b) => b.lane));
        if (antic.size) anticipated++;
        // anticipation only after a visible trigger on earlier reels
        const times = stopTimes(def, round.base!.landed, true, true);
        times.forEach((tt, r) => {
          if (r > 0 && tt - times[r - 1]! > 150) expect(antic.has(r)).toBe(true);
        });
        for (let t = 0; t < tl.endMs(); t += 100) {
          const f = reelFrame(round, tl, t);
          const g = reelFrame(round, tl, t, NO_TINT);
          for (let r = 0; r < REELS; r++) {
            const tinted = [0, 1, 2].some((y) => f.cells[cellIndex(r, y)]!.tint === TINT_ANTIC);
            if (tinted || g.arrows & (1 << r)) expect(antic.has(r)).toBe(true);
          }
        }
      }
      expect(anticipated).toBeGreaterThan(0);
    }
  });

  it('anticipating reels pulse (flashes on) or glow steadily (flashes off / reduce motion)', () => {
    const def = fakeDef('overworld');
    // scatters on reels 1 and 2: strips have SC at index 3 → stop 1 shows rows 2 = index 3
    const round = fakeRound(def, fakeTape(def, [1, 1, 10, 10, 10]));
    const tl = timelineOf(round);
    const a = tl.beats.find((b) => b.kind === SLOT_BEAT.ANTICIPATE);
    expect(a).toBeDefined();
    const mid = a!.at + 300;
    const tints = [mid, mid + 100].map((t) => reelFrame(round, tl, t).cells[cellIndex(a!.lane, 0)]!.tint);
    expect(new Set(tints)).toEqual(new Set([TINT_ANTIC, TINT_NONE]));
    const steady = [mid, mid + 100].map((t) => reelFrame(round, tl, t, REDUCED_OPTS).cells[cellIndex(a!.lane, 0)]!.tint);
    expect(steady).toEqual([TINT_ANTIC, TINT_ANTIC]);
  });
});

describe('slot frames: win show, tumbles, sticky wilds', () => {
  const findWin = (m: MachineId, pred: (r: ReturnType<typeof fakeRound>) => boolean) => {
    const def = fakeDef(m);
    const rng = new FxRng(5);
    for (let i = 0; i < 5000; i++) {
      const round = fakeRound(def, fakeTape(def, randomStops(rng)));
      if (pred(round)) return round;
    }
    throw new Error('no fixture');
  };

  it('win overview dims the rest; the way cycle lights one symbol at a time', () => {
    const round = findWin('overworld', (r) => new Set(r.base!.evals[0]!.wins.map((w) => w.symbol)).size >= 2);
    const tl = timelineOf(round);
    const show = tl.beats.find((b) => b.kind === SLOT_BEAT.WIN_SHOW)!;
    const f = reelFrame(round, tl, show.at + 100);
    const mask = round.base!.evals[0]!.winMask;
    f.cells.forEach((c, i) => expect(c.plane === PLANE_WIN).toBe((mask & (1 << i)) !== 0));
    f.cells.forEach((c, i) => expect(c.tint === TINT_DIM).toBe((mask & (1 << i)) === 0));
    const cyc = tl.beats.filter((b) => b.kind === SLOT_BEAT.WAY_CYCLE);
    expect(cyc.length).toBeGreaterThanOrEqual(2);
    for (const c of cyc) {
      const sym = c.args[0]!;
      const m = round.base!.evals[0]!.wins.filter((w) => w.symbol === sym).reduce((a, w) => a | w.cellMask, 0);
      reelFrame(round, tl, c.at + 50).cells.forEach((x, i) => expect(x.plane === PLANE_WIN).toBe((m & (1 << i)) !== 0));
    }
    // after the show: the paid window with its win glow, no dim (== terminal)
    expect(sameFrame(reelFrame(round, tl, beatEnd(cyc[cyc.length - 1]!) + 10), terminalFrame(round))).toBe(true);
    // without glyph tinting nothing is dimmed
    expect(reelFrame(round, tl, show.at + 100, NO_TINT).cells.every((c) => c.tint === TINT_NONE)).toBe(true);
  });

  it('Nether tumbles: winning cells burn to embers, gravity drops the rest, new cells fall from above', () => {
    const round = findWin('nether', (r) => r.base!.evals.length >= 3);
    const tl = timelineOf(round);
    const explodes = tl.beats.filter((b) => b.kind === SLOT_BEAT.TUMBLE_EXPLODE);
    const falls = tl.beats.filter((b) => b.kind === SLOT_BEAT.TUMBLE_FALL);
    expect(explodes.length).toBe(round.base!.evals.length - 1);
    explodes.forEach((ex, k) => {
      const prev = round.base!.evals[k]!;
      const f = reelFrame(round, tl, ex.at + 10);
      f.cells.forEach((c, i) => {
        if (prev.winMask & (1 << i)) expect(c.g).toBe(SLOT_GLYPH.EMBER);
      });
      // end of the fall = next evaluation's window
      const fall = falls[k]!;
      const g = reelFrame(round, tl, beatEnd(fall) + 10);
      expect(g.cells.map((c) => c.g)).toEqual(round.base!.evals[k + 1]!.window.map((s) => round.glyphs[s]));
      // mid-fall: every non-ember glyph of a column is a kept or new symbol of that column (no invented symbols)
      const mid = reelFrame(round, tl, fall.at + fall.dur / 2);
      for (let r = 0; r < REELS; r++) {
        const allowed = new Set([...prev.window.slice(r * 3, r * 3 + 3), ...round.base!.evals[k + 1]!.window.slice(r * 3, r * 3 + 3)].map((s) => round.glyphs[s]));
        for (let y = 0; y < ROWS; y++) {
          const g2 = mid.cells[cellIndex(r, y)]!.g;
          if (g2 !== SLOT_GLYPH.EMBER) expect(allowed.has(g2)).toBe(true);
        }
      }
    });
  });

  it('End free spins: an egg expands (2 frames outward) and its reel stays sticky, glowing, without spinning', () => {
    const def = fakeDef('end');
    // reel 2 (index 1) has a wild at strip index 6 → stop 5 puts it on row 1
    const tape = fakeTape(def, [0, 0, 0, 0, 0], {
      free: [
        { stops: [2, 5, 9, 12, 7], stickyMaskAfter: 0b001 },
        { stops: [4, 8, 1, 3, 11], stickyMaskAfter: 0b001 },
      ],
    });
    const round = fakeRound(def, tape);
    const tl = timelineOf(round);
    const ex = tl.beats.find((b) => b.kind === SLOT_BEAT.WILD_EXPAND)!;
    expect(ex.lane).toBe(1);
    const wild = round.glyphs[0]!;
    const before = reelFrame(round, tl, ex.at - 1);
    expect([0, 1, 2].filter((y) => before.cells[cellIndex(1, y)]!.g === wild).length).toBe(1);
    const after = reelFrame(round, tl, beatEnd(ex) + 1);
    expect([0, 1, 2].every((y) => after.cells[cellIndex(1, y)]!.g === wild)).toBe(true);
    // second free spin: reel 2 is sticky (WWW, win plane) while the others scroll
    const fs2 = tl.beats.filter((b) => b.kind === SLOT_BEAT.FS_SPIN)[1]!;
    const f = reelFrame(round, tl, fs2.at + 400);
    for (let y = 0; y < ROWS; y++) {
      expect(f.cells[cellIndex(1, y)]!.g).toBe(wild);
      expect(f.cells[cellIndex(1, y)]!.plane).toBe(PLANE_WIN);
    }
    expect(f.cells[cellIndex(4, 0)]!.plane).toBe(PLANE_BLUR);
  });
});

describe('slot frames: text', () => {
  it('rows are glyphs with § codes only (no letters), 2 spaces apart; arrow row only with arrows', () => {
    const def = fakeDef('overworld');
    const round = fakeRound(def, fakeTape(def, [1, 1, 10, 10, 10]));
    const tl = timelineOf(round);
    for (let t = 0; t < tl.endMs(); t += 100) {
      for (const o of [DEFAULT_FRAME_OPTIONS, NO_TINT]) {
        const f = reelFrame(round, tl, t, o);
        const rows = renderRows(f, o.tinting);
        expect(rows.length).toBe(f.arrows ? 4 : 3);
        for (const row of rows) expect(row.replace(/§./g, '')).not.toMatch(/[A-Za-zА-Яа-яЁё]/);
      }
    }
    const rows = renderRows(terminalFrame(round));
    expect(rows[0]!.startsWith('§f')).toBe(true);
    expect(rows[0]!.includes(String.fromCodePoint(onPlane(round.glyphs[round.base!.landed[0]!]!, PLANE_BASE))) || rows[0]!.includes(String.fromCodePoint(onPlane(round.glyphs[round.base!.landed[0]!]!, PLANE_WIN)))).toBe(true);
  });
});

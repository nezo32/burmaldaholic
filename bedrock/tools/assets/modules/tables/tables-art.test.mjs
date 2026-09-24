import { describe, expect, it } from 'vitest';
import generate, { HEAD_COLS, HEAD_FRAMES } from '../tables.mjs';
import { WHEEL_ORDER, REDS } from './theme.mjs';
import { LAYOUT_BIG, layoutSize, racetrackCells } from './roulette.mjs';
import { CRAPS_BIG, CRAPS_COMPACT, crapsRects } from './craps.mjs';

const out = generate();
const byPath = new Map(out.map((o) => [o.path, o]));
const size = (bytes) => [bytes.readUInt32BE(16), bytes.readUInt32BE(20)];
const T = 'src/main/resources/assets/burmaldaholic/textures';

describe('tables art', () => {
  it('is Java only and stays inside roulette / craps / extras / core ownership', () => {
    for (const o of out) {
      expect(o.edition).toBe('java');
      expect(o.path.startsWith(`${T}/`)).toBe(true);
      expect(o.path.split('/').some((s) => ['core', 'roulette', 'craps', 'extras'].includes(s))).toBe(true);
    }
    expect(new Set(out.map((o) => o.path)).size).toBe(out.length);
  });

  it('is deterministic', () => {
    const again = generate();
    expect(again.every((o, i) => o.path === out[i].path && o.bytes.equals(out[i].bytes))).toBe(true);
  }, 120_000);

  it('has a full wheel data set: 37 pockets, 18 red, head sheet of 74 frames in a 10-column grid', () => {
    expect([...WHEEL_ORDER].sort((a, b) => a - b)).toEqual([...Array(37).keys()]);
    expect(REDS.size).toBe(18);
    const head = byPath.get(`${T}/gui/tables/roulette/wheel_head.png`);
    expect(size(head.bytes)).toEqual([152 * HEAD_COLS, 152 * Math.ceil(HEAD_FRAMES / HEAD_COLS)]);
    expect(size(byPath.get(`${T}/gui/tables/roulette/wheel_mini_village.png`).bytes)).toEqual([720, 288]);
  });

  it('lays the racetrack out in wheel order with every number once', () => {
    const cells = racetrackCells();
    expect(cells.map((c) => c.n).sort((a, b) => a - b)).toEqual([...Array(37).keys()]);
    const seq = cells.map((c) => c.n);
    // clockwise around the stadium = the wheel order (rotated)
    const start = WHEEL_ORDER.indexOf(seq[0]);
    expect(seq).toEqual(seq.map((_, i) => WHEEL_ORDER[(start + i) % 37]));
  });

  it('matches the documented layout sizes', () => {
    expect(layoutSize(LAYOUT_BIG)).toEqual({ w: 278, h: 98 });
    expect(size(byPath.get(`${T}/gui/tables/roulette/layout_village.png`).bytes)).toEqual([278, 98]);
    expect(crapsRects(CRAPS_BIG).h).toBe(162);
    expect(size(byPath.get(`${T}/gui/tables/craps/layout_end.png`).bytes)).toEqual([386, 162]);
    expect(crapsRects(CRAPS_COMPACT).h).toBeLessThan(130);
  });

  it('gives every nine-slice mcmeta the real sprite size', () => {
    for (const o of out.filter((x) => x.path.endsWith('.mcmeta'))) {
      const meta = JSON.parse(o.bytes.toString());
      const png = byPath.get(o.path.replace(/\.mcmeta$/, ''));
      const [w, h] = size(png.bytes);
      if (meta.gui) {
        const s = meta.gui.scaling;
        expect(s.width, o.path).toBe(w);
        if (meta.animation) expect(h % s.height, o.path).toBe(0);
        else expect(s.height, o.path).toBe(h);
      } else if (meta.animation) expect(h % (meta.animation.height ?? w), o.path).toBe(0);
    }
  });

  it('keeps the whole set small (≤ 1.2 MB)', () => {
    expect(out.reduce((s, o) => s + o.bytes.length, 0)).toBeLessThan(1.2 * 1024 * 1024);
  });
});

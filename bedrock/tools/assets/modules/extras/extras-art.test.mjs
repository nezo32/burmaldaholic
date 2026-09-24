import { describe, expect, it } from 'vitest';
import generate from '../extras.mjs';
import coreGenerate from '../core.mjs';
import { COIN_ANGLES, coinFace, coinFrame } from './coin.mjs';
import { ALL_DEFS, SCRATCH_SYMBOLS, WHEEL_CODES, wheelIcon, wheelIconMini } from './icons.mjs';
import { alphaAt } from '../slots/raster.mjs';
import { art16, validate } from './kit.mjs';
import { BOARD, binX, pegXY } from './plinko.mjs';
import { TICKET, cellXY, scratchEdges } from './scratch.mjs';
import { APPENDIX_B, wheelFace } from './wheel.mjs';

const opaque = (img) => {
  let n = 0;
  for (let i = 3; i < img.data.length; i += 4) if (img.data[i]) n++;
  return n;
};
/** PNG size from the IHDR chunk. */
const pngSize = (bytes) => [bytes.readUInt32BE(16), bytes.readUInt32BE(20)];

describe('extras art: sources', () => {
  it('has valid grids (rectangular, every char in the legend)', () => {
    expect(() => validate(ALL_DEFS)).not.toThrow();
    for (const d of ALL_DEFS) expect(d.grid.length, d.id).toBe(16);
  });

  it('keeps every symbol and wheel icon readable (enough opaque pixels)', () => {
    for (const d of SCRATCH_SYMBOLS) expect(opaque(art16(d)), d.id).toBeGreaterThan(40);
    for (const code of WHEEL_CODES) {
      expect(opaque(wheelIcon(code)), code).toBeGreaterThan(40);
      expect(opaque(wheelIconMini(code)), code).toBeGreaterThan(12);
    }
  });

  it('bakes the Appendix B face (54 segments, the documented counts)', () => {
    expect(APPENDIX_B).toHaveLength(54);
    const count = (ch) => [...APPENDIX_B].filter((c) => c === ch).length;
    expect({ B: count('B'), C: count('C'), H: count('H'), M: count('M'), D: count('D'), T: count('T'), E: count('E'), X: count('X') })
      .toEqual({ B: 25, C: 1, H: 5, M: 11, D: 7, T: 3, E: 1, X: 1 });
    const face = wheelFace();
    expect([face.w, face.h]).toEqual([160, 160]);
    expect(alphaAt(face, 0, 0)).toBe(0);
    expect(alphaAt(face, 80, 80)).toBe(255);
  });
});

describe('extras art: coin', () => {
  it('tells heads and tails apart by shape (the emblem masks differ), not only by colour', () => {
    const h = coinFace('heads');
    const t = coinFace('tails');
    let diff = 0;
    for (let i = 0; i < h.data.length; i += 4) if (h.data[i] !== t.data[i] || h.data[i + 1] !== t.data[i + 1]) diff++;
    expect(diff).toBeGreaterThan(60);
  });

  it('spins from a full face through the edge (frame 6 is the narrowest) to the other face', () => {
    expect(COIN_ANGLES).toHaveLength(12);
    const widths = COIN_ANGLES.map((_, k) => {
      const f = coinFrame(k);
      let x0 = 64;
      let x1 = -1;
      for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) if (f.data[(y * 64 + x) * 4 + 3] > 200) [x0, x1] = [Math.min(x0, x), Math.max(x1, x)];
      return x1 - x0;
    });
    expect(Math.min(...widths)).toBe(widths[6]);
    expect(widths[0]).toBeGreaterThan(55);
    expect(widths[11]).toBeGreaterThan(55);
  });
});

describe('extras art: layouts', () => {
  it('drills the Plinko pegs inside the board and centres 13 bins on the pitch', () => {
    for (let r = 0; r < BOARD.rows; r++)
      for (let j = 0; j <= r; j++) {
        const [x, y] = pegXY(r, j);
        expect(x).toBeGreaterThan(8);
        expect(x).toBeLessThan(BOARD.w - 8);
        expect(y).toBeLessThan(BOARD.binY - 8);
      }
    expect(binX(0)).toBe(BOARD.cx - 6 * BOARD.pitchX);
    expect(binX(12) - binX(0)).toBe(12 * BOARD.pitchX);
  });

  it('fits the scratch cells inside their ticket and makes cells a whole number of 4 px sub-tiles', () => {
    for (const layout of ['solo', 'showdown']) {
      const L = TICKET[layout];
      expect(L.cell[0] % 4).toBe(0);
      expect(L.cell[1] % 4).toBe(0);
      const [x, y] = cellXY(layout, 8);
      expect(x + L.cell[0]).toBeLessThanOrEqual(L.w - 4);
      expect(y + L.cell[1]).toBeLessThanOrEqual(L.h - 4);
    }
    for (const t of ['basic', 'gold', 'showdown']) expect(scratchEdges(t)).toHaveLength(16);
  });
});

describe('extras art: outputs', () => {
  const out = generate();

  it('is Java-only, deterministic and inside the extras / pvp namespaces', () => {
    expect(out.length).toBeGreaterThan(150);
    for (const o of out) {
      expect(o.edition).toBe('java');
      expect(o.path, o.path).toMatch(/\/(extras|pvp)\//);
    }
    const again = generate();
    expect(again.map((o) => o.path)).toEqual(out.map((o) => o.path));
    again.forEach((o, i) => expect(o.bytes.equals(out[i].bytes), o.path).toBe(true));
    expect(new Set(out.map((o) => o.path)).size).toBe(out.length);
  });

  it('writes nine-slice metadata that matches each PNG and keeps borders inside it', () => {
    const byPath = new Map(out.map((o) => [o.path, o]));
    for (const o of out) {
      if (!o.path.endsWith('.png.mcmeta')) continue;
      const meta = JSON.parse(o.bytes.toString());
      const [w, h] = pngSize(byPath.get(o.path.replace(/\.mcmeta$/, '')).bytes);
      if (meta.gui?.scaling?.type === 'nine_slice') {
        const s = meta.gui.scaling;
        expect([s.width, s.height], o.path).toEqual([w, h]);
        expect(2 * s.border, o.path).toBeLessThan(Math.min(w, h));
      }
      if (meta.animation) expect(h % (meta.animation.height ?? w), o.path).toBe(0);
    }
  });

  it('adds the Casino Menu shell to core only under core-owned Java paths', () => {
    const core = coreGenerate().filter((o) => /\/(menu|hud\/(chip_counter|chip_icon|delta)|toast\/(achievement|pvp|loan))/.test(o.path));
    expect(core.length).toBeGreaterThan(40);
    for (const o of core) {
      expect(o.edition).toBe('java');
      expect(o.path).toMatch(/\/core\//);
    }
  });
});

import { describe, expect, it } from 'vitest';
import generate, { HEAD_COLS, HEAD_FRAMES, RO_HEAD_COLS, RO_HEAD_FRAMES } from '../tables.mjs';
import { BOWL_BIG, BOWL_M2, BOWL_M3, HEAD_M2, HEAD_M3, head, pocketHalfWidth } from './wheel.mjs';
import { inflateSync } from 'node:zlib';

/** Minimal decoder for the generator's own PNGs (8-bit RGBA, non-interlaced). */
function decode(bytes) {
  const w = bytes.readUInt32BE(16);
  const h = bytes.readUInt32BE(20);
  if (bytes[24] !== 8 || bytes[25] !== 6) throw new Error('not 8-bit RGBA');
  const parts = [];
  for (let o = 8; o < bytes.length; ) {
    const len = bytes.readUInt32BE(o);
    const type = bytes.toString('ascii', o + 4, o + 8);
    if (type === 'IDAT') parts.push(bytes.subarray(o + 8, o + 8 + len));
    o += 12 + len;
  }
  const raw = inflateSync(Buffer.concat(parts));
  const data = new Uint8Array(w * h * 4);
  const stride = w * 4;
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)];
    for (let x = 0; x < stride; x++) {
      const v = raw[y * (stride + 1) + 1 + x];
      const a = x >= 4 ? data[y * stride + x - 4] : 0;
      const b = y > 0 ? data[(y - 1) * stride + x] : 0;
      const c = x >= 4 && y > 0 ? data[(y - 1) * stride + x - 4] : 0;
      let p;
      if (f === 0) p = 0;
      else if (f === 1) p = a;
      else if (f === 2) p = b;
      else if (f === 3) p = (a + b) >> 1;
      else {
        const q = a + b - c;
        const pa = Math.abs(q - a), pb = Math.abs(q - b), pc = Math.abs(q - c);
        p = pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      }
      data[y * stride + x] = (v + p) & 255;
    }
  }
  return { w, h, data };
}
import { WHEEL_ORDER, REDS } from './theme.mjs';
import { LAYOUT_BIG, layoutSize, racetrackCells } from './roulette.mjs';
import { CRAPS_BIG, CRAPS_COMPACT, crapsRects } from './craps.mjs';

const out = generate();
const byPath = new Map(out.map((o) => [o.path, o]));
const size = (bytes) => [bytes.readUInt32BE(16), bytes.readUInt32BE(20)];
const T = 'src/main/resources/assets/burmaldaholic/textures';

describe('tables art', () => {
  it('stays inside roulette / craps / extras / core ownership', () => {
    for (const o of out) {
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

  // ---- RO (ROULETTE-ANIM-SPEC §5, PHASE2B D8) ------------------------------------------------------------------------
  const img = (rel) => decode(byPath.get(`${T}/${rel}`).bytes);
  const alpha = (im, x, y) => im.data[(y * im.w + x) * 4 + 3];

  it('RO: 222-frame medium heads in 20-column sheets, one 208 frame, per-size shadows', () => {
    expect(RO_HEAD_FRAMES).toBe(222);
    expect(RO_HEAD_COLS).toBe(20);
    const rows = Math.ceil(RO_HEAD_FRAMES / RO_HEAD_COLS);
    expect(size(byPath.get(`${T}/gui/tables/roulette/wheel_head_m104.png`).bytes)).toEqual([76 * 20, 76 * rows]);
    expect(size(byPath.get(`${T}/gui/tables/roulette/wheel_head_m144.png`).bytes)).toEqual([105 * 20, 105 * rows]);
    expect(size(byPath.get(`${T}/gui/tables/roulette/wheel_head_208.png`).bytes)).toEqual([152, 152]);
    expect(size(byPath.get(`${T}/gui/tables/roulette/wheel_shadow_m104.png`).bytes)).toEqual([112, 112]);
    expect(size(byPath.get(`${T}/gui/tables/roulette/wheel_shadow_m144.png`).bytes)).toEqual([152, 152]);
    for (const t of ['village', 'bastion', 'end']) {
      expect(size(byPath.get(`${T}/entity/roulette/rotor_${t}.png`).bytes)).toEqual([152, 152]);
      expect(size(byPath.get(`${T}/entity/roulette/bowl3d_${t}.png`).bytes)).toEqual([64, 64]);
    }
  });

  it('RO: frame f + 6 has its frets at the same screen angles as frame f (one pocket further on)', () => {
    const sheetImg = img('gui/tables/roulette/wheel_head_m144.png');
    const fs = 105;
    const isFret = (f, x, y) => {
      const ox = (f % RO_HEAD_COLS) * fs;
      const oy = Math.floor(f / RO_HEAD_COLS) * fs;
      const o = ((oy + y) * sheetImg.w + ox + x) * 4;
      const [r, g, b] = [sheetImg.data[o], sheetImg.data[o + 1], sheetImg.data[o + 2]];
      return r > 150 && g > 90 && b < 120 && r > b + 60; // brass / gold
    };
    const rc = (HEAD_M3.pocket[0] + HEAD_M3.pocket[1]) / 2;
    for (const f of [0, 7, 100, 215]) {
      let same = 0;
      let n = 0;
      for (let a = 0; a < 360; a += 0.5) {
        const x = Math.floor(fs / 2 + rc * Math.sin((a * Math.PI) / 180));
        const y = Math.floor(fs / 2 - rc * Math.cos((a * Math.PI) / 180));
        n++;
        if (isFret(f, x, y) === isFret((f + 6) % RO_HEAD_FRAMES, x, y)) same++;
      }
      expect(same / n).toBeGreaterThan(0.97);
    }
    // a step of one frame is 360/222°: the pockets do move (not the same picture)
    let diff = 0;
    for (let y = 0; y < fs; y++) for (let x = 0; x < fs; x++) if (isFret(0, x, y) !== isFret(1, x, y)) diff++;
    expect(diff).toBeGreaterThan(10);
  });

  it('RO: diamond flashes centred, sized per wheel', () => {
    for (const [tag, s, bowl] of [['m104', 5, BOWL_M2], ['m144', 7, BOWL_M3], ['208', 9, BOWL_BIG]]) {
      const im = img(`gui/sprites/tables/roulette/diamond_flash_${tag}.png`);
      expect([im.w, im.h]).toEqual([s, 3 * s]);
      for (let f = 0; f < 3; f++) expect(alpha(im, (s - 1) / 2, f * s + (s - 1) / 2), tag).toBe(255);
      expect(bowl.deflectorR * 2).toBeLessThanOrEqual(s + 0.01);
    }
  });

  it('RO: pocket glow ring radius = pocket half-width + 2 per size', () => {
    for (const [tag, s, hg] of [['m104', 12, HEAD_M2], ['m144', 16, HEAD_M3]]) {
      const im = img(`gui/sprites/tables/roulette/pocket_glow_${tag}.png`);
      expect([im.w, im.h]).toEqual([s, 4 * s]);
      // frame 2 (brightest): the most opaque radius along the row through the centre
      let best = 0;
      let bestR = 0;
      for (let x = 0; x < s; x++) {
        const a = alpha(im, x, 2 * s + s / 2);
        if (a > best) {
          best = a;
          bestR = Math.abs(x + 0.5 - s / 2);
        }
      }
      expect(Math.abs(bestR - (pocketHalfWidth(hg) + 2))).toBeLessThanOrEqual(1.25);
      expect(alpha(im, s / 2, 2 * s + s / 2)).toBe(0); // the pocket itself stays visible
    }
  });

  it('RO: loupe glass is clear, rake is a 24 × 6 nine-slice, ball shadow 7 × 4', () => {
    const lp = img('gui/sprites/tables/roulette/loupe.png');
    expect([lp.w, lp.h]).toEqual([40, 40]);
    expect(alpha(lp, 20, 20)).toBe(0);
    expect(alpha(lp, 26, 26)).toBe(0);
    expect(alpha(lp, 20, 2)).toBe(255);
    expect(size(byPath.get(`${T}/gui/sprites/tables/roulette/rake.png`).bytes)).toEqual([24, 6]);
    const meta = JSON.parse(byPath.get(`${T}/gui/sprites/tables/roulette/rake.png.mcmeta`).bytes.toString());
    expect(meta.gui.scaling.border.left).toBe(4);
    expect(size(byPath.get(`${T}/gui/sprites/tables/roulette/ball_shadow.png`).bytes)).toEqual([7, 4]);
    const b3 = img('entity/roulette/bowl3d_village.png');
    expect(alpha(b3, 8, 32)).toBe(255); // ball centre
    expect(alpha(b3, 0, 24)).toBe(0); // ball corner (cutout)
    expect(alpha(b3, 24, 32)).toBeGreaterThan(100); // shadow centre
  });

  it('RO: D8 budget — the 222-frame sheets add ≤ 4 MB to the jar and ≤ 24 MB VRAM', () => {
    const bytes = ['m104', 'm144'].reduce((s, t) => s + byPath.get(`${T}/gui/tables/roulette/wheel_head_${t}.png`).bytes.length, 0);
    expect(bytes).toBeLessThan(4 * 1024 * 1024);
    const vram = 4 * (76 * 20 * 76 * 12 + 105 * 20 * 105 * 12);
    expect(vram).toBeLessThanOrEqual(24 * 1024 * 1024);
    expect(head(HEAD_M3, 0).w).toBe(105);
  });
});

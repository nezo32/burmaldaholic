// Dice kit art (DCa, PHASE2B §4): the orientation atlas covers SO(3) within 22° (quaternion arc), rest frames are exact
// and show the right pips, sizes match the cells, the generated Java table and JSON agree, and output is deterministic.
import { describe, expect, it } from 'vitest';
import { Buffer } from 'node:buffer';
import zlib from 'node:zlib';
import generate, { ATLAS_JAVA } from '../modules/dicekit.mjs';
import * as D from '../modules/tables/dice.mjs';

const out = generate();
const byPath = new Map(out.map((o) => [o.path, o]));
const T = 'src/main/resources/assets/burmaldaholic/textures';
const size = (bytes) => [bytes.readUInt32BE(16), bytes.readUInt32BE(20)];

/** Minimal PNG decoder for our own RGBA8 non-interlaced output. */
function decode(bytes) {
  const [w, h] = size(bytes);
  const chunks = [];
  let o = 8;
  while (o < bytes.length) {
    const len = bytes.readUInt32BE(o);
    const type = bytes.toString('ascii', o + 4, o + 8);
    if (type === 'IDAT') chunks.push(bytes.subarray(o + 8, o + 8 + len));
    o += 12 + len;
  }
  const raw = zlib.inflateSync(Buffer.concat(chunks));
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
      if (f === 0) p = v;
      else if (f === 1) p = v + a;
      else if (f === 2) p = v + b;
      else if (f === 3) p = v + ((a + b) >> 1);
      else {
        const pa = Math.abs(b - c);
        const pb = Math.abs(a - c);
        const pc = Math.abs(a + b - 2 * c);
        p = v + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c);
      }
      data[y * stride + x] = p & 255;
    }
  }
  return { w, h, data };
}

const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];

describe('dice kit art', () => {
  it('stays core-owned and writes the Java table', () => {
    for (const o of out) {
      if (o.path === ATLAS_JAVA) continue;
      expect(o.path.startsWith(`${T}/`)).toBe(true);
      expect(o.path.split('/').includes('core')).toBe(true);
    }
    expect(byPath.has(ATLAS_JAVA)).toBe(true);
    expect(new Set(out.map((o) => o.path)).size).toBe(out.length);
  });

  it('is deterministic', () => {
    const again = generate();
    expect(again.every((o, i) => o.path === out[i].path && o.bytes.equals(out[i].bytes))).toBe(true);
  }, 120_000);

  it('has 384 unit frame quaternions in a 20-column sheet per cell', () => {
    const qs = D.atlasQuats();
    expect(qs.length).toBe(384);
    for (const q of qs) expect(Math.abs(Math.hypot(...q) - 1)).toBeLessThan(1e-12);
    for (const cell of D.ATLAS_CELLS) {
      const F = D.atlasFrame(cell);
      expect(size(byPath.get(`${T}/gui/tables/core/dice_atlas_${cell}.png`).bytes)).toEqual([F * 20, F * 20]);
      for (let s = 0; s < 3; s++) expect(byPath.has(`${T}/gui/sprites/tables/core/dice/shadow_${cell}_${s}.png`)).toBe(true);
      expect(size(byPath.get(`${T}/gui/sprites/tables/core/dice/rest_glint_${cell}.png`).bytes)).toEqual([F, F]);
    }
    expect(size(byPath.get(`${T}/gui/sprites/tables/core/dice/dust.png`).bytes)).toEqual([5, 20]);
    expect(size(byPath.get(`${T}/gui/sprites/tables/core/dice/spark.png`).bytes)).toEqual([3, 9]);
  });

  it('covers every orientation within 22° of quaternion arc (20k random rotations)', () => {
    const qs = D.atlasQuats();
    let seed = 12345;
    const rnd = () => {
      seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
      return seed / 4294967296;
    };
    let worst = 0;
    for (let k = 0; k < 20000; k++) {
      const [u1, u2, u3] = [rnd(), rnd(), rnd()];
      const q = [Math.sqrt(1 - u1) * Math.sin(2 * Math.PI * u2), Math.sqrt(1 - u1) * Math.cos(2 * Math.PI * u2), Math.sqrt(u1) * Math.sin(2 * Math.PI * u3), Math.sqrt(u1) * Math.cos(2 * Math.PI * u3)];
      let best = 0;
      for (const f of qs) best = Math.max(best, Math.abs(dot(q, f)));
      worst = Math.max(worst, (Math.acos(Math.min(1, best)) * 180) / Math.PI);
    }
    expect(worst).toBeLessThanOrEqual(22);
  });

  it('includes every rest pose (24 elements × yaw 0, ±15°) exactly', () => {
    const qs = D.atlasQuats();
    const g = D.cubeGroup();
    for (let i = 0; i < 24; i++) {
      expect(D.faceUp(g[i])).toBe((i >> 2) + 1);
      for (const j of [0, 5, 10]) expect(D.faceUp(qs[16 * i + j])).toBe((i >> 2) + 1);
    }
  });

  it('shows the right pips on the top face of every rest frame, at every cell', () => {
    const qs = D.atlasQuats();
    for (const cell of D.ATLAS_CELLS) {
      const F = D.atlasFrame(cell);
      const img = decode(byPath.get(`${T}/gui/tables/core/dice_atlas_${cell}.png`).bytes);
      for (let gi = 0; gi < 24; gi++) {
        const i = 16 * gi; // yaw 0 rest frame
        const face = (gi >> 2) + 1;
        const quad = D.topQuad(cell, qs[i]);
        const x0 = Math.ceil(Math.min(...quad.map((p) => p[0]))) + 1;
        const x1 = Math.floor(Math.max(...quad.map((p) => p[0]))) - 1;
        const y0 = Math.ceil(Math.min(...quad.map((p) => p[1]))) + 1;
        const y1 = Math.floor(Math.max(...quad.map((p) => p[1]))) - 1;
        const fx = (i % 20) * F;
        const fy = Math.floor(i / 20) * F;
        const dark = (x, y) => {
          const o = ((fy + y) * img.w + fx + x) * 4;
          const [r, gg, b, a] = [img.data[o], img.data[o + 1], img.data[o + 2], img.data[o + 3]];
          return a > 0 && (face === 1 ? r > 150 && gg < 110 : r + gg + b < 200);
        };
        const seen = new Set();
        let clusters = 0;
        for (let y = y0; y <= y1; y++)
          for (let x = x0; x <= x1; x++) {
            if (!dark(x, y) || seen.has(y * F + x)) continue;
            clusters++;
            const st = [[x, y]];
            seen.add(y * F + x);
            while (st.length) {
              const [cx, cy] = st.pop();
              for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
                const nx = cx + dx;
                const ny = cy + dy;
                if (nx < x0 || ny < y0 || nx > x1 || ny > y1 || seen.has(ny * F + nx) || !dark(nx, ny)) continue;
                seen.add(ny * F + nx);
                st.push([nx, ny]);
              }
            }
          }
        expect(clusters, `cell ${cell} element ${gi}`).toBe(face);
        // the die is exactly cell px wide at rest (ink outline included)
        let minX = F;
        let maxX = -1;
        for (let y = 0; y < F; y++)
          for (let x = 0; x < F; x++) {
            if (img.data[((fy + y) * img.w + fx + x) * 4 + 3]) {
              minX = Math.min(minX, x);
              maxX = Math.max(maxX, x);
            }
          }
        expect(maxX - minX + 1, `width cell ${cell}`).toBe(cell);
      }
    }
  });

  it('writes the same frames to the JSON and the Java table', () => {
    const data = JSON.parse(byPath.get(`${T}/gui/tables/core/dice_atlas.json`).bytes.toString());
    expect(data.frames.length).toBe(384);
    const java = byPath.get(ATLAS_JAVA).bytes.toString();
    const q = java.slice(java.indexOf('Q = {') + 5, java.indexOf('};', java.indexOf('Q = {')));
    const nums = q.split(',').map((s) => Number(s.trim()));
    expect(nums.length).toBe(384 * 4);
    data.frames.forEach((f, i) => f.q.forEach((v, k) => expect(nums[4 * i + k]).toBe(v + 0)));
  });
});

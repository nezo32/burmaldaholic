// Card-table art: thin hex-string wrappers over the slots raster toolkit (../slots/raster.mjs, a candidate for
// lib/ per its header) plus the shape helpers the tables need (masks, distance fields, supersampled shapes).
import { blend, col, fill, get, image, put } from '../slots/raster.mjs';

export { image, get };
export {
  affine, brighten, clone, crop, desaturate, disc, fade, halo, hstrip, outline, over, ring, rng, scaleUp, shadow, star, tint, vstrip,
} from '../slots/raster.mjs';

const cache = new Map();
/** '#hex' | [r,g,b,a] → [r,g,b,a] (memoised). */
export const C = (c) => {
  if (Array.isArray(c)) return c;
  let v = cache.get(c);
  if (!v) cache.set(c, (v = col(c)));
  return v;
};
/** Set pixel (replace). */
export const P = (img, x, y, c) => c && put(img, x, y, C(c));
/** Blend pixel with alpha k. */
export const B = (img, x, y, c, k = 1) => c && blend(img, x, y, C(c), k);
/** Filled rectangle x, y, w, h (replace, or blend when k < 1). */
export const R = (img, x, y, w, h, c, k = 1) => fill(img, x, y, w, h, C(c), k);
/** 1 px rectangle outline x, y, w, h. */
export function box(img, x, y, w, h, c, k = 1) {
  R(img, x, y, w, 1, c, k);
  R(img, x, y + h - 1, w, 1, c, k);
  R(img, x, y + 1, 1, h - 2, c, k);
  R(img, x + w - 1, y + 1, 1, h - 2, c, k);
}
/** Bevel: light top/left, dark bottom/right (1 px). */
export function bevel(img, x, y, w, h, lightC, darkC, k = 1) {
  R(img, x, y, w - 1, 1, lightC, k);
  R(img, x, y, 1, h - 1, lightC, k);
  R(img, x + 1, y + h - 1, w - 1, 1, darkC, k);
  R(img, x + w - 1, y + 1, 1, h - 1, darkC, k);
}
/** Rounded filled rectangle (corner radius r ∈ {0,1,2,3}). */
export function rrect(img, x, y, w, h, r, c, k = 1) {
  const cut = [[], [1], [2, 1], [3, 1, 1]][r] ?? [];
  for (let j = 0; j < h; j++) {
    const top = j < cut.length ? cut[j] : 0;
    const bot = h - 1 - j < cut.length ? cut[h - 1 - j] : 0;
    const inset = Math.max(top, bot);
    R(img, x + inset, y + j, w - 2 * inset, 1, c, k);
  }
}
/** Draws a string grid with a legend at (x, y); '.' and ' ' are transparent. */
export function grid(img, rows, legend, x = 0, y = 0) {
  rows.forEach((row, j) => [...row].forEach((ch, i) => ch !== '.' && ch !== ' ' && P(img, x + i, y + j, legend[ch] ?? fail(ch))));
  return img;
}
const fail = (ch) => {
  throw new Error(`cards grid: no colour for '${ch}'`);
};
/** A new image from a grid. */
export const fromRows = (rows, legend) => grid(image(rows[0].length, rows.length), rows, legend);

/** Linear mix of two hex colours → hex. */
export function mixHex(a, b, t) {
  const x = C(a);
  const y = C(b);
  const v = [0, 1, 2].map((i) => Math.round(x[i] + (y[i] - x[i]) * t));
  return `#${v.map((n) => n.toString(16).padStart(2, '0')).join('').toUpperCase()}`;
}

/** Boolean mask of a shape: `inside(x + 0.5, y + 0.5)` sampled ss × ss per pixel, coverage ≥ 0.5. */
export function mask(w, h, inside, ss = 4) {
  const m = new Uint8Array(w * h);
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      let n = 0;
      for (let j = 0; j < ss; j++) for (let i = 0; i < ss; i++) if (inside(x + (i + 0.5) / ss, y + (j + 0.5) / ss)) n++;
      m[y * w + x] = n * 2 >= ss * ss ? 1 : 0;
    }
  return { w, h, m, at: (x, y) => (x < 0 || y < 0 || x >= w || y >= h ? 0 : m[y * w + x]) };
}

/** Chamfer distance (3-4) from every inside pixel to the outside, in px (≈ Euclidean); 0 outside. */
export function insideDistance(mk) {
  const { w, h } = mk;
  const INF = 1e9;
  const d = new Float64Array(w * h);
  for (let i = 0; i < w * h; i++) d[i] = mk.m[i] ? INF : 0;
  const at = (x, y) => (x < 0 || y < 0 || x >= w || y >= h ? 0 : d[y * w + x]);
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const i = y * w + x;
      if (!d[i]) continue;
      d[i] = Math.min(d[i], at(x - 1, y) + 3, at(x, y - 1) + 3, at(x - 1, y - 1) + 4, at(x + 1, y - 1) + 4);
    }
  for (let y = h - 1; y >= 0; y--)
    for (let x = w - 1; x >= 0; x--) {
      const i = y * w + x;
      if (!d[i]) continue;
      d[i] = Math.min(d[i], at(x + 1, y) + 3, at(x, y + 1) + 3, at(x + 1, y + 1) + 4, at(x - 1, y + 1) + 4);
    }
  for (let i = 0; i < w * h; i++) d[i] /= 3;
  return { w, h, d, at: (x, y) => (x < 0 || y < 0 || x >= w || y >= h ? 0 : d[y * w + x]) };
}

/** Deterministic hash noise in [0, 1) for (x, y, seed). */
export function noise(x, y, seed = 0) {
  let n = (x * 374761393 + y * 668265263 + seed * 2147483647) | 0;
  n = Math.imul(n ^ (n >>> 13), 1274126177);
  return ((n ^ (n >>> 16)) >>> 0) / 4294967296;
}

/** Ordered 4×4 Bayer threshold. */
const BAYER = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5];
export const bayer = (x, y) => (BAYER[(y & 3) * 4 + (x & 3)] + 0.5) / 16;

/** Nine-slice sanity: every border row/column must be tileable — returns the image unchanged (documentation aid). */
export const nine = (img) => img;

/** Paints a shape mask with a bevelled fill: base inside, light on the top-left edge, dark on the bottom-right edge. */
export function paintMask(img, mk, ox, oy, { base, light, dark, outlineC }) {
  for (let y = 0; y < mk.h; y++)
    for (let x = 0; x < mk.w; x++) {
      if (!mk.at(x, y)) {
        if (outlineC && (mk.at(x - 1, y) || mk.at(x + 1, y) || mk.at(x, y - 1) || mk.at(x, y + 1))) P(img, ox + x, oy + y, outlineC);
        continue;
      }
      let c = base;
      if (dark && (!mk.at(x + 1, y) || !mk.at(x, y + 1))) c = dark;
      else if (light && (!mk.at(x - 1, y) || !mk.at(x, y - 1)) && mk.at(x + 1, y) && mk.at(x, y + 1)) c = light;
      P(img, ox + x, oy + y, c);
    }
  return img;
}

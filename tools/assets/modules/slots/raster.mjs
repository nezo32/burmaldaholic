// Slots art: small raster toolkit on top of lib/grid.mjs images ({w, h, data: RGBA8}). Deterministic.
// Everything here is plain pixel maths (nearest sampling, hard edges) so the output stays pixel art.
// Candidates for lib/transforms.mjs (lane X-L0) once that exists; kept module-local until then.
import { image } from '../../lib/grid.mjs';
import { rgba } from '../../lib/palette.mjs';

export { image };

export const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
export const col = (hex) => rgba(hex);

/** Mulberry32 (same family as core/logic/anim/seed.ts FxRng): seeded cosmetic noise only. */
export function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function get(img, x, y) {
  if (x < 0 || y < 0 || x >= img.w || y >= img.h) return [0, 0, 0, 0];
  const o = (y * img.w + x) * 4;
  return [img.data[o], img.data[o + 1], img.data[o + 2], img.data[o + 3]];
}
export const alphaAt = (img, x, y) => (x < 0 || y < 0 || x >= img.w || y >= img.h ? 0 : img.data[(y * img.w + x) * 4 + 3]);

/** Writes (replace) a pixel. */
export function put(img, x, y, c) {
  x |= 0;
  y |= 0;
  if (x < 0 || y < 0 || x >= img.w || y >= img.h) return;
  const o = (y * img.w + x) * 4;
  img.data[o] = c[0];
  img.data[o + 1] = c[1];
  img.data[o + 2] = c[2];
  img.data[o + 3] = c[3] ?? 255;
}

/** Source-over blend of colour c with alpha multiplier k (0..1). */
export function blend(img, x, y, c, k = 1) {
  x |= 0;
  y |= 0;
  if (x < 0 || y < 0 || x >= img.w || y >= img.h) return;
  const a = ((c[3] ?? 255) / 255) * k;
  if (a <= 0) return;
  const o = (y * img.w + x) * 4;
  const da = img.data[o + 3] / 255;
  const oa = a + da * (1 - a);
  if (oa <= 0) return;
  for (let i = 0; i < 3; i++) img.data[o + i] = Math.round((c[i] * a + img.data[o + i] * da * (1 - a)) / oa);
  img.data[o + 3] = Math.round(oa * 255);
}

/** Additive light (keeps alpha; used for glints on opaque pixels). */
export function light(img, x, y, c, k = 1) {
  x |= 0;
  y |= 0;
  if (x < 0 || y < 0 || x >= img.w || y >= img.h) return;
  const o = (y * img.w + x) * 4;
  if (img.data[o + 3] === 0) return;
  for (let i = 0; i < 3; i++) img.data[o + i] = clamp(Math.round(img.data[o + i] + c[i] * k), 0, 255);
}

export function fill(img, x0, y0, w, h, c, k = 1) {
  for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) (k >= 1 && (c[3] ?? 255) === 255 ? put : blend)(img, x, y, c, k);
  return img;
}

export function clone(img) {
  return { w: img.w, h: img.h, data: new Uint8Array(img.data) };
}

/** Source-over composite of src onto dst at (x, y) with an alpha multiplier. */
export function over(dst, src, x = 0, y = 0, k = 1) {
  for (let j = 0; j < src.h; j++)
    for (let i = 0; i < src.w; i++) {
      const o = (j * src.w + i) * 4;
      if (src.data[o + 3] === 0) continue;
      blend(dst, x + i, y + j, [src.data[o], src.data[o + 1], src.data[o + 2], src.data[o + 3]], k);
    }
  return dst;
}

/** Crop a rectangle. */
export function crop(img, x0, y0, w, h) {
  const out = image(w, h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) put(out, x, y, get(img, x0 + x, y0 + y));
  return out;
}

/** Integer nearest upscale. */
export function scaleUp(img, s) {
  const out = image(img.w * s, img.h * s);
  for (let y = 0; y < out.h; y++) for (let x = 0; x < out.w; x++) put(out, x, y, get(img, (x / s) | 0, (y / s) | 0));
  return out;
}

/** Nearest box downscale by an integer factor (majority-free: picks the most opaque sample). */
export function scaleDown(img, s) {
  const out = image((img.w / s) | 0, (img.h / s) | 0);
  for (let y = 0; y < out.h; y++)
    for (let x = 0; x < out.w; x++) {
      let best = [0, 0, 0, 0];
      for (let j = 0; j < s; j++)
        for (let i = 0; i < s; i++) {
          const p = get(img, x * s + i, y * s + j);
          if (p[3] > best[3]) best = p;
        }
      put(out, x, y, best);
    }
  return out;
}

/**
 * Affine render of a small source (the 16 × 16 art) into a w × h target: target = pivot + R·S·(src − srcPivot) + d.
 * Nearest sampling at pixel centres keeps hard edges. `tf`: {scale, sx, sy, rot (deg), dx, dy, shear (top rows
 * shift, target px at the top edge), px, py (source pivot, default centre-bottom), ox, oy (target position of the
 * source pivot)}.
 */
export function affine(src, w, h, tf = {}) {
  const out = image(w, h);
  const sc = tf.scale ?? 1;
  const sx = sc * (tf.sx ?? 1);
  const sy = sc * (tf.sy ?? 1);
  const a = ((tf.rot ?? 0) * Math.PI) / 180;
  const cos = Math.cos(a);
  const sin = Math.sin(a);
  const px = tf.px ?? src.w / 2;
  const py = tf.py ?? src.h;
  const ox = (tf.ox ?? w / 2) + (tf.dx ?? 0);
  const oy = (tf.oy ?? h - (h - src.h * sc) / 2) + (tf.dy ?? 0);
  const shear = tf.shear ?? 0;
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      let X = x + 0.5 - ox;
      const Y = y + 0.5 - oy;
      // shear: rows near the top of the source move by `shear` px (linear in height)
      if (shear) X -= shear * clamp(-Y / (src.h * sy), 0, 1);
      const u = (cos * X + sin * Y) / sx + px;
      const v = (-sin * X + cos * Y) / sy + py;
      const iu = Math.floor(u);
      const iv = Math.floor(v);
      if (iu < 0 || iv < 0 || iu >= src.w || iv >= src.h) continue;
      const p = get(src, iu, iv);
      if (p[3]) put(out, x, y, p);
    }
  return out;
}

/** Morphological outline: transparent pixels 4-adjacent (or 8 with `diag`) to opaque ones get colour c. */
export function outline(img, c, { diag = false, k = 1 } = {}) {
  const out = clone(img);
  for (let y = 0; y < img.h; y++)
    for (let x = 0; x < img.w; x++) {
      if (alphaAt(img, x, y) > 0) continue;
      let hit = alphaAt(img, x - 1, y) || alphaAt(img, x + 1, y) || alphaAt(img, x, y - 1) || alphaAt(img, x, y + 1);
      if (!hit && diag) hit = alphaAt(img, x - 1, y - 1) || alphaAt(img, x + 1, y - 1) || alphaAt(img, x - 1, y + 1) || alphaAt(img, x + 1, y + 1);
      if (hit) blend(out, x, y, c, k);
    }
  return out;
}

/** Silhouette copy in colour c (alpha from the source, × k). */
export function silhouette(img, c, k = 1) {
  const out = image(img.w, img.h);
  for (let y = 0; y < img.h; y++)
    for (let x = 0; x < img.w; x++) {
      const a = alphaAt(img, x, y);
      if (a) put(out, x, y, [c[0], c[1], c[2], Math.round(a * k)]);
    }
  return out;
}

/** Drop shadow: silhouette offset by (dx, dy) under the image. */
export function shadow(img, c, dx, dy, k) {
  const out = image(img.w, img.h);
  over(out, silhouette(img, c, k), dx, dy);
  return over(out, img);
}

/** Soft halo around the silhouette: rings at distance 1..r (quantised, pixel-art look) with alpha falling off. */
export function halo(img, c, r, k = 1) {
  const out = image(img.w, img.h);
  for (let y = 0; y < img.h; y++)
    for (let x = 0; x < img.w; x++) {
      if (alphaAt(img, x, y) > 0) continue;
      let best = 1e9;
      for (let j = -r; j <= r; j++) for (let i = -r; i <= r; i++) if (alphaAt(img, x + i, y + j) > 0) best = Math.min(best, Math.hypot(i, j));
      if (best <= r) blend(out, x, y, c, k * (1 - (Math.ceil(best) - 1) / r) * 0.85);
    }
  return over(out, img);
}

/** Multiplies RGB (brightness) of every opaque pixel; f > 1 brightens toward white. */
export function brighten(img, f) {
  const out = clone(img);
  for (let i = 0; i < out.data.length; i += 4) {
    if (!out.data[i + 3]) continue;
    for (let c = 0; c < 3; c++) {
      const v = out.data[i + c];
      out.data[i + c] = f >= 1 ? clamp(Math.round(v + (255 - v) * (f - 1)), 0, 255) : clamp(Math.round(v * f), 0, 255);
    }
  }
  return out;
}

/** Shifts every opaque pixel toward colour c by t (0..1). */
export function tint(img, c, t) {
  const out = clone(img);
  for (let i = 0; i < out.data.length; i += 4) {
    if (!out.data[i + 3]) continue;
    for (let k = 0; k < 3; k++) out.data[i + k] = Math.round(out.data[i + k] * (1 - t) + c[k] * t);
  }
  return out;
}

/** Desaturate toward luminance by t. */
export function desaturate(img, t) {
  const out = clone(img);
  for (let i = 0; i < out.data.length; i += 4) {
    if (!out.data[i + 3]) continue;
    const l = 0.3 * out.data[i] + 0.59 * out.data[i + 1] + 0.11 * out.data[i + 2];
    for (let k = 0; k < 3; k++) out.data[i + k] = Math.round(out.data[i + k] * (1 - t) + l * t);
  }
  return out;
}

/** Multiplies alpha. */
export function fade(img, k) {
  const out = clone(img);
  for (let i = 3; i < out.data.length; i += 4) out.data[i] = Math.round(out.data[i] * k);
  return out;
}

/** Diagonal shine band (top-left → bottom-right sweep): `pos` in [0, 1] across the diagonal, width in px. */
export function shine(img, pos, width, k = 0.6, c = [255, 255, 255, 255]) {
  const out = clone(img);
  const span = img.w + img.h;
  const centre = -width + pos * (span + 2 * width);
  for (let y = 0; y < img.h; y++)
    for (let x = 0; x < img.w; x++) {
      const d = Math.abs(x + y - centre);
      if (d > width / 2) continue;
      const kk = d < width / 4 ? k : k * 0.5;
      const o = (y * img.w + x) * 4;
      if (!out.data[o + 3]) continue;
      for (let i = 0; i < 3; i++) out.data[o + i] = clamp(Math.round(out.data[o + i] + (c[i] - out.data[o + i]) * kk), 0, 255);
    }
  return out;
}

/** 4-point star glint of radius r (1..3) at (x, y). */
export function star(img, x, y, r, c, k = 1) {
  blend(img, x, y, [255, 255, 255, 255], k);
  for (let i = 1; i <= r; i++) {
    const kk = k * (i === r ? 0.55 : 0.9);
    const cc = i === 1 ? [255, 255, 255, 255] : c;
    blend(img, x + i, y, cc, kk);
    blend(img, x - i, y, cc, kk);
    blend(img, x, y + i, cc, kk);
    blend(img, x, y - i, cc, kk);
  }
  if (r >= 2) for (const [i, j] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) blend(img, x + i, y + j, c, k * 0.35);
}

/** Pixel circle ring (radius r, thickness t) centred at (cx, cy). */
export function ring(img, cx, cy, r, t, c, k = 1) {
  for (let y = Math.floor(cy - r - 1); y <= cy + r + 1; y++)
    for (let x = Math.floor(cx - r - 1); x <= cx + r + 1; x++) {
      const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
      if (d <= r && d > r - t) blend(img, x, y, c, k);
    }
}

/** Filled disc. */
export function disc(img, cx, cy, r, c, k = 1) {
  for (let y = Math.floor(cy - r - 1); y <= cy + r + 1; y++)
    for (let x = Math.floor(cx - r - 1); x <= cx + r + 1; x++) if (Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r) blend(img, x, y, c, k);
}

/** Bresenham line. */
export function line(img, x0, y0, x1, y1, c, k = 1) {
  x0 |= 0;
  y0 |= 0;
  x1 |= 0;
  y1 |= 0;
  const dx = Math.abs(x1 - x0);
  const dy = -Math.abs(y1 - y0);
  const sx = x0 < x1 ? 1 : -1;
  const sy = y0 < y1 ? 1 : -1;
  let err = dx + dy;
  for (;;) {
    blend(img, x0, y0, c, k);
    if (x0 === x1 && y0 === y1) break;
    const e2 = 2 * err;
    if (e2 >= dy) {
      err += dy;
      x0 += sx;
    }
    if (e2 <= dx) {
      err += dx;
      y0 += sy;
    }
  }
}

/** Vertical gradient fill between colours a (top) and b (bottom), quantised to `steps` bands (pixel-art look). */
export function vgrad(img, x0, y0, w, h, a, b, steps = 8) {
  for (let y = 0; y < h; y++) {
    const t = Math.floor((y / Math.max(1, h - 1)) * (steps - 1) + 0.5) / Math.max(1, steps - 1);
    const c = [0, 1, 2].map((i) => Math.round(a[i] + (b[i] - a[i]) * t));
    for (let x = 0; x < w; x++) put(img, x0 + x, y0 + y, [...c, 255]);
  }
  return img;
}

/** Ordered 4×4 Bayer dither threshold (0..1). */
const BAYER = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5];
export const bayer = (x, y) => (BAYER[(y & 3) * 4 + (x & 3)] + 0.5) / 16;

/** Dithered darkening of a rectangle by `amount(y)` (0..1): pixels whose Bayer threshold is below it darken by f. */
export function ditherShade(img, x0, y0, w, h, amount, f = 0.55) {
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const t = amount(y, x);
      if (t <= 0 || bayer(x0 + x, y0 + y) >= t) continue;
      const o = ((y0 + y) * img.w + x0 + x) * 4;
      if (o < 0 || o >= img.data.length || !img.data[o + 3]) continue;
      for (let i = 0; i < 3; i++) img.data[o + i] = Math.round(img.data[o + i] * f);
    }
  return img;
}

/** Bounding box of opaque pixels. */
export function bounds(img) {
  let x0 = img.w;
  let y0 = img.h;
  let x1 = -1;
  let y1 = -1;
  for (let y = 0; y < img.h; y++)
    for (let x = 0; x < img.w; x++)
      if (alphaAt(img, x, y)) {
        x0 = Math.min(x0, x);
        y0 = Math.min(y0, y);
        x1 = Math.max(x1, x);
        y1 = Math.max(y1, y);
      }
  return { x0, y0, x1, y1 };
}

/** Horizontal frame sheet (frames left → right). */
export function hstrip(frames) {
  const out = image(frames.reduce((s, f) => s + f.w, 0), Math.max(...frames.map((f) => f.h)));
  let x = 0;
  for (const f of frames) {
    over(out, f, x, 0);
    x += f.w;
  }
  return out;
}

/** Vertical frame sheet (Java .mcmeta order). */
export function vstrip(frames) {
  const out = image(Math.max(...frames.map((f) => f.w)), frames.reduce((s, f) => s + f.h, 0));
  let y = 0;
  for (const f of frames) {
    over(out, f, 0, y);
    y += f.h;
  }
  return out;
}

/** Tiny 3 × 5 digit font for the allowed digit plates (ladder, wheel); returns the drawn width. */
const DIGITS = {
  0: ['###', '#.#', '#.#', '#.#', '###'],
  1: ['.#.', '##.', '.#.', '.#.', '###'],
  2: ['###', '..#', '###', '#..', '###'],
  3: ['###', '..#', '.##', '..#', '###'],
  4: ['#.#', '#.#', '###', '..#', '..#'],
  5: ['###', '#..', '###', '..#', '###'],
  6: ['###', '#..', '###', '#.#', '###'],
  7: ['###', '..#', '.#.', '.#.', '.#.'],
  8: ['###', '#.#', '###', '#.#', '###'],
  9: ['###', '#.#', '###', '..#', '###'],
  x: ['...', '#.#', '.#.', '#.#', '...'],
};
export function digits(img, text, x, y, c, s = 1) {
  let cx = x;
  for (const ch of String(text)) {
    const g = DIGITS[ch];
    if (!g) continue;
    g.forEach((row, j) => [...row].forEach((p, i) => p === '#' && fill(img, cx + i * s, y + j * s, s, s, c)));
    cx += 4 * s;
  }
  return cx - x - s;
}
export const digitsWidth = (text, s = 1) => String(text).length * 4 * s - s;

// Deterministic image transforms that build animation frames from one drawing (animation/slots.md §3.2:
// blur, shine band, palette swap, squash; plus the generic ones every module needs). Pure functions:
// each returns a NEW image and never mutates its input.
import { image } from './grid.mjs';
import { rgba } from './palette.mjs';

export const clone = (img) => ({ w: img.w, h: img.h, data: new Uint8Array(img.data) });

const at = (img, x, y) => (y * img.w + x) * 4;

/** Palette swap: exact colour matches `{from: to}` (both '#hex'/tokens; alpha kept from the source unless `to` has one). */
export function recolor(img, map) {
  const out = clone(img);
  const pairs = Object.entries(map).map(([a, b]) => [rgba(a), rgba(b)]);
  for (let i = 0; i < out.data.length; i += 4) {
    if (out.data[i + 3] === 0) continue;
    for (const [f, t] of pairs)
      if (out.data[i] === f[0] && out.data[i + 1] === f[1] && out.data[i + 2] === f[2]) {
        out.data.set(t.slice(0, 3), i);
        break;
      }
  }
  return out;
}

/** Multiplies every opaque pixel by a colour (white art → tinted art). */
export function tint(img, c) {
  const [r, g, b] = rgba(c);
  const out = clone(img);
  for (let i = 0; i < out.data.length; i += 4) {
    out.data[i] = Math.round((out.data[i] * r) / 255);
    out.data[i + 1] = Math.round((out.data[i + 1] * g) / 255);
    out.data[i + 2] = Math.round((out.data[i + 2] * b) / 255);
  }
  return out;
}

/** Scales alpha by k (0..1). */
export function fade(img, k) {
  const out = clone(img);
  for (let i = 3; i < out.data.length; i += 4) out.data[i] = Math.round(out.data[i] * k);
  return out;
}

/** Nearest-neighbour integer upscale. */
export function scale(img, k) {
  const out = image(img.w * k, img.h * k);
  for (let y = 0; y < out.h; y++)
    for (let x = 0; x < out.w; x++) out.data.set(img.data.subarray(at(img, (x / k) | 0, (y / k) | 0), at(img, (x / k) | 0, (y / k) | 0) + 4), at(out, x, y));
  return out;
}

/** Horizontal mirror. */
export function flipH(img) {
  const out = image(img.w, img.h);
  for (let y = 0; y < img.h; y++)
    for (let x = 0; x < img.w; x++) out.data.set(img.data.subarray(at(img, img.w - 1 - x, y), at(img, img.w - 1 - x, y) + 4), at(out, x, y));
  return out;
}

/** Quarter turns clockwise (square images keep their size). */
export function rotate90(img, turns = 1) {
  let cur = img;
  for (let t = 0; t < ((turns % 4) + 4) % 4; t++) {
    const out = image(cur.h, cur.w);
    for (let y = 0; y < cur.h; y++)
      for (let x = 0; x < cur.w; x++) out.data.set(cur.data.subarray(at(cur, x, y), at(cur, x, y) + 4), at(out, cur.h - 1 - y, x));
    cur = out;
  }
  return cur === img ? clone(img) : cur;
}

/** Sub-image (clipped). */
export function crop(img, x0, y0, w, h) {
  const out = image(w, h);
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const sx = x0 + x;
      const sy = y0 + y;
      if (sx < 0 || sy < 0 || sx >= img.w || sy >= img.h) continue;
      out.data.set(img.data.subarray(at(img, sx, sy), at(img, sx, sy) + 4), at(out, x, y));
    }
  return out;
}

/** 1 px outline around opaque pixels (4-neighbourhood), drawn under the art. */
export function outline(img, c) {
  const [r, g, b, a] = rgba(c);
  const out = clone(img);
  for (let y = 0; y < img.h; y++)
    for (let x = 0; x < img.w; x++) {
      if (img.data[at(img, x, y) + 3] !== 0) continue;
      const n = [
        [x - 1, y],
        [x + 1, y],
        [x, y - 1],
        [x, y + 1],
      ].some(([i, j]) => i >= 0 && j >= 0 && i < img.w && j < img.h && img.data[at(img, i, j) + 3] !== 0);
      if (n) out.data.set([r, g, b, a], at(out, x, y));
    }
  return out;
}

/**
 * Vertical motion blur (reel blur, falling things): each pixel = average of `radius` pixels above and below,
 * alpha averaged too. Wraps vertically when `wrap` (reel strips loop).
 */
export function blurV(img, radius, wrap = false) {
  const out = image(img.w, img.h);
  for (let y = 0; y < img.h; y++)
    for (let x = 0; x < img.w; x++) {
      const acc = [0, 0, 0, 0];
      let n = 0;
      for (let k = -radius; k <= radius; k++) {
        let yy = y + k;
        if (wrap) yy = ((yy % img.h) + img.h) % img.h;
        else if (yy < 0 || yy >= img.h) continue;
        const o = at(img, x, yy);
        const a = img.data[o + 3];
        acc[0] += img.data[o] * a;
        acc[1] += img.data[o + 1] * a;
        acc[2] += img.data[o + 2] * a;
        acc[3] += a;
        n++;
      }
      if (!acc[3]) continue;
      out.data.set([acc[0] / acc[3], acc[1] / acc[3], acc[2] / acc[3], acc[3] / n].map(Math.round), at(out, x, y));
    }
  return out;
}

/**
 * Diagonal shine band (badge sweeps, glass sheen): pixels with `pos − w/2 ≤ x + y·slope ≤ pos + w/2` are lerped
 * toward `c` by `strength`. Only opaque pixels are touched.
 */
export function shineBand(img, pos, width, c = '#FFFFFF', strength = 0.6, slope = 1) {
  const [r, g, b] = rgba(c);
  const out = clone(img);
  for (let y = 0; y < img.h; y++)
    for (let x = 0; x < img.w; x++) {
      const o = at(out, x, y);
      if (!out.data[o + 3]) continue;
      const d = Math.abs(x + y * slope - pos);
      if (d > width / 2) continue;
      const k = strength * (1 - d / (width / 2 + 0.5));
      out.data[o] += (r - out.data[o]) * k;
      out.data[o + 1] += (g - out.data[o + 1]) * k;
      out.data[o + 2] += (b - out.data[o + 2]) * k;
    }
  return out;
}

/**
 * Squash/stretch around the bottom centre (land pops, slots.md §3.2): output keeps the input size,
 * content scaled by (sx, sy) with nearest sampling.
 */
export function squash(img, sx, sy) {
  const out = image(img.w, img.h);
  const cx = img.w / 2;
  for (let y = 0; y < img.h; y++)
    for (let x = 0; x < img.w; x++) {
      const u = Math.floor(cx + (x + 0.5 - cx) / sx);
      const v = Math.floor(img.h - (img.h - y - 0.5) / sy);
      if (u < 0 || v < 0 || u >= img.w || v >= img.h) continue;
      out.data.set(img.data.subarray(at(img, u, v), at(img, u, v) + 4), at(out, x, y));
    }
  return out;
}

/** Rotation by any angle (degrees, clockwise) around the centre, nearest sampling, same size. */
export function rotate(img, deg) {
  const out = image(img.w, img.h);
  const a = (-deg * Math.PI) / 180;
  const c = Math.cos(a);
  const s = Math.sin(a);
  const cx = img.w / 2;
  const cy = img.h / 2;
  for (let y = 0; y < img.h; y++)
    for (let x = 0; x < img.w; x++) {
      const dx = x + 0.5 - cx;
      const dy = y + 0.5 - cy;
      const u = Math.floor(cx + dx * c - dy * s);
      const v = Math.floor(cy + dx * s + dy * c);
      if (u < 0 || v < 0 || u >= img.w || v >= img.h) continue;
      out.data.set(img.data.subarray(at(img, u, v), at(img, u, v) + 4), at(out, x, y));
    }
  return out;
}

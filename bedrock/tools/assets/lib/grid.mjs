// Pixel art as string grids (docs/branding/gen_logo.py style): one char per pixel, '.' = transparent,
// other chars map to palette colours through a legend. Deterministic transforms build animation frames.
// Also the raster primitives every asset module uses (rect, frame, ellipse, line, strips).
import { rgba } from './palette.mjs';

export function image(w, h) {
  return { w, h, data: new Uint8Array(w * h * 4) };
}

/** Draws `rows` (array of equal-length strings) at (x, y) using `legend` {char: '#hex'}; scale ≥ 1. */
export function drawGrid(img, rows, legend, x = 0, y = 0, scale = 1) {
  rows.forEach((row, j) => {
    [...row].forEach((ch, i) => {
      if (ch === '.' || ch === ' ') return;
      const hex = legend[ch];
      if (!hex) throw new Error(`grid: no colour for '${ch}'`);
      const [r, g, b, a] = rgba(hex);
      for (let dy = 0; dy < scale; dy++)
        for (let dx = 0; dx < scale; dx++) {
          const px = x + i * scale + dx;
          const py = y + j * scale + dy;
          if (px < 0 || py < 0 || px >= img.w || py >= img.h) continue;
          const o = (py * img.w + px) * 4;
          img.data[o] = r;
          img.data[o + 1] = g;
          img.data[o + 2] = b;
          img.data[o + 3] = a;
        }
    });
  });
  return img;
}

/** A new image from a grid (size = grid size × scale). */
export function fromGrid(rows, legend, scale = 1) {
  return drawGrid(image(rows[0].length * scale, rows.length * scale), rows, legend, 0, 0, scale);
}

/** Copies `src` into `dst` at (x, y) (frame strips, atlases, glyph sheets). Transparent source pixels are skipped. */
export function blit(dst, src, x, y) {
  for (let j = 0; j < src.h; j++)
    for (let i = 0; i < src.w; i++) {
      const s = (j * src.w + i) * 4;
      if (src.data[s + 3] === 0) continue;
      const px = x + i;
      const py = y + j;
      if (px < 0 || py < 0 || px >= dst.w || py >= dst.h) continue;
      dst.data.set(src.data.subarray(s, s + 4), (py * dst.w + px) * 4);
    }
  return dst;
}

/** Vertical strip of frames (Java `.mcmeta` animation / Bedrock flipbook). */
export function strip(frames) {
  const w = frames[0].w;
  const out = image(w, frames.reduce((s, f) => s + f.h, 0));
  let y = 0;
  for (const f of frames) {
    blit(out, f, 0, y);
    y += f.h;
  }
  return out;
}

/** Horizontal strip of frames (Bedrock particle atlas rows, code-indexed sheets). */
export function hstrip(frames) {
  const h = frames[0].h;
  const out = image(frames.reduce((s, f) => s + f.w, 0), h);
  let x = 0;
  for (const f of frames) {
    blit(out, f, x, 0);
    x += f.w;
  }
  return out;
}

// ---- raster primitives (clip to the image; colours are '#RRGGBB[AA]' strings; writes replace alpha) ----

/** Sets one pixel. A falsy colour is a no-op (handy for colour functions). */
export function setPx(img, x, y, hex) {
  if (!hex || x < 0 || y < 0 || x >= img.w || y >= img.h) return img;
  img.data.set(rgba(hex), (y * img.w + x) * 4);
  return img;
}

/** Reads one pixel as [r,g,b,a] (transparent black outside the image). */
export function getPx(img, x, y) {
  if (x < 0 || y < 0 || x >= img.w || y >= img.h) return [0, 0, 0, 0];
  const o = (y * img.w + x) * 4;
  return [...img.data.subarray(o, o + 4)];
}

/** Filled rectangle, inclusive corners. */
export function rect(img, x0, y0, x1, y1, hex) {
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) setPx(img, x, y, hex);
  return img;
}

/** 1 px rectangle outline, inclusive corners. */
export function frame(img, x0, y0, x1, y1, hex) {
  for (let x = x0; x <= x1; x++) {
    setPx(img, x, y0, hex);
    setPx(img, x, y1, hex);
  }
  for (let y = y0; y <= y1; y++) {
    setPx(img, x0, y, hex);
    setPx(img, x1, y, hex);
  }
  return img;
}

/**
 * Filled ellipse centred on (cx, cy) (pixel units; pixel centres at +0.5) with radii (rx, ry).
 * `hexOrFn` is a colour or `(x, y, d) => colour` with d = normalised distance 0..1 from the centre.
 */
export function ellipse(img, cx, cy, rx, ry, hexOrFn) {
  for (let y = Math.floor(cy - ry - 1); y <= Math.ceil(cy + ry + 1); y++)
    for (let x = Math.floor(cx - rx - 1); x <= Math.ceil(cx + rx + 1); x++) {
      const d = Math.hypot((x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry);
      if (d > 1) continue;
      setPx(img, x, y, typeof hexOrFn === 'function' ? hexOrFn(x, y, d) : hexOrFn);
    }
  return img;
}

/** Bresenham line (inclusive ends). */
export function line(img, x0, y0, x1, y1, hex) {
  const dx = Math.abs(x1 - x0);
  const dy = -Math.abs(y1 - y0);
  const sx = x0 < x1 ? 1 : -1;
  const sy = y0 < y1 ? 1 : -1;
  let err = dx + dy;
  for (;;) {
    setPx(img, x0, y0, hex);
    if (x0 === x1 && y0 === y1) return img;
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

/** Per-pixel painter: `fn(x, y) => colour | undefined` over the whole image (gradients, dithers). */
export function paint(img, fn) {
  for (let y = 0; y < img.h; y++) for (let x = 0; x < img.w; x++) setPx(img, x, y, fn(x, y));
  return img;
}

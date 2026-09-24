// Pixel art as string grids (docs/branding/gen_logo.py style): one char per pixel, '.' = transparent,
// other chars map to palette colours through a legend. Deterministic transforms build animation frames.
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

/** Copies `src` into `dst` at (x, y) (frame strips, atlases, glyph sheets). */
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

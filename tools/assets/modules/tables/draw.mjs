// Table art drawing kit: the slots raster toolkit (nearest, hard edges) plus what tables need on top of it:
// hex-string wrappers, bevelled plates, digit fonts (digits and ':' / '–' only; no words are ever baked),
// polygon fill for the 3D die tumble, and string-grid sprites. Deterministic, pure.
import { drawGrid } from '../../lib/grid.mjs';
import { alphaAt, blend, col, fill, image, put } from '../slots/raster.mjs';

export * from '../slots/raster.mjs';
export { drawGrid };

export const c = col;
/** Filled rectangle (x, y, w, h) in a hex colour, optional alpha multiplier. */
export const R = (img, x, y, w, h, hex, k = 1) => fill(img, x, y, w, h, c(hex), k);
export const P = (img, x, y, hex, k = 1) => (k >= 1 ? put(img, x, y, c(hex)) : blend(img, x, y, c(hex), k));

/** 1 px bevel frame: light top/left, dark bottom/right. */
export function bevel(img, x, y, w, h, light, dark) {
  R(img, x, y, w, 1, light);
  R(img, x, y, 1, h, light);
  R(img, x, y + h - 1, w, 1, dark);
  R(img, x + w - 1, y, 1, h, dark);
}

/** 1 px frame in one colour. */
export function box(img, x, y, w, h, hex, k = 1) {
  R(img, x, y, w, 1, hex, k);
  R(img, x, y + h - 1, w, 1, hex, k);
  R(img, x, y, 1, h, hex, k);
  R(img, x + w - 1, y, 1, h, hex, k);
}

/** Rounded rectangle (corner radius 1 or 2 px, pixel-art corners). */
export function rrect(img, x, y, w, h, hex, r = 1, k = 1) {
  for (let j = 0; j < h; j++) {
    const inset = r === 0 ? 0 : j < r ? r - j : j >= h - r ? j - (h - r - 1) : 0;
    R(img, x + inset, y + j, w - 2 * inset, 1, hex, k);
  }
}

/** Filled ellipse (replace, pixel centres) in a hex colour. */
export function ellipseFill(img, cx, cy, rx, ry, hex) {
  for (let y = Math.floor(cy - ry - 1); y <= cy + ry + 1; y++)
    for (let x = Math.floor(cx - rx - 1); x <= cx + rx + 1; x++) if (Math.hypot((x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry) <= 1) put(img, x, y, c(hex));
}

/** Filled convex/concave polygon (even-odd, pixel centres). pts = [[x,y],…]. */
export function polygon(img, pts, hexOrFn, k = 1) {
  const ys = pts.map((p) => p[1]);
  const y0 = Math.floor(Math.min(...ys));
  const y1 = Math.ceil(Math.max(...ys));
  for (let y = y0; y <= y1; y++) {
    const yc = y + 0.5;
    const xs = [];
    for (let i = 0; i < pts.length; i++) {
      const [ax, ay] = pts[i];
      const [bx, by] = pts[(i + 1) % pts.length];
      if (ay <= yc === by <= yc) continue;
      xs.push(ax + ((yc - ay) / (by - ay)) * (bx - ax));
    }
    xs.sort((a, b) => a - b);
    for (let i = 0; i + 1 < xs.length; i += 2)
      for (let x = Math.ceil(xs[i] - 0.5); x <= Math.floor(xs[i + 1] - 0.5); x++) {
        const hex = typeof hexOrFn === 'function' ? hexOrFn(x, y) : hexOrFn;
        if (hex) blend(img, x, y, c(hex), k);
      }
  }
}

/** String-grid sprite with a legend (tokens or hex). */
export const grid = (rows, legend) => drawGrid(image(rows[0].length, rows.length), rows, legend);

// ---- digit fonts -----------------------------------------------------------------------------------------------------

/** Bold 5 × 7 casino digits (layout numbers, place boxes). */
const BOLD = {
  0: ['.###.', '##.##', '##.##', '##.##', '##.##', '##.##', '.###.'],
  1: ['..##.', '.###.', '..##.', '..##.', '..##.', '..##.', '.####'],
  2: ['.###.', '##.##', '...##', '..##.', '.##..', '##...', '#####'],
  3: ['####.', '...##', '...##', '.###.', '...##', '...##', '####.'],
  4: ['...##', '..###', '.#.##', '##.##', '#####', '...##', '...##'],
  5: ['#####', '##...', '####.', '...##', '...##', '##.##', '.###.'],
  6: ['.###.', '##...', '##...', '####.', '##.##', '##.##', '.###.'],
  7: ['#####', '...##', '...##', '..##.', '..##.', '.##..', '.##..'],
  8: ['.###.', '##.##', '##.##', '.###.', '##.##', '##.##', '.###.'],
  9: ['.###.', '##.##', '##.##', '.####', '...##', '...##', '.###.'],
  ':': ['..', '##', '##', '..', '##', '##', '..'],
  '-': ['....', '....', '....', '####', '....', '....', '....'],
};
/** Small 3 × 5 digits (wheel ring, racetrack, compact layout, field odds). */
const SMALL = {
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
  ':': ['.', '#', '.', '#', '.'],
  '-': ['...', '...', '###', '...', '...'],
};
export const FONTS = { bold: { g: BOLD, h: 7, gap: 1 }, small: { g: SMALL, h: 5, gap: 1 } };

export function textWidth(text, font = 'bold', s = 1) {
  const f = FONTS[font];
  let w = 0;
  for (const ch of String(text)) w += (f.g[ch][0].length + f.gap) * s;
  return w - f.gap * s;
}

/** Draws digits at (x, y); `shadow` = colour drawn 1 px (× s) down-right first. Returns the width. */
export function text(img, str, x, y, hex, { font = 'bold', s = 1, shadow, k = 1 } = {}) {
  const f = FONTS[font];
  const pass = (ox, oy, h) => {
    let cx = x + ox;
    for (const ch of String(str)) {
      const g = f.g[ch];
      if (!g) throw new Error(`tables font: no glyph '${ch}'`);
      g.forEach((row, j) => [...row].forEach((p, i) => p === '#' && fill(img, cx + i * s, y + oy + j * s, s, s, c(h), k)));
      cx += (g[0].length + f.gap) * s;
    }
  };
  if (shadow) pass(s, s, shadow);
  pass(0, 0, hex);
  return textWidth(str, font, s);
}

/** Digits centred on (cx, cy). */
export function textC(img, str, cx, cy, hex, opts = {}) {
  const font = opts.font ?? 'bold';
  const s = opts.s ?? 1;
  const w = textWidth(str, font, s);
  const h = FONTS[font].h * s;
  return text(img, str, Math.round(cx - w / 2), Math.round(cy - h / 2), hex, opts);
}

/** 1 px ink outline around every opaque pixel (4-neighbour), on a copy that is 2 px larger each way. */
export function inked(img, ink = '#180A28', k = 1) {
  const out = image(img.w + 2, img.h + 2);
  for (let y = 0; y < img.h; y++)
    for (let x = 0; x < img.w; x++) {
      if (!alphaAt(img, x, y)) continue;
      for (const [dx, dy] of [[0, 1], [2, 1], [1, 0], [1, 2]]) if (!alphaAt(img, x + dx - 1, y + dy - 1)) blend(out, x + dx, y + dy, c(ink), k);
    }
  for (let y = 0; y < img.h; y++)
    for (let x = 0; x < img.w; x++) {
      const o = (y * img.w + x) * 4;
      if (img.data[o + 3]) put(out, x + 1, y + 1, [img.data[o], img.data[o + 1], img.data[o + 2], img.data[o + 3]]);
    }
  return out;
}

/** Pads an image into a w × h canvas at (x, y). */
export function pad(img, w, h, x = (w - img.w) >> 1, y = (h - img.h) >> 1) {
  const out = image(w, h);
  for (let j = 0; j < img.h; j++)
    for (let i = 0; i < img.w; i++) {
      const o = (j * img.w + i) * 4;
      if (img.data[o + 3]) put(out, x + i, y + j, [img.data[o], img.data[o + 1], img.data[o + 2], img.data[o + 3]]);
    }
  return out;
}

/** Light from the top-left: brightness factor for a surface normal angle (deg, 0 = up, clockwise). */
export const lightAt = (deg) => Math.cos(((deg - 315) * Math.PI) / 180);

/** Picks from a light→dark ramp by a light value in [-1, 1]. */
export function ramp(colours, v) {
  const t = (1 - v) / 2; // 0 = brightest
  return colours[Math.min(colours.length - 1, Math.max(0, Math.floor(t * colours.length)))];
}


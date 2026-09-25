// Extras / PvP / menu-shell drawing kit (docs/design/visual/extras.md §2). Pure pixel maths on top of the slots raster
// toolkit (same look: 16-px art at 2× with a 1 px ink outline and a 2 px drop shadow; hard edges; Bayer dithers).
// Every colour is a palette token of lib/palette.mjs or a game hex listed in extras.md §2.1.
import { rgba } from '../../lib/palette.mjs';
import {
  affine, blend, bayer, brighten, clamp, crop, disc, fade, fill, halo, image, light, line, outline, over, put, ring, rng, scaleUp, shadow, shine, star, vgrad,
} from '../slots/raster.mjs';

export {
  affine, blend, bayer, brighten, clamp, crop, disc, fade, fill, halo, image, light, line, outline, over, put, ring, rng, scaleUp, shadow, shine, star, vgrad,
};

/** Token or '#hex' → [r, g, b, a]. */
export const c = (x) => rgba(x);

// Named colours used across the extras art (branding.md palette + extras.md §2.1 game colours).
export const K = {
  ink: '#180A28',
  deep: '#26103C',
  darkest: '#140822',
  frame: '#783CBE',
  glint: '#BE5AFF',
  lilac: '#D696FF',
  bone: '#F4ECF8',
  boneShade: '#C0B0DC',
  gold: '#FFD640',
  goldShade: '#B07010',
  goldLight: '#FFF4B0',
  goldMid: '#E8B830',
  goldDeep: '#7A4A08',
  red: '#D83440',
  redLight: '#FF6E6A',
  redDark: '#8C1834',
  bonus: '#80FF40',
  felt: '#1E5E3A',
  feltDark: '#0E2E1C',
  wood: '#5A3418',
  woodDark: '#3A2010',
  woodLight: '#7A4A24',
  white: '#FFFFFF',
};

export function rect(img, x, y, w, h, hex, k = 1) {
  fill(img, x, y, w, h, c(hex), k);
  return img;
}

/** 1 px bevel: light top/left, dark bottom/right. */
export function bevel(img, x, y, w, h, lightHex, darkHex, k = 1) {
  rect(img, x, y, w, 1, lightHex, k);
  rect(img, x, y, 1, h, lightHex, k);
  rect(img, x, y + h - 1, w, 1, darkHex, k);
  rect(img, x + w - 1, y, 1, h, darkHex, k);
  return img;
}

export function outlineRect(img, x, y, w, h, hex, k = 1) {
  return bevel(img, x, y, w, h, hex, hex, k);
}

/** Clears the four corner pixels of a rectangle (rounded 1 px corners). */
export function roundCorners(img, x, y, w, h, r = 1) {
  const clear = (px, py) => put(img, px, py, [0, 0, 0, 0]);
  for (let i = 0; i < r; i++)
    for (let j = 0; j < r - i; j++) {
      clear(x + i, y + j);
      clear(x + w - 1 - i, y + j);
      clear(x + i, y + h - 1 - j);
      clear(x + w - 1 - i, y + h - 1 - j);
    }
  return img;
}

/**
 * The house gold trim (the slots cabinet language): ink, gold shade, gold with a light top-left bevel, ink inner
 * line. `t` = total thickness (≥ 4).
 */
export function goldTrim(img, x, y, w, h, t = 4, { inner = K.ink } = {}) {
  outlineRect(img, x, y, w, h, K.ink);
  bevel(img, x + 1, y + 1, w - 2, h - 2, K.goldLight, K.goldShade);
  for (let i = 2; i < t - 1; i++) bevel(img, x + i, y + i, w - 2 * i, h - 2 * i, K.gold, K.goldMid);
  if (inner) outlineRect(img, x + t - 1, y + t - 1, w - 2 * (t - 1), h - 2 * (t - 1), inner);
  return img;
}

/** Stud / rivet: a small domed disc with a white hot pixel. */
export function stud(img, x, y, hex, hot = K.white, r = 1.6) {
  disc(img, x + 0.5, y + 0.5, r + 0.7, c(K.ink));
  disc(img, x + 0.5, y + 0.5, r, c(hex));
  put(img, x, y, c(hot));
  return img;
}

/** Light bulb (on / off) centred at (x, y). */
export function bulb(img, x, y, on, hexOn = K.gold, hexOff = '#5A3A1A') {
  if (on) {
    disc(img, x + 0.5, y + 0.5, 3.2, c(hexOn), 0.25);
    disc(img, x + 0.5, y + 0.5, 2.2, c(hexOn));
    put(img, x, y, c(K.white));
    put(img, x - 1, y, c(K.goldLight));
  } else {
    disc(img, x + 0.5, y + 0.5, 2.2, c(K.ink));
    disc(img, x + 0.5, y + 0.5, 1.5, c(hexOff));
  }
  return img;
}

/** Dithered darkening towards the image edges (pixel-art vignette). */
export function vignette(img, strength = 0.5, f = 0.6) {
  for (let y = 0; y < img.h; y++)
    for (let x = 0; x < img.w; x++) {
      const dx = (x + 0.5) / img.w - 0.5;
      const dy = (y + 0.5) / img.h - 0.5;
      const d = Math.hypot(dx * 1.1, dy * 1.4) * 2; // 0 centre … ~1 corners
      const t = clamp((d - 0.55) / 0.6, 0, 1) * strength;
      if (t > 0 && bayer(x, y) < t) {
        const o = (y * img.w + x) * 4;
        for (let i = 0; i < 3; i++) img.data[o + i] = Math.round(img.data[o + i] * f);
      }
    }
  return img;
}

/** 16 × 16 art from a {grid, pal, glow} definition (legend chars → hex; '.' transparent). Not cached. */
export function art16(def, { lit = false } = {}) {
  const img = image(def.grid[0].length, def.grid.length);
  const pal = lit ? { ...def.pal, ...(def.glow ?? {}) } : def.pal;
  def.grid.forEach((row, y) =>
    [...row].forEach((ch, x) => {
      if (ch === '.' || ch === ' ') return;
      const hex = pal[ch];
      if (!hex) throw new Error(`extras art ${def.id}: no colour for '${ch}'`);
      put(img, x, y, c(hex));
    }),
  );
  return img;
}

/** Validates a set of grid definitions: rectangular grids, every char in the legend. */
export function validate(defs) {
  for (const d of defs) {
    const w = d.grid[0].length;
    for (const row of d.grid) if (row.length !== w) throw new Error(`extras art ${d.id}: ragged grid`);
    for (const row of d.grid) for (const ch of row) if (ch !== '.' && !d.pal[ch]) throw new Error(`extras art ${d.id}: '${ch}' not in legend`);
  }
}

/**
 * An icon cell in the house style (from a grid definition or a ready 16 × 16 image): 16-px art × `scale`, 1 px ink outline, optional 2 px shadow, centred in `size`.
 * size 16 → raw art; 18 → 1× with outline; 20 → 1× with outline + 1 px shadow; 32 / 36 / 40 → 2×.
 */
export function iconCell(def, size = 40, { lit = false, glow = null, glowK = 0.7, rot = 0, sx = 1, sy = 1, dy = 0 } = {}) {
  const src = def.data ? def : art16(def, { lit });
  if (size === 16) return src;
  const s = size >= 32 ? 2 : 1;
  let img = affine(src, size, size, { scale: s, sx, sy, rot, px: src.w / 2, py: src.h / 2, ox: size / 2, oy: size / 2 + dy });
  img = outline(img, c(def.rim ?? K.ink), { k: 0.95 });
  if (glow) img = halo(img, c(glow), s >= 2 ? 3 : 2, glowK);
  if (size === 18) return img;
  return shadow(img, c(K.ink), s, s, 0.4);
}

/** Horizontal strip of equal frames. */
export function hstrip(frames) {
  const out = image(frames.reduce((s, f) => s + f.w, 0), Math.max(...frames.map((f) => f.h)));
  let x = 0;
  for (const f of frames) {
    over(out, f, x, 0);
    x += f.w;
  }
  return out;
}

/** Vertical strip (Java `.mcmeta` animation order). */
export function vstrip(frames) {
  const out = image(Math.max(...frames.map((f) => f.w)), frames.reduce((s, f) => s + f.h, 0));
  let y = 0;
  for (const f of frames) {
    over(out, f, 0, y);
    y += f.h;
  }
  return out;
}

/** Grid sheet: rows of frames. */
export function sheet(rows) {
  return vstrip(rows.map(hstrip));
}

/** Generic nine-slice plaque: ink edge, `edge` ring, face with a light top line and dark bottom line, corner studs. */
export function plaque(w, h, { edge, face, light: lt, dark, studs = null, round = 1 } = {}) {
  const img = image(w, h);
  rect(img, 0, 0, w, h, K.ink);
  rect(img, 1, 1, w - 2, h - 2, edge);
  rect(img, 2, 2, w - 4, h - 4, face);
  if (lt) rect(img, 2, 2, w - 4, 1, lt);
  if (dark) rect(img, 2, h - 3, w - 4, 1, dark);
  if (studs) for (const [x, y] of [[3, 3], [w - 4, 3], [3, h - 4], [w - 4, h - 4]]) put(img, x, y, c(studs));
  roundCorners(img, 0, 0, w, h, round);
  return img;
}

/** Mixes two hex colours (t = 0 → a). */
export function mixHex(a, b, t) {
  const x = c(a);
  const y = c(b);
  return `#${[0, 1, 2].map((i) => Math.round(x[i] + (y[i] - x[i]) * t).toString(16).padStart(2, '0')).join('').toUpperCase()}`;
}

/** Hash of a string (seeds). */
export function hash(s) {
  let h = 2166136261;
  for (const ch of s) h = Math.imul(h ^ ch.charCodeAt(0), 16777619);
  return h >>> 0;
}

/** Emboss a mask (alpha > 0 of `mask`) onto `img` at (x, y): raised fill, light top-left rim, dark bottom-right rim. */
export function emboss(img, mask, x, y, { face, lightHex, darkHex }) {
  const inside = (i, j) => i >= 0 && j >= 0 && i < mask.w && j < mask.h && mask.data[(j * mask.w + i) * 4 + 3] > 0;
  for (let j = 0; j < mask.h; j++)
    for (let i = 0; i < mask.w; i++) {
      if (!inside(i, j)) continue;
      let hex = face;
      if (!inside(i - 1, j) || !inside(i, j - 1)) hex = lightHex;
      else if (!inside(i + 1, j) || !inside(i, j + 1)) hex = darkHex;
      put(img, x + i, y + j, c(hex));
    }
  return img;
}

/** Copies a tinted silhouette of `src` (alpha kept) — for masks and lamp states. */
export function recolor(src, hex, k = 1) {
  const out = image(src.w, src.h);
  const [r, g, b] = c(hex);
  for (let i = 0; i < src.data.length; i += 4) if (src.data[i + 3]) out.data.set([r, g, b, Math.round(src.data[i + 3] * k)], i);
  return out;
}

/** Draws a chip seen from the top (radius r) at (cx, cy): denomination base with 6 edge inserts and an inner ring. */
export function chipTop(img, cx, cy, r, baseHex, stripeHex) {
  for (let y = Math.floor(cy - r - 1); y <= cy + r + 1; y++)
    for (let x = Math.floor(cx - r - 1); x <= cx + r + 1; x++) {
      const dx = x + 0.5 - cx;
      const dy = y + 0.5 - cy;
      const d = Math.hypot(dx, dy);
      if (d > r + 0.5) continue;
      if (d > r - 0.5) {
        put(img, x, y, c(K.ink));
        continue;
      }
      const a = Math.atan2(dy, dx);
      const insert = d > r * 0.62 && Math.cos(a * 6) > 0.55;
      let hex = insert ? stripeHex : baseHex;
      if (Math.abs(d - r * 0.55) < 0.6) hex = stripeHex;
      put(img, x, y, c(hex));
      if (dx + dy < -r * 0.9 && d > r * 0.7) blend(img, x, y, c(K.white), 0.3);
    }
  return img;
}

/** Chip seen from the side (a stack slice) at (x, y), width w: 3 px tall. */
export function chipSide(img, x, y, w, baseHex, stripeHex) {
  rect(img, x, y, w, 3, K.ink);
  rect(img, x + 1, y, w - 2, 2, baseHex);
  for (let i = x + 2; i < x + w - 2; i += 4) rect(img, i, y, 2, 2, stripeHex);
  rect(img, x + 1, y, w - 2, 1, mixHex(baseHex, '#FFFFFF', 0.3));
  return img;
}

/** Pixel star field / dust. */
export function speckle(img, x0, y0, w, h, count, hexes, seed, k = 1) {
  const r = rng(seed);
  for (let i = 0; i < count; i++) blend(img, x0 + ((r() * w) | 0), y0 + ((r() * h) | 0), c(hexes[(r() * hexes.length) | 0]), k);
  return img;
}

/** Scales an image by 2 (crisp). */
export const x2 = (img) => scaleUp(img, 2);

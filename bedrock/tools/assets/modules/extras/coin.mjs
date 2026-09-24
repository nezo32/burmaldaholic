// Coin Flip art (extras.md §3; extras-pvp §1–§2): the big Lucky Coin (12 spin frames with a real reeded edge),
// glint, heat rims, flames, shadow, landing pad, chain pips, mini coins and the item variants.
// The coin is modelled as a 3 px thick cylinder turning about the vertical axis, drawn at 32-px art resolution and
// shown at 2× (64 px) with the house 1 px ink outline and 2 px shadow. Heads and tails differ by SHAPE (embossed
// crowned head vs crossed picks), never by colour (UI.md §13).
import { EMBLEM_HEADS, EMBLEM_TAILS } from './icons.mjs';
import {
  K, art16, blend, c, clamp, disc, emboss, fade, image, mixHex, outline, over, put, rng, scaleUp, shadow, vstrip,
} from './kit.mjs';

const R = 14.5; // art-space radius
const T = 3; // edge thickness (art px)
/** Frame angles (deg): 0 heads … 6 edge-on … 11 tails (extras-pvp §1.2 sheet order). */
export const COIN_ANGLES = [0, 18, 36, 54, 72, 84, 90, 108, 126, 144, 162, 180];

/** Flat 32 × 32 coin face (heads | tails) with rim bevel, bead ring and the embossed emblem. */
export function coinFace(kind) {
  const img = image(32, 32);
  for (let y = 0; y < 32; y++)
    for (let x = 0; x < 32; x++) {
      const dx = x + 0.5 - 16;
      const dy = y + 0.5 - 16;
      const d = Math.hypot(dx, dy);
      if (d > R) continue;
      const lit = (-dx - dy) / (d || 1); // +1 top-left … −1 bottom-right
      let hex;
      if (d > R - 1.1) hex = lit > 0.3 ? '#FFE87A' : lit < -0.3 ? K.goldShade : '#E0A828';
      else if (d > R - 2.2) hex = lit > 0.3 ? '#C88A18' : lit < -0.3 ? '#FFF0A0' : '#E8B830';
      else if (d > R - 3) {
        const a = Math.atan2(dy, dx);
        hex = Math.floor(((a + Math.PI) / (2 * Math.PI)) * 28) % 2 === 0 ? '#FFF4B0' : '#D8A020';
      } else hex = d > 9.5 ? '#F6C832' : '#FFD640';
      put(img, x, y, c(hex));
    }
  // soft field highlight (top-left crescent)
  for (let y = 0; y < 32; y++)
    for (let x = 0; x < 32; x++) {
      const dx = x + 0.5 - 16;
      const dy = y + 0.5 - 16;
      const d = Math.hypot(dx, dy);
      if (d < R - 3 && d > 7.5 && dx + dy < -9) blend(img, x, y, c('#FFF4B0'), 0.55);
    }
  const mask = art16(kind === 'heads' ? EMBLEM_HEADS : EMBLEM_TAILS);
  emboss(img, mask, 8, 8, { face: '#DCA424', lightHex: '#FFF8C8', darkHex: '#7A4A08' });
  return img;
}

/** One spin frame at angle `a` (deg) in 32-px art space (no outline). */
function spinArt(a) {
  const heads = coinFace('heads');
  const tails = coinFace('tails');
  const rad = (a * Math.PI) / 180;
  const ca = Math.cos(rad);
  const sa = Math.sin(rad);
  const face = ca >= 0 ? heads : tails;
  const xc = (ca >= 0 ? 1 : -1) * (T / 2) * sa;
  const w = Math.abs(ca);
  const shade = 0.7 + 0.3 * w;
  const img = image(32, 32);
  for (let y = 0; y < 32; y++)
    for (let x = 0; x < 32; x++) {
      const X = x + 0.5 - 16;
      const Y = y + 0.5 - 16;
      if (Math.abs(Y) > R) continue;
      const hw = R * Math.sqrt(Math.max(0, 1 - (Y / R) ** 2));
      const inFace = w > 0.02 && Math.abs(X - xc) <= hw * w;
      if (inFace) {
        const u = Math.floor(16 + (X - xc) / w);
        const p = face.data.subarray((y * 32 + clamp(u, 0, 31)) * 4, (y * 32 + clamp(u, 0, 31)) * 4 + 4);
        if (!p[3]) continue;
        put(img, x, y, [Math.round(p[0] * shade), Math.round(p[1] * shade), Math.round(p[2] * shade), 255]);
        continue;
      }
      const ext = (T / 2) * Math.abs(sa) + hw * w;
      if (Math.abs(sa) > 0.05 && Math.abs(X) <= ext) {
        // reeded edge: horizontal grooves, lighter towards the top
        const groove = y % 2 === 0;
        const top = Y < -R * 0.55;
        const bottom = Y > R * 0.6;
        let hex = groove ? '#D09A20' : '#8A5A08';
        if (top) hex = groove ? '#FFE070' : '#B07010';
        if (bottom) hex = groove ? '#A06A10' : '#6A3E06';
        put(img, x, y, c(hex));
      }
    }
  return img;
}

/** 64 × 64 spin frame k (0 heads … 6 edge … 11 tails): 2× art + ink outline + drop shadow. */
export function coinFrame(k) {
  const body = outline(scaleUp(spinArt(COIN_ANGLES[k]), 2), c(K.ink));
  return shadow(body, c(K.ink), 2, 2, 0.35);
}

/** The code-indexed spin sheet: 12 × 64 px frames in one row (768 × 64). */
export function coinSpinSheet() {
  const out = image(64 * 12, 64);
  for (let k = 0; k < 12; k++) over(out, coinFrame(k), k * 64, 0);
  return out;
}

/** Glint overlay: 4 frames (64 × 64) of a white diagonal band clipped to the face. */
export function coinGlint() {
  const mask = scaleUp(spinArt(0), 2);
  const frames = [];
  for (let f = 0; f < 4; f++) {
    const img = image(64, 64);
    const centre = -10 + f * 30;
    for (let y = 0; y < 64; y++)
      for (let x = 0; x < 64; x++) {
        if (!mask.data[(y * 64 + x) * 4 + 3]) continue;
        const dd = Math.abs(x + y - (centre + 32));
        if (dd < 3) put(img, x, y, [255, 255, 255, 170]);
        else if (dd < 6) put(img, x, y, [255, 250, 220, 90]);
      }
    frames.push(img);
  }
  return frames;
}

/** Heat rim overlays (64 × 64): 0 red-hot, 1 soul-blue (extras-pvp §2.2 heat 3–5; the Soul Wager tint). */
export function coinHeat(kind) {
  const img = image(64, 64);
  const [outer, mid, inner] = kind === 'soul' ? ['#1A6AFF', '#40D0FF', '#C8F8FF'] : ['#C81A08', '#FF6020', '#FFD080'];
  const r = rng(kind === 'soul' ? 77 : 33);
  for (let y = 0; y < 64; y++)
    for (let x = 0; x < 64; x++) {
      const d = Math.hypot(x + 0.5 - 32, y + 0.5 - 32);
      if (d > 30.5 || d < 24) continue;
      const hex = d > 29 ? outer : d > 27 ? mid : inner;
      const k = d > 29 ? 0.95 : d > 27 ? 0.8 : 0.35 + 0.25 * r();
      blend(img, x, y, c(hex), k);
    }
  return img;
}

/** Soft elliptical shadow under the coin (48 × 12, dithered alpha). */
export function coinShadow() {
  const img = image(48, 12);
  for (let y = 0; y < 12; y++)
    for (let x = 0; x < 48; x++) {
      const d = Math.hypot((x + 0.5 - 24) / 24, (y + 0.5 - 6) / 6);
      if (d > 1) continue;
      put(img, x, y, [24, 10, 40, Math.round(200 * (1 - d * d) + 30)]);
    }
  return img;
}

/**
 * Landing pad (144 × 40): an oval of green baize with a gold stitched border and a darker rim, the "table line"
 * where the coin lands and bounces (extras-pvp §1.2).
 */
export function coinPad() {
  const img = image(144, 40);
  for (let y = 0; y < 40; y++)
    for (let x = 0; x < 144; x++) {
      const d = Math.hypot((x + 0.5 - 72) / 71.5, (y + 0.5 - 20) / 19.5);
      if (d > 1) continue;
      let hex;
      if (d > 0.965) hex = K.ink;
      else if (d > 0.9) hex = y < 20 ? '#3A2010' : '#2A160A';
      else if (d > 0.86) hex = '#5A3418';
      else hex = d < 0.55 ? '#2A7A4A' : d < 0.75 ? '#237045' : '#1E5E3A';
      put(img, x, y, c(hex));
      // felt dither
      if (d < 0.86 && (x * 7 + y * 13) % 11 === 0) blend(img, x, y, c('#3A9A60'), 0.5);
    }
  // stitched gold ring
  for (let t = 0; t < 360; t += 3) {
    if ((t / 3) % 2) continue;
    const a = (t * Math.PI) / 180;
    put(img, Math.round(72 + Math.cos(a) * 71.5 * 0.83 - 0.5), Math.round(20 + Math.sin(a) * 19.5 * 0.83 - 0.5), c(K.gold));
  }
  // top highlight arc
  for (let x = 30; x < 114; x++) {
    const y = Math.round(20 - 19.5 * 0.9 * Math.sqrt(Math.max(0, 1 - ((x + 0.5 - 72) / (71.5 * 0.9)) ** 2)));
    blend(img, x, y + 1, c('#7A4A24'), 0.8);
  }
  return img;
}

/** Flame sprite (16 × 24 × 8 frames, V + mcmeta): licks around the rim at heat 4 (red) / 5 (soul). */
export function coinFlame(kind) {
  const pal = kind === 'soul' ? ['#0A2A6A', '#1A6AFF', '#40D0FF', '#C8F8FF'] : ['#6A1A00', '#C83A08', '#FF7A1A', '#FFE070'];
  const frames = [];
  for (let f = 0; f < 8; f++) {
    const img = image(16, 24);
    const ph = (f / 8) * Math.PI * 2;
    for (let y = 0; y < 24; y++)
      for (let x = 0; x < 16; x++) {
        const u = (x + 0.5 - 8) / 7.5;
        const v = 1 - (y + 0.5) / 24; // 0 bottom … 1 top
        const sway = 0.22 * Math.sin(v * 4 + ph) * v;
        let width = 0.95 * Math.sqrt(Math.max(0, 1 - v)) * Math.min(1, v * 5 + 0.35);
        width += 0.28 * Math.max(0, Math.sin(v * 11 - ph * 2 + u * 4)) * v * (1 - v) * 2;
        const q = Math.abs(u - sway) / Math.max(0.04, width);
        if (q > 1) continue;
        const heat = 1 - Math.hypot(q * 0.85, (v - 0.22) * 1.7);
        const hex = heat > 0.62 ? pal[3] : heat > 0.4 ? pal[2] : heat > 0.12 ? pal[1] : pal[0];
        put(img, x, y, c(hex));
      }
    // an ember breaking off
    const ey = 16 - ((f * 2) % 16);
    put(img, 8 + Math.round(4 * Math.sin(ph)), ey, c(pal[3]));
    frames.push(img);
  }
  return vstrip(frames);
}

/** Mini coin (size 12 | 14 | 16): gold disc, rim, a 2:1 downsample of the emblem, for pips, plates and items. */
export function miniCoin(kind, size = 14, { tint = null } = {}) {
  const img = image(size, size);
  const r = size / 2 - 0.5;
  const cx = size / 2;
  for (let y = 0; y < size; y++)
    for (let x = 0; x < size; x++) {
      const dx = x + 0.5 - cx;
      const dy = y + 0.5 - cx;
      const d = Math.hypot(dx, dy);
      if (d > r + 0.2) continue;
      let hex = d > r - 0.9 ? K.goldDeep : d > r - 1.9 ? (dx + dy < 0 ? '#FFE87A' : K.goldShade) : '#FFD640';
      if (d > r - 0.9) hex = '#5C3A00';
      put(img, x, y, c(tint && d <= r - 0.9 ? mixHex(hex, tint, 0.45) : hex));
    }
  const mask = art16(kind === 'heads' ? EMBLEM_HEADS : EMBLEM_TAILS);
  const small = image(8, 8);
  for (let y = 0; y < 8; y++)
    for (let x = 0; x < 8; x++) {
      let n = 0;
      for (let j = 0; j < 2; j++) for (let i = 0; i < 2; i++) if (mask.data[((y * 2 + j) * 16 + x * 2 + i) * 4 + 3]) n++;
      if (n >= 2) put(small, x, y, [0, 0, 0, 255]);
    }
  const o = Math.round((size - 8) / 2);
  emboss(img, small, o, o, { face: '#D8A020', lightHex: '#FFF4B0', darkHex: '#7A4A08' });
  return img;
}

/**
 * Chain pips (16 × 16 each, code-indexed row of 6): 0 hollow (future link), 1 current (gold ring), 2 heads won,
 * 3 heads lost, 4 tails won, 5 tails lost. Won = bonus-green ring, lost = chip-red ring + dimmed coin.
 */
export function chainPips() {
  const frames = [];
  const ringPx = (img, hex, dashed = false) => {
    for (let y = 0; y < 16; y++)
      for (let x = 0; x < 16; x++) {
        const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
        if (d > 7.6 || d < 6.3) continue;
        if (dashed && Math.floor((Math.atan2(y + 0.5 - 8, x + 0.5 - 8) + Math.PI) / (Math.PI / 6)) % 2) continue;
        put(img, x, y, c(hex));
      }
  };
  const hollow = image(16, 16);
  disc(hollow, 8, 8, 6.2, c('#140822'), 0.8);
  ringPx(hollow, K.boneShade, true);
  frames.push(hollow);
  const current = image(16, 16);
  disc(current, 8, 8, 6.2, c('#26103C'));
  ringPx(current, K.gold);
  put(current, 4, 3, c(K.white));
  disc(current, 8, 8, 2.2, c(K.gold));
  frames.push(current);
  for (const kind of ['heads', 'tails'])
    for (const won of [true, false]) {
      const img = image(16, 16);
      ringPx(img, won ? K.bonus : K.red);
      const coin = miniCoin(kind, 12);
      over(img, won ? coin : fade(coin, 0.7), 2, 2);
      frames.push(img);
    }
  return frames;
}

/** 16 × 16 item textures for the Lucky Coin display model (heads / tails). */
export const luckyCoinItem = (kind) => {
  const img = image(16, 16);
  over(img, miniCoin(kind, 14), 1, 1);
  return img;
};


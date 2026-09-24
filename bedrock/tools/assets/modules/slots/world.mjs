// In-world textures (animation/slots.md §5.1, §6.6, §9): reel strips from the REAL strips, blur loop, cabinet,
// overlay (win/pop frames, Hoard coin/empty, sticky column, glass, ladder plates with digits), marquee flipbooks,
// the End Dragon Wheel, the Nether final-window cell atlas, block textures (front flipbook) and particle sprites.
import { art } from './art.mjs';
import { baseFrame, blurFrame } from './frames.mjs';
import { ICON } from './icons.mjs';
import {
  affine, blend, col, digits, digitsWidth, disc, ditherShade, fill, halo, image, line, outline, over, put, ring, rng, star, vgrad,
} from './raster.mjs';
import { MACHINE_SYMBOLS } from './symbols.mjs';
import { THEME } from './theme.mjs';

const hexA = (hex, a) => [...col(hex).slice(0, 3), a];

// ---- reel strips ------------------------------------------------------------------------------------------------
/** One 16 × 16 strip cell: drum background, symbol art, 1 px separator (subtle) at the bottom. */
function stripCell(machine, symIndex, seed) {
  const t = THEME[machine];
  const img = image(16, 16);
  fill(img, 0, 0, 16, 16, col(t.drum));
  if (machine === 'end') {
    const r = rng(seed);
    for (let k = 0; k < 3; k++) put(img, (r() * 16) | 0, (r() * 16) | 0, col(r() > 0.5 ? '#4A3A6A' : '#2E2244'));
  }
  if (machine === 'nether') {
    const r = rng(seed);
    for (let k = 0; k < 4; k++) put(img, (r() * 16) | 0, (r() * 16) | 0, col('#4A0E0E'));
  }
  fill(img, 0, 15, 16, 1, col(t.drumShade));
  const sym = art(MACHINE_SYMBOLS[machine][symIndex]);
  // End: the void drum is nearly as dark as the ink outlines, so dark symbols (Dragon Egg, Dragon Head) lost their
  // silhouette at in-game scale; a soft lilac rim keeps every shape readable on the reel
  over(img, machine === 'end' ? halo(sym, col('#6A4A9E'), 2, 0.9) : sym);
  return img;
}

/** Strip texture: 16 × 16·(pre + L + post) px; cell k (0 ≤ k < L) at y = 16·(pre + k); wrap cells around. */
export function stripTexture(machine, strip, pre, post) {
  const L = strip.length;
  const cells = pre + L + post;
  const out = image(16, 16 * cells);
  for (let c = 0; c < cells; c++) {
    const k = (((c - pre) % L) + L) % L;
    over(out, stripCell(machine, strip[k], k * 7919 + L), 0, c * 16);
  }
  return out;
}

/** Blur loop: `loop` smeared cells + `wrap` repeat cells (continuous when scrolled with mod). Symbols: low pays. */
export function blurTexture(machine, loop, wrap) {
  const list = MACHINE_SYMBOLS[machine];
  const t = THEME[machine];
  const picks = [7, 3, 9, 5, 8, 4, 10, 6].slice(0, loop);
  const cellsImg = picks.map((si) => {
    const img = image(16, 16);
    fill(img, 0, 0, 16, 16, col(t.drum));
    over(img, blurFrame(list[si], 16));
    return img;
  });
  const out = image(16, 16 * (loop + wrap));
  for (let c = 0; c < loop + wrap; c++) over(out, cellsImg[c % loop], 0, c * 16);
  // speed streaks: faint vertical lines across the loop
  const r = rng(machine.length * 131);
  for (let k = 0; k < 3; k++) {
    const x = 2 + ((r() * 12) | 0);
    for (let y = 0; y < out.h; y++) if ((y + k * 5) % 9 < 5) blend(out, x, y, [255, 255, 255, 255], 0.12);
  }
  return out;
}

// ---- material helpers -----------------------------------------------------------------------------------------------
function material(img, machine, x0, y0, w, h, seed) {
  const t = THEME[machine];
  const r = rng(seed);
  const m = t.material.map(col);
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      let c;
      if (machine === 'overworld') {
        // planks: 4-px boards with seams and grain
        const board = Math.floor((y + (x > w / 2 ? 2 : 0)) / 4);
        c = (y + (x > w / 2 ? 2 : 0)) % 4 === 3 ? m[3] : m[board % 2 === 0 ? 0 : 1];
        if (r() < 0.12) c = m[2];
      } else if (machine === 'nether') {
        c = m[(r() * 3) | 0];
        if (r() < 0.05) c = m[3];
      } else {
        // purpur tiles: 4 × 4 squares with bevel
        const tx = x % 4;
        const ty = y % 4;
        c = tx === 0 || ty === 0 ? m[0] : tx === 3 || ty === 3 ? m[2] : m[1];
      }
      put(img, x0 + x, y0 + y, c);
    }
  if (machine === 'nether') {
    // glowing lava seams (a crack path)
    let x = x0 + ((r() * w) | 0);
    for (let y = y0; y < y0 + h; y += 1) {
      put(img, x, y, col(r() < 0.5 ? '#FF7A1A' : '#FFB040'));
      if (r() < 0.4) x += r() < 0.5 ? -1 : 1;
      x = Math.max(x0, Math.min(x0 + w - 1, x));
    }
  }
}

function border(img, x0, y0, w, h, c, cLight, cDark) {
  for (let x = x0; x < x0 + w; x++) {
    put(img, x, y0, col(cLight));
    put(img, x, y0 + h - 1, col(cDark));
  }
  for (let y = y0; y < y0 + h; y++) {
    put(img, x0, y, col(cLight));
    put(img, x0 + w - 1, y, col(cDark));
  }
  void c;
}

// ---- cabinet (64 × 64, 1 texel per model px; UV layout in entity.mjs CAB_UV) --------------------------------------
export const CAB_UV = {
  front: [0, 0, 14, 13],
  back: [14, 0, 14, 13],
  side: [28, 0, 12, 13],
  top: [40, 0, 14, 12],
  mqFront: [0, 14, 15, 4],
  mqBack: [15, 14, 15, 4],
  mqSide: [30, 14, 13, 4],
  mqTop: [0, 19, 15, 13],
  trim: [60, 60, 2, 2],
  stand: [56, 56, 2, 2],
};

export function cabinetTexture(machine) {
  const t = THEME[machine];
  const img = image(64, 64);
  const U = CAB_UV;
  // front: material + trim; window area dark; payout tray + plaque below
  material(img, machine, ...U.front.slice(0, 2), U.front[2], U.front[3], 11);
  border(img, U.front[0], U.front[1], U.front[2], U.front[3], t.trim, t.trimLight, t.trimDark);
  fill(img, 0, 1, 14, 9, col(t.window));
  fill(img, 5, 11, 4, 1, col('#000000'));
  put(img, 4, 11, col(t.trim));
  put(img, 9, 11, col(t.trim));
  // back / sides / top
  material(img, machine, U.back[0], U.back[1], U.back[2], U.back[3], 12);
  border(img, U.back[0], U.back[1], U.back[2], U.back[3], t.trim, t.trimLight, t.trimDark);
  for (let y = 3; y < 10; y += 2) fill(img, U.back[0] + 4, y, 6, 1, col(t.trimDark));
  material(img, machine, U.side[0], U.side[1], U.side[2], U.side[3], 13);
  border(img, U.side[0], U.side[1], U.side[2], U.side[3], t.trim, t.trimLight, t.trimDark);
  // side accent: a vertical trim band with rivets / lamp
  fill(img, U.side[0] + 5, 1, 2, 11, col(t.trim));
  fill(img, U.side[0] + 5, 1, 1, 11, col(t.trimLight));
  for (const y of [2, 6, 10]) put(img, U.side[0] + 2, y, col(t.rivet));
  for (const y of [2, 6, 10]) put(img, U.side[0] + 9, y, col(t.rivet));
  material(img, machine, U.top[0], U.top[1], U.top[2], U.top[3], 14);
  border(img, U.top[0], U.top[1], U.top[2], U.top[3], t.trim, t.trimLight, t.trimDark);
  // marquee box (trim material)
  for (const k of ['mqFront', 'mqBack', 'mqSide']) {
    const [x, y, w, h] = U[k];
    fill(img, x, y, w, h, col(t.trim));
    fill(img, x, y, w, 1, col(t.trimLight));
    fill(img, x, y + h - 1, w, 1, col(t.trimDark));
    for (let i = 1; i < w - 1; i += 3) put(img, x + i, y + 1 + (i % 2), col(t.rivet));
  }
  const [qx, qy, qw, qh] = U.mqTop;
  fill(img, qx, qy, qw, qh, col(t.trim));
  border(img, qx, qy, qw, qh, t.trim, t.trimLight, t.trimDark);
  if (machine === 'overworld') {
    // grass tuft on the marquee (§3.1)
    const r = rng(77);
    for (let y = qy + 1; y < qy + qh - 1; y++) for (let x = qx + 1; x < qx + qw - 1; x++) put(img, x, y, col(r() < 0.3 ? '#4FA83A' : r() < 0.6 ? '#5FC23A' : '#3E8A2E'));
  } else if (machine === 'nether') {
    for (let x = qx + 2; x < qx + qw - 2; x += 3) put(img, x, qy + (qh >> 1), col('#FF7A1A'));
  } else {
    put(img, qx + (qw >> 1), qy + (qh >> 1), col('#F4ECF8'));
  }
  fill(img, ...U.trim.slice(0, 2), U.trim[2], U.trim[3], col(t.trim));
  put(img, U.trim[0], U.trim[1], col(t.trimLight));
  fill(img, ...U.stand.slice(0, 2), U.stand[2], U.stand[3], col(machine === 'end' ? '#140C1C' : t.trimDark));
  return img;
}

// ---- overlay (64 × 64): see OVL_UV -----------------------------------------------------------------------------
export const OVL_UV = {
  win: [0, 0, 16, 16],
  pop: [16, 0, 16, 16],
  coin: [32, 0, 16, 16],
  empty: [48, 0, 16, 16],
  sticky: [0, 16, 16, 48],
  glass: [16, 16, 32, 20],
  plate: (i) => [48, 16 + i * 6, 16, 6],
};

function frameCell(outer, inner, glow) {
  const img = image(16, 16);
  for (let i = 0; i < 16; i++)
    for (const [x, y] of [[i, 0], [i, 15], [0, i], [15, i]]) put(img, x, y, col(outer));
  for (let i = 1; i < 15; i++)
    for (const [x, y] of [[i, 1], [i, 14], [1, i], [14, i]]) put(img, x, y, col(inner));
  for (let i = 2; i < 14; i++)
    for (const [x, y] of [[i, 2], [i, 13], [2, i], [13, i]]) blend(img, x, y, col(glow), 0.6);
  // corner gems
  for (const [x, y] of [[0, 0], [15, 0], [0, 15], [15, 15]]) put(img, x, y, [255, 255, 255, 255]);
  return img;
}

export function overlayTexture(machine, multPlates) {
  const img = image(64, 64);
  const U = OVL_UV;
  over(img, frameCell('#B07010', '#FFD640', '#FFF4B0'), U.win[0], U.win[1]);
  over(img, frameCell('#A02A00', '#FF7A1A', '#FFD080'), U.pop[0], U.pop[1]);
  // Hoard: coin on a dark cell, empty cell
  const cellBg = (x, y) => fill(img, x, y, 16, 16, col('#1A0A0A'));
  cellBg(U.coin[0], U.coin[1]);
  over(img, halo(art(MACHINE_SYMBOLS.nether[2], { lit: true }), col('#FFC400'), 1, 0.7), U.coin[0], U.coin[1]);
  over(img, art(ICON.empty_cell), U.empty[0], U.empty[1]);
  // sticky column: tall glowing egg with amethyst chain frame (16 × 48)
  const [sx, sy] = U.sticky;
  fill(img, sx, sy, 16, 48, col('#1E0A34'));
  over(img, affine(art(MACHINE_SYMBOLS.end[0], { lit: true }), 16, 48, { scale: 1, sx: 1, sy: 2.6, px: 8, py: 8, ox: 8, oy: 24 }), sx, sy);
  for (let y = 0; y < 48; y++) {
    const c = y % 4 < 2 ? '#D0A0FF' : '#7A3AC0';
    put(img, sx, sy + y, col(c));
    put(img, sx + 15, sy + y, col(c));
  }
  for (let x = 0; x < 16; x++) {
    put(img, sx + x, sy, col('#D0A0FF'));
    put(img, sx + x, sy + 47, col('#7A3AC0'));
  }
  // glass: dithered drum shading top/bottom + a diagonal sheen (alpha-tested pixels only)
  const [gx, gy, gw, gh] = U.glass;
  for (let y = 0; y < gh; y++)
    for (let x = 0; x < gw; x++) {
      const edge = Math.min(y, gh - 1 - y);
      const dark = edge < 4 ? (4 - edge) / 5 : 0;
      const th = ((x & 3) * 4 + (y & 3) * 7) % 16 / 16;
      if (dark > th) put(img, gx + x, gy + y, [0, 0, 0, 255]);
      else if ((x + y) % 23 === 0 && y > 4 && y < gh - 5 && x % 2 === 0) put(img, gx + x, gy + y, [255, 255, 255, 255]);
    }
  // reel dividers inside the glass (5 reels over 32 texels)
  for (let r = 1; r < 5; r++) for (let y = 0; y < gh; y++) if (y % 2 === 0) put(img, gx + Math.round((r * gw) / 5), gy + y, col('#000000'));
  // ladder plates ×N (digits allowed here, §9)
  multPlates.forEach((v, i) => {
    const [px, py, pw, ph] = U.plate(i);
    fill(img, px, py, pw, ph, col('#B07010'));
    fill(img, px, py, pw, 1, col('#FFF4B0'));
    fill(img, px + 1, py + 1, pw - 2, ph - 2, col('#FFD640'));
    const text = `x${v}`;
    digits(img, text, px + Math.floor((pw - digitsWidth(text)) / 2), py + 1, col('#5C3A00'));
  });
  void machine;
  return img;
}

// ---- marquee panel flipbooks: 64 × 16 per frame, 8 frames (0–3 chase, 4 on, 5 off, 6/7 halves) ---------------------
const BULBS = 13;
function marqueeFrame(machine, variant, f) {
  const t = THEME[machine];
  const img = image(64, 16);
  const panel = variant === 'fs' ? t.fsPanel : variant === 'jackpot' ? '#1A0A2A' : t.panel;
  fill(img, 0, 0, 64, 16, col(panel));
  fill(img, 0, 0, 64, 1, col(t.trimLight));
  fill(img, 0, 15, 64, 1, col(t.trimDark));
  fill(img, 0, 0, 1, 16, col(t.trimLight));
  fill(img, 63, 0, 1, 16, col(t.trimDark));
  // emblem in the middle (no text): machine sigil
  emblem(img, machine, variant, 32, 8);
  // bulbs along top and bottom rows
  const positions = [];
  for (let i = 0; i < BULBS; i++) positions.push([3 + i * 4.8, 3]);
  for (let i = 0; i < BULBS; i++) positions.push([3 + (BULBS - 1 - i) * 4.8, 12]);
  positions.forEach(([x, y], i) => {
    let on;
    if (f < 4) on = (i + f) % 4 === 0;
    else if (f === 4) on = true;
    else if (f === 5) on = false;
    else on = (x < 32) === (f === 6);
    let c = t.bulbOn;
    if (variant === 'fs') c = t.fsBulb;
    if (variant === 'jackpot') c = ['#FF4040', '#FFA030', '#FFE040', '#60E050', '#40A0FF', '#B050FF'][(i + f) % 6];
    const cx = Math.round(x);
    if (on) {
      disc(img, cx + 0.5, y + 0.5, 1.6, col(c));
      put(img, cx, y, col(t.bulbHot));
      blend(img, cx - 2, y, col(c), 0.35);
      blend(img, cx + 2, y, col(c), 0.35);
    } else {
      disc(img, cx + 0.5, y + 0.5, 1.2, col(variant === 'jackpot' ? '#3A2A4A' : t.bulbOff));
    }
  });
  return img;
}

function emblem(img, machine, variant, cx, cy) {
  const t = THEME[machine];
  if (machine === 'overworld') {
    if (variant === 'fs') {
      disc(img, cx, cy, 3.5, col('#F4ECD8'));
      disc(img, cx + 1.5, cy - 1, 3, col(t.fsPanel));
      for (const [x, y] of [[cx - 9, cy - 2], [cx + 8, cy + 1], [cx - 5, cy + 2]]) put(img, x, y, col('#FFFFFF'));
    } else {
      disc(img, cx, cy, 3.2, col('#FFD640'));
      for (let k = 0; k < 8; k++) {
        const a = (k / 8) * Math.PI * 2;
        put(img, Math.round(cx + Math.cos(a) * 5), Math.round(cy + Math.sin(a) * 4.5), col('#FFE070'));
      }
    }
  } else if (machine === 'nether') {
    // flame sigil
    const flame = ['..y..', '.yoy.', 'yoroy', 'yorry', '.yry.'];
    flame.forEach((row, j) => [...row].forEach((ch, i) => ch !== '.' && put(img, cx - 2 + i, cy - 2 + j, col({ y: '#FFE070', o: '#FF7A1A', r: '#C82A08' }[ch]))));
    fill(img, cx - 9, cy, 5, 1, col(t.trim));
    fill(img, cx + 5, cy, 5, 1, col(t.trim));
  } else {
    // eye sigil
    fill(img, cx - 3, cy - 1, 7, 3, col('#2E8A6A'));
    fill(img, cx - 2, cy - 2, 5, 5, col('#2E8A6A'));
    fill(img, cx - 1, cy - 1, 3, 3, col('#40E0A0'));
    put(img, cx, cy, col('#04120C'));
    for (const [x, y] of [[cx - 10, cy - 2], [cx + 9, cy + 2], [cx - 7, cy + 3], [cx + 12, cy - 3]]) put(img, x, y, col(variant === 'fs' ? '#C070FF' : '#F4ECF8'));
  }
}

export function marqueeTexture(machine, variant) {
  const out = image(64, 16 * 8);
  for (let f = 0; f < 8; f++) over(out, marqueeFrame(machine, variant, f), 0, f * 16);
  return out;
}

// ---- End Dragon Wheel (128 × 128; regions WHEEL_UV in 64-space) ----------------------------------------------------
export const WHEEL_UV = {
  outer: [0, 0, 32, 32],
  middle: [32, 0, 24, 24],
  core: [32, 24, 16, 16],
  pointer: [48, 24, 8, 8],
  stand: [56, 24, 4, 4],
};
/** SLOTS.md §3.3 wedge order (clockwise from the pointer at rest). */
export const WHEEL_WEDGES = [
  '10 UP 12 15 MINI 10 20 12 25 10 40 15 UP 12 20 MINI 15 10 75 25'.split(' '),
  '30 50 MINOR 75 30 100 50 UP 30 75 MINOR 50 100 30 75 50'.split(' '),
  '150 MAJOR 250 150 GRAND 250 MAJOR 150 500 250 MAJOR 150'.split(' '),
];

function wedgeColour(ring, v) {
  const special = { UP: '#B040FF', MINI: '#80FF40', MINOR: '#4080FF', MAJOR: '#FFD640', GRAND: '#FF40C0' };
  if (special[v]) return special[v];
  const n = Number(v);
  const ramps = [
    ['#8E8A5A', '#B8B478', '#D8D49A', '#E8E4A8', '#F4F0C0', '#FAF8DC', '#FFFFF0'], // end stone
    ['#6E4A6E', '#8E648E', '#A77BA7', '#C8A0C8', '#E0C0E0'], // purpur
    ['#3A1A4A', '#5A2A6A', '#7A3A8A', '#A050C0'], // dragon
  ];
  const steps = [
    [10, 12, 15, 20, 25, 40, 75],
    [30, 50, 75, 100],
    [150, 250, 500],
  ][ring];
  const k = steps.indexOf(n);
  return ramps[ring][Math.max(0, k)];
}

function wheelDisc(ring, size) {
  const wedges = WHEEL_WEDGES[ring];
  const n = wedges.length;
  const img = image(size, size);
  const c = size / 2;
  const rOut = c - 0.5;
  const rIn = ring === 2 ? size * 0.12 : size * 0.3;
  for (let y = 0; y < size; y++)
    for (let x = 0; x < size; x++) {
      const dx = x + 0.5 - c;
      const dy = y + 0.5 - c;
      const d = Math.hypot(dx, dy);
      if (d > rOut || d < rIn) continue;
      // angle clockwise from 12 o'clock
      let a = Math.atan2(dx, -dy);
      if (a < 0) a += Math.PI * 2;
      const seg = Math.floor((a / (Math.PI * 2)) * n) % n;
      const frac = ((a / (Math.PI * 2)) * n) % 1;
      let colr = col(wedgeColour(ring, wedges[seg]));
      if (seg % 2 === 1) colr = colr.map((v, i) => (i < 3 ? Math.round(v * 0.86) : v));
      if (frac < 0.06 || frac > 0.94) colr = col('#FFD640');
      if (d > rOut - 1.5) colr = col('#B07010');
      else if (d > rOut - 2.5) colr = col('#FFD640');
      put(img, x, y, colr);
    }
  // special wedge icons (dots) near the rim
  wedges.forEach((v, seg) => {
    if (!/[A-Z]/.test(v)) return;
    const a = ((seg + 0.5) / n) * Math.PI * 2;
    const rr = (rOut + rIn) / 2 + size * 0.06;
    const x = c + Math.sin(a) * rr;
    const y = c - Math.cos(a) * rr;
    disc(img, x, y, size * 0.045 + 0.6, col('#FFFFFF'));
    disc(img, x, y, size * 0.03 + 0.3, col(wedgeColour(ring, v)));
  });
  if (ring === 2) disc(img, c, c, size * 0.12, col('#140C1C'));
  return img;
}

export function wheelTexture() {
  const img = image(128, 128);
  over(img, wheelDisc(0, 64), 0, 0);
  over(img, wheelDisc(1, 48), 64, 0);
  over(img, wheelDisc(2, 32), 64, 48);
  // pointer (16 × 16 at 96,48): the shared pointer glyph
  over(img, art(ICON.pointer), 96, 48);
  fill(img, 112, 48, 8, 8, col('#140C1C'));
  fill(img, 112, 48, 8, 1, col('#3A2A4A'));
  return img;
}

// ---- Nether final-window atlas (64 × 64: symbol s at ((s % 4)·16, ⌊s / 4⌋·16)) ---------------------------------------
export function cellsAtlas(machine) {
  const img = image(64, 64);
  MACHINE_SYMBOLS[machine].forEach((s, i) => over(img, stripCell(machine, i, i * 31), (i % 4) * 16, Math.floor(i / 4) * 16));
  return img;
}

// ---- block textures (lower cabinet block): front flipbook 16 × 64 (4 frames), side, top ----------------------------
export function blockFront(machine, frame) {
  const t = THEME[machine];
  const img = image(16, 16);
  material(img, machine, 0, 0, 16, 16, 21);
  border(img, 0, 0, 16, 16, t.trim, t.trimLight, t.trimDark);
  // top trim strip with 4 chasing bulbs
  fill(img, 1, 1, 14, 3, col(t.panel));
  for (let i = 0; i < 4; i++) {
    const on = (i + frame) % 4 === 0 || (i + frame) % 4 === 2 ? (i + frame) % 4 === 0 : false;
    const x = 2 + i * 4;
    put(img, x, 2, col(on ? t.bulbHot : t.bulbOff));
    put(img, x + 1, 2, col(on ? t.bulbOn : t.bulbOff));
    if (on) {
      blend(img, x - 1, 2, col(t.bulbOn), 0.4);
      blend(img, x + 2, 2, col(t.bulbOn), 0.4);
    }
  }
  // control panel with coin slot and spin button
  fill(img, 2, 6, 12, 4, col(t.trim));
  fill(img, 2, 6, 12, 1, col(t.trimLight));
  fill(img, 4, 7, 3, 2, col('#000000'));
  fill(img, 10, 7, 2, 2, col(frame % 2 ? '#FF5040' : '#C02020'));
  put(img, 10, 7, col('#FFB0A0'));
  // payout tray with a coin glint cycling
  fill(img, 3, 11, 10, 3, col('#0A0606'));
  fill(img, 3, 13, 10, 1, col(t.trimDark));
  const gx = 4 + ((frame * 3) % 8);
  put(img, gx, 12, col('#FFD640'));
  put(img, 7, 12, col('#B07010'));
  put(img, 9, 12, col('#FFC400'));
  return img;
}
export function blockSide(machine) {
  const t = THEME[machine];
  const img = image(16, 16);
  material(img, machine, 0, 0, 16, 16, 22);
  border(img, 0, 0, 16, 16, t.trim, t.trimLight, t.trimDark);
  fill(img, 7, 1, 2, 14, col(t.trim));
  fill(img, 7, 1, 1, 14, col(t.trimLight));
  for (const y of [3, 12]) {
    put(img, 3, y, col(t.rivet));
    put(img, 12, y, col(t.rivet));
  }
  return img;
}
export function blockTop(machine) {
  const t = THEME[machine];
  const img = image(16, 16);
  material(img, machine, 0, 0, 16, 16, 23);
  border(img, 0, 0, 16, 16, t.trim, t.trimLight, t.trimDark);
  return img;
}

// ---- particle sprites (8 × 8 frames): ember_burst (4), void_motes (4) ----------------------------------------------
export function emberFrames() {
  return [0, 1, 2, 3].map((f) => {
    const img = image(8, 8);
    const r = [2.6, 2.2, 1.7, 1.1][f];
    disc(img, 3.5, 3.5, r + 0.8, col('#C83A08'));
    disc(img, 3.5, 3.5, r, col('#FF7A1A'));
    disc(img, 3.5, 3.5, Math.max(0.6, r - 1), col('#FFE070'));
    if (f < 2) put(img, 3, 3, [255, 255, 240, 255]);
    return img;
  });
}
export function moteFrames() {
  return [0, 1, 2, 3].map((f) => {
    const img = image(8, 8);
    const r = [1.2, 2, 2.6, 1.8][f];
    disc(img, 3.5, 3.5, r + 0.7, col('#5A1A8A'));
    disc(img, 3.5, 3.5, r, col('#B040FF'));
    put(img, 3, 3, col('#F0C8FF'));
    if (f === 2) star(img, 3, 3, 3, col('#D090FF'), 0.6);
    return img;
  });
}
export function particleAtlas() {
  const img = image(64, 64);
  emberFrames().forEach((f, i) => over(img, f, i * 8, 0));
  moteFrames().forEach((f, i) => over(img, f, i * 8, 8));
  return img;
}

export { baseFrame, line, ring, outline, vgrad, ditherShade, hexA };

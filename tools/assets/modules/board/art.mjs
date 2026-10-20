// In-world board kit art (docs/architecture/ui-v2.md §7, §9; lane W): the felt overhang tops, the apron wood, the
// card faces at in-world size, chip discs and bands, the holo backing. Pure pixel maths; no text is baked in.
import { CHIP_DENOMS, CHIPS } from '../../lib/palette.mjs';
import { BACK_ORDER, RANKS, back, cardBody, drawIndex, emblem } from '../cards/faces.mjs';
import { B, P, R, image, mixHex, noise, over, scaleUp } from '../cards/px.mjs';
import { pip } from '../cards/suits.mjs';
import { SUITS_4C, SUIT_ORDER } from '../cards/theme.mjs';

/** 64 texels per block on the felt tops. */
export const FELT_PX = 64;

/** Felt shapes — MUST match java core/ui/v2/board/BoardGeometry.Felt (w, d, frontR, backR in blocks). */
export const FELTS = {
  crescent: { w: 2.25, d: 1.25, frontR: 0.45, backR: 0.1 },
  rect: { w: 2.0, d: 1.4, frontR: 0.14, backR: 0.14 },
  oval: { w: 2.0, d: 2.0, frontR: 0.75, backR: 0.75 },
  fallback: { w: 1.0, d: 1.0, frontR: 0.06, backR: 0.06 },
};
export const RAIL = 0.08;

/** Themes (java BoardTextures.THEMES order: village, bastion, end). */
export const THEMES = {
  village: { felt: '#1E5E3A', deep: '#0E2E1C', rail: '#5A3418', railLight: '#8A5A2C', railDark: '#2E1A0C', line: '#EDE2C4', trim: '#B07010' },
  bastion: { felt: '#6A1A1E', deep: '#2A080A', rail: '#2A1C18', railLight: '#4A3628', railDark: '#140C0A', line: '#FFD640', trim: '#FFB020' },
  end: { felt: '#2E1E56', deep: '#100822', rail: '#1E1634', railLight: '#3A2E5C', railDark: '#0C0818', line: '#F4ECF8', trim: '#C0B0DC' },
};

/** Signed distance (blocks) from (x, z) to the felt edge, negative inside; the felt is centred on (0.5, 0.5). */
export function feltSdf(f, x, z) {
  const r = z > 0.5 ? f.frontR : f.backR;
  const qx = Math.abs(x - 0.5) - (f.w / 2 - r);
  const qz = Math.abs(z - 0.5) - (f.d / 2 - r);
  const ox = Math.max(qx, 0);
  const oz = Math.max(qz, 0);
  return Math.hypot(ox, oz) + Math.min(Math.max(qx, qz), 0) - r;
}

const SAMPLES = [
  [0.25, 0.25],
  [0.75, 0.25],
  [0.25, 0.75],
  [0.75, 0.75],
];

/** The felt top: rail band (wood, highlight on the inner edge), felt with grain + a soft edge vignette, a print line. */
export function feltTop(shape, theme) {
  const f = FELTS[shape];
  const t = THEMES[theme];
  const w = Math.round(f.w * FELT_PX);
  const h = Math.round(f.d * FELT_PX);
  const img = image(w, h);
  const x0 = 0.5 - f.w / 2;
  const z0 = 0.5 - f.d / 2;
  const rail = shape === 'fallback' ? 0.05 : RAIL;
  for (let j = 0; j < h; j++)
    for (let i = 0; i < w; i++) {
      // 2 × 2 supersampled coverage for a clean rounded edge (cutout: ≥ 2 of 4 samples)
      let cover = 0;
      for (const [sx, sy] of SAMPLES) if (feltSdf(f, x0 + (i + sx) / FELT_PX, z0 + (j + sy) / FELT_PX) <= 0) cover++;
      if (cover < 2) continue;
      const x = x0 + (i + 0.5) / FELT_PX;
      const z = z0 + (j + 0.5) / FELT_PX;
      const d = -feltSdf(f, x, z); // depth inside, blocks
      const n = noise(i, j, 7);
      if (d < rail) {
        // rail: rounded wood lip, light toward the felt, dark at the outer edge, a thin trim inside
        const k = d / rail;
        let c = k > 0.8 ? t.railLight : k < 0.2 ? t.railDark : t.rail;
        if (n < 0.18) c = mixHex(c, t.railDark, 0.35);
        if (k > 0.86 && k < 0.98) c = mixHex(c, t.trim, 0.55);
        P(img, i, j, c);
        continue;
      }
      // felt: base + fine grain + darker toward the rail
      const edge = Math.max(0, 1 - (d - rail) / 0.12);
      let c = mixHex(t.felt, t.deep, 0.45 * edge * edge);
      if (n < 0.22) c = mixHex(c, t.deep, 0.18);
      else if (n > 0.88) c = mixHex(c, '#FFFFFF', 0.05);
      P(img, i, j, c);
    }
  printLine(img, shape, f, t, x0, z0);
  return img;
}

/** A thin printed line on the felt (≈ 35 % of the theme's line colour): the players' arc / an inner border. */
function printLine(img, shape, f, t, x0, z0) {
  const plot = (x, z) => {
    const i = Math.floor((x - x0) * FELT_PX);
    const j = Math.floor((z - z0) * FELT_PX);
    if (i < 0 || j < 0 || i >= img.w || j >= img.h) return;
    if (img.data[(j * img.w + i) * 4 + 3] === 0) return;
    B(img, i, j, t.line, 0.35);
  };
  if (shape === 'crescent') {
    // the players' arc around the dealer (0.5, 0.10), inside the seat spots (BoardGeometry.crescentSeat)
    const seen = new Set();
    for (let a = -62; a <= 62; a += 0.1) {
      const r = (a * Math.PI) / 180;
      const x = 0.5 + 0.6 * Math.sin(r);
      const z = 0.1 + 0.48 * Math.cos(r);
      const key = `${Math.floor((x - x0) * FELT_PX)},${Math.floor((z - z0) * FELT_PX)}`;
      if (seen.has(key)) continue;
      seen.add(key);
      plot(x, z);
    }
  } else if (shape !== 'fallback') {
    const inset = RAIL + 0.06;
    const g = { w: f.w - 2 * inset, d: f.d - 2 * inset, frontR: Math.max(0.02, f.frontR - inset), backR: Math.max(0.02, f.backR - inset) };
    for (let j = 0; j < img.h; j++)
      for (let i = 0; i < img.w; i++) {
        const s = feltSdf(g, x0 + (i + 0.5) / FELT_PX, z0 + (j + 0.5) / FELT_PX);
        if (s > -0.5 / FELT_PX && s <= 0.5 / FELT_PX) B(img, i, j, t.line, 0.3);
      }
  }
}

/** The apron wood (16 × 16): top half = the apron side (grain, a trim line), bottom half = the underside (dark). */
export function railSide(theme) {
  const t = THEMES[theme];
  const img = image(16, 16);
  for (let j = 0; j < 16; j++)
    for (let i = 0; i < 16; i++) {
      const n = noise(i, j, 3);
      let c;
      if (j < 8) {
        c = j === 0 ? t.railLight : j === 7 ? t.railDark : t.rail;
        if (j === 1) c = mixHex(t.trim, t.rail, 0.4);
        if (n < 0.2 && j > 1 && j < 7) c = mixHex(c, t.railDark, 0.4);
      } else {
        c = mixHex(t.railDark, '#000000', 0.2);
        if (n < 0.25) c = mixHex(c, t.rail, 0.3);
      }
      P(img, i, j, c);
    }
  return img;
}

/**
 * In-world face 37 × 49, built to be read from 3–8 blocks (≈ 1 texel per screen px at 5 blocks): a 2× index top-left
 * (digits 2–10; a court emblem for J Q K; none for the ace — language-neutral like the W atlas), the suit pip under
 * it, and one big pip (ace: a 25 px gold-ringed pip; courts: the emblem again) in the lower right.
 */
export function faceW2(rank, suit, colours) {
  const sc = colours[suit];
  const img = cardBody(37, 49);
  const court = rank === 'J' || rank === 'Q' || rank === 'K';
  if (rank === 'A') {
    for (let a = 0; a < 32; a++) {
      const t = (a / 32) * Math.PI * 2;
      P(img, Math.round(18 + Math.cos(t) * 13.5), Math.round(24 + Math.sin(t) * 13.5), a % 2 ? '#FFD640' : '#B07010');
    }
    over(img, pip(suit, 17, sc), 10, 16);
    over(img, pip(suit, 7, sc), 3, 3);
    return img;
  }
  if (court) {
    over(img, scaleUp(emblem(rank, sc), 2), 2, 3);
    R(img, 15, 20, 19, 25, mixHex(sc.light, '#FBF6EA', 0.78));
    R(img, 15, 20, 19, 1, '#B07010');
    R(img, 15, 44, 19, 1, '#B07010');
    R(img, 15, 20, 1, 25, '#B07010');
    R(img, 33, 20, 1, 25, '#B07010');
    over(img, pip(suit, 11, sc), 19, 27);
  } else {
    const mark = image(16, 8);
    const w = drawIndex(mark, rank, 0, 1, sc.base);
    over(img, scaleUp(mark, 2), w > 4 ? 2 : 4, 1);
    over(img, pip(suit, 17, sc), 16, 26);
  }
  over(img, pip(suit, 7, sc), 4, 20);
  return img;
}

/** Card atlas 512 × 256: in-world faces (37 × 49) in 13 rank columns × 4 suit rows, the backs in row 4 (BACK_ORDER). */
export function facesW2() {
  const img = image(512, 256);
  SUIT_ORDER.forEach((suit, r) => RANKS.forEach((rank, c) => over(img, faceW2(rank, suit, SUITS_4C), c * 37, r * 49)));
  BACK_ORDER.forEach((d, i) => over(img, back(d, 'l'), i * 37, 4 * 49));
  return img;
}

/** Chip values left to right (the palette's one list; 128 px = 8 columns) and their colours. */
export const DENOMS = CHIP_DENOMS;
const CHIP_COLOURS = CHIPS;

/** Chip atlas 128 × 64: per denomination column (16 px) the top disc (16²) and a 6-disc side band (16 × 24). */
export function chipsW() {
  const img = image(128, 64);
  DENOMS.forEach((d, k) => {
    const { base, stripe } = CHIP_COLOURS[d];
    const x0 = k * 16;
    const dark = mixHex(base, '#000000', 0.35);
    // top disc: edge inserts, a dark inner ring, a lighter inlay
    for (let j = 0; j < 16; j++)
      for (let i = 0; i < 16; i++) {
        const dx = i + 0.5 - 8;
        const dy = j + 0.5 - 8;
        const r = Math.hypot(dx, dy);
        if (r > 7.6) continue;
        const ang = Math.atan2(dy, dx);
        let c;
        if (r > 6.0) c = Math.floor(((ang + Math.PI) / (2 * Math.PI)) * 8) % 2 === 0 ? stripe : base;
        else if (r > 5.0) c = dark;
        else if (r > 4.2) c = mixHex(base, '#FFFFFF', 0.15);
        else c = mixHex(base, '#FFFFFF', 0.08);
        if (r > 7.0) c = mixHex(c, '#000000', 0.25);
        P(img, x0 + i, j, c);
      }
    // side band: 6 discs × 4 px (a light edge, two body rows with inserts, a dark seam)
    for (let disc = 0; disc < 6; disc++)
      for (let row = 0; row < 4; row++)
        for (let i = 0; i < 16; i++) {
          const ins = i % 4 < 2;
          let c = row === 0 ? mixHex(base, '#FFFFFF', 0.12) : row === 3 ? mixHex(base, '#000000', 0.45) : ins ? stripe : base;
          if (row === 2 && !ins) c = mixHex(base, '#000000', 0.15);
          P(img, x0 + i, 16 + disc * 4 + row, c);
        }
  });
  return img;
}

/** The holo backing: 4 × 4 white (tinted ink 70 % in code). */
export function holoBacking() {
  const img = image(4, 4);
  R(img, 0, 0, 4, 4, '#FFFFFF');
  return img;
}

// ---- full multi-block tables (MT-DESIGN §2): the hi-res felt print decal ------------------------------------------

/**
 * Felt of a full 3×2 table inside the rail — MUST match java core/table/parts/FeltFrame.of(game, MAIN): w × d in blocks,
 * corner radius at the front (toward the players) and at the back. Roulette: the betting layout beside the wheel bowl.
 */
export const PRINT_FELTS = {
  crescent: { w: 2.75, d: 1.75, frontR: 0.875, backR: 0 },
  oval: { w: 2.75, d: 1.75, frontR: 0.875, backR: 0.875 },
  rect: { w: 2.75, d: 1.75, frontR: 0, backR: 0 },
  roulette: { w: 1.875, d: 1.75, frontR: 0, backR: 0 },
};

/** Furniture felt palettes — MUST match tools/assets/modules/tables/furniture.mjs THEMES (felt) + their print colour. */
export const PRINT_THEMES = {
  standard: { felt: ['#1E6A3E', '#1A5E37', '#22744A', '#175331'], deep: '#0C2A18', line: '#EDE2C4' },
  high_roller: { felt: ['#3A1E5E', '#331A54', '#43246A', '#2B1648'], deep: '#140A24', line: '#FFE070' },
  player_banked: { felt: ['#8A1C22', '#7A181E', '#9A2228', '#68141A'], deep: '#300808', line: '#E0B860' },
};

/** Signed distance (blocks) to a felt centred on (0, 0) with separate front (z > 0) and back corner radii. */
function printSdf(f, x, z) {
  const r = z > 0 ? f.frontR : f.backR;
  const qx = Math.abs(x) - (f.w / 2 - r);
  const qz = Math.abs(z) - (f.d / 2 - r);
  return Math.hypot(Math.max(qx, 0), Math.max(qz, 0)) + Math.min(Math.max(qx, qz), 0) - r;
}

/**
 * The felt print of a full table (64 texels per block, u → players' right, v → toward the players): woven baize in the
 * furniture's felt colours at 4 × the block felt's resolution, a soft shade toward the rail, an inner border line and
 * the game's printed marks (crescent: the players' arc round the dealer; oval: the racetrack line). Transparent
 * outside the rounded felt (cutout).
 */
export function feltPrint(shape, theme) {
  const f = PRINT_FELTS[shape];
  const t = PRINT_THEMES[theme];
  const w = Math.round(f.w * FELT_PX);
  const h = Math.round(f.d * FELT_PX);
  const img = image(w, h);
  const at = (i, j) => [-f.w / 2 + (i + 0.5) / FELT_PX, -f.d / 2 + (j + 0.5) / FELT_PX];
  for (let j = 0; j < h; j++)
    for (let i = 0; i < w; i++) {
      let cover = 0;
      for (const [sx, sy] of SAMPLES) if (printSdf(f, -f.w / 2 + (i + sx) / FELT_PX, -f.d / 2 + (j + sy) / FELT_PX) <= 0) cover++;
      if (cover < 2) continue;
      const [x, z] = at(i, j);
      const d = -printSdf(f, x, z);
      const n = noise(i, j, 11);
      // woven baize: a 2-px twill of the base tones, a faint nap
      let c = (i + j) % 4 < 2 ? t.felt[0] : t.felt[1];
      if (n < 0.12) c = t.felt[3];
      else if (n > 0.9) c = t.felt[2];
      const edge = Math.max(0, 1 - d / 0.16);
      c = mixHex(c, t.deep, 0.5 * edge * edge);
      P(img, i, j, c);
    }
  const line = (sdf, k) => {
    for (let j = 0; j < h; j++)
      for (let i = 0; i < w; i++) {
        if (img.data[(j * w + i) * 4 + 3] === 0) continue;
        const [x, z] = at(i, j);
        const s = sdf(x, z);
        const a = Math.max(0, 1 - Math.abs(s) * FELT_PX); // ≈ 1-px anti-aliased line
        if (a > 0) B(img, i, j, t.line, k * a);
      }
  };
  // inner border: a fine line 0.06 inside the rail and a second, fainter one 0.03 further in (a printed double rule)
  for (const [inset, k] of [
    [0.06, 0.5],
    [0.09, 0.28],
  ]) {
    const g = { w: f.w - 2 * inset, d: f.d - 2 * inset, frontR: Math.max(0, f.frontR - inset), backR: Math.max(0, f.backR - inset) };
    line((x, z) => printSdf(g, x, z), k);
  }
  // the casino's emblem: the four suits in a row, printed in the line colour (crescent: just in front of the dealer
  // row; oval: the centre of the racetrack); roulette and craps print their own layouts over the felt
  const suitsEmblem = (cz) => {
    const size = 9;
    const gap = 4;
    const total = 4 * size + 3 * gap;
    const x0 = Math.round(w / 2 - total / 2);
    const y0 = Math.round((cz + f.d / 2) * FELT_PX - size / 2);
    SUIT_ORDER.forEach((suit, k) => {
      const ink = { base: t.line, light: t.line, dark: t.line };
      over(img, pip(suit, size, ink, { flat: true }), x0 + k * (size + gap), y0, 0.42);
    });
  };
  if (shape === 'crescent') {
    // the players' arc round the dealer: the design arc (dealer (0.5, 0.10), radii 0.60 × 0.48, ±62°) on the full felt
    // (design → full scale 1.2222, design centre (0.5, 0.5) → (0, 0))
    const k = 1.75 / 1.25 < 2.75 / 2.25 ? 1.75 / 1.25 : 2.75 / 2.25;
    const cz = (0.1 - 0.5) * k;
    const rx = 0.6 * k;
    const rz = 0.48 * k;
    line((x, z) => {
      if (z < cz) return 9;
      const a = Math.atan2(x / rx, (z - cz) / rz);
      if (Math.abs(a) > (62 * Math.PI) / 180) return 9;
      return (Math.hypot(x / rx, (z - cz) / rz) - 1) * Math.min(rx, rz);
    }, 0.55);
    // a second arc just outside it (the printed band players bet behind) and the emblem behind the arc
    line((x, z) => {
      if (z < cz) return 9;
      const a = Math.atan2(x / (rx + 0.06), (z - cz) / (rz + 0.06));
      if (Math.abs(a) > (62 * Math.PI) / 180) return 9;
      return (Math.hypot(x / (rx + 0.06), (z - cz) / (rz + 0.06)) - 1) * Math.min(rx, rz);
    }, 0.3);
    suitsEmblem(cz + rz * 0.45);
  } else if (shape === 'oval') {
    const r = { w: f.w - 0.7, d: f.d - 0.7, frontR: f.frontR - 0.35, backR: f.backR - 0.35 };
    line((x, z) => printSdf(r, x, z), 0.35);
    const r2 = { w: f.w - 0.78, d: f.d - 0.78, frontR: f.frontR - 0.39, backR: f.backR - 0.39 };
    line((x, z) => printSdf(r2, x, z), 0.2);
    suitsEmblem(0.18);
  }
  return img;
}

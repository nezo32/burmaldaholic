// Scratch Cards art (extras.md §6; extras-pvp §7–§8): three ticket themes (Basic "Lucky Miner", Golden "Gold Rush",
// Showdown "Holo Duel"), foil per theme with an embossed coin-slot logo, an 8-frame shimmer per theme, the scratch
// edge autotile (16 neighbour variants of the 4 × 4 sub-tile, per theme) that makes a half-scratched cell look torn,
// scuffs, symbols at 16 / 20 / 40, trio frame, torn corner, burn puff, flakes, final-cell foil, charred cell, scraper
// and the creeper char vignette.
import { CROWN, DIAMOND, EMERALD, GOLD_INGOT, SCRATCH_SYMBOLS } from './icons.mjs';
import { miniCoin } from './coin.mjs';
import {
  K, affine, blend, c, disc, hstrip, iconCell, image, mixHex, outline, over, put, rect, rng, roundCorners, stud, vstrip,
} from './kit.mjs';

/** Ticket layouts (ticket-local GUI px). Cells are where symbols + amounts sit; foil covers exactly a cell. */
export const TICKET = {
  solo: { w: 212, h: 196, cell: [60, 44], gap: 4, grid: [12, 36], header: [6, 30], ribbon: 178 },
  showdown: { w: 86, h: 98, cell: [24, 24], gap: 2, grid: [6, 16], header: [4, 13], ribbon: null },
};
export const cellXY = (layout, i) => {
  const L = TICKET[layout];
  return [L.grid[0] + (i % 3) * (L.cell[0] + L.gap), L.grid[1] + Math.floor(i / 3) * (L.cell[1] + L.gap)];
};

/** Theme materials. */
export const THEMES = {
  basic: {
    paper: '#F4ECD8', guilloche: '#E2D4B8', border: ['#6A6A78', '#C8C8D4', '#9A9AA8'], band: '#0E5A3A', bandLight: '#1E8A56', ribbon: ['#1E9A3E', '#0E5A26', '#6CF08A'],
    cellBg: '#FFF8E8', cellLine: '#EADFC6',
    foil: { base: '#B4B4B4', light: '#D8D8D8', dark: '#8C8C8C', line: '#A6A6A6', logoLight: '#E8E8E8', logoDark: '#7A7A7A', edge: '#5A5A60', speck: '#F0F0F0' },
  },
  gold: {
    paper: '#FFF0C8', guilloche: '#F0D898', border: ['#8A5A08', '#FFD640', '#B07010'], band: '#7A1430', bandLight: '#A8203C', ribbon: ['#D83440', '#8C1834', '#FF9A90'],
    cellBg: '#FFFAEA', cellLine: '#F4E2B0',
    foil: { base: '#E0B040', light: '#FFE070', dark: '#A87818', line: '#D0A030', logoLight: '#FFF4B0', logoDark: '#8A5A08', edge: '#6A4208', speck: '#FFF8D0' },
  },
  showdown: {
    paper: '#ECE4F8', guilloche: '#D8CCF0', border: ['#3A1450', '#BE5AFF', '#783CBE'], band: '#26103C', bandLight: '#4A2474', ribbon: null,
    cellBg: '#FBF8FF', cellLine: '#E4DCF4',
    foil: { base: '#B8A8D8', light: '#E0D8FF', dark: '#7A68A8', line: '#A898CC', logoLight: '#F4ECFF', logoDark: '#5A4A88', edge: '#3A2A5A', speck: '#FFFFFF', holo: true },
  },
};

// ---- ticket -----------------------------------------------------------------------------------------------------------
export function ticket(theme) {
  const T = THEMES[theme];
  const L = theme === 'showdown' ? TICKET.showdown : TICKET.solo;
  const { w, h } = L;
  const img = image(w, h);
  // paper with a guilloche wave pattern
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const wave1 = Math.sin(x * 0.18 + Math.sin(y * 0.09) * 2.2);
      const wave2 = Math.sin(y * 0.21 + Math.cos(x * 0.07) * 2.0);
      put(img, x, y, c(Math.abs(wave1 - wave2) < 0.12 ? T.guilloche : T.paper));
    }
  // border band (4 px): ink, dark, rope, dark
  const [dark, mid, rope] = T.border;
  for (let i = 0; i < 4; i++) {
    const hex = i === 0 ? K.ink : i === 3 ? dark : mid;
    rect(img, i, i, w - 2 * i, 1, hex);
    rect(img, i, h - 1 - i, w - 2 * i, 1, hex);
    rect(img, i, i, 1, h - 2 * i, hex);
    rect(img, w - 1 - i, i, 1, h - 2 * i, hex);
  }
  for (let x = 3; x < w - 3; x += 3) {
    put(img, x, 1, c(rope));
    put(img, x + 1, 2, c(rope));
    put(img, x, h - 2, c(rope));
    put(img, x + 1, h - 3, c(rope));
  }
  for (let y = 3; y < h - 3; y += 3) {
    put(img, 1, y, c(rope));
    put(img, 2, y + 1, c(rope));
    put(img, w - 2, y, c(rope));
    put(img, w - 3, y + 1, c(rope));
  }
  // perforation: notches along the left / right edges
  for (let y = 6; y < h - 6; y += 6)
    for (const x of [0, w - 1]) {
      put(img, x, y, [0, 0, 0, 0]);
      put(img, x, y + 1, [0, 0, 0, 0]);
      put(img, x === 0 ? 1 : w - 2, y, c(K.ink));
      put(img, x === 0 ? 1 : w - 2, y + 1, c(K.ink));
    }
  // header band
  const [hy0, hy1] = L.header;
  rect(img, 5, hy0, w - 10, hy1 - hy0, T.band);
  rect(img, 5, hy0, w - 10, 1, K.gold);
  rect(img, 5, hy1 - 1, w - 10, 1, K.goldShade);
  for (let x = 6; x < w - 6; x += 4) put(img, x, hy0 + 2, c(T.bandLight));
  headerArt(img, theme, L);
  // cells: inset wells
  for (let i = 0; i < 9; i++) {
    const [x, y] = cellXY(theme === 'showdown' ? 'showdown' : 'solo', i);
    const [cw, ch] = L.cell;
    rect(img, x - 1, y - 1, cw + 2, ch + 2, mixHex(T.paper, K.ink, 0.35));
    rect(img, x, y, cw, ch, T.cellBg);
    for (let yy = y + 3; yy < y + ch; yy += 4) for (let xx = x + ((yy / 4) % 2 ? 2 : 0); xx < x + cw; xx += 4) put(img, xx, yy, c(T.cellLine));
    rect(img, x, y, cw, 1, mixHex(T.cellBg, K.ink, 0.25));
    rect(img, x, y, 1, ch, mixHex(T.cellBg, K.ink, 0.18));
  }
  // ribbon (solo tickets): swallow-tailed band for the hint line
  if (L.ribbon) {
    const y0 = L.ribbon;
    const [rb, rd, rl] = T.ribbon;
    rect(img, 10, y0, w - 20, 12, rd);
    rect(img, 12, y0 + 1, w - 24, 10, rb);
    rect(img, 12, y0 + 1, w - 24, 1, rl);
    for (let k = 0; k < 6; k++) {
      put(img, 5 + k, y0 + 2 + k, c(rd));
      put(img, 5 + k, y0 + 9 - k, c(rd));
      put(img, w - 6 - k, y0 + 2 + k, c(rd));
      put(img, w - 6 - k, y0 + 9 - k, c(rd));
    }
    rect(img, 6, y0 + 4, 6, 4, rb);
    rect(img, w - 12, y0 + 4, 6, 4, rb);
    // barcode (no text) in the corner
    const r = rng(theme.length * 31);
    let bx = w - 34;
    while (bx < w - 14) {
      const bw = r() < 0.5 ? 1 : 2;
      rect(img, bx, y0 - 9, bw, 6, K.ink);
      bx += bw + (r() < 0.5 ? 1 : 2);
    }
  }
  // corner seals
  for (const [x, y] of [[8, h - 9], [w - 9, h - 9]]) if (theme !== 'showdown') stud(img, x, y, theme === 'gold' ? K.gold : '#C8C8D4', K.white, 1.8);
  return img;
}

/** Header illustration per theme around a blank title plaque. */
function headerArt(img, theme, L) {
  const { w } = L;
  const [hy0, hy1] = L.header;
  const cy = (hy0 + hy1) >> 1;
  if (theme === 'showdown') {
    rect(img, 20, hy0 + 2, w - 40, hy1 - hy0 - 4, '#140822');
    rect(img, 20, hy0 + 2, w - 40, 1, '#BE5AFF');
    for (const [x, hex] of [[9, '#3D6AB0'], [w - 16, '#D83440']]) {
      rect(img, x, cy - 3, 7, 7, K.ink);
      rect(img, x + 1, cy - 2, 5, 5, hex);
    }
    return;
  }
  // blank plaque in the middle (the ticket name is drawn by code)
  const pw = 112;
  const px = (w - pw) >> 1;
  rect(img, px, hy0 + 4, pw, hy1 - hy0 - 8, K.ink);
  rect(img, px + 1, hy0 + 5, pw - 2, hy1 - hy0 - 10, theme === 'gold' ? '#FFF0C8' : '#F4ECD8');
  rect(img, px + 1, hy0 + 5, pw - 2, 1, K.gold);
  rect(img, px + 1, hy1 - 6, pw - 2, 1, K.goldShade);
  const L2 = theme === 'basic' ? [DIAMOND, EMERALD] : [CROWN, GOLD_INGOT];
  over(img, iconCell(L2[0], 18), 12, cy - 9);
  over(img, iconCell(L2[1], 18), w - 30, cy - 9);
  for (const [x, y] of [[34, cy - 5], [w - 38, cy + 3], [36, cy + 4], [w - 36, cy - 6]]) {
    put(img, x, y, c(K.white));
    for (const [ax, ay] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) put(img, x + ax, y + ay, c(K.gold));
  }
}

// ---- foil ---------------------------------------------------------------------------------------------------------------
/** Foil for one cell (w × h): brushed diagonal lines, top-left sheen, embossed coin-slot logo, holo bands (showdown). */
export function foil(theme, w, h) {
  const F = THEMES[theme].foil;
  const img = image(w, h);
  const streak = rng(theme.length * 101 + w);
  const lines = Array.from({ length: w + 2 * h + 4 }, () => streak());
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const v = lines[x + 2 * y];
      let hex = v < 0.22 ? F.line : v > 0.86 ? F.light : F.base;
      if (F.holo) {
        const band = Math.floor((x + y) / 7) % 4;
        hex = mixHex(hex, ['#FFB0E0', '#B0E0FF', '#FFF0B0', '#D0B0FF'][band], 0.3);
      }
      if (x + y < 10) hex = mixHex(hex, F.light, 0.5);
      put(img, x, y, c(hex));
    }
  // bevel
  rect(img, 0, 0, w, 1, F.light);
  rect(img, 0, 0, 1, h, F.light);
  rect(img, 0, h - 1, w, 1, F.dark);
  rect(img, w - 1, 0, 1, h, F.dark);
  // logo: a coin with a slot (embossed)
  const cx = w / 2;
  const cy = h / 2;
  const r = Math.min(w, h) * 0.26;
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
      if (Math.abs(d - r) < 0.6) put(img, x, y, c(x + y < cx + cy ? F.logoLight : F.logoDark));
      if (Math.abs(d - (r - 1)) < 0.5) put(img, x, y, c(x + y < cx + cy ? F.logoDark : F.logoLight));
    }
  // two sparkle glints
  for (const [gx, gy] of [[Math.round(w * 0.2), Math.round(h * 0.3)], [Math.round(w * 0.8), Math.round(h * 0.72)]]) {
    put(img, gx, gy, c('#FFFFFF'));
    for (const [ax, ay] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) put(img, gx + ax, gy + ay, c(F.light));
  }
  const sw = Math.max(2, Math.round(r * 0.9));
  rect(img, Math.round(cx - sw / 2), Math.round(cy - 1), sw, 1, F.logoDark);
  rect(img, Math.round(cx - sw / 2), Math.round(cy), sw, 1, F.logoLight);
  return img;
}

/** Shimmer overlay (w × h × 8 frames, V + mcmeta frametime 7): a diagonal sheen sweeping the foil. */
export function foilShimmer(w, h) {
  const frames = [];
  for (let f = 0; f < 8; f++) {
    const img = image(w, h);
    const centre = -12 + (f / 7) * (w + h + 24);
    for (let y = 0; y < h; y++)
      for (let x = 0; x < w; x++) {
        const d = Math.abs(x + y * 0.8 - centre);
        if (d < 2) put(img, x, y, [255, 255, 255, 120]);
        else if (d < 5) put(img, x, y, [255, 255, 255, 50]);
      }
    frames.push(img);
  }
  return vstrip(frames);
}

/**
 * Scratch-edge autotile (16 variants × 4 × 4, one row per theme): an OVERLAY drawn on every remaining foil sub-tile;
 * variant = bitmask of SCRATCHED neighbours (N 1, E 2, S 4, W 8). Scratched sides get a curled dark lip and a
 * bright torn speck, so a half-scratched cell looks torn, not pixelated.
 */
export function scratchEdges(theme) {
  const F = THEMES[theme].foil;
  const r = rng(theme.length * 7 + 3);
  const tiles = [];
  for (let m = 0; m < 16; m++) {
    const img = image(4, 4);
    const lip = (x, y) => put(img, x, y, c(F.edge));
    const speck = (x, y) => put(img, x, y, c(F.speck));
    if (m & 1) for (let x = 0; x < 4; x++) (r() < 0.12 ? speck : lip)(x, 0);
    if (m & 2) for (let y = 0; y < 4; y++) (r() < 0.12 ? speck : lip)(3, y);
    if (m & 4) for (let x = 0; x < 4; x++) (r() < 0.12 ? speck : lip)(x, 3);
    if (m & 8) for (let y = 0; y < 4; y++) (r() < 0.12 ? speck : lip)(0, y);
    // ragged corners where two scratched sides meet
    if ((m & 1) && (m & 2)) put(img, 3, 0, [0, 0, 0, 0]);
    if ((m & 2) && (m & 4)) put(img, 3, 3, [0, 0, 0, 0]);
    if ((m & 4) && (m & 8)) put(img, 0, 3, [0, 0, 0, 0]);
    if ((m & 8) && (m & 1)) put(img, 0, 0, [0, 0, 0, 0]);
    tiles.push(img);
  }
  return tiles;
}
/** Transparent pixels of a scratch-edge variant are CUT from the foil (the renderer skips them): see extras.md §6.4. */

/** Scuff overlay (w × h): faint grey scrape marks left on the revealed paper. */
export function scuff(w, h, seed = 5) {
  const img = image(w, h);
  const r = rng(seed);
  for (let i = 0; i < 9; i++) {
    let x = r() * w;
    let y = r() * h;
    const dx = 1.4 + r();
    const dy = (r() - 0.5) * 0.8;
    for (let k = 0; k < 8 + r() * 10; k++) {
      blend(img, x | 0, y | 0, c('#8C8C8C'), 0.22);
      x += dx;
      y += dy;
    }
  }
  return img;
}

// ---- symbols and effects ---------------------------------------------------------------------------------------------------
export const scratchSymbols16 = () => hstrip(SCRATCH_SYMBOLS.map((d) => iconCell(d, 16)));
export const scratchSymbols20 = () => hstrip(SCRATCH_SYMBOLS.map((d) => iconCell(d, 20)));
export const scratchSymbols40 = () => hstrip(SCRATCH_SYMBOLS.map((d) => iconCell(d, 40)));
/** Win variants (40 px, lit palette + gold halo) for the trio emphasis. */
export const scratchSymbols40Win = () => hstrip(SCRATCH_SYMBOLS.map((d) => iconCell(d, 40, { lit: true, glow: K.gold, glowK: 0.6 })));

/** Trio frame nine-slice (16 × 16, border 4): gold with corner gems. */
export function trioFrame() {
  const img = image(16, 16);
  for (let i = 0; i < 4; i++) {
    const hex = i === 0 ? K.ink : i === 1 ? K.goldLight : i === 2 ? K.gold : K.goldShade;
    rect(img, i, i, 16 - 2 * i, 1, hex);
    rect(img, i, 15 - i, 16 - 2 * i, 1, hex);
    rect(img, i, i, 1, 16 - 2 * i, hex);
    rect(img, 15 - i, i, 1, 16 - 2 * i, hex);
  }
  for (const [x, y] of [[1, 1], [14, 1], [1, 14], [14, 14]]) {
    put(img, x, y, c(K.white));
  }
  return img;
}

/** Torn corner (24 × 24 × 3): the top-right corner folds down on a losing card. */
export function tornCorner() {
  return [4, 10, 16].map((s) => {
    const img = image(24, 24);
    for (let y = 0; y < s; y++)
      for (let x = 24 - s + y; x < 24; x++) put(img, x, y, [0, 0, 0, 0]);
    // the folded flap (back of the paper, shaded)
    for (let y = 0; y < s; y++)
      for (let x = 24 - s; x < 24 - s + (s - y); x++) {
        if (x < 24 - s + y) continue;
        put(img, x, y, c(y === 0 || x === 24 - s ? K.ink : '#D8CCB0'));
      }
    for (let k = 0; k < s; k++) put(img, 24 - s + k, k, c(K.ink));
    for (let k = 0; k < s; k++) blend(img, 24 - s + k - 1, k + 1, c(K.ink), 0.35);
    return img;
  });
}

/** Burn impact puff (16 × 16 × 4). */
export function explosionPuff() {
  return [0, 1, 2, 3].map((f) => {
    const img = image(16, 16);
    const rr = rng(90 + f);
    const R = 3 + f * 1.8;
    for (let i = 0; i < 26; i++) {
      const a = rr() * Math.PI * 2;
      const d = rr() * R;
      const x = 8 + Math.cos(a) * d;
      const y = 8 + Math.sin(a) * d;
      const hex = f < 2 ? (d < R * 0.5 ? '#FFFFFF' : d < R * 0.8 ? '#FFE070' : '#FF7A1A') : d < R * 0.6 ? '#6A6A72' : '#3A3A40';
      disc(img, x, y, f < 2 ? 1.6 : 2.1, c(hex), f === 3 ? 0.6 : 1);
    }
    return img;
  });
}

/** GUI flakes (4 × 4 each): 4 foil flakes, 2 embers, 2 sparks → 32 × 4. Per theme foil colours in row order. */
export function flakes() {
  const tiles = [];
  const F = THEMES.basic.foil;
  const foilShapes = [['.##.', '####', '###.', '.#..'], ['##..', '###.', '.###', '..#.'], ['.#..', '###.', '####', '.##.'], ['..#.', '.##.', '###.', '##..']];
  foilShapes.forEach((g, i) => {
    const img = image(4, 4);
    g.forEach((row, y) => [...row].forEach((ch, x) => ch === '#' && put(img, x, y, c((x + y + i) % 3 === 0 ? F.light : (x + y) % 3 === 1 ? F.dark : F.base))));
    tiles.push(img);
  });
  for (const hex of ['#FF7A1A', '#FFE070']) {
    const img = image(4, 4);
    rect(img, 1, 1, 2, 2, hex);
    put(img, 1, 1, c(K.white));
    tiles.push(img);
  }
  for (const hex of [K.gold, K.lilac]) {
    const img = image(4, 4);
    put(img, 1, 0, c(hex));
    put(img, 0, 1, c(hex));
    put(img, 1, 1, c(K.white));
    put(img, 2, 1, c(hex));
    put(img, 1, 2, c(hex));
    tiles.push(img);
  }
  return hstrip(tiles);
}

/** Final-cell foil (24 × 24 × 6, V + mcmeta): gold foil with a travelling shimmer; the "?" is drawn as text. */
export function foilFinal() {
  const frames = [];
  const S = 24;
  const base = foil('gold', S, S);
  for (let f = 0; f < 6; f++) {
    const img = image(S, S);
    over(img, base);
    const centre = -8 + f * 12;
    for (let y = 0; y < S; y++)
      for (let x = 0; x < S; x++) {
        const d = Math.abs(x + y - centre);
        if (d < 2) blend(img, x, y, c(K.white), 0.6);
        else if (d < 4) blend(img, x, y, c(K.goldLight), 0.4);
      }
    // question-mark well (dark disc, text goes on top)
    disc(img, S / 2, S / 2, 6.5, c(K.goldDeep), 0.85);
    disc(img, S / 2, S / 2, 5.5, c('#5C3A00'), 0.85);
    frames.push(img);
  }
  return vstrip(frames);
}

/** Charred cell (24 × 24 × 2, V + mcmeta frametime 10): burnt paper with a flickering ember rim. */
export function charred() {
  return vstrip([0, 1].map((f) => {
    const img = image(24, 24);
    const r = rng(77);
    for (let y = 0; y < 24; y++)
      for (let x = 0; x < 24; x++) {
        const edge = Math.min(x, y, 23 - x, 23 - y);
        const n = r();
        let hex = n < 0.5 ? '#1A1412' : n < 0.85 ? '#241A16' : '#3A2C24';
        if (edge < 2 && r() < 0.6) hex = f ? '#FF6020' : '#C83A08';
        if (edge < 1 && r() < 0.4) hex = f ? '#FFE070' : '#FF7A1A';
        put(img, x, y, c(hex));
      }
    for (let i = 0; i < 6; i++) put(img, 3 + ((r() * 18) | 0), 3 + ((r() * 18) | 0), c(f ? '#FFB040' : '#8A3A10'));
    return img;
  }));
}

/** Scraper cursor (16 × 16): the Lucky Coin on its edge, tilted 30°. */
export function scraper() {
  const coin = miniCoin('heads', 14);
  const img = image(16, 16);
  over(img, affine(coin, 16, 16, { sx: 0.45, rot: 30, px: 7, py: 7, ox: 8, oy: 8 }));
  return outline(img, c(K.ink), { k: 0.9 });
}

/** Creeper char vignette nine-slice (32 × 32, border 12): black burnt edges growing inward. */
export function charVignette() {
  const img = image(32, 32);
  const r = rng(13);
  for (let y = 0; y < 32; y++)
    for (let x = 0; x < 32; x++) {
      const d = Math.min(x, y, 31 - x, 31 - y) + r() * 3;
      if (d > 11) continue;
      const a = 1 - d / 11;
      blend(img, x, y, c(d < 3 ? '#0A0606' : d < 6 ? '#1A1412' : '#3A1A0A'), Math.min(1, a * 1.2));
      if (d > 5 && d < 6.5 && r() < 0.3) put(img, x, y, c('#FF6020'));
    }
  return img;
}

/** Ticket shadow (for the slide-in): a soft ink rectangle, 16 × 16 nine-slice border 6. */
export function ticketShadow() {
  const img = image(16, 16);
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++) {
      const d = Math.min(x, y, 15 - x, 15 - y);
      put(img, x, y, [24, 10, 40, Math.min(150, 25 * (d + 1))]);
    }
  return roundCorners(img, 0, 0, 16, 16, 2);
}

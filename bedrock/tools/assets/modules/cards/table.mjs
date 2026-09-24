// Table art (visual/cards.md §2): the felt + padded rail per shape and theme, and the themed room backdrops.
//   crescent — blackjack, UTH, baccarat: a straight dealer edge at the top, a padded rail along the curve
//   oval     — Texas Hold'em: a padded rail all round
// Prints (bet spots, boxes, board slots, the insurance band) are separate white sprites the screen tints with
// the theme's print colour, so one felt serves every game and the layout can move without new art.
import { B, C, P, R, bayer, box, image, insideDistance, mask, mixHex, noise } from './px.mjs';
import { THEMES } from './theme.mjs';

const pow = (v, e) => Math.abs(v) ** e;

/** Shape predicate in table px (y down). */
function shapeOf(shape, w, h) {
  if (shape === 'crescent') {
    const rx = w / 2;
    const ry = h - 0.5;
    return (x, y) => y < 0 || pow((x - rx) / rx, 2.6) + pow(y / ry, 2.6) <= 1;
  }
  const rx = w / 2;
  const ry = h / 2;
  return (x, y) => pow((x - rx) / rx, 3.2) + pow((y - ry) / ry, 3.2) <= 1;
}

const RAIL = 12; // padded rail width (px) at full size; compact tables use 9

/**
 * Felt + rail. Returns an RGBA image; transparent outside the table. Lit from the top-left; the pad is a bump whose
 * normal is taken from the distance field, so the curve shades itself.
 */
export function tableArt(shape, theme, w, h) {
  const t = THEMES[theme];
  const rail = w >= 360 ? RAIL : 9;
  const inside = shapeOf(shape, w, h);
  const mk = mask(w, h + (shape === 'crescent' ? 0 : 0), (x, y) => inside(x, y), 3);
  // crescent: the top edge is not railed — pretend the table continues upwards
  const mkD = shape === 'crescent' ? { ...mk, at: (x, y) => (y < 0 ? (x >= 0 && x < w ? 1 : 0) : mk.at(x, y)) } : mk;
  const dist = insideDistance(shape === 'crescent' ? padTop(mk, rail + 4) : mk);
  const off = shape === 'crescent' ? rail + 4 : 0;
  const D = (x, y) => dist.at(x, y + off);
  const img = image(w, h);
  const [lx, ly] = [-0.55, -0.83];
  const cx = w / 2;
  const cy = shape === 'crescent' ? h * 0.42 : h / 2;
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      if (!mkD.at(x, y)) continue;
      const d = D(x, y);
      if (d < 1.2) {
        P(img, x, y, t.padDark === '#140E1A' ? '#07040A' : '#180A28');
        continue;
      }
      // inward unit normal of the edge (gradient of the distance field)
      let gx = D(x + 1, y) - D(x - 1, y);
      let gy = D(x, y + 1) - D(x, y - 1);
      const gl = Math.hypot(gx, gy) || 1;
      gx /= gl;
      gy /= gl;
      if (d < rail) {
        // pad profile: a bump; slope > 0 on the outer half → faces outward (−g)
        const u = (d - 1.2) / (rail - 1.2);
        const slope = Math.cos(u * Math.PI);
        const lit = -(gx * lx + gy * ly) * slope; // −g · L × slope
        const k = 0.5 + 0.5 * lit;
        let c = k > 0.8 ? t.padLight : k > 0.56 ? mixHex(t.pad, t.padLight, 0.45) : k > 0.4 ? t.pad : k > 0.22 ? mixHex(t.pad, t.padDark, 0.5) : t.padDark;
        // specular fleck along the crest where it faces the light
        if (u > 0.3 && u < 0.45 && lit > 0.35 && bayer(x, y) < 0.5) c = mixHex(t.padLight, '#FFFFFF', 0.25);
        // stitching: dotted seams near both edges of the pad
        const seam = Math.abs(d - 2.6) < 0.5 || Math.abs(d - (rail - 1.8)) < 0.5;
        if (seam && (x + y) % 3 === 0) c = t.stitch;
        P(img, x, y, c);
        continue;
      }
      if (d < rail + 2) {
        // brass/gold trim: lit on the side facing the light
        const lit = gx * lx + gy * ly; // inner edge faces inward (+g)
        P(img, x, y, d < rail + 1 ? (lit < -0.1 ? t.trimShade : t.trim) : lit < -0.1 ? t.trim : t.trimShade);
        continue;
      }
      // felt: spotlight bands (dithered), weave noise, inner shadow along the rail
      const r = Math.hypot((x - cx) / (w * 0.55), (y - cy) / (h * 0.62));
      const spot = Math.max(0, 1 - r); // 0 at the rim … 1 centre
      let c = t.felt;
      const bands = spot * 2.4 + (bayer(x, y) - 0.5) * 0.5;
      if (bands > 1.55) c = t.feltLight;
      else if (bands > 0.95) c = mixHex(t.felt, t.feltLight, 0.5);
      else if (bands < 0.25) c = mixHex(t.felt, t.feltDark, 0.5);
      const n = noise(x, y, 7);
      if (n < 0.06) c = mixHex(c, t.feltDark, 0.35);
      else if (n > 0.965) c = mixHex(c, t.feltLight, 0.4);
      const inner = d - (rail + 2);
      if (inner < 5) {
        const k = (5 - inner) / 5;
        if (bayer(x, y) < k * 0.9) c = mixHex(c, t.feltDark, 0.55);
        if (inner < 1) c = t.feltDark;
      }
      P(img, x, y, c);
    }
  if (shape === 'crescent') dealerEdge(img, t, w);
  return img;
}

/** A mask padded upward by `n` rows (inside), so the crescent's straight top edge has no rail. */
function padTop(mk, n) {
  const w = mk.w;
  const h = mk.h + n;
  const m = new Uint8Array(w * h);
  for (let y = 0; y < n; y++) for (let x = 0; x < w; x++) m[y * w + x] = mk.at(x, 0);
  m.set(mk.m, n * w);
  return { w, h, m, at: (x, y) => (x < 0 || y < 0 || x >= w || y >= h ? 0 : m[y * w + x]) };
}

/** The dealer's edge on a crescent: a 6 px wood strip with a trim line and the ink edge. */
function dealerEdge(img, t, w) {
  for (let x = 0; x < w; x++) {
    if (!img.data[(1 * w + x) * 4 + 3] && !img.data[(8 * w + x) * 4 + 3]) continue;
    P(img, x, 0, '#180A28');
    for (let y = 1; y < 6; y++) P(img, x, y, y === 1 ? t.woodLight : (x * 3 + y * 7) % 11 === 0 ? t.woodDark : t.wood);
    P(img, x, 6, t.trim);
    P(img, x, 7, t.trimShade);
    B(img, x, 8, t.feltDark, 0.8);
    B(img, x, 9, t.feltDark, 0.4);
  }
}

// ---- backdrops (428 × 240) -----------------------------------------------------------------------------------
export const BACKDROP = [428, 240];

export function backdrop(theme) {
  const [w, h] = BACKDROP;
  const t = THEMES[theme];
  const img = image(w, h);
  if (theme === 'village') villageRoom(img, t, w, h);
  else if (theme === 'bastion') bastionRoom(img, t, w, h);
  else endRoom(img, t, w, h);
  vignette(img, w, h, theme === 'end' ? 0.55 : 0.45);
  return img;
}

function vignette(img, w, h, k) {
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const r = Math.hypot((x - w / 2) / (w / 2), (y - h / 2) / (h / 2));
      const a = Math.max(0, r - 0.7) / 0.7;
      if (a > 0 && bayer(x, y) < a * 1.4) B(img, x, y, '#0A0412', k * Math.min(1, a * 1.6));
    }
}

function glow(img, cx, cy, r, c, k) {
  for (let y = Math.floor(cy - r); y <= cy + r; y++)
    for (let x = Math.floor(cx - r); x <= cx + r; x++) {
      const d = Math.hypot(x - cx, y - cy) / r;
      if (d >= 1) continue;
      const q = Math.floor((1 - d) * 4) / 4; // quantised rings
      if (q > 0) B(img, x, y, c, k * q);
    }
}

function lantern(img, x, y, t, soul = false) {
  const flame = soul ? '#5CE8E0' : '#FFD640';
  const flameHot = soul ? '#D8FFFA' : '#FFF4B0';
  glow(img, x + 4, y + 9, 26, soul ? '#5CE8E0' : t.glow, 0.28);
  R(img, x + 3, y - 6, 2, 6, '#3A3A44'); // chain
  P(img, x + 3, y - 4, '#5A5A66');
  R(img, x + 1, y, 6, 2, '#3A3A44');
  R(img, x, y + 2, 8, 9, '#2A2A34');
  R(img, x + 1, y + 3, 6, 7, flame);
  R(img, x + 3, y + 4, 2, 4, flameHot);
  R(img, x + 1, y + 11, 6, 1, '#3A3A44');
  box(img, x, y + 2, 8, 9, '#180A28');
}

function villageRoom(img, t, w, h) {
  // wallpaper with a damask lozenge pattern
  for (let y = 0; y < 170; y++)
    for (let x = 0; x < w; x++) {
      const u = (x + ((y / 16) | 0) * 8) % 16;
      const v = y % 16;
      const lz = Math.abs(u - 8) + Math.abs(v - 8);
      let c = t.wall;
      if (lz === 6) c = t.wallPattern;
      else if (lz < 3) c = t.wallLight;
      if (u === 8 && v === 8) c = '#B07010';
      if (x % 64 === 0) c = mixHex(t.wall, '#000000', 0.25);
      P(img, x, y, c);
    }
  // crown moulding
  R(img, 0, 0, w, 6, t.wainscotDark);
  R(img, 0, 6, w, 2, t.wainscotLight);
  R(img, 0, 8, w, 1, '#B07010');
  // wainscot panels
  R(img, 0, 170, w, h - 170, t.wainscot);
  R(img, 0, 170, w, 2, t.wainscotLight);
  R(img, 0, 172, w, 1, '#B07010');
  for (let x = 6; x < w; x += 54) {
    R(img, x, 178, 46, 40, t.wainscotDark);
    R(img, x + 1, 179, 44, 38, t.wainscot);
    R(img, x + 1, 179, 44, 1, t.wainscotLight);
    R(img, x + 1, 179, 1, 38, t.wainscotLight);
    for (let yy = 182; yy < 216; yy += 5) R(img, x + 4, yy, 38, 1, mixHex(t.wainscot, t.wainscotDark, 0.4));
  }
  R(img, 0, 222, w, h - 222, t.floor);
  for (let x = 0; x < w; x += 24) R(img, x, 222, 1, h - 222, '#1A0C06');
  R(img, 0, 222, w, 1, '#1A0C06');
  for (const lx of [20, 110, 310, 400]) lantern(img, lx, 14, t);
  // framed paintings between the lamps (emerald landscape, a chip still life)
  painting(img, 150, 20, 40, 28, ['#7EC0EE', '#5FC23A', '#2E7D32']);
  painting(img, 238, 20, 40, 28, ['#3C1E2E', '#D83440', '#FFD640']);
}

function painting(img, x, y, w, h, [sky, hill, deep]) {
  R(img, x, y, w, h, '#B07010');
  R(img, x + 1, y + 1, w - 2, h - 2, '#FFD640');
  R(img, x + 3, y + 3, w - 6, h - 6, sky);
  for (let i = 0; i < w - 6; i++) {
    const hh = Math.round(5 + 3 * Math.sin(i / 5));
    R(img, x + 3 + i, y + h - 3 - hh, 1, hh, hill);
    R(img, x + 3 + i, y + h - 5, 1, 2, deep);
  }
  box(img, x, y, w, h, '#180A28');
}

function bastionRoom(img, t, w, h) {
  // polished blackstone bricks with gilded seams
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const row = (y / 8) | 0;
      const bx = (x + (row % 2) * 8) % 16;
      const by = y % 8;
      let c = noise(x >> 1, y >> 1, 3) < 0.5 ? t.wall : t.wallLight;
      if (by === 0 || bx === 0) c = '#120C14';
      else if (by === 1 || bx === 1) c = mixHex(t.wallLight, '#FFFFFF', 0.08);
      const gild = noise((x + (row % 2) * 8) >> 4, row, 11) < 0.07;
      if (gild && (by === 0 || bx === 0)) c = noise(x, y, 5) < 0.5 ? '#FFB020' : '#B07010';
      if (gild && by > 1 && bx > 1 && (bx + by) % 5 === 0) c = '#E8A020';
      P(img, x, y, c);
    }
  // gold block frieze and hanging chains with soul lanterns
  for (let x = 0; x < w; x++) {
    R(img, x, 0, 1, 7, (x >> 3) % 2 ? '#FFD640' : '#F0B820');
    P(img, x, 7, '#B07010');
    P(img, x, 8, '#180A28');
  }
  for (const lx of [20, 110, 310, 400]) lantern(img, lx, 16, t, true);
  // lava glow from below
  for (let y = 150; y < h; y++) {
    const k = ((y - 150) / (h - 150)) ** 1.5;
    for (let x = 0; x < w; x++) if (bayer(x, y) < k) B(img, x, y, t.glow, 0.35 + 0.25 * k);
  }
  for (let x = 0; x < w; x++) {
    const hh = 4 + Math.round(2 * Math.sin(x / 9) + 1.5 * Math.sin(x / 3.7));
    R(img, x, h - hh, 1, hh, x % 7 === 0 ? '#FFD640' : '#FF7A1A');
  }
  // crimson banners
  for (const bx of [150, 262]) {
    R(img, bx, 10, 16, 40, '#8C1834');
    R(img, bx + 1, 10, 14, 38, '#B02838');
    for (let i = 0; i < 8; i++) {
      P(img, bx + i, 50 + (i >> 1), '#8C1834');
      P(img, bx + 15 - i, 50 + (i >> 1), '#8C1834');
    }
    R(img, bx + 5, 20, 6, 6, '#FFD640');
    R(img, bx + 6, 22, 1, 2, '#180A28');
    R(img, bx + 9, 22, 1, 2, '#180A28');
    R(img, bx - 1, 9, 18, 2, '#FFD640');
  }
}

function endRoom(img, t, w, h) {
  // void sky with stars
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      let c = y < 90 ? '#0A0414' : mixHex('#0A0414', t.wall, Math.min(1, (y - 90) / 60));
      const n = noise(x, y, 21);
      if (y < 120 && n > 0.992) c = n > 0.997 ? '#FFFFFF' : '#D696FF';
      P(img, x, y, c);
    }
  // distant end-stone island silhouette
  for (let x = 0; x < w; x++) {
    const hh = Math.round(18 + 6 * Math.sin(x / 23) + 4 * Math.sin(x / 7.3));
    R(img, x, 118 - hh, 1, hh, '#2A2438');
    P(img, x, 118 - hh, '#3E3656');
  }
  // purpur wall with pillars
  for (let y = 118; y < h; y++)
    for (let x = 0; x < w; x++) {
      const bx = x % 8;
      const by = (y - 118) % 8;
      let c = (bx + by) % 7 === 0 ? t.wainscotLight : t.wainscot;
      if (bx === 0 || by === 0) c = t.wainscotDark;
      P(img, x, y, c);
    }
  for (const px of [0, 60, 150, 262, 352, 412]) {
    R(img, px, 100, 16, h - 100, t.woodLight);
    for (let y = 100; y < h; y += 4) R(img, px + 2, y, 12, 1, t.wood);
    R(img, px, 100, 1, h - 100, t.wainscotDark);
    R(img, px + 15, 100, 1, h - 100, t.wainscotDark);
    R(img, px - 2, 96, 20, 4, t.woodLight);
    R(img, px - 2, 99, 20, 1, t.wainscotDark);
  }
  // end rods (glowing white sticks) on the pillars
  for (const px of [66, 156, 268, 358]) {
    glow(img, px + 1, 70, 20, t.glow, 0.25);
    R(img, px, 56, 3, 30, '#F4ECF8');
    R(img, px + 1, 56, 1, 30, '#FFFFFF');
    R(img, px - 1, 84, 5, 3, '#8A6A9A');
  }
  // chorus stalks in the corners
  for (const [x0, dir] of [[26, 1], [404, -1]]) {
    let x = x0;
    for (let y = h - 1; y > 150; y -= 3) {
      R(img, x, y - 3, 3, 3, '#8A5A9A');
      P(img, x + 1, y - 2, '#B07ACA');
      if ((y >> 2) % 5 === 0) x += dir * 3;
    }
    R(img, x - 1, 146, 5, 5, '#D8B8E8');
  }
}

export { C };

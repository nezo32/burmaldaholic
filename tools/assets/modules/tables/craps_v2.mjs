// Craps table UI v2 art (docs/architecture/ui-v2.md §9, lane D; REDESIGN.md §7.6): the dice of the v2 felt at the three
// tiers (14 / 18 / 22 px cells: T1 / T2 / T3, faces 1–6 then 8 blank tumble frames, never a scaled sprite) and the puck
// at 12 / 16 / 20 px (5 flip frames: OFF, flip, edge, flip, ON — the same order as `craps/puck`). Words (ON / OFF)
// stay out of the art (drawn by the screen, translated). Deterministic, pure.
import { BONE, INK } from './theme.mjs';
import { P, R, image, inked, rrect, textC } from './draw.mjs';
import { tumble } from './dice.mjs';

const PIP = '#2A1A3C';
/** Pip centres on a unit face (0..1), per face. */
const PIPS = {
  1: [[0.5, 0.5]],
  2: [[0.78, 0.22], [0.22, 0.78]],
  3: [[0.78, 0.22], [0.5, 0.5], [0.22, 0.78]],
  4: [[0.22, 0.22], [0.78, 0.22], [0.22, 0.78], [0.78, 0.78]],
  5: [[0.22, 0.22], [0.78, 0.22], [0.5, 0.5], [0.22, 0.78], [0.78, 0.78]],
  6: [[0.22, 0.22], [0.78, 0.22], [0.22, 0.5], [0.78, 0.5], [0.22, 0.78], [0.78, 0.78]],
};

/** The v2 die cell sizes (inked) and the matching puck sizes per tier. */
export const DIE_SIZES = [14, 18, 22];
export const PUCK_SIZES = [12, 16, 20];

/** Die face of `cell` px (inked, 1 px ink all round): bone face with a lower-right depth edge, square-ish pips. */
export function die(face, cell) {
  const n = cell - 2;
  const img = image(n, n);
  rrect(img, 1, 1, n - 1, n - 1, '#A090C0', 2);
  rrect(img, 0, 0, n - 1, n - 1, BONE, 2);
  R(img, 2, 0, n - 5, 1, '#FFFFFF');
  R(img, 0, 2, 1, n - 5, '#FFFFFF');
  const f = n - 1;
  const ps = n >= 18 ? 3 : 2;
  for (const [u, v] of PIPS[face]) {
    const x = Math.round(u * (f - 1) - (ps - 1) / 2);
    const y = Math.round(v * (f - 1) - (ps - 1) / 2);
    const red = face === 1;
    const col = red ? '#D83440' : PIP;
    const size = red ? ps + (n >= 14 ? 1 : 0) : ps;
    const ox = red ? x - (size - ps) : x;
    R(img, ox, y, size, size, col);
    if (size >= 3) {
      P(img, ox, y, red ? '#8C1834' : '#6A5A7A');
      P(img, ox + size - 1, y + size - 1, red ? '#FF6E6A' : '#8C7CA8');
    }
  }
  return inked(img, INK, 1);
}

/** One sheet row of faces 1–6 + two empty cells, one row of 8 tumble frames: `cell × 8` wide, `cell × 2` high. */
export function diceSheet(cell) {
  const img = image(cell * 8, cell * 2);
  for (let f = 1; f <= 6; f++) put(img, die(f, cell), (f - 1) * cell, 0);
  for (let t = 0; t < 8; t++) put(img, tumble(t, cell, cell * 0.29), t * cell, cell);
  return img;
}

/** Puck frames of `size` px stacked vertically (5 frames). */
export function puckSheet(size) {
  const r = (size - 2) / 2;
  const frame = (on, sx) => {
    const img = image(size - 2, size - 2);
    const face = on ? [BONE, '#C0B0DC', '#FFFFFF'] : ['#26202C', '#0C0A10', '#4A4050'];
    const ringC = on ? '#26202C' : BONE;
    for (let y = 0; y < size - 2; y++)
      for (let x = 0; x < size - 2; x++) {
        const dx = (x + 0.5 - r) / ((r - 0.5) * sx);
        const dy = (y + 0.5 - r) / (r - 0.5);
        const d = Math.hypot(dx, dy);
        if (d > 1) continue;
        let col = face[0];
        if (d > 0.86) col = face[1];
        else if (d > 0.72 && d <= 0.82) col = ringC;
        if (d > 0.86 && dx + dy < -0.6) col = face[2];
        P(img, x, y, col);
      }
    return inked(img, INK, 1);
  };
  const edge = () => {
    const img = image(size - 2, size - 2);
    const w = Math.max(3, Math.round(size / 4));
    rrect(img, Math.round((size - 2 - w) / 2), 0, w, size - 2, '#8C7CA8', 1);
    R(img, Math.round((size - 2) / 2), 1, 1, size - 4, '#FFFFFF');
    return inked(img, INK, 1);
  };
  const frames = [frame(false, 1), frame(false, 0.5), edge(), frame(true, 0.5), frame(true, 1)];
  const img = image(size, size * frames.length);
  frames.forEach((f, i) => put(img, f, 0, i * size));
  return img;
}

function put(dst, src, ox, oy) {
  for (let y = 0; y < src.h; y++)
    for (let x = 0; x < src.w; x++) {
      const i = (y * src.w + x) * 4;
      if (src.data[i + 3] === 0) continue;
      const j = ((oy + y) * dst.w + ox + x) * 4;
      for (let k = 0; k < 4; k++) dst.data[j + k] = src.data[i + k];
    }
}

// ---- in-world layout print (ui-v2.md §7.6, lane D) -------------------------------------------------------------------
/**
 * Geometry of the in-world craps print, in texture px (the Java mirror is `games/craps/present/CrapsBoardGeometry`, same
 * fractions as the v2 screen's `CrapsLayout`): Don't Come, six point boxes, Come, Field, Don't Pass bar, Pass line.
 */
export const PRINT_W = 256;
export const PRINT_H = 168;
export function printGeometry() {
  const m = 4;
  const gap = 3;
  const w = PRINT_W - 2 * m;
  const h = PRINT_H - 2 * m;
  const avail = h - 4 * gap;
  const placeH = Math.round(avail * 0.28);
  const comeH = Math.round(avail * 0.245);
  const fieldH = Math.round(avail * 0.18);
  const dpH = Math.round(avail * 0.105);
  const passH = avail - placeH - comeH - fieldH - dpH;
  const dcW = Math.round(w * 0.17);
  const px0 = m + dcW + gap;
  const pw = m + w - px0;
  const points = [4, 5, 6, 8, 9, 10].map((p, i) => {
    const a = px0 + Math.floor((pw * i) / 6);
    const b = px0 + Math.floor((pw * (i + 1)) / 6);
    return { p, x: a, y: m, w: b - a - (i < 5 ? gap : 0), h: placeH };
  });
  const comeY = m + placeH + gap;
  const fieldY = comeY + comeH + gap;
  const dpY = fieldY + fieldH + gap;
  const passY = dpY + dpH + gap;
  return {
    dontCome: { x: m, y: m, w: dcW, h: placeH + gap + comeH },
    points,
    come: { x: px0, y: comeY, w: pw, h: comeH },
    field: { x: m, y: fieldY, w, h: fieldH },
    dontPass: { x: m, y: dpY, w, h: dpH },
    pass: { x: m, y: passY, w, h: passH },
  };
}

/** The print: bone lines, the point numbers ×2, the field numbers (2 and 12 gold), the Pass line's gold edge. No words. */
export function boardPrint() {
  const img = image(PRINT_W, PRINT_H);
  const g = printGeometry();
  const line = (b, hex, k = 0.85) => {
    R(img, b.x, b.y, b.w, 1, hex, k);
    R(img, b.x, b.y + b.h - 1, b.w, 1, hex, k);
    R(img, b.x, b.y, 1, b.h, hex, k);
    R(img, b.x + b.w - 1, b.y, 1, b.h, hex, k);
  };
  const BONE_P = '#E8D9A0';
  const GOLD_P = '#FFD640';
  line(g.dontCome, BONE_P);
  for (const b of g.points) {
    line(b, BONE_P);
    textC(img, String(b.p), b.x + b.w / 2, b.y + 11, BONE_P, { s: 2 });
  }
  line(g.come, BONE_P);
  line(g.field, BONE_P);
  const nums = [2, 3, 4, 9, 10, 11, 12];
  const fx0 = g.field.x + Math.round(g.field.w * 0.2);
  const fw = g.field.x + g.field.w - 6 - fx0;
  nums.forEach((n, i) => {
    const cx = fx0 + (fw * (2 * i + 1)) / (2 * nums.length);
    textC(img, String(n), cx, g.field.y + 8, n === 2 || n === 12 ? GOLD_P : BONE_P);
  });
  line(g.dontPass, BONE_P);
  line(g.pass, GOLD_P, 1);
  line({ x: g.pass.x + 1, y: g.pass.y + 1, w: g.pass.w - 2, h: g.pass.h - 2 }, GOLD_P, 0.45);
  return img;
}

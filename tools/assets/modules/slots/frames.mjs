// Symbol frame generators (animation/slots.md §3.2): base, blur, win ×8, idle ×6, land ×2 — all derived from the
// 16 × 16 art by code transforms, so every size (40 / 32 / 16 px, glyph cells, strips) stays consistent.
//
// A recipe maps frame index → partial transform/effect params; a symbol's win loop is the composition of its
// recipes (symbols.mjs `win`), the idle flourish is one recipe (`idle`). Loops are seamless (frame N ≡ frame 0).
import { art } from './art.mjs';
import {
  affine, blend, brighten, clamp, col, fade, halo, image, outline, over, put, rng, ring, shadow, shine, star,
} from './raster.mjs';

export const INK = col('#180A28');
export const GOLD = col('#FFD640');
const WHITE = [255, 255, 255, 255];

const TAU = Math.PI * 2;
const wave = (i, n, phase = 0) => Math.sin(((i + phase) / n) * TAU);
const tri = (i, n) => 1 - Math.abs(((2 * i) / n) % 2 - 1); // 0..1..0 over n frames

/** Seeded glint positions around a cell (cosmetic; seed = symbol id hash). */
function hash(s) {
  let h = 2166136261;
  for (const ch of s) h = Math.imul(h ^ ch.charCodeAt(0), 16777619);
  return h >>> 0;
}

// ---- recipes: (i, n, sym) → params ------------------------------------------------------------------------------
// params: sx, sy, rot, dx, dy, shear (px at the top, in 16-px units), lit (bool), bright (≥1), glow (halo alpha 0..1),
// ring (0..1 progress of an expanding ring), shine (0..1 band position), fx: [(img, ctx) => void] overlay painters.
const R = {
  pulse: (i, n) => ({ sx: 1 + 0.06 * tri(i, n), sy: 1 + 0.06 * tri(i, n), glow: 0.35 + 0.45 * tri(i, n), bright: 1 + 0.12 * tri(i, n) }),
  ring: (i, n) => ({ ring: i / n }),
  glow: (i, n) => ({ glow: 0.4 + 0.5 * tri(i, n) }),
  sparkle: (i, n, sym) => ({ fx: [(img, c) => glints(img, c, sym, i, n)] }),
  wings: (i, n) => ({ sx: 1 + 0.1 * tri(i, n), glow: 0.3 + 0.6 * tri(i, n), lit: tri(i, n) > 0.4 }),
  needle: (i, n) => ({ rot: 6 * wave(i, n), lit: i % 2 === 0, glow: 0.35 + 0.35 * tri(i, n) }),
  lid: (i) => ({ dy: -[0, 1, 2, 2, 1, 0, 0, 0][i % 8], sy: i % 8 === 5 ? 0.92 : 1, sx: i % 8 === 5 ? 1.05 : 1, lit: i % 8 >= 1 && i % 8 <= 4 }),
  coins: (i, n) => ({ fx: [(img, c) => coins(img, c, i, n)] }),
  facets: (i, n) => ({ shine: i / n, bright: 1 + 0.1 * tri(i, n), lit: i % 4 === 2 }),
  tilt: (i, n) => ({ rot: 8 * wave(i, n), lit: tri(i, n) > 0.5 }),
  bounce: (i) => ({ dy: -[0, 2, 3, 3, 2, 0, 0, 0][i % 8], sy: [1, 1.04, 1.02, 1, 1.02, 0.9, 0.96, 1][i % 8], sx: [1, 0.97, 1, 1, 1, 1.08, 1.03, 1][i % 8] }),
  wiggle: (i) => ({ rot: [0, 8, -8, 8, -8, 4, -2, 0][i % 8] }),
  gust: (i, n) => ({ shear: 2.5 * wave(i, n), lit: tri(i, n) > 0.6 }),
  berries: (i) => ({ dy: -[0, 1, 2, 1, 0, 1, 0, 0][i % 8], sy: [1, 1, 1, 1, 0.94, 1, 0.97, 1][i % 8] }),
  slosh: (i, n) => ({ shear: 1.2 * wave(i, n), lit: i % 2 === 0, glow: 0.45 + 0.4 * tri(i, n) }),
  spin: (i) => ({ sx: [1, 0.72, 0.28, 0.72, 1, 0.72, 0.28, 0.72][i % 8], lit: i % 4 === 0 }),
  eyes: (i, n) => ({ lit: i % 4 !== 3, glow: 0.2 + 0.25 * tri(i, n) }),
  smoke: (i, n) => ({ fx: [(img, c) => particles(img, c, i, n, col('#2A2A30'), 'up', 5)] }),
  flare: (i, n) => ({ lit: true, bright: 1 + 0.25 * tri(i, n), glow: 0.4 + 0.5 * tri(i, n) }),
  sparks: (i, n, sym) => ({ fx: [(img, c) => particles(img, c, i, n, col(sym.way), 'out', 6)] }),
  squash: (i) => ({ sy: [1, 0.86, 1.1, 1.04, 0.94, 1.02, 1, 1][i % 8], sx: [1, 1.12, 0.92, 0.97, 1.05, 0.99, 1, 1][i % 8] }),
  spores: (i, n, sym) => ({ fx: [(img, c) => particles(img, c, i, n, col(sym.id === 'warped_fungus' ? '#60F0E0' : '#FF7A50'), 'up', 7)] }),
  crack: (i, n) => ({ lit: tri(i, n) > 0.3, glow: 0.35 + 0.55 * tri(i, n), bright: 1 + 0.1 * tri(i, n) }),
  motes: (i, n) => ({ fx: [(img, c) => particles(img, c, i, n, col('#C070FF'), 'up', 6)] }),
  iris: (i, n) => ({ lit: i % 2 === 0, glow: 0.3 + 0.5 * tri(i, n), sx: 1 + 0.04 * tri(i, n), sy: 1 + 0.04 * tri(i, n) }),
  beam: (i, n) => ({ fx: [(img, c) => beam(img, c, 0.35 + 0.45 * tri(i, n))], lit: i % 2 === 0 }),
  jaw: (i) => ({ dx: [0, 1, -1, 1, 0, 0, 0, 0][i % 8], lit: i % 8 >= 1 && i % 8 <= 4 }),
  spread: (i, n) => ({ sx: 1 + 0.14 * tri(i, n), sy: 1 - 0.03 * tri(i, n) }),
  pop: (i) => ({ sx: [1, 1.16, 0.94, 1.05, 0.99, 1, 1, 1][i % 8], sy: [1, 1.16, 0.94, 1.05, 0.99, 1, 1, 1][i % 8] }),
  swirl: (i, n) => ({ rot: (360 * i) / n, glow: 0.3 }),
  // idle flourishes (6 frames, subtle)
  shine: (i, n) => ({ shine: i / (n - 1) }),
  flutter: (i) => ({ sx: [1, 1.05, 1, 1.05, 1, 1][i % 6] }),
  peek: (i) => ({ dy: -[0, 1, 1, 1, 0, 0][i % 6], lit: i === 2 }),
  sway: (i, n) => ({ shear: 1.2 * wave(i, n) }),
  jiggle: (i) => ({ dx: [0, 1, 0, -1, 0, 0][i % 6] }),
  bubble: (i) => ({ lit: i === 1 || i === 3, sy: [1, 1.02, 1, 1.02, 1, 1][i % 6] }),
  drip: (i) => ({ dy: [0, 0, 1, 1, 0, 0][i % 6], sy: [1, 1.02, 1.05, 1.02, 1, 1][i % 6] }),
  glint: (i, n) => ({ shine: i / (n - 1), lit: i === 3 }),
  wobble: (i) => ({ rot: [0, 4, 0, -4, 0, 0][i % 6] }),
  flicker: (i) => ({ lit: i % 2 === 1, bright: i % 2 ? 1.1 : 1 }),
  twinkle: (i) => ({ lit: i === 1 || i === 4 }),
  look: (i) => ({ dx: [0, -1, -1, 1, 1, 0][i % 6] }),
  rotate: (i) => ({ sx: [1, 0.8, 0.6, 0.8, 1, 1][i % 6] }),
  blink: (i) => ({ lit: i === 2 || i === 3 }),
  fold: (i) => ({ sx: [1, 0.95, 0.9, 0.95, 1, 1][i % 6] }),
};

function merge(list, i, n, sym) {
  const p = { sx: 1, sy: 1, rot: 0, dx: 0, dy: 0, shear: 0, lit: false, bright: 1, glow: 0, ring: -1, shine: -1, fx: [] };
  for (const name of list) {
    const r = R[name];
    if (!r) throw new Error(`frames: unknown recipe '${name}'`);
    const q = r(i, n, sym);
    p.sx *= q.sx ?? 1;
    p.sy *= q.sy ?? 1;
    p.rot += q.rot ?? 0;
    p.dx += q.dx ?? 0;
    p.dy += q.dy ?? 0;
    p.shear += q.shear ?? 0;
    p.lit ||= !!q.lit;
    p.bright *= q.bright ?? 1;
    p.glow = Math.max(p.glow, q.glow ?? 0);
    if (q.ring !== undefined) p.ring = q.ring;
    if (q.shine !== undefined) p.shine = q.shine;
    if (q.fx) p.fx.push(...q.fx);
  }
  return p;
}

// ---- overlay painters (ctx: {size, scale, cx, cy, seed}) ---------------------------------------------------------
function glints(img, c, sym, i, n) {
  const r = rng(hash(sym.id) ^ 0x51f7);
  const pts = Array.from({ length: 4 }, () => [c.size * (0.18 + 0.64 * r()), c.size * (0.14 + 0.62 * r()), r()]);
  pts.forEach(([x, y, ph], k) => {
    const t = ((i / n + ph + k * 0.25) % 1);
    const a = t < 0.5 ? t * 2 : 2 - t * 2;
    if (a < 0.2) return;
    star(img, Math.round(x), Math.round(y), c.scale >= 2 ? (a > 0.7 ? 3 : 2) : 1, col(sym.way), a);
  });
}

function coins(img, c, i, n) {
  const r = rng(0xc01e);
  for (let k = 0; k < 4; k++) {
    const x0 = c.size * (0.3 + 0.4 * r());
    const vx = (r() - 0.5) * c.size * 0.5;
    const t = ((i / n + k / 4) % 1);
    const x = x0 + vx * t;
    const y = c.size * 0.45 - c.size * 0.5 * t + c.size * 0.9 * t * t;
    const s = Math.max(1, c.scale);
    for (let yy = 0; yy < s + 1; yy++) for (let xx = 0; xx < s + 1; xx++) blend(img, Math.round(x) + xx, Math.round(y) + yy, GOLD, 1);
    put(img, Math.round(x), Math.round(y), [255, 250, 200, 255]);
  }
}

function particles(img, c, i, n, colour, mode, count) {
  const r = rng(0x9a17 + count);
  for (let k = 0; k < count; k++) {
    const t = ((i / n + k / count) % 1);
    const a = 1 - t;
    let x;
    let y;
    if (mode === 'up') {
      x = c.size * (0.2 + 0.6 * r()) + Math.sin((t + k) * 5) * c.scale;
      y = c.size * (0.8 - 0.75 * t);
    } else {
      const ang = r() * TAU;
      const d = c.size * (0.18 + 0.36 * t);
      x = c.size / 2 + Math.cos(ang) * d;
      y = c.size / 2 + Math.sin(ang) * d;
    }
    const s = c.scale >= 2 ? 2 : 1;
    for (let yy = 0; yy < s; yy++) for (let xx = 0; xx < s; xx++) blend(img, Math.round(x) + xx, Math.round(y) + yy, colour, a);
  }
}

function beam(img, c, a) {
  const cx = Math.round(c.size / 2);
  for (let y = 0; y < c.size; y++) {
    const fall = 1 - Math.abs(y / c.size - 0.45);
    for (let d = -2 * c.scale; d <= 2 * c.scale; d++) {
      const k = a * fall * (1 - Math.abs(d) / (2.5 * c.scale));
      if (k > 0.05) blend(img, cx + d - 1, y, WHITE, k * 0.6);
    }
  }
}

// ---- renderers --------------------------------------------------------------------------------------------------
/** Cell geometry per output size: art scale, margin, and whether the ink outline / shadow are drawn. */
export const CELL = {
  40: { scale: 2, outline: true, shadow: true },
  32: { scale: 2, outline: true, shadow: false },
  16: { scale: 1, outline: false, shadow: false },
};

/** One rendered frame of `sym` at `size` with merged params `p`. */
export function renderFrame(sym, size, p = merge([], 0, 1, sym)) {
  const cfg = CELL[size];
  const sc = cfg.scale;
  const src = art(sym, { lit: p.lit });
  // pivot: art centre; squash keeps the bottom on the baseline (dy compensation)
  const baseH = 16 * sc;
  const squashDy = ((1 - p.sy) * baseH) / 2;
  let img = affine(src, size, size, {
    scale: sc, sx: p.sx, sy: p.sy, rot: p.rot, px: 8, py: 8, ox: size / 2, oy: size / 2,
    dx: p.dx * sc, dy: p.dy * sc + squashDy, shear: p.shear * sc,
  });
  if (p.bright !== 1) img = brighten(img, p.bright);
  if (p.shine >= 0) img = shine(img, p.shine, 4 * sc, 0.6);
  if (cfg.outline) img = outline(img, sym.rim ? col(sym.rim) : INK, { k: 0.9 });
  const body = img;
  // behind the symbol: glow halo and the expanding ring
  let back = image(size, size);
  if (p.glow > 0 && sym.way) back = halo(body, col(sym.way), sc >= 2 ? 3 : 1, p.glow);
  if (p.ring >= 0 && sym.way) ring(back, size / 2, size / 2, size * (0.3 + 0.2 * p.ring), sc, col(sym.way), 0.9 * (1 - p.ring));
  img = over(back, cfg.shadow ? shadow(body, INK, 2, 2, 0.4) : body);
  const ctx = { size, scale: sc };
  for (const f of p.fx) f(img, ctx);
  return img;
}

export const baseFrame = (sym, size) => renderFrame(sym, size);

/** Motion-blur frame: 3 copies at −6/0/+6 px (×size/40) with alpha 35/100/35 %, squashed to the cell. */
export function blurFrame(sym, size) {
  const b = renderFrame(sym, size, { ...merge([], 0, 1, sym), sy: 0.86 });
  const off = Math.max(1, Math.round((6 * size) / 40));
  const out = image(size, size);
  over(out, fade(b, 0.35), 0, -off);
  over(out, fade(b, 0.35), 0, off);
  over(out, b, 0, 0);
  // soften: blur frames are slightly desaturated toward the ink so the landed cell "pops" on arrival
  return out;
}

export const winFrames = (sym, size) => Array.from({ length: 8 }, (_, i) => renderFrame(sym, size, merge(sym.win, i, 8, sym)));
export const idleFrames = (sym, size) => Array.from({ length: 6 }, (_, i) => renderFrame(sym, size, merge([sym.idle], i, 6, sym)));

/** Land keys: squash (scale-Y 0.9, X 1.06) and a 1-frame bright rim. */
export function landFrames(sym, size) {
  const squash = renderFrame(sym, size, { ...merge([], 0, 1, sym), sx: 1.06, sy: 0.9 });
  const rim = renderFrame(sym, size, { ...merge([], 0, 1, sym), bright: 1.35, glow: 0.8 });
  return [squash, rim];
}

export { clamp };

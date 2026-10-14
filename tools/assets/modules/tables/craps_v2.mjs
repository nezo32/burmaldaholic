// Craps table UI v2 art (docs/architecture/ui-v2.md §9, lane D; REDESIGN.md §7.6): the dice of the v2 felt at the three
// tiers (14 / 18 / 22 px cells: T1 / T2 / T3, faces 1–6 then 8 blank tumble frames, never a scaled sprite), the puck at
// 12 / 16 / 20 / 26 px (7 flip frames: OFF, 2 tweens, edge, 2 tweens, ON), and for the CM motion package the rubber
// pyramid back wall strips (4 / 6 / 8 / 10 px), the stickman's stick (shaft tile + hook), the in-world print with its
// wall strip and the in-world wall / puck textures. Words (ON / OFF) stay out of the art (drawn by the screen,
// translated). Deterministic, pure.
import { BONE, GOLD, INK } from './theme.mjs';
import { P, R, image, inked, put as putPx, rrect, textC } from './draw.mjs';
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
export const PUCK_SIZES = [12, 16, 20, 26];
/** Back wall strip heights per tier (CM: T1 4, T2 6, T3 8, ROOMY 10). */
export const WALL_HEIGHTS = [4, 6, 8, 10];
/** Stickman shaft widths (the hook is 5n x 3n). */
export const STICK_WIDTHS = [3, 4, 5, 6];
/** Puck frames: OFF, 2 tweens, edge, 2 tweens, ON. */
export const PUCK_FRAMES = 7;

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

/**
 * Puck frames of `size` px stacked vertically (7 frames, CM): 0 OFF (black face, bone ring), 1–2 the OFF face turning
 * away (squashed, the rim showing on the right), 3 edge-on, 4–5 the ON face turning in, 6 ON (white face, dark ring).
 * No words (the screen draws the translated ON / OFF in the plain face centre). The puck is a thick disc: the lower rim
 * is shaded as its side.
 */
export function puckSheet(size) {
  const n = size - 2;
  const r = n / 2;
  const frame = (on, sx) => {
    const img = image(n, n);
    const face = on ? ['#F4ECF8', '#C9BCDD', '#FFFFFF', '#9A8CB4'] : ['#2A2230', '#120E16', '#4E4458', '#08060A'];
    const ringC = on ? '#26202C' : '#E8DCF0';
    // squashed discs leave the rim (the puck's edge) visible on the far side
    const rimW = sx < 1 ? Math.max(1, Math.round((1 - sx) * r * 0.5)) : 0;
    for (let y = 0; y < n; y++)
      for (let x = 0; x < n; x++) {
        const dx = (x + 0.5 - r) / ((r - 0.35) * sx);
        const dy = (y + 0.5 - r) / (r - 0.35);
        const d = Math.hypot(dx, dy);
        if (d > 1) {
          const ex = (x + 0.5 - r - rimW) / ((r - 0.35) * sx);
          if (rimW && Math.hypot(ex, dy) <= 1 && x + 0.5 > r) P(img, x, y, '#8C7CA8');
          continue;
        }
        let col = face[0];
        if (d > 0.84) col = dy > 0.35 ? face[3] : face[1];
        else if (sx > 0.5 && Math.abs(d * (r - 0.35) - 0.69 * (r - 0.35)) <= 0.62) col = ringC;
        if (d > 0.84 && dx + dy < -0.7) col = face[2];
        P(img, x, y, col);
      }
    return inked(img, INK, 1);
  };
  const edge = () => {
    const img = image(n, n);
    const w = Math.max(3, Math.round(size / 5));
    const x0 = Math.round((n - w) / 2);
    rrect(img, x0, 0, w, n, '#8C7CA8', 1);
    R(img, x0, 1, 1, n - 2, '#C9BCDD');
    R(img, x0 + w - 1, 1, 1, n - 2, '#5A4C6C');
    return inked(img, INK, 1);
  };
  const frames = [frame(false, 1), frame(false, 0.62), frame(false, 0.3), edge(), frame(true, 0.3), frame(true, 0.62), frame(true, 1)];
  const img = image(size, size * frames.length);
  frames.forEach((f, i) => put(img, f, 0, i * size));
  return img;
}

// ---- back wall (CM, CS §4.1 / §8) ------------------------------------------------------------------------------------
/** Rubber-pyramid palette, lit from the top-left: L lightest edge, T lit facet, M apex, D shade, K valley. */
export const RUB = { lip: '#100A16', rim: '#4A3E5A', valley: '#1A1420', base: '#0C0810', L: '#A494B8', T: '#7E6E94', M: '#5C4E72', D: '#3A3050', K: '#1C1626' };
const HIT = { L: '#FFFFFF', T: '#FFFFFF', M: '#F4ECF8', D: '#D8CCE6', K: '#B8A8CC' };
const HOT = { L: GOLD[0], T: GOLD[1], M: GOLD[1], D: GOLD[2], K: GOLD[3] };
/** One pyramid (4 wide) per height: lit top-left facets, the apex, the shaded bottom-right facets, a dark valley. */
const PYR = {
  3: ['LTTD', 'LMDK', 'DDKK'],
  4: ['LTTD', 'LTMD', 'TMDK', 'DDKK'],
};

/** Facet colour of pixel (x, y) of one rubber pyramid `4 × dh` (dh 3 or 4). */
function facet(x, y, dh, pal = RUB) {
  return pal[PYR[dh][y][x]];
}

/** Pyramid rows of a back wall strip of height H: [{y0, dh}] (the column of diamond i is x 4i..4i+3 in every row). */
function pyramidRows(H) {
  if (H <= 4) return [{ y0: 1, dh: H - 1 }];
  if (H <= 6) return [{ y0: 2, dh: H - 3 }];
  if (H <= 8) return [{ y0: 1, dh: 3 }, { y0: 4, dh: 3 }];
  return [{ y0: 1, dh: 4 }, { y0: 5, dh: 4 }];
}


/**
 * Back wall strip `16 × 3H` (H = 4 / 6 / 8 / 10): row 0 = the IDLE tile (seamless horizontally; 1-px dark lip on top,
 * a lit rim, four rubber pyramids per tile at pitch 4 — diamond i spans x 4i..4i+3 — and a dark base line where the wall
 * meets the felt); row 1 = the HIT overlay of ONE diamond (x 0..3, white facets); row 2 = the HOT overlay (gold).
 */
export function backWallStrip(H) {
  const img = image(16, 3 * H);
  R(img, 0, 0, 16, H, RUB.valley);
  R(img, 0, 0, 16, 1, RUB.lip);
  if (H === 6) R(img, 0, 1, 16, 1, RUB.rim);
  if (H > 4) R(img, 0, H - 1, 16, 1, RUB.base);
  const rows = pyramidRows(H);
  for (const [row, pal] of [[0, RUB], [1, HIT], [2, HOT]])
    for (const { y0, dh } of rows)
      for (let i = 0; i < (row ? 1 : 4); i++)
        for (let y = 0; y < dh; y++) for (let x = 0; x < 4; x++) P(img, 4 * i + x, row * H + y0 + y, facet(x, y, dh, pal));
  return img;
}

/** The IDLE pyramid strip drawn into `img` over `(x, y, w, H)` (tiles every 16 px from x). */
export function drawWallStrip(img, x, y, w, H) {
  const tile = backWallStrip(H);
  for (let j = 0; j < H; j++)
    for (let i = 0; i < w; i++) {
      const o = (j * 16 + (i % 16)) * 4;
      putPx(img, x + i, y + j, [tile.data[o], tile.data[o + 1], tile.data[o + 2], tile.data[o + 3]]);
    }
}

// ---- stickman stick (CM, CS §4.7) ------------------------------------------------------------------------------------
export const WOOD = ['#E0A858', '#C8903C', '#9C6430', '#6E4422'];

/** Shaft column i of a shaft n wide: ink, a lit wood ramp, ink. */
function shaftColumn(n, i) {
  if (i === 0 || i === n - 1) return INK;
  const m = n - 2;
  const k = i - 1;
  if (m === 1) return WOOD[1];
  if (m === 2) return [WOOD[0], WOOD[2]][k];
  if (m === 3) return [WOOD[0], WOOD[1], WOOD[3]][k];
  return WOOD[Math.min(3, Math.floor((k * 4) / m))];
}

/** Stick shaft tile `n × 8` (vertical, seamless): ink sides, lit from the left, a grain fleck every 4 rows. */
export function stickShaft(n) {
  const img = image(n, 8);
  for (let y = 0; y < 8; y++) for (let x = 0; x < n; x++) P(img, x, y, shaftColumn(n, x));
  if (n >= 4) {
    P(img, 1 + ((n - 2) >> 1), 2, WOOD[2]);
    P(img, 1 + ((n - 2) >> 1), 6, WOOD[2]);
  }
  return img;
}

/**
 * Stick hook `5n × 3n`: the shaft enters at the top (columns x in [2n, 3n), shaded like stickShaft) and ends in a
 * curved blade that spreads both ways; the dice are hooked at the bottom centre (2.5n, 3n).
 */
export function stickHook(n) {
  const W = 5 * n;
  const H = 3 * n;
  const img = image(W, H);
  const mask = new Uint8Array(W * H); // 1 shaft, 2 blade
  const t = Math.max(2, Math.round(0.75 * n));
  const cx = 2.5 * n;
  const yc = (x) => H - 0.6 - t / 2 - 1.15 * n * ((x + 0.5 - cx) / (cx - 0.5)) ** 2;
  for (let y = 0; y < H; y++) for (let x = 1; x < W - 1; x++) if (Math.abs(y + 0.5 - yc(x)) <= t / 2) mask[y * W + x] = 2;
  const bladeTop = Math.floor(yc(cx) - t / 2);
  for (let y = 0; y < bladeTop; y++) for (let x = 2 * n; x < 3 * n; x++) mask[y * W + x] = 1;
  for (let y = 0; y < H; y++)
    for (let x = 0; x < W; x++) {
      const m = mask[y * W + x];
      if (m === 1) P(img, x, y, shaftColumn(n, x - 2 * n));
      else if (m === 2) {
        const dv = y + 0.5 - yc(x);
        const col = dv < -t / 4 ? WOOD[0] : dv < t / 4 ? WOOD[1] : WOOD[3];
        P(img, x, y, Math.abs(x + 0.5 - cx) < n * 0.5 && dv < -t / 4 ? GOLD[1] : col);
      }
    }
  const ink = [];
  for (let y = 0; y < H; y++)
    for (let x = 0; x < W; x++) {
      if (mask[y * W + x]) continue;
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nx = x + dx;
        const ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
        if (mask[ny * W + nx] === 2) {
          ink.push([x, y]);
          break;
        }
      }
    }
  for (const [x, y] of ink) P(img, x, y, INK);
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

// ---- in-world layout print (ui-v2.md §7.6, lane D; CM) ---------------------------------------------------------------
/**
 * Geometry of the in-world craps print, in texture px (the Java mirror is `games/craps/present/CrapsBoardGeometry`, same
 * fractions as the v2 screen's `CrapsLayout` through `CrapsFeltFractions`): the 6-px back wall strip on the far edge,
 * then Don't Come, six point boxes, Come, Field, Don't Pass bar, Pass line.
 */
export const PRINT_W = 256;
export const PRINT_H = 176;
/** The back wall strip of the print (rubber pyramids, the dice's wall plane is its bottom edge). */
export const WALL = { x: 4, y: 4, w: 248, h: 6 };
/** Top of the rows (below the wall strip) and the margin. */
export const ROWS_Y = 12;
export function printGeometry() {
  const m = 4;
  const gap = 3;
  const w = PRINT_W - 2 * m;
  const y0 = ROWS_Y;
  const h = PRINT_H - y0 - m;
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
    return { p, x: a, y: y0, w: b - a - (i < 5 ? gap : 0), h: placeH };
  });
  const comeY = y0 + placeH + gap;
  const fieldY = comeY + comeH + gap;
  const dpY = fieldY + fieldH + gap;
  const passY = dpY + dpH + gap;
  return {
    wall: { ...WALL },
    dontCome: { x: m, y: y0, w: dcW, h: placeH + gap + comeH },
    points,
    come: { x: px0, y: comeY, w: pw, h: comeH },
    field: { x: m, y: fieldY, w, h: fieldH },
    dontPass: { x: m, y: dpY, w, h: dpH },
    pass: { x: m, y: passY, w, h: passH },
  };
}

/** A tiny printed die face `7 × 7` showing six (rounded bone square, 2 × 3 dark pips). */
function tinySix(img, x, y, bone, pip) {
  R(img, x + 1, y, 5, 7, bone);
  R(img, x, y + 1, 7, 5, bone);
  for (const py of [1, 3, 5]) {
    P(img, x + 2, y + py, pip);
    P(img, x + 4, y + py, pip);
  }
}

/**
 * The print: the rubber-pyramid back wall strip, high-contrast bone lines with a 1-px shadow, the point numbers ×2,
 * the field numbers (2 and 12 gold), the DON'T PASS BAR band with a printed 6 + 6 "bar 12" dice icon, the Pass line's
 * gold double edge and a 1-px gold inner border. No words (they are translated in the screen only).
 */
export function boardPrint() {
  const img = image(PRINT_W, PRINT_H);
  const g = printGeometry();
  const SHADOW = '#04140C';
  const line = (b, hex, k = 1) => {
    // shadow down-right first so the line reads on any felt
    R(img, b.x + 1, b.y + 1, b.w, 1, SHADOW, 0.45);
    R(img, b.x + 1, b.y + b.h, b.w, 1, SHADOW, 0.45);
    R(img, b.x + 1, b.y + 1, 1, b.h, SHADOW, 0.45);
    R(img, b.x + b.w, b.y + 1, 1, b.h, SHADOW, 0.45);
    R(img, b.x, b.y, b.w, 1, hex, k);
    R(img, b.x, b.y + b.h - 1, b.w, 1, hex, k);
    R(img, b.x, b.y, 1, b.h, hex, k);
    R(img, b.x + b.w - 1, b.y, 1, b.h, hex, k);
  };
  const BONE_P = '#F6EAC0';
  const GOLD_P = '#FFD640';
  // gold inner border (1 px), 1 px inside the print edge, clear of the rows and the wall strip
  const gb = { x: 1, y: 1, w: PRINT_W - 2, h: PRINT_H - 2 };
  R(img, gb.x, gb.y, gb.w, 1, GOLD_P, 0.75);
  R(img, gb.x, gb.y + gb.h - 1, gb.w, 1, GOLD_P, 0.75);
  R(img, gb.x, gb.y, 1, gb.h, GOLD_P, 0.75);
  R(img, gb.x + gb.w - 1, gb.y, 1, gb.h, GOLD_P, 0.75);
  // back wall strip (the dice bank off its bottom edge)
  drawWallStrip(img, g.wall.x, g.wall.y, g.wall.w, g.wall.h);
  R(img, g.wall.x, g.wall.y + g.wall.h, g.wall.w, 1, SHADOW, 0.55);
  line(g.dontCome, BONE_P);
  for (const b of g.points) {
    line(b, BONE_P);
    textC(img, String(b.p), b.x + b.w / 2 + 1, b.y + 11 + 1, SHADOW, { s: 2, k: 0.5 });
    textC(img, String(b.p), b.x + b.w / 2, b.y + 11, BONE_P, { s: 2 });
  }
  line(g.come, BONE_P);
  line(g.field, BONE_P);
  const nums = [2, 3, 4, 9, 10, 11, 12];
  const fx0 = g.field.x + Math.round(g.field.w * 0.2);
  const fw = g.field.x + g.field.w - 6 - fx0;
  nums.forEach((n, i) => {
    const cx = fx0 + (fw * (2 * i + 1)) / (2 * nums.length);
    const gold = n === 2 || n === 12;
    textC(img, String(n), cx + 1, g.field.y + 8 + 1, SHADOW, { k: 0.5 });
    textC(img, String(n), cx, g.field.y + 8, gold ? GOLD_P : BONE_P);
  });
  // DON'T PASS BAR: a darker band with a thin inner rule, and the bar-12 dice (6 + 6) at its centre
  const dp = g.dontPass;
  R(img, dp.x + 1, dp.y + 1, dp.w - 2, dp.h - 2, '#000000', 0.18);
  line(dp, BONE_P);
  const iy = dp.y + Math.round((dp.h - 7) / 2);
  const ix = dp.x + Math.round(dp.w / 2) - 8;
  tinySix(img, ix + 1, iy + 1, SHADOW, SHADOW);
  tinySix(img, ix + 10, iy + 1, SHADOW, SHADOW);
  tinySix(img, ix, iy, BONE_P, '#26202C');
  tinySix(img, ix + 9, iy, BONE_P, '#26202C');
  // Pass line: gold double edge
  line(g.pass, GOLD_P, 1);
  R(img, g.pass.x + 1, g.pass.y + 1, g.pass.w - 2, 1, GOLD_P, 0.45);
  R(img, g.pass.x + 1, g.pass.y + g.pass.h - 2, g.pass.w - 2, 1, GOLD_P, 0.45);
  R(img, g.pass.x + 1, g.pass.y + 1, 1, g.pass.h - 2, GOLD_P, 0.45);
  R(img, g.pass.x + g.pass.w - 2, g.pass.y + 1, 1, g.pass.h - 2, GOLD_P, 0.45);
  return img;
}

// ---- in-world textures (CM, CS §6) -----------------------------------------------------------------------------------
/**
 * `entity/craps/wall.png` 64 × 16: rows 0–7 the inner face of the raised back wall (two rows of rubber pyramids, offset,
 * tile every 16 px); rows 8–11 the padded top cap (dark leather with stitching, tileable); rows 12–15: x 0–15 the white
 * flash cell (one diamond, soft edge), x 16–31 the gold hot cell, x 32–47 the chip-groove tile for the rail top (dark
 * wood groove, tileable), x 48–63 the stick dowel colour (lit top row → dark bottom).
 */
export function wallTexture() {
  const img = image(64, 16);
  // pyramids: two rows of 4-high square pyramids, pitch 4
  for (let row = 0; row < 2; row++)
    for (let y = 0; y < 4; y++) for (let x = 0; x < 64; x++) P(img, x, row * 4 + y, facet(x & 3, y, 4));
  // top cap: padded leather
  const cap = ['#5C3E36', '#432C28', '#33201E', '#241618'];
  for (let y = 0; y < 4; y++) R(img, 0, 8 + y, 64, 1, cap[y]);
  for (let x = 1; x < 64; x += 4) P(img, x, 9, '#7A5A4C');
  // flash / hot cells: one diamond spanning the 16 × 4 cell
  for (const [x0, pal] of [[0, HIT], [16, HOT]])
    for (let y = 0; y < 4; y++)
      for (let x = 0; x < 16; x++) {
        const u = (x + 0.5 - 8) / 8;
        const v = (y + 0.5 - 2) / 2;
        const d = Math.abs(u) + Math.abs(v);
        if (d > 1.05) continue;
        const col = v < 0 ? (u < 0 ? pal.L : pal.T) : u < 0 ? pal.M : pal.D;
        P(img, x0 + x, 12 + y, col, d > 0.8 ? 0.6 : 1);
      }
  // chip groove
  const groove = ['#7A4A2A', '#2A160E', '#3A2014', '#6A3E22'];
  for (let y = 0; y < 4; y++) R(img, 32, 12 + y, 16, 1, groove[y]);
  for (const x of [35, 43]) P(img, x, 15, '#5A3420');
  // stick dowel
  const dowel = ['#E0A858', '#C8903C', '#9C6430', '#6E4422'];
  for (let y = 0; y < 4; y++) R(img, 48, 12 + y, 16, 1, dowel[y]);
  return img;
}

/** `entity/craps/puck.png` 32 × 16: x 0–15 the ON face (white disc, dark ring), x 16–31 the OFF face (black, light ring). */
export function puckTexture() {
  const img = image(32, 16);
  for (const [x0, on] of [[0, true], [16, false]])
    for (let y = 0; y < 16; y++)
      for (let x = 0; x < 16; x++) {
        const dx = x + 0.5 - 8;
        const dy = y + 0.5 - 8;
        const d = Math.hypot(dx, dy) / 7.6;
        if (d > 1) continue;
        let col = on ? '#F4ECF8' : '#2A2230';
        if (d > 0.9) col = INK;
        else if (d > 0.8) col = on ? (dx + dy < -2 ? '#FFFFFF' : '#C9BCDD') : dx + dy < -2 ? '#4E4458' : '#120E16';
        else if (d > 0.56 && d <= 0.7) col = on ? '#26202C' : '#E8DCF0';
        P(img, x0 + x, y, col);
      }
  return img;
}

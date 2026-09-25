// Roulette felt art (docs/design/visual/tables.md §3.2): the betting layout (big + compact, per theme), the
// racetrack, cell highlights, the dolly, history pills and sparks. Geometry constants are exported so the screen
// (and the mockup script) use the same cell rectangles as the art. Digits only; words are drawn at runtime.
import { BONE, GOLD, INK, POCKET, THEME, WHEEL_ORDER, pocketColour } from './theme.mjs';
import { P, R, blend, c, disc, grid, image, inked, polygon, ring, rrect, shine, textC, vstrip } from './draw.mjs';

/** Cell geometry. Numbers: col 0–11 (1–3, 4–6 …), row 0 = top (3, 6, …, 36). */
export const LAYOUT_BIG = { cellW: 20, cellH: 22, zeroW: 18, colW: 20, dozenH: 16, outsideH: 16, font: 'bold', inset: 2 };
export const LAYOUT_COMPACT = { cellW: 14, cellH: 15, zeroW: 12, colW: 14, dozenH: 11, outsideH: 11, font: 'small', inset: 1 };
export const layoutSize = (g) => ({ w: g.zeroW + 12 * g.cellW + g.colW, h: 3 * g.cellH + g.dozenH + g.outsideH });

/** Rectangle of number n (1–36) inside the layout image. */
export function numberRect(g, n) {
  const col = Math.floor((n - 1) / 3);
  const row = 2 - ((n - 1) % 3);
  return { x: g.zeroW + col * g.cellW, y: row * g.cellH, w: g.cellW, h: g.cellH };
}

export function layout(theme, g) {
  const t = THEME[theme];
  const { w, h } = layoutSize(g);
  const img = image(w, h);
  const L = t.line;
  const gridH = 3 * g.cellH;
  const x0 = g.zeroW;
  const x12 = g.zeroW + 12 * g.cellW;
  // soft felt darkening under the printed area
  R(img, 0, 0, w, gridH + 1, '#000000', 0.12);
  R(img, x0, gridH + 1, x12 - x0 + 1, h - gridH - 1, '#000000', 0.12);
  // number plates
  for (let n = 1; n <= 36; n++) {
    const r = numberRect(g, n);
    const pc = POCKET[pocketColour(n)];
    const i = g.inset;
    rrect(img, r.x + i, r.y + i, r.w - 2 * i, r.h - 2 * i, pc[3], 1);
    rrect(img, r.x + i, r.y + i, r.w - 2 * i, r.h - 2 * i - 1, pc[1], 1);
    R(img, r.x + i + 1, r.y + i, r.w - 2 * i - 2, 1, pc[0]);
    textC(img, String(n), r.x + r.w / 2, r.y + r.h / 2 - 0.5, BONE, { font: g.font, shadow: pc[3] });
  }
  // zero: a green pentagon pointing left, outlined like the other cells
  const zi = g.inset;
  rrect(img, 0, 0, x0 + 1, gridH + 1, L, 2);
  rrect(img, 1, 1, x0 - 1, gridH - 1, t.feltDark, 2);
  rrect(img, zi, zi, x0 + 1 - 2 * zi, gridH + 1 - 2 * zi, POCKET.green[3], 2);
  rrect(img, zi, zi, x0 + 1 - 2 * zi, gridH - 2 * zi, POCKET.green[1], 2);
  R(img, zi + 2, zi, x0 - 2 * zi - 3, 1, POCKET.green[0]);
  textC(img, '0', x0 / 2 + 1, gridH / 2 - 0.5, BONE, { font: g.font, shadow: POCKET.green[3] });
  // grid lines
  const hl = (y, xa, xb) => R(img, xa, y, xb - xa + 1, 1, L);
  const vl = (x, ya, yb) => R(img, x, ya, 1, yb - ya + 1, L);
  for (let r = 0; r <= 3; r++) hl(r * g.cellH, x0, w - 1);
  for (let k = 0; k <= 12; k++) vl(x0 + k * g.cellW, 0, gridH);
  vl(w - 1, 0, gridH);
  hl(gridH + g.dozenH, x0, x12);
  hl(h - 1, x0, x12);
  for (let k = 0; k <= 3; k++) vl(x0 + k * 4 * g.cellW, gridH, gridH + g.dozenH);
  for (let k = 0; k <= 6; k++) vl(x0 + k * 2 * g.cellW, gridH + g.dozenH, h - 1);
  // column bets: 2:1
  for (let r = 0; r < 3; r++) textC(img, '2:1', x12 + g.colW / 2 + 0.5, r * g.cellH + g.cellH / 2 - 0.5, L, { font: 'small', shadow: t.feltDeep });
  // dozens
  ['1-12', '13-24', '25-36'].forEach((s, k) => textC(img, s, x0 + (k * 4 + 2) * g.cellW + 0.5, gridH + g.dozenH / 2, L, { font: g.font === 'bold' ? 'bold' : 'small', shadow: t.feltDeep }));
  // outside: 1-18, EVEN (runtime), RED, BLACK, ODD (runtime), 19-36
  const oy = gridH + g.dozenH + g.outsideH / 2;
  const ow = 2 * g.cellW;
  textC(img, '1-18', x0 + ow / 2 + 0.5, oy, L, { font: g.font === 'bold' ? 'bold' : 'small', shadow: t.feltDeep });
  textC(img, '19-36', x0 + 5.5 * ow + 0.5, oy, L, { font: g.font === 'bold' ? 'bold' : 'small', shadow: t.feltDeep });
  for (const [k, col] of [[2, 'red'], [3, 'black']]) {
    const cx = x0 + (k + 0.5) * ow;
    const hw = g.cellW * 0.8;
    const hh = g.outsideH / 2 - 2.5;
    polygon(img, [[cx - hw - 1, oy], [cx, oy - hh - 1], [cx + hw + 1, oy], [cx, oy + hh + 1]], L);
    polygon(img, [[cx - hw, oy], [cx, oy - hh], [cx + hw, oy], [cx, oy + hh]], POCKET[col][1]);
    polygon(img, [[cx - hw + 3, oy - 0.5], [cx, oy - hh + 1], [cx + hw - 3, oy - 0.5], [cx, oy]], POCKET[col][0], 0.5);
  }
  // gold trim around the whole printed area (the double line of a real layout)
  return img;
}

/** Racetrack (big layout only): a stadium of 37 cells in wheel order, 0 on the right cap; inner sections blank. */
export const RACETRACK = { w: 278, h: 44, t: 13 };
export function racetrackCells() {
  const { w, h, t } = RACETRACK;
  const rCap = h / 2;
  const straight = w - 2 * rCap;
  const cells = [];
  const idx = (n) => WHEEL_ORDER.indexOf(n);
  // clockwise from the top-left end: top row 17, right cap 2 (upper = 0), bottom row 16 (right → left), left cap 2
  const top = WHEEL_ORDER.slice(idx(24), idx(26) + 1);
  const right = [0, 32];
  const bottom = WHEEL_ORDER.slice(idx(15), idx(23) + 1);
  const left = [10, 5];
  top.forEach((n, i) => cells.push({ n, x: rCap + (straight * i) / 17, y: 0, w: straight / 17, h: t, part: 'top' }));
  right.forEach((n, i) => cells.push({ n, x: w - rCap, y: i * (h / 2), w: rCap, h: h / 2, part: 'right' }));
  bottom.forEach((n, i) => cells.push({ n, x: w - rCap - (straight * (i + 1)) / 16, y: h - t, w: straight / 16, h: t, part: 'bottom' }));
  left.forEach((n, i) => cells.push({ n, x: 0, y: (1 - i) * (h / 2), w: rCap, h: h / 2, part: 'left' }));
  return cells;
}

export function racetrack(theme) {
  const t = THEME[theme];
  const { w, h, t: th } = RACETRACK;
  const img = image(w, h);
  const rCap = h / 2;
  const inStadium = (x, y, inset) => {
    const px = x + 0.5;
    const py = y + 0.5;
    const cx = Math.min(Math.max(px, rCap), w - rCap);
    return Math.hypot(px - cx, py - rCap) <= rCap - inset;
  };
  const cells = racetrackCells();
  const cellAt = (x, y) => {
    const px = x + 0.5;
    const py = y + 0.5;
    for (const cl of cells) {
      if (cl.part === 'top' || cl.part === 'bottom') {
        if (px >= cl.x && px < cl.x + cl.w && py >= cl.y && py < cl.y + cl.h) return cl;
      } else if ((cl.part === 'right' ? px >= w - rCap : px < rCap) && py >= cl.y && py < cl.y + cl.h) return cl;
    }
    return null;
  };
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      if (!inStadium(x, y, 0)) continue;
      if (!inStadium(x, y, 1)) {
        P(img, x, y, t.line);
        continue;
      }
      if (inStadium(x, y, th)) {
        if (!inStadium(x, y, th + 1)) P(img, x, y, t.line);
        else blend(img, x, y, c('#000000'), 0.12);
        continue;
      }
      const cl = cellAt(x, y);
      if (!cl) continue;
      const pc = POCKET[pocketColour(cl.n)];
      // 1 px felt gap between cells
      const edge = cl.part === 'top' || cl.part === 'bottom' ? x + 0.5 - cl.x < 1 : y + 0.5 - cl.y < 1 && cl.y > 0;
      P(img, x, y, edge ? t.line : pc[y === cl.y + 1 ? 0 : 1]);
    }
  for (const cl of cells) {
    let cx = cl.x + cl.w / 2;
    const cy = cl.y + cl.h / 2;
    if (cl.part === 'right') cx = w - rCap + 6.5;
    if (cl.part === 'left') cx = rCap - 6;
    textC(img, String(cl.n), cx + 0.5, cy - 0.5, BONE, { font: 'small', shadow: POCKET[pocketColour(cl.n)][3] });
  }
  // section dividers inside (Tier | Orphelins | Voisins | Zero): labels are runtime text
  for (const x of [84, 150, 222]) R(img, x, th + 1, 1, h - 2 * th - 2, t.line);
  return img;
}

// ---- highlights ------------------------------------------------------------------------------------------------------
/** Hover: nine-slice 12 × 12 (border 3): gold rim, white 25 % wash. */
export function cellHover() {
  const img = image(12, 12);
  R(img, 0, 0, 12, 12, '#FFFFFF', 0.25);
  rrect(img, 0, 0, 12, 1, GOLD[1], 0);
  R(img, 0, 11, 12, 1, GOLD[1]);
  R(img, 0, 0, 1, 12, GOLD[1]);
  R(img, 11, 0, 1, 12, GOLD[1]);
  for (const [x, y] of [[0, 0], [11, 0], [0, 11], [11, 11]]) P(img, x, y, '#FFFFFF');
  return img;
}
/** Neighbour (racetrack / covered cells of a call bet): lilac rim + wash. */
export function cellNeighbour() {
  const img = image(12, 12);
  R(img, 0, 0, 12, 12, '#D696FF', 0.2);
  for (const [x, y, ww, hh] of [[0, 0, 12, 1], [0, 11, 12, 1], [0, 0, 1, 12], [11, 0, 1, 12]]) R(img, x, y, ww, hh, '#D696FF');
  return img;
}
/** Win outline: nine-slice 12 × 12 (border 4), 4 pulse frames (animated). */
export function cellWin() {
  return vstrip([0, 1, 2, 3].map((f) => {
    const img = image(12, 12);
    const k = [0.5, 0.8, 1, 0.8][f];
    R(img, 0, 0, 12, 12, '#FFD640', 0.14 * k);
    for (const [x, y, ww, hh] of [[0, 0, 12, 2], [0, 10, 12, 2], [0, 0, 2, 12], [10, 0, 2, 12]]) R(img, x, y, ww, hh, GOLD[1]);
    for (const [x, y, ww, hh] of [[0, 0, 12, 1], [0, 0, 1, 12]]) R(img, x, y, ww, hh, GOLD[0]);
    for (const [x, y, ww, hh] of [[2, 2, 8, 1], [2, 9, 8, 1], [2, 2, 1, 8], [9, 2, 1, 8]]) R(img, x, y, ww, hh, '#FFF1A0', 0.6 * k);
    return img;
  }));
}

// ---- dolly (win marker) --------------------------------------------------------------------------------------------
const DOLLY = [
  '....yY....',
  '...yWYY...',
  '...YYYo...',
  '....Yo....',
  '...yYYo...',
  '....Yo....',
  '....Yo....',
  '...yYYo...',
  '..yYYYYo..',
  '.PpPpPpPp.',
  'PPPPPPPPPP',
  '.pppppppp.',
];
/** Dolly 12 × 14 (inked): a gold pawn on a purple felt foot; 4 frames with a shine band (plays while it stands). */
export function dolly() {
  const base = inked(grid(DOLLY, { y: '#FFF1A0', Y: GOLD[1], W: '#FFFFFF', o: GOLD[3], P: '#BE5AFF', p: '#783CBE' }), INK, 1);
  return vstrip([0, 1, 2, 3].map((f) => (f === 0 ? base : shine(base, f / 3, 4, 0.7))));
}
/** Dolly shadow 12 × 4 (the code scales its alpha 30 → 100 % during the drop). */
export function dollyShadow() {
  const img = image(12, 4);
  for (let y = 0; y < 4; y++) for (let x = 0; x < 12; x++) if (Math.hypot((x + 0.5 - 6) / 6, (y + 0.5 - 2) / 2) <= 1) blend(img, x, y, c(INK), 0.55);
  return img;
}

// ---- history pills (22 × 11): shape marker + space for the runtime number ------------------------------------------
export function pill(colour, newest = false) {
  const img = image(22, 11);
  const pc = POCKET[colour];
  rrect(img, 0, 0, 22, 11, newest ? GOLD[1] : INK, 2);
  rrect(img, 1, 1, 20, 9, pc[1], 1);
  R(img, 2, 1, 18, 1, pc[0]);
  R(img, 2, 9, 18, 1, pc[3]);
  // shape marker (red = disc, black = ring, zero = diamond): accessibility, never colour alone
  if (colour === 'red') disc(img, 5.5, 5.5, 2.6, c(BONE));
  else if (colour === 'black') ring(img, 5.5, 5.5, 2.8, 1.1, c(BONE));
  else polygon(img, [[5.5, 2], [9, 5.5], [5.5, 9], [2, 5.5]], BONE);
  return img;
}

/** Spark: 7 × 7, 4 frames (deflector kick, dice wall hit). */
export function spark() {
  return vstrip([0, 1, 2, 3].map((f) => {
    const img = image(7, 7);
    const r = [1, 3, 3, 2][f];
    const k = [1, 1, 0.6, 0.3][f];
    blend(img, 3, 3, c('#FFFFFF'), k);
    for (let i = 1; i <= r; i++) for (const [dx, dy] of [[i, 0], [-i, 0], [0, i], [0, -i]]) blend(img, 3 + dx, 3 + dy, c(i === 1 ? '#FFFFFF' : '#FFD640'), k * (i === r ? 0.6 : 1));
    if (f === 1) for (const [dx, dy] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) blend(img, 3 + dx, 3 + dy, c('#FFF1A0'), 0.7);
    return img;
  }));
}

/** Soft shadow under the big wheel (216²), drawn below the bowl in the spin view. */
export function wheelShadow() {
  const img = image(216, 216);
  for (let y = 0; y < 216; y++)
    for (let x = 0; x < 216; x++) {
      const d = Math.hypot(x + 0.5 - 110, y + 0.5 - 112);
      if (d < 108) blend(img, x, y, c(INK), d < 100 ? 0.55 : 0.55 * (1 - (d - 100) / 8));
    }
  return img;
}


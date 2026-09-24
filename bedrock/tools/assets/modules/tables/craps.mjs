// Craps felt (docs/design/visual/tables.md §4.1) and the Dice Duel arena floor (§5.1), per theme. Digits and
// shapes only: PASS LINE, COME, FIELD, DON'T PASS, DON'T COME and ODDS are runtime text in the label rects below.
import { GOLD, INK, THEME } from './theme.mjs';
import { P, R, blend, c, disc, image, over, polygon, ring, rng, textC } from './draw.mjs';
import { backWall, dieArt } from './dice.mjs';

export const CRAPS_BIG = { w: 386, wall: 6, gap: 3, placeH: 36, dcW: 64, comeH: 32, fieldH: 30, dpH: 16, passH: 18, oddsH: 8, big: true };
export const CRAPS_COMPACT = { w: 264, wall: 4, gap: 2, placeH: 26, dcW: 40, comeH: 22, fieldH: 22, dpH: 12, passH: 14, oddsH: 6, big: false };
export const PLACE_NUMBERS = [4, 5, 6, 8, 9, 10];
export const FIELD_NUMBERS = [2, 3, 4, 9, 10, 11, 12];

/** Every bet area of the craps layout (image-local rects); the screen hit-tests and draws labels with these. */
export function crapsRects(g) {
  const y0 = g.wall + g.gap;
  const boxW = Math.floor((g.w - g.dcW - 2) / 6);
  const place = PLACE_NUMBERS.map((n, k) => ({ n, x: g.dcW + 2 + k * boxW, y: y0, w: boxW - 1, h: g.placeH }));
  const comeY = y0 + g.placeH + g.gap;
  const fieldY = comeY + g.comeH + g.gap;
  const dpY = fieldY + g.fieldH + g.gap;
  const passY = dpY + g.dpH + g.gap;
  const oddsY = passY + g.passH + 1;
  return {
    wall: { x: 0, y: 0, w: g.w, h: g.wall },
    dontCome: { x: 0, y: y0, w: g.dcW, h: g.placeH + g.gap + g.comeH },
    place,
    come: { x: g.dcW + 2, y: comeY, w: g.w - g.dcW - 2, h: g.comeH },
    field: { x: 0, y: fieldY, w: g.w, h: g.fieldH },
    fieldLabel: { x: 0, y: fieldY, w: Math.round(g.w * 0.22), h: g.fieldH },
    dontPass: { x: 0, y: dpY, w: g.w, h: g.dpH },
    pass: { x: 0, y: passY, w: g.w, h: g.passH },
    odds: { x: 0, y: oddsY, w: g.w, h: g.oddsH },
    h: oddsY + g.oddsH,
  };
}

export function crapsLayout(theme, g) {
  const t = THEME[theme];
  const r = crapsRects(g);
  const img = image(g.w, r.h);
  const L = t.line;
  const frame = (q, col = L) => {
    R(img, q.x, q.y, q.w, 1, col);
    R(img, q.x, q.y + q.h - 1, q.w, 1, col);
    R(img, q.x, q.y, 1, q.h, col);
    R(img, q.x + q.w - 1, q.y, 1, q.h, col);
  };
  const wash = (q, hex, k) => R(img, q.x + 1, q.y + 1, q.w - 2, q.h - 2, hex, k);
  // back wall (pyramid rubber) with a trim rail on top
  const tile = backWall();
  for (let x = 0; x < g.w; x += 16) over(img, tile, x, g.wall - 8 + (g.wall < 8 ? 8 - g.wall : 0));
  R(img, 0, 0, g.w, 1, t.trim);
  R(img, 0, g.wall - 1, g.w, 1, INK);
  // don't come bar: dark box
  wash(r.dontCome, '#000000', 0.3);
  frame(r.dontCome);
  // place boxes: big digits, a chip spot lower half
  for (const b of r.place) {
    wash(b, '#000000', 0.14);
    frame(b);
    const s = g.big ? 2 : 1;
    const cy = b.y + Math.round(b.h * (g.big ? 0.36 : 0.4));
    textC(img, String(b.n), b.x + b.w / 2 + 0.5, cy, L, { font: 'bold', s, shadow: t.feltDeep });
    // chip spot: a faint ring where place bets sit
    ring(img, b.x + b.w / 2, b.y + b.h - (g.big ? 8 : 6), g.big ? 5.5 : 4, 1, c(L), 0.35);
  }
  // come
  wash(r.come, '#FFFFFF', 0.04);
  frame(r.come);
  // field: tinted band, numbers across the right part; 2 and 12 circled with their odds
  wash(r.field, '#000000', 0.12);
  frame(r.field);
  frame(r.fieldLabel);
  const fx0 = r.fieldLabel.x + r.fieldLabel.w;
  const step = (r.field.w - r.fieldLabel.w) / FIELD_NUMBERS.length;
  FIELD_NUMBERS.forEach((n, i) => {
    const cx = fx0 + step * (i + 0.5);
    const cy = r.field.y + r.field.h / 2 - (n === 2 || n === 12 ? (g.big ? 3 : 2) : 0);
    if (n === 2 || n === 12) {
      ring(img, cx, cy, g.big ? 8.5 : 6.5, 1, c(GOLD[1]));
      textC(img, n === 2 ? '2:1' : '3:1', cx + 0.5, cy + (g.big ? 11 : 8), GOLD[1], { font: 'small', shadow: t.feltDeep });
    }
    textC(img, String(n), cx + 0.5, cy, n === 2 || n === 12 ? GOLD[0] : L, { font: g.big ? 'bold' : 'small', shadow: t.feltDeep });
  });
  // don't pass bar: dark band + "bar 12" hint (two dice showing 6 and 6)
  wash(r.dontPass, '#000000', 0.32);
  frame(r.dontPass);
  const d6 = dieArt(6);
  const mini = image(8, 8);
  for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
    const o = ((y * 2) * 16 + x * 2) * 4;
    if (d6.data[o + 3]) P(mini, x, y, `#${[0, 1, 2].map((k) => d6.data[o + k].toString(16).padStart(2, '0')).join('')}`);
  }
  if (g.big) {
    over(img, mini, r.dontPass.x + r.dontPass.w - 24, r.dontPass.y + 4);
    over(img, mini, r.dontPass.x + r.dontPass.w - 14, r.dontPass.y + 4);
  }
  // pass line: double trim line, lighter felt
  wash(r.pass, t.feltLight, 0.55);
  frame(r.pass, t.trim);
  R(img, r.pass.x + 2, r.pass.y + 2, r.pass.w - 4, 1, L);
  R(img, r.pass.x + 2, r.pass.y + r.pass.h - 3, r.pass.w - 4, 1, L);
  // odds lane: dashed lane with chevrons (odds chips sit here, behind the line)
  for (let x = r.odds.x + 2; x < r.odds.x + r.odds.w - 2; x += 4) R(img, x, r.odds.y + r.odds.h - 1, 2, 1, L);
  for (let x = r.odds.x + 12; x < r.odds.x + r.odds.w - 8; x += 24)
    for (let k = 0; k < 3; k++) {
      const yy = r.odds.y + 1 + k;
      P(img, x + k, yy, L, 0.6);
      P(img, x + k, r.odds.y + r.odds.h - 3 - k + 1, L, 0.6);
    }
  return img;
}

// ---- Dice Duel arena --------------------------------------------------------------------------------------------------
/** Arena floor 360 × 140: an octagonal pit (theme stone rim, felt-sand floor), two lanes, a centre line, torches. */
export const ARENA = { w: 360, h: 140, laneY: 58, laneH: 56 };
export function arena(theme) {
  const t = THEME[theme];
  const { w, h } = ARENA;
  const img = image(w, h);
  const stone = theme === 'village' ? ['#9A9A9A', '#7A7A7A', '#5A5A5A', '#3A3A3A'] : theme === 'bastion' ? t.rail : ['#E8E4A8', '#C8C080', '#A8A060', '#6E6A40'];
  const oct = (inset) => {
    const k = 22 - inset * 0.4;
    return [[k + inset, inset], [w - k - inset, inset], [w - inset, k + inset], [w - inset, h - k - inset], [w - k - inset, h - inset], [k + inset, h - inset], [inset, h - k - inset], [inset, k + inset]];
  };
  polygon(img, oct(0), INK);
  // stone rim with block joints, lit from the top
  const rnd = rng(theme.length * 17);
  polygon(img, oct(1), (x, y) => {
    const joint = (x % 12 === 0 && y < h / 2) || (x % 12 === 6 && y >= h / 2) || y % 7 === 0;
    return joint ? stone[3] : y < h / 2 ? stone[rnd() < 0.2 ? 0 : 1] : stone[rnd() < 0.2 ? 1 : 2];
  });
  polygon(img, oct(9), INK);
  polygon(img, oct(10), t.trim);
  polygon(img, oct(11), (x, y) => ((x * 3 + y * 7) % 11 === 0 ? t.feltDark : (x * 5 + y) % 13 === 0 ? t.feltLight : t.felt));
  // inner shadow under the rim
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const o = (y * w + x) * 4;
    if (!img.data[o + 3]) continue;
    if (y >= 11 && y < 16 && x > 30 && x < w - 30) blend(img, x, y, c('#000000'), 0.3 - (y - 11) * 0.05);
  }
  // centre line and lanes (left = you, right = the dealer / opponent)
  for (let y = 14; y < h - 12; y += 4) R(img, w / 2, y, 1, 2, t.line);
  for (const [x0, col] of [[40, '#4FC3F7'], [w / 2 + 16, '#FF6E6A']]) {
    const lw = w / 2 - 56;
    R(img, x0, ARENA.laneY, lw, 1, col, 0.5);
    R(img, x0, ARENA.laneY + ARENA.laneH, lw, 1, col, 0.5);
    for (const cx of [x0, x0 + lw - 1]) R(img, cx, ARENA.laneY, 1, ARENA.laneH + 1, col, 0.5);
  }
  // torches on the rim corners
  for (const [x, y] of [[26, 8], [w - 28, 8], [26, h - 16], [w - 28, h - 16]]) {
    for (let rr = 12; rr > 0; rr -= 3) disc(img, x + 1, y, rr, c(theme === 'end' ? '#F4ECF8' : '#FFB040'), 0.06);
    R(img, x, y, 3, 6, '#6E4422');
    R(img, x, y - 3, 3, 3, theme === 'end' ? '#F4ECF8' : theme === 'bastion' ? '#40C8FF' : '#FFD080');
    P(img, x + 1, y - 4, '#FFFFFF');
  }
  return img;
}

// Casino UI kit art (lane J-L2; docs/architecture/animation.md §2.12, docs/design/visual/extras.md §1–§2): the gold
// nine-slice frame and the title banner per scene (the three casino locations + the Casino Menu lobby and the Loan
// Shark's harbour), blank plates, and the big round casino-chip action button. House style of extras.md §1: ink
// outline, 3 px gold bevel (#FFF4B0 / #FFD640 / #E8B830 / #B07010), the theme's material band, inner accent line,
// corner studs, edge lamps every 8 px (symmetric so tiled edges repeat cleanly). No text in any texture.
import { K, bevel, bulb, c, disc, image, mixHex, outlineRect, put, rect, ring, rng, stud } from '../extras/kit.mjs';

/** Scene materials: frame band (4 tones), inner accent line, lamp colour, banner panel. */
export const THEMES = {
  village: { mat: ['#5A2E1A', '#6A3A22', '#4A2412', '#3A1C0E'], inner: '#E8C860', lamp: '#FFD640', panel: '#2A140A', grain: 'planks' },
  bastion: { mat: ['#2A2230', '#3A3040', '#201A26', '#16121C'], inner: '#FF6020', lamp: '#FF8A3C', panel: '#140A10', grain: 'bricks' },
  end: { mat: ['#7C4F7C', '#8E5E8E', '#6A406A', '#4E2E4E'], inner: '#E8E4A8', lamp: '#D696FF', panel: '#1A0E24', grain: 'tiles' },
  lobby: { mat: ['#5A0E24', '#6A1430', '#4A0A1C', '#3A0614'], inner: '#C8903C', lamp: '#FFD640', panel: '#1E0610', grain: 'leather' },
  loan: { mat: ['#1A2A2E', '#22363A', '#0E1A1E', '#0A1214'], inner: '#D83440', lamp: '#D83440', panel: '#0A1214', grain: 'steel' },
};

const TRIM = ['#FFF4B0', '#FFD640', '#E8B830', '#B07010'];

function grain(theme, x, y, r) {
  const S = THEMES[theme];
  switch (S.grain) {
    case 'planks':
      return S.mat[(y % 6 === 5 ? 3 : ((x * 3 + y) >> 3) % 2)];
    case 'bricks': {
      const row = y >> 2;
      const edge = y % 4 === 3 || (x + (row % 2) * 4) % 8 === 7;
      return edge ? S.mat[3] : (x * 7 + y * 3) % 11 === 0 ? S.mat[1] : S.mat[0];
    }
    case 'tiles':
      return x % 8 === 7 || y % 8 === 7 ? S.mat[2] : (x + y) % 9 === 0 ? S.mat[1] : S.mat[0];
    case 'steel':
      return (x + y) % 7 === 0 ? S.mat[1] : S.mat[(r() * 4) | 0 ? 0 : 2];
    default:
      return (x * 2 + y) % 9 === 0 ? S.mat[1] : r() < 0.08 ? S.mat[2] : S.mat[0];
  }
}

/** Nine-slice frame 64² (border 12), transparent centre: the backdrop is the play area. */
export function frame(theme) {
  const S = THEMES[theme];
  const img = image(64, 64);
  const r = rng(theme.length * 977 + 13);
  for (let y = 0; y < 64; y++)
    for (let x = 0; x < 64; x++) {
      if (x >= 12 && x < 52 && y >= 12 && y < 52) continue;
      put(img, x, y, c(grain(theme, x, y, r)));
    }
  const band = (i, lt, dk) => {
    rect(img, i, i, 64 - 2 * i, 1, lt);
    rect(img, i, i, 1, 64 - 2 * i, lt);
    rect(img, i, 63 - i, 64 - 2 * i, 1, dk);
    rect(img, 63 - i, i, 1, 64 - 2 * i, dk);
  };
  band(0, K.ink, K.ink);
  band(1, TRIM[0], TRIM[3]);
  band(2, TRIM[1], TRIM[2]);
  band(3, TRIM[2], TRIM[3]);
  band(8, TRIM[3], TRIM[0]);
  band(9, TRIM[1], TRIM[2]);
  band(10, S.inner, mixHex(S.inner, '#000000', 0.4));
  band(11, K.ink, K.ink);
  for (const [x, y] of [[5, 5], [58, 5], [5, 58], [58, 58]]) stud(img, x, y, theme === 'loan' ? '#8A9AA8' : TRIM[1], K.white, 1.8);
  for (let k = 20; k <= 44; k += 8) for (const [x, y] of [[k, 5], [k, 58], [5, k], [58, k]]) put(img, x, y, c(S.lamp));
  if (theme === 'loan')
    // hazard chevrons on the corners of the steel band
    for (const [x0, y0] of [[1, 1], [55, 1], [1, 55], [55, 55]])
      for (let i = 0; i < 8; i++) if (((i >> 1) & 1) === 0) put(img, x0 + i, y0 + 7 - i, c('#D83440'));
  return img;
}

/** Title banner nine-slice 48 × 24 (border 10): the scene's panel inside the house gold trim, corner bulbs. */
export function banner(theme) {
  const S = THEMES[theme];
  const img = image(48, 24);
  rect(img, 0, 0, 48, 24, K.ink);
  rect(img, 1, 1, 46, 22, TRIM[3]);
  rect(img, 2, 2, 44, 20, TRIM[1]);
  rect(img, 2, 2, 44, 1, TRIM[0]);
  rect(img, 2, 21, 44, 1, TRIM[2]);
  rect(img, 4, 4, 40, 16, S.panel);
  rect(img, 4, 4, 40, 1, mixHex(S.panel, '#FFFFFF', 0.14));
  rect(img, 4, 19, 40, 1, mixHex(S.panel, '#000000', 0.4));
  outlineRect(img, 3, 3, 42, 18, K.ink);
  for (const [x, y] of [[2, 2], [45, 2], [2, 21], [45, 21]]) put(img, x, y, c(K.white));
  return img;
}

/** Blank plate 24 × 16 (border 5): dark face with a sheen line, 1 px rounded corners; gold or frame-purple rim. */
export function plate(gold) {
  const img = image(24, 16);
  const rim = gold ? [TRIM[1], TRIM[3]] : ['#BE5AFF', '#783CBE'];
  rect(img, 0, 0, 24, 16, K.ink);
  rect(img, 1, 1, 22, 14, rim[1]);
  bevel(img, 1, 1, 22, 14, rim[0], rim[1]);
  rect(img, 2, 2, 20, 12, gold ? '#1E0E2C' : '#26103C');
  rect(img, 2, 2, 20, 1, gold ? '#3A2448' : '#3A1A5C');
  rect(img, 2, 13, 20, 1, '#140822');
  for (const [x, y] of [[0, 0], [23, 0], [0, 15], [23, 15]]) put(img, x, y, [0, 0, 0, 0]);
  return img;
}

/**
 * Big round action button 40² (the casino-chip button of tables.md §2.2 in the core kit): a red chip with a gold
 * rim, six bone edge inserts, a dashed inner ring and a gloss spot; the icon / label is drawn by code on the inlay.
 * States: normal, highlighted (brighter + glint ring), pressed (1 px lower, no gloss), disabled (grey).
 */
export function actionButton(state) {
  const img = image(40, 40);
  const off = state === 'pressed' ? 1 : 0;
  const cx = 20;
  const cy = 20 + off;
  const grey = state === 'disabled';
  const hot = state === 'highlighted';
  const base = grey ? '#5A5A62' : hot ? '#F04450' : '#D83440';
  const dark = grey ? '#3A3A40' : '#8C1834';
  const rimA = grey ? '#8A8A92' : TRIM[1];
  const rimB = grey ? '#4A4A52' : TRIM[3];
  if (!off) disc(img, cx + 1, cy + 2, 19, c(K.ink), 0.4); // drop shadow
  disc(img, cx, cy, 19.5, c(K.ink));
  disc(img, cx, cy, 18.5, c(rimB));
  disc(img, cx, cy - 0.5, 18, c(rimA));
  disc(img, cx, cy, 16.5, c(dark));
  disc(img, cx, cy - 0.5, 16, c(base));
  // six bone edge inserts
  for (let i = 0; i < 6; i++) {
    const a = (i / 6) * Math.PI * 2 + Math.PI / 6;
    disc(img, cx + Math.cos(a) * 13.5, cy + Math.sin(a) * 13.5, 2.4, c(grey ? '#B0B0B8' : K.bone));
  }
  // dashed inner ring + inlay
  for (let i = 0; i < 24; i++) {
    if (i % 2) continue;
    const a = (i / 24) * Math.PI * 2;
    put(img, Math.round(cx - 0.5 + Math.cos(a) * 10), Math.round(cy - 0.5 + Math.sin(a) * 10), c(grey ? '#C8C8D0' : '#FFD0C8'));
  }
  disc(img, cx, cy, 8.5, c(dark));
  disc(img, cx, cy - 0.5, 8, c(grey ? '#6A6A72' : '#B82A36'));
  if (!off && !grey) {
    disc(img, cx - 7, cy - 9, 2.5, c(K.white), 0.45);
    put(img, cx - 8, cy - 10, c(K.white));
  }
  if (hot) ring(img, cx, cy, 19.5, 1, c('#FFF4B0'), 0.9);
  return img;
}

/** Tiny helper used by the tests: a scene's lamp bulbs for a marquee-free header (kept for parity with extras). */
export function lamp(on, theme) {
  const img = image(8, 8);
  bulb(img, 4, 4, on, THEMES[theme].lamp);
  return img;
}

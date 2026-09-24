// Suit shapes (visual/cards.md §3.2). Shapes carry the suit on their own (greyscale-safe); the four-colour deck
// adds hue + luminance. Small sizes (5 × 5 index pips) are hand-drawn grids; larger pips are supersampled
// geometry (circles + triangles), then bevelled so they read as the slots-quality chunky pixel art.
import { fromRows, image, mask, paintMask } from './px.mjs';

const inTri = (px, py, [ax, ay], [bx, by], [cx, cy]) => {
  const s = (x1, y1, x2, y2, x3, y3) => (x1 - x3) * (y2 - y3) - (x2 - x3) * (y1 - y3);
  const d1 = s(px, py, ax, ay, bx, by);
  const d2 = s(px, py, bx, by, cx, cy);
  const d3 = s(px, py, cx, cy, ax, ay);
  return !((d1 < 0 || d2 < 0 || d3 < 0) && (d1 > 0 || d2 > 0 || d3 > 0));
};
const inCirc = (x, y, cx, cy, r) => (x - cx) ** 2 + (y - cy) ** 2 <= r * r;

/** Unit shapes on [-1, 1]² (y down). */
export const SHAPES = {
  hearts: (x, y) => inCirc(x, y, -0.47, -0.38, 0.52) || inCirc(x, y, 0.47, -0.38, 0.52) || inTri(x, y, [-0.98, -0.2], [0.98, -0.2], [0, 1]),
  diamonds: (x, y) => Math.abs(x) / 0.78 + Math.abs(y) / 1.0 <= 1,
  spades: (x, y) =>
    inCirc(x, y, -0.45, 0.2, 0.47) || inCirc(x, y, 0.45, 0.2, 0.47) || inTri(x, y, [-0.92, 0.08], [0.92, 0.08], [0, -1]) || inTri(x, y, [0, 0.2], [-0.42, 1], [0.42, 1]),
  clubs: (x, y) =>
    inCirc(x, y, 0, -0.5, 0.42) || inCirc(x, y, -0.5, 0.18, 0.42) || inCirc(x, y, 0.5, 0.18, 0.42) || inCirc(x, y, 0, 0.05, 0.25) || inTri(x, y, [0, 0.05], [-0.4, 1], [0.4, 1]),
};

/** Hand-tuned 5 × 5 index pips. */
const SMALL = {
  spades: ['..#..', '.###.', '#####', '#####', '..#..'],
  hearts: ['.#.#.', '#####', '#####', '.###.', '..#..'],
  diamonds: ['..#..', '.###.', '#####', '.###.', '..#..'],
  clubs: ['.###.', '.###.', '#####', '##.##', '..#..'],
};
/** Hand-tuned 7 × 7 pips (the L-card pip; bevelled by `pip`). */
const MED = {
  spades: ['...#...', '..###..', '.#####.', '#######', '#######', '...#...', '..###..'],
  hearts: ['.##.##.', '#######', '#######', '#######', '.#####.', '..###..', '...#...'],
  diamonds: ['...#...', '..###..', '.#####.', '#######', '.#####.', '..###..', '...#...'],
  clubs: ['..###..', '..###..', '###.###', '#######', '###.###', '...#...', '..###..'],
};

const maskFromRows = (rows) => mask(rows[0].length, rows.length, (x, y) => rows[Math.floor(y)]?.[Math.floor(x)] === '#', 1);

/**
 * A pip image of `size` px (square) for `suit` with colours {base, light, dark}. 5 and 7 use the hand grids;
 * other sizes are geometry. `flat` skips the bevel (tiny UI glyphs).
 */
export function pip(suit, size, colours, { flat = false, outlineC } = {}) {
  let mk;
  if (size === 5) mk = maskFromRows(SMALL[suit]);
  else if (size === 7) mk = maskFromRows(MED[suit]);
  else {
    const f = SHAPES[suit];
    mk = mask(size, size, (x, y) => f((x / size) * 2 - 1, (y / size) * 2 - 1), 5);
  }
  const pad = outlineC ? 1 : 0;
  const img = image(size + 2 * pad, size + 2 * pad);
  if (flat) return paintMask(img, mk, pad, pad, { base: colours.base, outlineC });
  if (size <= 7) return paintMask(img, mk, pad, pad, { base: colours.base, dark: colours.dark, outlineC });
  return paintMask(img, mk, pad, pad, { base: colours.base, light: colours.light, dark: colours.dark, outlineC });
}

/** Flipped vertically (the lower half of a card shows pips upside down). */
export function flipV(img) {
  const out = image(img.w, img.h);
  for (let y = 0; y < img.h; y++) out.data.set(img.data.subarray(y * img.w * 4, (y + 1) * img.w * 4), (img.h - 1 - y) * img.w * 4);
  return out;
}

export const smallRows = SMALL;
export const suitGlyph = (suit, legend) => fromRows(SMALL[suit].map((r) => r.replace(/#/g, 'x')), { x: legend });

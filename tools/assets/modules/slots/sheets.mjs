// Symbol sheets (Java, code-indexed, animation/slots.md §9.1).
import { art } from './art.mjs';
import { baseFrame, blurFrame, idleFrames, landFrames, winFrames } from './frames.mjs';
import { ICON } from './icons.mjs';
import { affine, image, over, vstrip, hstrip } from './raster.mjs';
import { MACHINE_SYMBOLS } from './symbols.mjs';

/** Java machine sheet: 16 columns (0 base, 1 blur, 2–9 win, 10–15 idle) × 11 rows (symbol order), `size` px cells. */
export function machineSheet(machine, size) {
  const list = MACHINE_SYMBOLS[machine];
  const out = image(16 * size, list.length * size);
  list.forEach((s, row) => {
    [baseFrame(s, size), blurFrame(s, size), ...winFrames(s, size), ...idleFrames(s, size)].forEach((f, col) => over(out, f, col * size, row * size));
  });
  return out;
}

/** Showdown mini sheet (D2): 2 columns (base, blur) × 11 rows of 16 px. */
export function miniSheet(machine) {
  const list = MACHINE_SYMBOLS[machine];
  const out = image(32, list.length * 16);
  list.forEach((s, row) => {
    over(out, baseFrame(s, 16), 0, row * 16);
    over(out, blurFrame(s, 16), 16, row * 16);
  });
  return out;
}

/** Land keys sheet (Java, §3.2): 2 columns × 11 rows at 40 px. */
export function landSheet(machine) {
  const list = MACHINE_SYMBOLS[machine];
  const out = image(80, list.length * 40);
  list.forEach((s, row) => landFrames(s, 40).forEach((f, c) => over(out, f, c * 40, row * 40)));
  return out;
}

/**
 * End tall expanded Dragon Egg (§3.2): 40 × 132 frames, 8 shimmer + 3 grow keys, laid out horizontally (440 × 132).
 * The tall egg is the egg art stretched over three cells with the motes/crack loop drawn at the full height.
 */
export function tallEgg() {
  const egg = MACHINE_SYMBOLS.end[0];
  const frames = [];
  const tall = (sy, lit, phase) => {
    const img = image(40, 132);
    const body = affine(art(egg, { lit }), 40, 132, { scale: 2, sx: 1.1, sy: sy * 3.6, px: 8, py: 8, ox: 20, oy: 66 });
    over(img, body);
    // rising motes along the column
    for (let k = 0; k < 7; k++) {
      const y = 126 - (((phase * 17 + k * 19) % 120) | 0);
      const x = 6 + ((k * 23 + phase * 3) % 28);
      over(img, dot(k % 2 ? 2 : 1, lit ? '#F0B0FF' : '#B04CFF'), x, y);
    }
    return img;
  };
  for (let i = 0; i < 8; i++) frames.push(tall(1, i % 4 === 1 || i % 4 === 2, i));
  for (const g of [1 / 3, 2 / 3, 1]) frames.push(tall(g, true, 0));
  return hstrip(frames);
}

function dot(r, hex) {
  const img = image(r * 2 + 1, r * 2 + 1);
  const [cr, cg, cb] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
  for (let y = 0; y < img.h; y++) for (let x = 0; x < img.w; x++) if (Math.abs(x - r) + Math.abs(y - r) <= r) img.data.set([cr, cg, cb, x === r && y === r ? 255 : 200], (y * img.w + x) * 4);
  return img;
}

export { ICON, vstrip };

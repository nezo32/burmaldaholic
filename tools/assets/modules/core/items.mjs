// Chip stack item textures (global.md §4.4): `chip_<d>_few` (2 chips offset), `_stack` (short stack),
// `_tower` (tall stack + one leaning chip); 16², denomination palette, 1 px ink outline, top ellipse + side
// stripes with bone edge inserts. Java item models (`range_dispatch` on count) are lane J-L2's.
import { CHIPS, rgba, shade } from '../../lib/palette.mjs';
import { ellipse, image, setPx } from '../../lib/grid.mjs';
import { outline, rotate } from '../../lib/transforms.mjs';

/** One chip seen from the side: top ellipse centred at (cx, cy), 2 px edge below it. */
function chip(img, cx, cy, d, rx = 5.5) {
  const { base, stripe } = CHIPS[d];
  const insert = d === 1 ? '#3A6FD8' : '#F4ECF8';
  for (let y = 0; y < 2; y++)
    for (let x = Math.ceil(cx - rx); x < Math.floor(cx + rx); x++) {
      const k = Math.floor(x - (cx - rx));
      setPx(img, x, Math.round(cy) + 1 + y, k % 4 === 1 ? insert : shade(base, y ? 0.62 : 0.8));
    }
  ellipse(img, cx, cy + 0.5, rx, 2, (x, y, dd) => (dd > 0.72 ? stripe : dd < 0.4 ? shade(base, 1.18) : base));
}

export function chipStack(d, kind) {
  let img = image(16, 16);
  if (kind === 'few') {
    chip(img, 6, 11, d);
    chip(img, 10, 8, d);
  } else if (kind === 'stack') {
    for (let i = 0; i < 4; i++) chip(img, 8, 12 - i * 2, d);
  } else {
    const lean = image(16, 16);
    chip(lean, 8, 9, d, 4.5);
    const leaning = rotate(lean, -60);
    for (let i = 0; i < 6; i++) chip(img, 7, 12 - i * 2, d, 4.5);
    for (let y = 0; y < 16; y++)
      for (let x = 0; x < 16; x++) {
        const o = (y * 16 + x) * 4;
        const sx = x - 5;
        if (sx < 0 || !leaning.data[(y * 16 + sx) * 4 + 3] || img.data[o + 3]) continue;
        img.data.set(leaning.data.subarray((y * 16 + sx) * 4, (y * 16 + sx) * 4 + 4), o);
      }
  }
  img = outline(img, '#180A28');
  return img;
}

export { CHIP_DENOMS } from '../../lib/palette.mjs';

/**
 * The single-chip item texture `item/core/chip_<d>` (16², count 1): a round chip seen slightly from above, rim with 16
 * edge spots, an inner ring and a centre inlay in the stripe colour. Port of the old `gen_core_assets.py` chip (same
 * geometry); the colours are the palette's, so the item matches the tray and the stacks.
 */
export function chipItem(d) {
  const { base, stripe } = CHIPS[d];
  const img = image(16, 16);
  const put = (x, y, hex, f) => {
    const [r, g, b] = rgba(hex);
    const k = (v) => Math.max(0, Math.min(255, Math.trunc(v * f)));
    img.data.set([k(r), k(g), k(b), 255], (y * 16 + x) * 4);
  };
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++) {
      const dx = x - 7.5;
      const dy = y - 7.5;
      const r = Math.hypot(dx, dy);
      if (r > 7.6) continue;
      const ang = ((Math.atan2(dy, dx) * 180) / Math.PI + 360) % 360;
      if (r > 6.2) put(x, y, Math.floor(ang / 22.5) % 2 === 0 ? stripe : base, dy > 3 ? 0.8 : 1.0);
      else if (r > 5.2) put(x, y, base, 0.7);
      else if (r > 3.0) put(x, y, base, 1.05 - 0.04 * dy);
      else put(x, y, stripe, 0.95 - 0.03 * dy);
    }
  return img;
}
export const STACK_KINDS = ['few', 'stack', 'tower'];
